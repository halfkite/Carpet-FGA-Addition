#!/usr/bin/env python3
"""CurseForge Git-notes state, history guard, migration, and metadata helpers."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import secrets
import subprocess
import sys
import tempfile
import urllib.error
import urllib.parse
import urllib.request
import uuid
from pathlib import Path
from typing import Any, Iterable


class CurseForgeStateError(RuntimeError):
    pass


CURSEFORGE_API_ROOT = "https://minecraft.curseforge.com/api"
EXPECTED_PROJECT_ID = "1660840"
STATE_SCHEMA = 1
STATE_REF_PREFIX = "refs/notes/carpet-fga-addition/curseforge"
STATE_KEYS = {
    "schema",
    "repository",
    "tag",
    "release_commit",
    "curseforge_project_id",
    "files",
}
STATE_FILE_KEYS = {
    "build_project",
    "jar_name",
    "github_asset_id",
    "sha256",
    "curseforge_file_id",
}


def validate_project_identity(project_id: str) -> str:
    normalized = str(project_id).strip()
    if normalized != EXPECTED_PROJECT_ID:
        raise CurseForgeStateError(
            f"Configured CurseForge project is {normalized!r}; expected {EXPECTED_PROJECT_ID!r}"
        )
    return normalized


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


def read_json_object(path: Path, description: str) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8-sig"))
    except (OSError, json.JSONDecodeError) as exc:
        raise CurseForgeStateError(f"Cannot read {description} from {path}: {exc}") from exc
    if not isinstance(value, dict):
        raise CurseForgeStateError(f"{description} must contain a JSON object")
    return value


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    try:
        with path.open("rb") as stream:
            for chunk in iter(lambda: stream.read(1024 * 1024), b""):
                digest.update(chunk)
    except OSError as exc:
        raise CurseForgeStateError(f"Cannot read release JAR {path}: {exc}") from exc
    return digest.hexdigest()


def manifest_entry(manifest_path: Path, build_project: str) -> tuple[dict[str, Any], dict[str, Any]]:
    manifest = read_json_object(manifest_path, "release manifest")
    files = manifest.get("files")
    if not isinstance(files, list):
        raise CurseForgeStateError("Release manifest files must be an array")
    matches = [entry for entry in files if entry.get("build_project") == build_project]
    if len(matches) != 1:
        raise CurseForgeStateError(
            f"Expected exactly one release manifest entry for {build_project}, found {len(matches)}"
        )
    if manifest.get("curseforge_version") != manifest.get("tag"):
        raise CurseForgeStateError("CurseForge version must equal the GitHub Release tag exactly")
    return manifest, matches[0]


def validate_release_inputs(
    manifest_path: Path, context_path: Path, project_id: str
) -> tuple[dict[str, Any], dict[str, Any], dict[str, dict[str, Any]]]:
    project_id = validate_project_identity(project_id)
    manifest = read_json_object(manifest_path, "release manifest")
    context = read_json_object(context_path, "release context")
    tag = context.get("tag")
    commit = context.get("commit")
    if not isinstance(tag, str) or not tag:
        raise CurseForgeStateError("Release context tag must be a non-empty string")
    if not isinstance(commit, str) or not re.fullmatch(r"[0-9a-f]{40}", commit):
        raise CurseForgeStateError("Release context commit must be a full lowercase Git commit ID")
    if manifest.get("tag") != tag or manifest.get("commit") != commit:
        raise CurseForgeStateError("Release manifest tag/commit does not match release context")
    if manifest.get("curseforge_project_id") != project_id:
        raise CurseForgeStateError(
            "Release manifest CurseForge project does not match the configured canonical project"
        )
    if manifest.get("curseforge_version") != tag:
        raise CurseForgeStateError("CurseForge version must equal the GitHub Release tag exactly")

    selected_versions = manifest.get("selected_versions")
    all_versions = manifest.get("all_versions")
    files = manifest.get("files")
    all_entries = manifest.get("all_entries")
    for name, value in (
        ("selected_versions", selected_versions),
        ("all_versions", all_versions),
        ("files", files),
        ("all_entries", all_entries),
    ):
        if not isinstance(value, list):
            raise CurseForgeStateError(f"Release manifest {name} must be an array")
    if selected_versions != context.get("selected_versions"):
        raise CurseForgeStateError("Release manifest selected versions do not match release context")
    if all_versions != context.get("all_versions"):
        raise CurseForgeStateError("Release manifest all versions do not match release context")
    if (
        not all(isinstance(item, str) and item for item in selected_versions)
        or len(selected_versions) != len(set(selected_versions))
    ):
        raise CurseForgeStateError("Selected build projects must be unique non-empty strings")
    if (
        not all(isinstance(item, str) and item for item in all_versions)
        or len(all_versions) != len(set(all_versions))
    ):
        raise CurseForgeStateError("All build projects must be unique non-empty strings")
    if any(item not in all_versions for item in selected_versions):
        raise CurseForgeStateError("Selected build projects are not a subset of Tag publishVersions")

    all_by_project: dict[str, dict[str, Any]] = {}
    jar_names: set[str] = set()
    for entry in all_entries:
        if not isinstance(entry, dict):
            raise CurseForgeStateError("Release manifest all_entries contains a non-object")
        build_project = entry.get("build_project")
        jar_name = entry.get("jar_name")
        if build_project not in all_versions or not isinstance(jar_name, str) or not jar_name:
            raise CurseForgeStateError("Release manifest all_entries contains invalid mapping data")
        if build_project in all_by_project or jar_name in jar_names:
            raise CurseForgeStateError("Release manifest build_project/jar_name mappings are not unique")
        all_by_project[build_project] = entry
        jar_names.add(jar_name)
    if list(all_by_project) != all_versions:
        raise CurseForgeStateError("Release manifest all_entries order/content differs from all_versions")

    selected_seen: list[str] = []
    for entry in files:
        if not isinstance(entry, dict):
            raise CurseForgeStateError("Release manifest files contains a non-object")
        build_project = entry.get("build_project")
        expected = all_by_project.get(str(build_project))
        if expected is None or entry.get("jar_name") != expected.get("jar_name"):
            raise CurseForgeStateError("Release manifest files contains an invalid project/JAR mapping")
        digest = entry.get("sha256")
        if not isinstance(digest, str) or not re.fullmatch(r"[0-9a-f]{64}", digest):
            raise CurseForgeStateError(f"Invalid SHA-256 for build project {build_project}")
        jar_path = manifest_path.parent / "dist" / str(entry["jar_name"])
        if sha256_file(jar_path) != digest:
            raise CurseForgeStateError(f"Release package SHA-256 mismatch for {jar_path}")
        selected_seen.append(str(build_project))
    if selected_seen != selected_versions:
        raise CurseForgeStateError("Release manifest files order/content differs from selected_versions")
    return manifest, context, all_by_project


def release_assets(
    client: JsonClient, repository: str, tag: str
) -> tuple[dict[str, Any], list[dict[str, Any]]]:
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


def checked_legacy_file_id(asset: dict[str, Any]) -> str | None:
    label = str(asset.get("label") or "")
    file_id = marker_file_id(asset)
    if label.startswith("CF:") and file_id is None:
        raise CurseForgeStateError(
            f"GitHub asset {asset.get('name')!r} has a malformed CurseForge legacy label"
        )
    return file_id


def run_git(
    repository_path: Path, arguments: list[str], *, allowed_returncodes: Iterable[int] = (0,)
) -> subprocess.CompletedProcess[str]:
    environment = os.environ.copy()
    environment["GIT_TERMINAL_PROMPT"] = "0"
    try:
        result = subprocess.run(
            ["git", *arguments],
            cwd=repository_path,
            env=environment,
            text=True,
            encoding="utf-8",
            errors="replace",
            capture_output=True,
            check=False,
        )
    except OSError as exc:
        raise CurseForgeStateError(f"Cannot run Git in {repository_path}: {exc}") from exc
    if result.returncode not in set(allowed_returncodes):
        detail = (result.stderr or result.stdout).strip()
        raise CurseForgeStateError(
            f"git {' '.join(arguments)} failed with exit {result.returncode}: {detail}"
        )
    return result


def notes_ref_for_tag(repository_path: Path, tag: str) -> str:
    notes_ref = f"{STATE_REF_PREFIX}/{tag}"
    run_git(repository_path, ["check-ref-format", notes_ref])
    return notes_ref


def prepare_git_repository(
    repository_path: Path, tag: str, release_commit: str
) -> tuple[str, bool]:
    if not repository_path.is_dir():
        raise CurseForgeStateError(f"Git repository path does not exist: {repository_path}")
    tag_ref = f"refs/tags/{tag}"
    run_git(repository_path, ["check-ref-format", tag_ref])
    run_git(
        repository_path,
        ["fetch", "--no-tags", "--depth=1", "origin", f"{tag_ref}:{tag_ref}"],
    )
    resolved = run_git(
        repository_path, ["rev-parse", "--verify", f"{tag_ref}^{{commit}}"]
    ).stdout.strip()
    if resolved != release_commit:
        raise CurseForgeStateError(
            f"Release Tag {tag} resolves to {resolved!r}, expected release-context commit "
            f"{release_commit!r}"
        )
    run_git(repository_path, ["cat-file", "-e", f"{release_commit}^{{commit}}"])

    notes_ref = notes_ref_for_tag(repository_path, tag)
    remote = run_git(
        repository_path,
        ["ls-remote", "--exit-code", "origin", notes_ref],
        allowed_returncodes=(0, 2),
    )
    note_exists = remote.returncode == 0
    if note_exists:
        lines = [line for line in remote.stdout.splitlines() if line.strip()]
        if len(lines) != 1 or lines[0].split("\t", 1)[-1] != notes_ref:
            raise CurseForgeStateError(f"Remote Git notes ref {notes_ref} is ambiguous")
        run_git(repository_path, ["fetch", "--no-tags", "origin", f"{notes_ref}:{notes_ref}"])
    else:
        local = run_git(
            repository_path,
            ["show-ref", "--verify", "--quiet", notes_ref],
            allowed_returncodes=(0, 1),
        )
        if local.returncode == 0:
            raise CurseForgeStateError(
                f"Local Git notes ref {notes_ref} exists while the remote ref is absent"
            )
    return notes_ref, note_exists


def validate_note_state(
    value: Any,
    repository: str,
    tag: str,
    release_commit: str,
    project_id: str,
    all_by_project: dict[str, dict[str, Any]],
) -> dict[str, Any]:
    if not isinstance(value, dict) or set(value) != STATE_KEYS:
        raise CurseForgeStateError("CurseForge Git note has an invalid top-level schema")
    expected_scalars = {
        "schema": STATE_SCHEMA,
        "repository": repository,
        "tag": tag,
        "release_commit": release_commit,
        "curseforge_project_id": project_id,
    }
    for key, expected in expected_scalars.items():
        if value.get(key) != expected:
            raise CurseForgeStateError(
                f"CurseForge Git note {key} is {value.get(key)!r}, expected {expected!r}"
            )
    files = value.get("files")
    if not isinstance(files, list) or not files:
        raise CurseForgeStateError("CurseForge Git note files must be a non-empty array")
    projects: set[str] = set()
    asset_ids: set[int] = set()
    jar_names: set[str] = set()
    file_ids: set[str] = set()
    order = {project: index for index, project in enumerate(all_by_project)}
    previous_order = -1
    for entry in files:
        if not isinstance(entry, dict) or set(entry) != STATE_FILE_KEYS:
            raise CurseForgeStateError("CurseForge Git note contains an invalid file entry")
        build_project = entry.get("build_project")
        jar_name = entry.get("jar_name")
        asset_id = entry.get("github_asset_id")
        digest = entry.get("sha256")
        file_id = entry.get("curseforge_file_id")
        expected = all_by_project.get(str(build_project))
        if expected is None or jar_name != expected.get("jar_name"):
            raise CurseForgeStateError("CurseForge Git note project/JAR mapping is invalid")
        if not isinstance(asset_id, int) or isinstance(asset_id, bool) or asset_id <= 0:
            raise CurseForgeStateError("CurseForge Git note has an invalid GitHub asset ID")
        if not isinstance(digest, str) or not re.fullmatch(r"[0-9a-f]{64}", digest):
            raise CurseForgeStateError("CurseForge Git note has an invalid SHA-256")
        if not isinstance(file_id, str) or not re.fullmatch(r"\d+", file_id):
            raise CurseForgeStateError("CurseForge Git note has an invalid file ID")
        if (
            build_project in projects
            or asset_id in asset_ids
            or jar_name in jar_names
            or file_id in file_ids
        ):
            raise CurseForgeStateError("CurseForge Git note contains duplicate/conflicting entries")
        entry_order = order[str(build_project)]
        if entry_order <= previous_order:
            raise CurseForgeStateError("CurseForge Git note file entries are not in Tag order")
        previous_order = entry_order
        projects.add(str(build_project))
        asset_ids.add(asset_id)
        jar_names.add(str(jar_name))
        file_ids.add(file_id)
    return value


def read_note_state(
    repository_path: Path,
    notes_ref: str,
    note_ref_exists: bool,
    release_commit: str,
    repository: str,
    tag: str,
    project_id: str,
    all_by_project: dict[str, dict[str, Any]],
) -> dict[str, Any] | None:
    if not note_ref_exists:
        return None
    listing = run_git(repository_path, ["notes", f"--ref={notes_ref}", "list"])
    lines = [line.split() for line in listing.stdout.splitlines() if line.strip()]
    if not lines:
        raise CurseForgeStateError(f"Git notes ref {notes_ref} exists but contains no note")
    if len(lines) != 1 or len(lines[0]) != 2 or lines[0][1] != release_commit:
        raise CurseForgeStateError(
            f"Git notes ref {notes_ref} must contain exactly one note on {release_commit}"
        )
    payload = run_git(
        repository_path, ["notes", f"--ref={notes_ref}", "show", release_commit]
    ).stdout
    try:
        value = json.loads(payload)
    except json.JSONDecodeError as exc:
        raise CurseForgeStateError(f"CurseForge Git note contains invalid JSON: {exc}") from exc
    return validate_note_state(
        value, repository, tag, release_commit, project_id, all_by_project
    )


def write_note_state(
    repository_path: Path,
    notes_ref: str,
    release_commit: str,
    state: dict[str, Any],
) -> None:
    payload = json.dumps(state, ensure_ascii=False, sort_keys=True, indent=2) + "\n"
    with tempfile.TemporaryDirectory(prefix="fga-curseforge-note-") as temporary:
        state_path = Path(temporary) / "state.json"
        state_path.write_text(payload, encoding="utf-8")
        run_git(
            repository_path,
            [
                "-c",
                "user.name=github-actions[bot]",
                "-c",
                "user.email=41898282+github-actions[bot]@users.noreply.github.com",
                "notes",
                f"--ref={notes_ref}",
                "add",
                "-f",
                "-F",
                str(state_path),
                release_commit,
            ],
        )
    run_git(repository_path, ["push", "origin", f"{notes_ref}:{notes_ref}"])
    local_ref = run_git(repository_path, ["rev-parse", "--verify", notes_ref]).stdout.strip()
    remote = run_git(repository_path, ["ls-remote", "--exit-code", "origin", notes_ref])
    remote_lines = [line.split() for line in remote.stdout.splitlines() if line.strip()]
    if len(remote_lines) != 1 or remote_lines[0] != [local_ref, notes_ref]:
        raise CurseForgeStateError("Remote Git notes ref does not match the state just pushed")


def asset_snapshot(asset: dict[str, Any], expected_name: str, expected_sha256: str) -> dict[str, Any]:
    asset_id = asset.get("id")
    if not isinstance(asset_id, int) or isinstance(asset_id, bool) or asset_id <= 0:
        raise CurseForgeStateError(f"GitHub asset {expected_name} has an invalid asset ID")
    if asset.get("name") != expected_name:
        raise CurseForgeStateError(
            f"GitHub asset name {asset.get('name')!r} does not match {expected_name!r}"
        )
    expected_digest = f"sha256:{expected_sha256}"
    if asset.get("digest") != expected_digest:
        raise CurseForgeStateError(
            f"GitHub asset {expected_name} digest {asset.get('digest')!r} does not match "
            f"{expected_digest!r}"
        )
    download_url = asset.get("browser_download_url")
    if not isinstance(download_url, str) or not download_url:
        raise CurseForgeStateError(f"GitHub asset {expected_name} has no browser download URL")
    parsed = urllib.parse.urlparse(download_url)
    if urllib.parse.unquote(Path(parsed.path).name) != expected_name:
        raise CurseForgeStateError(
            f"GitHub asset {expected_name} browser download URL has a different filename"
        )
    return {
        "id": asset_id,
        "name": expected_name,
        "digest": expected_digest,
        "browser_download_url": download_url,
    }


def previous_upload_history(
    client: JsonClient,
    repository: str,
    tag: str,
    build_projects: Iterable[str],
    current_run_id: int,
    current_attempt: int,
) -> tuple[int, dict[str, list[dict[str, Any]]]]:
    projects = list(build_projects)
    histories = {project: [] for project in projects}
    expected_jobs = {f"Publish CurseForge Minecraft {project}": project for project in projects}
    workflow_url = f"https://api.github.com/repos/{repository}/actions/workflows/release.yml/runs"
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
    inspected_attempts = 0
    for run in matching_runs:
        run_id = int(run["id"])
        maximum_attempt = int(run.get("run_attempt") or 1)
        if run_id == current_run_id:
            maximum_attempt = min(maximum_attempt, current_attempt - 1)
        for attempt in range(1, maximum_attempt + 1):
            inspected_attempts += 1
            jobs_url = (
                f"https://api.github.com/repos/{repository}/actions/runs/{run_id}"
                f"/attempts/{attempt}/jobs?per_page=100"
            )
            jobs_payload, _ = client.request("GET", jobs_url)
            jobs = jobs_payload.get("jobs") if isinstance(jobs_payload, dict) else None
            if not isinstance(jobs, list):
                raise CurseForgeStateError(f"GitHub jobs response is invalid for run {run_id}")
            total_count = jobs_payload.get("total_count")
            if isinstance(total_count, int) and total_count > len(jobs):
                raise CurseForgeStateError(
                    f"GitHub jobs response for run {run_id} is truncated; refusing partial audit"
                )
            for job in jobs:
                project = expected_jobs.get(str(job.get("name")))
                if project is None:
                    continue
                for step in job.get("steps") or []:
                    if step.get("name") == "Upload to CurseForge" and step.get(
                        "conclusion"
                    ) not in (None, "skipped"):
                        histories[project].append(
                            {
                                "run_id": run_id,
                                "attempt": attempt,
                                "conclusion": step.get("conclusion"),
                                "html_url": job.get("html_url"),
                            }
                        )
    return inspected_attempts, histories


def previous_upload_steps(
    client: JsonClient,
    repository: str,
    tag: str,
    build_project: str,
    current_run_id: int,
    current_attempt: int,
) -> tuple[int, list[dict[str, Any]]]:
    inspected, histories = previous_upload_history(
        client, repository, tag, [build_project], current_run_id, current_attempt
    )
    return inspected, histories[build_project]


def assert_upload_state_is_known(
    event_name: str, inspected_attempts: int, history: list[dict[str, Any]]
) -> None:
    if history:
        raise CurseForgeStateError(
            "The GitHub asset has no CurseForge state, but a previous upload step may have sent "
            f"the file. Refusing a duplicate upload: {history}"
        )
    if event_name == "workflow_dispatch" and inspected_attempts == 0:
        raise CurseForgeStateError(
            "No prior release workflow attempt for this tag can be audited. The missing state is "
            "unknown, so a manual duplicate upload is refused."
        )


def collect_state(
    manifest_path: Path,
    context_path: Path,
    git_repository: Path,
    repository: str,
    project_id: str,
    token: str,
    *,
    build_projects: list[str] | None = None,
    event_name: str | None = None,
    current_run_id: int = 0,
    current_attempt: int = 0,
    enforce_history: bool = False,
) -> dict[str, Any]:
    manifest, context, all_by_project = validate_release_inputs(
        manifest_path, context_path, project_id
    )
    selected = list(build_projects or manifest["selected_versions"])
    if len(selected) != len(set(selected)) or any(
        project not in manifest["selected_versions"] for project in selected
    ):
        raise CurseForgeStateError("State audit build projects do not match the selected manifest")

    notes_ref, note_ref_exists = prepare_git_repository(
        git_repository, context["tag"], context["commit"]
    )
    note = read_note_state(
        git_repository,
        notes_ref,
        note_ref_exists,
        context["commit"],
        repository,
        context["tag"],
        project_id,
        all_by_project,
    )
    note_by_project = {
        entry["build_project"]: entry for entry in (note.get("files", []) if note else [])
    }

    client = JsonClient(token)
    _, assets = release_assets(client, repository, context["tag"])
    assets_by_name: dict[str, list[dict[str, Any]]] = {}
    for asset in assets:
        assets_by_name.setdefault(str(asset.get("name")), []).append(asset)

    manifest_files = {entry["build_project"]: entry for entry in manifest["files"]}
    known_assets: dict[str, dict[str, Any]] = {}
    known_legacy: dict[str, str] = {}
    for project, metadata in all_by_project.items():
        matches = assets_by_name.get(str(metadata["jar_name"]), [])
        if project in selected or project in note_by_project or matches:
            if len(matches) != 1:
                raise CurseForgeStateError(
                    f"Expected one GitHub Release asset named {metadata['jar_name']}, "
                    f"found {len(matches)}"
                )
            known_assets[project] = matches[0]
            legacy_id = checked_legacy_file_id(matches[0])
            if legacy_id:
                known_legacy[project] = legacy_id

    file_id_owners: dict[str, str] = {}
    for project, note_entry in note_by_project.items():
        asset = known_assets.get(project)
        if asset is None:
            raise CurseForgeStateError(f"Git note references missing asset for {project}")
        snapshot = asset_snapshot(asset, note_entry["jar_name"], note_entry["sha256"])
        if snapshot["id"] != note_entry["github_asset_id"]:
            raise CurseForgeStateError(f"Git note asset ID conflicts with GitHub for {project}")
        file_id_owners[note_entry["curseforge_file_id"]] = project
    for project, file_id in known_legacy.items():
        owner = file_id_owners.get(file_id)
        if owner is not None and owner != project:
            raise CurseForgeStateError(
                f"CurseForge file ID {file_id} is assigned to both {owner} and {project}"
            )
        file_id_owners[file_id] = project
        note_entry = note_by_project.get(project)
        if note_entry and note_entry["curseforge_file_id"] != file_id:
            raise CurseForgeStateError(
                f"Git note and legacy Asset label disagree for build project {project}"
            )

    rows: list[dict[str, Any]] = []
    missing: list[str] = []
    for project in selected:
        entry = manifest_files[project]
        asset = known_assets[project]
        snapshot = asset_snapshot(asset, entry["jar_name"], entry["sha256"])
        note_entry = note_by_project.get(project)
        legacy_id = known_legacy.get(project)
        if note_entry:
            if (
                note_entry["github_asset_id"] != snapshot["id"]
                or note_entry["jar_name"] != snapshot["name"]
                or note_entry["sha256"] != entry["sha256"]
            ):
                raise CurseForgeStateError(
                    f"Git note metadata conflicts with Release manifest/asset for {project}"
                )
            file_id = note_entry["curseforge_file_id"]
            source = "note"
        elif legacy_id:
            file_id = legacy_id
            source = "legacy-label"
        else:
            file_id = None
            source = "missing"
            missing.append(project)
        rows.append(
            {
                "build_project": project,
                "jar_name": entry["jar_name"],
                "github_asset_id": snapshot["id"],
                "sha256": entry["sha256"],
                "browser_download_url": snapshot["browser_download_url"],
                "curseforge_file_id": file_id,
                "state_source": source,
                "legacy_label": f"CF:{legacy_id}" if legacy_id else None,
            }
        )

    if enforce_history and missing:
        if event_name not in ("release", "workflow_dispatch"):
            raise CurseForgeStateError("A valid event name is required for upload history audit")
        inspected, histories = previous_upload_history(
            client,
            repository,
            context["tag"],
            missing,
            current_run_id,
            current_attempt,
        )
        for project in missing:
            assert_upload_state_is_known(event_name, inspected, histories[project])

    return {
        "schema": STATE_SCHEMA,
        "repository": repository,
        "tag": context["tag"],
        "release_commit": context["commit"],
        "curseforge_project_id": project_id,
        "notes_ref": notes_ref,
        "note": note,
        "rows": rows,
        "all_by_project": all_by_project,
        "git_repository": git_repository,
    }


def audit_state(
    manifest_path: Path,
    context_path: Path,
    git_repository: Path,
    repository: str,
    project_id: str,
    current_run_id: int,
    current_attempt: int,
    event_name: str,
    cleanup_mode: str,
    token: str,
    output_path: Path,
) -> None:
    result = collect_state(
        manifest_path,
        context_path,
        git_repository,
        repository,
        project_id,
        token,
        event_name=event_name,
        current_run_id=current_run_id,
        current_attempt=current_attempt,
        enforce_history=True,
    )
    if cleanup_mode == "canary" and len(result["rows"]) != 1:
        raise CurseForgeStateError(
            "Canary legacy-label cleanup requires exactly one selected build project"
        )
    report = {
        key: value
        for key, value in result.items()
        if key not in ("note", "all_by_project", "git_repository")
    }
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(
        json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    print(f"Audited {len(result['rows'])} selected CurseForge publication state entries")


def precheck(
    manifest_path: Path,
    context_path: Path,
    git_repository: Path,
    build_project: str,
    repository: str,
    project_id: str,
    current_run_id: int,
    current_attempt: int,
    event_name: str,
    token: str,
    output_path: Path,
) -> None:
    result = collect_state(
        manifest_path,
        context_path,
        git_repository,
        repository,
        project_id,
        token,
        build_projects=[build_project],
        event_name=event_name,
        current_run_id=current_run_id,
        current_attempt=current_attempt,
        enforce_history=True,
    )
    row = result["rows"][0]
    should_upload = row["curseforge_file_id"] is None
    values = {
        "should_upload": str(should_upload).lower(),
        "file_id": row["curseforge_file_id"] or "",
        "asset_id": str(row["github_asset_id"]),
        "sha256": row["sha256"],
        "state_source": row["state_source"],
        "has_legacy_label": str(row["legacy_label"] is not None).lower(),
    }
    for name, value in values.items():
        write_github_output(output_path, name, value)
    if should_upload:
        print(f"No prior CurseForge upload was found for {row['jar_name']}")
    else:
        print(
            f"CurseForge state for {row['jar_name']} is {row['state_source']}: "
            f"{row['curseforge_file_id']}"
        )


def validate_upload_outputs(
    manifest_path: Path,
    build_project: str,
    project_id: str,
    version: str,
    files_json: str,
    version_url: str,
    output_path: Path,
) -> None:
    project_id = validate_project_identity(project_id)
    manifest, entry = manifest_entry(manifest_path, build_project)
    if not re.fullmatch(r"\d+", version):
        raise CurseForgeStateError(f"CurseForge did not return a numeric version ID: {version!r}")
    try:
        files = json.loads(files_json)
    except json.JSONDecodeError as exc:
        raise CurseForgeStateError(f"curseforge-files output is not valid JSON: {exc}") from exc
    if not isinstance(files, list) or len(files) != 1 or not isinstance(files[0], dict):
        raise CurseForgeStateError("curseforge-files must contain exactly one uploaded file")
    uploaded = files[0]
    if str(uploaded.get("id")) != version:
        raise CurseForgeStateError("curseforge-version and curseforge-files IDs disagree")
    if uploaded.get("name") != manifest["tag"]:
        raise CurseForgeStateError(
            "curseforge-files name does not match the tag display name passed to mc-publish"
        )
    expected_download = (
        f"https://www.curseforge.com/api/v1/mods/{project_id}/files/{version}/download"
    )
    if uploaded.get("url") != expected_download:
        raise CurseForgeStateError("curseforge-files download URL does not match project/file IDs")
    parsed = urllib.parse.urlparse(version_url)
    if (
        parsed.scheme != "https"
        or parsed.hostname not in ("curseforge.com", "www.curseforge.com")
        or not parsed.path.rstrip("/").endswith(f"/files/{version}")
        or parsed.query
        or parsed.fragment
    ):
        raise CurseForgeStateError("curseforge-url does not identify the uploaded CurseForge file")
    if Path(entry["path"]).name != entry["jar_name"]:
        raise CurseForgeStateError("Manifest upload path does not identify the expected JAR")
    write_github_output(output_path, "file_id", version)
    print(
        f"Validated mc-publish outputs for {entry['jar_name']}: CurseForge file {version}"
    )


def record_state(
    manifest_path: Path,
    context_path: Path,
    git_repository: Path,
    build_project: str,
    repository: str,
    project_id: str,
    file_id: str,
    source: str,
    token: str,
) -> None:
    if not re.fullmatch(r"\d+", file_id):
        raise CurseForgeStateError(f"Invalid CurseForge file ID: {file_id!r}")
    result = collect_state(
        manifest_path,
        context_path,
        git_repository,
        repository,
        project_id,
        token,
        build_projects=[build_project],
    )
    row = result["rows"][0]
    existing = result["note"]
    note_by_project = {
        entry["build_project"]: entry for entry in (existing.get("files", []) if existing else [])
    }
    existing_entry = note_by_project.get(build_project)
    if existing_entry:
        if existing_entry["curseforge_file_id"] != file_id:
            raise CurseForgeStateError(
                f"Existing Git note for {build_project} has a conflicting CurseForge file ID"
            )
        print(f"CurseForge Git note already records {build_project}: {file_id}")
        return
    if source == "legacy-label":
        if row["legacy_label"] != f"CF:{file_id}":
            raise CurseForgeStateError("Legacy label changed before it could be migrated")
    elif source == "upload":
        if row["legacy_label"] is not None or row["curseforge_file_id"] is not None:
            raise CurseForgeStateError("Cannot record a new upload over existing CurseForge state")
    else:
        raise CurseForgeStateError(f"Unsupported CurseForge state source: {source}")

    note_by_project[build_project] = {
        "build_project": build_project,
        "jar_name": row["jar_name"],
        "github_asset_id": row["github_asset_id"],
        "sha256": row["sha256"],
        "curseforge_file_id": file_id,
    }
    order = {project: index for index, project in enumerate(result["all_by_project"])}
    files = sorted(note_by_project.values(), key=lambda entry: order[entry["build_project"]])
    state = {
        "schema": STATE_SCHEMA,
        "repository": repository,
        "tag": result["tag"],
        "release_commit": result["release_commit"],
        "curseforge_project_id": project_id,
        "files": files,
    }
    validate_note_state(
        state,
        repository,
        result["tag"],
        result["release_commit"],
        project_id,
        result["all_by_project"],
    )
    write_note_state(
        git_repository, result["notes_ref"], result["release_commit"], state
    )
    notes_ref, exists = prepare_git_repository(
        git_repository, result["tag"], result["release_commit"]
    )
    verified = read_note_state(
        git_repository,
        notes_ref,
        exists,
        result["release_commit"],
        repository,
        result["tag"],
        project_id,
        result["all_by_project"],
    )
    if verified != state:
        raise CurseForgeStateError("Remote CurseForge Git note failed read-after-write verification")
    print(f"Persisted CurseForge file {file_id} for build project {build_project}")


def clear_legacy_labels(
    manifest_path: Path,
    context_path: Path,
    git_repository: Path,
    repository: str,
    project_id: str,
    mode: str,
    token: str,
) -> None:
    result = collect_state(
        manifest_path,
        context_path,
        git_repository,
        repository,
        project_id,
        token,
    )
    if mode == "canary" and len(result["rows"]) != 1:
        raise CurseForgeStateError(
            "Canary legacy-label cleanup requires exactly one selected build project"
        )
    note = result["note"]
    note_by_project = {
        entry["build_project"]: entry for entry in (note.get("files", []) if note else [])
    }
    for row in result["rows"]:
        note_entry = note_by_project.get(row["build_project"])
        if note_entry is None or note_entry["curseforge_file_id"] != row["curseforge_file_id"]:
            raise CurseForgeStateError(
                f"Verified Git-note state is required before clearing {row['build_project']} label"
            )

    client = JsonClient(token)
    client.headers["Content-Type"] = "application/json"
    cleared = 0
    for row in result["rows"]:
        if row["legacy_label"] is None:
            continue
        asset_id = row["github_asset_id"]
        asset_url = f"https://api.github.com/repos/{repository}/releases/assets/{asset_id}"
        before, _ = client.request("GET", asset_url)
        if not isinstance(before, dict):
            raise CurseForgeStateError(f"GitHub asset {asset_id} GET returned an invalid response")
        before_snapshot = asset_snapshot(before, row["jar_name"], row["sha256"])
        if marker_file_id(before) != row["curseforge_file_id"]:
            raise CurseForgeStateError(
                f"Legacy label changed before cleanup for {row['build_project']}"
            )
        client.request("PATCH", asset_url, data=json.dumps({"label": ""}).encode("utf-8"))
        after, _ = client.request("GET", asset_url)
        if not isinstance(after, dict):
            raise CurseForgeStateError(f"GitHub asset {asset_id} verification GET is invalid")
        after_snapshot = asset_snapshot(after, row["jar_name"], row["sha256"])
        if before_snapshot != after_snapshot:
            raise CurseForgeStateError(
                f"GitHub asset metadata changed while clearing label for {row['build_project']}"
            )
        if marker_file_id(after) is not None or after.get("label") not in (None, ""):
            raise CurseForgeStateError(
                "GitHub did not normalize an empty Asset label as expected; stopping cleanup"
            )
        cleared += 1
        print(
            f"Cleared verified legacy label for {row['jar_name']}; asset name and digest unchanged"
        )
    print(f"Cleared {cleared} legacy CurseForge Asset label(s) in {mode} mode")


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
    project_id = validate_project_identity(project_id)
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


def add_state_arguments(parser: argparse.ArgumentParser, *, build_project: bool = False) -> None:
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--context", type=Path, required=True)
    parser.add_argument("--git-repository", type=Path, required=True)
    if build_project:
        parser.add_argument("--build-project", required=True)
    parser.add_argument("--repository", required=True)
    parser.add_argument("--project", default=EXPECTED_PROJECT_ID)


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser()
    subparsers = parser.add_subparsers(dest="command", required=True)

    audit = subparsers.add_parser("audit")
    add_state_arguments(audit)
    audit.add_argument("--run-id", type=int, required=True)
    audit.add_argument("--run-attempt", type=int, required=True)
    audit.add_argument("--event-name", choices=("release", "workflow_dispatch"), required=True)
    audit.add_argument("--cleanup-mode", choices=("none", "canary", "confirmed"), default="none")
    audit.add_argument("--output", type=Path, required=True)

    check = subparsers.add_parser("precheck")
    add_state_arguments(check, build_project=True)
    check.add_argument("--run-id", type=int, required=True)
    check.add_argument("--run-attempt", type=int, required=True)
    check.add_argument("--event-name", choices=("release", "workflow_dispatch"), required=True)
    check.add_argument("--github-output", type=Path, required=True)

    upload = subparsers.add_parser("validate-upload")
    upload.add_argument("--manifest", type=Path, required=True)
    upload.add_argument("--build-project", required=True)
    upload.add_argument("--project", default=EXPECTED_PROJECT_ID)
    upload.add_argument("--version", required=True)
    upload.add_argument("--files", required=True)
    upload.add_argument("--url", required=True)
    upload.add_argument("--github-output", type=Path, required=True)

    record = subparsers.add_parser("record")
    add_state_arguments(record, build_project=True)
    record.add_argument("--file-id", required=True)
    record.add_argument("--source", choices=("legacy-label", "upload"), required=True)

    clear = subparsers.add_parser("clear-labels")
    add_state_arguments(clear)
    clear.add_argument("--mode", choices=("canary", "confirmed"), required=True)

    update = subparsers.add_parser("update")
    update.add_argument("--manifest", type=Path, required=True)
    update.add_argument("--build-project", required=True)
    update.add_argument("--project", default=EXPECTED_PROJECT_ID)
    update.add_argument("--file-id", required=True)

    validate_project = subparsers.add_parser("validate-project")
    validate_project.add_argument("--project", required=True)
    validate_project.add_argument("--github-output", type=Path)
    return parser


def main() -> int:
    args = build_parser().parse_args()
    try:
        if args.command == "audit":
            audit_state(
                args.manifest,
                args.context,
                args.git_repository,
                args.repository,
                args.project,
                args.run_id,
                args.run_attempt,
                args.event_name,
                args.cleanup_mode,
                os.environ.get("GITHUB_TOKEN", ""),
                args.output,
            )
        elif args.command == "precheck":
            precheck(
                args.manifest,
                args.context,
                args.git_repository,
                args.build_project,
                args.repository,
                args.project,
                args.run_id,
                args.run_attempt,
                args.event_name,
                os.environ.get("GITHUB_TOKEN", ""),
                args.github_output,
            )
        elif args.command == "validate-upload":
            validate_upload_outputs(
                args.manifest,
                args.build_project,
                args.project,
                args.version,
                args.files,
                args.url,
                args.github_output,
            )
        elif args.command == "record":
            record_state(
                args.manifest,
                args.context,
                args.git_repository,
                args.build_project,
                args.repository,
                args.project,
                args.file_id,
                args.source,
                os.environ.get("GITHUB_TOKEN", ""),
            )
        elif args.command == "clear-labels":
            clear_legacy_labels(
                args.manifest,
                args.context,
                args.git_repository,
                args.repository,
                args.project,
                args.mode,
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
        elif args.command == "validate-project":
            project_id = validate_project_identity(args.project)
            if args.github_output:
                write_github_output(args.github_output, "project_id", project_id)
            else:
                print(project_id)
    except (OSError, json.JSONDecodeError, CurseForgeStateError) as exc:
        print(f"CurseForge state error: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
