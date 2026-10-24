from __future__ import annotations

import os
import re
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
# Explicit ASCII ranges: Python's \d also matches non-ASCII digits such as "٥".
RELEASE = re.compile(r"[0-9]+\.[0-9]+\.[0-9]+")
DEVELOPMENT = re.compile(r"[0-9]+\.[0-9]+\.[0-9]+-[0-9]{8}\.[0-9]{6}-[0-9]+")
VERSION_TABLE = (
    ("0.35.0", True, False),
    ("0.36.0-SNAPSHOT", False, False),
    ("0.36.0-20261024.143000-1", False, True),
    ("0.36.0-rc1", False, False),
    ("0.36", False, False),
    ("0.36.0-20261024.1430-1", False, False),
    ("0.3\u0665.0", False, False),
    ("\uff10.35.0", False, False),
    ("0.36.0-2026102\u0664.143000-1", False, False),
    ("0.36.0-20261024.143000-\u0661", False, False),
)


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
        self.assertEqual(gradle, rf"{RELEASE.pattern}(-[0-9]{{8}}\.[0-9]{{6}}-[0-9]+)?")
        bash = pinned(
            "tools/prepare-release",
            r'^if \[\[ ! "\$catalog_sdk_version" =~ (\S+) \]\]; then$',
        )
        self.assertEqual(bash, rf"^{RELEASE.pattern}$")

        gradle_rule = re.compile(gradle)
        for version, released, development in VERSION_TABLE:
            with self.subTest(version=version):
                self.assertEqual(bool(RELEASE.fullmatch(version)), released)
                self.assertEqual(bool(DEVELOPMENT.fullmatch(version)), development)
                self.assertEqual(bool(gradle_rule.fullmatch(version)), released or development)
                # The bash rule runs as tools/prepare-release runs it, in a UTF-8 locale.
                result = subprocess.run(
                    ["bash", "-c", f'[[ "$1" =~ {bash} ]]', "rule", version],
                    env={"PATH": "/usr/bin:/bin", "LC_ALL": "C.UTF-8"},
                    capture_output=True,
                    timeout=10,
                )
                self.assertEqual(result.returncode == 0, released)

    @unittest.skipUnless(shutil.which("java"), "the Gradle rule needs a JDK to run")
    def test_gradle_rule_matches_as_java_regex(self) -> None:
        # Kotlin's Regex.matches is java.util.regex.Pattern.matcher(input).matches().
        gradle = pinned("app/build.gradle.kts", r'Regex\("""([^"]+)"""\)\.matches\(sdkVersion\)')
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / "Rule.java"
            source.write_text(
                "public class Rule { public static void main(String[] arguments) {"
                " java.util.regex.Pattern rule = java.util.regex.Pattern.compile(arguments[0]);"
                " for (int index = 1; index < arguments.length; index++)"
                " System.out.println(rule.matcher(arguments[index]).matches()); } }",
                encoding="utf-8",
            )
            versions = [version for version, _, _ in VERSION_TABLE]
            result = subprocess.run(
                ["java", str(source), gradle, *versions],
                env={**os.environ, "LC_ALL": "C.UTF-8"},
                capture_output=True,
                text=True,
                timeout=60,
                check=True,
            )
        self.assertEqual(
            result.stdout.split(),
            [str(released or development).lower() for _, released, development in VERSION_TABLE],
        )

if __name__ == "__main__":
    unittest.main()
