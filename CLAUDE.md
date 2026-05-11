# yragent 项目约定与速查

## 项目定位

人机共驾（HITL）智能代理系统。核心理念：AI 负责执行，开发者保留关键控制权，系统在重要节点可授权、可中断、可接管、可追溯。

## 环境

- Java 17（**必须**），路径：`E:/testcqwm/jdk-17.0.14`
- Spring Boot 3.4.4 + Picocli + SQLite + DeepSeek LLM + MCP 协议
- DDD 多模块结构：types / domain / infrastructure / app
- 所有文件操作限定在 `E:\xiangmu` 目录内
- 报告位置：`E:\xiangmu\yragent\报告`

## 常用命令

```bash
export JAVA_HOME="E:/testcqwm/jdk-17.0.14" && export PATH="E:/testcqwm/jdk-17.0.14/bin:$PATH"
export DEEPSEEK_API_KEY="your-api-key"

# 全模块编译
mvn compile

# 测试
mvn test

# 启动 Web 服务
cd yragent-core-app && mvn spring-boot:run -q

# E2E CLI 模式
mvn spring-boot:run -q "-Dspring-boot.run.arguments=run-task --gate-input-file tests/gate-deep.txt 在workspace中创建README.md"
```

## 七阶段流水线 (v2)

```
GOAL_DEFINITION → CLARIFY_GOAL → PLANNING → GATE_CONFIRM(HITL) → EXECUTION → VERIFICATION → REVIEW
                                                                                        ↓
                                                                              未完成 → PLANNING (多轮循环)
```

- **CLARIFY_GOAL**（v2 新增）：置信度低时 LLM 生成 2-4 个具体澄清问题
- **GATE_CONFIRM**：v2 改为 LLM 全权裁决（删除关键词规则层），PASS / NEEDS_INFO / BLOCKED 三态
- **REVIEW**：v2 增强为判断项目完成度，未完成自动进入下一轮（max 10 轮）
- 开发者输入类型：UNDERSTANDING_INPUT / RISK_INPUT / CONFIRMATION / CLARIFICATION（v2 新增）
- 确认码支持 `all`（全部确认）、逗号分隔指定 code、`exit`（拒绝执行）

## 模块结构

```
yragent-core/
├── pom.xml                              ← 父 POM
├── yragent-core-types/                  ← 通用类型（占位）
├── yragent-core-domain/                 ← 领域层 (70 files)
│   └── com.yragent.domain.{gate,goal,mcp,memory,model,planning,policy,
│                            skill,stage,tool,trace,verification,workflow}
├── yragent-core-infrastructure/         ← 基础设施层 (16 files)
│   └── com.yragent.infrastructure.{config,integration.llm,integration.mcp,repository,skill}
└── yragent-core-app/                    ← 应用层 + 触发器 (31 files)
    └── com.yragent.{execution,orchestrator,service,tool,trigger.cli,trigger.http,workflow}
```

## 核心文件 (v2)

| 文件 | 职责 |
|------|------|
| `app/orchestrator/StageOrchestrator.java` | 七阶段编排 + 多轮循环 |
| `app/orchestrator/ClarifyGoalStageHandler.java` | v2 新增：澄清阶段 |
| `app/orchestrator/*StageHandler.java` | 七个阶段的 Handler |
| `domain/stage/TaskExecutionContext.java` | 共享上下文（含 v2 新增字段） |
| `domain/goal/GoalAnalysis.java` | 目标分析（含 confidence/needsClarification） |
| `domain/goal/GoalClarification.java` | v2 新增：澄清交互数据 |
| `domain/planning/PlanDocument.java` | v2 新增：详细计划书 |
| `domain/stage/RoundRecord.java` | v2 新增：多轮记录 |
| `domain/skill/*` | v2 新增：Skill 系统（5 files） |
| `domain/workflow/*` | v2 新增：工作流引擎（6 files） |
| `domain/gate/StageGateEngine.java` | v2 简化：LLM 全权门禁引擎 |
| `domain/gate/step/GateSemanticReviewStep.java` | v2 重写：LLM 全权裁决 |
| `app/execution/LocalToolExecutor.java` | 本地工具+路径沙盒 |
| `app/execution/UnifiedToolExecutor.java` | 本地优先+MCP 回退 |
| `app/workflow/WorkflowEngine.java` | v2 新增：工作流引擎 |
| `infrastructure/skill/YamlSkillLoader.java` | v2 新增：Skill YAML 加载 |
| `infrastructure/integration/llm/deepseek/DeepSeekCompatibleLlmClient.java` | LLM 客户端 |
| `trigger/cli/command/RunTaskCommand.java` | CLI 入口 |
| `trigger/http/TaskController.java` | REST 入口 |

## Skill 系统

- Skill 定义：`resources/agent/skills/{skill-name}/SKILL.md`（YAML frontmatter + Markdown 指令）
- Agent 配置：`resources/agent/yrskill-agent.yml`
- 启动时自动扫描 classpath 下的 Skill 并注册到 SkillRegistry
- 工作流支持：Sequential / Parallel / Loop 三种模式

## 安全机制

- **PolicyEngine**：工具风险分级 READ_ONLY / MUTATING / DANGEROUS
- **LocalToolExecutor.resolvePath()**：拒绝绝对路径和路径穿越
- **门禁 LLM 全权裁决**：删除规则层，LLM 检查开发者理解深度，识别认知偏差

## LLM 容错策略

所有 LLM 调用阶段失败时使用 empty() 默认值，不阻塞流水线。

## 当前状态

- v2 开发完成，七阶段流水线全部 LLM 驱动
- 24 单元测试通过，0 失败
- 4 个 Maven 子模块（DDD 多模块结构）
- 117 个 Java 源文件
- Skill 系统 + 工作流引擎基础设施就绪
- 后端编译通过，前端全中文对话式 UI
- E2E 链路：创建任务 → CLARIFY_GOAL → GATE_CONFIRM → LLM 裁决 全部联通
