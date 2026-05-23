# 简历项目描述 — 黑马点评 AI Agent 工程化演进

> 以下是单个项目在简历中的完整描述，4-5 条 bullet 覆盖架构、AI、缓存、安全四大方向。
> 每条按 "发现问题 → 方案 → 效果" 的 STAR 原则编写。
> 面试时每条都能展开 3-5 分钟。


## 项目：黑马点评 — 本地生活 AI 智能导购平台

**技术栈**：Spring Boot 2.3 + MyBatis-Plus + Redis + RocketMQ + Milvus + Caffeine + Resilience4j + Nginx + Docker

**项目描述**：主导一个本地生活服务平台从教学 Demo 到 SaaS-ready 的工程化演进，在不动主版本的前提下系统性落地了 34 项改进，覆盖架构、稳定性、AI 能力、安全合规与缓存治理五大领域。

---

**架构重构与稳定性治理**

将秒杀链路中的 Redis Stream 自建消费替换为 RocketMQ 事务消息，利用半消息 + `executeLocalTransaction` + `checkLocalTransaction` 回查机制解决 Redis 预占库存后 MySQL 落库失败导致的库存丢失问题，配合补偿 Lua 脚本实现 Redis/MySQL 双向最终一致。为 LLM 调用构建 Resilience4j 三层保护（15s 超时 → 3 次指数退避重试 → 50% 失败率熔断 30s 自动降级），防止外部 AI 服务抖动拖垮整个系统。引入 Actuator + Micrometer + Prometheus 可观测性体系，覆盖接口 QPS、AI 调用成功率、RocketMQ 消费延迟、JVM GC 等核心指标。

**AI 智能导购助手**

构建意图路由 + RAG 检索增强 + LLM 生成的 Agent 架构：将 994 行单体类拆分为 8 个独立 Handler（每种意图一个），引入 Milvus 向量数据库 + 通义千问 Embedding 实现 FAQ 规则与探店内容的语义检索，设计 RRF（Reciprocal Rank Fusion）融合向量检索和关键词检索结果，消除双路打分量纲不一致问题。店名识别从 if-else 硬编码改为启动时全量加载构建内存索引 + Redis Pub/Sub 热更新，新增店铺无需改代码。

**缓存稳定性治理**

设计并落地 Caffeine L1（30s TTL）+ Redis L2（30min TTL）二级缓存架构，通过 Redis Pub/Sub 广播实现多实例 L1 一致性失效。针对热点 Key 采用逻辑过期 + 互斥锁同步重建策略，配合布隆过滤器与缓存空值解决穿透问题。修复了逻辑过期重建中"锁在当前线程获取、子线程释放"的 Bug，改为同线程 Double-Check 后重建。写侧采用延迟双删（先删缓存 → 更新 DB → 200ms 后再删）覆盖并发读回填旧数据的窗口。三档压测结果：直查 MySQL 6407 QPS → Redis 缓存 12276 QPS → 二级缓存 15442 QPS。

**安全合规与工程质量**

设计三级鉴权体系（匿名/登录用户/管理员），从黑名单 `excludePathPatterns` 改为白名单 `addPathPatterns` 显式声明受保护接口。通过 `TaskDecorator` 实现 ThreadLocal 用户上下文在异步线程池中的自动透传，解决秒杀等异步场景下用户信息丢失问题。对所有 LLM 调用入口过滤控制字符防止 Prompt 注入。引入 `@AuditLog` 注解 + AOP 切面实现关键操作审计（自动记录 userId、IP、耗时）。配置三级 Spring Profile（dev/staging/prod），敏感信息通过 `.env` 注入，消除密钥硬编码。引入 Checkstyle 静态分析、统一 SLF4J 日志规范、traceId 全链路追踪。
