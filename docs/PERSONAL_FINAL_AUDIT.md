# 个人版最终整理：代码与上游兼容性审查

审查日期：2026-10-22。审查范围为个人分支的所有功能域及其关键接入点，并对本轮确认的两个底层问题作独立修复；不等同于逐行证明整个应用没有缺陷。运行测试、APK 签名、覆盖升级及模拟器结果以本轮最终交付清单为准，本报告不把未执行的检查记为通过。

## 1. 官方基线与当前官方分支

- 个人版共同基线：`c79ff932b616465ff42296781fe07002fdbfddb5`，与 `PERSONAL_BASELINE.json` 一致。
- 审计开始时的个人提交：`cda0fd04`（beta.26）。它相对共同基线改动 289 个文件。
- 本次只读查询并取得的官方 `main`：`cefdac2ee830d66e6ed4962643d5c8a91807e485`，相对共同基线新增 24 个提交、改动 103 个文件。
- 本轮没有把这 24 个官方提交整体合入个人分支。仅回移了下述官方代理响应上限修复。个人版本号不能被解读为已包含同名或所有较新的官方版本。

在不修改分支、索引和工作文件的情况下，以共同基线运行三方 `merge-tree`。静态结果保存于任务工作目录 `work/beta27-upstream-merge-tree.txt`。已提交的 beta.26 与这次取得的官方 main 之间，只有以下 4 个路径双方都有改动，输出中没有 `<<<<<<<` / `>>>>>>>` 文本冲突标记：

| 文件 | 官方新增行为 | 个人版需要保留的行为 |
| --- | --- | --- |
| `app/.../core/player/PlayerManager.kt` | 向现有队列末尾追加曲目 | 本地优先、严格策略代际、无缝/重叠准备及撤销 |
| `app/.../feature/settings/ui/ExtensionsSettingsScreen.kt` | 界面缩放重建说明 | 个人入口及本轮重新整理后的设置归属 |
| `app/.../feature/settings/ui/SettingsScreen.kt` | 保存打开的子菜单 | 本轮设置分组、兼容旧入口 |
| `shared/.../core/preferences/SettingsPreferences.kt` | 全屏歌词与缩放偏好 | 个人偏好默认值、存量数据保留 |

这里的“无文本冲突”只描述静态合并，不代表接口、运行行为或测试已兼容。该结果针对审计起点的已提交 beta.26，不能冒充本轮尚未提交的最终界面变更已经完成上游集成。尤其是官方新增的 `PlaybackController.appendToQueue`、新作连续播放、Activity 配置缩放和桌面设置重构，需要在将来的独立集成分支中整体编译及回归。

## 2. 本轮确认并修复的底层问题

### 2.1 更换导入内容后，旧目标歌单请求污染新预览

`PlaylistTextImportViewModel.loadContent` 原先只取消并等待歌曲匹配任务，没有处理仍在读取旧预览目标歌单的 `targetJob`。重新粘贴或选择文件后，旧请求可把目标歌单、错误或完成状态写入新的预览；切换账户后也可能短暂显示旧账户的歌单。实际保存前已有服务器所有者检查，审查没有发现此问题能够绕过该检查写入别人的歌单。

修复位置：`app/.../feature/library/textimport/PlaylistTextImportViewModel.kt:132`。替换预览时同时取消旧目标请求，并等待其结果及 `finally` 完成后再发布新会话，不更改导入写入接口或歌单内容。

新增测试位于 `PlaylistTextImportViewModelTest`：

- `replacingPreviewCancelsItsOutstandingDestinationRead`：歌曲预览更换后，旧读取确实被取消，不能回填旧歌单或错误。
- `newAccountPreviewCannotReceiveOldAccountsDestinations`：旧账户的慢请求不能覆盖新账户预览，新账户随后仍能正常读取自己的目标歌单。

### 2.2 插件代理整段响应可能使应用耗尽内存

官方提交 `20b94c022149adb228756c3c9dd5a8a691c2d952` 已修复该问题，但审计开始时的个人版尚未包含：插件请求被代理为完整字符串，再经 `JSONObject.quote` 传给 JS；若服务器返回大文件，会出现多份大字符串及内存峰值。

本轮将该官方小修复原样移植到两处：

- `shared/.../core/source/SourceHttpClient.kt:58`：可选的 `maxBodyBytes` 参数；先核对声明长度，再有界预读，处理没有长度或经过解压的响应。
- `app/.../feature/source/plugin/LxPluginEngine.kt:28`、`:314`：仅插件代理的 GET / POST 明确传入 **4 MiB** 上限，超限走原有请求失败分支。

两份生产文件已通过 Git blob hash 比对，与官方该提交对应文件完全相同，便于今后合并时自动消解重复补丁。已有调用参数及不指定上限的行为保留，不改变严格模式音频片段读取器的独立预算。

新增 `SourceHttpClientBodyLimitTest` 六项：正常 POST 与请求内容、UTF-8 精确字节上限、声明长度超限、chunked 无长度超限、gzip 解压后超限，以及旧调用默认行为。测试使用本机 MockWebServer，不调用社区 JS 后端。

## 3. 已有个人功能的关键检查与测试映射

| 功能域 | 审查结论及合并触点 | 应保留的回归覆盖 |
| --- | --- | --- |
| 本地优先与双向歌曲关联 | `preferred_local_audio` 独立保存引用，不伪装成下载记录；自动查询完成前重新读取手动选择及开关。播放器原始及预准备路径都先查本地关联。 | `PreferredLocalAudioTest`、`CurrentPreferredLocalAudioTest`、`ManualAssociationCommitTest`、`ManualLocalAudioAssociationsTest`、`PreferredLocalAudioRepository` 实际安装回归 |
| 批量歌曲与专辑匹配 | 专辑范围、人工核验指纹、歌曲关联分别存储；预览不等于确认，文件内容变化后不能继续沿用旧确认。报告和关联均与官方曲库表隔离。 | `BatchAlbumMatchPolicyTest`、`AlbumReviewPolicyTest`、`BatchAlbumWritePolicyTest`、`BatchAssociationGuardTest`、报告 codec 与人工核验跨重启回归 |
| 关联导入导出 | 格式有大小及条数上限、未知字段兼容、写入前重新规划冲突；不把自动播放命中当成已确认记录导出。 | `AssociationBackupCodecTest`、文件失效/重选、重复导入、存量链接保留 |
| WAV 标签与多作者合辑 | 扫描、导入及关联检查复用补充元数据；合辑按专用目录证据分组，歌曲作者仍分别保留。 | `LocalAudioMetadataTest`、`LocalAlbumGroupingTest`、`LocalAlbumFolderIdentityTest`，用户提供的真实音频样本 |
| 严格换源与旧缓存 | `StrictPlaybackPolicy` 明确拒绝无证明网络来源及历史流缓存；`PlayerManager.onSourcePolicyChanged` 先递增代际，再取消解析、预取和转场。媒体会话及恢复也通过门槛。 | `AudioSourceRouterStrictTest`、`StrictPlaybackPolicyTest`、`SourcePreferencesStrictTest`、`BoundedAudioRangeReaderTest`、`PcmIdentityMatcherTest`、Android 真实解码与模式切换 |
| 原始无缝及平滑重叠 | 使用与当前播放相同的本地优先/严格来源解析；只预准备下一首，队列、匹配或策略变化撤销；原生转场按代际去重。 | `GaplessQueueSnapshotTest`、`GaplessPlaybackTest`、重叠/取消/短曲保护原生测试；手机实际音频输出仍需验收 |
| TXT / 粘贴导入 | 文件和文本共用解析；双向名称有歧义时不自动猜。目标保存前校验账户、所有者和服务器原始 ID；重试固定目标并追加缺失项。 | `PlaylistTextDocumentTest`、`PlaylistTextWriterTest`、`PlaylistTextImportViewModelTest`、粘贴/目标选择与触底滑动仪器测试 |
| 歌单倒序、分页与新增歌曲 | 显示与播放共用排序；倒序完整队列需补齐曲目，不把分页前缀当完整列表，不主动改服务器顺序。 | `PlaylistTrackSortTest`、`PlaylistPlaybackOrderTest`、`PlaylistTrackPagingTest`、新增/删除与迟到分页回归 |
| 个人音乐库和弹层滚动 | 已展开弹层只消费列表未使用的向上余量，不截断正常惯性及向下关闭；不是靠不断重设列表位置来掩盖抖动。 | `ExpandedSheetScrollBoundary` 边界单测、歌手/专辑展开全部及导入报告多次触底仪器测试 |
| 歌手资料库及人工修正 | 目录匹配、纠正存储与官方专辑分页分离。本轮去掉最少曲数逻辑，仍需保留每歌手资料库选择、手动添加/隐藏及恢复记录。 | `ArtistCatalogMatcherTest`、`ArtistCatalogRepositoryTest`、`ArtistAlbumCorrectionsTest`、correction repository/ViewModel 测试及人工修正时间排序 |

截至本轮专项检查，除第 2 节两项外，未取得足以确认其他新增底层缺陷的证据；这不构成对所有路径无缺陷的保证。新专辑页与设置、导航、歌手页整理由本轮各专项实现并统一构建，不能用这里列出的历史测试名称代替本轮实际通过结果。

## 4. 新收藏专辑页必须守住的播放与滚动约束

- “随机播放”先打乱专辑，再按每张专辑的原曲序展开。启动时不能继承旧的全曲随机或单曲循环模式，导致二次打乱/卡在一首。
- 同一首歌在不同专辑出现时，应保留各自队列位置，不应按歌曲 ID 全局去重破坏专辑完整性。
- 网络取曲目属于准备过程；取消、切页、账户变化或后来一次播放请求应使旧结果失效。准备未成功时不能先清空用户原有队列。
- 专辑页作为普通页面使用一个受约束的 Lazy 网格，不叠加可滚动父列表或弹层惯性处理；搜索、列数和收藏变化后使用稳定专辑 ID，保留合理滚动位置。
- 只应对 ModalBottomSheet 使用 `ExpandedSheetScrollBoundary`；该类 `onPreFling` / `onPostScroll` / `onPostFling` 位于 `app/.../core/ui/scroll/ExpandedSheetScrollBoundary.kt:22` 起。普通页面不应为“修抖动”盲目吞掉所有拖动或反复启动动画。

## 5. 与官方更新的隔离边界及后续 PR 建议

审计起点的官方 `HomeScreen`、官方核心网络目录、原音乐库数据接口、原曲库数据库目录没有被个人分支修改；官方依赖目录 `gradle/libs.versions.toml` 和 `shared/build.gradle.kts` 也保持基线。个人新增匹配、资料库、报告、布局状态以独立文件/存储为主；必要的播放器、导航、设置接入仍然是未来升级需要人工复查的热点。

本轮删除附加音乐页应只移除该附加入口及其按钮，不删除官方主页的音乐内容。本轮移除曲数过滤应停止读取旧阈值而保留其他旧偏好，不做清库或全量偏好重置。最终 APK 必须保持个人应用 ID 与原签名，递增 versionCode 并通过覆盖安装保留数据验证。

未来向官方提 PR 时建议独立组织：

1. 有证据的本地专辑标签误判修复；WAV 补充标签读取另一个 PR。
2. 多作者合辑分组，配上同目录/平铺目录反例。
3. 本地歌曲/专辑关联及批量核验，排除个人导航、签名、更新源等内容。
4. MusicBrainz 主要录音室专辑与手动修正，排除已删除的最少曲数功能。
5. TXT 文本导入及目的歌单选择；底层导入会话竞态与该功能一起提交。
6. 无缝/平滑转场、歌单倒序等分别提交，保留共享层和原生验证，不与页面美化打包。

个人音乐库重构、收藏专辑入口、设置重排、个人品牌与更新仓库留在个人分支。官方 `20b94c02` 只需保留来源归属，不向官方重复提交相同修复。

后续更新应在独立集成分支合并官方，再运行 Android 单测、共享模块测试、桌面测试/编译、Release（含 R8）、旧个人包覆盖安装及关键真实播放回归。不得用当前静态“无文本冲突”结果替代这些步骤，也不能承诺未来官方任意改动都无需适配。

## 6. 当日官方 main 的隔离集成验证方案

任务目录中的 `work/verify-upstream-integration.ps1` 不检出、重置或更新个人分支。它接受一个固定的最终个人提交 SHA，与固定官方提交做现代 `merge-tree --write-tree`，使用唯一 TEMP 下的 `GIT_OBJECT_DIRECTORY` 保存临时对象，现有仓库对象目录只作为只读 alternate。因此临时合并不写公共 refs、原索引或原工作文件。

只有三方合并返回成功才用 `git archive` 导出没有 `.git` 的临时源码。冲突返回非零并保存完整诊断，不能带着冲突标记冒充编译成功。脚本默认离线复用现有 toolRoot 的 Gradle/JDK/SDK 缓存，不读取个人 Release 签名密码，也不生成面向用户的正式发布包。

最终提交落盘后，从任务根目录执行（将占位内容换成真实 40 位 SHA）：

```powershell
.\work\verify-upstream-integration.ps1 `
  -PersonalCommit <最终个人提交SHA> `
  -UpstreamCommit cefdac2ee830d66e6ed4962643d5c8a91807e485 `
  -Stage Compile
```

- `Prepare`：只合并、导出和记录，不运行 Gradle。
- `Compile`：编译 `:shared:compileKotlinDesktop`、`:app:compileDebugKotlin`、`:desktopApp:compileKotlin`。
- `Tests`：在上述编译基础上再跑 `:shared:desktopTest`、`:app:testDebugUnitTest`、`:desktopApp:test`；桌面原生测试依赖仍须按既有测试环境准备。
- 缓存缺失时保持失败记录；如确需取依赖，可明确使用 `-AllowNetwork`。不要用空实现替换新接口来掩盖合并失败。

每次运行的 JSON 清单记录双方 SHA、共同基线、临时合并树、源 HEAD/状态前后值、实际任务与退出码；merge/build 日志和临时源码保留，便于复查。此处列的是准备好的验证方法，不能在尚未运行时称为集成通过；执行后的结果应由最终交付记录补充。

### 4 个交集文件之外的跨文件语义风险

1. 官方新增 `PlaybackController.appendToQueue` 的同时，也更新了 `PlaybackQueue`、Android PlayerManager、桌面播放器实现与共享测试的 `PodcastTestSupport.FakePlaybackController`。个人分支没有改动该 Fake 文件；完整合并会带入其 `appendCalls` 和 override，不需要在当前旧接口的个人分支中硬塞无效 override。新专辑单测使用 Java Proxy，不因新增一个接口成员而产生实现缺失编译错误。
2. 官方新作页用 `appendToQueue` 连续追加；合并后应测试追加时不重置当前歌曲，以及个人播放器对队列变化撤销旧的无缝快照。不能只拷贝接口签名而漏掉实现与队列测试。
3. 官方 `LibraryViewModel.selectFilter` 仅新增显式设置筛选的方法，服务于桌面返回/前进；不改变 `LibraryItem`、收藏专辑 DTO 或个人收藏接口。本轮独立专辑页不应依赖原音乐库私有排序状态。
4. 官方 `PlayerViewModel.resolveSongDetail` 是新增的详情复用入口；它的使用方与桌面“更多菜单/加入歌单”应一起合并，不应为了保留旧文件而遗漏新方法。
5. 官方缩放已从 Compose 局部变换改到 Activity 配置，包括 `MainActivity`、主题配置、`UiScale` 及设置状态保存；只合并设置文案不能得到实际行为。全屏歌词偏好也需随其使用方一起合并，旧默认值及个人设置键应继续独立保存。
6. 官方桌面缓存根目录迁移、设置重构、任务栏缩略图等改动与个人 Android 页面无直接冲突，但跨文件调用较多。静态合并没有文本冲突不能代替桌面编译，缓存迁移运行验证也不能由 Android 编译推定通过。

导入会话专项单测在修正测试自身对 `Dispatchers.Default` 的等待和 ViewModelScope 清理后，已定向验证 **17 项通过、0 失败**（`work/beta27-import-race-retest.log`）；该记录不替代最终全量测试。
