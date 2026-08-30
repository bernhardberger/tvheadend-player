from __future__ import annotations

import json
import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CONFIG_PATH = ROOT / ".opencode/opencode.json"
AGENT_DIR = ROOT / ".opencode/agents"
COMMAND_PATH = ROOT / ".opencode/commands/claude-audit-track.md"

CLAUDE_AGENTS = {
    "claude-audit-lead": ("anthropic/claude-opus-5", "max", 60),
    "claude-local-analysis": ("anthropic/claude-sonnet-5", "xhigh", 45),
    "claude-external-research": ("anthropic/claude-sonnet-5", "high", 35),
}
OPENAI_ANALYTICAL_ROLES = {
    "app-analyze",
    "app-explore",
    "app-research",
    "app-planner",
    "android-reviewer",
    "tv-ux-reviewer",
}


def frontmatter(path: Path) -> str:
    text = path.read_text(encoding="utf-8")
    match = re.match(r"---\n(.*?)\n---\n", text, flags=re.DOTALL)
    if match is None:
        raise AssertionError(f"missing frontmatter: {path}")
    return match.group(1)


def normalized(path: Path) -> str:
    return " ".join(path.read_text(encoding="utf-8").split())


def permissions(path: Path) -> dict[str, str | dict[str, str]]:
    metadata = frontmatter(path)
    lines = metadata.splitlines()
    start = lines.index("permission:") + 1
    result: dict[str, str | dict[str, str]] = {}
    current: str | None = None
    for line in lines[start:]:
        if not line.startswith("  "):
            break
        if line.startswith("    "):
            if current is None or not isinstance(result[current], dict):
                raise AssertionError(f"unexpected nested permission line: {line}")
            key, value = line.strip().split(": ", 1)
            result[current][key.strip('"')] = value
            continue
        key, separator, value = line.strip().partition(":")
        if not separator:
            raise AssertionError(f"invalid permission line: {line}")
        current = key
        result[key] = value.strip() if value.strip() else {}
    return result


class ClaudeAuditHarnessTest(unittest.TestCase):
    def setUp(self) -> None:
        self.config = json.loads(CONFIG_PATH.read_text(encoding="utf-8"))

    def test_configures_exact_models_variants_modes_and_budgets(self) -> None:
        self.assertEqual(self.config["subagent_depth"], 2)
        for name, (model, variant, steps) in CLAUDE_AGENTS.items():
            with self.subTest(name=name):
                agent = self.config["agent"][name]
                self.assertEqual(agent["model"], model)
                self.assertEqual(agent["variant"], variant)
                self.assertEqual(agent["steps"], steps)
                metadata = frontmatter(AGENT_DIR / f"{name}.md")
                self.assertIn("mode: subagent", metadata)

    def test_root_can_invoke_only_the_claude_lead(self) -> None:
        task = self.config["permission"]["task"]
        self.assertEqual(task["*"], "deny")
        self.assertEqual(task["claude-audit-lead"], "allow")
        self.assertNotIn("claude-local-analysis", task)
        self.assertNotIn("claude-external-research", task)

    def test_lead_allows_only_claude_children_and_independent_luna(self) -> None:
        task = permissions(AGENT_DIR / "claude-audit-lead.md")["task"]
        self.assertEqual(
            task,
            {
                "*": "deny",
                **{role: "deny" for role in OPENAI_ANALYTICAL_ROLES},
                "app-locator": "allow",
                "claude-local-analysis": "allow",
                "claude-external-research": "allow",
            },
        )

    def test_every_claude_role_is_read_only_and_fail_closed(self) -> None:
        expected_common = {
            "edit": "deny",
            "bash": "deny",
            "external_directory": "deny",
            "websearch": "deny",
            "todowrite": "deny",
            "skill": "deny",
            "question": "deny",
            "publish_artifact": "deny",
            "compress": "deny",
        }
        for name in CLAUDE_AGENTS:
            with self.subTest(name=name):
                text = normalized(AGENT_DIR / f"{name}.md")
                role_permissions = permissions(AGENT_DIR / f"{name}.md")
                for tool, action in expected_common.items():
                    self.assertEqual(role_permissions[tool], action)
                expected_webfetch = (
                    "allow" if name == "claude-external-research" else "deny"
                )
                self.assertEqual(role_permissions["webfetch"], expected_webfetch)
                self.assertIn("Never read credentials", text)
                self.assertIn("session or process", text)
                self.assertIn("Never read project instructions", text)

    def test_every_role_locks_runtime_quota_and_provenance(self) -> None:
        expected_runtime = {
            "claude-audit-lead": (
                "anthropic/claude-opus-5",
                "claude-opus-5",
                "max",
                "60",
            ),
            "claude-local-analysis": (
                "anthropic/claude-sonnet-5",
                "claude-sonnet-5",
                "xhigh",
                "45",
            ),
            "claude-external-research": (
                "anthropic/claude-sonnet-5",
                "claude-sonnet-5",
                "high",
                "35",
            ),
        }
        for name, (route, model, variant, steps) in expected_runtime.items():
            with self.subTest(name=name):
                text = normalized(AGENT_DIR / f"{name}.md")
                for expected in (
                    f"observed_route={route}",
                    "observed_provider=anthropic",
                    f"observed_model={model}",
                    f"observed_variant={variant}",
                    "observed_mode=subagent",
                    f"observed_steps={steps}",
                    "quota_checked_at",
                    "RFC 3339 UTC",
                    "quota_max_age_minutes=30",
                    "quota_eligible=true",
                    "Reject a future, malformed, or more-than-30-minute-old check",
                    "Require provenance for every supplied fact and evidence item",
                    "Reject every Sol-, Grok-, or other-track-generated input regardless of its label",
                ):
                    self.assertIn(expected, text)

    def test_sonnet_roles_cannot_delegate(self) -> None:
        for name in ("claude-local-analysis", "claude-external-research"):
            with self.subTest(name=name):
                role_permissions = permissions(AGENT_DIR / f"{name}.md")
                self.assertEqual(role_permissions["task"], {"*": "deny"})

    def test_command_requires_quota_isolation_and_sealed_reports(self) -> None:
        text = normalized(COMMAND_PATH)
        required_contract = (
            "anthropic/claude-opus-5",
            "anthropic/claude-sonnet-5",
            "quota_checked_at",
            "quota_eligible",
            "quota_max_age_minutes=30",
            "RFC 3339 UTC",
            "Reject a future, malformed, or more-than-30-minute-old check",
            "observed_provider",
            "observed_model",
            "observed_variant",
            "observed_mode",
            "observed_steps",
            "stop without substitution",
            "separate task packets",
            "Do not pass one child's output to another child",
            "SEALED_LOCAL_ANALYSIS",
            "SEALED_EXTERNAL_RESEARCH",
            "SEALED_CLAUDE_AUDIT",
            "Do not accept a Sol-track map, report, candidate list, or conclusion",
            "Reject every Sol-, Grok-, or other-track-generated input regardless of its label",
            "Require provenance for every supplied fact and evidence item",
        )
        for expected in required_contract:
            with self.subTest(expected=expected):
                self.assertIn(expected, text)


if __name__ == "__main__":
    unittest.main()
