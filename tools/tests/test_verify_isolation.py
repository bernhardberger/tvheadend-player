import os
from pathlib import Path
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]


class VerifyIsolationTest(unittest.TestCase):
    def run_verify(self, *, installed=False, isolated=False, arguments=()):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "tools").mkdir()
            (root / "bin").mkdir()
            launcher = root / "launcher"
            verify = root / "tools/verify"
            verify.write_text((ROOT / "tools/verify").read_text().replace(
                "/usr/local/bin/tvhplayer-verify", str(launcher)))
            verify.chmod(0o755)
            scripts = {
                root / "bin/java": 'echo \'openjdk version "21.0.1"\' >&2\n',
                root / "bin/python3": "exit 0\n",
                root / "gradlew": 'printf "ARG=%s\\n" "$@"; exit 23\n',
            }
            if installed:
                scripts[launcher] = 'printf "LAUNCH=%s\\n" "$@"; exit 37\n'
            for path, body in scripts.items():
                path.write_text("#!/bin/sh\n" + body)
                path.chmod(0o755)
            env = {**os.environ, "PATH": f"{root / 'bin'}:{os.environ['PATH']}",
                   "ANDROID_HOME": str(root),
                   "TVHPLAYER_VERIFY_ISOLATED": "1" if isolated else ""}
            return subprocess.run([str(verify), *arguments], env=env,
                                  text=True, capture_output=True, timeout=10)

    def test_installed_launcher_gets_options_and_failure_never_falls_back(self):
        result = self.run_verify(installed=True, arguments=("--staged-sdk", "--visual-captures", "--release"))
        self.assertEqual(result.returncode, 37)
        self.assertIn("LAUNCH=--staged-sdk", result.stdout)
        self.assertIn("LAUNCH=--visual-captures", result.stdout)
        self.assertIn("LAUNCH=--release", result.stdout)
        self.assertNotIn("ARG=", result.stdout)

    def test_worker_does_not_recurse_or_reuse_external_daemons(self):
        result = self.run_verify(installed=True, isolated=True)
        self.assertEqual(result.returncode, 23)
        self.assertIn("ARG=--no-daemon", result.stdout)
        self.assertIn("ARG=--max-workers=2", result.stdout)
        self.assertIn("ARG=-Pkotlin.compiler.execution.strategy=in-process", result.stdout)
        self.assertIn("ARG=-Ptvhplayer.captureTests=exclude", result.stdout)
        self.assertNotIn("LAUNCH=", result.stdout)

    def test_other_hosts_keep_existing_behavior(self):
        result = self.run_verify()
        self.assertEqual(result.returncode, 23)
        self.assertNotIn("ARG=--no-daemon", result.stdout)
        self.assertNotIn("compiler.execution.strategy", result.stdout)

    def test_invalid_option_fails_before_launch(self):
        result = self.run_verify(installed=True, arguments=("--unknown",))
        self.assertEqual(result.returncode, 1)
        self.assertNotIn("LAUNCH=", result.stdout)


if __name__ == "__main__":
    unittest.main()
