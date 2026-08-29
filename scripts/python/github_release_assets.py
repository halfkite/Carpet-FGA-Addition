#!/usr/bin/env python3
"""Idempotently upload final JARs to an existing GitHub Release."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Any


class ReleaseAssetError(RuntimeError):
    pass


class GitHubClient:
    def __init__(self, token: str):
        if not token:
            raise ReleaseAssetError("GITHUB_TOKEN is required")
        self.headers = {
            "Accept": "application/vnd.github+json",
            "Authorization": f"Bearer {token}",
            "X-GitHub-Api-Version": "2022-11-28",
            "User-Agent": "Carpet-FGA-Addition release publisher",
        }

    def request(
        self,
        method: str,
        url: str,
        data: bytes | None = None,
        content_type: str | None = None,
        accept: str | None = None,
    ) -> tuple[Any, dict[str, str]]:
        headers = dict(self.headers)
        if accept:
            headers["Accept"] = accept
        if content_type:
            headers["Content-Type"] = content_type
        request = urllib.request.Request(url, data=data, headers=headers, method=method)
        try:
            with urllib.request.urlopen(request) as response:
                payload = response.read()
                response_headers = dict(response.headers.items())
        except urllib.error.HTTPError as exc:
            body = exc.read().decode("utf-8", errors="replace")
            raise ReleaseAssetError(f"GitHub API {method} {url} failed: HTTP {exc.code}: {body}") from exc
        if not payload:
            return None, response_headers
        content_header = response_headers.get("Content-Type", "")
        if "json" in content_header:
            return json.loads(payload), response_headers
        return payload, response_headers


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def remote_sha256(client: GitHubClient, repository: str, asset: dict[str, Any]) -> str:
    digest = asset.get("digest")
    if isinstance(digest, str) and digest.startswith("sha256:"):
        return digest.removeprefix("sha256:").lower()
    payload, _ = client.request(
        "GET",
        f"https://api.github.com/repos/{repository}/releases/assets/{asset['id']}",
        accept="application/octet-stream",
    )
    if not isinstance(payload, bytes):
        raise ReleaseAssetError(f"GitHub did not return binary data for asset {asset['name']}")
    return hashlib.sha256(payload).hexdigest()


def list_assets(client: GitHubClient, repository: str, release_id: int) -> list[dict[str, Any]]:
    assets: list[dict[str, Any]] = []
    page = 1
    while True:
        url = (
            f"https://api.github.com/repos/{repository}/releases/{release_id}/assets"
            f"?per_page=100&page={page}"
        )
        payload, _ = client.request("GET", url)
        if not isinstance(payload, list):
            raise ReleaseAssetError("GitHub Release assets response is not an array")
        assets.extend(payload)
        if len(payload) < 100:
            return assets
        page += 1


def sync_assets(manifest_path: Path, repository: str, tag: str, token: str) -> None:
    manifest = json.loads(manifest_path.read_text(encoding="utf-8-sig"))
    if manifest.get("tag") != tag:
        raise ReleaseAssetError(f"Manifest tag {manifest.get('tag')!r} does not match {tag!r}")
    client = GitHubClient(token)
    encoded_tag = urllib.parse.quote(tag, safe="")
    release, _ = client.request(
        "GET", f"https://api.github.com/repos/{repository}/releases/tags/{encoded_tag}"
    )
    if not isinstance(release, dict) or release.get("draft"):
        raise ReleaseAssetError(f"Published GitHub Release {tag} was not found")
    assets = list_assets(client, repository, int(release["id"]))
    by_name: dict[str, list[dict[str, Any]]] = {}
    for asset in assets:
        by_name.setdefault(asset["name"], []).append(asset)
    for entry in manifest["files"]:
        path = Path(entry["path"])
        if not path.is_file():
            raise ReleaseAssetError(f"Release JAR does not exist: {path}")
        local_hash = sha256_file(path)
        if local_hash != entry["sha256"]:
            raise ReleaseAssetError(f"Manifest digest mismatch for {path}")
        matches = by_name.get(entry["jar_name"], [])
        if len(matches) > 1:
            raise ReleaseAssetError(f"GitHub Release contains duplicate assets named {entry['jar_name']}")
        if matches:
            existing_hash = remote_sha256(client, repository, matches[0])
            if existing_hash != local_hash:
                raise ReleaseAssetError(
                    f"GitHub asset {entry['jar_name']} already exists with a different SHA-256"
                )
            print(f"GitHub asset already matches: {entry['jar_name']}")
            continue
        query = urllib.parse.urlencode({"name": entry["jar_name"]})
        upload_url = (
            f"https://uploads.github.com/repos/{repository}/releases/{release['id']}/assets?{query}"
        )
        uploaded, _ = client.request(
            "POST", upload_url, data=path.read_bytes(), content_type="application/java-archive"
        )
        if not isinstance(uploaded, dict):
            raise ReleaseAssetError(f"GitHub did not return metadata for {entry['jar_name']}")
        uploaded_hash = remote_sha256(client, repository, uploaded)
        if uploaded_hash != local_hash:
            raise ReleaseAssetError(f"GitHub asset verification failed for {entry['jar_name']}")
        print(f"Uploaded GitHub asset: {entry['jar_name']}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--repository", required=True)
    parser.add_argument("--tag", required=True)
    args = parser.parse_args()
    try:
        sync_assets(args.manifest, args.repository, args.tag, os.environ.get("GITHUB_TOKEN", ""))
    except (OSError, json.JSONDecodeError, ReleaseAssetError) as exc:
        print(f"GitHub Release asset error: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
