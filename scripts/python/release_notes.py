#!/usr/bin/env python3
"""Generate a short Chinese release note from commits since the previous tag."""

from __future__ import annotations

import argparse
import re
import subprocess
from pathlib import Path


def git(*args: str) -> str:
    return subprocess.check_output(["git", *args], text=True).strip()


def detect_previous(head: str) -> str | None:
    try:
        return git("describe", "--tags", "--abbrev=0", f"{head}^")
    except subprocess.CalledProcessError:
        return None


def display_subject(subject: str) -> str:
    subject = re.sub(r"^(feat|fix|perf|refactor|docs|build|chore|test)(\([^)]*\))?!?:\s*", "", subject, flags=re.I)
    subject = re.sub(r"^release\s+", "", subject, flags=re.I)
    return subject.strip()


def category(subject: str) -> str:
    match = re.match(r"^(feat|fix|perf|refactor|docs|build|chore|test)(?:\([^)]*\))?!?:", subject, re.I)
    if not match:
        return "变更"
    return {
        "feat": "新增",
        "fix": "修复",
        "perf": "优化",
        "refactor": "重构",
        "docs": "文档",
        "build": "构建",
        "chore": "维护",
        "test": "测试",
    }.get(match.group(1).lower(), "变更")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--head", default="HEAD")
    parser.add_argument("--tag", required=True)
    parser.add_argument("--previous-tag")
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()

    previous = args.previous_tag or detect_previous(args.head)
    revision_range = f"{previous}..{args.head}" if previous else args.head
    raw = git("log", "--no-merges", "--format=%s%x09%h", revision_range)
    entries: list[str] = []
    seen: set[str] = set()
    for line in raw.splitlines():
        subject, _, short_hash = line.partition("\t")
        if not subject or subject.lower().startswith("release "):
            continue
        text = f"{category(subject)}：{display_subject(subject)}（{short_hash}）"
        if text not in seen:
            entries.append(text)
            seen.add(text)
        if len(entries) == 15:
            break

    if not entries:
        entries.append("维护：本次版本没有可自动归类的提交")

    lines = [f"## Carpet FGA Addition {args.tag}", "", *[f"- {entry}" for entry in entries], ""]
    args.output.write_text("\n".join(lines), encoding="utf-8")


if __name__ == "__main__":
    main()
