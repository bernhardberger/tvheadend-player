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
        self.assertEqual(CHECKER["SDK_COORDINATE"], "at.bernhardberger.tvheadend:sdk-media3:0.4.0")
        self.assertEqual(
            CHECKER["EXPECTED_AAR_SHA256"],
            "c162c6e6a08078af9823e3db4c43e229cb7ef861cfcb4bab050398d8742bd826",
        )
        self.assertEqual(
            CHECKER["EXPECTED_FFMPEG_SOURCES_SHA256"],
            "9eeca8490f794574185986c0df7800d65ccca2980f57dc26b630a398581d7929",
        )
        self.assertEqual(len(CHECKER["EXPECTED_STANDARD_SOURCE_SHA256"]), 4)
        self.assertEqual(
            CHECKER["EXPECTED_STANDARD_SOURCE_SHA256"],
            {
                "sdk-android-0.4.0-sources.jar":
                    "a3b4ec913b611f536f89b66dd16445307bf71c07ec116c7b69ddf358b0c7edb0",
                "sdk-core-0.4.0-sources.jar":
                    "2fb0b821aa60aef7974554a581928f5fcb53cec3783b310fbd178cd97d8fb254",
                "sdk-media3-0.4.0-sources.jar":
                    "82753388fe681c0b3ee3c59fba08ca8e22cd2ef8455bf1848e1d36e6e078b475",
                "sdk-playback-0.4.0-sources.jar":
                    "cab46ff691302d24ea8192631a9b1a131e5009cd9ac3b709013bb24c0eed9a26",
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
