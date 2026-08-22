# 发布流程

仓库的正式发布由 `.github/workflows/release.yml` 负责。它会按照 `build.gradle` 中的发布矩阵构建 Minecraft `1.20.1`、`1.21`、`1.21.1`、`1.21.4`、`1.21.5`、`1.21.8`、`1.21.10`、`1.21.11`、`26.1.2` 和 `26.2`，跳过不发布的 `1.21.3`。

以后需要发布时，告诉维护者“发布”并提供版本号即可。维护者在 GitHub Actions 手动运行 `Release Carpet FGA Addition`，输入例如 `1.5.1`。`release` 类型会从提交记录生成简短更新日志，创建同名 GitHub Release，构建所选版本，并将成功的 JAR 同步到 GitHub、Modrinth 和 CurseForge；正式版上传前会把 JAR 重命名为不含 Gradle 构建时间戳的 `carpet-fga-addition-版本-mc版本.jar`。选择 `beta` 类型时保留原始时间戳文件名，并以 beta 版本发布。GitHub Release 只附加可安装的 JAR，Modrinth 将全部 Minecraft 版本归入同一个纯版本号条目。

## 仓库设置

在 GitHub 仓库的 Settings → Secrets and variables → Actions 中配置：

- Secret `MODRINTH_API_TOKEN`
- Secret `CURSEFORGE_TOKEN`
- Repository variable `MODRINTH_PROJECT_ID`（可选，未设置时使用项目 slug `carpet-fga-addition`）
- Repository variable `CURSEFORGE_PROJECT_ID`（可选，当前默认使用项目 `1660840`）

Token 只存在于 GitHub Secrets，不写入仓库。两个项目 ID 不是凭猜测写死的，首次配置仓库时填入对应项目的真实 ID。

## 构建检查

`Gradle build` 会在 push、Pull Request 和手动运行时检查发布矩阵。可以用 `target_subproject` 只构建一个版本；正式发布也支持只发布一个版本，适合先验证新 Minecraft 版本。

构建成功只代表 JAR 构建和打包通过，不代表已经完成游戏内测试。发布前仍应确认对应版本的服务端启动和功能实测结果。
