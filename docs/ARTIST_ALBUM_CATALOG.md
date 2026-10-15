# 按音乐资料库筛选歌手主要录音室专辑

beta.7 保留已有本地优先、搜索和曲数筛选功能。MusicBrainz 专辑目录筛选现在默认开启，使用资料库的作品分类，不按热度或评分推荐“最好”的专辑。

## 使用

**设置 → 扩展 → 歌手专辑**统一管理资料库筛选，默认开启。未单独设置的歌手跟随全局开关；只有实际访问歌手时才按需读取资料，不批量抓取所有歌手。

歌手详情 → 三个竖点 → **主要录音室专辑（资料库）**，可单独开启或关闭，或恢复跟随全局。最低曲数入口仍保留，每位歌手独立保存；0 首表示不限制曲数。beta.6 已保存的明确开关与歌手绑定原样保留，全局切换不会覆盖个人例外。

第一次按名称、别名和已有专辑核对身份：只有唯一精确名称候选与至少两张不同专辑交叉命中时自动绑定；否则点选正确的资料库歌手。之后复用绑定，不需要反复选。

加载后只展示网易列表中与主要专辑目录匹配的代表版本，仍可打开原网易专辑。最低曲数设置继续生效；设为 0 即只使用资料库分类。菜单可更换资料库歌手、重试目录或恢复全部列表。

## 分类与版本

以 MusicBrainz Release Group 为原专辑组：`primary-type=Album`，且明确提供空的 `secondary-types`，才进入普通专辑候选。Single、EP、Live、Compilation、Remix 及其他附加类型排除；缺少类型信息不当作录音室专辑。不会因为《Live for Today》的标题含 Live 就排除。

同组的明确重制、地区、豪华、Super Deluxe 等发行后缀可归并，支持连续后缀及嵌套括号。按完整标题、原发行日期等证据选择网易代表专辑，并按资料库原专辑发行时间排列。不同录音、现场及语言版本不靠模糊相似度强行归并。匹配支持大小写、常见标点以及简繁字符折叠；翻译标题或歧义名称可能仍未识别。

网易云明确的 `type=专辑/Album/Single/EP` 可帮助区分同名专辑与单曲、访谈。不会用 `subType=录音室版` 覆盖 MusicBrainz 的 Live、Remix 等分类，因为源数据存在误标。

开启资料库筛选并进入专辑页时，会自动读完网易云所有专辑页，无需不断滚动。读取期间显示已加载数量和进度，暂时可见的匹配继续展示；失败或重复空页会暂停并提供重试。分页依据服务器 `more` 和实际原始行数，不使用可能过时的歌手专辑总数。关闭筛选继续按原列表滚动加载。

页面显示已加载网易专辑的匹配、未识别、合并和排除数量。未识别项不在筛选列表，可以恢复全部查看。

## U2 回归核查

2026-10-15 读取网易云 164 条专辑和 MusicBrainz 203 个发行组。beta.6 漏项原因包括没有读完源分页、《The Joshua Tree》的同名访谈冲突、《The Unforgettable Fire》的同名单曲冲突和 Super Deluxe 后缀。新增完整元数据回放测试，要求 14 张已发行且无次类型的 Album 全部匹配。

《Rattle and Hum》在资料库标为 Soundtrack，官方也说明它混合现场表演与新录音，当前主要录音室规则仍排除。可在该歌手菜单关闭筛选查看原列表。数据库并不保证每位歌手的目录完整或分类正确。

## 数据边界与联网

资料库不保证涵盖每位中外歌手，也不保证类型标注完整无误。例如 MusicBrainz 将 Suchmos《THE ASHTRAY》标为 Album，而 Sony 官方称 mini-album；本功能遵循数据库字段，不能承诺完整权威的录音室目录。

只在开启后按需读取公开资料，串行请求间隔至少 1.1 秒；成功目录缓存 7 天，失败短暂冷却，不后台定时抓取。网络失败保留旧缓存；没有缓存时保留原列表并明确显示错误，不把错误当成“没有专辑”。目录与绑定使用独立存储，不改变官方曲库数据库。

中文比较使用 OpenCC 官方固定版本的 TSCharacters 字表，附带许可与来源说明；这是字符折叠，不是完整短语或地区用语转换。

## 来源

- [MusicBrainz Release Group](https://musicbrainz.org/doc/Release_Group)
- [MusicBrainz 类型定义](https://musicbrainz.org/doc/Release_Group/Type)
- [U2《Rattle and Hum》官方说明](https://www.u2.com/products/rattle-and-hum)
- [MusicBrainz API 限频和 User-Agent](https://musicbrainz.org/doc/MusicBrainz_API/Rate_Limiting)
- [Sony《THE ASHTRAY》发行说明](https://www.sonymusic.co.jp/artist/Suchmos/discography/KSJL-6200)
- [OpenCC TSCharacters 字表](https://github.com/BYVoid/OpenCC/blob/master/data/dictionary/TSCharacters.txt)
