from __future__ import annotations

import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import time
import unittest


ROOT = Path(__file__).resolve().parents[2]
PRODUCTION_LOCK_DIR = "gradle_lock_dir=/tmp/tvheadend-player-gradle-$gradle_lock_uid"


def install_wrapper(fixture: Path) -> Path:
    """Copies the wrapper with its host lock directory moved into the fixture."""
    text = (ROOT / "gradlew").read_text(encoding="utf-8")
    if PRODUCTION_LOCK_DIR not in text:
        raise AssertionError("gradlew no longer uses the shared host lock directory")
    wrapper = fixture / "gradlew"
    wrapper.write_text(
        text.replace(PRODUCTION_LOCK_DIR, f"gradle_lock_dir={fixture / 'locks'}"),
        encoding="utf-8",
    )
    wrapper.chmod(0o755)
    return wrapper


def install_java(fixture: Path, body: str) -> None:
    java = fixture / "jdk/bin/java"
    java.parent.mkdir(parents=True)
    java.write_text("#!/bin/sh\n" + body, encoding="utf-8")
    java.chmod(0o755)


def wait_for_lines(log: Path, count: int, timeout: float) -> list[str]:
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        lines = log.read_text(encoding="utf-8").splitlines() if log.exists() else []
        if len(lines) >= count:
            return lines
        time.sleep(0.02)
    return log.read_text(encoding="utf-8").splitlines() if log.exists() else []


@unittest.skipUnless(shutil.which("flock"), "flock is required")
class GradleLockTest(unittest.TestCase):
    def start(self, fixture: Path, wrapper: Path, name: str, slots: str | None = None) -> subprocess.Popen[bytes]:
        process = subprocess.Popen([str(wrapper), "help"], env=self.environment(fixture, name, slots))
        self.processes.append(process)
        return process

    def release(self, fixture: Path) -> None:
        (fixture / "release").touch()

    def stop_all(self, fixture: Path) -> None:
        self.release(fixture)
        for process in self.processes:
            if process.poll() is None:
                process.kill()
                process.wait(timeout=5)

    def setUp(self) -> None:
        self.processes: list[subprocess.Popen[bytes]] = []

    def environment(self, fixture: Path, name: str, slots: str | None = None) -> dict[str, str]:
        environment = {
            **os.environ,
            "JAVA_HOME": str(fixture / "jdk"),
            "GRADLE_LOCK_TEST_LOG": str(fixture / "starts.log"),
            "GRADLE_LOCK_TEST_RELEASE": str(fixture / "release"),
            "HOME": str(fixture / f"{name}-home"),
            "XDG_CACHE_HOME": str(fixture / f"{name}-cache"),
        }
        environment.pop("TVHPLAYER_GRADLE_SLOTS", None)
        if slots is not None:
            environment["TVHPLAYER_GRADLE_SLOTS"] = slots
        return environment

    def test_parallel_wrappers_serialize_before_java_starts(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            fixture = Path(directory)
            wrapper = install_wrapper(fixture)
            install_java(
                fixture,
                "printf 'started\\n' >> \"$GRADLE_LOCK_TEST_LOG\"\n"
                "sleep 0.4\n",
            )
            log = fixture / "starts.log"

            first = subprocess.Popen([str(wrapper), "help"], env=self.environment(fixture, "first"))
            deadline = time.monotonic() + 2
            while not log.exists() and time.monotonic() < deadline:
                time.sleep(0.01)
            self.assertTrue(log.exists(), "first wrapper did not reach Java")

            second = subprocess.Popen([str(wrapper), "help"], env=self.environment(fixture, "second"))
            time.sleep(0.1)
            self.assertEqual(["started"], log.read_text(encoding="utf-8").splitlines())

            self.assertEqual(0, first.wait(timeout=2))
            self.assertEqual(0, second.wait(timeout=2))
            self.assertEqual(
                ["started", "started"],
                log.read_text(encoding="utf-8").splitlines(),
            )

    def test_slots_admit_that_many_wrappers_and_queue_the_next(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            fixture = Path(directory)
            wrapper = install_wrapper(fixture)
            install_java(
                fixture,
                "printf 'started\\n' >> \"$GRADLE_LOCK_TEST_LOG\"\n"
                "while [ ! -e \"$GRADLE_LOCK_TEST_RELEASE\" ]; do sleep 0.02; done\n",
            )
            log = fixture / "starts.log"

            try:
                running = [self.start(fixture, wrapper, name, "2") for name in ("first", "second")]
                self.assertEqual(["started", "started"], wait_for_lines(log, 2, timeout=10))

                third = self.start(fixture, wrapper, "third", "2")
                time.sleep(1.5)
                self.assertEqual(2, len(log.read_text(encoding="utf-8").splitlines()))

                self.release(fixture)
                for process in [*running, third]:
                    self.assertEqual(0, process.wait(timeout=10))
                self.assertEqual(3, len(log.read_text(encoding="utf-8").splitlines()))
            finally:
                self.stop_all(fixture)

    def test_single_slot_wrapper_holds_the_first_slot(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            fixture = Path(directory)
            wrapper = install_wrapper(fixture)
            install_java(
                fixture,
                "printf 'started\\n' >> \"$GRADLE_LOCK_TEST_LOG\"\n"
                "while [ ! -e \"$GRADLE_LOCK_TEST_RELEASE\" ]; do sleep 0.02; done\n",
            )
            log = fixture / "starts.log"

            try:
                single = self.start(fixture, wrapper, "single")
                self.assertEqual(["started"], wait_for_lines(log, 1, timeout=10))

                slotted = self.start(fixture, wrapper, "slotted", "2")
                self.assertEqual(["started", "started"], wait_for_lines(log, 2, timeout=10))

                queued = self.start(fixture, wrapper, "queued")
                time.sleep(1.5)
                self.assertEqual(2, len(log.read_text(encoding="utf-8").splitlines()))

                self.release(fixture)
                for process in (single, slotted, queued):
                    self.assertEqual(0, process.wait(timeout=10))
                self.assertEqual(3, len(log.read_text(encoding="utf-8").splitlines()))
            finally:
                self.stop_all(fixture)

    def test_invalid_slot_count_fails_before_java_starts(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            fixture = Path(directory)
            wrapper = install_wrapper(fixture)
            install_java(fixture, "printf 'started\\n' >> \"$GRADLE_LOCK_TEST_LOG\"\n")

            for slots in ("0", "5", "two", "01"):
                with self.subTest(slots=slots):
                    result = subprocess.run(
                        [str(wrapper), "help"],
                        env=self.environment(fixture, "invalid", slots),
                        capture_output=True,
                        text=True,
                        timeout=10,
                    )
                    self.assertNotEqual(0, result.returncode)
                    self.assertIn("TVHPLAYER_GRADLE_SLOTS", result.stderr)
                    self.assertFalse((fixture / "starts.log").exists())


if __name__ == "__main__":
    unittest.main()
