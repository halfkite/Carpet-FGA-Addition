#!/usr/bin/env python3
"""Create or reconcile the single Modrinth Version for one GitHub Release tag."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import secrets
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Any


API_ROOT = "https://api.modrinth.com/v2"
EXPECTED_PROJECT_ID = "Nfhbipsz"


class ModrinthError(RuntimeError):
    pass


def validate_project_identity(manifest: dict[str, Any], canonical_project_id: str) -> str:
    if canonical_project_id != EXPECTED_PROJECT_ID:
        raise ModrinthError(
            f"Configured Modrinth project resolves to {canonical_project_id!r}; "
            f"expected {EXPECTED_PROJECT_ID!r}"
        )
    if manifest.get("modrinth_project_id") != EXPECTED_PROJECT_ID:
        raise ModrinthError(
            f"Release manifest targets Modrinth project {manifest.get('modrinth_project_id')!r}; "
            f"expected {EXPECTED_PROJECT_ID!r}"
        )
    return canonical_project_id


class ModrinthClient:
    def __init__(self, token: str):
        if not token:
            raise ModrinthError("MODRINTH_API_TOKEN is required")
        self.headers = {
            "Authorization": token,
            "User-Agent": "halfkite/Carpet-FGA-Addition release publisher",
        }

    def request(
        self, method: str, path: str, data: bytes | None = None, content_type: str | None = None
    ) -> Any:
        headers = dict(self.headers)
        headers["Accept"] = "application/json"
        if content_type:
            headers["Content-Type"] = content_type
        request = urllib.request.Request(f"{API_ROOT}{path}", data=data, headers=headers, method=method)
        try:
            with urllib.request.urlopen(request) as response:
                payload = response.read()
        except urllib.error.HTTPError as exc:
            body = exc.read().decode("utf-8", errors="replace")
            raise ModrinthError(
                f"Modrinth API {method} {path} failed: HTTP {exc.code}: {body}"
            ) from exc
        return json.loads(payload) if payload else None

    def json_request(self, method: str, path: str, value: Any) -> Any:
        return self.request(
            method,
            path,
            data=json.dumps(value, ensure_ascii=False).encode("utf-8"),
            content_type="application/json",
        )


def unique_preserving_order(values):
    seen = set()
    result = []
    for value in values:
        key = json.dumps(value, sort_keys=True) if isinstance(value, dict) else value
        if key not in seen:
            seen.add(key)
            result.append(value)
    return result


def manifest_entry(manifest: dict[str, Any], build_project: str) -> dict[str, Any]:
    matches = [entry for entry in manifest["files"] if entry["build_project"] == build_project]
    if len(matches) != 1:
        raise ModrinthError(
            f"Expected exactly one packaged JAR for {build_project}, found {len(matches)}"
        )
    return matches[0]


def expected_metadata(
    manifest: dict[str, Any], canonical_project_id: str, build_project: str
) -> dict[str, Any]:
    tag = manifest["tag"]
    if manifest.get("modrinth_version_number") != tag:
        raise ModrinthError("Modrinth version_number must equal the GitHub Release tag exactly")
    entry = manifest_entry(manifest, build_project)
    return {
        "project_id": canonical_project_id,
        "name": f"{manifest['title']} for Minecraft {entry['artifact_mc_version']}",
        "version_number": tag,
        "changelog": manifest["body"],
        "dependencies": entry["modrinth_dependencies"],
        "game_versions": entry["game_versions"],
        "version_type": "release",
        "loaders": ["fabric"],
        "featured": False,
        "status": "listed",
    }


def local_files(manifest: dict[str, Any], build_project: str) -> dict[str, dict[str, Any]]:
    entry = manifest_entry(manifest, build_project)
    path = Path(entry["path"])
    if not path.is_file():
        raise ModrinthError(f"Release JAR does not exist: {path}")
    sha512 = hashlib.sha512(path.read_bytes()).hexdigest()
    if sha512 != entry["sha512"]:
        raise ModrinthError(f"Manifest SHA-512 mismatch for {path}")
    return {entry["jar_name"]: {"path": path, "sha512": sha512}}


def find_existing_version(
    tag_matches: list[dict[str, Any]], expected_filename: str, allowed_filenames: set[str]
) -> dict[str, Any] | None:
    expected_matches: list[dict[str, Any]] = []
    for version in tag_matches:
        filenames = [file.get("filename") for file in version.get("files") or []]
        if len(filenames) != 1 or filenames[0] not in allowed_filenames:
            raise ModrinthError(
                "A same-tag Modrinth Version has an unexpected file set; refusing to guess its identity"
            )
        if filenames[0] == expected_filename:
            expected_matches.append(version)
    if len(expected_matches) > 1:
        raise ModrinthError(
            f"Multiple same-tag Modrinth Versions contain the expected file {expected_filename}"
        )
    return expected_matches[0] if expected_matches else None


def validate_existing_files(
    remote_files: list[dict[str, Any]], expected_files: dict[str, dict[str, Any]]
) -> None:
    remote_by_name = {item["filename"]: item for item in remote_files}
    if len(remote_by_name) != len(remote_files):
        raise ModrinthError("The existing Modrinth Version contains duplicate file names")
    if set(remote_by_name) != set(expected_files):
        raise ModrinthError(
            "The existing Modrinth Version file set differs from the GitHub Release assets"
        )
    for name, expected in expected_files.items():
        remote_hash = (remote_by_name[name].get("hashes") or {}).get("sha512")
        if remote_hash != expected["sha512"]:
            raise ModrinthError(f"The existing Modrinth file {name} has a different SHA-512")


def mutable_patch(existing: dict[str, Any], expected: dict[str, Any]) -> dict[str, Any]:
    patch: dict[str, Any] = {}
    for key in (
        "name",
        "changelog",
        "dependencies",
        "game_versions",
        "version_type",
        "loaders",
        "featured",
        "status",
    ):
        existing_value = existing.get(key)
        expected_value = expected[key]
        if key == "dependencies":
            normalize = lambda dependencies: sorted(
                [
                    {
                        field: dependency[field]
                        for field in ("project_id", "version_id", "file_name", "dependency_type")
                        if dependency.get(field) is not None
                    }
                    for dependency in dependencies or []
                ],
                key=lambda value: json.dumps(value, sort_keys=True),
            )
            if normalize(existing_value) != normalize(expected_value):
                patch[key] = expected_value
        elif key in {"game_versions", "loaders"}:
            if sorted(existing_value or [], key=lambda value: json.dumps(value, sort_keys=True)) != sorted(
                expected_value, key=lambda value: json.dumps(value, sort_keys=True)
            ):
                patch[key] = expected_value
        elif existing_value != expected_value:
            patch[key] = expected_value
    return patch


def multipart_body(data: dict[str, Any], files: dict[str, dict[str, Any]]) -> tuple[bytes, str]:
    boundary = f"fga-{secrets.token_hex(16)}"
    chunks: list[bytes] = []

    def field(name: str, value: bytes, content_type: str, filename: str | None = None) -> None:
        chunks.append(f"--{boundary}\r\n".encode())
        disposition = f'Content-Disposition: form-data; name="{name}"'
        if filename is not None:
            disposition += f'; filename="{filename}"'
        chunks.append((disposition + "\r\n").encode())
        chunks.append(f"Content-Type: {content_type}\r\n\r\n".encode())
        chunks.append(value)
        chunks.append(b"\r\n")

    field("data", json.dumps(data, ensure_ascii=False).encode("utf-8"), "application/json")
    for part_name, file_info in files.items():
        field(
            part_name,
            file_info["path"].read_bytes(),
            "application/java-archive",
            filename=file_info["path"].name,
        )
    chunks.append(f"--{boundary}--\r\n".encode())
    return b"".join(chunks), f"multipart/form-data; boundary={boundary}"


def verify_version(
    version: dict[str, Any], expected: dict[str, Any], files: dict[str, dict[str, Any]]
) -> None:
    if version.get("project_id") != expected["project_id"]:
        raise ModrinthError("Published Version belongs to the wrong project")
    if version.get("version_number") != expected["version_number"]:
        raise ModrinthError("Published Modrinth version_number does not equal the GitHub tag")
    validate_existing_files(version.get("files") or [], files)
    patch = mutable_patch(version, expected)
    if patch:
        raise ModrinthError(f"Published Modrinth metadata did not converge: {sorted(patch)}")


def publish(
    manifest_path: Path, token: str, configured_project: str, build_project: str
) -> None:
    manifest = json.loads(manifest_path.read_text(encoding="utf-8-sig"))
    client = ModrinthClient(token)
    project = client.request("GET", f"/project/{urllib.parse.quote(configured_project, safe='')}")
    if not isinstance(project, dict):
        raise ModrinthError("Modrinth project lookup returned an unexpected response")
    canonical_project_id = validate_project_identity(manifest, str(project.get("id") or ""))
    expected = expected_metadata(manifest, canonical_project_id, build_project)
    files = local_files(manifest, build_project)
    expected_filename = next(iter(files))
    allowed_filenames = {entry["jar_name"] for entry in manifest["all_entries"]}
    versions = client.request("GET", f"/project/{canonical_project_id}/version")
    tag_matches = [item for item in versions if item.get("version_number") == manifest["tag"]]
    existing_summary = find_existing_version(tag_matches, expected_filename, allowed_filenames)
    if existing_summary:
        existing = client.request("GET", f"/version/{existing_summary['id']}")
        validate_existing_files(existing.get("files") or [], files)
        patch = mutable_patch(existing, expected)
        if patch:
            client.json_request("PATCH", f"/version/{existing['id']}", patch)
            print(f"Updated Modrinth Version metadata: {existing['id']}")
        else:
            print(f"Modrinth Version already matches: {existing['id']}")
        verified = client.request("GET", f"/version/{existing['id']}")
        verify_version(verified, expected, files)
        return

    file_parts = ["file"]
    file_mapping = {"file": files[expected_filename]}
    create_payload = {
        **{key: value for key, value in expected.items() if key != "status"},
        "file_parts": file_parts,
        "primary_file": file_parts[0],
    }
    body, content_type = multipart_body(create_payload, file_mapping)
    created = client.request("POST", "/version", data=body, content_type=content_type)
    print(f"Created Modrinth Version: {created['id']}")
    verified = client.request("GET", f"/version/{created['id']}")
    verify_version(verified, expected, files)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--project", default="Nfhbipsz")
    parser.add_argument("--build-project", required=True)
    args = parser.parse_args()
    try:
        publish(
            args.manifest,
            os.environ.get("MODRINTH_API_TOKEN", ""),
            args.project,
            args.build_project,
        )
    except (OSError, json.JSONDecodeError, ModrinthError) as exc:
        print(f"Modrinth publish error: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
