from __future__ import annotations

import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]
SKILL = ROOT / ".opencode/skills/gradle-run"
WRAPPER = SKILL / "scripts/gradle_run.py"
EXPECTED_PACKAGE_PATHS = {
    "LICENSE",
    "SKILL.md",
    "agents",
    "agents/openai.yaml",
    "scripts",
    "scripts/gradle_run.py",
    "scripts/test_gradle_run.py",
}


class GradleSkillTest(unittest.TestCase):
    def test_skill_is_a_complete_in_repository_package(self) -> None:
        self.assertTrue(SKILL.is_dir())
        self.assertFalse(SKILL.is_symlink())

        package_paths = {str(path.relative_to(SKILL)) for path in SKILL.rglob("*")}
        self.assertEqual(EXPECTED_PACKAGE_PATHS, package_paths)

        skill_root = SKILL.resolve(strict=True)
        for path in (SKILL, *SKILL.rglob("*")):
            self.assertFalse(path.is_symlink(), path)
            self.assertTrue(path.resolve(strict=True).is_relative_to(skill_root), path)

        for path in (SKILL / "SKILL.md", WRAPPER, SKILL / "scripts/test_gradle_run.py"):
            self.assertNotIn(
                "tvheadend-sdk-workspace",
                path.read_text(encoding="utf-8"),
                path,
            )

    def test_in_repository_wrapper_executes_without_a_sibling_workspace(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            managed_root = Path(temporary_directory) / "gradle-run"
            result = subprocess.run(
                [sys.executable, str(WRAPPER), "--root", str(managed_root), "create"],
                cwd=ROOT,
                text=True,
                capture_output=True,
                timeout=10,
                check=False,
            )

            self.assertEqual(0, result.returncode, result.stderr)
            payload = json.loads(result.stdout)
            self.assertEqual(managed_root / payload["workflow"], Path(payload["directory"]))

    @unittest.skipUnless(shutil.which("opencode"), "opencode is required")
    def test_pure_opencode_discovers_gradle_skill_once(self) -> None:
        result = subprocess.run(
            ["opencode", "debug", "skill", "--pure"],
            cwd=ROOT,
            env={
                **os.environ,
                "OPENCODE_DISABLE_EXTERNAL_SKILLS": "1",
                "OPENCODE_DISABLE_CLAUDE_CODE_SKILLS": "1",
            },
            text=True,
            capture_output=True,
            timeout=60,
            check=False,
        )

        self.assertEqual(0, result.returncode, result.stderr[-2000:])
        self.assertEqual(1, result.stdout.count('"name": "gradle-run"'))
        self.assertIn(f'"location": "{SKILL / "SKILL.md"}"', result.stdout)


if __name__ == "__main__":
    unittest.main()
