from pathlib import Path
import os
import shutil
import signal
import subprocess
import tempfile
import time
import unittest


ROOT = Path(__file__).resolve().parents[2]


class GradleEnvironmentTest(unittest.TestCase):
    @unittest.skipUnless(shutil.which("timeout") and Path("/proc").is_dir(), "Linux coreutils")
    def test_interrupting_timeout_cleans_up_signal_resistant_descendant(self):
        with tempfile.TemporaryDirectory() as temporary:
            pid_file = Path(temporary) / "pid"
            child = subprocess.Popen(
                ["timeout", "--kill-after=0.2s", "60s", "sh", "-c",
                 'trap "" TERM; sleep 60 & echo $! > "$1"; wait', "sh", str(pid_file)],
                start_new_session=True,
            )
            try:
                deadline = time.monotonic() + 3
                while not pid_file.exists() or not pid_file.read_text().strip():
                    if time.monotonic() >= deadline:
                        self.fail("descendant did not start")
                    time.sleep(0.01)
                pid = int(pid_file.read_text())
                child.send_signal(signal.SIGTERM)
                self.assertEqual(-9, child.wait(timeout=5))
                deadline = time.monotonic() + 2
                while time.monotonic() < deadline:
                    status = Path(f"/proc/{pid}/stat")
                    if not status.exists() or status.read_text().rpartition(") ")[2].split()[0] == "Z":
                        break
                    time.sleep(0.01)
                else:
                    self.fail("interrupted timeout left its descendant running")
            finally:
                try:
                    os.killpg(child.pid, signal.SIGKILL)
                except ProcessLookupError:
                    pass
                child.wait()

    @unittest.skipUnless(shutil.which("timeout") and Path("/proc").is_dir(), "Linux coreutils")
    def test_standard_timeout_kills_signal_resistant_descendants(self):
        with tempfile.TemporaryDirectory() as temporary:
            pid_file = Path(temporary) / "pid"
            result = subprocess.run(
                ["timeout", "--kill-after=0.2s", "0.3s", "sh", "-c",
                 'trap "" TERM; sleep 60 & echo $! > "$1"; wait', "sh", str(pid_file)],
                capture_output=True, text=True, timeout=5,
            )
            self.assertEqual(-9, result.returncode)
            pid = int(pid_file.read_text())
            deadline = time.monotonic() + 2
            while time.monotonic() < deadline:
                status = Path(f"/proc/{pid}/stat")
                if not status.exists() or status.read_text().rpartition(") ")[2].split()[0] == "Z":
                    break
                time.sleep(0.01)
            else:
                os.kill(pid, 9)
                self.fail("timeout left its descendant running")

    @unittest.skipUnless(shutil.which("timeout"), "GNU timeout")
    def test_standard_timeout_preserves_command_failure_and_reports_timeout(self):
        for command, expected in ((["sh", "-c", "exit 23"], 23), (["sleep", "60"], 124)):
            with self.subTest(command=command):
                result = subprocess.run(
                    ["timeout", "--kill-after=0.2s", "0.1s", *command], timeout=5,
                )
                self.assertEqual(expected, result.returncode)

    def test_live_test_environment_is_opt_in_at_standard_entrypoint(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            shutil.copy2(ROOT / "gradlew", root / "gradlew")
            java = root / "bin/java"
            java.parent.mkdir()
            java.write_text(
                '#!/bin/sh\n'
                'test "${TVHEADEND_SOAK_CREDENTIALS_FILE+x}" = '
                '"${EXPECTED_PRESENT}" || exit 21\n'
                'test "${TVHEADEND_SOAK_NODVR_CREDENTIALS_FILE+x}" = '
                '"${EXPECTED_PRESENT}" || exit 22\n'
            )
            java.chmod(0o700)
            for opt_in in ("", "0", "1"):
                with self.subTest(opt_in=opt_in):
                    result = subprocess.run(
                        [str(root / "gradlew"), "--version"],
                        env={**os.environ, "JAVA_HOME": str(root),
                             "GRADLE_RUN_ALLOW_LIVE_TESTS": opt_in,
                             "TVHEADEND_SOAK_CREDENTIALS_FILE": "/not-a-secret/test.json",
                             "TVHEADEND_SOAK_NODVR_CREDENTIALS_FILE": "/not-a-secret/nodvr.json",
                             "EXPECTED_PRESENT": "x" if opt_in == "1" else ""},
                        capture_output=True, text=True, timeout=10,
                    )
                    self.assertEqual(0, result.returncode, result.stderr)


if __name__ == "__main__":
    unittest.main()
