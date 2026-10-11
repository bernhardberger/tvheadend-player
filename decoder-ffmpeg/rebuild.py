#!/usr/bin/env python3
"""Offline rebuild, two make jobs; writes candidates under build/, never updates pins."""
import argparse
import hashlib
from pathlib import Path
import shutil
import subprocess
import tarfile

ROOT = Path(__file__).resolve().parent
SOURCE_SHA256 = "7f84a79a525f80fa89ca4bf541325b5a068a20fc59ad5835fa6d148e68177a54"
TARGETS = {
    "armeabi-v7a": "armv7a-linux-androideabi",
    "arm64-v8a": "aarch64-linux-android",
    "x86": "i686-linux-android",
    "x86_64": "x86_64-linux-android",
}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--ndk", type=Path, default=Path("/opt/android-sdk/ndk/26.1.10909125"))
    args = parser.parse_args()
    ndk = args.ndk.resolve()
    if "Pkg.Revision = 26.1.10909125" not in (ndk / "source.properties").read_text():
        raise SystemExit("Rebuild requires the recorded NDK 26.1.10909125")
    source = ROOT / "ffmpeg-media3-1.11.1-sources.tar.xz"
    with source.open("rb") as stream:
        if hashlib.file_digest(stream, "sha256").hexdigest() != SOURCE_SHA256:
            raise SystemExit("Corresponding-source SHA-256 mismatch")
    work = ROOT / "build/native"
    if work.exists():
        raise SystemExit("build/native already exists; preserve/move earlier evidence before rebuilding")
    work.mkdir(parents=True)
    with tarfile.open(source) as archive:
        archive.extractall(work, filter="data")
    main_path = work / "media3-1.11.1/libraries/decoder_ffmpeg/src/main"
    jni = main_path / "jni"
    shutil.move(work / "ffmpeg", jni / "ffmpeg")
    script = jni / "build_ffmpeg.sh"
    original = script.read_text()
    job_line = 'JOBS="$(nproc 2> /dev/null || sysctl -n hw.ncpu 2> /dev/null || echo 4)"'
    if original.count(job_line) != 1:
        raise SystemExit("Unexpected upstream job selection")
    script.write_text(original.replace(job_line, 'JOBS="2"'))
    subprocess.run([
        "bash", str(script), str(main_path), str(ndk), "linux-x86_64", "28",
        "ac3", "eac3", "mp3",
    ], check=True, timeout=900)
    compiler_dir = ndk / "toolchains/llvm/prebuilt/linux-x86_64/bin"
    for abi, target in TARGETS.items():
        output = work / "jniLibs" / abi / "libffmpegJNI.so"
        output.parent.mkdir(parents=True)
        libraries = jni / "ffmpeg/android-libs" / abi
        command = [
            str(compiler_dir / f"{target}28-clang++"), "-shared", "-fPIC", "-O2",
            "-std=c++11", "-fvisibility=hidden", "-fvisibility-inlines-hidden",
            "-static-libstdc++", "-Wl,-soname,libffmpegJNI.so", "-Wl,--no-undefined",
            "-Wl,-z,max-page-size=16384", "-Wl,--build-id=sha1",
            "-I", str(jni / "ffmpeg"), str(jni / "ffmpeg_jni.cc"),
            "-Wl,--start-group", *(str(libraries / f"lib{lib}.a") for lib in (
                "swresample", "avcodec", "avutil",
            )), "-Wl,--end-group", "-landroid", "-llog", "-lm", "-lz",
            "-o", str(output),
        ]
        if abi == "arm64-v8a":
            command.append("-Wl,-Bsymbolic")
        subprocess.run(command, check=True, timeout=120)
        subprocess.run([str(compiler_dir / "llvm-strip"), "--strip-unneeded", str(output)],
                       check=True, timeout=60)
        print(abi, hashlib.sha256(output.read_bytes()).hexdigest(), flush=True)
    print(f"Candidates built in {work / 'jniLibs'}; compare to pinned src/main/jniLibs before adoption")


if __name__ == "__main__":
    main()
