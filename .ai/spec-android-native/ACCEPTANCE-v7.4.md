# 验收包 v7.4 — 首页模块化 / 最近播放歌单 / 菜单精简

**级别**：L2 · 分支 `feat/home-modules`（基于 `98fd971`）· versionCode 12 / 0.7.4

## 改动
| 区域 | 内容 |
|---|---|
| 数据 | `Playlist.lastPlayedAt`（默认 0，备份兼容）；`LibraryRepository.markPlaylistPlayed`；Music Library / 歌单详情播放时记录 |
| 首页 | 新增「最近播放的歌单」卡片（2×2 封面拼图、名称、N 首 · 最近播放、点击进歌单）；无记录时显示「我的歌单」；新增「我的收藏」「收听统计」模块；B 站热门只显示前 6 首 + 完整榜单；最近播放单曲去重 |
| 导航 | 抽屉/宽屏导航去掉「搜索」项（首页搜索框仍可进入搜索页，搜索页仍显示底部迷你播放器） |
| 版本 | 0.7.4 / code 12 |

## 验证证据（.ai/evidence/v10/）
- `01-home-modules.png`：首页模块（搜索/最近播放/统计/热门）
- `02-drawer-no-search.png`：抽屉仅 首页/音乐库/我的 + 更多，无「搜索」
- `03-create-playlist-dialog.png`、`04-added-to-playlist.png`、`06-add-dialog.png`：歌单创建与加入
- `05/07-home-recent-playlists*.png`：播放歌单后首页出现「最近播放的歌单 / TestList」
- 真机 dump：`最近播放的歌单`、`音乐库` 跳转、`TestList` 卡片、热门 6 首
- 全量门禁：`assembleDebug + assembleRelease + app/data/playback/lyrics 单测` exit 0

## 发布
- 版本 0.7.4 / code 12；tag `v0.7.4` → CI Release（固定签名，可直接覆盖升级）。
