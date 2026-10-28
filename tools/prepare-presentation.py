#!/usr/bin/env python3
"""Package the README presentation captures for local review; never publishes."""
import hashlib
import html
import json
from pathlib import Path
import shutil
import subprocess

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/build/outputs/presentation"
OUT = ROOT / "captures/presentation"
SIZE = (1920, 1080)

notes = {
    "channels": "Channels, Immersive view: the plainest picture of a TV app. Title and focused card read at README width.",
    "channels-grid": "Channels, Grid view with program art on. Not in the README: logo badges cover titles printed in the art.",
    "channels-list": "Channels, List view: numbered channels beside the focused program's picture and details.",
    "channels-logos": "Channels, Immersive view without program pictures: logos and text only.",
    "guide": "Guide: shows the Tvheadend program data directly. Times are small at README width.",
    "details": "Program details from the Guide. Supporting image.",
    "drawer": "Side menu over Channels while Northline News keeps playing behind: its now-playing header above the menu, Channels focused.",
    "player": "Player controls: closest to watching TV. A still cannot show remote response or motion.",
    "player-rail": "Quick zap rail over the playing picture, with the focused channel's program above it.",
    "player-schedule": "The playing channel's schedule beside the picture.",
    "recordings": "Recordings: the previous evening's Ridge Light inside Documentaries, partly watched.",
    "recordings-folders": "Recordings at the Archive's top level: the server's folders, then recordings with channel logos.",
    "settings": "Playback settings over the playing picture. Supporting image.",
}

OUT.mkdir(parents=True, exist_ok=True)
manifest = {
    "commit": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip(),
    "dirty_diff_sha256": hashlib.sha256(subprocess.check_output(["git", "diff"], cwd=ROOT)).hexdigest(),
    "locale": "en-US / UTC",
    "font_scale": 1.0,
    "captures": {},
}
for name in notes:
    path = SOURCE / f"{name}.png"
    with Image.open(path) as image:
        # Full frames at their captured size: never crop, repaint, resize or upscale.
        if image.size != SIZE:
            raise SystemExit(f"{path} is {image.size[0]}x{image.size[1]}, expected {SIZE[0]}x{SIZE[1]}")
    shutil.copyfile(path, OUT / f"{name}.png")
    manifest["captures"][name] = {
        "source": str(path.relative_to(ROOT)),
        "pixels": list(SIZE),
        "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
    }
(OUT / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")

buttons = "".join(f'<button onclick="show(\'{name}\')">{html.escape(name.replace("-", " ").capitalize())}</button>' for name in notes)
(OUT / "review.html").write_text(f'''<!doctype html><html lang="en"><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><title>Presentation candidates</title>
<style>body{{margin:28px auto;max-width:830px;padding:0 16px;background:#0d1117;color:#e6edf3;font:16px/1.6 system-ui}}
img{{display:block;width:100%;height:auto}}button{{background:#21262d;color:inherit;border:1px solid #484f58;border-radius:6px;padding:8px 14px;margin:0 6px 8px 0;cursor:pointer}}p{{color:#b1bac4}}</style>
<h1>A Tvheadend client for Android TV, built around the remote.</h1>
<p>Local draft. Full 1920×1080 frames shown at 830 CSS pixels, about the README's desktop width. The owner picks the lead.</p>
<nav>{buttons}</nav><p id="caption"></p><img id="capture" alt="Capture of the production screen">
<p>Fictional fixtures. The player shows a still picture, not decoded video.</p>
<script>const notes={json.dumps(notes)};function show(name){{document.getElementById('capture').src=name+'.png';document.getElementById('caption').textContent=notes[name];}}show('channels');</script></html>''')
print(OUT / "review.html")
