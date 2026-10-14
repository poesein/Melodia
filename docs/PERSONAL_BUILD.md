# Melodia Personal 的构建与后续更新

本次个人修改保留在 `personal/local-first-search` 功能分支中：恢复原版安卓搜索页（含热搜首曲与热搜榜），音乐推荐/最新独立为底部“音乐”页，“你的偏好”旁增加搜索入口。在线歌曲优先使用已有本地音频，本地首页支持一键批量匹配及逐首结果，也支持手动双向关联和 JSON 备份。关联存储与批量报告独立于官方 Room 曲库和下载记录，便于后续合并；合并时仍需检查冲突、测试和构建，不能保证未来任意版本无需调整。

当前资料库个人 APK 为 `2.0.0-beta.6`（`versionCode=2000006`），另保留曲数筛选版 `2.0.0-beta.5`。两版仍以已记录的官方 beta.2 提交为代码基线；个人版本号不表示已经包含未来官方同名版本的源码。签名与应用 ID 保持一致，beta.6 可覆盖升级 beta.5 并保留已有数据。

beta.5 增加从音乐页搜索后返回原音乐页、匹配标题中的歌手提取、兼容合作歌手与发行版本、点选即关联及连续处理、匹配专辑缩略图、下次开始自动重试未成功项。歌手详情的专辑列表始终按最低曲数筛选，默认 0 首显示全部，不额外增加精选按钮；歌手右上角三个竖点菜单可设置该歌手的阈值，各歌手独立保存。设置页也可调整默认值。

beta.6 在此基础上增加可选的 MusicBrainz 主要录音室专辑目录匹配；每歌手独立开关和身份绑定，按需联网与缓存，关闭即可恢复曲数方案。详见 [资料库筛选说明](ARTIST_ALBUM_CATALOG.md)。

## Fork、官方更新和应用内更新是三件事

- Fork 不会自动同步 `rinchao0721/Melodia` 的后续源码提交，也不会自动复制官方 GitHub Releases 或资产。
- 新增的 `Personal Android APK` 工作流只响应手动运行，不会自动同步上游、创建 tag 或发布 Release。
- 个人包传入 `personalUpdateOwner=poesein`，应用内更新检查 `poesein/Melodia` 的 Releases。未传此参数的构建仍使用原项目默认的官方更新源。
- 工作流的 artifact 只是构建下载文件，不会自动成为应用内可检测的 Release。个人仓库没有发布新 Release 时，应用不会因为官方仓库发布新版本而得到个人版更新。
- 本地生成个人 APK 不等于 GitHub 已配置签名 secrets 或已成功运行工作流；没有完成下面的配置时，不能认为云端已经能够自动生产个人安装包。

## 合并官方后续修改

先保存当前修改并确认工作目录干净。在仓库根目录执行：

```powershell
git status
git switch personal/local-first-search
git remote -v
```

若还没有 `upstream` 远程，只执行一次：

```powershell
git remote add upstream https://github.com/rinchao0721/Melodia.git
```

每次更新从功能分支合并官方主分支：

```powershell
git fetch upstream
git merge upstream/main
```

遇到冲突，检查双方修改并保留需要的个人功能，再 `git add` 已处理文件、`git commit` 完成合并；可用 `git merge --abort` 撤销尚未完成的这次合并。优先检查播放源解析、本地音频匹配、曲库数据库、搜索 UI、APK 标识和更新源配置。然后运行相关测试、构建 APK，并在手机上检查本地播放、在线回退及搜索交互。

不要对个人功能分支执行 `reset --hard upstream/main`，也不要把网页 `Sync fork` 的强制丢弃提交选项用于覆盖个人功能。可以让 fork 的 `main` 单独跟随上游，但个人功能分支应通过 merge 接收更新。

## 保持 APK 可以覆盖升级

后续个人包保持以下配置：

| 配置 | 固定值或规则 |
| --- | --- |
| 应用 ID | `com.lin0721.linmusic.personal` |
| 应用名称 | `Melodia Personal` |
| 更新仓库 | `poesein/Melodia` |
| 签名 | 始终使用同一个个人 keystore、别名和密钥 |
| `versionCode` | 高于手机中已安装的个人版，并与下方版本公式一致 |

个人包可与原版应用并存。应用 ID 或签名证书改变后，不能当作同一个应用正常覆盖升级。妥善保管并备份个人签名文件和密码；不要将 keystore、密码或含密码的配置提交到 Git。后续构建不要另生成一把新密钥代替现有个人签名。

## 在 GitHub 手动构建

将个人修改和工作流提交到 fork 后，在仓库 Settings → Secrets and variables → Actions 添加以下 Repository secrets：

| Secret | 内容 |
| --- | --- |
| `RELEASE_KEYSTORE_BASE64` | 同一个个人 keystore 的 Base64 内容 |
| `RELEASE_STORE_PASSWORD` | keystore 密码 |
| `RELEASE_KEY_ALIAS` | 个人签名密钥别名 |
| `RELEASE_KEY_PASSWORD` | 该别名的密钥密码 |

工作流须位于仓库默认分支，GitHub Actions 才会提供手动运行入口；可将这个工作流单独合入默认分支，再在 Run workflow 的分支下拉框选择个人功能分支。通过网页秘密输入框填写签名材料，不要把内容粘贴到 issue、聊天、日志或源码。

进入 Actions → Personal Android APK → Run workflow，选择包含个人修改的分支，输入 `version` 和 `version_code`。例如：

```text
version:      2.0.0-beta.3
version_code: 2000003
```

这只是格式示例；实际输入必须高于已安装个人版。工作流使用 JDK 21，执行 `:app:testDebugUnitTest` 和 `:app:assembleRelease`，并传入：

```text
-PpersonalApplicationId=com.lin0721.linmusic.personal
-PpersonalAppLabel=Melodia Personal
-PpersonalUpdateOwner=poesein
```

四项签名材料缺失、版本格式错误、APK 回退到 Android Debug 签名、APK 证书与提供的个人 keystore 不一致，或 APK 包名/版本不符时，工作流失败，不上传该 APK。成功后在该次运行的 Artifacts 下载个人 APK；混淆映射用于排查该版本崩溃，单元测试报告用于查看测试结果。

## 可选：发布个人 Release 供应用内检查

当前 `VersionCodeResolver` 只接受 `vMAJOR.MINOR.PATCH`、`vMAJOR.MINOR.PATCH-beta.N` 和 `vMAJOR.MINOR.PATCH-rc.N` 形式的 tag；`beta.N` 的 N 为 0–49，`rc.N` 为 0–48。不要使用 `-personal`、`-local` 等额外后缀，否则应用内更新会跳过该 Release。

版本码公式为：

```text
versionCode = (MAJOR × 10000 + MINOR × 100 + PATCH) × 100 + subCode

beta.N: subCode = N
rc.N:   subCode = 50 + N
正式版: subCode = 99
```

同一基本版本中 beta、rc、正式版依次递增。例如 `2.0.0-beta.3` 为 `2000003`、`2.0.0-rc.0` 为 `2000050`、`2.0.0` 为 `2000099`。工作流会校验输入版本码与公式相同。个人版本发布次数与官方版本命名需自行协调；发布同一版本名的改动必须提升到下一个有效版本，不能重复使用旧 versionCode。

如需应用内更新，在 `poesein/Melodia` 手动创建 Release，使用受支持且与 APK `versionName` 对应的 tag，并上传**由个人工作流构建、具有个人应用 ID 和同一签名的 APK**。预发布 tag 在 GitHub 标为 Pre-release，并在应用中允许预发布更新。不要上传官方 APK作为个人 Release 的安装资产。

原仓库仍带有 `Release` 工作流，匹配 `v*.*.*` 的新 tag 推送可能触发原版 Android 和桌面构建及发布；该工作流没有个人构建参数。首次手动创建个人 Release 前，可在 fork 的 Actions 页面禁用原 `Release` 工作流，或由你另外明确改造成个人发布流程，避免它发布或混入原版包。新增的个人工作流本身不会创建、推送 tag，也不会触发该发布流程。

仅发布 artifact、复制官方 Release 信息、发布草稿 Release，或上传无法被版本解析器识别的 tag，都不能保证应用内更新可见。每次发布后实际检查个人应用能否找到该 Release、下载的包名是否正确、签名是否相同及能否覆盖安装。
