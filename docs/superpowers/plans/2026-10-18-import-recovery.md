# Import Recovery Implementation Plan

**Goal:** 修复 PR #4 审查复现的确认校验与失败恢复问题，并验证离线导入到排程的交接。

**Architecture:** 保留 ImportService 和 ImportCommitPort 接口，按失败阶段恢复请求；提交提案冻结并由端口提供幂等保障。

**Tech Stack:** Java release 21、Spring Boot 3.5.16、JUnit 5、AssertJ、MockMvc。

## Global Constraints

- 不改四个冻结契约；使用注入 Clock；不接真实模型或正式数据库。
- 在独立分支 codex/import-recovery 上修改，不合并组员 PR。

## Task 1 失败恢复与幂等

- [x] 原三个复现测试已证实失败。
- [x] 修改 ImportService.submit：仅对无提案的 MODEL_UNAVAILABLE 失败重新识别。
- [x] 修改 ImportService.confirm：保存首次确认提案；仅对同提案的 COMMIT_FAILED 允许再次提交。
- [x] 修改 ImportJob.withProposal，成功状态清空旧错误。
- [x] ImportCommitPort 注明 proposalId 幂等要求，内存适配器按 ID 去重并拒绝内容冲突。
- [x] 回归验证临时失败、回执丢失、成功后重复确认、修改重试内容被拒绝。

## Task 2 确认与联调

- [x] 确认入口拒绝未清除歧义/错误、非 REVIEW 状态和 ASSIGNMENT 缺少截止或时长。
- [x] MockMvc 验证无效确认返回 400、不调用提交端口，以及同键识别恢复后可确认。
- [x] 固定 Clock 测试校对提案转 TaskSnapshot 后排程无冲突，并由 Agent 查询上下文；课程 ID 明确由业务层解析。
- [x] 更新 samples/importing/README.md 和 planning.md，运行 mvnw.cmd test，记录实际结果和依赖限制。
