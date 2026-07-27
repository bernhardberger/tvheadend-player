from __future__ import annotations

import json
import runpy
import stat
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
RELEASE = runpy.run_path(str(ROOT / "tools/release"), run_name="release_tool")


class ReleaseOrchestratorTest(unittest.TestCase):
    def test_config_must_be_owner_only(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "release.json"
            path.write_text(json.dumps(self._config()), encoding="utf-8")
            path.chmod(stat.S_IRUSR | stat.S_IWUSR | stat.S_IRGRP)

            with self.assertRaisesRegex(ValueError, "owner-only"):
                RELEASE["load_config"](path)

    def test_config_rejects_ssh_options_and_relative_remote_paths(self) -> None:
        config = self._config()
        config["signingHost"] = "-oProxyCommand=unsafe"
        with self.assertRaisesRegex(ValueError, "SSH host"):
            RELEASE["validate_config"](config)

        config = self._config()
        config["remoteReleaseRoot"] = "relative/releases"
        with self.assertRaisesRegex(ValueError, "absolute remote path"):
            RELEASE["validate_config"](config)

    def test_release_readiness_requires_clean_pushed_head(self) -> None:
        errors = RELEASE["release_readiness_errors"](
            porcelain=" M README.md\n",
            head="a" * 40,
            upstream_head="b" * 40,
            head_is_upstream_ancestor=False,
        )

        self.assertIn("worktree is not clean", errors)
        self.assertIn("HEAD is not present on the configured upstream", errors)

    def test_remote_sign_command_uses_trusted_checkout_without_passwords(self) -> None:
        command = RELEASE["remote_sign_command"](
            config=self._config(),
            source_commit="a" * 40,
            incoming_dir="/srv/releases/session/incoming/0.1.0",
            signed_dir="/srv/releases/session/signed/0.1.0",
        )

        self.assertIn("git checkout --detach", command)
        self.assertIn("tools/sign-release", command)
        self.assertNotIn("password", command.lower())
        self.assertNotIn("TVHPLAYER_RELEASE_", command)

    def test_parser_keeps_device_deployment_outside_release_tool(self) -> None:
        parser = RELEASE["build_parser"]()
        actions = parser._subparsers._group_actions[0].choices

        self.assertEqual(set(actions), {"prepare", "sign", "verify-signed"})

    def test_remote_orchestration_is_non_interactive(self) -> None:
        release = (ROOT / "tools/release").read_text(encoding="utf-8")

        self.assertNotIn('"-tt"', release)

    @staticmethod
    def _config() -> dict[str, object]:
        return {
            "schemaVersion": 1,
            "signingHost": "release-signer",
            "signingRepository": "/srv/tvheadend-player",
            "signingKeystore": "/secure/tvheadend-player/release.jks",
            "remoteReleaseRoot": "/srv/tvheadend-player-releases",
            "signingRemote": "fork",
            "signingBranch": "main",
        }


if __name__ == "__main__":
    unittest.main()
