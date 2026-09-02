#!/usr/bin/env python3

import importlib.util
import json
import tempfile
import unittest
from pathlib import Path
from unittest import mock


MODULE_PATH = Path(__file__).with_name("modrinth_publish.py")
SPEC = importlib.util.spec_from_file_location("modrinth_publish", MODULE_PATH)
modrinth_publish = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(modrinth_publish)


class ModrinthPublishTest(unittest.TestCase):
    def manifest(self):
        return {
            "tag": "1.5.4",
            "title": "Release title",
            "body": "Changes",
            "modrinth_version_number": "1.5.4",
            "modrinth_project_id": "Nfhbipsz",
            "is_all_versions": True,
            "all_versions": ["1.21.1", "1.21.3"],
            "all_entries": [
                {"jar_name": "carpet-fga-addition-1.5.4-mc1.21-1.21.1.jar"},
                {"jar_name": "carpet-fga-addition-1.5.4-mc1.21.2-1.21.3.jar"},
            ],
            "files": [
                {
                    "build_project": "1.21.1",
                    "artifact_mc_version": "1.21-1.21.1",
                    "jar_name": "carpet-fga-addition-1.5.4-mc1.21-1.21.1.jar",
                    "game_versions": ["1.21", "1.21.1"],
                    "modrinth_dependencies": [
                        {"project_id": "TQTTVgYE", "dependency_type": "required"},
                        {"project_id": "P7dR8mSH", "dependency_type": "required"},
                    ],
                },
                {
                    "build_project": "1.21.3",
                    "artifact_mc_version": "1.21.2-1.21.3",
                    "jar_name": "carpet-fga-addition-1.5.4-mc1.21.2-1.21.3.jar",
                    "game_versions": ["1.21.2", "1.21.3"],
                    "modrinth_dependencies": [
                        {"project_id": "TQTTVgYE", "dependency_type": "required"},
                        {"project_id": "P7dR8mSH", "dependency_type": "required"},
                    ],
                },
            ],
        }

    def test_expected_version_number_is_exact_tag(self):
        expected = modrinth_publish.expected_metadata(self.manifest(), "Nfhbipsz", "1.21.1")
        self.assertEqual("1.5.4", expected["name"])
        self.assertEqual("1.5.4", expected["version_number"])
        self.assertEqual(["fabric"], expected["loaders"])
        self.assertEqual(["1.21", "1.21.1"], expected["game_versions"])

    def test_project_slug_may_resolve_to_expected_canonical_id(self):
        self.assertEqual(
            "Nfhbipsz",
            modrinth_publish.validate_project_identity(self.manifest(), "Nfhbipsz"),
        )

    def test_wrong_canonical_project_id_fails_before_publication(self):
        with self.assertRaises(modrinth_publish.ModrinthError):
            modrinth_publish.validate_project_identity(self.manifest(), "wrong-project")

    def test_wrong_manifest_project_id_fails_before_publication(self):
        manifest = self.manifest()
        manifest["modrinth_project_id"] = "wrong-project"
        with self.assertRaises(modrinth_publish.ModrinthError):
            modrinth_publish.validate_project_identity(manifest, "Nfhbipsz")

    def test_publish_stops_after_resolving_wrong_canonical_project(self):
        with tempfile.TemporaryDirectory() as temporary:
            manifest_path = Path(temporary) / "manifest.json"
            manifest_path.write_text(json.dumps(self.manifest()), encoding="utf-8")
            with mock.patch.object(
                modrinth_publish.ModrinthClient,
                "request",
                return_value={"id": "wrong-project"},
            ) as request:
                with self.assertRaises(modrinth_publish.ModrinthError):
                    modrinth_publish.publish(
                        manifest_path,
                        "token",
                        "configured-slug",
                        "1.21.1",
                    )
            request.assert_called_once_with("GET", "/project/configured-slug")

    def test_title_and_changelog_changes_are_patchable(self):
        expected = modrinth_publish.expected_metadata(self.manifest(), "Nfhbipsz", "1.21.1")
        existing = dict(expected)
        existing["name"] = "Old title"
        existing["changelog"] = "Old changes"
        patch = modrinth_publish.mutable_patch(existing, expected)
        self.assertEqual("1.5.4", patch["name"])
        self.assertEqual("Changes", patch["changelog"])
        self.assertEqual(2, len(patch))

    def test_legacy_minecraft_suffix_only_patches_name(self):
        expected = modrinth_publish.expected_metadata(self.manifest(), "Nfhbipsz", "1.21.1")
        existing = dict(expected)
        existing["name"] = "Release title for Minecraft 1.21-1.21.1"
        self.assertEqual({"name": "1.5.4"}, modrinth_publish.mutable_patch(existing, expected))

    def test_remote_dependency_null_fields_do_not_cause_a_patch_loop(self):
        expected = modrinth_publish.expected_metadata(self.manifest(), "Nfhbipsz", "1.21.1")
        existing = dict(expected)
        existing["dependencies"] = [
            {**dependency, "version_id": None, "file_name": None}
            for dependency in expected["dependencies"]
        ]
        self.assertEqual({}, modrinth_publish.mutable_patch(existing, expected))

    def test_different_existing_hash_fails(self):
        remote = [{"filename": "a.jar", "hashes": {"sha512": "remote"}}]
        local = {"a.jar": {"sha512": "local"}}
        with self.assertRaises(modrinth_publish.ModrinthError):
            modrinth_publish.validate_existing_files(remote, local)

    def test_same_tag_versions_are_disambiguated_by_expected_filename(self):
        versions = [
            {
                "id": "a",
                "version_number": "1.5.4",
                "files": [{"filename": "carpet-fga-addition-1.5.4-mc1.21.2-1.21.3.jar"}],
            },
            {
                "id": "b",
                "version_number": "1.5.4",
                "files": [{"filename": "carpet-fga-addition-1.5.4-mc1.21-1.21.1.jar"}],
            },
        ]
        found = modrinth_publish.find_existing_version(
            versions,
            "carpet-fga-addition-1.5.4-mc1.21-1.21.1.jar",
            {entry["jar_name"] for entry in self.manifest()["all_entries"]},
        )
        self.assertEqual("b", found["id"])

    def test_empty_orphan_version_is_removed_before_matching(self):
        client = mock.Mock()
        versions = [
            {
                "id": "orphan",
                "version_number": "1.5.4",
                "files": [],
                "game_versions": [],
                "loaders": [],
            },
            {
                "id": "valid",
                "version_number": "1.5.4",
                "files": [{"filename": "carpet-fga-addition-1.5.4-mc1.21-1.21.1.jar"}],
                "game_versions": ["1.21", "1.21.1"],
                "loaders": ["fabric"],
            },
        ]
        remaining = modrinth_publish.remove_empty_orphan_versions(client, versions, "1.5.4")
        self.assertEqual(["valid"], [version["id"] for version in remaining])
        client.request.assert_called_once_with("DELETE", "/version/orphan")


if __name__ == "__main__":
    unittest.main()
