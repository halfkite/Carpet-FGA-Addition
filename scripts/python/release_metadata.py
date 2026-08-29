#!/usr/bin/env python3
"""Release metadata and artifact validation for Carpet FGA Addition."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import shutil
import sys
import uuid
import zipfile
from pathlib import Path
from typing import Any, Iterable


MOD_ID = "carpet-fga-addition"
MODRINTH_PROJECT_ID = "Nfhbipsz"
MODRINTH_CARPET_ID = "TQTTVgYE"
MODRINTH_FABRIC_API_ID = "P7dR8mSH"
CURSEFORGE_PROJECT_ID = "1660840"
CURSEFORGE_CARPET_ID = "349239"
CURSEFORGE_FABRIC_API_ID = "306612"


class MetadataError(RuntimeError):
    pass


def compact_json(value: Any) -> str:
    return json.dumps(value, ensure_ascii=False, separators=(",", ":"))


def read_json(path: Path) -> Any:
    try:
        return json.loads(path.read_text(encoding="utf-8-sig"))
    except (OSError, json.JSONDecodeError) as exc:
        raise MetadataError(f"Cannot read JSON from {path}: {exc}") from exc


def write_json(path: Path, value: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def read_properties(path: Path) -> dict[str, str]:
    result: dict[str, str] = {}
    try:
        lines = path.read_text(encoding="utf-8-sig").splitlines()
    except OSError as exc:
        raise MetadataError(f"Cannot read properties from {path}: {exc}") from exc
    for raw_line in lines:
        line = raw_line.strip()
        if not line or line.startswith("#") or line.startswith("!"):
            continue
        if "=" not in line:
            raise MetadataError(f"Invalid property line in {path}: {raw_line!r}")
        key, value = line.split("=", 1)
        result[key.strip()] = value.strip()
    return result


def version_tuple(value: str) -> tuple[int, ...]:
    if not re.fullmatch(r"\d+(?:\.\d+)*", value):
        raise MetadataError(f"Unsupported Minecraft version syntax: {value}")
    return tuple(int(part) for part in value.split("."))


def compare_versions(left: str, right: str) -> int:
    left_parts = version_tuple(left)
    right_parts = version_tuple(right)
    width = max(len(left_parts), len(right_parts))
    left_normalized = left_parts + (0,) * (width - len(left_parts))
    right_normalized = right_parts + (0,) * (width - len(right_parts))
    return (left_normalized > right_normalized) - (left_normalized < right_normalized)


def expand_artifact_range(label: str) -> list[str]:
    if "-" not in label:
        version_tuple(label)
        return [label]
    lower, upper = label.split("-", 1)
    lower_parts = version_tuple(lower)
    upper_parts = version_tuple(upper)
    if len(lower_parts) not in (2, 3) or len(upper_parts) not in (2, 3):
        raise MetadataError(f"Cannot enumerate compatibility range {label}")
    if lower_parts[:2] != upper_parts[:2]:
        raise MetadataError(f"Compatibility range crosses a minor boundary: {label}")
    lower_patch = lower_parts[2] if len(lower_parts) == 3 else 0
    upper_patch = upper_parts[2] if len(upper_parts) == 3 else 0
    if lower_patch > upper_patch:
        raise MetadataError(f"Compatibility range is reversed: {label}")
    major, minor = lower_parts[:2]
    values: list[str] = []
    for patch in range(lower_patch, upper_patch + 1):
        if patch == 0 and len(lower_parts) == 2:
            values.append(f"{major}.{minor}")
        else:
            values.append(f"{major}.{minor}.{patch}")
    return values


def version_satisfies(version: str, predicate: str) -> bool:
    terms = predicate.split()
    if not terms:
        raise MetadataError("Minecraft dependency predicate is empty")
    for term in terms:
        match = re.fullmatch(r"(>=|<=|>|<)?(\d+(?:\.\d+)*)", term)
        if not match:
            raise MetadataError(f"Unsupported Minecraft dependency term: {term}")
        operator, boundary = match.groups()
        comparison = compare_versions(version, boundary)
        if operator is None and comparison != 0:
            return False
        if operator == ">=" and comparison < 0:
            return False
        if operator == "<=" and comparison > 0:
            return False
        if operator == ">" and comparison <= 0:
            return False
        if operator == "<" and comparison >= 0:
            return False
    return True


def _validated_list(settings: dict[str, Any], key: str) -> list[str]:
    value = settings.get(key)
    if not isinstance(value, list) or not value or not all(isinstance(item, str) and item for item in value):
        raise MetadataError(f"settings.json.{key} must be a non-empty string array")
    if len(value) != len(set(value)):
        raise MetadataError(f"settings.json.{key} contains duplicate entries")
    return list(value)


def load_release_settings(repo_root: Path) -> tuple[list[str], list[str]]:
    settings = read_json(repo_root / "settings.json")
    if not isinstance(settings, dict):
        raise MetadataError("settings.json must contain an object")
    versions = _validated_list(settings, "versions")
    publish_versions = _validated_list(settings, "publishVersions")
    unknown = [version for version in publish_versions if version not in versions]
    if unknown:
        raise MetadataError(f"publishVersions contains projects missing from versions: {unknown}")
    if "1.21" in versions or "1.21" in publish_versions:
        raise MetadataError("The independent Minecraft 1.21 project must not be configured")
    for version in versions:
        properties = repo_root / "versions" / version / "gradle.properties"
        if not properties.is_file():
            raise MetadataError(f"Missing Gradle properties for project {version}: {properties}")
    return versions, publish_versions


def select_publish_versions(publish_versions: list[str], target: str | None) -> list[str]:
    requested = (target or "").strip()
    if not requested or requested.lower() == "all":
        return list(publish_versions)
    raw_values = [value.strip() for value in requested.split(",") if value.strip()]
    if not raw_values:
        raise MetadataError("No publish project was selected")
    if len(raw_values) != len(set(raw_values)):
        raise MetadataError("The requested publish projects contain duplicates")
    invalid = [value for value in raw_values if value not in publish_versions]
    if invalid:
        raise MetadataError(f"Unsupported publish project(s): {invalid}")
    requested_set = set(raw_values)
    return [version for version in publish_versions if version in requested_set]


def matrix_for_versions(versions: Iterable[str]) -> dict[str, list[dict[str, str]]]:
    return {
        "include": [
            {"mc_version": version, "java": "25" if version.startswith("26.") else "21"}
            for version in versions
        ]
    }


def project_metadata(repo_root: Path, build_project: str, tag: str) -> dict[str, Any]:
    properties = read_properties(repo_root / "versions" / build_project / "gradle.properties")
    required = ["minecraft_version", "minecraft_dependency", "carpet_dependency"]
    missing = [key for key in required if not properties.get(key)]
    if missing:
        raise MetadataError(f"Project {build_project} is missing properties: {missing}")
    artifact_label = properties.get("artifact_mc_version", properties["minecraft_version"])
    game_versions = expand_artifact_range(artifact_label)
    invalid_games = [
        version for version in game_versions if not version_satisfies(version, properties["minecraft_dependency"])
    ]
    if invalid_games:
        raise MetadataError(
            f"Project {build_project} artifact range {artifact_label} is outside "
            f"minecraft_dependency={properties['minecraft_dependency']}: {invalid_games}"
        )
    if properties["minecraft_version"] not in game_versions:
        raise MetadataError(
            f"Project {build_project} build Minecraft version {properties['minecraft_version']} "
            f"is not covered by artifact_mc_version={artifact_label}"
        )
    fabric_api_required = compare_versions(properties["minecraft_version"], "1.21") >= 0
    modrinth_dependencies = [{"project_id": MODRINTH_CARPET_ID, "dependency_type": "required"}]
    curseforge_dependencies = [CURSEFORGE_CARPET_ID]
    if fabric_api_required:
        modrinth_dependencies.append(
            {"project_id": MODRINTH_FABRIC_API_ID, "dependency_type": "required"}
        )
        curseforge_dependencies.append(CURSEFORGE_FABRIC_API_ID)
    return {
        "build_project": build_project,
        "minecraft_version": properties["minecraft_version"],
        "minecraft_dependency": properties["minecraft_dependency"],
        "carpet_dependency": properties["carpet_dependency"],
        "artifact_mc_version": artifact_label,
        "game_versions": game_versions,
        "java": "25" if build_project.startswith("26.") else "21",
        "fabric_api_required": fabric_api_required,
        "jar_name": f"{MOD_ID}-{tag}-mc{artifact_label}.jar",
        "modrinth_dependencies": modrinth_dependencies,
        "curseforge_dependencies": curseforge_dependencies,
        "modrinth_version_number": tag,
        "curseforge_version": tag,
    }


def release_context(
    repo_root: Path,
    release: dict[str, Any],
    expected_tag: str,
    commit: str,
    target: str | None,
) -> dict[str, Any]:
    if release.get("tagName") != expected_tag:
        raise MetadataError(
            f"GitHub Release tag is {release.get('tagName')!r}, expected {expected_tag!r}"
        )
    if release.get("isDraft"):
        raise MetadataError("Draft GitHub Releases cannot be published")
    title = str(release.get("name") or expected_tag).strip()
    body = str(release.get("body") or "")
    if not body.strip():
        raise MetadataError("GitHub Release body must not be empty")
    root_properties = read_properties(repo_root / "gradle.properties")
    if root_properties.get("mod_version") != expected_tag:
        raise MetadataError(
            f"GitHub Release tag {expected_tag} does not match gradle.properties mod_version="
            f"{root_properties.get('mod_version')!r}"
        )
    _, publish_versions = load_release_settings(repo_root)
    selected_versions = select_publish_versions(publish_versions, target)
    all_entries = [project_metadata(repo_root, version, expected_tag) for version in publish_versions]
    selected_set = set(selected_versions)
    selected_entries = [entry for entry in all_entries if entry["build_project"] in selected_set]
    return {
        "tag": expected_tag,
        "title": title,
        "body": body,
        "commit": commit,
        "prerelease": bool(release.get("isPrerelease")),
        "draft": False,
        "all_versions": publish_versions,
        "selected_versions": selected_versions,
        "is_all_versions": selected_versions == publish_versions,
        "matrix": matrix_for_versions(selected_versions),
        "all_entries": all_entries,
        "selected_entries": selected_entries,
        "modrinth_project_id": MODRINTH_PROJECT_ID,
        "curseforge_project_id": CURSEFORGE_PROJECT_ID,
        "modrinth_version_number": expected_tag,
        "curseforge_version": expected_tag,
    }


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _sha512(path: Path) -> str:
    digest = hashlib.sha512()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _read_fabric_mod_json(jar: Path) -> dict[str, Any]:
    try:
        with zipfile.ZipFile(jar) as archive:
            return json.loads(archive.read("fabric.mod.json").decode("utf-8-sig"))
    except (OSError, KeyError, zipfile.BadZipFile, json.JSONDecodeError) as exc:
        raise MetadataError(f"Cannot read fabric.mod.json from {jar}: {exc}") from exc


def _validate_jar(jar: Path, entry: dict[str, Any]) -> dict[str, Any]:
    fabric = _read_fabric_mod_json(jar)
    if fabric.get("id") != MOD_ID:
        raise MetadataError(f"{jar} has unexpected mod id {fabric.get('id')!r}")
    dependencies = fabric.get("depends")
    if not isinstance(dependencies, dict):
        raise MetadataError(f"{jar} has an invalid fabric.mod.json depends object")
    actual_minecraft_dependency = dependencies.get("minecraft")
    if actual_minecraft_dependency != entry["minecraft_dependency"]:
        raise MetadataError(
            f"{jar} declares Minecraft {actual_minecraft_dependency!r}; "
            f"expected {entry['minecraft_dependency']!r}"
        )
    actual_carpet_dependency = dependencies.get("carpet")
    if actual_carpet_dependency != entry["carpet_dependency"]:
        raise MetadataError(
            f"{jar} declares Carpet {actual_carpet_dependency!r}; "
            f"expected {entry['carpet_dependency']!r}"
        )
    has_fabric_api_dependency = "fabric-api" in dependencies
    if entry["fabric_api_required"] and not dependencies.get("fabric-api"):
        raise MetadataError(f"{jar} must declare a fabric-api dependency for Minecraft 1.21+")
    if not entry["fabric_api_required"] and has_fabric_api_dependency:
        raise MetadataError(f"{jar} must not declare a fabric-api dependency below Minecraft 1.21")
    expected_version = entry["modrinth_version_number"]
    if fabric.get("version") != expected_version:
        raise MetadataError(
            f"{jar} embeds version {fabric.get('version')!r}; expected Release tag {expected_version!r}"
        )
    return fabric


def _prepare_package_dir(output_dir: Path) -> Path:
    dist = output_dir / "dist"
    if output_dir.exists():
        shutil.rmtree(output_dir)
    dist.mkdir(parents=True)
    return dist


def assemble_build_artifacts(
    context_path: Path,
    artifacts_root: Path,
    output_dir: Path,
    build_versions: list[str],
    build_commit: str,
) -> dict[str, Any]:
    context = read_json(context_path)
    if build_versions != context["selected_versions"]:
        raise MetadataError(
            f"Reusable build selected {build_versions}, expected {context['selected_versions']}"
        )
    if build_commit != context["commit"]:
        raise MetadataError(
            f"Reusable build resolved commit {build_commit}, expected {context['commit']}"
        )
    dist = _prepare_package_dir(output_dir)
    files: list[dict[str, Any]] = []
    for entry in context["selected_entries"]:
        build_project = entry["build_project"]
        source_dir = artifacts_root / f"build-{build_project}"
        jars = [
            path
            for path in source_dir.rglob("*.jar")
            if not re.search(r"(?:-dev|-sources|-javadoc)\.jar$", path.name)
        ]
        if len(jars) != 1:
            raise MetadataError(
                f"Expected exactly one distributable JAR in {source_dir}, found {len(jars)}"
            )
        manifest_path = source_dir / f"build-manifest-{build_project}.json"
        build_manifest = read_json(manifest_path)
        if build_manifest.get("build_project") != build_project:
            raise MetadataError(f"Build manifest project mismatch in {manifest_path}")
        if build_manifest.get("commit") != context["commit"]:
            raise MetadataError(f"Build manifest commit mismatch in {manifest_path}")
        source_jar = jars[0]
        source_hash = _sha256(source_jar)
        if build_manifest.get("sha256") != source_hash:
            raise MetadataError(f"Build manifest digest mismatch for {source_jar}")
        fabric = _validate_jar(source_jar, entry)
        destination = dist / entry["jar_name"]
        shutil.copy2(source_jar, destination)
        files.append(
            {
                **entry,
                "path": str(destination),
                "sha256": source_hash,
                "sha512": _sha512(destination),
                "size": destination.stat().st_size,
                "embedded_version": fabric.get("version"),
            }
        )
    return _write_package(context_path, context, output_dir, files)


def assemble_release_assets(
    context_path: Path, assets_root: Path, output_dir: Path
) -> dict[str, Any]:
    context = read_json(context_path)
    dist = _prepare_package_dir(output_dir)
    files: list[dict[str, Any]] = []
    for entry in context["selected_entries"]:
        matches = list(assets_root.rglob(entry["jar_name"]))
        if len(matches) != 1:
            raise MetadataError(
                f"Expected exactly one GitHub Release asset named {entry['jar_name']}, found {len(matches)}"
            )
        source_jar = matches[0]
        fabric = _validate_jar(source_jar, entry)
        destination = dist / entry["jar_name"]
        shutil.copy2(source_jar, destination)
        files.append(
            {
                **entry,
                "path": str(destination),
                "sha256": _sha256(destination),
                "sha512": _sha512(destination),
                "size": destination.stat().st_size,
                "embedded_version": fabric.get("version"),
            }
        )
    return _write_package(context_path, context, output_dir, files)


def _write_package(
    context_path: Path, context: dict[str, Any], output_dir: Path, files: list[dict[str, Any]]
) -> dict[str, Any]:
    package = {
        "tag": context["tag"],
        "title": context["title"],
        "body": context["body"],
        "commit": context["commit"],
        "prerelease": context["prerelease"],
        "all_versions": context["all_versions"],
        "all_entries": context["all_entries"],
        "selected_versions": context["selected_versions"],
        "is_all_versions": context["is_all_versions"],
        "modrinth_project_id": context["modrinth_project_id"],
        "curseforge_project_id": context["curseforge_project_id"],
        "modrinth_version_number": context["tag"],
        "curseforge_version": context["tag"],
        "files": files,
    }
    write_json(output_dir / "release-manifest.json", package)
    shutil.copy2(context_path, output_dir / "release-context.json")
    (output_dir / "CHANGELOG.md").write_text(context["body"], encoding="utf-8")
    return package


def write_github_output(path: Path, name: str, value: str) -> None:
    with path.open("a", encoding="utf-8") as stream:
        if "\n" not in value and "\r" not in value:
            stream.write(f"{name}={value}\n")
            return
        delimiter = f"FGA_{uuid.uuid4().hex}"
        stream.write(f"{name}<<{delimiter}\n{value}\n{delimiter}\n")


def emit_context_outputs(context: dict[str, Any], output_path: Path) -> None:
    values = {
        "tag": context["tag"],
        "commit": context["commit"],
        "prerelease": str(context["prerelease"]).lower(),
        "matrix": compact_json(context["matrix"]),
        "versions": compact_json(context["selected_versions"]),
        "all_versions": compact_json(context["all_versions"]),
        "is_all_versions": str(context["is_all_versions"]).lower(),
    }
    for name, value in values.items():
        write_github_output(output_path, name, value)


def emit_matrix_outputs(repo_root: Path, target: str | None, output_path: Path) -> None:
    _, publish_versions = load_release_settings(repo_root)
    selected = select_publish_versions(publish_versions, target)
    write_github_output(output_path, "matrix", compact_json(matrix_for_versions(selected)))
    write_github_output(output_path, "versions", compact_json(selected))
    write_github_output(output_path, "all_versions", compact_json(publish_versions))


def emit_platform_entry(manifest_path: Path, build_project: str, output_path: Path) -> None:
    manifest = read_json(manifest_path)
    matches = [entry for entry in manifest["files"] if entry["build_project"] == build_project]
    if len(matches) != 1:
        raise MetadataError(f"Expected one manifest entry for {build_project}, found {len(matches)}")
    entry = matches[0]
    dependencies = [
        f"carpet(required){{modrinth:{MODRINTH_CARPET_ID}}}{{curseforge:{CURSEFORGE_CARPET_ID}}}"
    ]
    if entry["fabric_api_required"]:
        dependencies.append(
            f"fabric-api(required){{modrinth:{MODRINTH_FABRIC_API_ID}}}"
            f"{{curseforge:{CURSEFORGE_FABRIC_API_ID}}}"
        )
    values = {
        "jar": entry["path"],
        "asset_name": entry["jar_name"],
        "sha256": entry["sha256"],
        "game_versions": "\n".join(entry["game_versions"]),
        "dependencies": "\n".join(dependencies),
    }
    for name, value in values.items():
        write_github_output(output_path, name, value)


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser()
    subparsers = parser.add_subparsers(dest="command", required=True)

    matrix = subparsers.add_parser("matrix")
    matrix.add_argument("--repo-root", type=Path, default=Path.cwd())
    matrix.add_argument("--target", default="all")
    matrix.add_argument("--github-output", type=Path)

    inspect_release = subparsers.add_parser("inspect-release")
    inspect_release.add_argument("--repo-root", type=Path, default=Path.cwd())
    inspect_release.add_argument("--release-json", type=Path, required=True)
    inspect_release.add_argument("--tag", required=True)
    inspect_release.add_argument("--commit", required=True)
    inspect_release.add_argument("--target", default="all")
    inspect_release.add_argument("--output", type=Path, required=True)
    inspect_release.add_argument("--github-output", type=Path)

    assemble_build = subparsers.add_parser("assemble-build")
    assemble_build.add_argument("--context", type=Path, required=True)
    assemble_build.add_argument("--artifacts-root", type=Path, required=True)
    assemble_build.add_argument("--build-versions", required=True)
    assemble_build.add_argument("--build-commit", required=True)
    assemble_build.add_argument("--output-dir", type=Path, required=True)

    assemble_assets = subparsers.add_parser("assemble-assets")
    assemble_assets.add_argument("--context", type=Path, required=True)
    assemble_assets.add_argument("--assets-root", type=Path, required=True)
    assemble_assets.add_argument("--output-dir", type=Path, required=True)

    platform_entry = subparsers.add_parser("platform-entry")
    platform_entry.add_argument("--manifest", type=Path, required=True)
    platform_entry.add_argument("--build-project", required=True)
    platform_entry.add_argument("--github-output", type=Path, required=True)
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    try:
        if args.command == "matrix":
            _, publish_versions = load_release_settings(args.repo_root)
            selected = select_publish_versions(publish_versions, args.target)
            result = {
                "matrix": matrix_for_versions(selected),
                "versions": selected,
                "all_versions": publish_versions,
            }
            if args.github_output:
                emit_matrix_outputs(args.repo_root, args.target, args.github_output)
            else:
                print(compact_json(result))
        elif args.command == "inspect-release":
            context = release_context(
                args.repo_root,
                read_json(args.release_json),
                args.tag,
                args.commit,
                args.target,
            )
            write_json(args.output, context)
            if args.github_output:
                emit_context_outputs(context, args.github_output)
        elif args.command == "assemble-build":
            try:
                build_versions = json.loads(args.build_versions)
            except json.JSONDecodeError as exc:
                raise MetadataError(f"Invalid reusable build versions JSON: {exc}") from exc
            if not isinstance(build_versions, list) or not all(
                isinstance(item, str) for item in build_versions
            ):
                raise MetadataError("Reusable build versions must be a JSON string array")
            assemble_build_artifacts(
                args.context,
                args.artifacts_root,
                args.output_dir,
                build_versions,
                args.build_commit,
            )
        elif args.command == "assemble-assets":
            assemble_release_assets(args.context, args.assets_root, args.output_dir)
        elif args.command == "platform-entry":
            emit_platform_entry(args.manifest, args.build_project, args.github_output)
        else:
            raise MetadataError(f"Unsupported command: {args.command}")
    except MetadataError as exc:
        print(f"release metadata error: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
