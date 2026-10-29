#!/usr/bin/env python3
"""Package round-two Play candidates; requires fresh whole-class offline captures."""
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
OUT = ROOT / "captures/play-store/round2"
BASELINE = ROOT / "captures/play-store/round1"
SOURCE = ROOT / "app/build/outputs/presentation"
FONT = ROOT / "artwork/fonts/Outfit-550.ttf"
FIELD, TEXT, CYAN, ORANGE = "#0F1014", "#E3E3E8", "#00BCFA", "#FA7F00"
SCENES = [
    ("channels", "Live match", "ChannelsLayoutCaptureTest", "storeChannels", "01-channels",
     "Harbor Kickoff replaces Ridge Light. The REC badge, stadium and bright neighboring cards make live TV and recording visible together."),
    ("guide", "Tonight’s guide", "GuideVisualLayoutTest", "storeGuide", "02-guide",
     "Northline Tonight is focused on row three. A title-free blue studio still clears the native header chips; the now-line, recording dot and scheduled clock remain visible."),
    ("player", "Concert at live", "PlayerChromeCaptureTest", "storePlayer", "03-player",
     "A warm concert replaces the mountain. The large program name, Pause focus and buffered timeline identify the player even when smaller labels cannot be read."),
    ("recording", "Continue a recording", "RecordingsVisualLayoutTest", "storeRecording", "07-recordings",
     "Glass Harbor replaces the documentary. A complete, partly watched drama shows saved progress beside the library, without repeating the live program artwork."),
    ("recording-now", "Recording now", "RecordingsVisualLayoutTest", "storeRecordingNow", "06-recordings-folders",
     "Recording now replaces Documentaries focus. The red-dot folder and running match connect the archive to the live match in the opening frame."),
]
CLASSES = ["ChannelsLayoutCaptureTest", "ChannelsLayoutFocusTest", "CardGlowRoomTest", "DrawerBackdropFadeTest",
           "GuideVisualLayoutTest", "RecordingsVisualLayoutTest", "PlayerChromeCaptureTest"]
FEATURES = [
    ("a-guide", "Guide-led", "a-landscape",
     "The generic mountain is replaced by a large crop of the real guide: now-line, recording dot and selected news program. The product category is explicit."),
    ("b-three-views", "Three views", "b-type",
     "Three edge-to-edge crops of the actual player, guide and archive replace the flat cyan field. The screen roles are readable before the small UI text."),
    ("c-television", "Television", "c-product",
     "The owner’s preferred left-copy/right-TV composition, now with the store-specific live-match scene and a larger screen. One coherent app view replaces the programme wall; no added logo."),
]
RULES = [
    ("Preview asset requirements", "https://support.google.com/googleplay/android-developer/answer/9866151?hl=en"),
    ("TV-G4 screenshot requirement", "https://developer.android.com/docs/quality-guidelines/tv-app-quality#TV-G4"),
    ("Icon design specifications", "https://developer.android.com/distribute/google-play/resources/icon-design-specifications"),
    ("Metadata policy", "https://support.google.com/googleplay/android-developer/answer/9898842?hl=en"),
]
LISTINGS = [
    ("Zattoo", "https://play.google.com/store/apps/details?id=com.zattoo.player&hl=en_US&gl=DE",
     "Its landscape assets establish a short message beside large UI. The final TV direction keeps that clear side-by-side hierarchy, without awards or channel-count claims."),
    ("ZDF", "https://play.google.com/store/apps/details?id=com.zdf.android.mediathek&hl=en_US&gl=DE",
     "Its opening image puts a logo and short message over a programme wall. We explored that direction, but the owner preferred a single TV screen; the wall is retired."),
    ("Plex", "https://play.google.com/store/apps/details?id=com.plexapp.android&hl=en_US&gl=DE",
     "Its opener uses a strong left-aligned headline and large imagery. We retain the clear headline hierarchy, without the collage or an added mark."),
    ("Sparkle TV", "https://play.google.com/store/apps/details?id=se.hedekonsult.sparkle&hl=en_US",
     "Its opening player and guide images let full-screen UI explain the product. We use that directness and preserve full-size TV text in all five screenshots."),
    ("TiviMate", "https://play.google.com/store/apps/details?id=ar.tvplayer.tv&hl=en_US",
     "Its first two images pair large role-oriented headings with recognizable player/guide surfaces. We use that hierarchy in feature B, while keeping our screenshots plain and omitting device frames."),
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
    path = SOURCE / f"store-{name}.png"
    with Image.open(path) as image:
        if image.size != (1920, 1080):
            raise ValueError(f"{path}: expected 1920x1080, got {image.size}")
        if "A" in image.getbands() and image.getchannel("A").getextrema() != (255, 255):
            raise ValueError(f"{path}: non-opaque capture")
        return image.convert("RGB")


def test_evidence():
    """Check scenes and every consumer class, not just five passing capture methods."""
    directory = ROOT / "app/build/test-results/testPlayDebugUnitTest"
    suites, scenes = {}, []
    for cls in CLASSES:
        paths = list(directory.glob(f"TEST-*.{cls}.xml"))
        if len(paths) != 1:
            raise ValueError(f"Missing or ambiguous result for {cls}")
        tree = ET.parse(paths[0]).getroot()
        cases = tree.findall("testcase")
        skipped = [case.get("name") for case in cases if case.find("skipped") is not None]
        # Existing RTL-airings finding is intentionally assumption-skipped by the suite.
        known_skips = {"pseudoArXb"} if cls == "PlayerChromeCaptureTest" else set()
        if not cases or any(case.find(tag) is not None for case in cases for tag in ("failure", "error")) or set(skipped) - known_skips:
            raise ValueError(f"{cls}: class failed or has unexpected skipped tests")
        suites[cls] = {"tests": len(cases), "skipped": skipped, "result": str(paths[0].relative_to(ROOT)), "sha256": sha(paths[0])}
        for name, _, owner, method, _, _ in SCENES:
            if owner != cls:
                continue
            if not any(case.get("name") == method and case.find("skipped") is None for case in cases):
                raise ValueError(f"No passing result for {cls}.{method}")
            source = SOURCE / f"store-{name}.png"
            if source.stat().st_mtime > paths[0].stat().st_mtime:
                raise ValueError(f"{name}: capture is newer than its test result")
            scenes.append({"scene": name, "test": f"{tree.get('name')}.{method}",
                           "source": str(source.relative_to(ROOT)), "sha256": sha(source)})
    return suites, scenes


def features():
    # Promotional crops only. The plain screenshots below are never cropped or retouched.
    guide = source_image("guide")
    a = Image.new("RGB", (1024, 500), FIELD)
    crop = guide.crop((480, 456, 1320, 1016)).resize((554, 369), Image.Resampling.LANCZOS)
    a.paste(crop, (470, 66))  # Bleeds at the right edge; no tiny whole-screen box.
    text(a, (64, 90), "Tvheadend", 56)
    text(a, (64, 153), "Player", 56, ORANGE)
    text(a, (67, 245), "A Tvheadend client", 23)
    text(a, (67, 277), "for Android TV,", 23)
    text(a, (67, 309), "built around the remote.", 23)
    text(a, (67, 416), "LIVE TV  /  GUIDE  /  RECORDINGS", 23, CYAN)
    save(a, "feature/a-guide.png")

    b = Image.new("RGB", (1024, 500), FIELD)
    # Three distinct production surfaces at a useful crop scale, never simulated UI.
    panels = [
        source_image("player").crop((320, 180, 1320, 940)),
        guide.crop((480, 462, 1250, 1040)),
        source_image("recording").crop((1100, 210, 1710, 670)),
    ]
    for index, panel in enumerate(panels):
        x = index * 342
        panel = ImageOps.fit(panel, (342, 310), centering=(0.5, 0.5))
        b.paste(panel, (x, 190))
    # Separate marketing labels from native UI text, rather than overlapping it.
    ImageDraw.Draw(b).rectangle((0, 424, 1024, 500), fill=FIELD)
    text(b, (64, 42), "Tvheadend", 56)
    text(b, (352, 42), "Player", 56, ORANGE)
    text(b, (67, 119), "A Tvheadend client for Android TV, built around the remote.", 23)
    for x, label in ((67, "Live TV"), (390, "Guide"), (728, "Recordings")):
        text(b, (x, 443), label, 30)
    save(b, "feature/b-three-views.png")

    c = Image.new("RGB", (1024, 500), FIELD)
    d = ImageDraw.Draw(c)
    # A restrained, unbranded television frame, requested for this promotional graphic.
    d.rounded_rectangle((390, 64, 1009, 420), radius=12, fill="#272B31", outline="#454B54", width=2)
    c.paste(source_image("channels").resize((600, 338), Image.Resampling.LANCZOS), (400, 73))
    d.polygon(((670, 420), (730, 420), (747, 443), (653, 443)), fill="#272B31")
    d.rounded_rectangle((624, 441, 776, 447), radius=3, fill="#454B54")
    text(c, (56, 110), "Tvheadend", 48)
    text(c, (56, 166), "Player", 48, ORANGE)
    text(c, (59, 258), "A Tvheadend client", 22)
    text(c, (59, 289), "for Android TV,", 22)
    text(c, (59, 320), "built around the remote.", 22)
    text(c, (59, 425), "LIVE TV  /  GUIDE  /  RECORDINGS", 19, CYAN)
    save(c, "feature/c-television.png")


def figure(path, caption, small=False):
    encoded = base64.b64encode(path.read_bytes()).decode("ascii")
    css = ' class="thumb"' if small else ''
    return (f'<figure{css}><img loading="lazy" src="data:image/png;base64,{encoded}" '
            f'alt="{html.escape(caption)}"><figcaption>{html.escape(caption)}</figcaption></figure>')


def review():
    comparisons, thumbnails = [], []
    for i, (name, title, _, _, old, reason) in enumerate(SCENES, 1):
        new = OUT / "previews/plain" / f"{i:02}-{name}.png"
        comparisons.append(f'<section><h3>{i:02} · {html.escape(title)}</h3><p>{html.escape(reason)}</p><div class="pair">' +
                           figure(BASELINE / f"previews/plain/{old}.png", "Round 1") + figure(new, "Round 2") + '</div></section>')
        thumbnails.append(figure(OUT / "thumbnails/plain" / f"{i:02}-{name}.png", title, small=True))
    for name, title, old, reason in FEATURES:
        comparisons.append(f'<section><h3>Feature · {title}</h3><p>{reason}</p><div class="pair">' +
                           figure(BASELINE / f"previews/feature/{old}.png", "Round 1") +
                           figure(OUT / f"previews/feature/{name}.png", "Round 2") + '</div></section>')
        thumbnails.append(figure(OUT / f"thumbnails/feature/{name}.png", title, small=True))
    rules = ''.join(f'<li><a href="{url}">{label}</a></li>' for label, url in RULES)
    research = ''.join(f'<li><a href="{url}">{label}</a>: {why}</li>' for label, url, why in LISTINGS)
    body = f'''<!doctype html><html lang="en-US"><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><title>Play Store assets · Round 2</title>
<style>body{{margin:32px auto;padding:0 20px;max-width:1400px;background:{FIELD};color:{TEXT};font:16px/1.6 system-ui}}h1,h2,h3{{line-height:1.2}}h2{{margin-top:3rem}}p,figcaption{{max-width:90ch;color:#BAC4CC}}a{{color:#6CD5FA}}img{{display:block;width:100%;height:auto}}figure{{margin:0 0 24px}}figcaption{{margin-top:8px}}.pair{{display:grid;grid-template-columns:1fr 1fr;gap:20px}}section{{margin:32px 0}}.note{{padding:20px;background:#1C252C;border-radius:12px}}.strip{{display:flex;flex-wrap:wrap;gap:20px}}.thumb{{width:320px;max-width:100%}}@media(max-width:900px){{.pair{{grid-template-columns:1fr}}}}code{{overflow-wrap:anywhere}}</style>
<h1>Tvheadend Player · Round 2</h1><p>Five store-specific scenes · three feature directions · local review</p>
<div class="note"><strong>Recommendation: the five plain screenshots in order, with feature C.</strong><p>The opening match establishes live TV; the blue guide and warm concert make the next frames visibly different. The archive then shows saved progress and a recording in progress. C returns to the owner’s preferred headline/copy on the left and a screenshot in a TV on the right. B explains the three functions most explicitly; A is the quieter guide-led alternative. No added logo on the feature graphics. Icon and banner stay unchanged.</p></div>
<h2>Phone-thumbnail check · 320px wide</h2><p>Titles, focus and screen roles survive. Dense guide cells and archive metadata require opening the image; no full TV interface can make every label readable at this size.</p><div class="strip">{''.join(thumbnails)}</div>
<h2>Round 1 → Round 2</h2><p>All five plain candidates are unaltered 1920×1080 RGB renders of production composables: no captions, UI rescaling, retouching or cropping. Feature graphics are promotional compositions and intentionally crop those renders.</p>{''.join(comparisons)}
<p>The added logo and programme-wall proposals were retired after owner feedback. The TV frame appears only in promotional feature C; all five plain app screenshots remain unaltered.</p>
<h2>Icon and TV banner · retained</h2><div class="pair">{figure(OUT / 'previews/icon/current-rgba.png', '512×512 RGBA; current mark and scale')}{figure(OUT / 'previews/banner/current.png', '1280×720 RGB; existing banner, byte-identical')}</div>
<h2>Listing research · October 29, 2026</h2><ul>{research}</ul><p>These are observations of public listing images, not evidence of conversion performance. No competitor assets are used in our candidates.</p>
<h2>Evidence and remaining decisions</h2><ul><li>English (US), font scale 1.0, 960×540dp at xhdpi, UTC; one fictional evening at 20:15 on October 4, 2026. Recordings are from earlier evenings. Program art and server figures are fictional fixtures.</li><li>Seven whole affected fixture-consumer classes ran: 277 passed, one existing pseudoArXb assumption-skip for the known RTL airings-row defect, zero failures. All five store methods passed. The manifest records tests, source revision and input/output hashes. It cannot establish source freshness from XML alone.</li><li>Independent screenshot review accepted the five-frame sequence and corrected feature B crop. The latest C follows the owner’s subsequent TV-frame direction; see visual-review.md for review scope.</li><li>These are offline production compositions, not device screenshots or decoded-video evidence. TV-G4 does not explicitly settle offline fixture capture acceptance. Confirm that before submission.</li><li>Owner approval of the five-frame selection and feature A/B/C remains open. No artwork/play promotion or upload has occurred.</li></ul>
<h2>Google requirements</h2><ul>{rules}</ul><p>At most eight TV screenshots, including an unaltered high-resolution current-experience frame. RGB screenshots/feature/banner; 512px RGBA icon ≤1 MB. Features are 1024×500 and below the assignment’s 15 MB ceiling. No rankings, prices, install calls or affiliation claims. Device-frame guidance applies to screenshot presentation; these plain screenshots contain no added frame. Feature C uses the owner-requested generic TV frame.</p>
<p>Full files: <code>plain/</code>, <code>feature/</code>, <code>icon/</code>, <code>banner/</code>. All images in this page are embedded. See <code>report.md</code> and <code>manifest.json</code> for the handoff.</p></html>'''
    (OUT / "review.html").write_text(body, encoding="utf-8")


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    suites, scenes = test_evidence()
    for i, (name, _, _, _, _, _) in enumerate(SCENES, 1):
        image = source_image(name)
        path = save(image, f"plain/{i:02}-{name}.png")
        with Image.open(path) as encoded:
            if ImageChops.difference(image, encoded).getbbox():
                raise ValueError(f"Changed pixels in {path}")
    features()
    save(Image.open(ROOT / "app/src/main/ic_launcher-playstore.png").convert("RGBA"), "icon/current-rgba.png")
    (OUT / "banner").mkdir(exist_ok=True)
    shutil.copyfile(ROOT / "artwork/tvheadend-player-banner@4x.png", OUT / "banner/current.png")
    for group in ("plain", "feature", "icon", "banner"):
        for path in (OUT / group).glob("*.png"):
            with Image.open(path) as image:
                for folder, width in (("previews", 960), ("thumbnails", 320)):
                    resized = image.resize((width, round(image.height * width / image.width)), Image.Resampling.LANCZOS)
                    save(resized, f"{folder}/" + str(path.relative_to(OUT)))
    review()
    inputs = [FONT, Path(__file__), ROOT / "tools/RenderArtwork.java", ROOT / "app/src/main/ic_launcher-playstore.png",
              ROOT / "artwork/tvheadend-player-banner@4x.png"]
    inputs.extend((ROOT / "app/src/test/resources/fixture-art").rglob("*"))
    for cls in CLASSES + ["ChannelsLayoutFixture"]:
        inputs.extend((ROOT / "app/src/test/java").rglob(f"{cls}.kt"))
    inputs.extend((BASELINE / "previews").rglob("*.png"))
    manifest = {
        "source_commit": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip(),
        "tracked_diff_sha256": hashlib.sha256(subprocess.check_output(["git", "diff", "HEAD"], cwd=ROOT)).hexdigest(),
        "locale": "en-US", "font_scale": 1.0, "timezone": "UTC", "canvas_dp": [960, 540],
        "test_task": ":app:testPlayDebugUnitTest", "capture_lane": "all", "suites": suites, "scenes": scenes,
        "limits": "Offline composition only, not device capture or Google acceptance. Run whole classes immediately before packaging; XML cannot establish source freshness.",
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
