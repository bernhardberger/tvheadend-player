from __future__ import annotations

import os
from pathlib import Path
import shutil
import subprocess
import unittest


ROOT = Path(__file__).resolve().parents[2]
ADAPTER = ROOT / ".opencode/skills/gradle-run"
VENDOR_SKILL = (
    ROOT.parent
    / "tvheadend-sdk-workspace/.opencode/vendor/chrisbanes-skills/skills/gradle-run"
)
VENDOR_ROUTE = (
    "../../../../tvheadend-sdk-workspace/.opencode/vendor/"
    "chrisbanes-skills/skills/gradle-run/SKILL.md"
)


class GradleSkillTest(unittest.TestCase):
    def test_adapter_routes_to_complete_vendor_skill_package(self) -> None:
        self.assertTrue(ADAPTER.is_dir())
        self.assertFalse(ADAPTER.is_symlink())

        adapter_skill = ADAPTER / "SKILL.md"
        self.assertTrue(adapter_skill.is_file())
        self.assertFalse(adapter_skill.is_symlink())
        self.assertIn(VENDOR_ROUTE, adapter_skill.read_text(encoding="utf-8"))
        self.assertNotEqual(
            VENDOR_SKILL.joinpath("SKILL.md").read_bytes(),
            adapter_skill.read_bytes(),
        )

        for name in ("scripts", "agents"):
            adapter_path = ADAPTER / name
            self.assertTrue(adapter_path.is_symlink(), name)
            self.assertEqual(VENDOR_SKILL / name, adapter_path.resolve(strict=True))

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
        self.assertIn(f'"location": "{ADAPTER / "SKILL.md"}"', result.stdout)


if __name__ == "__main__":
    unittest.main()
