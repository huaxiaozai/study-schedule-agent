# ActionExecutor 联调约定

本文档交付给黄健华，用于把已冻结的 `ActionSpec` 和 `HardConstraintValidator` 接入事务执行器。这里约定执行语义和错误码，不修改四个跨模块契约。

## 执行顺序

1. 在事务外接收并反序列化 `ActionSpec`，只接受 `NEEDS_CONFIRMATION` 的写动作。
2. 进入短事务后按用户和 `authorizationScope` 校验权限。
3. 按 `operationKey` 查询历史执行记录。相同 key 和相同请求返回原结果；相同 key 但请求内容不同返回 `DUPLICATE_OPERATION_MISMATCH`。
4. 锁定用户日程并比较 `baseRevision`。不一致时返回 HTTP 409 和 `REVISION_CONFLICT`，不得写入。
5. 将动作转换为候选变更，并调用同一份 `HardConstraintValidator`。任何违反项都返回 HTTP 422，错误码直接使用 `ConstraintCode` 名称。
6. 保存变更、执行前镜像和执行后镜像，递增 `planningRevision`，将结果记为 `APPLIED` 后提交。

模型调用、导入识别和其他外部 I/O 不得放入该事务。

## 幂等规则

- 幂等键的作用域为“用户 + `operationKey`”。
- 首次成功、业务失败和版本冲突都应保存可重放的确定结果。
- 完全相同的重复请求不得再次写库或再次递增 revision，返回内容应与首次结果一致，并可在传输层标记 `replayed: true`。
- 相同 key 对应不同 `ActionSpec` 时拒绝执行，不得覆盖旧记录。

样例见 `action-idempotent-replay.json`。

## 撤销条件

撤销以已成功执行的 `executionId` 为目标，不扩展 `ActionType`。实现方可以提供 `POST /api/v1/actions/{executionId}/undo`，请求至少包含新的 `operationKey` 和当前 `baseRevision`。

仅在以下条件全部满足时允许撤销：

- 原执行属于当前用户且状态为 `APPLIED`；
- 原执行尚未被撤销；
- 当前 revision 等于请求的 `baseRevision`；
- 目标对象仍与原执行保存的“执行后镜像”一致；
- 根据“执行前镜像”生成的逆变更通过权限和硬约束校验。

撤销也必须在单个事务内完成，成功后递增 revision，并记录新的 execution。重复提交同一撤销 `operationKey` 返回原结果。样例见 `action-undo-flow.json`。

## 稳定错误码

| HTTP | 错误码 | 含义 |
| --- | --- | --- |
| 400 | `INVALID_ACTION` | 动作结构或字段值非法 |
| 403 | `AUTHORIZATION_DENIED` | 当前用户或 scope 无权执行 |
| 404 | `TARGET_NOT_FOUND` | 目标任务或执行记录不存在 |
| 409 | `ACTION_NOT_CONFIRMABLE` | 动作不是可确认的写动作 |
| 409 | `REVISION_CONFLICT` | `baseRevision` 已过期 |
| 409 | `DUPLICATE_OPERATION_MISMATCH` | 幂等键已绑定到另一请求 |
| 409 | `UNDO_NOT_AVAILABLE` | 原执行失败、已撤销或不支持撤销 |
| 409 | `UNDO_CONFLICT` | 目标在原执行后又被修改，无法安全恢复 |
| 422 | `TASK_NOT_FOUND` | 候选块引用了不存在的任务 |
| 422 | `DEADLINE_EXCEEDED` | 安排超过任务截止时间 |
| 422 | `FORBIDDEN_PERIOD` | 安排进入禁止时段 |
| 422 | `LOCKED_BLOCK_OVERLAP` | 与锁定块重叠 |
| 422 | `TIME_OVERLAP` | 与已有安排重叠 |
| 422 | `DAILY_LIMIT_EXCEEDED` | 超过每日学习时长上限 |

版本冲突响应样例见 `action-conflict-response.json`。`ContractError.objectIds` 应包含相关任务、时间块、日程或执行记录 ID。
