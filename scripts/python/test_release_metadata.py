#!/usr/bin/env python3

import importlib.util
import hashlib
import json
import tempfile
import unittest
import zipfile
from pathlib import Path


MODULE_PATH = Path(__file__).with_name("release_metadata.py")
SPEC = importlib.util.spec_from_file_location("release_metadata", MODULE_PATH)
release_metadata = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(release_metadata)


class ReleaseMetadataTest(unittest.TestCase):
    repo_root = Path(__file__).resolve().parents[2]

    @staticmethod
    def write_test_jar(
        path: Path,
        *,
        version: str,
        minecraft_dependency: str,
        carpet_dependency: str | None,
        fabric_api_dependency: str | None,
    ) -> None:
        depends = {"minecraft": minecraft_dependency}
        if carpet_dependency is not None:
            depends["carpet"] = carpet_dependency
        if fabric_api_dependency is not None:
            depends["fabric-api"] = fabric_api_dependency
        with zipfile.ZipFile(path, "w") as archive:
            archive.writestr(
                "fabric.mod.json",
                json.dumps(
                    {
                        "id": "carpet-fga-addition",
                        "version": version,
                        "depends": depends,
                    }
                ),
            )

    def test_repository_settings_have_no_independent_121_project(self):
        versions, publish_versions = release_metadata.load_release_settings(self.repo_root)
        self.assertEqual(17, len(versions))
        self.assertNotIn("1.21", versions)
        self.assertNotIn("1.21", publish_versions)
        self.assertIn("1.21.1", publish_versions)
        self.assertEqual(10, len(publish_versions))

    def test_repository_has_no_independent_121_build_references(self):
        build_gradle = (self.repo_root / "build.gradle").read_text(encoding="utf-8")
        build_workflow = (self.repo_root / ".github/workflows/build.yml").read_text(
            encoding="utf-8"
        )
        release_workflow = (self.repo_root / ".github/workflows/release.yml").read_text(
            encoding="utf-8"
        )
        removed_project = "1." + "21"
        self.assertFalse(
            (self.repo_root / "versions" / removed_project / "gradle.properties").exists()
        )
        self.assertNotIn(f"createNode('{removed_project}'", build_gradle)
        self.assertNotIn("mc" + "1210", build_gradle)
        self.assertNotIn(f":{removed_project}:build", build_workflow)
        self.assertIn("fromJSON(needs.prepare.outputs.matrix)", build_workflow)
        self.assertNotIn("gh release create", release_workflow)
        self.assertNotIn("git tag", release_workflow)

    def test_dispatch_uses_default_branch_helper_and_separate_release_source(self):
        release_workflow = (self.repo_root / ".github/workflows/release.yml").read_text(
            encoding="utf-8"
        )
        self.assertIn("path: publisher", release_workflow)
        self.assertIn("path: release-source", release_workflow)
        self.assertIn("github.event_name == 'workflow_dispatch' && github.sha", release_workflow)
        self.assertIn("git -C release-source rev-parse HEAD", release_workflow)
        self.assertIn("--repo-root release-source", release_workflow)
        self.assertIn("publisher/scripts/python/release_metadata.py", release_workflow)
        self.assertNotIn("python scripts/python/", release_workflow)
        dispatch_job = release_workflow.split("  package-dispatch:", 1)[1].split(
            "  publish-github:", 1
        )[0]
        self.assertIn("gh release download", dispatch_job)
        self.assertNotIn("gradlew", dispatch_job)
        self.assertNotIn("uses: ./.github/workflows/build.yml", dispatch_job)

    def test_external_project_identity_guards_are_present(self):
        release_workflow = (self.repo_root / ".github/workflows/release.yml").read_text(
            encoding="utf-8"
        )
        archive_workflow = (
            self.repo_root / ".github/workflows/archive-modrinth-version.yml"
        ).read_text(encoding="utf-8")
        self.assertIn("validate-project", release_workflow)
        self.assertIn("steps.curseforge-project.outputs.project_id", release_workflow)
        self.assertIn("$project.id -ne 'Nfhbipsz'", archive_workflow)

    def test_all_publish_compatibility_ranges(self):
        _, publish_versions = release_metadata.load_release_settings(self.repo_root)
        actual = {
            version: release_metadata.project_metadata(self.repo_root, version, "1.5.4")[
                "game_versions"
            ]
            for version in publish_versions
        }
        self.assertEqual(
            {
                "1.20.1": ["1.20", "1.20.1"],
                "1.21.1": ["1.21", "1.21.1"],
                "1.21.3": ["1.21.2", "1.21.3"],
                "1.21.4": ["1.21.4"],
                "1.21.5": ["1.21.5"],
                "1.21.8": ["1.21.6", "1.21.7", "1.21.8"],
                "1.21.10": ["1.21.9", "1.21.10"],
                "1.21.11": ["1.21.11"],
                "26.1.2": ["26.1", "26.1.1", "26.1.2"],
                "26.2": ["26.2"],
            },
            actual,
        )

    def test_matrix_is_dynamic_and_uses_java_rule(self):
        _, publish_versions = release_metadata.load_release_settings(self.repo_root)
        matrix = release_metadata.matrix_for_versions(publish_versions)
        self.assertEqual(publish_versions, [item["mc_version"] for item in matrix["include"]])
        self.assertEqual("21", matrix["include"][0]["java"])
        self.assertEqual("25", matrix["include"][-1]["java"])

    def test_target_selection_rejects_removed_and_duplicate_projects(self):
        _, publish_versions = release_metadata.load_release_settings(self.repo_root)
        with self.assertRaises(release_metadata.MetadataError):
            release_metadata.select_publish_versions(publish_versions, "1.21")
        with self.assertRaises(release_metadata.MetadataError):
            release_metadata.select_publish_versions(publish_versions, "1.21.1,1.21.1")
        self.assertEqual(
            ["1.21.1"], release_metadata.select_publish_versions(publish_versions, "1.21.1")
        )

    def test_1211_metadata_preserves_121_runtime_support(self):
        entry = release_metadata.project_metadata(self.repo_root, "1.21.1", "1.5.4")
        self.assertEqual("1.21-1.21.1", entry["artifact_mc_version"])
        self.assertEqual(["1.21", "1.21.1"], entry["game_versions"])
        self.assertEqual(
            "carpet-fga-addition-1.5.4-mc1.21-1.21.1.jar", entry["jar_name"]
        )
        self.assertEqual("1.5.4", entry["modrinth_version_number"])
        self.assertEqual("1.5.4", entry["curseforge_version"])

    def test_compatibility_range_expansion(self):
        self.assertEqual(
            ["1.21.6", "1.21.7", "1.21.8"],
            release_metadata.expand_artifact_range("1.21.6-1.21.8"),
        )
        self.assertEqual(
            ["26.1", "26.1.1", "26.1.2"],
            release_metadata.expand_artifact_range("26.1-26.1.2"),
        )

    def test_duplicate_publish_settings_fail(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / "versions" / "1.21.1").mkdir(parents=True)
            (root / "versions" / "1.21.1" / "gradle.properties").write_text(
                "minecraft_version=1.21.1\n", encoding="utf-8"
            )
            (root / "settings.json").write_text(
                json.dumps(
                    {"versions": ["1.21.1"], "publishVersions": ["1.21.1", "1.21.1"]}
                ),
                encoding="utf-8",
            )
            with self.assertRaises(release_metadata.MetadataError):
                release_metadata.load_release_settings(root)

    def test_build_package_uses_compatibility_filename_and_tag_only_versions(self):
        entry = release_metadata.project_metadata(self.repo_root, "1.21.1", "1.5.4")
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            context = {
                "tag": "1.5.4",
                "title": "Release title",
                "body": "Changes",
                "commit": "abc123",
                "prerelease": False,
                "all_versions": ["1.21.1"],
                "selected_versions": ["1.21.1"],
                "is_all_versions": True,
                "all_entries": [entry],
                "selected_entries": [entry],
                "modrinth_project_id": "Nfhbipsz",
                "curseforge_project_id": "1660840",
            }
            context_path = root / "context.json"
            context_path.write_text(json.dumps(context), encoding="utf-8")
            artifact_dir = root / "artifacts" / "build-1.21.1"
            artifact_dir.mkdir(parents=True)
            jar = artifact_dir / "built.jar"
            self.write_test_jar(
                jar,
                version="1.5.4",
                minecraft_dependency=">=1.21 <=1.21.1",
                carpet_dependency=">=1.4.147",
                fabric_api_dependency="*",
            )
            digest = hashlib.sha256(jar.read_bytes()).hexdigest()
            (artifact_dir / "build-manifest-1.21.1.json").write_text(
                json.dumps(
                    {"build_project": "1.21.1", "commit": "abc123", "sha256": digest}
                ),
                encoding="utf-8",
            )
            package = release_metadata.assemble_build_artifacts(
                context_path,
                root / "artifacts",
                root / "package",
                ["1.21.1"],
                "abc123",
            )
            self.assertEqual("1.5.4", package["modrinth_version_number"])
            self.assertEqual("1.5.4", package["curseforge_version"])
            self.assertTrue((root / "package" / "dist" / entry["jar_name"]).is_file())

    def test_jar_validation_requires_exact_carpet_dependency(self):
        entry = release_metadata.project_metadata(self.repo_root, "1.21.1", "1.5.4")
        with tempfile.TemporaryDirectory() as temporary:
            jar = Path(temporary) / "missing-carpet.jar"
            self.write_test_jar(
                jar,
                version="1.5.4",
                minecraft_dependency=entry["minecraft_dependency"],
                carpet_dependency=None,
                fabric_api_dependency="*",
            )
            with self.assertRaises(release_metadata.MetadataError):
                release_metadata._validate_jar(jar, entry)

            jar = Path(temporary) / "wrong-carpet.jar"
            self.write_test_jar(
                jar,
                version="1.5.4",
                minecraft_dependency=entry["minecraft_dependency"],
                carpet_dependency=">=0",
                fabric_api_dependency="*",
            )
            with self.assertRaises(release_metadata.MetadataError):
                release_metadata._validate_jar(jar, entry)

    def test_jar_validation_requires_fabric_api_for_minecraft_121_and_newer(self):
        entry = release_metadata.project_metadata(self.repo_root, "1.21.1", "1.5.4")
        with tempfile.TemporaryDirectory() as temporary:
            jar = Path(temporary) / "missing-fabric-api.jar"
            self.write_test_jar(
                jar,
                version="1.5.4",
                minecraft_dependency=entry["minecraft_dependency"],
                carpet_dependency=entry["carpet_dependency"],
                fabric_api_dependency=None,
            )
            with self.assertRaises(release_metadata.MetadataError):
                release_metadata._validate_jar(jar, entry)

    def test_jar_validation_rejects_fabric_api_below_minecraft_121(self):
        entry = release_metadata.project_metadata(self.repo_root, "1.20.1", "1.5.4")
        with tempfile.TemporaryDirectory() as temporary:
            jar = Path(temporary) / "unexpected-fabric-api.jar"
            self.write_test_jar(
                jar,
                version="1.5.4",
                minecraft_dependency=entry["minecraft_dependency"],
                carpet_dependency=entry["carpet_dependency"],
                fabric_api_dependency="*",
            )
            with self.assertRaises(release_metadata.MetadataError):
                release_metadata._validate_jar(jar, entry)

    def test_jar_validation_accepts_low_version_without_fabric_api(self):
        entry = release_metadata.project_metadata(self.repo_root, "1.20.1", "1.5.4")
        with tempfile.TemporaryDirectory() as temporary:
            jar = Path(temporary) / "valid.jar"
            self.write_test_jar(
                jar,
                version="1.5.4",
                minecraft_dependency=entry["minecraft_dependency"],
                carpet_dependency=entry["carpet_dependency"],
                fabric_api_dependency=None,
            )
            release_metadata._validate_jar(jar, entry)


if __name__ == "__main__":
    unittest.main()
