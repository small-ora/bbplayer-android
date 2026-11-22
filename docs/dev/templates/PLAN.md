# PLAN — <任务名>

> SPEC：[SPEC.md](SPEC.md)（冻结于 `<sha>`）；BASE=`<sha>`；分支 `feat/<slug>`
> 交付：本地提交 + 证据 + 验收包；发布由 owner 执行（见 docs/dev/CONVENTIONS.md）

## 1. 步骤
| 步 | 主题 | 写范围 | 验证 | 回滚点 |
|---|---|---|---|---|
| S1 | | | QUICK/AFFECTED | BASE |

## 2. 人工门禁
- FULL gate 红 → 停；审查 P0/P1 必须处置；验收包由人给结论。

## 3. 重分级触发（出现即停，回 SPEC）
- 需要新依赖/改数据契约/改 schema/改发布配置/需求 AC 不再成立。

## 4. 执行记录
| 步 | 状态 | commit | 证据 |
|---|---|---|---|
