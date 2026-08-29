# 发布流程

正式发布由 GitHub Release 驱动。维护者先在 GitHub Releases 页面创建 Release，填写已有 Tag、标题和正文，然后点击 Publish。`.github/workflows/release.yml` 收到 `release: published` 后会 checkout 该 Tag 实际指向的 commit，而不是默认分支最新 commit。

Workflow 不创建或移动 Tag，不创建 Release，也不改写 Release 标题或正文。Tag 是 GitHub、Modrinth 和 CurseForge 的唯一发布版本号；三个平台都直接使用相同 Tag，不追加 Minecraft 版本、构建项目名或其他后缀。GitHub Release body 会原样作为 Modrinth 和 CurseForge changelog。

发布构建会将 Tag 作为 `publication_version` 传给 Gradle，因此发行 JAR 内的 `fabric.mod.json.version` 也必须与 Tag 完全一致。归档任务关闭文件时间戳并使用可复现文件顺序，使同一 Tag commit 的补跑能够得到相同摘要；普通 CI 和本地开发构建仍使用原有的时间戳开发版本。

## 发布项目与 Minecraft 1.21 兼容

`settings.json.publishVersions` 是唯一发行项目列表。`.github/workflows/build.yml` 的 `prepare` job checkout 指定 ref 后读取该数组并输出 JSON matrix，后续构建通过 `fromJSON(needs.prepare.outputs.matrix)` 展开，不在 workflow 中维护第二份版本列表。

多版本 Gradle/Preprocessor 结构不再包含独立 `1.21` 项目。Minecraft 1.21 和 1.21.1 共用 `versions/1.21.1` 构建的 JAR：

- `fabric.mod.json` 声明 `"minecraft": ">=1.21 <=1.21.1"`；
- 最终文件名使用兼容范围，例如 `carpet-fga-addition-1.5.4-mc1.21-1.21.1.jar`；
- Modrinth 和 CurseForge 同时标记 Minecraft 1.21、1.21.1；
- CI 使用独立 runtime fixture，在真实 Minecraft 1.21 + Fabric Loader + Carpet + Fabric API 环境中加载该 `1.21.1` 正式 JAR。

## 自动发布顺序

1. 校验已有 GitHub Release、Tag、标题和正文，解析 Tag commit，并确认 Tag 等于 `gradle.properties` 中的 `mod_version`。
2. 调用 `build.yml`，按 `settings.json.publishVersions` 构建全部发行项目。
3. 校验每个 build artifact 的 commit、SHA-256 和 `fabric.mod.json`，按 `artifact_mc_version` 生成最终文件名。
4. 将最终 JAR 上传到当前 GitHub Release。Release Assets 只包含可安装 JAR；同名且摘要相同会跳过，摘要不同则失败。
5. 正式 Release 发布到 Modrinth 和 CurseForge；prerelease 到此停止，只保留 GitHub Assets。

Modrinth 每个发行 JAR 对应一条 Version。所有 Version 的 `version_number` 都直接等于 GitHub Tag；workflow 先按项目 ID 与 Tag 取得候选集合，再用确定性的最终 JAR 文件名定位具体条目，不依赖可变显示名称。标题或正文变化只更新原条目的可变 metadata。每条 Version 都能声明自己的 Minecraft 版本和依赖，因此 Fabric API 仅在 Minecraft 1.21+ 条目中标记为 required。

CurseForge 每个 JAR 独立上传，但 display name/version 都直接使用 GitHub Tag。上传成功后，CurseForge file ID 会写入对应 GitHub Release Asset 的 label（`CF:<file-id>`），作为补发时的稳定标记。

## 失败重试

- GitHub 构建、打包或 Assets 上传失败：在原 workflow run 中使用 “Re-run failed jobs”。
- Modrinth 或 CurseForge 失败：从默认分支手动运行 `Release Carpet FGA Addition`，输入已有 Release Tag，并选择目标平台。
- 手动补发不会重新构建，而是下载并校验当前 GitHub Release Assets。
- CurseForge 可按 `settings.json.publishVersions` 中的构建项目名补发；Minecraft 1.21 对应项目名为 `1.21.1`，不接受 `1.21`。
- Modrinth 和 CurseForge 都可按 `settings.json.publishVersions` 中的构建项目名补发。
- CurseForge Asset marker 缺失且历史上传步骤状态不明确时，workflow 会拒绝再次上传，避免产生重复文件。

## 仓库设置

在 GitHub 仓库的 Settings → Secrets and variables → Actions 中配置：

- Secret `MODRINTH_API_TOKEN`
- Secret `CURSEFORGE_TOKEN`
- Repository variable `MODRINTH_PROJECT_ID`（可选，默认 `Nfhbipsz`）
- Repository variable `CURSEFORGE_PROJECT_ID`（可选，默认 `1660840`）

建议使用 GitHub Ruleset 禁止已发布 Tag 被强制移动或删除。
