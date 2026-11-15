# PLAN v7 — 播放页三页横滑（封面 / 歌词 / 评论）

对应 SPEC：`.ai/spec-android-native/SPEC-v7.md` · 级别 L2 · BASE `0d502f8`

## 写范围

| 文件 | 动作 |
|---|---|
| `app/src/main/java/com/bbplayer/android/ui/screens/player/PlayerScreen.kt` | 主改：`HorizontalPager` 三页、顶部页码点、歌词页纯展示、评论页嵌入、「歌词」按钮/「查看评论」菜单跳页、移除逐曲偏移状态 |
| `app/src/main/java/com/bbplayer/android/ui/screens/comments/CommentsScreen.kt` | 加 `embedded` 参数：嵌入模式去掉返回栏、加「评论 / 共 N 条」内嵌标题、底部 padding 调整 |
| `app/src/main/java/com/bbplayer/android/ui/AppRoot.kt` | 移除已不用的 `onOpenComments` 透传（评论改在播放页内） |
| `.ai/spec-android-native/SPEC-v7.md` | 本任务冻结 SPEC |

不改：数据层/契约/偏好键；设置页歌词偏移（v6 已有，作为唯一调整入口保留）；其它页面。

## 步骤

1. **S1 冻结 SPEC**：`ec63a1a`。
2. **S2 实现**：pager + 三页内容 + 页码点 + 入口跳页 + 移除偏移按钮。
   - 验证：`./gradlew :app:compileDebugKotlin` exit 0（实际通过）。
3. **S3 真机验证**（MI 6 / 1080×1920）：
   - 默认封面页（截图 `02-player-cover.png`）；
   - 左滑 → 歌词页（`03-player-lyrics.png`，dump 无「偏移/0.1s」，有 MiniTransport）；
   - 左滑 → 评论页（`04-player-comments.png`，dump「评论 / 共 35 条」+ 评论内容 + 查看回复）；
   - 右滑反向两次回封面通过；
   - 「歌词」按钮跳页 1（`05-lyrics-via-button.png`）、更多→「查看评论」跳页 2（`06-comments-via-menu.png`）；
   - seek 拖拽不误翻页、位置变化（03:58→09:50，`07-seek-drag-no-page-change.png`）；
   - 评论页垂直滚动正常（`08-comments-scrolled.png`）；
   - 设置→歌词偏移存在且可调（+100ms → 「+100 ms」，`09/10-settings-*.png`）；
   - `adb logcat` FATAL=0。
4. **S4 门禁**：`assembleDebug + app/data/playback/lyrics 单测` → exit 0（455+160=615 用例）。
5. **S5 独立审查**：fresh subagent 审 `0d502f8..ba78efe`，P0/P1 处置。
6. **S6 验收包**：`.ai/spec-android-native/ACCEPTANCE-v7.md`。
7. **S7 合入/发布**：等用户授权（本次未自动合入 main / 未发布）。

## 重分级触发

- 需要改数据层/偏好键/新增依赖 → 停下回 SPEC。
- pager 与播放核心手势冲突且无法在 UI 层解决 → 回 SPEC 重新设计。
