# 按音乐资料库筛选歌手主要录音室专辑

beta.6 保留 beta.5 的全部功能，新增可选的 MusicBrainz 专辑目录匹配。它使用资料库的作品分类，不按热度或评分推荐“最好”的专辑。

## 使用

歌手详情 → 三个竖点 → **主要录音室专辑（资料库）**。开启仅影响当前网易歌手 ID，开关及资料库歌手绑定各自保存。默认关闭，继续使用原曲数筛选。

第一次按名称、别名和已有专辑核对身份：只有唯一精确名称候选与至少两张不同专辑交叉命中时自动绑定；否则点选正确的资料库歌手。之后复用绑定，不需要反复选。

加载后只展示网易列表中与主要专辑目录匹配的代表版本，仍可打开原网易专辑。最低曲数设置继续生效；设为 0 即只使用资料库分类。菜单可更换资料库歌手、重试目录或恢复全部列表。

## 分类与版本

以 MusicBrainz Release Group 为原专辑组：`primary-type=Album`，且明确提供空的 `secondary-types`，才进入普通专辑候选。Single、EP、Live、Compilation、Remix 及其他附加类型排除；缺少类型信息不当作录音室专辑。不会因为《Live for Today》的标题含 Live 就排除。

同组的明确重制、地区、豪华等发行后缀可归并，按完整标题、原发行日期等证据选择网易代表专辑。不同录音、现场及语言版本不靠模糊相似度强行归并。匹配支持大小写、常见标点以及简繁字符折叠；翻译标题或歧义名称可能仍未识别。

页面显示当前已加载网易专辑的匹配、未识别、合并和排除数量。过滤后当前页为空仍继续分页。未识别项不在筛选列表，可以恢复全部查看。

## 数据边界与联网

资料库不保证涵盖每位中外歌手，也不保证类型标注完整无误。例如 MusicBrainz 将 Suchmos《THE ASHTRAY》标为 Album，而 Sony 官方称 mini-album；本功能遵循数据库字段，不能承诺完整权威的录音室目录。

只在开启后按需读取公开资料，串行请求间隔至少 1.1 秒；成功目录缓存 7 天，失败短暂冷却，不后台定时抓取。网络失败保留旧缓存；没有缓存时保留原列表并明确显示错误，不把错误当成“没有专辑”。目录与绑定使用独立存储，不改变官方曲库数据库。

中文比较使用 OpenCC 官方固定版本的 TSCharacters 字表，附带许可与来源说明；这是字符折叠，不是完整短语或地区用语转换。

## 来源

- [MusicBrainz Release Group](https://musicbrainz.org/doc/Release_Group)
- [MusicBrainz 类型定义](https://musicbrainz.org/doc/Release_Group/Type)
- [MusicBrainz API 限频和 User-Agent](https://musicbrainz.org/doc/MusicBrainz_API/Rate_Limiting)
- [Sony《THE ASHTRAY》发行说明](https://www.sonymusic.co.jp/artist/Suchmos/discography/KSJL-6200)
- [OpenCC TSCharacters 字表](https://github.com/BYVoid/OpenCC/blob/master/data/dictionary/TSCharacters.txt)
