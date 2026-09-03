from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]


class GradleConfigurationCacheTest(unittest.TestCase):
    def test_configuration_cache_is_enabled_and_fails_on_problems(self) -> None:
        properties = {}
        for line in (ROOT / "gradle.properties").read_text(encoding="utf-8").splitlines():
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            key, value = line.split("=", 1)
            properties[key.strip()] = value.strip().lower()

        self.assertEqual("true", properties.get("org.gradle.configuration-cache"))
        self.assertEqual("fail", properties.get("org.gradle.configuration-cache.problems"))
        self.assertNotEqual("true", properties.get("org.gradle.caching"))


if __name__ == "__main__":
    unittest.main()
