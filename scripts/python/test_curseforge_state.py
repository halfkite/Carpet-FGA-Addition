#!/usr/bin/env python3

import importlib.util
import unittest
from pathlib import Path


MODULE_PATH = Path(__file__).with_name("curseforge_state.py")
SPEC = importlib.util.spec_from_file_location("curseforge_state", MODULE_PATH)
curseforge_state = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(curseforge_state)


class CurseForgeStateTest(unittest.TestCase):
    def test_marker_is_exact_and_numeric(self):
        self.assertEqual("12345", curseforge_state.marker_file_id({"label": "CF:12345"}))
        self.assertIsNone(curseforge_state.marker_file_id({"label": "CF:1.21.1"}))
        self.assertIsNone(curseforge_state.marker_file_id({"label": "notes"}))

    def test_dispatch_without_auditable_history_fails_closed(self):
        with self.assertRaises(curseforge_state.CurseForgeStateError):
            curseforge_state.assert_upload_state_is_known("workflow_dispatch", 0, [])

    def test_release_first_attempt_can_upload(self):
        curseforge_state.assert_upload_state_is_known("release", 0, [])

    def test_any_previous_upload_step_is_ambiguous(self):
        with self.assertRaises(curseforge_state.CurseForgeStateError):
            curseforge_state.assert_upload_state_is_known(
                "workflow_dispatch", 1, [{"conclusion": "success"}]
            )

    def test_game_version_names_resolve_to_numeric_ids_with_fabric(self):
        version_types = [
            {"id": 10, "slug": "minecraft-1-21"},
            {"id": 20, "slug": "modloader"},
        ]
        game_versions = [
            {"id": 1210, "gameVersionTypeID": 10, "name": "1.21"},
            {"id": 1211, "gameVersionTypeID": 10, "name": "1.21.1"},
            {"id": 4, "gameVersionTypeID": 20, "name": "Fabric"},
        ]
        self.assertEqual(
            [1210, 1211, 4],
            curseforge_state.resolve_curseforge_game_version_ids(
                ["1.21", "1.21.1"], version_types, game_versions
            ),
        )

    def test_unknown_game_version_fails_closed(self):
        with self.assertRaises(curseforge_state.CurseForgeStateError):
            curseforge_state.resolve_curseforge_game_version_ids(
                ["1.21"],
                [{"id": 10, "slug": "minecraft"}, {"id": 20, "slug": "modloader"}],
                [{"id": 4, "gameVersionTypeID": 20, "name": "Fabric"}],
            )

if __name__ == "__main__":
    unittest.main()
