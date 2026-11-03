#!/usr/bin/env python3
"""Package the Play Store set from freshly rendered store capture tests."""
import io
from pathlib import Path
import shutil
import xml.etree.ElementTree as ET

import cairosvg
from PIL import Image, ImageDraw, ImageFilter, ImageFont, ImageStat
from PIL.PngImagePlugin import PngInfo

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "app/src/play/play/listings/en-US/graphics"
SOURCE = ROOT / "app/build/outputs/presentation"
FONT = ROOT / "artwork/fonts/Outfit-550.ttf"
MARK = ROOT / "artwork/tvheadend-player-symbol.svg"
SCENES = (
    ("store-home-onnow.png", "01.png"),
    ("store-channels.png", "02.png"),
    ("store-guide.png", "03.png"),
    ("store-player.png", "04.png"),
    ("store-recording.png", "05.png"),
    ("store-details.png", "06.png"),
    ("store-schedule.png", "07.png"),
)


def opaque(image, path):
    if image.convert("RGBA").getchannel("A").getextrema() != (255, 255):
        raise ValueError(f"Non-opaque image: {path}")


def save(image, relative):
    path = OUT / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    info = PngInfo()
    info.add(b"sRGB", b"\x00")
    image.save(path, pnginfo=info, optimize=True)


def text(image, xy, value, size, fill="#E3E3E8"):
    ImageDraw.Draw(image).text(xy, value, font=ImageFont.truetype(str(FONT), size),
                               fill=fill, anchor="lt")


def mark(image, xy, size):
    # Use the canonical symbol without its background or outer padding.
    svg = ET.fromstring(MARK.read_text())
    for child in list(svg):
        if child.tag.endswith("rect"):
            svg.remove(child)
    svg.set("viewBox", "72 72 368 368")
    png = cairosvg.svg2png(bytestring=ET.tostring(svg), output_width=size * 3, output_height=size * 3)
    logo = Image.open(io.BytesIO(png)).convert("RGBA").resize((size, size), Image.Resampling.LANCZOS)
    image.paste(logo, xy, logo)


def ambient(image, source, xy, width):
    """Diffuse stadium-edge colors, excluding the lower program-card rail."""
    x, y = xy
    height = round(width * 9 / 16)
    reach = 44
    stadium = source.crop((0, 0, source.width, source.height * 2 // 3))
    screen = stadium.resize((width, height), Image.Resampling.LANCZOS)
    spill = Image.new("RGB", (width + 2 * reach, height + 2 * reach))
    spill.paste(screen, (reach, reach))
    for box, size, position in (
        ((0, 0, width, 8), (width, reach), (reach, 0)),
        ((0, height - 8, width, height), (width, reach), (reach, height + reach)),
        ((0, 0, 8, height), (reach, height), (0, reach)),
        ((width - 8, 0, width, height), (reach, height), (width + reach, reach)),
        ((0, 0, 8, 8), (reach, reach), (0, 0)),
        ((width - 8, 0, width, 8), (reach, reach), (width + reach, 0)),
        ((0, height - 8, 8, height), (reach, reach), (0, height + reach)),
        ((width - 8, height - 8, width, height), (reach, reach), (width + reach, height + reach)),
    ):
        spill.paste(screen.crop(box).resize(size, Image.Resampling.BILINEAR), position)
    layer = Image.new("RGBA", image.size)
    spill = spill.convert("RGBA")
    spill.putalpha(110)
    layer.paste(spill, (x - reach, y - reach))
    blurred = layer.filter(ImageFilter.GaussianBlur(28))
    image.paste(blurred, (0, 0), blurred)


def feature():
    with Image.open(OUT / "tv-screenshots/02.png") as channels:
        stadium_mean = ImageStat.Stat(channels.crop((0, 0, 1920, 720))).mean
        field = tuple(round(channel * 18 / max(stadium_mean)) for channel in stadium_mean)
        image = Image.new("RGB", (1024, 500), field)
        ambient(image, channels, (406, 80), 566)
        mark(image, (54, 80), 51)
        text(image, (122, 80), "Tvheadend", 26)
        text(image, (122, 111), "Player", 26, "#FA7F00")
        text(image, (52, 184), "Built around", 44)
        text(image, (52, 236), "the remote.", 44)
        text(image, (55, 315), "A Tvheadend client", 22)
        text(image, (55, 345), "for Android TV.", 22)
        text(image, (55, 391), "Live TV · Guide · Recordings", 18, "#B7C4C8")
        x, y, width = 406, 80, 566
        height = round(width * 9 / 16)
        draw = ImageDraw.Draw(image)
        draw.rounded_rectangle((x - 4, y - 4, x + width + 4, y + height + 6), radius=5,
                               fill="#111418", outline="#464F5B", width=1)
        image.paste(channels.resize((width, height), Image.Resampling.LANCZOS), (x, y))
        draw.line((x + 6, y + height + 4, x + width - 6, y + height + 4), fill="#3B424C", width=1)
        save(image, "feature-graphic/01.png")


def main():
    # Validate inputs before replacing the five graphics directories. Leave
    # other graphics, locales, release notes and listing text alone.
    for source, _ in SCENES:
        path = SOURCE / source
        if not path.is_file():
            raise ValueError(f"Missing capture: {path}; run the Play Store tests first")
        with Image.open(path) as image:
            if image.size != (1920, 1080):
                raise ValueError(f"Wrong capture size: {path}: {image.size}; expected 1920x1080")
            opaque(image, path)
    icon_path = ROOT / "app/src/main/ic_launcher-playstore.png"
    with Image.open(icon_path) as image:
        if image.size != (512, 512):
            raise ValueError(f"Wrong icon size: {icon_path}: {image.size}; expected 512x512")
        opaque(image, icon_path)
    for directory in ("icon", "feature-graphic", "tv-banner", "tv-screenshots", "large-tablet-screenshots"):
        path = OUT / directory
        if path.exists():
            shutil.rmtree(path)
        path.mkdir(parents=True)
    for source, target in SCENES:
        with Image.open(SOURCE / source) as image:
            save(image.convert("RGB"), f"tv-screenshots/{target}")
    for source, target in (("02.png", "01.png"), ("03.png", "02.png")):
        shutil.copyfile(OUT / "tv-screenshots" / source, OUT / "large-tablet-screenshots" / target)
    feature()
    with Image.open(icon_path) as image:
        save(image.convert("RGBA"), "icon/01.png")
    shutil.copyfile(ROOT / "artwork/tvheadend-player-banner@4x.png", OUT / "tv-banner/01.png")
    print(OUT)


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError) as error:
        raise SystemExit(str(error)) from error
