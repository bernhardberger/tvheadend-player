from pathlib import Path
import subprocess
import sys
import unittest


ROOT = Path(__file__).resolve().parents[2]
CONTRACT = (ROOT / "tools/verify").read_text().split("<<'PLAY_ENTRY_CONTRACT'\n", 1)[1].split("\nPLAY_ENTRY_CONTRACT", 1)[0]
COMPANION_CONTRACT = (ROOT / "tools/verify").read_text().split("<<'COMPANION_ENTRY_CONTRACT'\n", 1)[1].split("\nCOMPANION_ENTRY_CONTRACT", 1)[0]
PERMISSION = "at.bernhardberger.tvhplayer.debug.permission.APPLIANCE_ENTRY"
COMPANION_PERMISSIONS = (PERMISSION, "android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE",
                         "android.permission.REQUEST_INSTALL_PACKAGES", "android.permission.UPDATE_PACKAGES_WITHOUT_USER_ACTION")
COMPANION_TREE = "".join(f'  E: uses-permission (line=3)\n    A: android:name(0x01010003)="{p}"\n'
                         for p in COMPANION_PERMISSIONS)
COMPANION_TREE += '''  E: application (line=10)
    A: android:usesCleartextTraffic(0x010104ec)=(type 0x12)0x0
    E: service (line=11)
      A: android:name(0x01010003)="at.bernhardberger.tvhplayer.appliance.update.CompanionUpdateJob"
      A: android:permission(0x01010006)="android.permission.BIND_JOB_SERVICE"
      A: android:exported(0x01010010)=(type 0x12)0xffffffff
    E: receiver (line=12)
      A: android:name(0x01010003)="at.bernhardberger.tvhplayer.appliance.update.UpdateInstallReceiver"
      A: android:exported(0x01010010)=(type 0x12)0x0
'''
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
    def test_companion_component_guards_belong_to_the_correct_component(self):
        for old, new in (("BIND_JOB_SERVICE", "INTERNET"),
                         ('E: service (line=11)', 'E: activity (line=11)'),
                         ('E: receiver (line=12)', 'E: activity (line=12)'),
                         ('android:exported(0x01010010)=(type 0x12)0x0',
                          'android:exported(0x01010010)=(type 0x12)0xffffffff')):
            with self.subTest(changed=old):
                result = subprocess.run([sys.executable, "-c", COMPANION_CONTRACT,
                                         COMPANION_TREE.replace(old, new)], capture_output=True, text=True)
                self.assertNotEqual(result.returncode, 0)

    def test_release_companion_rejects_cleartext_and_security_config(self):
        cleartext = '    A: android:usesCleartextTraffic(0x010104ec)=(type 0x12)0x0\n'
        def check(replacement, debug=False, release=False):
            tree = COMPANION_TREE.replace(cleartext, replacement)
            if debug:
                tree = tree.replace('  E: application (line=10)\n',
                                    '  E: application (line=10)\n    A: android:debuggable(0x0101000f)=(type 0x12)0xffffffff\n')
            if release:
                tree = tree.replace(PERMISSION, PERMISSION.replace('.debug', ''))
            return subprocess.run([sys.executable, "-c", COMPANION_CONTRACT, tree] + (["release"] if release else []),
                                  capture_output=True, text=True)
        self.assertEqual(check("").returncode, 0)
        enabled = cleartext.replace('0x0\n', '0xffffffff\n')
        self.assertNotEqual(check(enabled).returncode, 0)
        self.assertEqual(check(enabled, debug=True).returncode, 0)
        self.assertEqual(check(cleartext, release=True).returncode, 0)
        self.assertNotEqual(check(enabled, debug=True, release=True).returncode, 0)
        self.assertNotEqual(check(cleartext + '    A: android:networkSecurityConfig(0x01010527)=@0x7f010001\n').returncode, 0)

    def test_companion_requests_player_entry_permission(self):
        tree = COMPANION_TREE
        result = subprocess.run([sys.executable, "-c", COMPANION_CONTRACT, tree], capture_output=True, text=True)
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_companion_permission_on_another_element_or_wrong_package_fails(self):
        for element, permission in (("permission", PERMISSION), ("uses-permission", "other.permission")):
            tree = f'  E: {element} (line=3)\n    A: android:name(0x01010003)="{permission}"\n'
            result = subprocess.run([sys.executable, "-c", COMPANION_CONTRACT, tree], capture_output=True, text=True)
            self.assertNotEqual(result.returncode, 0)

    def test_every_companion_update_permission_is_required_on_uses_permission(self):
        for permission in COMPANION_PERMISSIONS:
            entry = f'  E: uses-permission (line=3)\n    A: android:name(0x01010003)="{permission}"\n'
            for replacement in ("", entry.replace("E: uses-permission", "E: permission")):
                with self.subTest(permission=permission, replacement=replacement):
                    result = subprocess.run([sys.executable, "-c", COMPANION_CONTRACT,
                                             COMPANION_TREE.replace(entry, replacement)], capture_output=True, text=True)
                    self.assertNotEqual(result.returncode, 0)

    def check_contract(self, tree):
        return subprocess.run([sys.executable, "-c", CONTRACT, tree], capture_output=True, text=True)

    def test_signature_permission_and_guarded_alias_pass(self):
        result = self.check_contract(TREE)
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_player_keeps_network_but_never_requests_install_permissions(self):
        for element in ("uses-permission", "uses-permission-sdk-23"):
            def entry(permission):
                return f'  E: {element} (line=3)\n    A: android:name(0x01010003)="{permission}"\n'
            self.assertEqual(self.check_contract(TREE + entry("android.permission.INTERNET")).returncode, 0)
            for permission in ("android.permission.REQUEST_INSTALL_PACKAGES", "android.permission.UPDATE_PACKAGES_WITHOUT_USER_ACTION"):
                self.assertNotEqual(self.check_contract(TREE + entry(permission)).returncode, 0)

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
