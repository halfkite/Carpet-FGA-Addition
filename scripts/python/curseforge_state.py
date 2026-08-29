#!/usr/bin/env python3
"""CurseForge idempotency marker, history guard, and metadata repair helpers."""

from __future__ import annotations

import argparse
import json
import os
import re
import secrets
import sys
import urllib.error
import urllib.parse
import urllib.request
import uuid
from pathlib import Path
from typing import Any


class CurseForgeStateError(RuntimeError):
    pass


CURSEFORGE_API_ROOT = "https://minecraft.curseforge.com/api"


class JsonClient:
    def __init__(self, token: str):
        if not token:
            raise CurseForgeStateError("GitHub token is required")
        self.headers = {
            "Accept": "application/vnd.github+json",
            "Authorization": f"Bearer {token}",
            "X-GitHub-Api-Version": "2022-11-28",
            "User-Agent": "Carpet-FGA-Addition CurseForge state guard",
        }

    def request(self, method: str, url: str, data: bytes | None = None) -> tuple[Any, dict[str, str]]:
        request = urllib.request.Request(url, data=data, headers=self.headers, method=method)
        try:
            with urllib.request.urlopen(request) as response:
                payload = response.read()
                headers = dict(response.headers.items())
        except urllib.error.HTTPError as exc:
            body = exc.read().decode("utf-8", errors="replace")
            raise CurseForgeStateError(
                f"GitHub API {method} {url} failed: HTTP {exc.code}: {body}"
            ) from exc
        return (json.loads(payload) if payload else None), headers

    def paged(self, url: str) -> list[Any]:
        result: list[Any] = []
        page = 1
        separator = "&" if "?" in url else "?"
        while True:
            payload, _ = self.request("GET", f"{url}{separator}per_page=100&page={page}")
            if not isinstance(payload, list):
                raise CurseForgeStateError(f"Expected an array from {url}")
            result.extend(payload)
            if len(payload) < 100:
                return result
            page += 1


def write_github_output(path: Path, name: str, value: str) -> None:
    with path.open("a", encoding="utf-8") as stream:
        if "\n" not in value and "\r" not in value:
            stream.write(f"{name}={value}\n")
            return
        delimiter = f"FGA_{uuid.uuid4().hex}"
        stream.write(f"{name}<<{delimiter}\n{value}\n{delimiter}\n")


def manifest_entry(manifest_path: Path, build_project: str) -> tuple[dict[str, Any], dict[str, Any]]:
    manifest = json.loads(manifest_path.read_text(encoding="utf-8-sig"))
    matches = [entry for entry in manifest["files"] if entry["build_project"] == build_project]
    if len(matches) != 1:
        raise CurseForgeStateError(
            f"Expected exactly one release manifest entry for {build_project}, found {len(matches)}"
        )
    if manifest.get("curseforge_version") != manifest.get("tag"):
        raise CurseForgeStateError("CurseForge version must equal the GitHub Release tag exactly")
    return manifest, matches[0]


def release_assets(client: JsonClient, repository: str, tag: str) -> tuple[dict[str, Any], list[dict[str, Any]]]:
    encoded_tag = urllib.parse.quote(tag, safe="")
    release, _ = client.request(
        "GET", f"https://api.github.com/repos/{repository}/releases/tags/{encoded_tag}"
    )
    if not isinstance(release, dict):
        raise CurseForgeStateError(f"GitHub Release {tag} was not found")
    assets = client.paged(
        f"https://api.github.com/repos/{repository}/releases/{release['id']}/assets"
    )
    return release, assets


def marker_file_id(asset: dict[str, Any]) -> str | None:
    label = str(asset.get("label") or "")
    match = re.fullmatch(r"CF:(\d+)", label)
    return match.group(1) if match else None


def previous_upload_steps(
    client: JsonClient,
    repository: str,
    tag: str,
    build_project: str,
    current_run_id: int,
    current_attempt: int,
) -> tuple[int, list[dict[str, Any]]]:
    workflow_url = (
        f"https://api.github.com/repos/{repository}/actions/workflows/release.yml/runs"
    )
    runs_payload: list[dict[str, Any]] = []
    page = 1
    while True:
        payload, _ = client.request("GET", f"{workflow_url}?per_page=100&page={page}")
        workflow_runs = payload.get("workflow_runs") if isinstance(payload, dict) else None
        if not isinstance(workflow_runs, list):
            raise CurseForgeStateError("GitHub workflow runs response is invalid")
        runs_payload.extend(workflow_runs)
        if len(workflow_runs) < 100:
            break
        page += 1

    matching_runs = [run for run in runs_payload if run.get("display_title") == f"Publish {tag}"]
    matches: list[dict[str, Any]] = []
    inspected_attempts = 0
    expected_job_name = f"Publish CurseForge Minecraft {build_project}"
    for run in matching_runs:
        run_id = int(run["id"])
        maximum_attempt = int(run.get("run_attempt") or 1)
        if run_id == current_run_id:
            maximum_attempt = min(maximum_attempt, current_attempt - 1)
        for attempt in range(1, maximum_attempt + 1):
            inspected_attempts += 1
            jobs_url = (
                f"https://api.github.com/repos/{repository}/actions/runs/{run_id}"
                f"/attempts/{attempt}/jobs"
            )
            jobs_payload, _ = client.request("GET", f"{jobs_url}?per_page=100")
            jobs = jobs_payload.get("jobs") if isinstance(jobs_payload, dict) else None
            if not isinstance(jobs, list):
                raise CurseForgeStateError(f"GitHub jobs response is invalid for run {run_id}")
            for job in jobs:
                if job.get("name") != expected_job_name:
                    continue
                for step in job.get("steps") or []:
                    if step.get("name") == "Upload to CurseForge" and step.get("conclusion") not in (
                        None,
                        "skipped",
                    ):
                        matches.append(
                            {
                                "run_id": run_id,
                                "attempt": attempt,
                                "conclusion": step.get("conclusion"),
                                "html_url": job.get("html_url"),
                            }
                        )
    return inspected_attempts, matches


def assert_upload_state_is_known(
    event_name: str, inspected_attempts: int, history: list[dict[str, Any]]
) -> None:
    if history:
        raise CurseForgeStateError(
            "The GitHub asset has no CurseForge marker, but a previous upload step may have sent "
            f"the file. Refusing a duplicate upload: {history}"
        )
    if event_name == "workflow_dispatch" and inspected_attempts == 0:
        raise CurseForgeStateError(
            "No prior release workflow attempt for this tag can be audited. The missing marker is an "
            "unknown state, so a manual duplicate upload is refused."
        )


def precheck(
    manifest_path: Path,
    build_project: str,
    repository: str,
    current_run_id: int,
    current_attempt: int,
    event_name: str,
    token: str,
    output_path: Path,
) -> None:
    manifest, entry = manifest_entry(manifest_path, build_project)
    client = JsonClient(token)
    _, assets = release_assets(client, repository, manifest["tag"])
    matches = [asset for asset in assets if asset.get("name") == entry["jar_name"]]
    if len(matches) != 1:
        raise CurseForgeStateError(
            f"Expected one GitHub Release asset named {entry['jar_name']}, found {len(matches)}"
        )
    asset = matches[0]
    file_id = marker_file_id(asset)
    if file_id:
        write_github_output(output_path, "should_upload", "false")
        write_github_output(output_path, "file_id", file_id)
        write_github_output(output_path, "asset_id", str(asset["id"]))
        print(f"CurseForge marker already exists for {entry['jar_name']}: {file_id}")
        return

    inspected_attempts, history = previous_upload_steps(
        client,
        repository,
        manifest["tag"],
        build_project,
        current_run_id,
        current_attempt,
    )
    assert_upload_state_is_known(event_name, inspected_attempts, history)
    write_github_output(output_path, "should_upload", "true")
    write_github_output(output_path, "file_id", "")
    write_github_output(output_path, "asset_id", str(asset["id"]))
    print(f"No prior CurseForge upload was found for {entry['jar_name']}")


def mark_asset(repository: str, asset_id: str, file_id: str, token: str) -> None:
    if not re.fullmatch(r"\d+", file_id):
        raise CurseForgeStateError(f"CurseForge did not return a numeric file ID: {file_id!r}")
    client = JsonClient(token)
    data = json.dumps({"label": f"CF:{file_id}"}).encode("utf-8")
    client.headers["Content-Type"] = "application/json"
    updated, _ = client.request(
        "PATCH",
        f"https://api.github.com/repos/{repository}/releases/assets/{asset_id}",
        data=data,
    )
    if marker_file_id(updated) != file_id:
        raise CurseForgeStateError("GitHub asset label did not retain the CurseForge file ID")


def multipart_metadata(metadata: dict[str, Any]) -> tuple[bytes, str]:
    boundary = f"fga-{secrets.token_hex(16)}"
    payload = json.dumps(metadata, ensure_ascii=False).encode("utf-8")
    body = b"".join(
        (
            f"--{boundary}\r\n".encode(),
            b'Content-Disposition: form-data; name="metadata"\r\n',
            b"Content-Type: application/json\r\n\r\n",
            payload,
            b"\r\n",
            f"--{boundary}--\r\n".encode(),
        )
    )
    return body, f"multipart/form-data; boundary={boundary}"


def curseforge_json(path: str, token: str) -> Any:
    request = urllib.request.Request(
        f"{CURSEFORGE_API_ROOT}{path}",
        headers={
            "X-Api-Token": token,
            "Accept": "application/json",
            "User-Agent": "Carpet-FGA-Addition release publisher",
        },
    )
    try:
        with urllib.request.urlopen(request) as response:
            return json.loads(response.read())
    except urllib.error.HTTPError as exc:
        error_body = exc.read().decode("utf-8", errors="replace")
        raise CurseForgeStateError(
            f"CurseForge metadata lookup failed: HTTP {exc.code}: {error_body}"
        ) from exc
    except json.JSONDecodeError as exc:
        raise CurseForgeStateError(f"CurseForge returned invalid metadata JSON: {exc}") from exc


def resolve_curseforge_game_version_ids(
    game_version_names: list[str],
    version_types: list[dict[str, Any]],
    game_versions: list[dict[str, Any]],
) -> list[int]:
    minecraft_type_ids = {
        item.get("id")
        for item in version_types
        if str(item.get("slug") or "").lower().startswith("minecraft")
    }
    loader_type_ids = {
        item.get("id")
        for item in version_types
        if str(item.get("slug") or "").lower().startswith("modloader")
    }
    if not minecraft_type_ids or not loader_type_ids:
        raise CurseForgeStateError("CurseForge did not return Minecraft and modloader version types")

    minecraft_versions = [
        item for item in game_versions if item.get("gameVersionTypeID") in minecraft_type_ids
    ]
    loader_versions = [
        item for item in game_versions if item.get("gameVersionTypeID") in loader_type_ids
    ]
    resolved: list[int] = []
    for name in game_version_names:
        matches = [
            item
            for item in minecraft_versions
            if re.sub(r"(?:^Beta )|(?:-Snapshot$)", "", str(item.get("name") or "")) == name
        ]
        if len(matches) != 1:
            raise CurseForgeStateError(
                f"Expected one CurseForge game version ID for {name}, found {len(matches)}"
            )
        resolved.append(int(matches[0]["id"]))

    fabric_matches = [
        item
        for item in loader_versions
        if "fabric" in re.findall(r"[a-z0-9]+", str(item.get("name") or "").lower())
    ]
    if len(fabric_matches) != 1:
        raise CurseForgeStateError(
            f"Expected one CurseForge Fabric loader ID, found {len(fabric_matches)}"
        )
    resolved.append(int(fabric_matches[0]["id"]))
    return list(dict.fromkeys(resolved))


def curseforge_game_version_ids(game_version_names: list[str], token: str) -> list[int]:
    version_types = curseforge_json("/game/version-types?cache=true", token)
    game_versions = curseforge_json("/game/versions?cache=true", token)
    if not isinstance(version_types, list) or not isinstance(game_versions, list):
        raise CurseForgeStateError("CurseForge game version metadata has an unexpected shape")
    return resolve_curseforge_game_version_ids(game_version_names, version_types, game_versions)


def update_curseforge_metadata(
    manifest_path: Path, build_project: str, project_id: str, file_id: str, token: str
) -> None:
    if not token:
        raise CurseForgeStateError("CURSEFORGE_TOKEN is required")
    if not re.fullmatch(r"\d+", file_id):
        raise CurseForgeStateError(f"Invalid CurseForge file ID: {file_id!r}")
    manifest, entry = manifest_entry(manifest_path, build_project)
    relations = [{"slug": "carpet", "type": "requiredDependency"}]
    if entry["fabric_api_required"]:
        relations.append({"slug": "fabric-api", "type": "requiredDependency"})
    game_version_ids = curseforge_game_version_ids(entry["game_versions"], token)
    metadata = {
        "fileID": int(file_id),
        "changelog": manifest["body"],
        "changelogType": "markdown",
        "displayName": manifest["tag"],
        "releaseType": "release",
        "gameVersions": game_version_ids,
        "relations": {"projects": relations},
    }
    body, content_type = multipart_metadata(metadata)
    request = urllib.request.Request(
        f"{CURSEFORGE_API_ROOT}/projects/{project_id}/update-file",
        data=body,
        method="POST",
        headers={
            "X-Api-Token": token,
            "Content-Type": content_type,
            "User-Agent": "Carpet-FGA-Addition release publisher",
        },
    )
    try:
        with urllib.request.urlopen(request) as response:
            response.read()
    except urllib.error.HTTPError as exc:
        error_body = exc.read().decode("utf-8", errors="replace")
        raise CurseForgeStateError(
            f"CurseForge metadata update failed: HTTP {exc.code}: {error_body}"
        ) from exc
    print(f"Updated CurseForge metadata for file {file_id}")


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser()
    subparsers = parser.add_subparsers(dest="command", required=True)

    check = subparsers.add_parser("precheck")
    check.add_argument("--manifest", type=Path, required=True)
    check.add_argument("--build-project", required=True)
    check.add_argument("--repository", required=True)
    check.add_argument("--run-id", type=int, required=True)
    check.add_argument("--run-attempt", type=int, required=True)
    check.add_argument("--event-name", choices=("release", "workflow_dispatch"), required=True)
    check.add_argument("--github-output", type=Path, required=True)

    mark = subparsers.add_parser("mark")
    mark.add_argument("--repository", required=True)
    mark.add_argument("--asset-id", required=True)
    mark.add_argument("--file-id", required=True)

    update = subparsers.add_parser("update")
    update.add_argument("--manifest", type=Path, required=True)
    update.add_argument("--build-project", required=True)
    update.add_argument("--project", default="1660840")
    update.add_argument("--file-id", required=True)
    return parser


def main() -> int:
    args = build_parser().parse_args()
    try:
        if args.command == "precheck":
            precheck(
                args.manifest,
                args.build_project,
                args.repository,
                args.run_id,
                args.run_attempt,
                args.event_name,
                os.environ.get("GITHUB_TOKEN", ""),
                args.github_output,
            )
        elif args.command == "mark":
            mark_asset(
                args.repository,
                args.asset_id,
                args.file_id,
                os.environ.get("GITHUB_TOKEN", ""),
            )
        elif args.command == "update":
            update_curseforge_metadata(
                args.manifest,
                args.build_project,
                args.project,
                args.file_id,
                os.environ.get("CURSEFORGE_TOKEN", ""),
            )
    except (OSError, json.JSONDecodeError, CurseForgeStateError) as exc:
        print(f"CurseForge state error: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
