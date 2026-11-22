# 仓库清理记录（2026-11-22）

## 分支
- 本地已合并分支全部删除，仅保留 `main`；发布点由 tag 保留（v0.7.0–v0.12.0）。
- 远端过期分支删除：`origin/feat/v2-non-login`、`origin/release/v0.10.0`（均已合并进 main / 已被 tag 覆盖）。
- 删除的本地分支：feat/ui-premium-p0、p1、p2，feat/search-collection-playlist，fix/v0.10.1、v0.10.2，release/v0.10.0、release/v0.6.0，feat/home-modules、feat/player-fixed-controls、feat/player-pager、feat/ui-bottomnav-refresh、feat/ui-sidebar-playerbar、feat/v073-ui-blend-white、feat/v2-non-login、fix/navbar-inset-bg、fix/search-multi-results（共 16 个）。

## Worktree
- 移除 `.agents/worktrees/**`（feat/ui-premium-p1、feat/ui-premium-p2、feat/ui-sidebar-playerbar、search-collection），释放约 1.2G。

## 文档
### 保留（迁入 `docs/`）
- `docs/reference/saltplayer-design.md`（Salt Player 设计基线）
- `docs/reference/upstream-features.md`（上游 BBPlayer 功能 inventory）
- `docs/reference/app-recon.md`（当前 Android 工程勘察）
- `docs/reference/features/search-collection-playlist.md`（合集歌单需求规格）
- `docs/reference/evidence/miui-media-controls-2026-11-22.jpg`（OEM 媒体面板证据）
- `docs/reviews/2026-11-ui-premium-review.md` + `docs/reviews/assets/`（三轮 UI 改造的原始评审与标注图）
- `tools/ui.py`、`tools/smoke.sh`（设备验证脚本）

### 删除
- `.ai/**` 全部临时工作区（ui-bottomnav、ui-polish、ui-premium*、spec-android-native、release-*、evidence、data-dev、ui-sidebar、recon、脚本副本等）
- `docs/ui-premium-review.md`、`docs/ui-review/`（内容已迁入 `docs/reviews/`）
- `docs/roadmap-next.md`（已被 `docs/dev/DEV-PLAN-v0.13-v0.14.md` 取代）
- `.agents/**`（worktree 目录）

## 保留原则
- 发布历史 → `docs/RELEASES.md`（tag 永久保留）
- 规范/模板 → `docs/dev/`
- 设计/上游参考 → `docs/reference/`
- 评审历史 → `docs/reviews/`（带日期）
- 产品截图 → `docs/screenshots/`（README 使用）
