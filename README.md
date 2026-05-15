# yr-agent  v2.2

以人为本 AI Agent 框架。LLM 负责分析规划，人类在关键门禁点介入确认，两者在开发过程中相互交融。

**核心思路**：LLM 存在概率性（输出不可靠）和独立性（自主执行无约束）两大缺陷。项目通过阶段门禁机制——规则层 checklist 框架 + LLM 逐项批改——将概率性输出转化为确定性决策，以用户参与克制独立性。

## v2.2 新增：记忆系统

- **上下文组装**：每次 LLM 调用自动注入 `【上下文记忆】` 前缀（偏好策略 + 相关记忆 + 对话历史）
- **对话历史**：滑动窗口 + LLM 摘要压缩，多轮执行时 LLM 能看到之前发生了什么
- **四区记忆分区**：PREFERENCE（偏好）/ EXPERIENCE（经验）/ DECISION（决策）/ ENTITY（实体）
- **FTS5 全文搜索**：SQLite 触发器自动同步，中文分词 + 相关性排序
- **自动捕获**：每阶段 LLM 产出自动保存为记忆，建立"从交互中学习"的反馈环

## 架构

```
yragent-core/
├── yragent-core-types/      # 通用类型（枚举/异常/常量）
├── yragent-core-domain/     # 领域模型 + 门禁引擎 + 记忆系统 + Skill + Workflow (74 files)
├── yragent-core-infra/      # LLM/MCP/SQLite/FTS5/Skill加载实现 (16 files)
└── yragent-core-app/        # 编排层 + 7阶段Handler + CLI/REST (31 files)
```

**七阶段流水线**：目标定义 → 目标澄清 → 规划 → 门禁确认(HITL) → 执行 → 验证 → 审查（支持多轮循环）

**门禁机制**：每个阶段内置 checklist 维度，LLM 生成提问+批改用户回答，三层交叉验证（EvidenceValidator + DimensionChecker + RoundConsistencyChecker）汇总裁决。

## 技术栈

Java 17 · Spring Boot 3.4 · DDD · SQLite · FTS5 · DeepSeek LLM · MCP 协议(JSON-RPC 2.0) · Picocli · JUnit 5

## 快速开始

```bash
export DEEPSEEK_API_KEY=你的密钥

# 启动 Web 服务 (localhost:18080)
cd yragent-core-app && mvn spring-boot:run

# CLI 模式
mvn spring-boot:run -q "-Dspring-boot.run.arguments=run-task 在workspace中创建README.md"

# 测试 (127 tests)
mvn test
```

## 配置

编辑 `yragent-core-app/src/main/resources/application.yml`：

| 配置项 | 环境变量 | 默认值 |
|--------|---------|--------|
| LLM API Key | `DEEPSEEK_API_KEY` | — |
| LLM 地址 | — | `https://api.deepseek.com` |
| 工作区 | `yragent.workspace-root` | `E:/xiangmu` |
| 服务端口 | `server.port` | `18080` |
| 门禁最多追问 | `yragent.gate.max-attempts` | 3 |
| 多轮最多循环 | `yragent.loop.max-rounds` | 10 |
