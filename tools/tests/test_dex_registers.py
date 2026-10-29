import sys
import tempfile
import unittest
import zipfile
from pathlib import Path


sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "tools"))

from check_dex_registers import check_apk, method_registers


def dump(owner, registers, name="render", signature="()V"):
    return (
        f"  Class descriptor  : '{owner}'\n"
        f"      name          : '{name}'\n"
        f"      type          : '{signature}'\n"
        "      code          -\n"
        f"      registers     : {registers}\n"
    )


class DexRegistersTest(unittest.TestCase):
    def setUp(self):
        self.temporary_directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary_directory.cleanup)
        self.root = Path(self.temporary_directory.name)
        self.apk = self.root / "app.apk"
        self.dexdump = self.root / "dexdump"
        # A local fake reads the canned dump stored in each classes*.dex entry.
        self.dexdump.write_text("#!/usr/bin/env python3\nimport pathlib, sys\nprint(pathlib.Path(sys.argv[2]).read_text())\n")
        self.dexdump.chmod(0o700)

    def write_apk(self, entries):
        with zipfile.ZipFile(self.apk, "w") as archive:
            for name, content in entries.items():
                archive.writestr(name, content)

    def test_parses_overloads_and_resets_method_identity(self):
        text = dump("Lat/bernhardberger/tvhplayer/ScreenKt;", 201)
        text += "      name          : 'render'\n      type          : '(J)V'\n      registers     : 202\n"
        records = list(method_registers(text.splitlines()))
        self.assertEqual([("()V", 201), ("(J)V", 202)], [(row[2], row[3]) for row in records])
        with self.assertRaisesRegex(ValueError, "lacks a method identity"):
            list(method_registers(["      registers     : 201"]))

    def test_checks_all_dex_files_and_only_exact_app_package(self):
        self.write_apk({
            "classes.dex": dump("Lat/bernhardberger/tvhplayer/ScreenKt;", 200),
            "classes2.dex": dump("Llibrary/Screen;", 500)
                + dump("Lat/bernhardberger/tvhplayerextra/Screen;", 400)
                + dump("Lat/bernhardberger/tvhplayer/ui/OtherKt;", 201),
            "classes10.dex": dump("Lat/bernhardberger/tvhplayer/ui/LastKt;", 203),
            "assets/classes.dex": dump("Lat/bernhardberger/tvhplayer/Fake;", 900),
        })
        violations, count, maximum = check_apk(self.apk, self.dexdump)
        self.assertEqual((3, 203), (count, maximum))
        self.assertEqual(2, len(violations))
        self.assertTrue(any("OtherKt;->render()V: 201 registers (limit 200)" in item for item in violations))
        self.assertTrue(any("classes10.dex:" in item for item in violations))

    def test_accepts_limit_inclusive(self):
        self.write_apk({"classes.dex": dump("Lat/bernhardberger/tvhplayer/ScreenKt;", 200)})
        self.assertEqual(([], 1, 200), check_apk(self.apk, self.dexdump))

    def test_tolerates_non_utf8_dex_string_contents(self):
        self.dexdump.write_text("#!/usr/bin/env python3\nimport pathlib, sys\nsys.stdout.buffer.write(pathlib.Path(sys.argv[2]).read_bytes())\n")
        self.write_apk({"classes.dex": b"string contents: \xed\xa0\x80\n" + dump(
            "Lat/bernhardberger/tvhplayer/ScreenKt;", 199,
        ).encode()})
        self.assertEqual(([], 1, 199), check_apk(self.apk, self.dexdump))

    def test_rejects_missing_dex_or_app_records(self):
        self.write_apk({"assets/example": "not dex"})
        with self.assertRaisesRegex(ValueError, "no classes dex"):
            check_apk(self.apk, self.dexdump)
        self.write_apk({"classes.dex": dump("Lobfuscated;", 200)})
        with self.assertRaisesRegex(ValueError, "no app method register records"):
            check_apk(self.apk, self.dexdump)

    def test_dexdump_failure_is_not_a_pass(self):
        self.write_apk({"classes.dex": dump("Lat/bernhardberger/tvhplayer/ScreenKt;", 200)})
        self.dexdump.write_text("#!/usr/bin/env python3\nimport sys\nsys.exit(7)\n")
        with self.assertRaisesRegex(ValueError, "dexdump failed for classes.dex \\(exit 7\\)"):
            check_apk(self.apk, self.dexdump)


if __name__ == "__main__":
    unittest.main()
