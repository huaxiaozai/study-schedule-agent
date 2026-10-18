# 多模态导入接口样例

初版导入模块使用内存实现，便于在后端没有 MySQL 和真实模型密钥时完成联调。

## 上传截图或录音

```bash
curl -X POST "http://localhost:8080/api/v1/imports" \
  -H "X-Request-Key: request-demo-001" \
  -F "sourceType=SCREENSHOT" \
  -F "file=@course-table.png"
```

语音文件使用 `sourceType=AUDIO`：

```bash
curl -X POST "http://localhost:8080/api/v1/imports" \
  -H "X-Request-Key: request-demo-002" \
  -F "sourceType=AUDIO" \
  -F "file=@homework.m4a"
```

`X-Request-Key` 用于幂等控制。相同请求键重复上传时返回第一次的结果，不会重复执行识别和提交。

## 查询状态

```bash
curl "http://localhost:8080/api/v1/imports/request-demo-001"
```

## 确认导入

将查询返回的 `proposal` 修改后提交：

```bash
curl -X POST "http://localhost:8080/api/v1/imports/request-demo-001/confirm" \
  -H "Content-Type: application/json" \
  -d @samples/importing/import-proposal-confirmed.json
```

当前确认端口使用内存实现。接入业务模块后，应替换为事务型保存，不能保留隐式成功行为。

## 校对与失败恢复约定

- 确认提案的 `status` 必须为 `REVIEW`，`ambiguities` 和 `errors` 必须清空；`ASSIGNMENT` 还必须填写 `deadline`、正数 `durationMinutes`。未满足时返回 HTTP 400 / `INVALID_IMPORT_REQUEST`，不会调用提交端口。
- `MODEL_UNAVAILABLE` 后重新上传相同 `X-Request-Key` 可再次识别；已成功识别或提交的请求继续返回原结果。
- `COMMIT_FAILED` 后查询任务，使用其中保存的已确认 `proposal` 再次调用确认端点。同键重新上传只返回现有失败记录，不会覆盖已确认内容。重试不能修改提案；这避免第一次实际写入但回执丢失时产生内容冲突。
- `ImportCommitPort` 实现必须按 `proposalId` 幂等。相同 ID、相同内容返回原结果；相同 ID、不同内容拒绝。正式数据库适配器需要在业务写入事务内落实此要求。
- 模型或提交恢复成功后，响应中的旧错误清空。

## 可重复验证

在 `backend` 目录运行：

```powershell
.\mvnw.cmd test '-Dtest=ImportReviewRegressionTest,ImportControllerTest,ImportSchedulingFlowTest'
```

覆盖识别重试、确认校验、提交恢复、提交成功但回执丢失的幂等恢复，以及固定样例到任务候选、Agent 排程的离线交接。任务候选映射只在测试中模拟业务模块；`courseCandidate` 是名称候选，不可直接当正式课程 ID。

本阶段仍为内存/Mock 演示，不代表 MySQL 事务写入、撤销或真实模型已验收。
