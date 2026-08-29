# Project Scripts

This directory contains local build, compatibility, and smoke-test helpers.

- `cmd/`: Windows command scripts.
- `powershell/`: PowerShell build and test scripts.
- `python/`: source-rewrite, release metadata/publishing helpers, and the Minecraft 1.21 runtime fixture launcher.
- `fixtures/mc121-runtime/`: independent Minecraft 1.21 server runtime used to load the shared `1.21.1` release JAR.
- `logs/`: local command output; ignored by Git.

The Gradle wrapper and core build files remain at the repository root.
