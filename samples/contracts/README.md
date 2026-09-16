# 跨模块契约样例

当前契约版本为 `schemaVersion: 1`。带时刻的字段统一使用 ISO-8601 UTC 字符串，用户时区使用 IANA Zone ID。

## 正常样例与交接对象

| 文件 | 使用方 | 用途 |
| --- | --- | --- |
| `import-proposal.json` | 赵欢 | 导入识别结果进入人工校对和 Agent 上下文 |
| `schedule-snapshot.json` | 黄健华 | 动作执行前的版本、目标和硬约束校验 |
| `action-spec.json` | 黄健华、樊华钰 | 事务执行输入及前端动作预览/状态映射 |
| `profile-fact.json` | 樊华钰 | 用户事实展示、修改、否认和删除 |
| `action-executor-handoff.md` | 黄健华 | 执行顺序、幂等、撤销条件和稳定错误码 |
| `action-conflict-response.json` | 黄健华、樊华钰 | 过期 `baseRevision` 的 409 响应 |
| `action-idempotent-replay.json` | 黄健华 | 重复 `operationKey` 返回原执行结果 |
| `action-undo-flow.json` | 黄健华、樊华钰 | 基于执行记录的撤销请求和成功回执 |

## 异常样例

| 文件 | 预期结果 |
| --- | --- |
| `invalid/missing-required-field.json` | 拒绝，错误为 `title is required` |
| `invalid/unknown-action-type.json` | 拒绝未知的 `ActionType` 枚举值 |
| `invalid/expired-schema-version.json` | 拒绝版本 `0`，当前仅接受版本 `1` |

## 字段约定

- `ImportProposal`：`courseCandidate`、`deadline` 和 `durationMinutes` 可以为空，用 `ambiguities` 记录需要人工补充的信息；其余顶层字段必填。
- `ScheduleSnapshot`：`now` 是快照生成时刻，`planningRevision` 是动作乐观锁基线；课程固定时间在 `meetingBlocks`，任务已排时间在 `scheduledBlocks`，不可移动时间在 `lockedBlocks`。
- `ActionSpec`：`operationKey` 用于幂等，`baseRevision` 用于版本冲突校验，`changes` 保留结构化 JSON 类型；Agent 只生成该对象，不直接写数据库。
- `ProfileFact`：`value` 保留结构化 JSON 类型，`strength` 范围为 `0.0` 至 `1.0`，`version` 从 `1` 开始递增。
- `ContractError`：错误由稳定的 `code`、可读 `message` 和相关对象 `objectIds` 构成。

在 `backend` 目录执行以下命令验证全部样例：

```powershell
.\mvnw.cmd test '-Dtest=ContractSerializationTest'
```

执行器交接样例可单独验证：

```powershell
.\mvnw.cmd test '-Dtest=ActionExecutorHandoffSamplesTest'
```
