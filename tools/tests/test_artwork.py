import shutil
import struct
import subprocess
import tempfile
import unittest
import xml.etree.ElementTree as ElementTree
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]

GENERATED_PNG_DIMENSIONS = {
    "app/src/main/ic_launcher-playstore.png": (512, 512),
    "app/src/main/res/drawable/banner.png": (320, 180),
    "app/src/main/res/drawable/ic_launcher_background.png": (432, 432),
    "app/src/main/res/drawable/ic_launcher_foreground.png": (432, 432),
    "app/src/main/res/mipmap-hdpi/ic_launcher.png": (72, 72),
    "app/src/main/res/mipmap-hdpi/ic_launcher_round.png": (72, 72),
    "app/src/main/res/mipmap-mdpi/ic_launcher.png": (48, 48),
    "app/src/main/res/mipmap-mdpi/ic_launcher_round.png": (48, 48),
    "app/src/main/res/mipmap-xhdpi/ic_launcher.png": (96, 96),
    "app/src/main/res/mipmap-xhdpi/ic_launcher_round.png": (96, 96),
    "app/src/main/res/mipmap-xxhdpi/ic_launcher.png": (144, 144),
    "app/src/main/res/mipmap-xxhdpi/ic_launcher_round.png": (144, 144),
    "app/src/main/res/mipmap-xxxhdpi/ic_launcher.png": (192, 192),
    "app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.png": (192, 192),
    "artwork/github-social-preview.png": (1280, 640),
    "artwork/tvheadend-player-logo.png": (960, 300),
}

DETERMINISTIC_VECTOR_ARTWORK = (
    "app/src/main/res/drawable/ic_launcher_monochrome.xml",
    "artwork/tvheadend-player-logo.svg",
)


class ArtworkTest(unittest.TestCase):
    def test_diamond_identity_reaches_launcher_and_dark_splash(self):
        colors = self._color_resources()
        self.assertEqual("#00BCFA", colors["ic_launcher_background"])
        self.assertEqual("#0F1014", colors["splash_screen_background"])

        themes = (ROOT / "app/src/main/res/values/themes.xml").read_text()
        self.assertIn(
            "<item name=\"windowSplashScreenAnimatedIcon\">@drawable/ic_launcher_foreground</item>",
            themes,
        )
        self.assertIn(
            "<item name=\"windowSplashScreenBackground\">@color/splash_screen_background</item>",
            themes,
        )

        startup = (
            ROOT
            / "app/src/main/java/at/bernhardberger/tvhplayer/ui/startup/MainStartupScreen.kt"
        ).read_text()
        self.assertIn("painterResource(R.drawable.ic_launcher_foreground)", startup)

        readme = (ROOT / "README.md").read_text()
        self.assertIn("![TVHeadend Player](artwork/tvheadend-player-logo.png)", readme)
        identity = (ROOT / "docs/product-identity-plan.md").read_text()
        self.assertIn("The mark is a diamond aperture on a cyan field", identity)

    def test_renderer_regenerates_expected_surfaces(self):
        with tempfile.TemporaryDirectory() as directory:
            generated_root = Path(directory)
            (generated_root / "tools").mkdir()
            shutil.copy2(
                ROOT / "tools/RenderArtwork.java",
                generated_root / "tools/RenderArtwork.java",
            )
            subprocess.run(
                ["java", "tools/RenderArtwork.java"],
                cwd=generated_root,
                check=True,
                capture_output=True,
                text=True,
            )

            for relative_path, expected_dimensions in GENERATED_PNG_DIMENSIONS.items():
                with self.subTest(path=relative_path):
                    self.assertEqual(
                        expected_dimensions,
                        self._png_dimensions(ROOT / relative_path),
                    )
                    self.assertEqual(
                        expected_dimensions,
                        self._png_dimensions(generated_root / relative_path),
                    )

            for relative_path in DETERMINISTIC_VECTOR_ARTWORK:
                with self.subTest(path=relative_path):
                    self.assertEqual(
                        (ROOT / relative_path).read_text(),
                        (generated_root / relative_path).read_text(),
                    )

    @staticmethod
    def _color_resources():
        resources = ElementTree.parse(
            ROOT / "app/src/main/res/values/colors.xml"
        ).getroot()
        return {color.attrib["name"]: (color.text or "").strip() for color in resources}

    @staticmethod
    def _png_dimensions(path):
        data = path.read_bytes()
        if data[:8] != b"\x89PNG\r\n\x1a\n" or data[12:16] != b"IHDR":
            raise AssertionError(f"Not a PNG with an IHDR header: {path}")
        return struct.unpack(">II", data[16:24])


if __name__ == "__main__":
    unittest.main()
