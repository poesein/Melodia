# 个人功能的上游 PR 准备与维护边界

核查日期：2026-10-15。已实际读取官方仓库 `rinchao0721/Melodia` 的 `main`；最新提交仍为 `c79ff932b616465ff42296781fe07002fdbfddb5`，与个人版起点一致。本文件是提交准备记录；两个功能没有向官方创建 PR。

## 官方明确要求

仓库没有单独的 `CONTRIBUTING.md` 或 `AGENTS.md`。可执行的约定来自以下文件，而非额外推定的审批要求：

- [功能 PR 模板](https://github.com/rinchao0721/Melodia/blob/c79ff932b616465ff42296781fe07002fdbfddb5/.github/PULL_REQUEST_TEMPLATE/feature.md)：单一功能，按模块说明，附 Android 界面证据；文件较多时说明为何作为一个功能交付；复用现有交互；核对网易云接口；无临时文件、调试代码、密钥或签名材料。
- [README 的模块和依赖方向](https://github.com/rinchao0721/Melodia/blob/c79ff932b616465ff42296781fe07002fdbfddb5/README.md)：按业务域组织 `data/domain/ui`；平台能力位于 `app`，跨平台接口位于 `shared`；`core` 不依赖 `feature`。
- [CI](https://github.com/rinchao0721/Melodia/blob/c79ff932b616465ff42296781fe07002fdbfddb5/.github/workflows/ci.yml)：JDK 21；Android、shared、desktop 单元测试，桌面编译，含 R8 的 Android Release。模板复选项没有列出 `desktopApp:test`，实际 CI 有此项，准备流程按较完整的 CI 执行。
- [发布工作流](https://github.com/rinchao0721/Melodia/blob/c79ff932b616465ff42296781fe07002fdbfddb5/.github/workflows/release.yml) 根据 `feat/fix/refactor/test/docs` 等提交前缀整理发布说明；使用该前缀有利于日志分类，但仓库未声明它是 PR 标题的强制门槛。

两份正文按 `feature.md` 准备；提交地址可用 `?template=feature.md`。没有关联的官方 Issue 就不写虚构的 `Closes #...`。功能建议模板要求先查重；真正提交前需重新核对 Issues/PR，不能把本地准备等同于已获维护者接受。

## 两个互相独立的功能

| 功能 | 主要代码 | 集成边界 | 数据与兼容性 |
|---|---|---|---|
| 歌手主要录音室专辑筛选 | `feature/artist/{data,domain,ui}/catalog`，共享 `ArtistAlbumSelection`、`ArtistAlbumFilterSettings` | 歌手专辑列表、三个竖点、扩展设置、Koin 注册 | 独立资料库偏好/缓存；已有共享模型只增加有默认值的可选字段；不改官方 Room schema |
| 本地歌曲和专辑关联及优先播放 | `feature/localmusic/{data,domain,ui}` 中关联、候选、批处理、备份模块 | `LocalMusicApi`、播放器、歌曲菜单、专辑菜单、设置及 DI | 独立歌曲关联、专辑对应、批处理报告；文件只保存引用；保留下载流程和旧记录；手动确认具有独立完成状态 |

两个功能各自从同一个官方提交构造，不依赖对方先合并。它们修改少数相同的设置/DI 文件，后续合并会有可人工核对的小块冲突，但不存在运行时依赖。不要直接用整个 `personal/local-first-search` 分支作为任一上游 PR 的 head；该分支还包含个人打包和音乐页定制。

beta.12 的独立目录为 `work/upstream-prs-beta12-final/artist-album-filter`（36 个功能文件）和 `work/upstream-prs-beta12-final-local/local-audio-matching`（101 个功能文件，含最终“刷新曲库”扫描修复）。两个补丁已分别对固定官方基线通过 `git apply --cached --check`；构建文件、Manifest、更新仓库实现均与官方基线逐字节相同。对应源码 ZIP 共 999 / 1049 个文件，未包含签名、`local.properties`、构建目录、个人发布工作流或个人构建说明。旧的 `upstream-prs-beta12-final/local-audio-matching` 只保留为构建诊断记录，不是 beta.12 最终交付。

当前本地匹配准备包已更新至 `work/upstream-prs-beta13-local-final/local-audio-matching`（112 个功能文件），包括专辑标签与目录同名修复、目录内无专辑歌手合辑分组、WAV 标签/封面只读补读及旧导入 WAV 刷新。新增的 11 个文件涉及原本尚未修改的扫描、索引与封面入口以及新元数据模块和测试，仍属于同一功能链路。新补丁独立 apply-check、构建与测试通过；准确哈希和逐文件来源见 `beta13上游本地匹配逐文件清单.json`。导出文档的个人版本适用行改为功能适用范围，并移除被排除的个人构建指南链接；个人源码中的文档保持原样。歌手专辑筛选全部导出块已按当前源码重新生成并与 beta.12 已测包逐字节比对一致，因此复用原有构建证据，不将未执行的重跑写成新测试。

本地匹配的文件较多，因为从推荐到保存、回放、离线可播放判断、批量进度、撤销及备份是一条完整用户路径。若维护者要求分阶段，可以按“关联存储与手动本地优先 → 歌曲推荐/批量/备份 → 双向专辑与批量核验”堆叠提交；后两段明确依赖前段，不将不完整入口暴露给用户。

## 必须排除的个人变化

两个功能包都保留官方 `applicationId`、应用名、更新仓库、构建版本、Manifest、发布工作流；不携带个人签名或 APK。排除 `personal-android.yml`、`PERSONAL_BUILD.md`、`PERSONAL_BASELINE.json`。

独立音乐底栏、主页调整、“你的偏好”搜索入口、返回音乐页的导航行为及对应测试属于个人 UI 定制，不进入两个功能 PR。专辑筛选 PR 不含本地播放器或匹配代码；本地匹配 PR 不含 MusicBrainz、OpenCC 或歌手专辑筛选。MusicBrainz User-Agent 使用真实构建版本和项目联系 URL，生产源码本身不依赖个人更新参数。

## 混合文件的精确拆分

| 文件 | 专辑筛选 PR | 本地匹配 PR |
|---|---|---|
| `di/ViewModelModule.kt` | 只新增 `ArtistCatalogViewModel` | 只新增本地选择器、专辑关联、专辑批量 ViewModel |
| `SettingsViewModel.kt` | 只保留 `artistCatalog` 依赖、曲数和资料库偏好 | 只保留 `preferredLocalAudio` 依赖和本地播放偏好 |
| `SettingsScreen.kt` | 只增加“歌手专辑”设置分组 | 只增加“本地与网易云匹配”入口 |
| `ArtistContent.kt` | 保留筛选、资料库和分页 UI；歌曲菜单登录行为保持官方原样 | 在官方文件上仅开放未登录歌曲菜单，使本地关联可用；账号操作仍各自校验登录 |
| `SearchScreen.kt` | 保持官方原样 | 仅开放未登录歌曲菜单；保持官方搜索页与返回逻辑 |

其他文件按业务域白名单复制；新增源码或上游移动文件导致未分类时导出失败，要求人工审核。`manifest.json` 逐文件记录源 SHA256、导出 SHA256 和转换说明，可审计完整文件与局部块的来源。

## 设计核查与评审入口

### 专辑筛选

从 `ArtistCatalogMatcher` 的分类和版本选择阅读，再到 Repository 的分页、单请求节流、缓存与错误语义，最后查看 ViewModel/UI 接线。最低曲数应参与同一作品的代表发行选择，不能先选少曲原版、再把整张有合格扩展版的作品过滤掉。重复选择已经绑定的资料库歌手时，即使偏好值未改变，也要重新启动先前取消的读取任务。

资料库是辅助分类来源。未知类型不假定为录音室专辑；失败保留旧缓存或原列表；重制/地区版本可归并，Live、Remix、语言等录音差异不能仅靠模糊名称消除。全局默认和每位歌手例外分别保存。上游是否采用默认开启属于产品策略，可在评审时讨论，不假称这是官方既定要求。

未新增运行库依赖；包含第三方 OpenCC 固定版本字符表，必须保留 `LICENSE`、`PROVENANCE.txt` 和字表。它仅作字符折叠，不能声称完整 OpenCC 语义转换。MusicBrainz 按官方建议提供版本/联系 URL，并将请求间隔限制为至少 1.1 秒；参见 [API 限频](https://musicbrainz.org/doc/MusicBrainz_API/Rate_Limiting) 和 [Release Group 类型定义](https://musicbrainz.org/doc/Release_Group/Type)。

### 本地匹配

阅读顺序为 `LocalMatchMetadata`/各个 MatchPolicy → `PreferredLocalAudioRepository` 和存储 → 批处理 Repository/ViewModel → 对话框。纯匹配策略不读文件或联网；推荐与提交分开，提交重新核对文件身份、权限、现有手动选择与自动匹配禁用状态。

本地专辑索引保留有效专辑标签，不再因为它与目录同名就改为未知。缺少专辑歌手时，仅对同一专用目录内、专辑标签等于该目录名的多歌手曲目推断合辑；跨目录或平铺同名专辑不靠标题合并。普通单歌手和有明确专辑歌手的旧标识保持不变；合辑成员查询通过 `LocalLibraryIndex.albumForTrack` 使用完整索引身份，歌手专辑页与匹配入口不能再各自按单首歌曲推算而漏掉合辑。

WAV 在部分 Android 系统中会缺失 ID3 标签，即使真实嵌入字段齐全，也可能被文件名/目录名替代。`data/metadata` 复用项目已有 TagLib 1.0.5，只读打开现有授权文件；扫描、导入、关联核验及封面回退共用这条路径，未新增依赖或权限。WAV 以实际嵌入标签优先，其他格式仅补缺项；时长继续由 Android 解码器读取。旧手动导入 WAV 仅在显式刷新曲库时重读文字字段，保留引用、歌曲 ID、来源和加入日期，缺失的新字段不覆盖旧值；原音频和数据库 schema 不变。解析失败或本机原生库不可用时保留旧结果，不让单文件失败清空曲库。

“专辑已人工核验”和“每首本地歌曲都已关联”是两个事实，必须独立表示。人工确认的专辑显示已完成并退出自动队列，仍展示真实歌曲覆盖数；不能为显示完成而编造未建立的歌曲关联。专辑内容变化、解除对应时要使确认依据失效或重新进入待处理，避免旧确认遮蔽新文件。

网络错误、超时、风控与鉴权失败不归类为未命中；批次停止但保留剩余项。暂停必须在异常后释放暂停状态；报告解码按条目容错，损坏单条不能吞掉整批历史。自动流程避免覆盖已有优先音源；对话框人工替换是明确动作。播放器经 `core.localmusic.LocalMusicApi` 接入，`core` 不直接依赖本地功能实现。

显式“刷新曲库”先等待本轮匹配暂停，再扫描 MediaStore、同步 Room 并刷新报告；普通状态刷新仍只重读已索引元数据。没有读取权限时需先在本地音乐页授权，未被 MediaStore 收录的文件仍需导入。刷新期间防止启动新批次，异常后恢复入口，不把刷新报告误当作已经扫描新增文件。

新增专辑详情 DTO 只在本地匹配域保留总曲数和完整性证据，复用已有 WEAPI Retrofit。已于 2026-10-15 对照项目 `PlaylistApi` 与 [api-enhanced album.js](https://github.com/NeteaseCloudMusicApiEnhanced/api-enhanced/blob/master/module/album.js)；歌曲/专辑搜索复用原 `SearchRepository`，参数与 [cloudsearch.js](https://github.com/NeteaseCloudMusicApiEnhanced/api-enhanced/blob/master/module/cloudsearch.js) 的关键词、类型、分页字段一致。分页上限或过滤导致证据不完整时，不自动假定唯一候选。

## 可重现导出与验证

任务附件提供 `prepare-upstream-prs.py`。在包含 `work/Melodia` 的任务目录中运行：

```powershell
python .\work\prepare-upstream-prs.py --output-dir .\work\upstream-prs-next
```

脚本从固定官方树构造 `artist-album-filter` 和 `local-audio-matching` 两个独立目录，然后叠加已审核文件和符号块，输出各自 binary patch 与 manifest。它不修改个人 checkout、不联网、不提交到远程、不推 tag。输出目录存在时会停止；选择新的专用目录即可，不递归覆盖旧构建或成果。`upstream/main` 不再等于固定基线时也会停止，必须重新审查再调整。

分别在两个导出目录按官方环境配置 Android SDK，执行：

```powershell
.\gradlew.bat :app:testDebugUnitTest :shared:desktopTest :desktopApp:test :desktopApp:compileKotlin :app:assembleRelease
```

独立目录的编译结果必须单独记录。个人完整分支测试通过不能代替拆分后的构建；本地构建也不能代替后来提交的 GitHub CI。正文草案只勾选实际完成的检查。Release 含 R8，但不等同于完整 `lintRelease` 全绿。

2026-10-15 两个功能的最终独立目录均执行上述全部五个任务并得到 `BUILD SUCCESSFUL`。本地匹配已在 beta.13 目录重新执行；beta.12 历史结果同时保留。由 JUnit XML 汇总如下：

| 独立功能 | Android / shared / desktop 声明测试数 | 实际执行通过 | 跳过 | 失败 / 错误 | 构建耗时 |
|---|---|---:|---:|---|---|
| 歌手专辑筛选（beta.12，源码未变） | 300 / 569 / 248 | 1102 | 15 | 0 / 0 | 3 分 47 秒 |
| 本地歌曲与专辑匹配（beta.12 历史） | 565 / 544 / 248 | 1342 | 15 | 0 / 0 | 5 分 46 秒 |
| 本地歌曲与专辑匹配（beta.13 当前） | 589 / 544 / 248 | 1366 | 15 | 0 / 0 | 7 分 53 秒 |

各次均有 15 个桌面原生播放器测试因环境缺少 `libmpv-2.dll` 按原测试条件跳过，不计入执行通过数。独立 Release APK 仅用于验证构建，保留官方默认应用 ID `com.lin0721.linmusic` 和版本 `1.0.0`，未安装或发布；独立 APK 的界面和真机验收仍留给提交前完成。

本地匹配的初次编译曾在默认 2 GB 堆下触发 Kotlin 编译器内存不足。最终验证只在命令行使用 `--max-workers=2 -Pkotlin.compiler.execution.strategy=in-process "-Dorg.gradle.jvmargs=-Xmx3g -Dfile.encoding=UTF-8"`，没有修改上游构建配置、传入个人版本/更新参数或读取签名秘密；专辑筛选使用 `--max-workers=3`。两者均使用 `--no-daemon --console=plain`。完整日志、各测试模块 XML 摘要与 SHA256、初次失败和最终命令保存在附件 `beta12上游PR拆分检查.json` 与两份 CI 日志中。

附件 `beta12上游PR逐文件清单.json` 是两个最终导出的合并清单，记录各自准确目录、文件和补丁/源码 ZIP 哈希；两份 `beta12-upstream-*-source.zip` 与 `beta12-upstream-*.patch` 是可交付源码。导出时个人源工作树尚未提交，清单以每个实际文件的 SHA256 标识来源，不能仅以 `sourceHead` 代替这些工作树内容。

当前本地匹配交付使用 `beta13-upstream-local-audio-matching-source.zip`、`beta13-upstream-local-audio-matching.patch`、`beta13上游本地匹配逐文件清单.json` 和 `beta13上游PR拆分检查.json`。后者记录最新 112 个功能文件、1381 个声明测试/1366 个实际通过、15 个跳过及 0 个失败/错误，并引用源码未变的歌手筛选原有证据。beta.13 同样使用 3 GB in-process/2 workers；首次受限运行在任何任务前未能连接本机 Gradle daemon，换为已获准的工程运行上下文后以相同命令成功，未改源码或构建配置。该启动失败日志与最终完整日志分别保留，不混写为测试失败或假装初次成功。

未来创建真正的功能分支时，从当时的官方 `upstream/main` 新建分支，先 `git apply --check` 相应补丁，再应用和提交。官方提交已变化而补丁冲突时，按逐文件清单迁移对应块，不整文件覆盖新版官方实现。提交前重新运行 CI、添加独立分支截图，再使用对应正文创建 PR；本地准备目录中的基线提交仅为构建/差异验证，不应作为个人历史整体推送。

手动回归至少覆盖：专辑筛选关闭/全局/单人例外、U2 分页、请求取消后重选；歌曲双向关联、手动优先与失效回退、导入冲突；专辑人工核验→已完成→重启→继续跳过、增加文件后重新核验、解除对应后重新出现；横竖屏和回到原筛选位置。

本地识别还应覆盖：专辑标签与目录同名、无专辑歌手的同目录多演奏者合辑、不同目录/平铺同名专辑不误合并、WAV 的系统占位标签与实际 ID3 标签、旧手动导入 WAV 显式刷新、内嵌封面回退及无法读取标签时原记录保留。新增 24 项回归围绕这些纯分组、元数据合并与旧记录保留规则；原生 TagLib 和真实文件读取需要设备路径验证，不能以 JVM 测试代替。

beta.13 个人集成包在受控模拟器上使用四张真实专辑得到 50 / 6 / 14 / 10 首，验证 WAV 中文标签、封面和专辑匹配入口；这可作为集成开发证据，不能替代上游独立 APK 的验收。旧手动导入 WAV 刷新仅有 3 项纯规则回归，系统选择器 SAF 全流程未完成，不声称已实测。旧歌曲 URI 映射实际有 43 条，旧成功报告有 45 项且含共用音源；报告数与独立关联数须分别计数。本轮未新建关联，旧歌曲/专辑/歌手资料库偏好文件逐字节保留。

## 官方更新后的维护

保留个人分支及其固定应用 ID、签名与单调递增版本码。先 fetch 官方，在临时集成分支合入新 `upstream/main`，逐项解决设置、DI、播放器与歌手分页的冲突，再跑完整 CI 与上述回归。确认后才将集成结果合回个人分支并构建 APK。不要直接用“Sync fork”覆盖承载个人功能的分支。

兼容性依赖实际 API/数据库变化，不能承诺以后每一版零冲突。独立存储、可选模型字段、已有接口和两个功能补丁会缩小维护范围。Fork 的 Releases 不会因官方发布自动生成个人 APK；个人 Release 仍须从合并并验证过的代码构建、使用同一签名后显式发布。现有手动个人构建工作流不会自动同步上游或创建 Release。
