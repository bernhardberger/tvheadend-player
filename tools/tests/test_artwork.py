import hashlib
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
    "app/src/main/res/drawable-mdpi/banner.png": (160, 90),
    "app/src/main/res/drawable-hdpi/banner.png": (240, 135),
    "app/src/main/res/drawable-xhdpi/banner.png": (320, 180),
    "app/src/main/res/drawable-xxhdpi/banner.png": (480, 270),
    "app/src/main/res/drawable-xxxhdpi/banner.png": (640, 360),
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
    "artwork/tvheadend-player-logo@2x.png": (1920, 600),
    "artwork/tvheadend-player-banner.png": (320, 180),
    "artwork/tvheadend-player-banner@2x.png": (640, 360),
    "artwork/tvheadend-player-banner@4x.png": (1280, 720),
    "artwork/tvheadend-player-android-tv.png": (960, 300),
    "artwork/tvheadend-player-android-tv@2x.png": (1920, 600),
    "artwork/tvheadend-player-symbol.png": (512, 512),
    "artwork/tvheadend-player-symbol@2x.png": (1024, 1024),
}

DETERMINISTIC_VECTOR_ARTWORK = (
    "app/src/main/res/drawable/ic_launcher_monochrome.xml",
    "artwork/tvheadend-player-logo.svg",
    "artwork/tvheadend-player-banner.svg",
    "artwork/tvheadend-player-android-tv.svg",
    "artwork/tvheadend-player-symbol.svg",
    "artwork/github-social-preview.svg",
)


class ArtworkTest(unittest.TestCase):
    def test_dark_field_diamond_identity_reaches_launcher_and_splash(self):
        colors = self._color_resources()
        self.assertEqual("#0F1014", colors["ic_launcher_background"])
        self.assertEqual("#0F1014", colors["splash_screen_background"])

        logo_svg = (ROOT / "artwork/tvheadend-player-logo.svg").read_text()
        self.assertIn('<rect width="100%" height="100%" fill="#0F1014"/>', logo_svg)
        self.assertIn('<path fill="#00BCFA"', logo_svg)
        self.assertIn('<path fill="#171717"', logo_svg)
        self.assertNotIn("#0B1B2E", logo_svg.upper())

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
        self.assertIn("![Tvheadend Player](artwork/tvheadend-player-logo.png)", readme)
        identity = (ROOT / "docs/product-identity-plan.md").read_text()
        self.assertIn(
            "The mark is a cyan diamond aperture on a dark neutral field",
            identity,
        )

    def test_renderer_regenerates_expected_surfaces(self):
        with tempfile.TemporaryDirectory() as directory:
            generated_root = Path(directory)
            (generated_root / "tools").mkdir()
            shutil.copy2(
                ROOT / "tools/RenderArtwork.java",
                generated_root / "tools/RenderArtwork.java",
            )
            shutil.copytree(ROOT / "artwork/fonts", generated_root / "artwork/fonts")
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
                    self.assertEqual(
                        (ROOT / relative_path).read_bytes(),
                        (generated_root / relative_path).read_bytes(),
                    )

            for relative_path in DETERMINISTIC_VECTOR_ARTWORK:
                with self.subTest(path=relative_path):
                    self.assertEqual(
                        (ROOT / relative_path).read_text(),
                        (generated_root / relative_path).read_text(),
                    )

    def test_pinned_font_and_portable_outlined_exports(self):
        for name, digest in {
            "Outfit-variable.ttf": "fc7287273e66929776e2ba54f144fe699080bec29f61bf649d70d871468aeade",
            "Outfit-550.ttf": "727366fc010a90ad71c0f639ddc85336c175bb5074041311e2b863960d6ecf46",
        }.items():
            self.assertEqual(digest, hashlib.sha256((ROOT / "artwork/fonts" / name).read_bytes()).hexdigest())
        self.assertIn("SIL OPEN FONT LICENSE Version 1.1", (ROOT / "artwork/fonts/OFL.txt").read_text())
        for relative_path in DETERMINISTIC_VECTOR_ARTWORK[1:]:
            svg = ElementTree.parse(ROOT / relative_path).getroot()
            self.assertFalse(svg.findall(".//{http://www.w3.org/2000/svg}text"))
            self.assertFalse(svg.findall(".//{http://www.w3.org/2000/svg}image"))
            title = svg.findtext("{http://www.w3.org/2000/svg}title", default="")
            self.assertEqual("for Android TV" in title, "android-tv" in relative_path)

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
