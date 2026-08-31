from __future__ import annotations

import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import time
import unittest


ROOT = Path(__file__).resolve().parents[2]


@unittest.skipUnless(shutil.which("flock"), "flock is required")
class GradleLockTest(unittest.TestCase):
    def test_parallel_wrappers_serialize_before_java_starts(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            fixture = Path(directory)
            wrapper = fixture / "gradlew"
            shutil.copy2(ROOT / "gradlew", wrapper)
            wrapper.chmod(0o755)

            java = fixture / "jdk/bin/java"
            java.parent.mkdir(parents=True)
            java.write_text(
                "#!/bin/sh\n"
                "printf 'started\\n' >> \"$GRADLE_LOCK_TEST_LOG\"\n"
                "sleep 0.4\n",
                encoding="utf-8",
            )
            java.chmod(0o755)

            log = fixture / "starts.log"
            environment = {
                **os.environ,
                "JAVA_HOME": str(fixture / "jdk"),
                "GRADLE_LOCK_TEST_LOG": str(log),
            }
            first_environment = {
                **environment,
                "HOME": str(fixture / "first-home"),
                "XDG_CACHE_HOME": str(fixture / "first-cache"),
            }
            second_environment = {
                **environment,
                "HOME": str(fixture / "second-home"),
                "XDG_CACHE_HOME": str(fixture / "second-cache"),
            }

            first = subprocess.Popen([str(wrapper), "help"], env=first_environment)
            deadline = time.monotonic() + 2
            while not log.exists() and time.monotonic() < deadline:
                time.sleep(0.01)
            self.assertTrue(log.exists(), "first wrapper did not reach Java")

            second = subprocess.Popen([str(wrapper), "help"], env=second_environment)
            time.sleep(0.1)
            self.assertEqual(["started"], log.read_text(encoding="utf-8").splitlines())

            self.assertEqual(0, first.wait(timeout=2))
            self.assertEqual(0, second.wait(timeout=2))
            self.assertEqual(
                ["started", "started"],
                log.read_text(encoding="utf-8").splitlines(),
            )


if __name__ == "__main__":
    unittest.main()
