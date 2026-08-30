#!/usr/bin/env python3

import hashlib
import importlib.util
import json
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest import mock


MODULE_PATH = Path(__file__).with_name("curseforge_state.py")
SPEC = importlib.util.spec_from_file_location("curseforge_state", MODULE_PATH)
curseforge_state = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(curseforge_state)


class CurseForgeStateTest(unittest.TestCase):
    repository = "halfkite/Carpet-FGA-Addition"
    project_id = "1660840"

    @staticmethod
    def git(path: Path, *arguments: str) -> str:
        result = subprocess.run(
            ["git", *arguments],
            cwd=path,
            text=True,
            encoding="utf-8",
            capture_output=True,
            check=True,
        )
        return result.stdout.strip()

    def make_remote_with_old_release(self, root: Path, *, annotated: bool = False):
        remote = root / "remote.git"
        source = root / "source"
        checkout = root / "checkout"
        self.git(root, "init", "--bare", str(remote))
        source.mkdir()
        self.git(source, "init", "-b", "main")
        self.git(source, "config", "user.name", "Test")
        self.git(source, "config", "user.email", "test@example.com")
        (source / "release.txt").write_text("release", encoding="utf-8")
        self.git(source, "add", "release.txt")
        self.git(source, "commit", "-m", "release")
        release_commit = self.git(source, "rev-parse", "HEAD")
        if annotated:
            self.git(source, "tag", "-a", "1.5.4", "-m", "1.5.4")
        else:
            self.git(source, "tag", "1.5.4")
        (source / "main.txt").write_text("new main", encoding="utf-8")
        self.git(source, "add", "main.txt")
        self.git(source, "commit", "-m", "main advances")
        self.git(source, "remote", "add", "origin", str(remote))
        self.git(source, "push", "origin", "main", "refs/tags/1.5.4")
        remote_url = remote.resolve().as_uri()
        self.git(root, "clone", "--depth=1", "--branch", "main", remote_url, str(checkout))
        return remote, checkout, release_commit

    @staticmethod
    def all_entries():
        return {
            "1.21.1": {
                "build_project": "1.21.1",
                "jar_name": "carpet-fga-addition-1.5.4-mc1.21-1.21.1.jar",
            },
            "1.21.3": {
                "build_project": "1.21.3",
                "jar_name": "carpet-fga-addition-1.5.4-mc1.21.2-1.21.3.jar",
            },
        }

    def note_state(self, commit: str, files: list[dict] | None = None):
        if files is None:
            files = [
                {
                    "build_project": "1.21.1",
                    "jar_name": self.all_entries()["1.21.1"]["jar_name"],
                    "github_asset_id": 100,
                    "sha256": "a" * 64,
                    "curseforge_file_id": "8762071",
                }
            ]
        return {
            "schema": 1,
            "repository": self.repository,
            "tag": "1.5.4",
            "release_commit": commit,
            "curseforge_project_id": self.project_id,
            "files": files,
        }

    def test_expected_project_id_is_accepted(self):
        self.assertEqual("1660840", curseforge_state.validate_project_identity("1660840"))

    def test_wrong_project_id_fails_before_publication(self):
        with self.assertRaises(curseforge_state.CurseForgeStateError):
            curseforge_state.validate_project_identity("9999999")

    def test_update_stops_before_metadata_lookup_for_wrong_project(self):
        with mock.patch.object(
            curseforge_state, "curseforge_game_version_ids"
        ) as game_version_ids, mock.patch.object(
            curseforge_state.urllib.request, "urlopen"
        ) as urlopen:
            with self.assertRaises(curseforge_state.CurseForgeStateError):
                curseforge_state.update_curseforge_metadata(
                    Path("missing-manifest.json"),
                    "1.21.1",
                    "9999999",
                    "12345",
                    "token",
                )
        game_version_ids.assert_not_called()
        urlopen.assert_not_called()

    def test_marker_is_exact_numeric_and_malformed_marker_fails(self):
        self.assertEqual("12345", curseforge_state.marker_file_id({"label": "CF:12345"}))
        self.assertIsNone(curseforge_state.marker_file_id({"label": "notes"}))
        with self.assertRaises(curseforge_state.CurseForgeStateError):
            curseforge_state.checked_legacy_file_id({"name": "file.jar", "label": "CF:1.21.1"})

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

    def test_shallow_main_checkout_fetches_old_lightweight_release_tag(self):
        with tempfile.TemporaryDirectory() as temporary:
            _, checkout, release_commit = self.make_remote_with_old_release(Path(temporary))
            with self.assertRaises(subprocess.CalledProcessError):
                self.git(checkout, "cat-file", "-e", f"{release_commit}^{{commit}}")
            notes_ref, exists = curseforge_state.prepare_git_repository(
                checkout, "1.5.4", release_commit
            )
            self.assertEqual(
                "refs/notes/carpet-fga-addition/curseforge/1.5.4", notes_ref
            )
            self.assertFalse(exists)
            self.assertEqual(
                release_commit,
                self.git(checkout, "rev-parse", "refs/tags/1.5.4^{commit}"),
            )

    def test_annotated_tag_is_peeled_and_commit_mismatch_fails_closed(self):
        with tempfile.TemporaryDirectory() as temporary:
            _, checkout, release_commit = self.make_remote_with_old_release(
                Path(temporary), annotated=True
            )
            curseforge_state.prepare_git_repository(checkout, "1.5.4", release_commit)
            with self.assertRaises(curseforge_state.CurseForgeStateError):
                curseforge_state.prepare_git_repository(checkout, "1.5.4", "0" * 40)

    def test_git_note_round_trip_is_cross_clone_persistent(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            _, checkout, release_commit = self.make_remote_with_old_release(root)
            notes_ref, _ = curseforge_state.prepare_git_repository(
                checkout, "1.5.4", release_commit
            )
            state = self.note_state(release_commit)
            curseforge_state.write_note_state(checkout, notes_ref, release_commit, state)

            second = root / "second"
            self.git(root, "clone", "--depth=1", "--branch", "main", (root / "remote.git").resolve().as_uri(), str(second))
            fetched_ref, exists = curseforge_state.prepare_git_repository(
                second, "1.5.4", release_commit
            )
            self.assertTrue(exists)
            self.assertEqual(
                state,
                curseforge_state.read_note_state(
                    second,
                    fetched_ref,
                    exists,
                    release_commit,
                    self.repository,
                    "1.5.4",
                    self.project_id,
                    self.all_entries(),
                ),
            )

    def test_stale_concurrent_note_push_is_rejected_without_force(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            _, first, release_commit = self.make_remote_with_old_release(root)
            second = root / "second"
            self.git(
                root,
                "clone",
                "--depth=1",
                "--branch",
                "main",
                (root / "remote.git").resolve().as_uri(),
                str(second),
            )
            first_ref, _ = curseforge_state.prepare_git_repository(
                first, "1.5.4", release_commit
            )
            second_ref, _ = curseforge_state.prepare_git_repository(
                second, "1.5.4", release_commit
            )
            curseforge_state.write_note_state(
                first, first_ref, release_commit, self.note_state(release_commit)
            )
            conflicting = self.note_state(release_commit)
            conflicting["files"][0]["curseforge_file_id"] = "9999999"
            with self.assertRaises(curseforge_state.CurseForgeStateError):
                curseforge_state.write_note_state(
                    second, second_ref, release_commit, conflicting
                )

    def test_note_mapping_and_file_ids_must_be_unique(self):
        commit = "1" * 40
        duplicate = self.note_state(
            commit,
            [
                self.note_state(commit)["files"][0],
                {
                    "build_project": "1.21.3",
                    "jar_name": self.all_entries()["1.21.3"]["jar_name"],
                    "github_asset_id": 101,
                    "sha256": "b" * 64,
                    "curseforge_file_id": "8762071",
                },
            ],
        )
        with self.assertRaises(curseforge_state.CurseForgeStateError):
            curseforge_state.validate_note_state(
                duplicate,
                self.repository,
                "1.5.4",
                commit,
                self.project_id,
                self.all_entries(),
            )

    def test_corrupt_note_schema_fails_closed(self):
        state = self.note_state("1" * 40)
        state["unexpected"] = True
        with self.assertRaises(curseforge_state.CurseForgeStateError):
            curseforge_state.validate_note_state(
                state,
                self.repository,
                "1.5.4",
                "1" * 40,
                self.project_id,
                self.all_entries(),
            )

    def test_asset_snapshot_preserves_normal_filename_and_url(self):
        name = self.all_entries()["1.21.1"]["jar_name"]
        snapshot = curseforge_state.asset_snapshot(
            {
                "id": 100,
                "name": name,
                "label": "CF:8762071",
                "digest": f"sha256:{'a' * 64}",
                "browser_download_url": f"https://github.com/example/releases/download/1.5.4/{name}",
            },
            name,
            "a" * 64,
        )
        self.assertEqual(name, snapshot["name"])

    def test_note_and_legacy_label_conflict_fails_closed(self):
        commit = "1" * 40
        name = self.all_entries()["1.21.1"]["jar_name"]
        manifest = {
            "selected_versions": ["1.21.1"],
            "files": [{"build_project": "1.21.1", "jar_name": name, "sha256": "a" * 64}],
        }
        asset = {
            "id": 100,
            "name": name,
            "label": "CF:9999999",
            "digest": f"sha256:{'a' * 64}",
            "browser_download_url": f"https://github.com/example/releases/download/1.5.4/{name}",
        }
        with mock.patch.object(
            curseforge_state,
            "validate_release_inputs",
            return_value=(manifest, {"tag": "1.5.4", "commit": commit}, self.all_entries()),
        ), mock.patch.object(
            curseforge_state, "prepare_git_repository", return_value=("refs/notes/test", True)
        ), mock.patch.object(
            curseforge_state, "read_note_state", return_value=self.note_state(commit)
        ), mock.patch.object(
            curseforge_state, "release_assets", return_value=({}, [asset])
        ):
            with self.assertRaises(curseforge_state.CurseForgeStateError):
                curseforge_state.collect_state(
                    Path("manifest"),
                    Path("context"),
                    Path("repo"),
                    self.repository,
                    self.project_id,
                    "token",
                )

    def test_dispatch_repair_uses_note_without_upload(self):
        output = None
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary) / "output"
            row = {
                "jar_name": self.all_entries()["1.21.1"]["jar_name"],
                "github_asset_id": 100,
                "sha256": "a" * 64,
                "curseforge_file_id": "8762071",
                "state_source": "note",
                "legacy_label": None,
            }
            with mock.patch.object(curseforge_state, "collect_state", return_value={"rows": [row]}):
                curseforge_state.precheck(
                    Path("manifest"),
                    Path("context"),
                    Path("repo"),
                    "1.21.1",
                    self.repository,
                    self.project_id,
                    1,
                    1,
                    "workflow_dispatch",
                    "token",
                    output,
                )
            values = output.read_text(encoding="utf-8")
            self.assertIn("should_upload=false", values)
            self.assertIn("file_id=8762071", values)

    def test_mc_publish_outputs_must_all_identify_same_file(self):
        manifest = {
            "tag": "1.5.4",
            "curseforge_version": "1.5.4",
            "files": [
                {
                    "build_project": "1.21.1",
                    "jar_name": self.all_entries()["1.21.1"]["jar_name"],
                    "path": f"release-package/dist/{self.all_entries()['1.21.1']['jar_name']}",
                }
            ],
        }
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            manifest_path = root / "manifest.json"
            output = root / "output"
            manifest_path.write_text(json.dumps(manifest), encoding="utf-8")
            files = json.dumps(
                [
                    {
                        "id": 8762071,
                        "name": "1.5.4",
                        "url": "https://www.curseforge.com/api/v1/mods/1660840/files/8762071/download",
                    }
                ]
            )
            curseforge_state.validate_upload_outputs(
                manifest_path,
                "1.21.1",
                self.project_id,
                "8762071",
                files,
                "https://www.curseforge.com/minecraft/mc-mods/example/files/8762071",
                output,
            )
            self.assertEqual("file_id=8762071\n", output.read_text(encoding="utf-8"))
            with self.assertRaises(curseforge_state.CurseForgeStateError):
                curseforge_state.validate_upload_outputs(
                    manifest_path,
                    "1.21.1",
                    self.project_id,
                    "8762072",
                    files,
                    "https://www.curseforge.com/minecraft/mc-mods/example/files/8762072",
                    output,
                )

    def test_legacy_migration_records_note_before_label_cleanup(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            _, checkout, release_commit = self.make_remote_with_old_release(root)
            row = {
                "build_project": "1.21.1",
                "jar_name": self.all_entries()["1.21.1"]["jar_name"],
                "github_asset_id": 100,
                "sha256": "a" * 64,
                "curseforge_file_id": "8762071",
                "state_source": "legacy-label",
                "legacy_label": "CF:8762071",
            }
            collected = {
                "rows": [row],
                "note": None,
                "all_by_project": self.all_entries(),
                "tag": "1.5.4",
                "release_commit": release_commit,
                "notes_ref": "refs/notes/carpet-fga-addition/curseforge/1.5.4",
            }
            with mock.patch.object(curseforge_state, "collect_state", return_value=collected):
                curseforge_state.record_state(
                    Path("manifest"),
                    Path("context"),
                    checkout,
                    "1.21.1",
                    self.repository,
                    self.project_id,
                    "8762071",
                    "legacy-label",
                    "token",
                )
            notes_ref, exists = curseforge_state.prepare_git_repository(
                checkout, "1.5.4", release_commit
            )
            saved = curseforge_state.read_note_state(
                checkout,
                notes_ref,
                exists,
                release_commit,
                self.repository,
                "1.5.4",
                self.project_id,
                self.all_entries(),
            )
            self.assertEqual("8762071", saved["files"][0]["curseforge_file_id"])

    def test_cleanup_patches_only_label_then_gets_and_verifies_asset(self):
        name = self.all_entries()["1.21.1"]["jar_name"]
        digest = "a" * 64
        before = {
            "id": 100,
            "name": name,
            "label": "CF:8762071",
            "digest": f"sha256:{digest}",
            "browser_download_url": f"https://github.com/example/releases/download/1.5.4/{name}",
        }
        after = {**before, "label": None}
        calls = []

        class FakeClient:
            def __init__(self, token):
                self.headers = {}
                self.gets = 0

            def request(self, method, url, data=None):
                calls.append((method, data))
                if method == "PATCH":
                    self.assert_patch = json.loads(data)
                    return after, {}
                self.gets += 1
                return (before if self.gets == 1 else after), {}

        result = {
            "rows": [
                {
                    "build_project": "1.21.1",
                    "jar_name": name,
                    "github_asset_id": 100,
                    "sha256": digest,
                    "browser_download_url": before["browser_download_url"],
                    "curseforge_file_id": "8762071",
                    "state_source": "note",
                    "legacy_label": "CF:8762071",
                }
            ],
            "note": self.note_state("1" * 40),
        }
        with mock.patch.object(curseforge_state, "collect_state", return_value=result), mock.patch.object(
            curseforge_state, "JsonClient", FakeClient
        ):
            curseforge_state.clear_legacy_labels(
                Path("manifest"),
                Path("context"),
                Path("repo"),
                self.repository,
                self.project_id,
                "canary",
                "token",
            )
        self.assertEqual(["GET", "PATCH", "GET"], [method for method, _ in calls])
        self.assertEqual({"label": ""}, json.loads(calls[1][1]))

    def test_cleanup_stops_if_get_does_not_remove_label(self):
        name = self.all_entries()["1.21.1"]["jar_name"]
        asset = {
            "id": 100,
            "name": name,
            "label": "CF:8762071",
            "digest": f"sha256:{'a' * 64}",
            "browser_download_url": f"https://github.com/example/releases/download/1.5.4/{name}",
        }

        class FakeClient:
            def __init__(self, token):
                self.headers = {}

            def request(self, method, url, data=None):
                return asset, {}

        result = {
            "rows": [
                {
                    "build_project": "1.21.1",
                    "jar_name": name,
                    "github_asset_id": 100,
                    "sha256": "a" * 64,
                    "browser_download_url": asset["browser_download_url"],
                    "curseforge_file_id": "8762071",
                    "state_source": "note",
                    "legacy_label": "CF:8762071",
                }
            ],
            "note": self.note_state("1" * 40),
        }
        with mock.patch.object(curseforge_state, "collect_state", return_value=result), mock.patch.object(
            curseforge_state, "JsonClient", FakeClient
        ):
            with self.assertRaises(curseforge_state.CurseForgeStateError):
                curseforge_state.clear_legacy_labels(
                    Path("manifest"),
                    Path("context"),
                    Path("repo"),
                    self.repository,
                    self.project_id,
                    "canary",
                    "token",
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
