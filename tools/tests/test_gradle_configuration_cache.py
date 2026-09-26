from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[2]


class GradleConfigurationCacheTest(unittest.TestCase):
    def test_configuration_cache_is_enabled_and_fails_on_problems(self) -> None:
        properties = _properties()

        self.assertEqual("true", properties.get("org.gradle.configuration-cache", "").lower())
        self.assertEqual("fail", properties.get("org.gradle.configuration-cache.problems", "").lower())
        self.assertEqual("true", properties.get("org.gradle.caching", "").lower())

    def test_idle_daemons_release_memory_within_ten_minutes(self) -> None:
        properties = _properties()
        self.assertLessEqual(int(properties["org.gradle.daemon.idletimeout"]), 10 * 60 * 1000)
        kotlin_idle = re.search(
            r"-Dkotlin\.daemon\.options=(?:\S*,)?autoshutdownIdleSeconds=(\d+)(?:[,\s]|$)",
            properties["org.gradle.jvmargs"],
        )
        self.assertIsNotNone(kotlin_idle)
        self.assertLessEqual(int(kotlin_idle.group(1)), 10 * 60)

    def test_test_tasks_are_never_cached_and_time_out(self) -> None:
        # Tests read and write undeclared files, so a cache hit could skip evidence or reuse a
        # foreign worktree's result; a hung test must not hold the shared build lock.
        build = (ROOT / "build.gradle.kts").read_text(encoding="utf-8")
        tests = _block(_block(build, "subprojects {"), "tasks.withType<Test>().configureEach {")
        statements = [
            line.strip() for line in tests.splitlines() if line.strip() and not line.strip().startswith("//")
        ]

        guard = re.compile(r'outputs\.doNotCacheIf\("[^"]+"\) \{ true \}')
        self.assertTrue(any(guard.fullmatch(statement) for statement in statements), statements)
        self.assertIn("timeout.set(Duration.ofMinutes(15))", statements)


def _properties() -> dict[str, str]:
    properties = {}
    for line in (ROOT / "gradle.properties").read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        key, value = line.split("=", 1)
        properties[key.strip()] = value.strip()
    return properties


def _block(text: str, opening: str) -> str:
    """Returns the body of the brace block that starts with the given opening line."""
    start = text.index(opening) + len(opening)
    depth = 1
    for index in range(start, len(text)):
        if text[index] == "{":
            depth += 1
        elif text[index] == "}":
            depth -= 1
            if depth == 0:
                return text[start:index]
    raise AssertionError(f"unclosed block: {opening}")


if __name__ == "__main__":
    unittest.main()
