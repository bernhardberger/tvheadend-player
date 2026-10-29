#!/usr/bin/env python3
"""Package local Play candidates from production-composable offline captures."""
import base64
import hashlib
import html
import json
from pathlib import Path
import shutil
import subprocess
import xml.etree.ElementTree as ET

from PIL import Image, ImageChops, ImageDraw, ImageFont, ImageOps
from PIL.PngImagePlugin import PngInfo

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "captures/play-store/round1"
SOURCE = ROOT / "app/build/outputs/presentation"
FONT = ROOT / "artwork/fonts/Outfit-550.ttf"
FIELD, TEXT, CYAN, ORANGE = "#0F1014", "#E3E3E8", "#00BCFA", "#FA7F00"
SCENES = [
    ("channels", "Your channels, at a glance", "ChannelsLayoutCaptureTest", "presentation"),
    ("guide", "See what’s on next", "GuideVisualLayoutTest", "presentation"),
    ("player", "Live TV, within reach", "PlayerChromeCaptureTest", "presentation"),
    ("player-rail", "Browse while you watch", "PlayerChromeCaptureTest", "presentationRail"),
    ("details", "Watch or record", "ProgrammeDetailsCaptureTest", "presentation"),
    ("recordings-folders", "Your recording library", "RecordingsVisualLayoutTest", "presentationFolders"),
    ("recordings", "Pick up where you left off", "RecordingsVisualLayoutTest", "presentation"),
    ("player-schedule", "A channel’s day, beside the picture", "ChannelScheduleCaptureTest", "presentation"),
]
ALT = {
    "channels": "Channels with Ridge Earth focused and fictional programs in a picture row.",
    "guide": "Program guide with Ridge Light focused, a live time marker and upcoming recordings.",
    "player": "Player controls over a mountain scene, with Pause focused and the live timeline visible.",
    "player-rail": "Channel row over the playing picture, with current program information.",
    "details": "Ridge Light program details with Watch, Record and Show in Guide actions.",
    "recordings-folders": "Recording archive with Documentaries focused and its contents previewed.",
    "recordings": "Ridge Light recording selected with its artwork and saved viewing progress.",
    "player-schedule": "Ridge Earth schedule beside a mountain scene, with Today and Ridge Light selected.",
}
RULES = [
    ("Preview assets: dimensions, formats, captions, banner", "https://support.google.com/googleplay/android-developer/answer/9866151?hl=en"),
    ("TV quality: TV-G4 unaltered screenshot", "https://developer.android.com/docs/quality-guidelines/tv-app-quality#TV-G4"),
    ("Icon: full square, sRGB, no outer shadow or baked mask", "https://developer.android.com/distribute/google-play/resources/icon-design-specifications"),
    ("Metadata: honest and relevant claims", "https://support.google.com/googleplay/android-developer/answer/9898842?hl=en"),
]


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def text(image, xy, value, size, fill=TEXT):
    ImageDraw.Draw(image).text(xy, value, font=ImageFont.truetype(str(FONT), size), fill=fill, anchor="lt")


def save(image, relative):
    path = OUT / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    info = PngInfo()
    info.add(b"sRGB", b"\x00")
    image.save(path, pnginfo=info, optimize=True)
    return path


def source_image(name):
    path = SOURCE / f"{name}.png"
    with Image.open(path) as image:
        if image.size != (1920, 1080):
            raise ValueError(f"{path}: expected 1920x1080, got {image.size}")
        if "A" in image.getbands() and image.getchannel("A").getextrema() != (255, 255):
            raise ValueError(f"{path}: non-opaque capture")
        return image.convert("RGB")


def test_evidence():
    """Reject missing/skipped/failed scenes, including the captureTests=only trap."""
    result = []
    directory = ROOT / "app/build/test-results/testPlayDebugUnitTest"
    for name, _, cls, method in SCENES:
        paths = list(directory.glob(f"TEST-*.{cls}.xml"))
        if len(paths) != 1:
            raise ValueError(f"Missing or ambiguous result for {cls}; rerun captures with captureTests=all")
        tree = ET.parse(paths[0]).getroot()
        cases = [case for case in tree.findall("testcase") if case.get("name") == method]
        if len(cases) != 1 or any(cases[0].find(tag) is not None for tag in ("failure", "error", "skipped")):
            raise ValueError(f"No passing result for {cls}.{method}")
        if (SOURCE / f"{name}.png").stat().st_mtime > paths[0].stat().st_mtime:
            raise ValueError(f"{name}: capture is newer than its test result")
        result.append({"scene": name, "test": f"{tree.get('name')}.{method}",
                       "result": str(paths[0].relative_to(ROOT)), "result_sha256": sha(paths[0]),
                       "source": str((SOURCE / f"{name}.png").relative_to(ROOT)),
                       "source_sha256": sha(SOURCE / f"{name}.png"), "alt": ALT[name]})
    return result


def features():
    # A: editorial landscape; supporting fixture imagery, not a fake app screen.
    still = ROOT / "app/src/test/resources/fixture-art/art/still-ridge-light.jpg"
    landscape = ImageOps.fit(Image.open(still).convert("RGB"), (1024, 500))
    scrim = Image.new("RGBA", landscape.size)
    draw = ImageDraw.Draw(scrim)
    for x in range(1024):
        draw.line((x, 0, x, 500), fill=(9, 20, 31, round(208 - 150 * x / 1023)))
    landscape = Image.alpha_composite(landscape.convert("RGBA"), scrim).convert("RGB")
    text(landscape, (104, 134), "Tvheadend", 57)
    text(landscape, (104, 198), "Player", 57, ORANGE)
    text(landscape, (107, 290), "A Tvheadend client for Android TV,", 25)
    text(landscape, (107, 325), "built around the remote.", 25)
    save(landscape, "feature/a-landscape.png")
    # B: pure typography; cyan ground, no generated program imagery.
    typographic = Image.new("RGB", (1024, 500), CYAN)
    d = ImageDraw.Draw(typographic)
    d.polygon([(815, 0), (1024, 0), (1024, 500), (630, 500)], fill="#079CCF")
    text(typographic, (104, 115), "Tvheadend Player", 62, FIELD)
    text(typographic, (108, 217), "A Tvheadend client for Android TV,", 29, FIELD)
    text(typographic, (108, 260), "built around the remote.", 29, FIELD)
    d.rounded_rectangle((108, 340, 220, 348), radius=4, fill=FIELD)
    save(typographic, "feature/b-type.png")
    # C: entire actual UI, uniformly downscaled, with no device frame.
    product = Image.new("RGB", (1024, 500), "#123B50")
    ImageDraw.Draw(product).polygon([(760, 0), (1024, 0), (1024, 500), (580, 500)], fill="#174C65")
    text(product, (82, 136), "Tvheadend", 39)
    text(product, (82, 184), "Player", 39, ORANGE)
    text(product, (84, 259), "A Tvheadend client", 21)
    text(product, (84, 289), "for Android TV,", 21)
    text(product, (84, 319), "built around the remote.", 21)
    product.paste(source_image("channels").resize((544, 306), Image.Resampling.LANCZOS), (398, 97))
    save(product, "feature/c-product.png")


def preview(path):
    with Image.open(path) as image:
        if image.width < 960:
            canvas = Image.new("RGB", (960, 540), FIELD)
            canvas.paste(image.convert("RGB"), ((960 - image.width) // 2, (540 - image.height) // 2))
            return canvas
        return image.resize((960, round(image.height * 960 / image.width)), Image.Resampling.LANCZOS)


def figure(relative, caption, alt):
    encoded = base64.b64encode((OUT / "previews" / relative).read_bytes()).decode("ascii")
    return f'<figure><img loading="lazy" src="data:image/png;base64,{encoded}" alt="{html.escape(alt)}"><figcaption>{html.escape(caption)}</figcaption></figure>'


def review():
    comparisons = []
    for i, (name, caption, _, _) in enumerate(SCENES, 1):
        filename = f"{i:02}-{name}.png"
        comparisons.append(f'<section><h3>{i:02} · {html.escape(caption)}</h3><div class="pair">' +
                           figure(f"plain/{filename}", "Plain · full frame, original RGB pixels", ALT[name]) +
                           figure(f"captioned/{filename}", "Captioned · whole UI scaled to 1600×900", ALT[name]) + '</div></section>')
    graphics = [
        figure("feature/a-landscape.png", "A · Landscape — recommended. A viewing-first image and readable brand, without duplicating the app icon.", "Tvheadend Player wordmark over a mountain landscape; built around the remote."),
        figure("feature/b-type.png", "B · Typography — strongest small-size readability; no generated art. Less specific to watching TV.", "Tvheadend Player and its remote-first tagline on a cyan field."),
        figure("feature/c-product.png", "C · Product — real UI, but its small interface competes with the listing screenshots.", "Tvheadend Player beside its Channels interface on a blue field."),
    ]
    rules = ''.join(f'<li><a href="{url}">{label}</a></li>' for label, url in RULES)
    body = f'''<!doctype html><html lang="en-US"><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><title>Play Store assets · Round 1</title>
<style>body{{margin:32px auto;padding:0 20px;max-width:1400px;background:{FIELD};color:{TEXT};font:16px/1.6 system-ui}}h1,h2,h3{{line-height:1.2}}h1{{font-size:2.2rem}}h2{{margin-top:3rem}}p,figcaption{{max-width:85ch;color:#BAC4CC}}a{{color:#6CD5FA}}img{{display:block;width:100%;height:auto}}figure{{margin:0 0 24px}}figcaption{{margin-top:8px}}.pair{{display:grid;grid-template-columns:1fr 1fr;gap:20px}}.features{{max-width:960px}}section{{margin:32px 0}}.note{{padding:20px;background:#1C252C;border-radius:12px}}@media(max-width:900px){{.pair{{grid-template-columns:1fr}}}}code{{overflow-wrap:anywhere}}</style>
<h1>Tvheadend Player · Play Store candidates</h1><p>Round 1 · local review · en-US · 1920×1080 TV frames · no upload</p>
<div class="note"><strong>Recommendation: plain screenshots + feature A + the current icon.</strong>
<p>The eight-frame story moves from browsing to the guide, watching, switching channels, choosing a program, and recordings, then the channel schedule. Plain captures preserve TV text size. The captioned alternative adds a 160px header (14.8% of the canvas), shrinks the whole UI without cropping and loses detail at listing size. Do not replace every plain frame with a captioned one.</p></div>
<h2>TV screenshot comparison</h2><p>Each column is a complete alternative set, not sixteen uploads. Full files live in <code>plain/</code> and <code>captioned/</code>. Previews are 960px wide. No retouching inside the app frames.</p>
{''.join(comparisons)}
<h2>Feature graphic · three directions</h2><p>1024×500 RGB PNG. Essential copy stays inside generous margins. Arbitrary crops cannot be guaranteed. These are promotional compositions, not unaltered screenshots.</p><div class="features">{''.join(graphics)}</div>
<h2>Icon and TV banner</h2><div class="pair">{figure('icon/current-rgba.png', 'Keep the current mark and scale. Packaging fix only: 512×512 RGBA instead of RGB, with an opaque full-square field. No mask or shadow baked in.', 'Cyan diamond aperture and orange play symbol on charcoal.')}{figure('banner/current.png', 'Existing 1280×720 RGB TV banner, copied byte-for-byte. Name is readable; no redesign needed.', 'Tvheadend Player name beside its cyan and orange mark.')}</div>
<h2>Evidence and owner decisions</h2><ul>
<li>These are production-composable offline captures at the recorded source revision, not device or decoded-video evidence. Plain assets preserve every RGB pixel; only opaque alpha is removed. Google’s TV-G4 wording does not explicitly settle acceptance of offline fixture captures. Confirm this before submission; no compliance approval is claimed.</li>
<li>All visible channel names, program art, logos and disk figures are fictional fixtures. The channel-art badge/title collision is absent in the selected frames. Recordings’ parent-list peeking remains the accepted behavior.</li>
<li>Grok Imagine–derived program art: all selected screenshots except the folders-only archive; feature A and C. Feature B, icon and banner do not use it. See the fixture pack README for provenance; owner decides attribution before publishing.</li>
<li>Choose plain versus a mixed captioned set; choose feature A/B/C; approve the unchanged icon with RGBA packaging. No assets go into tracked artwork/play until that choice.</li>
</ul><h2>Rules checked · October 29, 2026</h2><ul>{rules}</ul><p>Up to eight TV screenshots; at least one unaltered high-resolution current-experience frame. RGB screenshots, feature and banner; RGBA icon ≤1 MB. Captions under 20%; no rankings, prices, calls to install, device frames or third-party brand marks. Feature files also meet the handoff’s 15 MB ceiling (the preview-assets page does not state that ceiling).</p>
<p>See <code>manifest.json</code> for source commit, tests, input and output SHA-256 values, modes and dimensions. Reproduce with <code>docs/presentation-captures.md</code>. This review embeds every image; it needs no relative-image loading or network.</p></html>'''
    (OUT / "review.html").write_text(body, encoding="utf-8")


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    evidence = test_evidence()
    for i, (name, caption, _, _) in enumerate(SCENES, 1):
        image = source_image(name)
        filename = f"{i:02}-{name}.png"
        path = save(image, f"plain/{filename}")
        with Image.open(path) as encoded:
            if ImageChops.difference(image, encoded).getbbox():
                raise ValueError(f"Changed pixels in {path}")
        composed = Image.new("RGB", (1920, 1080), FIELD)
        text(composed, (160, 49), caption, 52)
        ImageDraw.Draw(composed).line((160, 132, 1760, 132), fill=CYAN, width=3)
        composed.paste(image.resize((1600, 900), Image.Resampling.LANCZOS), (160, 168))
        save(composed, f"captioned/{filename}")
    features()
    save(Image.open(ROOT / "app/src/main/ic_launcher-playstore.png").convert("RGBA"), "icon/current-rgba.png")
    (OUT / "banner").mkdir(exist_ok=True)
    shutil.copyfile(ROOT / "artwork/tvheadend-player-banner@4x.png", OUT / "banner/current.png")
    for group in ("plain", "captioned", "feature", "icon", "banner"):
        for path in (OUT / group).glob("*.png"):
            save(preview(path), "previews/" + str(path.relative_to(OUT)))
    review()
    inputs = [FONT, ROOT / "tools/prepare-play-assets.py", ROOT / "tools/RenderArtwork.java",
              ROOT / "app/src/main/ic_launcher-playstore.png", ROOT / "artwork/tvheadend-player-banner@4x.png"]
    inputs.extend(sorted((ROOT / "app/src/test/resources/fixture-art").rglob("*")))
    manifest = {
        "source_commit": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip(),
        "tracked_diff_sha256": hashlib.sha256(subprocess.check_output(["git", "diff", "HEAD"], cwd=ROOT)).hexdigest(),
        "locale": "en-US", "font_scale": 1.0, "timezone": "UTC", "canvas_dp": [960, 540],
        "test_task": ":app:testPlayDebugUnitTest", "capture_lane": "all", "tests": evidence,
        "limits": "Offline composition evidence only; not a device capture or Google acceptance. Run tests immediately before packaging; XML checks cannot establish source freshness.",
        "inputs": {str(p.relative_to(ROOT)): sha(p) for p in inputs if p.is_file()},
        "files": {},
    }
    for path in sorted(OUT.rglob("*")):
        if not path.is_file() or path.name == "manifest.json":
            continue
        item = {"sha256": sha(path), "bytes": path.stat().st_size}
        if path.suffix == ".png":
            with Image.open(path) as image:
                item.update(pixels=list(image.size), mode=image.mode)
        manifest["files"][str(path.relative_to(OUT))] = item
    (OUT / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
    print(OUT / "review.html")


if __name__ == "__main__":
    main()
