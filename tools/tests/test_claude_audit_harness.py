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
    "claude-audit-lead": ("anthropic/claude-opus-5", "high", 45),
    "claude-local-analysis": ("anthropic/claude-sonnet-5", "high", 35),
    "claude-external-research": ("anthropic/claude-sonnet-5", "medium", 30),
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

    def test_roles_do_not_require_admission_ceremony(self) -> None:
        forbidden = (
            "quota_checked_at",
            "quota_max_age_minutes",
            "quota_eligible",
            "RFC 3339 UTC",
            "observed_route",
            "observed_provider",
            "observed_model",
            "observed_variant",
            "observed_mode",
            "observed_steps",
            "SEALED_",
            "Require provenance for every supplied fact",
            "Reject every Sol-",
            "exact revision",
        )
        for name in CLAUDE_AGENTS:
            with self.subTest(name=name):
                text = normalized(AGENT_DIR / f"{name}.md")
                for ceremony in forbidden:
                    self.assertNotIn(ceremony, text)

        lead_text = normalized(AGENT_DIR / "claude-audit-lead.md")
        self.assertIn("Do not require quota telemetry", lead_text)
        self.assertIn("Never retry a child automatically", lead_text)
        for name in ("claude-local-analysis", "claude-external-research"):
            child_text = normalized(AGENT_DIR / f"{name}.md")
            self.assertIn(
                "Do not reject a usable task because optional packet metadata is absent",
                child_text,
            )

    def test_sonnet_roles_cannot_delegate(self) -> None:
        for name in ("claude-local-analysis", "claude-external-research"):
            with self.subTest(name=name):
                role_permissions = permissions(AGENT_DIR / f"{name}.md")
                self.assertEqual(role_permissions["task"], {"*": "deny"})

    def test_command_uses_configured_routes_and_simple_packets(self) -> None:
        text = normalized(COMMAND_PATH)
        forbidden = (
            "quota_checked_at",
            "quota_eligible",
            "quota_max_age_minutes",
            "RFC 3339 UTC",
            "observed_route",
            "observed_provider",
            "observed_model",
            "observed_variant",
            "observed_mode",
            "observed_steps",
            "SEALED_",
            "Require provenance for every supplied fact",
            "Reject every Sol-",
        )
        for ceremony in forbidden:
            self.assertNotIn(ceremony, text)

        required_contract = (
            "Use the configured Claude routes",
            "Do not ask any task to repeat route, model, variant, step, revision, quota, or provenance attestations",
            "Never retry a child automatically",
            "bounded question",
            "allowed paths or sources",
            "exclusions",
            "required output",
            "stop condition",
            "Do not pass another track's analysis or conclusions",
        )
        for expected in required_contract:
            with self.subTest(expected=expected):
                self.assertIn(expected, text)


if __name__ == "__main__":
    unittest.main()
