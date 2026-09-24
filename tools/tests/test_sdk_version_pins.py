from __future__ import annotations

import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


def pinned(path: str, pattern: str) -> str:
    text = (ROOT / path).read_text(encoding="utf-8")
    matches = re.findall(pattern, text, flags=re.MULTILINE)
    if len(matches) != 1:
        raise AssertionError(f"{path}: expected one match for {pattern!r}, found {matches}")
    return matches[0]


class SdkVersionPinsTest(unittest.TestCase):
    def test_every_release_pin_names_the_catalog_sdk_version(self) -> None:
        catalog = pinned("gradle/libs.versions.toml", r'^tvheadend-sdk = "([^"]+)"$')

        self.assertEqual(
            {
                "check-native-libs SDK_VERSION": pinned(
                    "tools/check-native-libs", r'^SDK_VERSION = "([^"]+)"$'
                ),
                "check-native-libs SDK_COORDINATE": pinned(
                    "tools/check-native-libs",
                    r'^SDK_COORDINATE = "at\.bernhardberger\.tvheadend:sdk-media3:([^"]+)"$',
                ),
                "prepare-release SDK_VERSION": pinned(
                    "tools/prepare-release", r'^SDK_VERSION="([^"]+)"$'
                ),
                "app/build.gradle.kts public SDK check": pinned(
                    "app/build.gradle.kts", r'check\(sdkVersion == "([^"]+)"\)'
                ),
            },
            dict.fromkeys(
                [
                    "check-native-libs SDK_VERSION",
                    "check-native-libs SDK_COORDINATE",
                    "prepare-release SDK_VERSION",
                    "app/build.gradle.kts public SDK check",
                ],
                catalog,
            ),
        )


if __name__ == "__main__":
    unittest.main()
