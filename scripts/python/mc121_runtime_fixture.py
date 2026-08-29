#!/usr/bin/env python3
"""Load the shared 1.21.1 production JAR on a real Minecraft 1.21 server."""

from __future__ import annotations

import argparse
import os
import queue
import re
import subprocess
import sys
import tempfile
import threading
import time
from pathlib import Path


STARTUP_TIMEOUT_SECONDS = 8 * 60
COMMAND_TIMEOUT_SECONDS = 30
FAILURE_PATTERNS = [
    re.compile(pattern, re.IGNORECASE)
    for pattern in (
        r"Mixin apply for mod carpet-fga-addition failed",
        r"Mixin prepare for mod carpet-fga-addition failed",
        r"Critical injection failure",
        r"InvalidInjectionException",
        r"InjectionError",
        r"Could not execute entrypoint stage",
        r"NoSuchMethodError",
        r"NoClassDefFoundError",
        r"net\.fabricmc\.loader\.impl\.FormattedException",
        r"Dependency resolution failed",
        r"Incompatible mod set",
    )
]


class FixtureError(RuntimeError):
    pass


def gradle_wrapper(repo_root: Path) -> Path:
    name = "gradlew.bat" if os.name == "nt" else "gradlew"
    wrapper = repo_root / name
    if not wrapper.is_file():
        raise FixtureError(f"Gradle wrapper not found: {wrapper}")
    return wrapper


def reader_thread(stream, output_queue: queue.Queue[str], lines: list[str]) -> None:
    for line in iter(stream.readline, ""):
        lines.append(line)
        output_queue.put(line)
        sys.stdout.write(line)
        sys.stdout.flush()


def has_failure(lines: list[str]) -> str | None:
    text = "".join(lines)
    for pattern in FAILURE_PATTERNS:
        match = pattern.search(text)
        if match:
            return match.group(0)
    return None


def wait_for(
    process: subprocess.Popen[str],
    output_queue: queue.Queue[str],
    lines: list[str],
    pattern: re.Pattern[str],
    timeout: int,
) -> None:
    deadline = time.monotonic() + timeout
    buffered = "".join(lines)
    if pattern.search(buffered):
        return
    while time.monotonic() < deadline:
        failure = has_failure(lines)
        if failure:
            raise FixtureError(f"Runtime failure detected: {failure}")
        if process.poll() is not None:
            raise FixtureError(f"Minecraft server exited early with code {process.returncode}")
        try:
            line = output_queue.get(timeout=1)
        except queue.Empty:
            continue
        if pattern.search(line) or pattern.search("".join(lines)):
            return
    raise FixtureError(f"Timed out waiting for runtime marker: {pattern.pattern}")


def run_fixture(jar: Path) -> None:
    jar = jar.resolve()
    if not jar.is_file():
        raise FixtureError(f"Built JAR does not exist: {jar}")
    repo_root = Path(__file__).resolve().parents[2]
    fixture_dir = repo_root / "scripts" / "fixtures" / "mc121-runtime"
    with tempfile.TemporaryDirectory(prefix="fga-mc121-runtime-") as temporary:
        run_dir = Path(temporary)
        (run_dir / "eula.txt").write_text("eula=true\n", encoding="ascii")
        (run_dir / "server.properties").write_text(
            "\n".join(
                (
                    "online-mode=false",
                    "server-port=0",
                    "view-distance=2",
                    "simulation-distance=2",
                    "difficulty=peaceful",
                    "level-name=fga-mc121-runtime",
                )
            )
            + "\n",
            encoding="ascii",
        )
        command = [
            str(gradle_wrapper(repo_root)),
            "-p",
            str(fixture_dir),
            "runServer",
            "--no-daemon",
            "--stacktrace",
            f"-PfgaJar={jar}",
            f"-PrunDir={run_dir}",
        ]
        process = subprocess.Popen(
            command,
            cwd=repo_root,
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            encoding="utf-8",
            errors="replace",
            bufsize=1,
        )
        assert process.stdout is not None
        assert process.stdin is not None
        lines: list[str] = []
        output_queue: queue.Queue[str] = queue.Queue()
        reader = threading.Thread(
            target=reader_thread, args=(process.stdout, output_queue, lines), daemon=True
        )
        reader.start()
        try:
            wait_for(
                process,
                output_queue,
                lines,
                re.compile(r"Done \([0-9.]+s\)!"),
                STARTUP_TIMEOUT_SECONDS,
            )
            process.stdin.write("carpet resilientPlants\n")
            process.stdin.flush()
            wait_for(
                process,
                output_queue,
                lines,
                re.compile(r"resilientPlants", re.IGNORECASE),
                COMMAND_TIMEOUT_SECONDS,
            )
            process.stdin.write("fga help\n")
            process.stdin.flush()
            wait_for(
                process,
                output_queue,
                lines,
                re.compile(r"FGA Help", re.IGNORECASE),
                COMMAND_TIMEOUT_SECONDS,
            )
            process.stdin.write("stop\n")
            process.stdin.flush()
            try:
                exit_code = process.wait(timeout=60)
            except subprocess.TimeoutExpired as exc:
                raise FixtureError("Minecraft server did not stop within 60 seconds") from exc
            if exit_code != 0:
                raise FixtureError(f"Minecraft server exited with code {exit_code}")
            failure = has_failure(lines)
            if failure:
                raise FixtureError(f"Runtime failure detected: {failure}")
        finally:
            if process.poll() is None:
                process.terminate()
                try:
                    process.wait(timeout=10)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait(timeout=10)
            reader.join(timeout=5)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--jar", type=Path, required=True)
    args = parser.parse_args()
    try:
        run_fixture(args.jar)
    except FixtureError as exc:
        print(f"Minecraft 1.21 runtime fixture failed: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
