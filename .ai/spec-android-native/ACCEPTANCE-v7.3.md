# 验收包 v7.3 — 评论页融合 / 迷你播放器手势 / 纯白主题 / 风格统一

**级别**：L2 · 分支 `feat/v073-ui-blend-white`（基于 `80c8304` v0.7.2）· versionCode 11 / 0.7.3
**状态**：已实现、真机验证、已合入 main 并发布 v0.7.3（CI success）

## 改动
| 区域 | 内容 |
|---|---|
| 评论页 | 去掉白色卡片；透明底 + 白色文字 + 轻微压暗渐变（纯白播放页用深色文字）；嵌套 SaltTheme 配色，评论卡片/描边/按钮全部半透明白 |
| 迷你播放器 | 去掉最右侧队列按钮与参数；左右滑动切歌（阈值 56dp）；点按整条仍打开播放页 |
| 纯白主题 | `ThemeMode.PURE_WHITE("纯白")`；设置→外观→主题模式可选；全局纯白调色板（白底黑字）；纯白下隐藏动态取色/强调色；播放页徽章/歌词/评论自适应深色 |
| 风格统一 | 设置「凭据安全」改标题+说明行（不再竖排）；`SettingRow` 值单行省略；纯白主题下卡片/描边对比度可读 |
| 版本 | versionCode 11 / 0.7.3 |

## 验证证据
- 评论页（深色播放器）：`.ai/evidence/v9/04-comments-blended.png`（透明融合、白字、可读）
- 评论页（纯白播放器）：`.ai/evidence/v9/12-comments-purewhite.png`
- 迷你播放器：dump 仅「上一首/播放/下一首」；左滑后标题 20世纪… → 循环歌单…，右滑恢复（`05-miniplayer-swiped.png`）
- 纯白主题：设置页 `13-settings-purewhite-fixed.png`、首页 `07-home-purewhite.png`、音乐库 `14-library-purewhite.png`、播放页 `10-player-purewhite-full.png`、歌词页 `11-lyrics-purewhite.png`
- 全量门禁：`assembleDebug + assembleRelease + app/data/playback/lyrics 单测` exit 0（615 tests）

## 发布（已完成）
- 版本 0.7.3 / code 11；tag `v0.7.3` → main `10a6194`；[CI #4379...](https://github.com/small-ora/bbplayer-android/actions/runs/)（v0.7.3 job success）
- Release：https://github.com/small-ora/bbplayer-android/releases/tag/v0.7.3 ，资产 `app-release.apk`（aapt2 校验 versionCode 11 / 0.7.3，固定签名，可直接覆盖升级）
