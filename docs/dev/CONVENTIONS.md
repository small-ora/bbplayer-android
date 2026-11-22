# 开发协作约定（BBPlayer）

> 适用于所有后续 AI/人工开发。与 [DEV-PLAN-v0.13-v0.14.md](DEV-PLAN-v0.13-v0.14.md) 配套。

## 1. 角色与发布权

- **发布 owner 唯一**：同一时间只允许一个会话/一个人执行「合并到 main、打 tag、push、创建 Release」。其他会话只产出分支 + 验收包，不得直接改 main 或动 tag。
- 并行开发时：每个任务一个 worktree（建议放在仓库外 `/root/bbplayer-worktrees/<name>`，避免仓库体积膨胀）；任务完成、审查通过后再由 owner 合并。
- 禁止：`push --force` 到 main、删除已发布 tag、把别人的未完成分支合并进自己的分支、在 main 上直接改代码。

## 2. 分支与提交

| 类型 | 命名 | 说明 |
|---|---|---|
| 功能 | `feat/<slug>` | 一个里程碑或一个专项 |
| 修复 | `fix/<version或slug>` | 线上修复走 `fix/vX.Y.Z` |
| 杂务 | `chore/<slug>` | 文档/构建/清理 |
| 发布 | `release/vX.Y.Z` | 只放版本号与 README/RELEASES 更新 |

- commit 格式：`<type>(<scope>): <subject>`，中文 subject 可用；**一主题一 commit**，便于审查与回滚。
- 每个任务至少一条 QUICK 验证（编译），里程碑完成跑 FULL gate（见计划 §2）。

## 3. 每个任务的产物（L2 流程）

```
.ai/<slug>/              # 临时工作区（.gitignore，不提交）
├── recon.md             # 只读勘察：命令、红线、VCS/设备状态
├── SPEC.md              # 冻结规格：目标/AC/非目标/风险
├── PLAN.md              # 分步计划：写范围/验证/回滚点
├── REVIEW.md            # fresh reviewer 的 P0/P1/P2 结论
├── ACCEPTANCE.md        # 验收包：AC 证据/风险/需人验收项
└── evidence/            # 截图/录屏/日志（不提交）
```

- SPEC 实现前单独提交冻结（`chore(spec): ...`），实现期间不得改 AC；AC 变更必须人确认并记录 old→new。
- 可提交的文档：`docs/dev/**`、`docs/reference/**`、`docs/reviews/**`、`docs/RELEASES.md`、`README.md`；其余留在 `.ai/` 不提交。

## 4. 发布流程（owner 执行）

1. 门禁全绿：FULL gate + `assembleRelease`（+ lint，T6 起）。
2. 独立审查 P0/P1 清零；验收包已交人验收。
3. `tools/release.sh X.Y.Z`：校验工作区 clean → 更新 `versionCode`（+1）与 `versionName` → 更新 README 版本段与 `docs/RELEASES.md` → `assembleRelease` 预检 → commit。
4. `git switch main && git merge --ff-only release/vX.Y.Z && git push origin main`。
5. `git tag -a vX.Y.Z -m ... && git push origin vX.Y.Z`；等待 CI（单测 + 签名 APK + GitHub Release）成功。
6. 下载 Release APK → `apksigner verify` → 设备覆盖安装 → 回填 RELEASES.md 的 CI/Release 链接（必要时补提交）。
7. 失败处理：CI 红则修复后重新 tag（删除本地 tag、重打同版本 tag 需人确认）；已发布版本的 bug 走 `fix/vX.Y.(Z+1)` hotfix，不回滚历史 tag。

## 5. 版本号规则

- `versionCode` 单调 +1；`versionName` 用 `0.MINOR.PATCH`：UI 里程碑进 MINOR（如 0.13.0），hotfix 进 PATCH（0.13.1）。
- tag 严格为 `v0.MINOR.PATCH`，与 main 同点。

## 6. 质量红线

- 视觉：只消费 `ui/theme` token（字阶/间距/圆角/色/图标/动效）；禁止新增魔法值；三主题（浅/深/纯白）必须都验证。
- 对比度：正文/次级文字 ≥4.5:1；非文本控件 ≥3:1；播放页沉浸背景必须过 `PlayerBackdrop` 亮度守卫（或等效断言）。
- 触控：可点元素 ≥48dp 热区。
- 无障碍：新可点元素必须带 `Role`/语义/状态；进度类控件必须提供非拖拽操作。
- 性能：列表用 key + `animateItem`；不在组合期做阻塞 IO；不新增全屏 blur（播放页已有方案除外）。
- 依赖：新增依赖需在 SPEC 说明用途、license、体积/R8 影响；禁止统计/崩溃 SDK 与第二套图标库。
- 测试：新逻辑必须有单测；UI 改动必须附真机或 Roborazzi 截图。

## 7. 设备与证据

- 基准设备：MI 6（LineageOS 14 / Android 14 / 1080×1920@480dpi），adb 已可用。
- OEM 面板/系统差异（小米/OPPO/vivo）需用户真机验证；提交证据时注明机型、系统版本、截图/录屏。
- 设备验证脚本：`tools/ui.py`（uiautomator 辅助）、`tools/smoke.sh`（安装 + 逐页截图），见文件头用法。
- 每次视觉改动至少留：三主题截图 + 关键交互录屏（`adb shell screenrecord`）。

## 8. 文档保留与清理

| 保留 | 删除 |
|---|---|
| `README.md`、`docs/dev/**`（计划/规范/模板）、`docs/reference/**`（设计/上游 recon/证据）、`docs/reviews/**`（带日期的评审）、`docs/screenshots/**`（README 用）、`docs/RELEASES.md`、`tools/**` | `.ai/**` 临时工作区、`.agents/**` worktree、构建产物、一次性日志、已被替代的计划文档 |

- 分支：合并后即可删除（tag 保留发布点）；远端过期分支同步删除。
- 清理动作要在一份 commit 中完成，并在 commit message 列出删除范围。

## 9. AI 交接格式

- 新会话开始先读：`docs/dev/DEV-PLAN-*.md` → `docs/dev/CONVENTIONS.md` → 当前任务对应 `.ai/<slug>/SPEC.md`。
- 每完成一个任务：更新 `.ai/<slug>/PLAN.md` 执行记录 + 跑门禁 + 产出证据，再进入下一个任务。
- 遇到：需求歧义/验收标准缺失、需要改数据契约或接口、需要动鉴权/签名/发布、同一错误连续失败 2 次 —— **停下来问人**，不要猜。
