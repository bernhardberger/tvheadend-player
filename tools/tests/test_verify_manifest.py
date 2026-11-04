from pathlib import Path
import subprocess
import sys
import unittest


ROOT = Path(__file__).resolve().parents[2]
CONTRACT = (ROOT / "tools/verify").read_text().split("<<'PLAY_ENTRY_CONTRACT'\n", 1)[1].split("\nPLAY_ENTRY_CONTRACT", 1)[0]
COMPANION_CONTRACT = (ROOT / "tools/verify").read_text().split("<<'COMPANION_ENTRY_CONTRACT'\n", 1)[1].split("\nCOMPANION_ENTRY_CONTRACT", 1)[0]
PERMISSION = "at.bernhardberger.tvhplayer.debug.permission.APPLIANCE_ENTRY"
TREE = f'''  E: manifest (line=2)
    E: permission (line=13)
      A: android:name(0x01010003)="{PERMISSION}"
      A: android:protectionLevel(0x01010009)=(type 0x11)0x2
    E: application (line=37)
      E: activity-alias (line=73)
        A: android:name(0x01010003)="at.bernhardberger.tvhplayer.ui.ApplianceEntryActivity"
        A: android:targetActivity(0x01010202)="at.bernhardberger.tvhplayer.ui.MainActivity"
        A: android:exported(0x01010010)=(type 0x12)0xffffffff
        A: android:permission(0x01010006)="{PERMISSION}"
        E: intent-filter (line=78)
'''


class VerifyManifestTest(unittest.TestCase):
    def test_companion_requests_player_entry_permission(self):
        tree = f'  E: uses-permission (line=3)\n    A: android:name(0x01010003)="{PERMISSION}"\n'
        result = subprocess.run([sys.executable, "-c", COMPANION_CONTRACT, tree], capture_output=True, text=True)
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_companion_permission_on_another_element_or_wrong_package_fails(self):
        for element, permission in (("permission", PERMISSION), ("uses-permission", "other.permission")):
            tree = f'  E: {element} (line=3)\n    A: android:name(0x01010003)="{permission}"\n'
            result = subprocess.run([sys.executable, "-c", COMPANION_CONTRACT, tree], capture_output=True, text=True)
            self.assertNotEqual(result.returncode, 0)

    def check_contract(self, tree):
        return subprocess.run([sys.executable, "-c", CONTRACT, tree], capture_output=True, text=True)

    def test_signature_permission_and_guarded_alias_pass(self):
        result = self.check_contract(TREE)
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_missing_or_weakened_contract_fails(self):
        for text in ("E: permission", "E: activity-alias", "android:permission(", "android:targetActivity("):
            with self.subTest(missing=text):
                self.assertNotEqual(self.check_contract(TREE.replace(text, "removed")).returncode, 0)
        for old, new in (("(type 0x11)0x2", "(type 0x11)0x0"),
                         ("(type 0x12)0xffffffff", "(type 0x12)0x0"),
                         (PERMISSION, "other.permission")):
            with self.subTest(changed=old):
                self.assertNotEqual(self.check_contract(TREE.replace(old, new)).returncode, 0)

    def test_permission_on_another_component_does_not_guard_alias(self):
        tree = TREE.replace(f'        A: android:permission(0x01010006)="{PERMISSION}"\n', "")
        tree += f'      E: activity\n        A: android:permission(0x01010006)="{PERMISSION}"\n'
        self.assertNotEqual(self.check_contract(tree).returncode, 0)
