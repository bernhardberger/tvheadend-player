#!/usr/bin/env python3
"""Offline, byte-verified import of the two upstream source inputs."""
import hashlib
import io
from pathlib import Path
import sys
import tarfile

ROOT = Path(__file__).resolve().parent
MEDIA_REVISION = "8c6678b657ede1e7883fc164ef73ed483c7796c3"
INPUTS = (
    ("bbe2157458f2eb365f9eebf4a8d4f903d8da7ab1e275aa0ad680cd253811b724", "media3"),
    ("9eeca8490f794574185986c0df7800d65ccca2980f57dc26b630a398581d7929", "ffmpeg"),
)


def main():
    if len(sys.argv) != 3:
        raise SystemExit("usage: import-sources.py MEDIA3_UPSTREAM.tar.gz SDK_FFMPEG_SOURCES.tar.xz")
    files = {}
    for path, (expected, kind) in zip(map(Path, sys.argv[1:]), INPUTS):
        with path.open("rb") as stream:
            if hashlib.file_digest(stream, "sha256").hexdigest() != expected:
                raise SystemExit(f"source input hash mismatch: {kind}")
        with tarfile.open(path) as archive:
            for member in archive:
                if kind == "media3":
                    prefix = f"media-{MEDIA_REVISION}/"
                    if not member.name.startswith(prefix):
                        continue
                    relative = member.name[len(prefix):]
                    if not (relative.startswith("libraries/decoder_ffmpeg/src/main/") or relative in (
                        "libraries/decoder_ffmpeg/README.md",
                        "libraries/decoder_ffmpeg/build.gradle.kts",
                        "libraries/decoder_ffmpeg/proguard-rules.txt",
                        "LICENSE", "NOTICE", "README.md", "constants.gradle", "build.gradle",
                        "build.gradle.kts", "settings.gradle", "settings.gradle.kts",
                    )):
                        continue
                    name = f"media3-1.11.1/{relative}"
                else:
                    if not member.name.startswith("ffmpeg/"):
                        continue
                    name = member.name
                if member.isdir():
                    continue
                if not member.isfile() or ".." in Path(name).parts:
                    raise SystemExit(f"non-regular or unsafe source entry: {name}")
                files[name] = (archive.extractfile(member).read(), member.mode & 0o777)
    with tarfile.open(ROOT / "ffmpeg-media3-1.11.1-sources.tar.xz", "w:xz", preset=6) as archive:
        for name, (data, mode) in sorted(files.items()):
            entry = tarfile.TarInfo(name)
            entry.size, entry.mode = len(data), mode
            archive.addfile(entry, io.BytesIO(data))
            if name.startswith("media3-1.11.1/"):
                target = ROOT / "upstream" / name.removeprefix("media3-1.11.1/")
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(data)
                target.chmod(mode)
    print(f"Imported {len(files)} regular source files; upstream bytes preserved")


if __name__ == "__main__":
    main()
