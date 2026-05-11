# yragent-core 1.0

以人为本 AI Agent 框架 — 基于阶段门控的工作流，LLM 负责规划，人类在关键节点审核后再执行。
  当前版本1.0实现了六个阶段,但过于死板.

## 架构

六个流水线阶段：**目标定义 → 规划 → 门禁确认 → 执行 → 验证 → 复盘**

Agent 会在门禁确认阶段暂停，由人类审核通过后才会真正执行工具操作。

```
用户任务 → [目标分析] → [LLM 规划] → [人工门禁] → [沙箱执行] → [结果验证] → [复盘]
```

## 技术栈

- **Java 17** / **Spring Boot 3.4**
- **Maven** 构建
- **SQLite** 持久化
- **DeepSeek**（兼容 OpenAI 协议）或 **DashScope** 作为 LLM
- **MCP**（Model Context Protocol）工具集成
- Web UI + CLI 双界面

## 快速开始

```bash
# 设置 API Key
export DEEPSEEK_API_KEY=你的密钥

# 构建并运行
mvn clean package -DskipTests
java -jar target/yragent-core-0.0.1-SNAPSHOT.jar

# 或使用 CLI 模式
java -jar target/yragent-core-0.0.1-SNAPSHOT.jar run-task --instruction "列出 ./workspace 中的文件"
```

Web 界面：`http://localhost:18080`

## 配置

编辑 `src/main/resources/application.yml` 或使用环境变量：

| 配置项 | 环境变量 | 默认值 |
|--------|---------|--------|
| LLM API Key | `DEEPSEEK_API_KEY` | — |
| LLM 接口地址 | — | `https://api.deepseek.com` |
| 工作区根目录 | — | `./workspace` |
| 服务端口 | `SERVER_PORT` | `18080` |


