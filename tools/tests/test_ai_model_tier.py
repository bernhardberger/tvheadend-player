from __future__ import annotations

import json
import runpy
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
TOOL = runpy.run_path(str(ROOT / "tools/ai-model-tier"), run_name="ai_model_tier_tool")
AGENT_MODELS = TOOL["AGENT_MODELS"]
load_config = TOOL["load_config"]
model_tier = TOOL["model_tier"]
switch_model_tier = TOOL["switch_model_tier"]


class AiModelTierTest(unittest.TestCase):
    def config(self, *, fast: bool) -> dict[str, object]:
        suffix = "-fast" if fast else ""
        return {
            "$schema": "https://opencode.ai/config.json",
            "agent": {
                name: {
                    "model": model if model == "openai/gpt-6-astra" else f"{model}{suffix}",
                    "variant": "medium",
                    "steps": 48,
                    "permission": {
                        "task": {"*": "deny", "app-locator": "allow"}
                    },
                }
                for name, model in AGENT_MODELS.items()
            },
            "share": "disabled",
        }

    def write_config(self, directory: str, config: dict[str, object]) -> Path:
        path = Path(directory) / "opencode.json"
        path.write_text(json.dumps(config, indent=2) + "\n", encoding="utf-8")
        return path

    def test_switches_all_child_models_to_fast_without_changing_other_fields(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = self.write_config(directory, self.config(fast=False))

            changed = switch_model_tier(path, "fast")
            updated = load_config(path)

            self.assertTrue(changed)
            self.assertEqual(model_tier(updated), "fast")
            self.assertEqual(updated["share"], "disabled")
            for name, model in AGENT_MODELS.items():
                agent = updated["agent"][name]
                self.assertEqual(
                    agent["model"],
                    model if model == "openai/gpt-6-astra" else f"{model}-fast",
                )
                self.assertEqual(agent["variant"], "medium")
                self.assertEqual(agent["steps"], 48)
                self.assertEqual(
                    agent["permission"],
                    {"task": {"*": "deny", "app-locator": "allow"}},
                )

    def test_switches_fast_models_back_to_standard(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = self.write_config(directory, self.config(fast=True))

            changed = switch_model_tier(path, "standard")
            updated = load_config(path)

            self.assertTrue(changed)
            self.assertEqual(model_tier(updated), "standard")
            for name, model in AGENT_MODELS.items():
                self.assertEqual(updated["agent"][name]["model"], model)

    def test_repeated_switch_is_a_no_op(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = self.write_config(directory, self.config(fast=True))
            original = path.read_bytes()

            changed = switch_model_tier(path, "fast")

            self.assertFalse(changed)
            self.assertEqual(path.read_bytes(), original)

    def test_rejects_unexpected_model_without_modifying_config(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            config = self.config(fast=True)
            config["agent"]["app-locator"]["model"] = "other/model"
            path = self.write_config(directory, config)
            original = path.read_bytes()

            with self.assertRaisesRegex(ValueError, "app-locator.*unexpected model"):
                switch_model_tier(path, "standard")

            self.assertEqual(path.read_bytes(), original)

    def test_manages_only_read_only_child_roles(self) -> None:
        self.assertEqual(
            set(AGENT_MODELS),
            {
                "app-locator",
                "app-explore",
                "app-planner",
                "app-analyze",
                "app-research",
                "android-reviewer",
                "tv-evidence-curator",
            },
        )
        self.assertEqual(
            set(AGENT_MODELS.values()),
            {
                "openai/gpt-5.6-luna",
                "openai/gpt-5.6-terra",
                "openai/gpt-6-astra",
            },
        )

    def test_rejects_unregistered_astra_fast_without_modifying_config(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            config = self.config(fast=False)
            config["agent"]["app-planner"]["model"] = "openai/gpt-6-astra-fast"
            path = self.write_config(directory, config)
            original = path.read_bytes()
            with self.assertRaisesRegex(ValueError, "app-planner.*unexpected model"):
                switch_model_tier(path, "standard")
            self.assertEqual(path.read_bytes(), original)

    def test_reports_mixed_tier(self) -> None:
        config = self.config(fast=True)
        config["agent"]["app-locator"]["model"] = AGENT_MODELS["app-locator"]

        self.assertEqual(model_tier(config), "mixed")


if __name__ == "__main__":
    unittest.main()
