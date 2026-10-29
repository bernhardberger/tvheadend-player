#!/usr/bin/env python3
"""Package round-three Play candidates, preserving the four approved round-two files."""
import base64
import hashlib
import html
import io
import json
from pathlib import Path
import shutil
import subprocess
import xml.etree.ElementTree as ET

import cairosvg
from PIL import Image, ImageDraw, ImageFilter, ImageFont
from PIL.PngImagePlugin import PngInfo

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "captures/play-store/round3"
BASELINE = ROOT / "captures/play-store/round2"
SOURCE = ROOT / "app/build/outputs/presentation"
FONT = ROOT / "artwork/fonts/Outfit-550.ttf"
MARK = ROOT / "artwork/tvheadend-player-symbol.svg"
FIELD, TEXT, CYAN, ORANGE = "#0F1014", "#E3E3E8", "#00BCFA", "#FA7F00"
RETAINED = ["01-channels", "02-guide", "03-player", "04-recording"]
NEW = [("details", "ProgrammeDetailsCaptureTest", "storeDetails"),
       ("schedule", "ChannelScheduleCaptureTest", "storeSchedule")]
FEATURES = [
    ("c1-floating", "C1 · Floating screen", "A thin, wall-mounted screen with a soft cool halo. The small mark anchors the left column; the three functions now sit directly under the product name."),
    ("c2-gallery", "C2 · Gallery", "A warm, light field and a graphite television on slender feet. The same live-match UI gives the frame and palette a fair comparison with C1."),
    ("c3-cinema", "C3 · Cinema", "A compact mark-and-name lockup, a remote-first headline and the concert player on a low-profile pedestal. The screen shows viewing rather than browsing."),
    ("c4-warm", "C4 · Warm title-led", "The owner’s blend: C1’s product-name hierarchy, copy and mark placement with C3’s concert picture, warm glow and low-profile television."),
    ("c5-remote-channels", "C5 · Remote + Channels", "The primary’s blend: C3’s left column and television unchanged, with C1’s complete Channels screen. The channel cards fit vertically inside the TV; there is no player-rail peek along its bottom edge."),
]
RULES = [
    ("Preview asset requirements", "https://support.google.com/googleplay/android-developer/answer/9866151?hl=en"),
    ("TV-G4", "https://developer.android.com/docs/quality-guidelines/tv-app-quality#TV-G4"),
    ("Metadata policy", "https://support.google.com/googleplay/android-developer/answer/9898842?hl=en"),
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


def copy_baseline(relative, target=None):
    source = BASELINE / relative
    record = json.loads((BASELINE / "manifest.json").read_text())["files"][relative]
    if sha(source) != record["sha256"]:
        raise ValueError(f"Changed round-two baseline: {source}")
    dest = OUT / (target or relative)
    dest.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(source, dest)
    return dest


def test_evidence():
    suites, scenes = {}, []
    for name, cls, method in NEW:
        paths = list((ROOT / "app/build/test-results/testPlayDebugUnitTest").glob(f"TEST-*.{cls}.xml"))
        if len(paths) != 1:
            raise ValueError(f"Missing or ambiguous result for {cls}")
        tree = ET.parse(paths[0]).getroot()
        cases = tree.findall("testcase")
        if not cases or any(c.find(t) is not None for c in cases for t in ("failure", "error", "skipped")):
            raise ValueError(f"{cls}: class failed or skipped tests")
        if not any(c.get("name") == method for c in cases):
            raise ValueError(f"No passing result for {cls}.{method}")
        source = SOURCE / f"store-{name}.png"
        if source.stat().st_mtime > paths[0].stat().st_mtime:
            raise ValueError(f"Capture newer than test result: {name}")
        suites[cls] = {"tests": len(cases), "result": str(paths[0].relative_to(ROOT)), "sha256": sha(paths[0])}
        scenes.append({"scene": name, "test": f"{tree.get('name')}.{method}",
                       "source": str(source.relative_to(ROOT)), "sha256": sha(source)})
    return suites, scenes


def mark(image, xy, size):
    # Canonical paths and colours; omit only the export's background and outer padding.
    svg = ET.fromstring(MARK.read_text())
    for child in list(svg):
        if child.tag.endswith("rect"):
            svg.remove(child)
    svg.set("viewBox", "72 72 368 368")
    png = cairosvg.svg2png(bytestring=ET.tostring(svg), output_width=size * 3, output_height=size * 3)
    logo = Image.open(io.BytesIO(png)).convert("RGBA").resize((size, size), Image.Resampling.LANCZOS)
    image.paste(logo, xy, logo)


def halo(image, box, color, blur):
    layer = Image.new("RGBA", image.size)
    ImageDraw.Draw(layer).ellipse(box, fill=color)
    blurred = layer.filter(ImageFilter.GaussianBlur(blur))
    image.paste(blurred, (0, 0), blurred)


def television(image, scene, xy, width, treatment):
    x, y = xy
    height = round(width * 9 / 16)
    d = ImageDraw.Draw(image)
    # Full, undistorted 16:9 production screenshot. Frames belong only to feature graphics.
    d.rounded_rectangle((x - 4, y - 4, x + width + 4, y + height + 6), radius=5,
                        fill="#111418", outline="#626971" if treatment == "gallery" else "#464F5B", width=1)
    with Image.open(OUT / f"plain/{scene}.png") as screen:
        image.paste(screen.resize((width, height), Image.Resampling.LANCZOS), (x, y))
    d.line((x + 6, y + height + 4, x + width - 6, y + height + 4), fill="#3B424C", width=1)
    bottom = y + height + 6
    if treatment == "gallery":
        for centre, end in ((x + 70, x + 45), (x + width - 70, x + width - 45)):
            d.line((centre, bottom, end, bottom + 20), fill="#202830", width=4)
            d.line((end - 9, bottom + 20, end + 9, bottom + 20), fill="#616B74", width=2)
    elif treatment == "cinema":
        centre = x + width // 2
        d.rectangle((centre - 6, bottom, centre + 6, bottom + 18), fill="#313741")
        d.polygon(((centre - 58, bottom + 18), (centre + 58, bottom + 18),
                   (centre + 72, bottom + 22), (centre - 72, bottom + 22)), fill="#555B64")


def features():
    a = Image.new("RGB", (1024, 500), FIELD)
    halo(a, (395, 58, 1030, 448), (19, 94, 133, 140), 55)
    mark(a, (54, 58), 62)
    text(a, (52, 153), "Tvheadend", 48)
    text(a, (52, 208), "Player", 48, ORANGE)
    text(a, (55, 276), "Live TV · Guide · Recordings", 18, "#B5C3CE")
    text(a, (55, 329), "A Tvheadend client", 22)
    text(a, (55, 359), "for Android TV,", 22)
    text(a, (55, 389), "built around the remote.", 22)
    television(a, "01-channels", (394, 101), 596, "floating")
    save(a, "feature/c1-floating.png")

    b = Image.new("RGB", (1024, 500), "#EDECE7")
    halo(b, (405, 428, 997, 457), (39, 48, 58, 100), 13)
    mark(b, (54, 58), 62)
    text(b, (52, 153), "Tvheadend", 48, "#142532")
    text(b, (52, 208), "Player", 48, "#142532")
    text(b, (55, 276), "Live TV · Guide · Recordings", 18, "#485B67")
    text(b, (55, 329), "A Tvheadend client", 22, "#273B48")
    text(b, (55, 359), "for Android TV,", 22, "#273B48")
    text(b, (55, 389), "built around the remote.", 22, "#273B48")
    television(b, "01-channels", (394, 88), 596, "gallery")
    save(b, "feature/c2-gallery.png")

    c = Image.new("RGB", (1024, 500), "#101319")
    halo(c, (380, 95, 1060, 490), (164, 83, 31, 80), 80)
    mark(c, (54, 62), 51)
    text(c, (122, 62), "Tvheadend", 26)
    text(c, (122, 93), "Player", 26, ORANGE)
    text(c, (52, 176), "Built around", 44)
    text(c, (52, 228), "the remote.", 44)
    text(c, (55, 315), "A Tvheadend client", 22)
    text(c, (55, 345), "for Android TV.", 22)
    text(c, (55, 398), "Live TV · Guide · Recordings", 18, "#B9B1A8")
    television(c, "03-player", (410, 101), 580, "cinema")
    save(c, "feature/c3-cinema.png")

    primary = c.copy()
    television(primary, "01-channels", (410, 101), 580, "cinema")
    save(primary, "feature/c5-remote-channels.png")

    hybrid = Image.new("RGB", (1024, 500), "#101319")
    halo(hybrid, (380, 95, 1060, 490), (164, 83, 31, 80), 80)
    mark(hybrid, (54, 58), 62)
    text(hybrid, (52, 153), "Tvheadend", 48)
    text(hybrid, (52, 208), "Player", 48, ORANGE)
    text(hybrid, (55, 276), "Live TV · Guide · Recordings", 18, "#B9B1A8")
    text(hybrid, (55, 329), "A Tvheadend client", 22)
    text(hybrid, (55, 359), "for Android TV,", 22)
    text(hybrid, (55, 389), "built around the remote.", 22)
    television(hybrid, "03-player", (410, 101), 580, "cinema")
    save(hybrid, "feature/c4-warm.png")


def figure(path, caption):
    data = base64.b64encode(path.read_bytes()).decode("ascii")
    return f'<figure><img loading="lazy" src="data:image/png;base64,{data}" alt="{html.escape(caption)}"><figcaption>{html.escape(caption)}</figcaption></figure>'


def review(suites):
    comparisons = []
    for name, title, reason in FEATURES:
        comparisons.append(f'<section><h2>{title}</h2><p>{reason}</p><div class="pair">' +
                           figure(OUT / "previews/reference/round2-c.png", "Round 2 · C reference") +
                           figure(OUT / f"previews/feature/{name}.png", title) + '</div></section>')
    choices = ''.join(figure(OUT / f"thumbnails/feature/{name}.png", title) for name, title, _ in FEATURES)
    titles = ["01 · Channels — unchanged", "02 · Guide — unchanged", "03 · Player — unchanged", "04 · Recordings — unchanged",
              "05 · Programme details — Ion Wake", "06 · Player schedule — Harbor Lights"]
    screens = ''.join(figure(p, title) for p, title in zip(sorted((OUT / "previews/plain").glob("*.png")), titles))
    rules = ''.join(f'<li><a href="{url}">{label}</a></li>' for label, url in RULES)
    count = sum(s["tests"] for s in suites.values())
    (OUT / "review.html").write_text(f'''<!doctype html><html lang="en-US"><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><title>Play Store assets · Round 3</title>
<style>body{{margin:32px auto;padding:0 20px;max-width:1440px;background:{FIELD};color:{TEXT};font:16px/1.6 system-ui}}h1,h2{{line-height:1.2}}p,figcaption{{color:#BAC4CC;max-width:90ch}}a{{color:#6CD5FA}}img{{display:block;width:100%;height:auto}}figure{{margin:0 0 24px}}figcaption{{margin-top:8px}}.pair{{display:grid;grid-template-columns:1fr 1fr;gap:24px}}.choices{{display:grid;grid-template-columns:repeat(3,1fr);gap:24px}}section{{margin:48px 0}}.screens{{max-width:960px}}@media(max-width:800px){{.pair,.choices{{grid-template-columns:1fr}}}}</style>
<h1>Tvheadend Player · Round 3</h1><p>Six plain app screenshots. Two combinations for comparison: C4 combines C1’s text with C3’s warmth; C5 retains C3’s left column and frame with C1’s full Channels screen.</p>
<h2>The two combinations</h2><div class="pair">{figure(OUT / 'previews/feature/c4-warm.png', 'C4 · Owner’s combination: C1 text + C3 warmth')}{figure(OUT / 'previews/feature/c5-remote-channels.png', 'C5 · Primary’s combination: C3 text + C1 Channels')}</div>
<h2>Original references</h2><div class="pair">{figure(OUT / 'previews/feature/c1-floating.png', 'C1 · Original title-led / Channels')}{figure(OUT / 'previews/feature/c3-cinema.png', 'C3 · Original remote-led / concert')}</div>
<h2>Variations · thumbnail comparison</h2><div class="choices">{choices}</div>
{''.join(comparisons)}
<h2>The six screenshots · listing order</h2><p>01–04 are byte-for-byte copies of round 2. Recording now is removed. Details and schedule are fresh, unaltered production-composable captures from the same fictional evening: October 4, 2026 at 20:15 UTC, English (US), font scale 1.0.</p>
<div class="screens">{screens}</div>
<h2>Evidence</h2><p>Both affected whole test classes passed: {count} tests, no failures or skips. The retained frames carry round 2’s manifest provenance. The manifest records source, inputs, tests and output hashes. All six full-size screenshots are 1920×1080 RGB; each feature is 1024×500 RGB. The icon and banner are retained unchanged. See report.md and manifest.json.</p>
<p>These are offline production compositions, not decoded-video or physical-TV evidence. TV-G4 acceptance of offline fixture captures remains a submission question. Owner selection of C1/C2/C3 remains open. The generic television framing is confined to the promotional feature graphics; app screenshots are plain.</p>
<h2>Google requirements</h2><ul>{rules}</ul><p>Rule sources checked October 29, 2026 in the preceding round. No store upload or artwork/play promotion.</p></html>''', encoding="utf-8")


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    suites, scenes = test_evidence()
    for name in RETAINED:
        copy_baseline(f"plain/{name}.png")
    for i, (name, _, _) in enumerate(NEW, 5):
        with Image.open(SOURCE / f"store-{name}.png") as image:
            if image.size != (1920, 1080) or ("A" in image.getbands() and image.getchannel("A").getextrema() != (255, 255)):
                raise ValueError(f"Wrong size or non-opaque capture: {name}")
            save(image.convert("RGB"), f"plain/{i:02}-{name}.png")
    copy_baseline("feature/c-television.png", "reference/round2-c.png")
    copy_baseline("icon/current-rgba.png")
    copy_baseline("banner/current.png")
    features()
    for group in ("plain", "feature", "reference", "icon", "banner"):
        for path in (OUT / group).glob("*.png"):
            with Image.open(path) as image:
                for folder, width in (("previews", 960), ("thumbnails", 320)):
                    save(image.resize((width, round(image.height * width / image.width)), Image.Resampling.LANCZOS),
                         f"{folder}/" + str(path.relative_to(OUT)))
    review(suites)
    baseline = json.loads((BASELINE / "manifest.json").read_text())
    inputs = [FONT, MARK, Path(__file__), BASELINE / "manifest.json", BASELINE / "feature/c-television.png"]
    inputs.extend(BASELINE / f"plain/{name}.png" for name in RETAINED)
    inputs.extend([BASELINE / "icon/current-rgba.png", BASELINE / "banner/current.png"])
    inputs.extend((ROOT / "app/src/test/resources/fixture-art").rglob("*"))
    for _, cls, _ in NEW:
        inputs.extend((ROOT / "app/src/test/java").rglob(f"{cls}.kt"))
    manifest = {
        "source_commit": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip(),
        "tracked_diff_sha256": hashlib.sha256(subprocess.check_output(["git", "diff", "HEAD"], cwd=ROOT)).hexdigest(),
        "locale": "en-US", "font_scale": 1.0, "timezone": "UTC", "canvas_dp": [960, 540],
        "test_task": ":app:testPlayDebugUnitTest", "capture_lane": "all", "suites": suites, "scenes": scenes,
        "retained_round2": {"source_commit": baseline["source_commit"], "manifest_sha256": sha(BASELINE / "manifest.json"),
                            "scenes": [s for s in baseline["scenes"] if s["scene"] in ("channels", "guide", "player", "recording")],
                            "suites": baseline["suites"]},
        "limits": "Offline compositions, not device capture or Google acceptance. Run the two whole classes immediately before packaging; XML alone cannot establish source freshness. Frames 01–04 deliberately reuse round-two bytes and evidence.",
        "inputs": {str(p.relative_to(ROOT)): sha(p) for p in sorted(set(inputs)) if p.is_file()}, "files": {},
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
