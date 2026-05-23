# 黑马点评 AI Agent — Demo to SaaS 工程化演进方案

> 目标：1-2 人团队，Spring Boot + Redis + Milvus + RocketMQ 技术栈，面向 SaaS 用户

## 总览

```
第一层：基础设施 (20-25%) → 第二层：稳定性 (30%) → 第三层：安全合规 (20%) → 第四层：AI 能力 (30%)
```

每层完成后独立验证，不阻塞后续层。版本（Spring Boot 2.3.12 + Java 8）本次不动，后续单独推进。

---

## 第一层：基础设施升级

### 1.1 构建与部署（P0）

**Maven Wrapper**
- 添加 `.mvn/wrapper/maven-wrapper.properties`，锁定 Maven 版本，避免本地环境差异

**Docker 化应用**
- 编写 `Dockerfile`（multi-stage build：Maven 编译 + JRE 运行）
- 将 Spring Boot 容器加入 `docker-compose.yml`，与 MySQL/Redis/Milvus 统一编排
- 环境变量全部通过 `.env` 文件注入（见 3.1）

**`.gitignore` 清理**
- `git rm --cached target/`、`.idea/` 等已提交的构建产物
- 添加 `.env`（不含 `.env.example`）

**配置外部化**
- 建立三级 Profile：`application-dev.yml` / `application-staging.yml` / `application-prod.yml`
- 当前只有 `application.yaml` 和一个半成品的 `application-prod.yml`，需要补齐

### 1.2 CI/CD（P1）

**GitHub Actions 最小流水线**：

```
PR 触发:
  check-out → mvn compile → mvn test → Checkstyle/SpotBugs → 结果评论到 PR

main 合并触发:
  check-out → mvn package -DskipTests → docker build → docker push → SSH deploy
```

- 自动部署先做手工触发（`workflow_dispatch`），稳定后再开自动
- 部署通知到飞书/钉钉群

### 1.3 代码质量基础设施（P0）

| 问题 | 修复 |
|------|------|
| `System.out.println` / `e.printStackTrace()` 散布在核心路径 | 全部替换为 `log.info`/`log.warn`/`log.error` |
| `AiLlmServiceImpl` 重复 import | 删除重复行 |
| `isBlank`/`isNotBlank`/`safe` 本地定义 | 统一使用 Hutool `StrUtil` |
| `AiChatServiceImpl` 994 行 | 将 intent 路由处理拆分为独立 Handler 类 |
| 无静态分析 | 引入 Checkstyle（基础规则），后续加 SpotBugs |

---

## 第二层：稳定性

### 2.1 异常处理体系重建（P0）

**统一错误响应结构**：
```json
{
  "code": "BUSINESS_ERROR",
  "message": "库存不足",
  "detail": "voucherId=123 库存已耗尽",
  "traceId": "a1b2c3d4"
}
```

**三级异常分类**：
- `BusinessException`（业务异常）→ HTTP 200 + 业务错误码 + 中文消息
- `AiServiceException`（AI 调用异常）→ HTTP 200 + 降级回复
- `ValidationException`（参数校验）→ HTTP 400 + 字段级详情
- 未捕获异常 → HTTP 500 + 通用兜底 + 完整堆栈打日志

### 2.2 AI 调用韧性（P0）

引入 **Resilience4j**：

```
LLM 调用 → Timeout(15s) → Retry(3次, 指数退避) → CircuitBreaker(50%失败率开闸30s)
                                                                      ↓
                                                            返回降级回复 / fallback
```

当前 `AiLlmServiceImpl` 每个方法已有 try-catch + null 返回 + 硬编码兜底的模式，需要改为：
- 超时由 Resilience4j TimeLimiter 统一控制（不依赖 HTTP Client 的 socket timeout）
- 熔断状态通过 Actuator 暴露，可被 Prometheus 抓取
- 降级回复集中管理在 `AiFallbackMessages` 常量类

### 2.3 秒杀链路加固 + RocketMQ 改造（P0）

**现状问题**（见 2.3.1-2.3.5）→ **引入 RocketMQ 事务消息**解决。

**新架构**：

```
用户请求
  │
  ▼
Lua 脚本（Redis 防重 + 预占库存 + 返回 orderId）
  │
  ▼
RocketMQ 事务消息（半消息）
  ├─ 半消息 OK → executeLocalTransaction:
  │   扣 MySQL 库存 + 创建订单
  │   ├─ 成功 → Commit → 下游（发通知/同步缓存）
  │   └─ 失败 → Rollback → 补偿 Lua（Redis 库存 +1 + 移除防重标记）
  └─ 无响应 → checkLocalTransaction:
      回查订单是否存在 → Commit/Rollback
```

**原 Redis Stream 消费逻辑全部移除**，由 RocketMQ 原生能力替代：
- 消息持久化 → 磁盘 + 主从同步（不再担心 Redis 挂了丢消息）
- 重试 → 内置指数退避，最大 16 次
- 死信队列 → 重试耗尽自动转入 `%DLQ%`，触发告警
- 消费进度 → Broker 管理 offset

**阿里云 RocketMQ 托管版**，1-2 人团队运维成本低。

### 2.4 秒杀缓存一致性修复（P0）

与 RocketMQ 改造配合，额外处理以下问题：

| 问题 | 修复 |
|------|------|
| Lua 扣了 Redis 库存但 MySQL 订单落库失败时库存丢失 | 事务消息 Rollback → 补偿 Lua 脚本回滚库存 |
| `createVoucherOrder` 中 `!success` 时只 log 不抛异常 | 改为抛 `BusinessException`，触发事务回滚 + 消息重试 |
| `seckill:order:{id}` Set 无 TTL | 秒杀结束后自动设置 TTL = 活动结束时间 + 7 天 |
| 无库存对账机制 | Scheduled Task 每 5 分钟比对 Redis 与 MySQL，差异 > 阈值告警 |

### 2.5 监控与告警（P1）

**技术选型**：Spring Boot Actuator + Micrometer + Prometheus + 阿里云云监控

**核心指标**：
- 接口 QPS / P99 延迟（`http_server_requests_seconds`）
- AI 调用成功率 / 耗时 P95 / 熔断状态
- RocketMQ 消费延迟 / 死信队列消息数
- Redis 连接池使用率 / 命令耗时
- JVM 堆内存 / GC 次数与耗时

**告警规则**：
- AI 调用失败率 > 10% 持续 5 分钟
- 秒杀接口 P99 > 2s
- RocketMQ 死信队列有新消息
- 应用健康检查失败

### 2.6 数据库与连接池（P1）

- Redis Lettuce 连接池 `max-active` 从 10 提升到 50，`max-idle` 从 10 提升到 20
- 新增 MySQL 连接池配置（HikariCP）：`maximum-pool-size=20`，`minimum-idle=5`
- MyBatis-Plus 移除 `StdOutImpl` 日志，生产环境不打印全部 SQL
- 引入慢 SQL 检测：Druid 或 P6Spy，阈值 100ms

---

## 第三层：安全与合规

### 3.1 API Key 与密钥管理（P0）

- `docker-compose.yml` 中所有密码/密钥改为 `${VAR}` 引用，值由 `.env` 文件提供
- `.env.example` 提交（模板），`.env` 不提交
- 生产环境 DashScope API Key 通过阿里云 KMS 或 ECS 实例元数据注入
- DashScope Key 建立轮转机制（每季度更换）

### 3.2 接口鉴权（P0）

- 明确三级权限划分：
  - **匿名**：浏览店铺、查看笔记
  - **登录用户**：秒杀、AI 对话、签到、点赞
  - **管理员**：向量索引管理、规则文档管理
- 统一在 `MvcConfig` 中配置拦截器链，Controller 层不再散落鉴权逻辑
- `UserHolder` ThreadLocal 上下文在异步线程池中需要显式传递（`TaskDecorator` 模式）
- 登录 Token 加固：当前 Redis token 无 HttpOnly，加 Cookie 保护（视前端是否同域部署而定）

### 3.3 输入校验（P0）

- 引入 `spring-boot-starter-validation`
- 所有 Request DTO 加约束：

```java
public class AiChatRequest {
    @NotBlank @Size(max = 500)
    private String message;  // 防止超长输入耗尽 Token

    @Size(max = 36)
    private String sessionId;
    // ...
}
```

- Prompt 注入防护：`AiLlmServiceImpl.buildEvidencePrompt` 中对用户输入过滤控制字符
- 检查 `VoucherMapper.xml` 中是否有 `${}` 拼接（SQL 注入风险）

### 3.4 数据安全（P1）

- 用户手机号在日志中脱敏（`138****1234`）
- 生产环境 Nginx 启用 HTTPS + TLS 1.2+
- 关键操作审计日志：登录、下单、秒杀，记录 userId + IP + 时间戳

---

## 第四层：AI 能力增强

### 4.1 意图识别改为 LLM Function Calling（P0）

**当前**：`detectIntent()` 纯关键词匹配（140+ 行 if-else），店名硬编码。

**改为**：Spring AI ChatClient + Function Calling

```java
// 8 种意图 → 8 个 @Tool 定义的方法
@Tool(name = "searchShops", description = "搜索商铺")
List<ShopSimpleDTO> searchShops(String keyword, Double lat, Double lon);

@Tool(name = "queryCoupons", description = "查询店铺优惠券")
List<VoucherSimpleDTO> queryCoupons(Long shopId);
// ...
```

- LLM 自己选择调用哪个工具，支持复合意图（"推荐评分 4.5 以上的火锅店有什么券"→ 先推荐后查券）
- 未知意图由 LLM 自然语言回复引导，不再需要 `UNKNOWN` 分支
- 成本：每次对话多一次 LLM 调用（Function Calling 的 system prompt），可控

### 4.2 店名识别数据驱动化（P0）

**当前**：`extractPossibleShopName()` 硬编码 9 个店名。

**改为**：
- 应用启动时加载全部店铺名称到内存，构建 Aho-Corasick 自动机（多模式匹配）
- 店铺新增/改名时通过 Redis Pub/Sub 通知所有实例刷新本地 Trie
- 从消息中提取到的候选店名再通过数据库精确匹配确认

### 4.3 对话上下文记忆增强（P1）

- 保存最近 5 轮对话摘要（而非完整历史），减少 Redis 存储压力
- 支持指代消解：`"上一家"` / `"刚才那家店"` / `"换一家"`
- 上下文 TTL 从 30 分钟调整为可配置（默认 2 小时）
- 支持用户主动"结束对话"清除上下文

### 4.4 RAG 质量优化（P1）

| 问题 | 改进 |
|------|------|
| 分块大小无验证 | 评估 `BlogVectorBuildService` / `FaqVectorBuildService` 的分块策略，过长影响检索精度 |
| 单一向量检索 | 加入 BM25 关键词检索 + Cross-Encoder 重排序（Hybrid Search → RRF 融合 → Top-3） |
| 无检索评估 | 手工标注 50 组 question → expected_answer，定期跑回归看 Top-3 召回率 |
| 检索结果不透明 | `RagDebugInfoDTO` 已有定义但未返回前端，改为返回"参考来源"折叠面板 |

### 4.5 成本与质量监控（P1）

- 每次 LLM 调用记录 `prompt_tokens` + `completion_tokens` + `model`，日/周报表
- 模型降级策略：高峰时段非核心场景（店铺详情润色）切到 `qwen-turbo`
- Prompt 模板版本化管理：`AiPromptTemplates` 改为配置项，不同版本可通过开关对比效果

---

## 第五层：旁路缓存改进

### 5.1 修复逻辑过期锁释放 Bug（P0）

**当前**：锁在当前线程获取，在子线程释放。如果线程池拒绝任务，锁只能等 TTL 10s 自动过期。

**修复**：
- 获取锁后，当前线程执行重建，子线程仅用于可选的异步预热
- 获取锁失败的线程直接返回旧数据（保持现有行为）
- 锁释放始终在获取锁的线程中

### 5.2 重建线程池有界化（P0）

**当前**：`Executors.newFixedThreadPool(10)` + 无界队列 → OOM 风险

**修复**：切换到 `ThreadPoolConfig` 体系的 `cacheRebuildExecutor`：
- 核心线程 4，最大线程 10，有界队列 100
- 拒绝策略：`CallerRunsPolicy`（队列满时由调用线程执行，自然降速）

### 5.3 写入时延迟双删（P1）

**当前**：`updateById` → `delete cache`，简单直接，但有并发窗口

**修复**：对于更新频繁的实体（Voucher），使用延迟双删：
```
updateById → delete cache → sleep(200ms) → delete cache
```
对于低频实体（Shop），保持现有模式。

### 5.4 缓存预热（P1）

- 应用启动后 `@PostConstruct` 或 `ApplicationReadyEvent` 触发
- 预加载热点 Shop（根据近期访问量 Top-50）
- 预加载店铺类型列表（`ShopType`，通常不变）

### 5.5 缓存策略扩展（P2）

- 将 `CacheClient` 扩展为支持批量查询、Hash 结构
- Voucher、Blog、UserInfo 逐步纳入缓存体系
- 统一的 Key 规范：`{entity}:{id}:{field}` — 当前 `RedisConstants` 改为枚举

### 5.6 Redis 高可用（P2）

- 生产环境 Redis Sentinel（至少 1 主 2 从 3 Sentinel）
- Jedis/Lettuce 配置 Sentinel 模式连接

---

## 优先级汇总

### P0（上线前必须完成）

| 项 | 所属层 |
|----|--------|
| Docker 化应用 + 配置外部化 | 基础设施 |
| `.gitignore` 清理 + 代码质量问题修复 | 基础设施 |
| 异常处理体系重建 | 稳定性 |
| AI 调用韧性（Resilience4j） | 稳定性 |
| RocketMQ 改造秒杀链路 | 稳定性 |
| 秒杀缓存一致性修复（4 项） | 稳定性 |
| API Key 与密钥管理 | 安全 |
| 接口鉴权分级 | 安全 |
| 输入校验 | 安全 |
| 意图识别改为 Function Calling | AI |
| 店名识别数据驱动化 | AI |
| 逻辑过期锁释放 Bug | 缓存 |
| 重建线程池有界化 | 缓存 |

### P1（上线后一个月内）

| 项 | 所属层 |
|----|--------|
| CI/CD 流水线 | 基础设施 |
| 监控与告警 | 稳定性 |
| 数据库连接池优化 | 稳定性 |
| 数据脱敏 + HTTPS + 审计日志 | 安全 |
| 对话上下文记忆增强 | AI |
| RAG 质量优化 | AI |
| 成本监控 | AI |
| 延迟双删 | 缓存 |
| 缓存预热 | 缓存 |

### P2（持续优化）

| 项 | 所属层 |
|----|--------|
| 缓存策略扩展到全实体 | 缓存 |
| Redis HA | 缓存 |
| A/B 测试框架 | AI |
