<p align="center">
  <img src="https://img.shields.io/badge/Spring%20Boot-2.3.12-brightgreen" alt="Spring Boot"/>
  <img src="https://img.shields.io/badge/Java-8-blue" alt="Java"/>
  <img src="https://img.shields.io/badge/Redis-7-red" alt="Redis"/>
  <img src="https://img.shields.io/badge/Milvus-2.3.3-blueviolet" alt="Milvus"/>
  <img src="https://img.shields.io/badge/Spring%20AI-1.0.0--M2-orange" alt="Spring AI"/>
  <img src="https://img.shields.io/badge/License-MIT-green" alt="License"/>
</p>

# AI-Agent-hm-dianping — AI 智能点评平台

基于 **Spring Boot + Redis + Milvus + Spring AI** 构建的智能本地生活服务平台。在经典黑马点评项目基础上，引入 **AI Agent 架构**，支持用户通过自然语言与平台交互，实现智能问答、个性化推荐、优惠券查询等能力。

## 目录

- [核心功能](#核心功能)
- [技术栈](#技术栈)
- [系统架构](#系统架构)
- [AI Agent 架构](#ai-agent-架构)
- [快速开始](#快速开始)
- [API 概览](#api-概览)
- [部署](#部署)
- [项目结构](#项目结构)

---

## 核心功能

### 🛍️ 商铺服务
- 商铺信息浏览、按类型/名称搜索
- 基于地理坐标的附近商铺推荐
- Redis 缓存加速，缓存穿透/雪崩防护

### 🎫 秒杀优惠券
- 高并发秒杀下单
- Redis + Redisson 分布式锁保证库存安全
- Lua 脚本原子化扣减库存
- Redis Stream 异步订单处理

### 💬 AI 智能助手
- 自然语言对话式交互
- 智能意图识别与路由（8 种业务意图）
- 店铺 FAQ 知识库问答（RAG 检索增强生成）
- 个性化商铺/优惠券推荐
- 结合 Milvus 向量数据库的语义搜索

### 👤 用户体系
- 手机号验证码/密码登录，基于 Redis 的 Session 管理
- 每日签到（Redis BitMap 存储）
- 关注/取关与关注 Feed 流（滚动分页）

### 📝 笔记社交
- 探店笔记发布、点赞（Redis Set 防重复）
- 热榜排序、关注 Feed 流

---

## 技术栈

| 层级 | 技术 | 用途 |
|------|------|------|
| **框架** | Spring Boot 2.3.12 | 应用基础框架 |
| **ORM** | MyBatis-Plus 3.5.3.1 | 数据库访问 |
| **数据库** | MySQL 8.0 | 业务数据存储 |
| **缓存** | Redis 7 + Lettuce + Redisson | 缓存、分布式锁、消息队列 |
| **向量数据库** | Milvus 2.3.3 | 语义搜索与 RAG |
| **AI 框架** | Spring AI Alibaba 1.0.0-M2 | LLM 集成与对话管理 |
| **AI 增强** | LangChain4j 0.35.0 | Agent 与工具调用 |
| **LLM** | DashScope Qwen (通义千问) | 自然语言理解与生成 |
| **嵌入模型** | text-embedding-v3 | 文本向量化 |
| **工具库** | Hutool 5.7.17, Lombok | 工具增强 |
| **部署** | Docker Compose, Nginx | 容器化与反向代理 |

---

## 系统架构

```


## AI Agent 架构

```
用户输入 "帮我推荐附近好吃的川菜"
        │
        ▼
┌──────────────────┐
│  AiChatController │
│  POST /ai/chat    │
└────────┬─────────┘
         ▼
┌──────────────────┐
│  AiChatService   │
│  会话管理 + 消息  │
│  组装             │
└────────┬─────────┘
         ▼
┌──────────────────┐
│  AiIntentService │
│  LLM 意图识别    │
└────────┬─────────┘
         ▼
   ┌─────┴──────┬────────┬────────┬──────┐
   ▼            ▼        ▼        ▼      ▼
┌──────┐ ┌──────────┐ ┌────┐ ┌──────┐ ┌───┐
│RULE_ │ │COUPON_   │ │SHOP│ │RECOM│ │UNK│
│QA    │ │QUERY     │ │... │ │MEND │ │OWN│
└──┬───┘ └────┬─────┘ └─┬──┘ └──┬───┘ └───┘
   │          │         │       │
   ▼          ▼         ▼       ▼
┌──────┐ ┌────────┐ ┌──────┐ ┌──────────┐
│  RAG  │ │Coupon  │ │Shop  │ │ 整合LLM  │
│ 知识库 │ │Tool    │ │Tool  │ │ 生成推荐  │
│ 查询   │ │调用     │ │调用   │ │ 理由      │
└───────┘ └────────┘ └──────┘ └──────────┘
         │
         ▼
┌──────────────────┐
│  AiLlmService    │
│  LLM 生成最终回答 │
│  + 引用来源       │
└──────────────────┘
```

### 支持的业务意图

| 意图 | 说明 | 处理方式 |
|------|------|----------|
| `RULE_QA` | 平台规则/政策问答 | RAG 向量检索 + LLM 回答 |
| `COUPON_QUERY` | 查询可用优惠券 | 工具调用 → 数据库查询 |
| `SHOP_DETAIL` | 获取商铺详情 | 工具调用 → Redis/数据库 |
| `SHOP_SEARCH` | 搜索商铺 | 工具调用 + 地理排序 |
| `SHOP_PROFILE_QA` | 店铺画像问答 | 向量检索 + 属性查询 |
| `VOUCHER_RULE_QA` | 优惠券规则问答 | RAG + 规则引擎 |
| `SHOP_BASE_QA` | 商铺常规问答 | 向量检索 |
| `RECOMMEND` | 个性化推荐 | 协同特征 + LLM 生成 |

---

## 快速开始

### 前置条件

- JDK 17+
- Maven 3.6+
- Docker & Docker Compose

### 1. 启动依赖服务

```bash
docker compose up -d
```

启动 MySQL 8.0、Redis 7、Milvus 2.3.3 等依赖服务。

### 2. 初始化数据库

```bash
docker exec -i mysql mysql -uroot -p123456 hmdp < src/main/resources/db/hmdp.sql
```

### 3. 配置 API Key

在 `application.yaml` 中设置 DashScope API Key：

```yaml
spring:
  ai:
    openai:
      api-key: ${DASHSCOPE_API_KEY:your-api-key}
```

### 4. 启动应用

```bash
mvn package -DskipTests
java -jar target/AI-Agent-hm-dianping-0.0.1-SNAPSHOT.jar
```

访问 `http://localhost:8081`。

---

## API 概览

| 路径 | 方法 | 说明 |
|------|------|------|
| `/ai/chat` | POST | AI 智能对话 |
| `/shop/{id}` | GET | 获取商铺详情 |
| `/shop/of/type` | GET | 按类型浏览商铺 |
| `/shop/of/name` | GET | 按名称搜索商铺 |
| `/user/login` | POST | 用户登录 |
| `/user/sign` | POST | 每日签到 |
| `/voucher-order/seckill/{id}` | POST | 秒杀优惠券 |
| `/blog/hot` | GET | 热门笔记 |
| `/blog/of/follow` | GET | 关注 Feed 流 |

---

## 部署

项目支持 Docker Compose 一键部署到阿里云 ECS，详见 [阿里云部署报告](阿里云部署报告.md)。

### 部署架构

```
Nginx (:80) → Spring Boot (:8081) → MySQL + Redis + Milvus (Docker)
```

### 生产环境配置

复制 `application-prod.yml`（已列入 .gitignore）并填入生产环境参数：

```bash
cp application-prod.yml application-prod.yml.bak
# 编辑 application-prod.yml 中的数据库密码、API Key 等
java -jar app.jar --spring.profiles.active=prod
```

---

## 项目结构

```
src/main/java/com/hmdp/
├── config/          # 全局配置（Redis、Milvus、AI、线程池等）
├── controller/      # REST API 控制器
├── dto/             # 数据传输对象
├── entity/          # 数据库实体
├── enums/           # 枚举（意图类型、知识类型等）
├── mapper/          # MyBatis-Plus 数据访问
├── memory/          # AI 会话与用户偏好记忆管理
├── prompt/          # AI Prompt 模板
├── rag/             # RAG 检索增强生成（向量搜索、混合检索）
├── service/         # 业务逻辑层
│   ├── impl/        # 服务实现
│   └── ...          # 服务接口
├── threadpool/      # 自定义线程池与拒绝策略
├── tool/            # AI Agent 工具（商铺、优惠券、订单等）
└── utils/           # 工具类（缓存、锁、登录拦截器等）
```

---

## License

[MIT](LICENSE)
