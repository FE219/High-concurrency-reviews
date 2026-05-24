# AI-Agent-hm-dianping GitHub 上传报告

> 生成日期：2026-05-21

---

## 一、基本信息

| 项目 | 详情 |
|------|------|
| **项目名称** | AI-Agent-hm-dianping（AI 智能点评平台） |
| **GitHub 仓库** | https://github.com/FE219/AI-Agent-hm-dianping |
| **仓库拥有者** | FE219 |
| **分支** | master |
| **当前状态** | 已同步到远程仓库（up to date） |

---

## 二、提交历史

共计 **5 次提交**，时间线如下：

| # | 提交哈希 | 时间 | 作者 | 说明 |
|---|----------|------|------|------|
| 1 | `bdb532d` | 2026-05-21 10:22 | dell | Initial commit: 黑马点评 AI Agent 项目 |
| 2 | `cea4fbc` | 2026-05-21 10:33 | dell | Add comprehensive README with architecture, features, and quick start guide |
| 3 | `5755b20` | 2026-05-21 10:40 | FE219 | Update README.md |
| 4 | `cebdcaf` | 2026-05-21 10:46 | FE219 | Update README.md |
| 5 | `db0741f` | 2026-05-21 10:49 | dell | Replace ASCII architecture diagrams with images |

### 时间线分析

- 从首次提交到最后一次提交，整个过程耗时约 **26 分钟**
- 提交节奏紧凑，在 2026-05-21 上午完成全部上传工作
- 参与者：dell（主要贡献者，4 次提交）和 FE219（1 次提交）

---

## 三、首次提交内容（`bdb532d`）

初始提交包含完整的项目代码库，总计 **190 个文件，12,431 行代码**。

### 项目结构概览

```
AI-Agent-hm-dianping/
├── .gitignore                    # Git 忽略规则
├── docker-compose.yml            # Docker 容器编排（Redis, Milvus, MySQL）
├── hmdp-nginx.conf               # Nginx 反向代理配置
├── pom.xml                       # Maven 项目配置（Spring Boot 2.3.12）
├── start.sh                      # 启动脚本
├── README.md                     # 项目说明文档
└── src/
    ├── main/java/com/hmdp/
    │   ├── HmDianPingApplication.java     # 应用入口
    │   ├── config/                        # 配置类（11 个）
    │   │   ├── AiAgentConfig.java         # AI Agent 配置
    │   │   ├── AiModelConfig.java         # AI 模型配置
    │   │   ├── DashScopeProperties.java   # 阿里云 DashScope 配置
    │   │   ├── HttpClientConfig.java      # HTTP 客户端配置
    │   │   ├── MilvusConfig.java          # Milvus 向量数据库配置
    │   │   ├── MilvusProperties.java      # Milvus 属性配置
    │   │   ├── MvcConfig.java             # MVC 拦截器配置
    │   │   ├── MybatisConfig.java         # MyBatis-Plus 配置
    │   │   ├── RedissonConfig.java        # Redisson 分布式锁配置
    │   │   ├── ThreadPoolConfig.java      # 自定义线程池配置
    │   │   ├── VectorStoreConfig.java     # 向量存储配置
    │   │   └── WebExceptionAdvice.java    # 全局异常处理
    │   ├── constant/                      # 常量定义
    │   ├── controller/                    # 控制器（11 个）
    │   │   ├── AiChatController.java      # AI 对话接口
    │   │   ├── BlogController.java        # 笔记接口
    │   │   ├── FollowController.java      # 关注接口
    │   │   ├── ShopController.java        # 商铺接口
    │   │   ├── ShopTypeController.java    # 商铺类型接口
    │   │   ├── UploadController.java      # 文件上传接口
    │   │   ├── UserController.java        # 用户接口
    │   │   ├── VoucherController.java     # 优惠券接口
    │   │   └── VoucherOrderController.java # 秒杀订单接口
    │   ├── dto/                           # 数据传输对象（20+ 个）
    │   ├── entity/                        # 数据实体（10 个）
    │   ├── enums/                         # 枚举类
    │   ├── mapper/                        # MyBatis Mapper（12 个）
    │   ├── memory/                        # 记忆管理
    │   │   ├── SessionMemoryManager.java  # 会话记忆管理
    │   │   └── UserPreferenceMemoryManager.java # 用户偏好记忆
    │   ├── prompt/                        # AI Prompt 模板
    │   ├── rag/                           # RAG 检索增强生成模块
    │   │   ├── dto/                       # RAG 数据传输对象
    │   │   ├── enums/                     # RAG 枚举
    │   │   ├── service/                   # RAG 服务接口（15 个）
    │   │   └── service/impl/              # RAG 服务实现（10 个）
    │   ├── service/                       # 业务服务层
    │   │   ├── AiChatService.java         # AI 对话核心服务
    │   │   ├── AiIntentService.java       # AI 意图识别服务
    │   │   ├── AiLlmService.java          # LLM 调用服务
    │   │   ├── AiMemoryService.java       # AI 记忆服务
    │   │   ├── AiRecommendService.java    # AI 推荐服务
    │   │   ├── AiRuleQaService.java       # AI 规则问答服务
    │   │   ├── ShopProfileService.java    # 商铺画像服务
    │   │   ├── UserProfileService.java    # 用户画像服务
    │   │   └── impl/                      # 服务实现（14 个）
    │   ├── threadpool/                    # 自定义线程池
    │   │   ├── BizExecutors.java          # 业务执行器
    │   │   ├── NamedThreadFactory.java    # 命名线程工厂
    │   │   ├── monitor/                   # 线程池监控
    │   │   └── reject/                    # 拒绝策略
    │   ├── tool/                          # AI Agent 工具集（8 个）
    │   │   ├── BlogTool.java              # 笔记查询工具
    │   │   ├── CouponTool.java            # 优惠券查询工具
    │   │   ├── OrderTool.java             # 订单查询工具
    │   │   ├── RuleTool.java              # 规则查询工具
    │   │   ├── ShopProfileTool.java       # 商铺画像工具
    │   │   ├── ShopTool.java              # 商铺搜索工具
    │   │   ├── ShopTypeTool.java          # 商铺类型工具
    │   │   └── UserTool.java              # 用户查询工具
    │   └── utils/                         # 工具类（12 个）
    │       ├── CacheClient.java           # 通用缓存客户端
    │       ├── ILock.java                 # 分布式锁接口
    │       ├── LoginInterceptor.java      # 登录拦截器
    │       ├── PasswordEncoder.java       # 密码加密
    │       ├── RedisConstants.java        # Redis 键常量
    │       ├── RedisData.java             # Redis 数据封装
    │       ├── RedisIdWorker.java         # 全局 ID 生成器
    │       ├── RefreshTokenInterceptor.java # Token 刷新拦截器
    │       ├── RegexPatterns.java         # 正则模式
    │       ├── RegexUtils.java            # 正则工具
    │       ├── SimpleRedisLock.java       # Redis 分布式锁实现
    │       ├── SystemConstants.java       # 系统常量
    │       └── UserHolder.java            # 用户上下文持有者
    ├── main/resources/
    │   ├── application.yaml              # 应用配置
    │   ├── db/hmdp.sql                   # 数据库初始化脚本（1284 行）
    │   ├── mapper/VoucherMapper.xml       # MyBatis XML 映射
    │   ├── seckill.lua                   # 秒杀 Lua 脚本
    │   └── unlock.lua                    # 分布式锁释放 Lua 脚本
    └── test/                             # 单元测试
```

### 技术栈汇总

| 层级 | 技术 | 用途 |
|------|------|------|
| 框架 | Spring Boot 2.3.12 | 应用框架 |
| ORM | MyBatis-Plus | 数据访问层 |
| 缓存 | Redis 7 + Redisson | 缓存、分布式锁、Session |
| 向量数据库 | Milvus 2.3.3 | 语义搜索 / RAG |
| AI 框架 | Spring AI 1.0.0-M2 | LLM 集成 |
| AI 模型 | 阿里云 DashScope (通义千问) | 对话、意图识别、推荐 |
| 数据库 | MySQL | 业务数据存储 |
| 容器 | Docker + Docker Compose | 环境部署 |

### 核心功能模块

1. **商铺服务** — 浏览、搜索、地理坐标推荐、Redis 缓存
2. **秒杀优惠券** — 高并发下单、Redis + Lua 原子操作、分布式锁
3. **AI 智能助手** — 自然语言对话、意图识别（8 种意图）、RAG 知识库问答、个性化推荐
4. **用户体系** — 手机号登录、Session 管理、签到打卡、关注/取关
5. **笔记社交** — 探店笔记、点赞、热榜、Feed 流

---

## 四、后续提交内容

### 第 2 次提交（`cea4fbc`）
- 新增 README.md（210 行）
- 补充架构图、功能说明、快速开始指南、API 概览、部署说明

### 第 3-4 次提交（`5755b20`, `cebdcaf`）
- 更新 README.md，优化说明内容

### 第 5 次提交（`db0741f`）
- 添加架构图图片（`pic/architecture-ai-agent.png`、`pic/architecture-system.png`）
- 替换 README 中的 ASCII 架构图为正式图片

---

## 五、远程仓库配置

```
remote  origin  https://github.com/FE219/AI-Agent-hm-dianping.git (fetch)
remote  origin  https://github.com/FE219/AI-Agent-hm-dianping.git (push)
```

- 上传方式：通过 HTTPS 协议推送
- 分支策略：单分支 `master`，本地与远程保持同步

---

## 六、上传操作推测流程

根据提交记录分析，完成 GitHub 上传的步骤大致如下：

1. **初始化本地仓库** — 使用 `git init` 创建本地 Git 仓库
2. **添加文件** — `git add .` 将所有项目文件纳入版本控制
3. **首次提交** — `git commit -m "Initial commit: 黑马点评 AI Agent 项目"`
4. **在 GitHub 上创建远程仓库** — 通过 GitHub Web 界面创建仓库 `FE219/AI-Agent-hm-dianping`
5. **关联远程仓库** — `git remote add origin https://github.com/FE219/AI-Agent-hm-dianping.git`
6. **推送代码** — `git push -u origin master` 将本地代码推送到 GitHub
7. **后续迭代** — 依次添加 README、更新文档、添加架构图，每次提交后执行 `git push` 同步

---

## 七、文件统计

| 指标 | 数值 |
|------|------|
| 总文件数 | 190 |
| 总代码行数 | 12,431 |
| Java 源文件 | ~130 个 |
| 配置文件 | 8 个 |
| SQL 脚本 | 1 个（1284 行） |
| Lua 脚本 | 2 个 |
| Docker 配置 | 2 个 |
| 图片资源 | 2 个（~8MB） |

---

## 八、注意事项

- 远程仓库使用 HTTPS 协议连接，如需免密推送，建议配置 Git 凭据管理器或切换为 SSH
- `target/` 目录下的编译产物不应提交到 Git，建议确认 `.gitignore` 已覆盖
- 当前仅有 `master` 分支，建议后续采用 Git Flow 或 GitHub Flow 分支策略进行协作开发
- 阿里云 DashScope API Key、数据库密码等敏感信息应通过环境变量管理，避免硬编码在配置文件中

---

*报告完毕。*
