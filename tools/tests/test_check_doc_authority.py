from __future__ import annotations

import runpy
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
TOOL = runpy.run_path(
    str(ROOT / "tools/check-doc-authority"), run_name="check_doc_authority_tool"
)
validate = TOOL["validate"]


class DocumentationAuthorityTest(unittest.TestCase):
    def fixture(self, directory: str) -> Path:
        root = Path(directory)
        (root / "docs/archive/handoffs").mkdir(parents=True)
        (root / ".opencode/agents").mkdir(parents=True)
        (root / ".opencode/commands").mkdir(parents=True)
        (root / ".opencode/skills/example").mkdir(parents=True)

        (root / "AGENTS.md").write_text("Read docs/README.md.\n", encoding="utf-8")
        (root / "docs/active-plan.md").write_text(
            "# Active plan\n\nStatus: active\n", encoding="utf-8"
        )
        (root / "docs/README.md").write_text(
            "# Authority\n\n"
            "## Normative and operational documents\n\n"
            "## Active plans\n\n"
            "| `active-plan.md` | Active plan |\n\n"
            "## Dated references\n",
            encoding="utf-8",
        )
        (root / "docs/archive/handoffs/old.md").write_text(
            "# Old\n\n> **Status: historical.**\n", encoding="utf-8"
        )
        (root / "docs/archive/README.md").write_text(
            "# Archive\n\n## Handoffs\n\n"
            "| `handoffs/old.md` | Historical |\n\n"
            "## Reports\n",
            encoding="utf-8",
        )
        (root / ".opencode/agents/example.md").write_text(
            "Use current specifications.\n", encoding="utf-8"
        )
        return root

    def test_accepts_classified_active_and_archived_documents(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            self.assertEqual(validate(self.fixture(directory)), [])

    def test_rejects_unclassified_root_document(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = self.fixture(directory)
            (root / "docs/new-reference.md").write_text("# New\n", encoding="utf-8")
            with (root / "docs/README.md").open("a", encoding="utf-8") as index:
                index.write("\nMentioned outside a class: `new-reference.md`.\n")

            self.assertIn(
                "docs/README.md does not classify new-reference.md",
                validate(root),
            )

    def test_rejects_session_handoff_in_active_docs(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = self.fixture(directory)
            (root / "docs/new-handoff.md").write_text(
                "# New\n\nStatus: active\n", encoding="utf-8"
            )
            (root / "docs/README.md").write_text(
                "## Normative and operational documents\n\n"
                "## Active plans\n\n"
                "| `active-plan.md` | Active |\n"
                "| `new-handoff.md` | Active |\n\n"
                "## Dated references\n",
                encoding="utf-8",
            )

            self.assertIn(
                "session/handoff document must not live in docs/: new-handoff.md",
                validate(root),
            )

    def test_requires_status_from_authority_class_not_filename(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = self.fixture(directory)
            (root / "docs/work-queue.md").write_text("# Work queue\n", encoding="utf-8")
            (root / "docs/evidence.md").write_text("# Evidence\n", encoding="utf-8")
            (root / "docs/README.md").write_text(
                "## Normative and operational documents\n\n"
                "## Active plans\n\n"
                "| `active-plan.md` | Active |\n"
                "| `work-queue.md` | Active |\n\n"
                "## Dated references\n\n"
                "| `evidence.md` | Dated evidence |\n",
                encoding="utf-8",
            )

            errors = validate(root)
            self.assertIn("docs/work-queue.md must declare Status near the top", errors)
            self.assertIn("docs/evidence.md must declare Status near the top", errors)

    def test_rejects_dated_or_exact_archive_context_in_agent_prompt(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = self.fixture(directory)
            (root / ".opencode/agents/example.md").write_text(
                "Read docs/ai-skills-audit-2026-07-28.md and "
                "docs/archive/handoffs/old.md.\n",
                encoding="utf-8",
            )

            errors = validate(root)
            self.assertTrue(any("must not hard-code dated context" in error for error in errors))
            self.assertTrue(any("must not hard-code archived context" in error for error in errors))

    def test_rejects_any_dated_document_in_operational_context(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = self.fixture(directory)
            (root / ".opencode/agents/example.md").write_text(
                "Read docs/current-player-ui-ux-2026-07-29.md.\n",
                encoding="utf-8",
            )

            self.assertTrue(
                any("current-player-ui-ux-2026-07-29.md" in error for error in validate(root))
            )


if __name__ == "__main__":
    unittest.main()
