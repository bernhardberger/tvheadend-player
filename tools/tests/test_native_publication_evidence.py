from __future__ import annotations

import copy
import hashlib
import runpy
import shutil
import struct
import subprocess
import sys
import tarfile
import tempfile
import unittest
import zipfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CHECKER_PATH = ROOT / "tools/check-native-libs"
CHECKER = runpy.run_path(str(CHECKER_PATH), run_name="native_checker_test")


class NativePublicationEvidenceTest(unittest.TestCase):
    def test_app_owned_bytes_and_compiled_sources_match_provenance(self) -> None:
        errors = []
        module = ROOT / "decoder-ffmpeg"
        CHECKER["validate_corresponding_source"](
            module / CHECKER["FFMPEG_SOURCES_NAME"], errors, module / "upstream",
        )
        libraries = CHECKER["validate_native_module"](module, errors)
        self.assertEqual(errors, [])
        self.assertEqual(set(libraries), set(CHECKER["EXPECTED_ABIS"]))

    def test_native_gate_rejects_tampered_missing_and_extra_libraries(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            module = Path(directory)
            native = module / "src/main/jniLibs"
            shutil.copytree(ROOT / "decoder-ffmpeg/src/main/jniLibs", native)
            library = native / "arm64-v8a/libffmpegJNI.so"
            library.write_bytes(library.read_bytes() + b"tampered")
            (native / "x86/libffmpegJNI.so").unlink()
            (native / "x86/extra.so").write_bytes(b"unexpected")
            errors = []
            CHECKER["validate_native_module"](module, errors)
            self.assertTrue(any("SHA-256" in e for e in errors))
            self.assertTrue(any("missing" in e for e in errors))
            self.assertTrue(any("inventory" in e for e in errors))

    def test_native_gate_rejects_wrong_abi_even_when_hash_matches(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            module = Path(directory)
            native = module / "src/main/jniLibs"
            shutil.copytree(ROOT / "decoder-ffmpeg/src/main/jniLibs", native)
            library = native / "arm64-v8a/libffmpegJNI.so"
            data = bytearray(library.read_bytes())
            struct.pack_into("<H", data, 18, 62)  # x86-64 in the arm64 slot
            library.write_bytes(data)
            hashes = CHECKER["PROVENANCE"]["libraries"]
            old = hashes["arm64-v8a"]
            try:
                hashes["arm64-v8a"] = hashlib.sha256(data).hexdigest()
                errors = []
                CHECKER["validate_native_module"](module, errors)
                self.assertTrue(any("wrong ELF ABI" in e for e in errors))
            finally:
                hashes["arm64-v8a"] = old

    def test_java_source_substitution_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            upstream = Path(directory) / "upstream"
            shutil.copytree(ROOT / "decoder-ffmpeg/upstream", upstream)
            java = upstream / "libraries/decoder_ffmpeg/src/main/java/androidx/media3/decoder/ffmpeg/FfmpegAudioRenderer.java"
            java.write_bytes(java.read_bytes() + b"// changed")
            errors = []
            CHECKER["validate_corresponding_source"](
                ROOT / "decoder-ffmpeg" / CHECKER["FFMPEG_SOURCES_NAME"], errors, upstream,
            )
            self.assertTrue(any("source differs" in e for e in errors))

    def test_staged_sdk_keeps_native_gate_and_cannot_pass_release(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            apk = Path(directory) / "app.apk"
            libraries = ROOT / "decoder-ffmpeg/src/main/jniLibs"
            with zipfile.ZipFile(apk, "w") as archive:
                for abi in CHECKER["EXPECTED_ABIS"]:
                    archive.write(libraries / abi / CHECKER["EXPECTED_LIBRARY"],
                                  f"lib/{abi}/{CHECKER['EXPECTED_LIBRARY']}")
            command = [sys.executable, str(CHECKER_PATH), "--staged-sdk", "--apk", str(apk),
                       "--evidence-directory", str(Path(directory) / "absent-sdk-evidence")]
            staged = subprocess.run(command, capture_output=True, text=True, timeout=30)
            self.assertEqual(staged.returncode, 0, staged.stderr)
            self.assertIn("public SDK provenance NOT verified", staged.stdout)
            release = subprocess.run(command + ["--release"], capture_output=True,
                                     text=True, timeout=30)
            self.assertNotEqual(release.returncode, 0)
            self.assertIn("a staged SDK cannot pass the release gate", release.stderr)
            with zipfile.ZipFile(apk, "w") as archive:
                for abi in CHECKER["EXPECTED_ABIS"]:
                    data = (libraries / abi / CHECKER["EXPECTED_LIBRARY"]).read_bytes()
                    archive.writestr(f"lib/{abi}/{CHECKER['EXPECTED_LIBRARY']}", data + b"tampered")
            tampered = subprocess.run(command, capture_output=True, text=True, timeout=30)
            self.assertNotEqual(tampered.returncode, 0)
            self.assertIn("differs from the app-owned decoder", tampered.stderr)

    def test_debug_gate_accepts_a_development_pin_and_release_gate_refuses_it(self) -> None:
        pin_errors = CHECKER["sdk_pin_errors"]
        development = "0.36.0-20261024.143000-1"
        self.assertEqual(pin_errors(development, development, release=False), [])
        release = pin_errors(development, development, release=True)
        self.assertEqual(len(release), 1)
        self.assertIn("a Player release needs a released SDK", release[0])
        self.assertEqual(pin_errors("0.35.0", "0.35.0", release=True), [])
        self.assertEqual(pin_errors("0.35.0", "0.35.0", release=False), [])
        mismatch = pin_errors("0.36.0-20261024.143000-1", "0.35.0", release=False)
        self.assertTrue(any("publication evidence must be updated" in e for e in mismatch))
        for version in ("0.36.0-SNAPSHOT", "0.36.0-rc1"):
            with self.subTest(version=version):
                malformed = pin_errors(version, version, release=False)
                self.assertTrue(any("neither a release nor a development build" in e for e in malformed))
                refused = pin_errors(version, version, release=True)
                self.assertTrue(any("a Player release needs a released SDK" in e for e in refused))

    def test_source_gate_rejects_every_missing_required_legal_or_build_entry(self) -> None:
        required = CHECKER["REQUIRED_SOURCE_ENTRIES"]
        members = []
        for name in required:
            member = tarfile.TarInfo(name)
            member.size = 1
            member.type = tarfile.REGTYPE
            members.append(member)
        errors: list[str] = []
        CHECKER["validate_source_members"](members, errors)
        self.assertEqual(errors, [])

        for removed in required:
            with self.subTest(removed=removed):
                candidate = [copy.copy(member) for member in members if member.name != removed]
                mutation_errors: list[str] = []
                CHECKER["validate_source_members"](candidate, mutation_errors)
                self.assertTrue(mutation_errors, f"accepted source archive without {removed}")

    def test_source_gate_rejects_duplicate_unsafe_and_special_entries(self) -> None:
        member = tarfile.TarInfo("ffmpeg/LICENSE.md")
        member.type = tarfile.REGTYPE
        duplicate_errors: list[str] = []
        CHECKER["validate_source_members"]([member, copy.copy(member)], duplicate_errors)
        self.assertTrue(any("duplicate" in error for error in duplicate_errors))

        unsafe = tarfile.TarInfo("../credential")
        unsafe.type = tarfile.REGTYPE
        unsafe_errors: list[str] = []
        CHECKER["validate_source_members"]([unsafe], unsafe_errors)
        self.assertTrue(any("unsafe paths" in error for error in unsafe_errors))

        hard_link = tarfile.TarInfo("ffmpeg/nested/hard-link")
        hard_link.type = tarfile.LNKTYPE
        hard_link.linkname = "../outside"
        hard_link_errors: list[str] = []
        CHECKER["validate_source_members"]([hard_link], hard_link_errors)
        self.assertTrue(any("unsafe links" in error for error in hard_link_errors))

        special = tarfile.TarInfo("ffmpeg/device")
        special.type = tarfile.CHRTYPE
        special_errors: list[str] = []
        CHECKER["validate_source_members"]([special], special_errors)
        self.assertTrue(any("special entries" in error for error in special_errors))

    def test_alignment_contract_targets_only_shipped_64_bit_abis(self) -> None:
        self.assertEqual(
            CHECKER["EXPECTED_ABIS"],
            ("arm64-v8a", "armeabi-v7a", "x86", "x86_64"),
        )
        self.assertEqual(CHECKER["ALIGNMENT_ABIS"], ("arm64-v8a", "x86_64"))
        self.assertEqual(CHECKER["EXPECTED_MINIMUM_ALIGNMENT"], 16384)

    def test_apk_decoder_bytes_must_match_app_owned_bytes(self) -> None:
        elf = bytearray(120)
        elf[:6] = b"\x7fELF\x02\x01"
        struct.pack_into("<Q", elf, 32, 64)
        struct.pack_into("<H", elf, 54, 56)
        struct.pack_into("<H", elf, 56, 1)
        struct.pack_into("<I", elf, 64, CHECKER["PT_LOAD"])
        struct.pack_into("<Q", elf, 112, CHECKER["EXPECTED_MINIMUM_ALIGNMENT"])
        decoder = bytes(elf)
        aar_libraries = {abi: decoder for abi in CHECKER["EXPECTED_ABIS"]}

        with tempfile.TemporaryDirectory() as directory:
            apk = Path(directory) / "app.apk"
            with zipfile.ZipFile(apk, mode="w") as archive:
                for abi in CHECKER["EXPECTED_ABIS"]:
                    archive.writestr(f"lib/{abi}/{CHECKER['EXPECTED_LIBRARY']}", decoder)

            matching_errors: list[str] = []
            CHECKER["validate_apk"](apk, aar_libraries, matching_errors)
            self.assertEqual(matching_errors, [])

            mismatched_libraries = dict(aar_libraries)
            mismatched_libraries["arm64-v8a"] = decoder + b"different"
            mismatch_errors: list[str] = []
            CHECKER["validate_apk"](apk, mismatched_libraries, mismatch_errors)
            self.assertTrue(any("differs" in error for error in mismatch_errors))

if __name__ == "__main__":
    unittest.main()
