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
