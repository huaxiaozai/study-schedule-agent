# 导入失败恢复与确认校验

用户已选择继续后续开发。本次落实 PR 审查中已明确的三项缺陷，不修改已冻结契约字段。

选择在现有 submit/confirm 入口恢复失败请求：比增加 retry 端点更少影响前端，比要求用户换 requestKey 更容易保持幂等。

- submit 仅对 MODEL_UNAVAILABLE 且没有提案的 FAILED 记录重新识别，保留 createdAt；其他已存在记录仍返回原结果。
- confirm 允许重试 COMMIT_FAILED。首次提交前持久保存规范为 REVIEW 的已确认提案；失败重试只接受同一份提案，避免端口实际写入但回执丢失后改变数据。
- 提交端口必须按 proposalId 幂等，相同 ID 不同内容拒绝；内存适配器落实此规则。真实事务适配器仍由业务模块提供。
- 确认必须使用 REVIEW 提案，ambiguities 和 errors 为空；ASSIGNMENT 必须具备 deadline 和 durationMinutes。courseCandidate 仍可空，不把候选课程名称当正式 courseId。
- 成功恢复必须清除旧错误；失败仍保留稳定错误码。原三个复现测试及新增幂等、错误清理、HTTP 校验测试作为验收。
- 以测试中的任务候选映射验证导入、排程和 Agent 排程的衔接，不提供未经持久化模块验收的正式入库实现。

公开方法签名不变；影响仅限 importing 内部与测试。CodeGraph 工具及 CLI 不可用，已用 rg 检查 ImportCommitPort、withProposal、ImportService 的全部调用点。
