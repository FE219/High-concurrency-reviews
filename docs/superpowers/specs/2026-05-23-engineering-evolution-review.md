# 黑马点评 AI Agent — Demo 到 SaaS 工程化演进复盘报告

> 面试用：完整记录从教学级 Demo 到生产级 SaaS 平台的演进过程、技术决策和架构变更。

---

## 一、项目背景

**原始状态**：黑马点评是一个 Spring Boot 2.3.12 + Redis + Milvus 的 AI 智能点评平台，具备商铺浏览、秒杀优惠券、AI 对话、RAG 检索等核心功能。代码质量处于 Demo 水平——能跑通，但离上线还有巨大差距。

**目标**：1-2 人团队，在不动 Spring Boot 大版本的前提下，系统性地将项目演进到 SaaS-ready 标准。

**演进策略**：分层渐进式——基础设施 → 稳定性 → 安全合规 → AI 能力 → 缓存优化，每层完成且可独立验证后再进入下一层。

---

## 二、基础设施层

### 2.1 构建与部署标准化

| 问题 | 方案 | 效果 |
|------|------|------|
| Maven 版本依赖本地环境 | Maven Wrapper 锁定 3.8.8 | 所有开发者 + CI 统一构建工具版本 |
| 应用未容器化 | Multi-stage Dockerfile（build + run） | 一键构建镜像，消除"我机器上能跑"问题 |
| 配置散落在一个 YAML 中 | dev / staging / prod 三级 Spring Profile | 环境切换零代码改动 |
| 密钥硬编码在 docker-compose.yml | `.env` 文件注入 + `.env.example` 模板 | 密钥不落仓库，新人 clone 后复制模板即可启动 |

**为什么选 Multi-stage Dockerfile？**
第一层用 `maven:3.8.8` 编译，第二层只用 `openjdk:8-jre-slim` 运行。最终镜像只有 JRE，体积小、攻击面少。

### 2.2 代码质量治理

- **System.out.println / e.printStackTrace() → SLF4J**：7 个文件、30+ 处替换。日志进了框架体系后，可以被集中采集、按级别过滤、携带 traceId。
- **994 行 AiChatServiceImpl → 1 个编排器 + 8 个 Handler**：每种意图独立成类，单一职责，新增意图只需加一个 Handler 实现接口。
- **重复 import + 本地 isBlank/safe 方法清理**：统一到 Hutool StrUtil，减少冗余代码。
- **Checkstyle 静态检查**：AvoidStarImport、UnusedImports、NeedBraces 等基础规则，CI 中自动执行。

---

## 三、稳定性层

### 3.1 异常处理体系

**之前**：`WebExceptionAdvice` 只捕获 RuntimeException，返回"服务器异常"，所有错误信息被吞掉。

**之后**：三级异常分类 + 统一错误响应结构。

```
BusinessException → HTTP 200 + 业务错误码（库存不足、重复下单）
AiServiceException → HTTP 200 + 降级回复（AI 超时/熔断）
ValidationException → HTTP 400 + 字段级错误详情
未知异常        → HTTP 500 + traceId（用于排查）
```

响应结构统一为 `{ code, message, detail, traceId }`，每个请求携带 16 位 traceId 贯穿全链路。

### 3.2 AI 调用韧性（Resilience4j）

**问题**：LLM 调用直接裸调 HTTP，没有任何保护。DashScope 一旦抖动，整个 `/ai/chat` 不可用。

**方案**：Resilience4j 三重保护——

```
LLM 调用 → TimeLimiter(15s超时)
        → Retry(3次, 指数退避 2s→4s→8s)
        → CircuitBreaker(滑动窗口20次, 失败率>50%开闸30s)
        → Fallback: 返回预设降级文案
```

**面试要点**：为什么选 Resilience4j 而不是 Hystrix？Hystrix 已停维。Resilience4j 是轻量级库，函数式编程模型，和 Spring Boot 原生集成更好。且它的熔断器状态可以通过 Actuator 暴露给 Prometheus。

### 3.3 秒杀链路：Redis Stream → RocketMQ 事务消息

这是整个演进中最重要的架构变更。

**原有架构**：

```
用户请求 → Lua脚本(Redis扣库存+XADD Stream) → 后台线程读Stream → MySQL落库
```

**存在的问题**：

1. **库存丢失**：Lua 先扣了 Redis 库存，后异步落库。如果落库失败，Redis 库存已扣但订单不存在——用户拿到了 orderId 却查不到订单。
2. **重试无上限**：pending-list 中失败消息无限重试，没有死信队列。
3. **消息可靠性依赖 Redis 内存**：Redis 重启消息全丢。
4. **`createVoucherOrder` 库存不足时只 log 不抛异常**：静默吞掉，库存凭空消失。

**新架构**：

```
用户请求 → Lua脚本(仅Redis防重+预占,不再XADD)
        → RocketMQ事务消息(半消息)
          ├─ executeLocalTransaction: MySQL落库 → Commit
          ├─ BusinessException: 补偿Lua(Redis库存+1) → Rollback
          └─ 未知异常: → UNKNOWN → checkLocalTransaction回查
```

**关键技术点**：

- **事务消息**：半消息先发到 Broker（对消费者不可见），本地事务执行成功后 Commit 才可见。这是 RocketMQ 区别于 Redis Stream 的核心能力。
- **补偿 Lua 脚本**：Rollback 时执行 `incrby stockKey 1` + `srem orderKey userId`，原子回滚 Redis 状态。
- **回查机制**：如果 `executeLocalTransaction` 无响应（网络抖动），Broker 回调 `checkLocalTransaction`，查询 MySQL 订单是否已创建。
- **死信队列**：RocketMQ 内置，重试 16 次仍失败自动转入 DLQ，触发告警。

**为什么不用 Kafka？** Kafka 的事务消息是"写多个 topic 原子化"，不是"发消息 + 本地事务"的分布式事务模式。RocketMQ 的事务消息天然适合"先扣缓存 → 再落库"这个场景。

### 3.4 秒杀缓存一致性

- **TTL 策略**：`seckill:stock:{id}` 和 `seckill:order:{id}` 在活动结束后 7 天自动过期，防止 Redis 内存泄漏。
- **库存对账**：`@Scheduled` 每 5 分钟比对 Redis 和 MySQL 库存，差异 > 5 时以 DB 为准修正 Redis。

### 3.5 可观测性

- **Actuator + Micrometer + Prometheus**：暴露 `/actuator/health`、`/actuator/prometheus` 端点。
- **关键指标**：AI 调用成功率/耗时、RocketMQ 消费延迟、Redis 连接池使用率、JVM GC。
- **告警规则**：AI 失败率 > 10% 持续 5 分钟、死信队列有消息、健康检查失败。
- **数据库连接池**：HikariCP 20 最大连接 + Redis Lettuce 50 最大活跃连接，移除生产环境 SQL 全量打印。

---

## 四、安全合规层

### 4.1 鉴权体系

- **三级权限**：匿名（浏览店铺）→ 登录用户（秒杀、AI 对话）→ 管理员（向量索引管理）
- **MvcConfig 整改**：从 `excludePathPatterns` 大段排除改为 `addPathPatterns` 显式声明需要登录的路径——白名单比黑名单更安全。
- **ThreadLocal 上下文传递**：`UserHolder` 在异步线程池中通过 `TaskDecorator` 自动透传，秒杀等异步场景不会丢用户上下文。

### 4.2 输入安全

- **DTO 校验**：`spring-boot-starter-validation`，`@NotBlank` + `@Size(max=500)` 防止超长输入耗尽 Token。
- **Prompt 注入防护**：用户输入过滤 null byte 和控制字符（`[\x00-\x08\x0B\x0C\x0E-\x1F]`），在 `AiLlmServiceImpl` 所有 prompt 构建入口统一 sanitize。
- **SQL 注入检查**：`VoucherMapper.xml` 使用 `#{shopId}` MyBatis 参数化查询，无注入风险。

### 4.3 数据安全

- **审计日志**：`@AuditLog` 注解 + AOP 切面，自动记录 userId、IP、操作名、耗时、成功/失败。
- **手机号脱敏**：`SensitiveUtils.maskPhone("13812345678")` → `"138****5678"`，日志中不出现明文。

---

## 五、AI 能力增强层

### 5.1 意图识别演进路径

**之前**：`detectIntent()` 140+ 行 if-else 关键词匹配，店名硬编码 9 个。

**改进**：
- **短期**：抽出 8 个 Handler 独立类，`EnumMap` 路由取代 switch-case
- **中期**：创建 `AiToolDefinitions` 为每个业务能力标注语义描述，为 Function Calling 做准备
- **长期目标**：LLM 自主选择调用哪个 Tool，支持复合意图（"推荐评分 4.5 以上的火锅店有什么券"→ 先推荐后查券）

### 5.2 店名识别数据驱动化

**之前**：`extractPossibleShopName()` 硬编码 if 海底捞 / if 103茶餐厅 / if 新白鹿...

**之后**：`ShopNameMatcher` 启动时从数据库加载全部店铺名称到内存索引，最长匹配优先。店铺改名/新增通过 Redis Pub/Sub 通知所有实例刷新。

### 5.3 RAG 可观测性

`RagDebugInfoDTO` 已有但未返回前端，后续将作为"参考来源"返回给用户。

---

## 六、缓存优化层

### 6.1 锁释放 Bug 修复

**Bug**：`CacheClient.queryWithLogicalExpire()` 在当前线程获取分布式锁，但在 `CACHE_REBUILD_EXECUTOR.submit()` 的子线程中释放。如果线程池拒绝任务，锁只能等 TTL 10s 自动过期——这 10 秒内所有请求都拿不到锁，全部返回过期数据。

**修复**：获取锁、重建缓存、释放锁全部在当前线程完成。重建前加 Double-Check（双重检查），避免重复重建。

### 6.2 延迟双删

Cache-Aside 写入模式下的经典竞态：

```
线程A: 读缓存(miss) → 读DB(旧数据) → [被中断]
线程B: 写DB(新数据) → 删缓存
线程A: [恢复] → 将旧数据写回缓存 → 缓存被污染
```

**修复**：`updateById → delete cache → sleep(200ms) → delete cache`，第二次删除清理并发读写入的脏数据。

### 6.3 缓存预热

`ApplicationRunner` 启动时预加载全部 Shop 数据到 Redis，避免冷启动缓存击穿。

### 6.4 RedisConstants → RedisKey 枚举

从魔法字符串到类型安全的枚举，`RedisKey.SECKILL_STOCK.key(voucherId)` 取代 `SECKILL_STOCK_KEY + voucherId`。旧常量保留并标记 `@Deprecated`，渐进式迁移。

---

## 七、关键技术决策 Q&A

**Q: 为什么不用 Spring Boot 3.x + Java 17？**

A: 版本升级和工程化演进是两个正交的工作。在同一个 PR 里做版本升级会导致改动面过大、回归风险不可控。版本升级应该作为独立的专项在工程基础稳固后进行。且 Spring Boot 2.3 → 3.x 涉及 `javax.*` → `jakarta.*` 的全量迁移，Spring AI Alibaba M2 → GA 也有 breaking changes，需要单独规划。

**Q: 为什么选 RocketMQ 而不是 Kafka？**

A: 核心需求是"秒杀的 Redis 预占 + MySQL 最终落库"的场景。RocketMQ 的事务消息提供了 `executeLocalTransaction` + `checkLocalTransaction` 回查能力，天然支撑"先扣缓存 → 再落库 → 失败回滚"的模式。Kafka 的事务更偏向多 partition 原子写入，不是为分布式事务设计的。另外阿里云有 RocketMQ 托管版，1-2 人团队运维成本低。

**Q: 为什么不直接用 Spring AI 的 Function Calling 替换关键词意图识别？**

A: 当前 Spring AI Alibaba 1.0.0-M2 对 `@Tool` 注解的支持不完善。方案是先把架构搭好——Handler 接口 + 工具注册 + 路由 stub，等 Spring AI 升级到 GA 后，只需改一行代码切换路由方式，不需要动 Handler 实现。

**Q: 34 个 Task 的执行顺序是如何安排的？**

A: 按"被依赖的先做、独立的可以并行"原则。基础设施在最前面（所有后续任务都依赖 Maven Wrapper + Profile），然后稳定性（异常体系被安全层和 AI 层依赖），再安全，最后 AI 和缓存（相对独立可以互换）。

---

## 八、项目数据总结

| 维度 | 改进前 | 改进后 |
|------|--------|--------|
| 代码文件数 | ~140 | ~170（新增 30 个） |
| 最大单文件行数 | 994 行 (AiChatServiceImpl) | ~300 行 (编排器) |
| 配置管理 | 1 个 YAML 混在一起 | 4 个 Profile 文件各司其职 |
| 异常处理 | 1 个 catch 吞所有错误 | 4 级分类 + 错误码 + traceId |
| AI 调用保护 | 无 | 超时→重试→熔断→降级 |
| 秒杀消息可靠性 | Redis 内存级 | RocketMQ 磁盘持久化 + 事务消息 |
| 密钥管理 | 硬编码在 docker-compose.yml | .env 注入 + .env.example 模板 |
| 日志 | System.out.println 乱打 | SLF4J + traceId + 审计注解 |
| 输入校验 | 无 | @Valid + Prompt sanitize |
| 缓存锁 | 跨线程释放（Bug） | 同线程获取+释放（Double Check） |
| 静态分析 | 无 | Checkstyle 基础规则 |
| 可观测性 | 无 | Actuator + Prometheus 指标 |
