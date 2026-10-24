from __future__ import annotations

import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
RELEASE = re.compile(r"\d+\.\d+\.\d+")
DEVELOPMENT = re.compile(r"\d+\.\d+\.\d+-\d{8}\.\d{6}-\d+")


def pinned(path: str, pattern: str) -> str:
    text = (ROOT / path).read_text(encoding="utf-8")
    matches = re.findall(pattern, text, flags=re.MULTILINE)
    if len(matches) != 1:
        raise AssertionError(f"{path}: expected one match for {pattern!r}, found {matches}")
    return matches[0]


class SdkVersionPinsTest(unittest.TestCase):
    def test_release_pins_agree_and_staged_catalog_cannot_pass_public_gate(self) -> None:
        catalog = pinned("gradle/libs.versions.toml", r'^tvheadend-sdk = "([^"]+)"$')
        public = pinned("tools/check-native-libs", r'^SDK_VERSION = "([^"]+)"$')
        release = pinned("tools/prepare-release", r'^SDK_VERSION="([^"]+)"$')

        self.assertEqual(
            pinned(
                "tools/check-native-libs",
                r'^SDK_COORDINATE = "at\.bernhardberger\.tvheadend:sdk-media3:([^"]+)"$',
            ),
            public,
        )
        # tools/prepare-release keeps the last released SDK and its source commit.
        self.assertRegex(release, rf"^{RELEASE.pattern}$")
        if RELEASE.fullmatch(public):
            self.assertEqual(public, release, "a released SDK pin must match the release pins")
        else:
            self.assertRegex(public, rf"^{DEVELOPMENT.pattern}$")
        if catalog != public:
            self.assertTrue(catalog.endswith("-SNAPSHOT"), "a published SDK requires updated pins")
            gate = (ROOT / "app/build.gradle.kts").read_text()
            self.assertIn("check(!stagedSdkSubstitution)", gate)
            checker = (ROOT / "tools/check-native-libs").read_text()
            self.assertIn("catalog_version != pinned_version", checker)

    def test_every_gate_uses_the_same_development_version_rule(self) -> None:
        self.assertEqual(
            pinned("tools/check-native-libs", r'^RELEASE_SDK_VERSION = re\.compile\(r"([^"]+)"\)$'),
            RELEASE.pattern,
        )
        self.assertEqual(
            pinned(
                "tools/check-native-libs",
                r'^DEVELOPMENT_SDK_VERSION = re\.compile\(r"([^"]+)"\)$',
            ),
            DEVELOPMENT.pattern,
        )
        gradle = pinned("app/build.gradle.kts", r'Regex\("""([^"]+)"""\)\.matches\(sdkVersion\)')
        self.assertEqual(gradle, r"\d+\.\d+\.\d+(-\d{8}\.\d{6}-\d+)?")
        self.assertIn(
            r'"$catalog_sdk_version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$',
            (ROOT / "tools/prepare-release").read_text(encoding="utf-8"),
        )

        gradle_rule = re.compile(gradle)
        for version, released, development in (
            ("0.35.0", True, False),
            ("0.36.0-SNAPSHOT", False, False),
            ("0.36.0-20261024.143000-1", False, True),
            ("0.36.0-rc1", False, False),
            ("0.36", False, False),
            ("0.36.0-20261024.1430-1", False, False),
        ):
            with self.subTest(version=version):
                self.assertEqual(bool(RELEASE.fullmatch(version)), released)
                self.assertEqual(bool(DEVELOPMENT.fullmatch(version)), development)
                self.assertEqual(bool(gradle_rule.fullmatch(version)), released or development)


if __name__ == "__main__":
    unittest.main()
