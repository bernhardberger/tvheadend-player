import sys
import tempfile
import unittest
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPOSITORY_ROOT / "tools"))

from static_rules import find_static_rule_violations


class StaticRulesTest(unittest.TestCase):
    def setUp(self):
        self.temporary_directory = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary_directory.name)
        self.write(
            "app/src/main/java/at/bernhardberger/tvhplayer/ui/Theme.kt",
            "package at.bernhardberger.tvhplayer.ui\nval darkOnly = true\n",
        )
        self.write(
            "app/src/main/res/values/themes.xml",
            '<resources><style name="Theme.TVHeadendPlayer" '
            'parent="Theme.Material3.Dark.NoActionBar" /></resources>',
        )

    def tearDown(self):
        self.temporary_directory.cleanup()

    def test_accepts_current_static_contract(self):
        self.assertEqual([], find_static_rule_violations(self.root))

    def test_finds_connection_probe_symbols_anywhere_in_production(self):
        self.write(
            "app/src/main/java/example/MovedProbe.kt",
            "fun moved() = testConnection()\n",
        )

        violations = find_static_rule_violations(self.root)

        self.assertTrue(any("NoConnectionProbe" in violation for violation in violations))
        self.assertTrue(any("MovedProbe.kt:1" in violation for violation in violations))

    def test_finds_connection_probe_resource_references_in_kotlin(self):
        self.write(
            "app/src/main/java/example/MovedProbe.kt",
            "val label = R . string . test_connection\n",
        )

        violations = find_static_rule_violations(self.root)

        self.assertTrue(any("R.string.test_connection" in violation for violation in violations))

    def test_ignores_connection_probe_words_in_comments_and_strings(self):
        self.write(
            "app/src/main/java/example/Documentation.kt",
            '// testConnection() was removed\n'
            'val note = "ConnectionProbeUiState and R.string.test_connection"\n',
        )

        self.assertEqual([], find_static_rule_violations(self.root))

    def test_finds_connection_probe_calls_in_string_interpolation(self):
        self.write(
            "app/src/main/java/example/InterpolatedProbe.kt",
            'val normal = "${testConnection()}"\n'
            'val raw = """${testConnection()}"""\n',
        )

        violations = find_static_rule_violations(self.root)

        self.assertEqual(2, sum("uses testConnection" in item for item in violations))

    def test_finds_connection_probe_resource_declarations(self):
        self.write(
            "app/src/main/res/values/strings.xml",
            '<resources><string name="test_connection">Test</string></resources>',
        )

        violations = find_static_rule_violations(self.root)

        self.assertTrue(any("NoConnectionProbe" in violation for violation in violations))
        self.assertTrue(any("strings.xml" in violation for violation in violations))

    def test_ignores_connection_probe_words_in_xml_comments(self):
        self.write(
            "app/src/main/res/values/strings.xml",
            "<resources><!-- @string/test_connection was removed --></resources>",
        )

        self.assertEqual([], find_static_rule_violations(self.root))

    def test_finds_multiline_connection_probe_resource_references(self):
        self.write(
            "app/src/main/res/values/aliases.xml",
            '<resources><item type="string" name="probe_alias">\n'
            "    @string/test_connection\n"
            "</item></resources>",
        )

        violations = find_static_rule_violations(self.root)

        self.assertTrue(any("aliases.xml" in violation for violation in violations))

    def test_requires_dark_only_platform_theme(self):
        self.write(
            "app/src/main/res/values/themes.xml",
            '<resources><style name="Theme.TVHeadendPlayer" '
            'parent="Theme.Material3.DayNight.NoActionBar" /></resources>',
        )

        violations = find_static_rule_violations(self.root)

        self.assertTrue(any("DarkOnlyTheme" in violation for violation in violations))

    def test_finds_reachable_light_theme_identifiers(self):
        self.write(
            "app/src/main/java/at/bernhardberger/tvhplayer/ui/Theme.kt",
            "val colors = lightColorScheme()\n",
        )

        violations = find_static_rule_violations(self.root)

        self.assertTrue(any("DarkOnlyTheme" in violation for violation in violations))
        self.assertTrue(any("Theme.kt:1" in violation for violation in violations))

    def write(self, relative_path: str, content: str):
        destination = self.root / relative_path
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_text(content, encoding="utf-8")

    def test_transient_feedback_rejects_parallel_presentations_and_owners(self):
        for source in ("Toast.makeText()", "SnackbarHost()", "SnackbarHostState()",
                       "AppNoticePresentation()", "val center = NoticeCenter(clock, context)",
                       "val label = entry.subscriptionError ?. name",
                       'val label = "${entry.subscriptionError?.name}"'):
            with self.subTest(source=source):
                self.write("app/src/main/java/example/Screen.kt", source)
                self.assertTrue(any("TransientFeedback: app/src/main/java/example/Screen.kt:1" in item
                                    for item in find_static_rule_violations(self.root)))

    def test_transient_feedback_allows_owners_and_persistent_semantics(self):
        self.write("app/src/main/java/example/ui/notifications/Host.kt", "fun host() = AppNoticePresentation()\n")
        self.write("app/src/main/java/example/di/Module.kt", "val center = NoticeCenter(clock, context)\n")
        self.write("client/src/main/java/example/notices/NoticeCenter.kt", "class NoticeCenter(clock: Clock)\n")
        self.write("app/src/main/java/example/Screen.kt", '// Toast SnackbarHost\nval note = "AppNoticePresentation()"\nval liveRegion = polite\n')
        self.assertEqual([], find_static_rule_violations(self.root))

    def test_transient_feedback_also_checks_client_sources(self):
        self.write("client/src/main/java/example/Producer.kt", "val center = NoticeCenter(clock, context)\n")
        self.assertTrue(any("TransientFeedback" in item for item in find_static_rule_violations(self.root)))

    def test_shared_tabs_rejects_parallel_rows_and_motion(self):
        for source in (
            "import androidx.tv.material3.TabRow as NativeRow",
            "import androidx.tv.material3.Tab",
            "TabRow(selectedTabIndex = 0)", "Tab(selected = true)",
            "val motion = BrowseMotionPolicy.tabTransform(1)",
            "import at.bernhardberger.tvhplayer.ui.BrowseMotionPolicy.tabTransform as slide",
            "AnimatedContent(targetState = state.tab) {}",
            "AnimatedContent(selectedTab) {}",
            "import androidx.compose.animation.AnimatedContent as Animate\nAnimate(targetState = tab) {}",
        ):
            with self.subTest(source=source):
                self.write("app/src/main/java/example/Screen.kt", source)
                self.assertTrue(any("SharedTabs: app/src/main/java/example/Screen.kt:" in item
                                    for item in find_static_rule_violations(self.root)))

    def test_shared_tabs_allows_shared_owners_and_unrelated_motion(self):
        components = "app/src/main/java/at/bernhardberger/tvhplayer/ui/components/"
        self.write(components + "AppTabRow.kt",
                   "import androidx.tv.material3.TabRow\nimport androidx.tv.material3.Tab\nTabRow { Tab() }")
        self.write(components + "TabContent.kt",
                   "AnimatedContent(targetState = tab) { BrowseMotionPolicy.tabTransform(1) }")
        self.write("app/src/main/java/example/Screen.kt",
                   '// TabRow() AnimatedContent(tab)\nval note = "Tab() BrowseMotionPolicy.tabTransform(1)"\n'
                   'AppTabRow {}\nTabContent()\nAnimatedContent(targetState = page) {}')
        self.assertEqual([], find_static_rule_violations(self.root))


if __name__ == "__main__":
    unittest.main()
