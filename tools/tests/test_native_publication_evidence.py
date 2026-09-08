from __future__ import annotations

import copy
import runpy
import struct
import tarfile
import tempfile
import unittest
import zipfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CHECKER_PATH = ROOT / "tools/check-native-libs"
CHECKER = runpy.run_path(str(CHECKER_PATH), run_name="native_checker_test")


class NativePublicationEvidenceTest(unittest.TestCase):
    def test_public_release_bytes_and_corresponding_sources_are_pinned(self) -> None:
        self.assertEqual(CHECKER["SDK_COORDINATE"], "at.bernhardberger.tvheadend:sdk-media3:0.9.1")
        self.assertEqual(
            CHECKER["EXPECTED_AAR_SHA256"],
            "19a43aac323c82de406361605cf9c8ab4b634265c0e7215df00bba654cfd0555",
        )
        self.assertEqual(
            CHECKER["EXPECTED_FFMPEG_SOURCES_SHA256"],
            "9eeca8490f794574185986c0df7800d65ccca2980f57dc26b630a398581d7929",
        )
        self.assertEqual(len(CHECKER["EXPECTED_STANDARD_SOURCE_SHA256"]), 4)
        self.assertEqual(
            CHECKER["EXPECTED_STANDARD_SOURCE_SHA256"],
            {
                "sdk-android-0.9.1-sources.jar":
                    "605da05e61b13fb4b5c18a87a9009e3ac95503cb72e66b25f3dcbfc9142f5695",
                "sdk-core-0.9.1-sources.jar":
                    "bb55ec643a62e5c733a7792b808853c9ac963cb4012940141bfbb0bc6975057f",
                "sdk-media3-0.9.1-sources.jar":
                    "95be340a06b7d9d20064fc5964f6a4f50398542d8fb663122a908d108047fccd",
                "sdk-playback-0.9.1-sources.jar":
                    "5be9abf8f13970b55ab50de354bf88b034c34de83f8d4b97145bdf0239c3471f",
            },
        )

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

    def test_apk_decoder_bytes_must_match_released_aar_bytes(self) -> None:
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
