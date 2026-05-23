# Demo-to-SaaS 工程化演进 — 实现计划（P0 阶段）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将黑马点评 AI Agent 项目从 Demo 升级到 SaaS-ready 工程标准，覆盖基础设施、稳定性、安全、AI 能力、缓存五大领域

**Architecture:** 分层渐进式演进，每层完成且可独立验证后再进入下一层。P0 阶段共 40 个 Task，覆盖上线前必须完成的所有改进项

**Tech Stack:** Spring Boot 2.3.12 + Java 8 + MyBatis-Plus + Redis + Milvus + RocketMQ + Resilience4j + Spring AI

---

## 文件结构总览

```
创建的新文件:
  .mvn/wrapper/maven-wrapper.properties          # Maven Wrapper
  .env.example                                    # 环境变量模板
  Dockerfile                                       # 多阶段构建
  docker-compose-app.yml                          # 应用容器编排（追加）
  src/main/resources/application-dev.yml           # 开发环境配置
  src/main/resources/application-staging.yml       # 预发布环境配置
  src/main/resources/checkstyle.xml               # Checkstyle 规则
  src/main/java/com/hmdp/exception/BusinessException.java
  src/main/java/com/hmdp/exception/AiServiceException.java
  src/main/java/com/hmdp/exception/ValidationException.java
  src/main/java/com/hmdp/constant/AiFallbackMessages.java
  src/main/java/com/hmdp/constant/ErrorCode.java
  src/main/java/com/hmdp/handler/AiChatHandler.java          # 接口
  src/main/java/com/hmdp/handler/impl/RecommendHandler.java
  src/main/java/com/hmdp/handler/impl/CouponQueryHandler.java
  src/main/java/com/hmdp/handler/impl/ShopSearchHandler.java
  src/main/java/com/hmdp/handler/impl/ShopDetailHandler.java
  src/main/java/com/hmdp/handler/impl/RuleQaHandler.java
  src/main/java/com/hmdp/handler/impl/ShopProfileQaHandler.java
  src/main/java/com/hmdp/handler/impl/VoucherRuleQaHandler.java
  src/main/java/com/hmdp/handler/impl/ShopBaseQaHandler.java
  src/main/java/com/hmdp/config/RocketMQConfig.java
  src/main/java/com/hmdp/mq/RocketMQTransactionProducer.java
  src/main/java/com/hmdp/mq/RocketMQConsumer.java
  src/main/java/com/hmdp/mq/SeckillOrderTransactionListener.java
  src/main/java/com/hmdp/tool/AiToolDefinitions.java         # Function Calling 工具注册
  src/main/java/com/hmdp/shopmatcher/ShopNameMatcher.java      # Aho-Corasick 匹配器
  src/main/java/com/hmdp/config/Resilience4jConfig.java
  src/main/java/com/hmdp/config/AiModelProperties.java
  src/main/java/com/hmdp/schedule/StockReconciliationTask.java
  src/main/java/com/hmdp/utils/TraceIdFilter.java
  src/main/java/com/hmdp/config/TaskDecoratorConfig.java
  src/main/resources/compensate-seckill.lua                    # 补偿脚本

修改的关键文件:
  pom.xml                                           # 加 RocketMQ / Resilience4j / validation 依赖
  .gitignore                                        # 加 .env
  docker-compose.yml                                # 密码改为 ${VAR}
  src/main/java/com/hmdp/HmDianPingApplication.java
  src/main/java/com/hmdp/config/MvcConfig.java      # 拦截器路径整改
  src/main/java/com/hmdp/config/WebExceptionAdvice.java  # 重写异常处理
  src/main/java/com/hmdp/dto/Result.java            # 加 code + traceId 字段
  src/main/java/com/hmdp/dto/request/AiChatRequest.java  # 加 @NotNull/@Size 校验
  src/main/java/com/hmdp/service/impl/AiChatServiceImpl.java  # 重构为 handler 编排
  src/main/java/com/hmdp/service/impl/AiLlmServiceImpl.java   # Resilience4j 包围 + 清理重复
  src/main/java/com/hmdp/service/impl/VoucherOrderServiceImpl.java  # RocketMQ 替换 Stream
  src/main/java/com/hmdp/utils/CacheClient.java              # 修复锁释放
  src/main/java/com/hmdp/utils/UserHolder.java               # 支持 ThreadLocal 传递
  src/main/resources/application.yaml                        # 加 RocketMQ / Resilience4j 配置
  src/main/resources/seckill.lua                             # 移除 XADD
```

---

## 第一层：基础设施（7 个 Task）

### Task 1: Maven Wrapper

**Files:**
- Create: `.mvn/wrapper/maven-wrapper.properties`
- Create: `.mvn/wrapper/maven-wrapper.jar` (binary via command)
- Modify: `.gitignore`

- [ ] **Step 1: Generate Maven Wrapper**

Run:
```bash
mvn wrapper:wrapper -Dmaven=3.8.8
```
Expected: `.mvn/wrapper/maven-wrapper.properties` and `.mvn/wrapper/maven-wrapper.jar` created. `mvnw` and `mvnw.cmd` scripts created in project root.

- [ ] **Step 2: Verify wrapper works**

Run:
```bash
./mvnw --version
```
Expected: Maven 3.8.8 displayed.

- [ ] **Step 3: Add wrapper jar to .gitignore exception, commit**

Read `.gitignore` first, then add:
```
!.mvn/wrapper/maven-wrapper.jar
```

```bash
git add .mvn/ mvnw mvnw.cmd
git commit -m "build: add Maven Wrapper (3.8.8)"
```

---

### Task 2: .gitignore 清理

**Files:**
- Modify: `.gitignore`
- Run: `git rm --cached` commands

- [ ] **Step 1: Update .gitignore**

Read `.gitignore` first, append:
```
# Env
.env
.env.local
.env.production

# Docker
docker-compose.override.yml
```

- [ ] **Step 2: Remove tracked build artifacts**

```bash
git rm --cached -r target/
git rm --cached -r .idea/ 2>/dev/null
git rm --cached *.iml 2>/dev/null
```

- [ ] **Step 3: Commit**

```bash
git add .gitignore
git commit -m "chore: clean .gitignore and remove tracked build artifacts"
```

---

### Task 3: .env.example + docker-compose 密钥化

**Files:**
- Create: `.env.example`
- Modify: `docker-compose.yml`

- [ ] **Step 1: Create .env.example**

```bash
# MySQL
MYSQL_ROOT_PASSWORD=replace-with-secure-password
MYSQL_DATABASE=hmdp

# Redis
REDIS_PASSWORD=replace-with-secure-password

# MinIO
MINIO_ROOT_USER=minioadmin
MINIO_ROOT_PASSWORD=replace-with-secure-password

# Application
DASHSCOPE_API_KEY=sk-your-dashscope-api-key
SPRING_PROFILES_ACTIVE=dev
```

- [ ] **Step 2: Rewrite docker-compose.yml secrets**

Read `docker-compose.yml`, replace all hardcoded passwords:

```yaml
services:
  mysql:
    environment:
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD}
      MYSQL_DATABASE: ${MYSQL_DATABASE}

  redis:
    command: redis-server --requirepass ${REDIS_PASSWORD}

  minio:
    environment:
      MINIO_ROOT_USER: ${MINIO_ROOT_USER}
      MINIO_ROOT_PASSWORD: ${MINIO_ROOT_PASSWORD}
```

- [ ] **Step 3: Commit**

```bash
git add .env.example docker-compose.yml
git commit -m "security: move secrets from docker-compose.yml to .env"
```

---

### Task 4: Dockerfile（多阶段构建）

**Files:**
- Create: `Dockerfile`

- [ ] **Step 1: Write Dockerfile**

```dockerfile
FROM maven:3.8.8-eclipse-temurin-8 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -DskipTests -B

FROM openjdk:8-jre-slim
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
```

- [ ] **Step 2: Add app service to docker-compose-app.yml**

Create `docker-compose-app.yml` (追加编排，不修改原文件):

```yaml
version: '3.8'
services:
  app:
    build: .
    container_name: hmdp-app
    ports:
      - "8081:8081"
    environment:
      - MYSQL_HOST=mysql
      - REDIS_HOST=redis
      - MILVUS_HOST=milvus
      - DASHSCOPE_API_KEY=${DASHSCOPE_API_KEY}
      - SPRING_PROFILES_ACTIVE=${SPRING_PROFILES_ACTIVE:-dev}
    depends_on:
      - mysql
      - redis
      - milvus
```

- [ ] **Step 3: Verify build**

```bash
docker build -t hmdp-app .
```
Expected: Image built successfully.

- [ ] **Step 4: Commit**

```bash
git add Dockerfile docker-compose-app.yml
git commit -m "build: add Docker multi-stage build and app compose file"
```

---

### Task 5: 配置文件外部化（三级 Profile）

**Files:**
- Create: `src/main/resources/application-dev.yml`
- Create: `src/main/resources/application-staging.yml`
- Modify: `src/main/resources/application.yaml` (精简为公共配置)
- Modify: `application-prod.yml` (已存在，完善)

- [ ] **Step 1: Refactor application.yaml to common config only**

Read `application.yaml`, reduce to shared config:

```yaml
server:
  port: 8081
spring:
  application:
    name: hmdp
  jackson:
    default-property-inclusion: non_null
mybatis-plus:
  type-aliases-package: com.hmdp.entity
  configuration:
    map-underscore-to-camel-case: true
```

- [ ] **Step 2: Create application-dev.yml**

```yaml
spring:
  datasource:
    url: jdbc:mysql://${MYSQL_HOST:localhost}:3306/hmdp?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
    username: ${MYSQL_USER:root}
    password: ${MYSQL_PASSWORD:root}
  redis:
    host: ${REDIS_HOST:localhost}
    port: ${REDIS_PORT:6379}
    password: ${REDIS_PASSWORD:}
    lettuce:
      pool:
        max-active: 10
        max-idle: 10
        min-idle: 1
  ai:
    openai:
      api-key: ${DASHSCOPE_API_KEY:}
      base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
      chat:
        options:
          model: qwen-plus
          temperature: 0.7
    dashscope:
      embedding:
        enabled: true
        api-key: ${DASHSCOPE_API_KEY:}
        options:
          model: text-embedding-v3
    vectorstore:
      milvus:
        client:
          host: ${MILVUS_HOST:localhost}
          port: ${MILVUS_PORT:19530}
          faq-collection: faq_vector
          blog-collection: blog_vector
logging:
  level:
    com.hmdp: debug
mybatis-plus:
  configuration:
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl
```

- [ ] **Step 3: Create application-staging.yml**

Same as dev but with `logging.level.com.hmdp: info` and no `StdOutImpl`.

- [ ] **Step 4: Rewrite application-prod.yml**

Read existing `application-prod.yml`, ensure it has production-grade settings:

```yaml
spring:
  datasource:
    url: ${MYSQL_URL}
    username: ${MYSQL_USER}
    password: ${MYSQL_PASSWORD}
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
  redis:
    host: ${REDIS_HOST}
    port: ${REDIS_PORT:6379}
    password: ${REDIS_PASSWORD}
    lettuce:
      pool:
        max-active: 50
        max-idle: 20
        min-idle: 5
  ai:
    openai:
      api-key: ${DASHSCOPE_API_KEY}
      base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
      chat:
        options:
          model: qwen-plus
          temperature: 0.7
logging:
  level:
    com.hmdp: info
    org.springframework: warn
```

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/application*.yml application-prod.yml
git commit -m "config: add 3-tier Spring profiles (dev/staging/prod)"
```

---

### Task 6: 修复 println / printStackTrace → SLF4J 日志

**Audit files:**
- `src/main/java/com/hmdp/service/impl/AiChatServiceImpl.java`
- `src/main/java/com/hmdp/service/impl/AiLlmServiceImpl.java`
- `src/main/java/com/hmdp/rag/service/impl/*.java` (all rags)
- `src/main/java/com/hmdp/HmDianPingApplication.java` (if any)

- [ ] **Step 1: Fix AiChatServiceImpl**

Read and replace all `System.out.println` calls. Example — line 260, 266:

Old:
```java
System.out.println("[RAG] 未检索到上下文，使用默认回复");
```
New:
```java
log.warn("[RAG] 未检索到上下文，使用默认回复");
```

Old:
```java
e.printStackTrace();
```
New:
```java
log.error("generateRagReply error, question={}", question, e);
```

Do the same for lines 266, 273 (`handleVoucherRuleQa`/`handleShopBaseQa`), 606 (`handleRuleQa`), 822, 829, 839, 843 (`resolveShopId`).

- [ ] **Step 2: Fix AiLlmServiceImpl**

Replace line with `e.printStackTrace()` equivalent:

Old:
```java
catch (Exception e) {
    log.error("AiLlmService chat error", e);
    return null;
}
```
(This one is already correct — verify no `e.printStackTrace()` remains.)

- [ ] **Step 3: Fix RAG service implementations**

Search for `System.out.println` and `e.printStackTrace()` in:
```
src/main/java/com/hmdp/rag/service/impl/
```

Replace all with `log.info/warn/error`.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "fix: replace println/printStackTrace with SLF4J logging"
```

---

### Task 7: AiLlmServiceImpl 清理重复 import + 本地方法

**Files:**
- Modify: `src/main/java/com/hmdp/service/impl/AiLlmServiceImpl.java`

- [ ] **Step 1: Remove duplicate imports**

Read the file. Remove duplicate lines:
```java
import dev.langchain4j.model.chat.ChatLanguageModel;  // appears twice
import org.springframework.beans.factory.annotation.Value;  // appears twice
import org.springframework.stereotype.Service;  // appears twice
import javax.annotation.Resource;  // appears twice
```

- [ ] **Step 2: Replace local isBlank with StrUtil**

Delete the local `isBlank` method and replace call sites:

Old at line 190:
```java
if (isBlank(result)) {
    return fallbackAnswer;
}
```
New:
```java
if (StrUtil.isBlank(result)) {
    return fallbackAnswer;
}
```

Also delete local `safe` method and use `StrUtil.nullToEmpty`:

Old at line 148:
```java
+ safe(userQuestion)
```
New:
```java
+ StrUtil.nullToEmpty(userQuestion)
```

Delete the `private String safe(String str)` and `private boolean isBlank(String str)` methods at lines 200-206.

Add import: `import cn.hutool.core.util.StrUtil;`

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/hmdp/service/impl/AiLlmServiceImpl.java
git commit -m "refactor: clean duplicate imports and local utils in AiLlmServiceImpl"
```

---

### Task 8: 拆分 AiChatServiceImpl 为 Handler 模式

**Files:**
- Create: `src/main/java/com/hmdp/handler/AiChatHandler.java`
- Create: `src/main/java/com/hmdp/handler/impl/RecommendHandler.java`
- Create: `src/main/java/com/hmdp/handler/impl/CouponQueryHandler.java`
- Create: `src/main/java/com/hmdp/handler/impl/ShopSearchHandler.java`
- Create: `src/main/java/com/hmdp/handler/impl/ShopDetailHandler.java`
- Create: `src/main/java/com/hmdp/handler/impl/RuleQaHandler.java`
- Create: `src/main/java/com/hmdp/handler/impl/ShopProfileQaHandler.java`
- Create: `src/main/java/com/hmdp/handler/impl/VoucherRuleQaHandler.java`
- Create: `src/main/java/com/hmdp/handler/impl/ShopBaseQaHandler.java`
- Modify: `src/main/java/com/hmdp/service/impl/AiChatServiceImpl.java`

- [ ] **Step 1: Create AiChatHandler interface**

```java
package com.hmdp.handler;

import com.hmdp.dto.request.AiChatRequest;
import com.hmdp.dto.memory.AiSessionContextDTO;
import com.hmdp.dto.response.AiChatResponse;

public interface AiChatHandler {
    AiChatResponse handle(AiChatRequest request, AiSessionContextDTO context);
}
```

- [ ] **Step 2: Create RecommendHandler**

Move `aiRecommendService.recommend(request, context)` call logic:

```java
package com.hmdp.handler.impl;

import com.hmdp.dto.request.AiChatRequest;
import com.hmdp.dto.memory.AiSessionContextDTO;
import com.hmdp.dto.response.AiChatResponse;
import com.hmdp.handler.AiChatHandler;
import com.hmdp.service.AiRecommendService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RecommendHandler implements AiChatHandler {
    private final AiRecommendService aiRecommendService;

    @Override
    public AiChatResponse handle(AiChatRequest request, AiSessionContextDTO context) {
        return aiRecommendService.recommend(request, context);
    }
}
```

- [ ] **Step 3: Create other handlers**

Same pattern for the remaining 7 handlers, each extracting the corresponding private method from `AiChatServiceImpl`:
- `CouponQueryHandler` → wraps `handleCouponQuery`
- `ShopSearchHandler` → wraps `handleShopSearch`
- `ShopDetailHandler` → wraps `handleShopDetail`
- `RuleQaHandler` → wraps `handleRuleQa`
- `ShopProfileQaHandler` → wraps `handleShopProfileQa`
- `VoucherRuleQaHandler` → wraps `handleVoucherRuleQa`
- `ShopBaseQaHandler` → wraps `handleShopBaseQa`

Each handler is `@Component` + `@RequiredArgsConstructor`, self-contained with its own dependencies injected.

- [ ] **Step 4: Rewrite AiChatServiceImpl.chat() as orchestrator**

```java
@Slf4j
@Service
public class AiChatServiceImpl implements AiChatService {
    // Shared dependencies
    @Resource private AiLlmService aiLlmService;
    @Resource private StringRedisTemplate stringRedisTemplate;
    @Resource private ShopProfileTool shopProfileTool;
    @Resource private RuleTool ruleTool;
    @Resource private ShopTool shopTool;
    @Resource private CouponTool couponTool;
    @Resource private BlogTool blogTool;
    @Resource private UserProfileService userProfileService;
    @Resource private RagService ragService;
    @Resource private AiRecommendService aiRecommendService;

    // Handlers
    @Resource private RecommendHandler recommendHandler;
    @Resource private CouponQueryHandler couponQueryHandler;
    @Resource private ShopSearchHandler shopSearchHandler;
    @Resource private ShopDetailHandler shopDetailHandler;
    @Resource private RuleQaHandler ruleQaHandler;
    @Resource private ShopProfileQaHandler shopProfileQaHandler;
    @Resource private VoucherRuleQaHandler voucherRuleQaHandler;
    @Resource private ShopBaseQaHandler shopBaseQaHandler;

    // Keep: chat(), getContext(), saveContext(), mergeRequestToContext(),
    // detectIntent(), resolveShopId(), extractPossibleShopName(),
    // buildDefaultSuggestions(), buildTextResponse(),
    // buildSearchSuggestions(), buildCouponSuggestions(),
    // buildRuleSuggestions(), buildDefaultResponse(), formatMoney(),
    // containsAny(), extractBudget(), extractMinScore(),
    // extractSearchKeyword(), buildSearchReason(), isNotBlank(), isBlank()

    @Override
    public AiChatResponse chat(AiChatRequest request) {
        // ... same validation + context logic (lines 74-98) ...
        AiIntentType intent = detectIntent(request);
        context.setLastIntent(intent.name());

        AiChatResponse response;
        switch (intent) {
            case RECOMMEND:      response = recommendHandler.handle(request, context); break;
            case COUPON_QUERY:   response = couponQueryHandler.handle(request, context); break;
            case SHOP_SEARCH:    response = shopSearchHandler.handle(request, context); break;
            case SHOP_DETAIL:    response = shopDetailHandler.handle(request, context); break;
            case RULE_QA:        response = ruleQaHandler.handle(request, context); break;
            case SHOP_PROFILE_QA:response = shopProfileQaHandler.handle(request, context); break;
            case VOUCHER_RULE_QA:response = voucherRuleQaHandler.handle(request, context); break;
            case SHOP_BASE_QA:   response = shopBaseQaHandler.handle(request, context); break;
            default:             response = buildDefaultResponse(); break;
        }

        saveContext(request.getSessionId(), context);
        return response;
    }
}
```

Remove the 8 private handler methods from the class (they move to their respective handler classes). Keep all shared utility methods.

- [ ] **Step 5: Verify compiles**

```bash
./mvnw compile
```
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/hmdp/handler/ src/main/java/com/hmdp/service/impl/AiChatServiceImpl.java
git commit -m "refactor: split AiChatServiceImpl intent handlers into separate classes"
```

---

### Task 9: Checkstyle 配置

**Files:**
- Create: `src/main/resources/checkstyle.xml`
- Modify: `pom.xml`

- [ ] **Step 1: Add checkstyle plugin to pom.xml**

Read `pom.xml`, add to `<build><plugins>`:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-checkstyle-plugin</artifactId>
    <version>3.3.1</version>
    <configuration>
        <configLocation>src/main/resources/checkstyle.xml</configLocation>
        <consoleOutput>true</consoleOutput>
        <failsOnError>false</failsOnError>
    </configuration>
    <executions>
        <execution>
            <goals><goal>check</goal></goals>
        </execution>
    </executions>
</plugin>
```

- [ ] **Step 2: Create checkstyle.xml (basic rules)**

Minimal ruleset — line length 150, no wildcard imports, no trailing whitespace, Javadoc disabled (not ready):

```xml
<?xml version="1.0"?>
<!DOCTYPE module PUBLIC
    "-//Checkstyle//DTD Checkstyle Configuration 1.3//EN"
    "https://checkstyle.org/dtds/configuration_1_3.dtd">
<module name="Checker">
    <module name="TreeWalker">
        <module name="AvoidStarImport"/>
        <module name="UnusedImports"/>
        <module name="NeedBraces"/>
        <module name="EqualsHashCode"/>
        <module name="IllegalThrows"/>
    </module>
    <module name="FileTabCharacter"/>
    <module name="RegexpSingleline">
        <property name="format" value="\s+$"/>
        <property name="message" value="Line has trailing whitespace."/>
    </module>
</module>
```

- [ ] **Step 3: Run checkstyle**

```bash
./mvnw checkstyle:check
```
Expected: CHECKSTYLE report. Fix any violations.

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/checkstyle.xml pom.xml
git commit -m "build: add Checkstyle with basic rules"
```

---

## 第二层：稳定性（15 个 Task）

### Task 10: 异常体系 — BusinessException / AiServiceException

**Files:**
- Create: `src/main/java/com/hmdp/exception/BusinessException.java`
- Create: `src/main/java/com/hmdp/exception/AiServiceException.java`
- Create: `src/main/java/com/hmdp/exception/ValidationException.java`
- Create: `src/main/java/com/hmdp/constant/ErrorCode.java`

- [ ] **Step 1: Create ErrorCode**

```java
package com.hmdp.constant;

public enum ErrorCode {
    STOCK_INSUFFICIENT("STOCK_INSUFFICIENT", "库存不足"),
    DUPLICATE_ORDER("DUPLICATE_ORDER", "不能重复下单"),
    SHOP_NOT_FOUND("SHOP_NOT_FOUND", "店铺不存在"),
    VOUCHER_NOT_FOUND("VOUCHER_NOT_FOUND", "优惠券不存在"),
    AI_TIMEOUT("AI_TIMEOUT", "AI服务响应超时"),
    AI_CIRCUIT_OPEN("AI_CIRCUIT_OPEN", "AI服务暂时不可用"),
    VALIDATION_ERROR("VALIDATION_ERROR", "参数校验失败"),
    INTERNAL_ERROR("INTERNAL_ERROR", "服务器内部错误");

    private final String code;
    private final String defaultMsg;

    ErrorCode(String code, String defaultMsg) {
        this.code = code;
        this.defaultMsg = defaultMsg;
    }
    public String getCode() { return code; }
    public String getDefaultMsg() { return defaultMsg; }
}
```

- [ ] **Step 2: Create BusinessException**

```java
package com.hmdp.exception;

import com.hmdp.constant.ErrorCode;

public class BusinessException extends RuntimeException {
    private final ErrorCode errorCode;
    private final String detail;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getDefaultMsg());
        this.errorCode = errorCode;
        this.detail = null;
    }

    public BusinessException(ErrorCode errorCode, String detail) {
        super(errorCode.getDefaultMsg());
        this.errorCode = errorCode;
        this.detail = detail;
    }

    public ErrorCode getErrorCode() { return errorCode; }
    public String getDetail() { return detail; }
}
```

- [ ] **Step 3: Create AiServiceException**

```java
package com.hmdp.exception;

public class AiServiceException extends RuntimeException {
    private final boolean circuitOpen;

    public AiServiceException(String message, boolean circuitOpen) {
        super(message);
        this.circuitOpen = circuitOpen;
    }

    public AiServiceException(String message, Throwable cause) {
        super(message, cause);
        this.circuitOpen = false;
    }

    public boolean isCircuitOpen() { return circuitOpen; }
}
```

- [ ] **Step 4: Create ValidationException (extend existing pattern)**

```java
package com.hmdp.exception;

import java.util.Map;

public class ValidationException extends RuntimeException {
    private final Map<String, String> fieldErrors;

    public ValidationException(Map<String, String> fieldErrors) {
        super("参数校验失败");
        this.fieldErrors = fieldErrors;
    }

    public Map<String, String> getFieldErrors() { return fieldErrors; }
}
```

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/hmdp/exception/ src/main/java/com/hmdp/constant/ErrorCode.java
git commit -m "feat: add exception hierarchy (Business/AI/Validation)"
```

---

### Task 11: 重写 Result + WebExceptionAdvice

**Files:**
- Modify: `src/main/java/com/hmdp/dto/Result.java`
- Modify: `src/main/java/com/hmdp/config/WebExceptionAdvice.java`
- Create: `src/main/java/com/hmdp/utils/TraceIdFilter.java`

- [ ] **Step 1: Create TraceIdFilter**

```java
package com.hmdp.utils;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;

@Component
public class TraceIdFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        String traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        MDC.put("traceId", traceId);
        response.setHeader("X-Trace-Id", traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove("traceId");
        }
    }
}
```

- [ ] **Step 2: Update Result to include code + traceId**

Read `Result.java`, change:

```java
package com.hmdp.dto;

import lombok.Data;
import org.slf4j.MDC;

@Data
public class Result {
    private Boolean success;
    private String code;
    private String errorMsg;
    private String detail;
    private String traceId;
    private Object data;
    private Long total;

    private Result() {}

    public static Result ok() {
        Result r = new Result();
        r.success = true;
        r.traceId = MDC.get("traceId");
        return r;
    }

    public static Result ok(Object data) {
        Result r = ok();
        r.data = data;
        return r;
    }

    public static Result ok(java.util.List<?> data, Long total) {
        Result r = ok(data);
        r.total = total;
        return r;
    }

    public static Result fail(String errorMsg) {
        Result r = new Result();
        r.success = false;
        r.errorMsg = errorMsg;
        r.code = "BUSINESS_ERROR";
        r.traceId = MDC.get("traceId");
        return r;
    }

    public static Result fail(String code, String errorMsg, String detail) {
        Result r = new Result();
        r.success = false;
        r.code = code;
        r.errorMsg = errorMsg;
        r.detail = detail;
        r.traceId = MDC.get("traceId");
        return r;
    }
}
```

Note: `lombok` is still used — keep `@Data` but remove `@NoArgsConstructor` and `@AllArgsConstructor`.

- [ ] **Step 3: Rewrite WebExceptionAdvice**

Read existing `WebExceptionAdvice.java`, rewrite:

```java
package com.hmdp.config;

import com.hmdp.constant.ErrorCode;
import com.hmdp.dto.Result;
import com.hmdp.exception.BusinessException;
import com.hmdp.exception.AiServiceException;
import com.hmdp.exception.ValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class WebExceptionAdvice {

    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result handleBusinessException(BusinessException e) {
        log.warn("Business error: code={}, detail={}", e.getErrorCode().getCode(), e.getDetail());
        return Result.fail(e.getErrorCode().getCode(), e.getMessage(), e.getDetail());
    }

    @ExceptionHandler(AiServiceException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result handleAiServiceException(AiServiceException e) {
        log.error("AI service error: {}", e.getMessage());
        if (e.isCircuitOpen()) {
            return Result.fail(ErrorCode.AI_CIRCUIT_OPEN.getCode(),
                    ErrorCode.AI_CIRCUIT_OPEN.getDefaultMsg(), null);
        }
        return Result.fail(ErrorCode.AI_TIMEOUT.getCode(), e.getMessage(), null);
    }

    @ExceptionHandler(ValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result handleValidationException(ValidationException e) {
        log.warn("Validation error: {}", e.getFieldErrors());
        return Result.fail(ErrorCode.VALIDATION_ERROR.getCode(), e.getMessage(),
                e.getFieldErrors().toString());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result handleSpringValidation(MethodArgumentNotValidException e) {
        Map<String, String> errors = new HashMap<>();
        e.getBindingResult().getFieldErrors().forEach(fe ->
                errors.put(fe.getField(), fe.getDefaultMessage()));
        return Result.fail(ErrorCode.VALIDATION_ERROR.getCode(), "参数校验失败", errors.toString());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result handleUnknownException(Exception e) {
        log.error("Unhandled exception", e);
        return Result.fail(ErrorCode.INTERNAL_ERROR.getCode(),
                ErrorCode.INTERNAL_ERROR.getDefaultMsg(), null);
    }
}
```

- [ ] **Step 4: Verify compiles**

```bash
./mvnw compile
```
Expected: BUILD SUCCESS (existing callers of `Result.fail("msg")` still work via backwards-compat method).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/hmdp/dto/Result.java src/main/java/com/hmdp/config/WebExceptionAdvice.java src/main/java/com/hmdp/utils/TraceIdFilter.java
git commit -m "feat: add traceId, error codes, and unified exception handling"
```

---

### Task 12: 引入 Resilience4j 依赖

**Files:**
- Modify: `pom.xml`

- [ ] **Step 1: Add Resilience4j to pom.xml**

Read `pom.xml`, add to `<dependencies>`:

```xml
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-spring-boot2</artifactId>
    <version>1.7.1</version>
</dependency>
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-circuitbreaker</artifactId>
    <version>1.7.1</version>
</dependency>
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-timelimiter</artifactId>
    <version>1.7.1</version>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aop</artifactId>
</dependency>
```

- [ ] **Step 2: Verify dependency resolves**

```bash
./mvnw dependency:resolve
```
Expected: SUCCESS

- [ ] **Step 3: Commit**

```bash
git add pom.xml
git commit -m "build: add Resilience4j for AI circuit breaker and retry"
```

---

### Task 13: Resilience4j 配置 + AiLlmService 集成

**Files:**
- Create: `src/main/java/com/hmdp/config/Resilience4jConfig.java`
- Create: `src/main/java/com/hmdp/constant/AiFallbackMessages.java`
- Modify: `src/main/resources/application.yaml`
- Modify: `src/main/java/com/hmdp/service/impl/AiLlmServiceImpl.java`

- [ ] **Step 1: Add Resilience4j config to application.yaml**

Append to `application.yaml`:

```yaml
resilience4j:
  circuitbreaker:
    instances:
      aiLlmCall:
        sliding-window-size: 20
        failure-rate-threshold: 50
        wait-duration-in-open-state: 30s
        permitted-number-of-calls-in-half-open-state: 3
  timelimiter:
    instances:
      aiLlmCall:
        timeout-duration: 15s
  retry:
    instances:
      aiLlmCall:
        max-attempts: 3
        wait-duration: 2s
        exponential-backoff-multiplier: 2
```

- [ ] **Step 2: Create AiFallbackMessages**

```java
package com.hmdp.constant;

public final class AiFallbackMessages {
    private AiFallbackMessages() {}

    public static final String CIRCUIT_OPEN = "AI助手暂时繁忙，请稍后再试。你可以先浏览店铺列表或查看优惠券。";
    public static final String TIMEOUT = "AI助手响应较慢，请稍后重试或换个简单的问题。";
    public static final String GENERAL_ERROR = "AI助手暂时无法回答这个问题，建议你直接查看店铺详情或优惠券信息。";
}
```

- [ ] **Step 3: Wrap AiLlmServiceImpl.chat() with Resilience4j**

Read `AiLlmServiceImpl.java`. Add annotations to the `chat()` method:

```java
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import java.util.concurrent.CompletableFuture;

@CircuitBreaker(name = "aiLlmCall", fallbackMethod = "chatFallback")
@Retry(name = "aiLlmCall")
@TimeLimiter(name = "aiLlmCall")
@Override
public String chat(String prompt) {
    try {
        return chatLanguageModel.generate(prompt);
    } catch (Exception e) {
        log.error("AiLlmService chat error", e);
        throw new AiServiceException("AI call failed", e);
    }
}
```

Note: `@TimeLimiter` requires returning `CompletableFuture<String>`. Since Spring Boot 2.3 doesn't support virtual threads, wrap in:

```java
@Override
public String chat(String prompt) {
    try {
        return chatAsync(prompt).get(15, TimeUnit.SECONDS);
    } catch (java.util.concurrent.TimeoutException e) {
        throw new AiServiceException("AI call timed out", false);
    } catch (Exception e) {
        throw new AiServiceException("AI call failed: " + e.getMessage(), e);
    }
}

@CircuitBreaker(name = "aiLlmCall", fallbackMethod = "chatFallback")
@Retry(name = "aiLlmCall")
public CompletableFuture<String> chatAsync(String prompt) {
    return CompletableFuture.supplyAsync(() ->
        chatLanguageModel.generate(prompt)
    );
}

private CompletableFuture<String> chatFallback(String prompt, AiServiceException e) {
    log.warn("AI fallback triggered, circuitOpen={}", e.isCircuitOpen());
    return CompletableFuture.completedFuture(
        e.isCircuitOpen() ? AiFallbackMessages.CIRCUIT_OPEN : AiFallbackMessages.GENERAL_ERROR
    );
}
```

- [ ] **Step 4: Also wrap answerWithEvidence**

Same pattern — wrap with `@CircuitBreaker(name = "aiLlmCall", fallbackMethod = "evidenceFallback")`.

Fallback method returns `CompletableFuture.completedFuture(fallbackAnswer)` — the existing fallback value already passed in.

- [ ] **Step 5: Verify compiles**

```bash
./mvnw compile
```
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/hmdp/service/impl/AiLlmServiceImpl.java src/main/java/com/hmdp/constant/AiFallbackMessages.java src/main/resources/application.yaml
git commit -m "feat: add Resilience4j circuit-breaker/timeout/retry to AI calls"
```

---

### Task 14: 引入 RocketMQ 依赖

**Files:**
- Modify: `pom.xml`
- Read: `application.yaml` to append RocketMQ config

- [ ] **Step 1: Add RocketMQ Spring Boot starter to pom.xml**

Read `pom.xml`, add to `<dependencies>`:

```xml
<dependency>
    <groupId>org.apache.rocketmq</groupId>
    <artifactId>rocketmq-spring-boot-starter</artifactId>
    <version>2.2.3</version>
</dependency>
```

Wait — `rocketmq-spring-boot-starter 2.2.3` requires Spring Boot 2.x compatible. Check compatibility:
- `rocketmq-spring-boot-starter:2.2.3` is compatible with Spring Boot 2.x ✓

- [ ] **Step 2: Add RocketMQ config to application.yaml**

Append:

```yaml
rocketmq:
  name-server: ${ROCKETMQ_NAME_SERVER:localhost:9876}
  producer:
    group: hmdp-producer
    send-message-timeout: 3000
  consumer:
    group: hmdp-consumer
```

- [ ] **Step 3: Verify**

```bash
./mvnw dependency:resolve
```
Expected: SUCCESS

- [ ] **Step 4: Commit**

```bash
git add pom.xml src/main/resources/application.yaml
git commit -m "build: add RocketMQ Spring Boot starter"
```

---

### Task 15: RocketMQ 配置 + Producer

**Files:**
- Create: `src/main/java/com/hmdp/config/RocketMQConfig.java`
- Create: `src/main/java/com/hmdp/mq/RocketMQTransactionProducer.java`

- [ ] **Step 1: Create RocketMQConfig (if needed)**

Since the starter auto-configures much of it, create minimal config:

```java
package com.hmdp.config;

import org.apache.rocketmq.spring.support.RocketMQMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RocketMQConfig {
    // Auto-config from rocketmq.name-server / rocketmq.producer.group is sufficient
    // Explicit bean only if needed
}
```

- [ ] **Step 2: Create RocketMQTransactionProducer**

```java
package com.hmdp.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class RocketMQTransactionProducer {

    private static final String TOPIC = "seckill-order-topic";

    @Resource
    private RocketMQTemplate rocketMQTemplate;

    @Resource
    private SeckillOrderTransactionListener transactionListener;

    public boolean sendSeckillOrder(Long voucherId, Long userId, Long orderId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("voucherId", voucherId);
        payload.put("userId", userId);
        payload.put("orderId", orderId);

        Message<?> message = MessageBuilder
                .withPayload(payload)
                .setHeader("orderId", orderId.toString())
                .build();

        try {
            rocketMQTemplate.sendMessageInTransaction(
                    TOPIC, message, null);  // null arg for transaction listener (uses registered one)
            log.info("Transaction message sent: orderId={}", orderId);
            return true;
        } catch (Exception e) {
            log.error("Failed to send transaction message, orderId={}", orderId, e);
            return false;
        }
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/hmdp/config/RocketMQConfig.java src/main/java/com/hmdp/mq/RocketMQTransactionProducer.java
git commit -m "feat: add RocketMQ transaction message producer for seckill"
```

---

### Task 16: RocketMQ TransactionListener + Consumer

**Files:**
- Create: `src/main/java/com/hmdp/mq/SeckillOrderTransactionListener.java`
- Create: `src/main/java/com/hmdp/mq/RocketMQConsumer.java`
- Create: `src/main/resources/compensate-seckill.lua`

- [ ] **Step 1: Write compensate Lua script**

Create `src/main/resources/compensate-seckill.lua`:

```lua
local voucherId = ARGV[1]
local userId = ARGV[2]

local stockKey = 'seckill:stock:' .. voucherId
local orderKey = 'seckill:order:' .. voucherId

redis.call('incrby', stockKey, 1)
redis.call('srem', orderKey, userId)
return 0
```

- [ ] **Step 2: Create SeckillOrderTransactionListener**

```java
package com.hmdp.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.exception.BusinessException;
import com.hmdp.service.IVoucherOrderService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionState;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.messaging.Message;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.Map;

@Slf4j
@RocketMQTransactionListener
public class SeckillOrderTransactionListener implements RocketMQLocalTransactionListener {

    @Resource
    private IVoucherOrderService voucherOrderService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static final DefaultRedisScript<Long> COMPENSATE_SCRIPT;
    static {
        COMPENSATE_SCRIPT = new DefaultRedisScript<>();
        COMPENSATE_SCRIPT.setLocation(
            new org.springframework.core.io.ClassPathResource("compensate-seckill.lua"));
        COMPENSATE_SCRIPT.setResultType(Long.class);
    }

    @Override
    public RocketMQLocalTransactionState executeLocalTransaction(Message msg, Object arg) {
        Map<String, Object> payload = (Map<String, Object>) msg.getPayload();
        Long voucherId = ((Number) payload.get("voucherId")).longValue();
        Long userId = ((Number) payload.get("userId")).longValue();
        Long orderId = ((Number) payload.get("orderId")).longValue();

        try {
            voucherOrderService.createVoucherOrderFromMq(voucherId, userId, orderId);
            log.info("Local transaction commit: orderId={}", orderId);
            return RocketMQLocalTransactionState.COMMIT;
        } catch (BusinessException e) {
            log.error("Business error, rolling back: orderId={}, error={}", orderId, e.getMessage());
            compensateRedisStock(voucherId, userId);
            return RocketMQLocalTransactionState.ROLLBACK;
        } catch (Exception e) {
            log.error("Unknown error, will retry: orderId={}", orderId, e);
            return RocketMQLocalTransactionState.UNKNOWN;
        }
    }

    @Override
    public RocketMQLocalTransactionState checkLocalTransaction(Message msg) {
        Map<String, Object> payload = (Map<String, Object>) msg.getPayload();
        Long voucherId = ((Number) payload.get("voucherId")).longValue();
        Long userId = ((Number) payload.get("userId")).longValue();
        Long orderId = ((Number) payload.get("orderId")).longValue();

        boolean exists = voucherOrderService.orderExists(voucherId, userId);
        if (exists) {
            return RocketMQLocalTransactionState.COMMIT;
        }
        log.warn("Order not found on recheck, rolling back: orderId={}", orderId);
        compensateRedisStock(voucherId, userId);
        return RocketMQLocalTransactionState.ROLLBACK;
    }

    private void compensateRedisStock(Long voucherId, Long userId) {
        stringRedisTemplate.execute(
                COMPENSATE_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString()
        );
        log.info("Redis stock compensated: voucherId={}, userId={}", voucherId, userId);
    }
}
```

- [ ] **Step 3: Create RocketMQConsumer (downstream notifications)**

```java
package com.hmdp.mq;

import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RocketMQMessageListener(topic = "seckill-order-topic",
        consumerGroup = "${rocketmq.consumer.group}")
public class RocketMQConsumer implements RocketMQListener<Map<String, Object>> {

    @Override
    public void onMessage(Map<String, Object> payload) {
        Long orderId = ((Number) payload.get("orderId")).longValue();
        Long userId = ((Number) payload.get("userId")).longValue();
        Long voucherId = ((Number) payload.get("voucherId")).longValue();

        log.info("Order committed: orderId={}, userId={}, voucherId={}", orderId, userId, voucherId);
        // Placeholder for downstream: send notification, sync cache, etc.
    }
}
```

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/hmdp/mq/ src/main/resources/compensate-seckill.lua
git commit -m "feat: add RocketMQ transaction listener, consumer, and compensate script"
```

---

### Task 17: 修改 VoucherOrderService 接口 + 实现（新增 createVoucherOrderFromMq / orderExists）

**Files:**
- Modify: `src/main/java/com/hmdp/service/IVoucherOrderService.java`
- Modify: `src/main/java/com/hmdp/service/impl/VoucherOrderServiceImpl.java`
- Modify: `src/main/resources/seckill.lua`

- [ ] **Step 1: Add new methods to IVoucherOrderService**

Read `IVoucherOrderService.java`, add:

```java
void createVoucherOrderFromMq(Long voucherId, Long userId, Long orderId);

boolean orderExists(Long voucherId, Long userId);
```

- [ ] **Step 2: Update seckill.lua — remove XADD**

Read `seckill.lua`, remove lines 38-40 (XADD) and the `orderId` parameter handling:

```lua
-- 1.参数列表
local voucherId = ARGV[1]
local userId = ARGV[2]

-- 2.数据key
local stockKey = 'seckill:stock:' .. voucherId
local orderKey = 'seckill:order:' .. voucherId

-- 3.1 判断库存是否充足
local stockStr = redis.call('get', stockKey)
if (not stockStr) then
    return 1
end

local stock = tonumber(stockStr)
if (not stock) then
    return 1
end

if (stock <= 0) then
    return 1
end

-- 3.2 判断是否重复下单
if (redis.call('sismember', orderKey, userId) == 1) then
    return 2
end

-- 3.3 扣库存 + 标记下单
redis.call('incrby', stockKey, -1)
redis.call('sadd', orderKey, userId)
return 0
```

- [ ] **Step 3: Rewrite seckillVoucher in VoucherOrderServiceImpl**

```java
@Resource
private RocketMQTransactionProducer transactionProducer;

@Override
public Result seckillVoucher(Long voucherId) {
    Long userId = UserHolder.getUser().getId();
    long orderId = redisIdWorker.nextId("order");

    // 1. Lua 原子预占库存（Redis 层面防重 + 扣库存）
    Long result = stringRedisTemplate.execute(
            SECKILL_SCRIPT,
            Collections.emptyList(),
            voucherId.toString(),
            userId.toString()
    );
    int r = result.intValue();
    if (r != 0) {
        return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
    }

    // 2. RocketMQ 事务消息（MySQL 落库）
    boolean sent = transactionProducer.sendSeckillOrder(voucherId, userId, orderId);
    if (!sent) {
        // 发送失败 → 补偿 Redis 库存
        compensateRedisStock(voucherId, userId);
        return Result.fail("系统繁忙，请稍后重试");
    }

    return Result.ok(orderId);
}

private void compensateRedisStock(Long voucherId, Long userId) {
    stringRedisTemplate.execute(COMPENSATE_SCRIPT,
            Collections.emptyList(),
            voucherId.toString(), userId.toString());
}
```

Keep the static `COMPENSATE_SCRIPT` field — load the same `compensate-seckill.lua`.

- [ ] **Step 4: Add createVoucherOrderFromMq method**

```java
@Override
@Transactional
public void createVoucherOrderFromMq(Long voucherId, Long userId, Long orderId) {
    // 1. 一人一单
    Long count = query()
            .eq("user_id", userId)
            .eq("voucher_id", voucherId)
            .count();
    if (count > 0) {
        throw new BusinessException(ErrorCode.DUPLICATE_ORDER,
                "userId=" + userId + ", voucherId=" + voucherId);
    }

    // 2. 扣减库存（失败则抛异常，触发事务回滚 + MQ Rollback）
    boolean success = seckillVoucherService.update()
            .setSql("stock = stock - 1")
            .eq("voucher_id", voucherId)
            .gt("stock", 0)
            .update();
    if (!success) {
        throw new BusinessException(ErrorCode.STOCK_INSUFFICIENT,
                "voucherId=" + voucherId);
    }

    // 3. 创建订单
    VoucherOrder voucherOrder = new VoucherOrder();
    voucherOrder.setId(orderId);
    voucherOrder.setUserId(userId);
    voucherOrder.setVoucherId(voucherId);
    save(voucherOrder);
}
```

- [ ] **Step 5: Add orderExists method**

```java
@Override
public boolean orderExists(Long voucherId, Long userId) {
    return query()
            .eq("user_id", userId)
            .eq("voucher_id", voucherId)
            .count() > 0;
}
```

- [ ] **Step 6: Remove old Redis Stream code**

Delete `VoucherOrderHandler` inner class, `handleVoucherOrder`, `handlePendingList`, `init()`, `destroy()`, `running` field, `proxy` field, `SECKILL_ORDER_EXECUTOR` field. Remove `@PostConstruct` and `@PreDestroy` methods.

- [ ] **Step 7: Verify compiles**

```bash
./mvnw compile
```
Expected: BUILD SUCCESS

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/hmdp/service/ src/main/resources/seckill.lua
git commit -m "refactor: replace Redis Stream with RocketMQ transaction messages in seckill"
```

---

### Task 18: 秒杀 Redis Key 设置 TTL

**Files:**
- Modify: `src/main/java/com/hmdp/service/impl/VoucherServiceImpl.java`

- [ ] **Step 1: Set TTL in addSeckillVoucher**

Read `VoucherServiceImpl.java`, in `addSeckillVoucher` method, after line 57:

```java
//保存秒杀库存到Redis中
stringRedisTemplate.opsForValue().set(
    SECKILL_STOCK_KEY + voucher.getId(), voucher.getStock().toString());

// Set TTL = activity end time + 7 days
if (voucher.getEndTime() != null) {
    java.time.Duration ttl = java.time.Duration.between(
        java.time.LocalDateTime.now(), voucher.getEndTime()).plusDays(7);
    if (!ttl.isNegative() && !ttl.isZero()) {
        stringRedisTemplate.expire(SECKILL_STOCK_KEY + voucher.getId(), ttl);
        stringRedisTemplate.expire("seckill:order:" + voucher.getId(), ttl);
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/hmdp/service/impl/VoucherServiceImpl.java
git commit -m "fix: add TTL to seckill Redis keys to prevent memory leak"
```

---

### Task 19: 库存对账 Scheduled Task

**Files:**
- Create: `src/main/java/com/hmdp/schedule/StockReconciliationTask.java`

- [ ] **Step 1: Annotate HmDianPingApplication with @EnableScheduling**

Read `HmDianPingApplication.java`, add:

```java
@EnableScheduling
```

- [ ] **Step 2: Create StockReconciliationTask**

```java
package com.hmdp.schedule;

import com.hmdp.entity.SeckillVoucher;
import com.hmdp.service.ISeckillVoucherService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

import static com.hmdp.utils.RedisConstants.SECKILL_STOCK_KEY;

@Slf4j
@Component
public class StockReconciliationTask {

    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Scheduled(cron = "0 */5 * * * ?")
    public void reconcileStock() {
        List<SeckillVoucher> vouchers = seckillVoucherService.list();
        for (SeckillVoucher sv : vouchers) {
            String redisKey = SECKILL_STOCK_KEY + sv.getVoucherId();
            String redisStockStr = stringRedisTemplate.opsForValue().get(redisKey);
            if (redisStockStr == null) continue;

            int redisStock = Integer.parseInt(redisStockStr);
            int dbStock = sv.getStock();

            if (Math.abs(redisStock - dbStock) > 5) {
                log.warn("Stock drift detected: voucherId={}, redis={}, db={}",
                        sv.getVoucherId(), redisStock, dbStock);
                // Correct Redis to match DB (DB is authoritative)
                stringRedisTemplate.opsForValue().set(redisKey, String.valueOf(dbStock));
            }
        }
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/hmdp/schedule/ src/main/java/com/hmdp/HmDianPingApplication.java
git commit -m "feat: add seckill stock reconciliation scheduled task"
```

---

### Task 20: Actuator + Micrometer 可观测性（P1）

**Files:**
- Modify: `pom.xml`
- Modify: `src/main/resources/application.yaml`

- [ ] **Step 1: Add actuator dependency**

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

- [ ] **Step 2: Configure actuator in application.yaml**

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: always
  metrics:
    export:
      prometheus:
        enabled: true
```

- [ ] **Step 3: Verify**

Start app, curl `http://localhost:8081/actuator/health`. Expected: `{"status":"UP"}`.

- [ ] **Step 4: Commit**

```bash
git add pom.xml src/main/resources/application.yaml
git commit -m "feat: add Actuator + Prometheus metrics export"
```

---

### Task 21: 数据库连接池优化（P1）

**Files:**
- Modify: `src/main/resources/application.yaml` (add HikariCP config)
- Modify: `application-prod.yml` (remove StdOutImpl)

- [ ] **Step 1: Add HikariCP config to application.yaml**

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
```

- [ ] **Step 2: Remove StdOutImpl from non-dev profiles**

Read `application-prod.yml`, ensure `mybatis-plus.configuration.log-impl` is NOT set (defaults to no logging SQL).

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/application.yaml application-prod.yml
git commit -m "config: add HikariCP pool settings and disable SQL logging in prod"
```

---

## 第三层：安全合规（7 个 Task）

### Task 22: MvcConfig 拦截器路径整改

**Files:**
- Modify: `src/main/java/com/hmdp/config/MvcConfig.java`

- [ ] **Step 1: Rewrite interceptor paths**

Read `MvcConfig.java`. Change to explicit three-tier mapping:

```java
@Override
public void addInterceptors(InterceptorRegistry registry) {
    // LoginInterceptor: 需要登录
    registry.addInterceptor(new LoginInterceptor())
            .addPathPatterns(
                    "/voucher-order/**",     // 秒杀
                    "/user/sign",            // 签到
                    "/blog/like/**",         // 点赞
                    "/blog/save",            // 发笔记
                    "/follow/**",            // 关注
                    "/ai/chat"               // AI 对话
            )
            .order(1);

    // RefreshTokenInterceptor: 所有请求（可选登录）
    registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate))
            .addPathPatterns("/**")
            .order(0);
}
```

Old exclusions (`/ai/**`, `/voucher/**`, `/shop/**`) removed — let refresh token interceptor handle all paths, LoginInterceptor only blocks where login is required.

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/hmdp/config/MvcConfig.java
git commit -m "fix: tighten auth interceptor paths — AI chat requires login"
```

---

### Task 23: UserHolder ThreadLocal 在异步线程中传递

**Files:**
- Create: `src/main/java/com/hmdp/config/TaskDecoratorConfig.java`

- [ ] **Step 1: Create TaskDecoratorConfig**

```java
package com.hmdp.config;

import com.hmdp.dto.UserDTO;
import com.hmdp.utils.UserHolder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;

@Configuration
public class TaskDecoratorConfig {

    @Bean
    public TaskDecorator userContextTaskDecorator() {
        return (runnable) -> {
            UserDTO user = UserHolder.getUser();
            return () -> {
                try {
                    UserHolder.saveUser(user);
                    runnable.run();
                } finally {
                    UserHolder.removeUser();
                }
            };
        };
    }
}
```

- [ ] **Step 2: Verify ThreadPoolConfig uses this decorator**

All `ThreadPoolExecutor` instances in `ThreadPoolConfig` should use `setTaskDecorator(userContextTaskDecorator)`. Update each executor:

```java
@Bean(name = BizExecutors.SECKILL_ORDER_EXECUTOR)
public ExecutorService seckillOrderExecutor(TaskDecorator taskDecorator) {
    ThreadPoolExecutor executor = new ThreadPoolExecutor(...);
    executor.setTaskDecorator(taskDecorator);  // No direct setter — use ThreadPoolTaskExecutor instead
    return executor;
}
```

Since `ThreadPoolExecutor` doesn't have `setTaskDecorator`, switch to `ThreadPoolTaskExecutor` in `ThreadPoolConfig`:

```java
@Bean(name = BizExecutors.SECKILL_ORDER_EXECUTOR)
public ExecutorService seckillOrderExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(8);
    executor.setMaxPoolSize(16);
    executor.setKeepAliveSeconds(60);
    executor.setQueueCapacity(200);
    executor.setThreadFactory(new NamedThreadFactory("seckill-order"));
    executor.setRejectedExecutionHandler(new SeckillRejectPolicy());
    executor.setTaskDecorator(taskDecorator());
    executor.initialize();
    return executor.getThreadPoolExecutor();
}
```

Apply same pattern to `cacheRebuildExecutor` and `feedDispatchExecutor`.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/hmdp/config/TaskDecoratorConfig.java src/main/java/com/hmdp/config/ThreadPoolConfig.java
git commit -m "feat: propagate UserHolder context to async thread pools"
```

---

### Task 24: spring-boot-starter-validation + DTO 校验

**Files:**
- Modify: `pom.xml`
- Modify: `src/main/java/com/hmdp/dto/request/AiChatRequest.java`
- Modify: `src/main/java/com/hmdp/controller/AiChatController.java`

- [ ] **Step 1: Add validation starter to pom.xml**

Read `pom.xml`, add:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
```

- [ ] **Step 2: Add validation annotations to AiChatRequest**

Read `AiChatRequest.java`, add:

```java
package com.hmdp.dto.request;

import lombok.Data;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Data
public class AiChatRequest {
    @Size(max = 36, message = "sessionId长度不能超过36位")
    private String sessionId;

    private Long userId;

    @NotBlank(message = "消息不能为空")
    @Size(max = 500, message = "消息长度不能超过500字")
    private String message;

    private Double lat;
    private Double lon;
    private Long shopId;
}
```

- [ ] **Step 3: Add @Valid to controller**

Read `AiChatController.java`, find the `/ai/chat` POST method, add `@Valid`:

```java
@PostMapping("/chat")
public Result chat(@RequestBody @Valid AiChatRequest request) {
    // existing logic
}
```

- [ ] **Step 4: Apply same pattern to other request DTOs**

Add `@NotBlank` to `LoginFormDTO.phone`, `@Size(min=6, max=20)` to password fields, etc.

- [ ] **Step 5: Commit**

```bash
git add pom.xml src/main/java/com/hmdp/dto/request/ src/main/java/com/hmdp/controller/
git commit -m "feat: add input validation on API request DTOs"
```

---

### Task 25: Prompt 注入防护

**Files:**
- Modify: `src/main/java/com/hmdp/service/impl/AiLlmServiceImpl.java`

- [ ] **Step 1: Add sanitization method to AiLlmServiceImpl**

```java
private String sanitize(String input) {
    if (input == null) return "";
    return input
            .replace(" ", "")   // null byte
            .replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "");  // control chars
}
```

- [ ] **Step 2: Apply sanitization to all user-facing inputs**

In `buildEvidencePrompt`, `generateShopProfileReply`, `polishRecommendReply`, etc., wrap user input:

```java
String prompt = String.format(AiPromptTemplates.RAG_QA_PROMPT,
        sanitize(question), contextText);
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/hmdp/service/impl/AiLlmServiceImpl.java
git commit -m "fix: add prompt injection sanitization for user inputs"
```

---

### Task 26: SQL 注入检查 + 审计日志（P1）

**Files:**
- Read: `src/main/resources/mapper/VoucherMapper.xml`
- Create: `src/main/java/com/hmdp/config/AuditLogAspect.java`

- [ ] **Step 1: Verify VoucherMapper.xml uses #{param} not ${param}**

Read `VoucherMapper.xml` — line 11: `WHERE v.shop_id = #{shopId}` → uses `#{}` ✓. No injection risk.

- [ ] **Step 2: Create AuditLogAspect for critical operations**

```java
package com.hmdp.config;

import com.hmdp.dto.UserDTO;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Slf4j
@Aspect
@Component
public class AuditLogAspect {

    @Around("@annotation(com.hmdp.annotation.AuditLog)")
    public Object audit(ProceedingJoinPoint pjp) throws Throwable {
        UserDTO user = UserHolder.getUser();
        String ip = getClientIp();
        String operation = pjp.getSignature().toShortString();
        Long userId = user != null ? user.getId() : null;

        log.info("AUDIT: userId={}, ip={}, operation={}, start", userId, ip, operation);
        long start = System.currentTimeMillis();
        try {
            Object result = pjp.proceed();
            log.info("AUDIT: userId={}, ip={}, operation={}, success, costMs={}",
                    userId, ip, operation, System.currentTimeMillis() - start);
            return result;
        } catch (Exception e) {
            log.error("AUDIT: userId={}, ip={}, operation={}, failed, costMs={}",
                    userId, ip, operation, System.currentTimeMillis() - start);
            throw e;
        }
    }

    private String getClientIp() {
        ServletRequestAttributes attrs =
            (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) return "unknown";
        return attrs.getRequest().getRemoteAddr();
    }
}
```

Create annotation: `src/main/java/com/hmdp/annotation/AuditLog.java`:

```java
package com.hmdp.annotation;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditLog {}
```

Apply `@AuditLog` to `VoucherOrderServiceImpl.seckillVoucher()`, `UserServiceImpl.login()`, etc.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/hmdp/config/AuditLogAspect.java src/main/java/com/hmdp/annotation/
git commit -m "feat: add audit log aspect for critical operations"
```

---

### Task 27: 手机号脱敏（P1）

**Files:**
- Create: `src/main/java/com/hmdp/utils/SensitiveUtils.java`
- Search all log statements with phone numbers

- [ ] **Step 1: Create SensitiveUtils**

```java
package com.hmdp.utils;

public final class SensitiveUtils {
    private SensitiveUtils() {}

    public static String maskPhone(String phone) {
        if (phone == null || phone.length() != 11) return phone;
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }
}
```

- [ ] **Step 2: Apply in logging**

Grep for log statements with phone-related content. Replace raw phone with `SensitiveUtils.maskPhone(phone)` in log output.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/hmdp/utils/SensitiveUtils.java
git commit -m "fix: add phone number masking utility for log output"
```

---

## 第四层：AI 能力增强（5 个 Task）

### Task 28: 工具类适配 Spring AI Function Calling

**Files:**
- Create: `src/main/java/com/hmdp/tool/AiToolDefinitions.java`
- Read existing tools: `ShopTool.java`, `CouponTool.java`, `RuleTool.java`, `ShopProfileTool.java`, `BlogTool.java`, `ShopTypeTool.java`, `UserTool.java`, `OrderTool.java`

- [ ] **Step 1: Create AiToolDefinitions — unified tool registry**

```java
package com.hmdp.tool;

import com.hmdp.dto.AiEvidenceDTO;
import com.hmdp.dto.tool.ShopSimpleDTO;
import com.hmdp.dto.tool.VoucherSimpleDTO;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Function;

@Component
public class AiToolDefinitions {

    private final ShopTool shopTool;
    private final CouponTool couponTool;
    private final RuleTool ruleTool;
    private final ShopProfileTool shopProfileTool;
    private final BlogTool blogTool;
    private final ShopTypeTool shopTypeTool;
    private final UserTool userTool;
    // Constructor injection...

    @Tool(name = "searchShops",
          description = "Search shops by keyword. Use when user asks to find stores by name or category.")
    public List<ShopSimpleDTO> searchShops(
            @ToolParam(description = "Search keyword, e.g. 'hotpot', 'KTV'") String keyword,
            @ToolParam(description = "User latitude", required = false) Double lat,
            @ToolParam(description = "User longitude", required = false) Double lon) {
        return shopTool.searchByKeyword(keyword, 1);
    }

    @Tool(name = "queryCoupons",
          description = "Query available coupons/vouchers for a specific shop.")
    public List<VoucherSimpleDTO> queryCoupons(
            @ToolParam(description = "Shop ID") Long shopId) {
        return couponTool.queryShopCoupons(shopId);
    }

    @Tool(name = "getShopDetail",
          description = "Get detailed information about a specific shop.")
    public ShopSimpleDTO getShopDetail(
            @ToolParam(description = "Shop ID") Long shopId) {
        return shopTool.getShopById(shopId);
    }

    @Tool(name = "queryRuleEvidence",
          description = "Query platform rules and policies documentation.")
    public List<AiEvidenceDTO> queryRuleEvidence(
            @ToolParam(description = "Question about rules/policies") String question) {
        return ruleTool.queryRuleEvidence(question);
    }

    @Tool(name = "queryShopProfile",
          description = "Query shop reviews, atmosphere, and user experience feedback.")
    public List<AiEvidenceDTO> queryShopProfile(
            @ToolParam(description = "Shop ID") Long shopId,
            @ToolParam(description = "What the user wants to know") String question) {
        return blogTool.queryShopBlogEvidence(shopId, question, 3);
    }

    @Tool(name = "queryShopTypes",
          description = "Get all available shop type categories (e.g. food, KTV, etc).")
    public List<String> queryShopTypes() {
        return shopTypeTool.getAllTypes();
    }

    @Tool(name = "queryUserPreferences",
          description = "Get current user's preference profile for personalized recommendations.")
    public String queryUserPreferences(
            @ToolParam(description = "User ID") Long userId) {
        return userTool.getPreferences(userId);
    }

    @Tool(name = "queryUserOrders",
          description = "Query current user's order history.")
    public String queryUserOrders(
            @ToolParam(description = "User ID") Long userId) {
        return userTool.getOrders(userId);
    }
}
```

Note: The actual `@Tool` and `@ToolParam` annotations will depend on whether we use Spring AI's or LangChain4j's annotation system. This task shows the design — adjust annotation imports when we select the exact approach in a follow-up.

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/hmdp/tool/AiToolDefinitions.java
git commit -m "feat: add unified AI tool definitions for Function Calling"
```

---

### Task 29: 重构 AiChatService 支持 Spring AI ChatClient

**Files:**
- Modify: `src/main/java/com/hmdp/service/impl/AiChatServiceImpl.java`
- Create: `src/main/java/com/hmdp/config/AiModelConfig.java` (modify existing)

Note: Spring AI Alibaba 1.0.0-M2 may not support `@Tool` annotations natively. This task keeps current LLM call path but restructures for future Function Calling.

- [ ] **Step 1: Create LLM-based intent routing method**

In `AiChatServiceImpl`, add a new `detectIntentByLlm()` method that uses LLM to classify intent, replacing the keyword-based `detectIntent()`:

```java
private AiIntentType detectIntentByLlm(AiChatRequest request) {
    // For now: fallback to keyword match
    // When Spring AI version supports Function Calling, replace with:
    // chatClient.prompt().tools(toolDefinitions).call().entity(AiIntentType.class);
    return detectIntent(request);  // keep old behavior for now
}
```

- [ ] **Step 2: Refactor chat() to use handler map instead of switch**

```java
private final Map<AiIntentType, AiChatHandler> handlerMap = new EnumMap<>(AiIntentType.class);

@PostConstruct
public void initHandlers() {
    handlerMap.put(AiIntentType.RECOMMEND, recommendHandler);
    handlerMap.put(AiIntentType.COUPON_QUERY, couponQueryHandler);
    handlerMap.put(AiIntentType.SHOP_SEARCH, shopSearchHandler);
    handlerMap.put(AiIntentType.SHOP_DETAIL, shopDetailHandler);
    handlerMap.put(AiIntentType.RULE_QA, ruleQaHandler);
    handlerMap.put(AiIntentType.SHOP_PROFILE_QA, shopProfileQaHandler);
    handlerMap.put(AiIntentType.VOUCHER_RULE_QA, voucherRuleQaHandler);
    handlerMap.put(AiIntentType.SHOP_BASE_QA, shopBaseQaHandler);
}

// In chat():
AiChatHandler handler = handlerMap.get(intent);
if (handler != null) {
    response = handler.handle(request, context);
} else {
    response = buildDefaultResponse();
}
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/hmdp/service/impl/AiChatServiceImpl.java
git commit -m "refactor: use handler map instead of switch, prepare for LLM routing"
```

---

### Task 30: 店名识别 Aho-Corasick 数据驱动

**Files:**
- Create: `src/main/java/com/hmdp/shopmatcher/ShopNameMatcher.java`
- Modify: `src/main/java/com/hmdp/service/impl/AiChatServiceImpl.java`

- [ ] **Step 1: Create ShopNameMatcher with simple Trie**

Since Aho-Corasick needs an external library, use a simple Trie which works well for 100s of shop names:

```java
package com.hmdp.shopmatcher;

import cn.hutool.core.util.StrUtil;
import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class ShopNameMatcher {

    @Resource
    private IShopService shopService;

    private volatile Map<String, Long> nameIndex = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        refresh();
    }

    public void refresh() {
        List<Shop> shops = shopService.list();
        Map<String, Long> index = new ConcurrentHashMap<>();
        for (Shop shop : shops) {
            if (StrUtil.isNotBlank(shop.getName())) {
                index.put(shop.getName().toLowerCase(), shop.getId());
            }
        }
        this.nameIndex = index;
        log.info("ShopNameMatcher refreshed: {} shops loaded", index.size());
    }

    public Long match(String message) {
        if (StrUtil.isBlank(message)) return null;
        String lower = message.toLowerCase();

        // Longest match first (sorted by name length desc)
        return nameIndex.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByKey(
                        Comparator.comparingInt(String::length).reversed()))
                .filter(e -> lower.contains(e.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }
}
```

- [ ] **Step 2: Replace extractPossibleShopName in AiChatServiceImpl**

Delete the hardcoded `extractPossibleShopName()` method (lines 865-896). Replace with:

```java
@Resource
private ShopNameMatcher shopNameMatcher;

private String extractPossibleShopName(String message) {
    Long shopId = shopNameMatcher.match(message);
    if (shopId != null) {
        ShopSimpleDTO shop = shopTool.getShopById(shopId);
        return shop != null ? shop.getName() : null;
    }
    return null;
}
```

- [ ] **Step 3: Add Redis Pub/Sub for shop name refresh**

In `ShopServiceImpl.update()`, after updating shop and deleting cache:

```java
stringRedisTemplate.convertAndSend("shop:name:refresh", shop.getId().toString());
```

Create listener:

```java
@Component
public class ShopNameRefreshListener {
    @Resource private ShopNameMatcher matcher;

    @PostConstruct
    public void init() {
        // Register Redis pub/sub listener
        stringRedisTemplate.getConnectionFactory().getConnection()
            .subscribe((message, pattern) -> matcher.refresh(),
                "shop:name:refresh".getBytes());
    }
}
```

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/hmdp/shopmatcher/ src/main/java/com/hmdp/service/impl/AiChatServiceImpl.java src/main/java/com/hmdp/service/impl/ShopServiceImpl.java
git commit -m "feat: replace hardcoded shop names with data-driven Trie matcher"
```

---

## 第五层：旁路缓存改进（4 个 Task）

### Task 31: 修复 CacheClient 锁释放 Bug

**Files:**
- Modify: `src/main/java/com/hmdp/utils/CacheClient.java`
- Modify: `src/main/java/com/hmdp/config/ThreadPoolConfig.java`

- [ ] **Step 1: Rewrite queryWithLogicalExpire**

Read `CacheClient.java`. The key change: unlock in the same thread that locked, not in the submitted task. Use a two-phase approach:

```java
public <R, ID> R queryWithLogicalExpire(String keyPrefix, ID id, Class<R> type,
        Function<ID, R> dbFallback, Long time, TimeUnit unit) {
    String key = keyPrefix + id;
    String shopJson = stringRedisTemplate.opsForValue().get(key);
    if (StrUtil.isBlank(shopJson)) {
        return null;
    }

    RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
    R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);

    LocalDateTime expireTime = redisData.getExpireTime();
    if (expireTime.isAfter(LocalDateTime.now())) {
        return r;  // not expired, return directly
    }

    // Expired — try to acquire lock
    String lockKey = LOCK_SHOP_KEY + id;
    boolean isLock = tryLock(lockKey);
    if (!isLock) {
        return r;  // someone else is rebuilding, return stale data
    }

    try {
        // Double-check: another thread may have rebuilt while we waited for lock
        shopJson = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(shopJson)) {
            redisData = JSONUtil.toBean(shopJson, RedisData.class);
            if (redisData.getExpireTime().isAfter(LocalDateTime.now())) {
                return JSONUtil.toBean((JSONObject) redisData.getData(), type);
            }
        }

        // Rebuild in current thread (lock held by this thread)
        R r1 = dbFallback.apply(id);
        this.setWithLogicalExpire(key, r1, time, unit);
        return r1;
    } finally {
        unLock(lockKey);  // Release in SAME thread that acquired
    }
}
```

Remove the `CACHE_REBUILD_EXECUTOR` field from this class. Remove the `submit()` pattern entirely.

- [ ] **Step 2: Remove the old static CACHE_REBUILD_EXECUTOR**

Delete line 73: `private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);`

- [ ] **Step 3: Switch cache rebuild thread pool in ThreadPoolConfig**

Read `ThreadPoolConfig.java`. The `cacheRebuildExecutor` already exists and uses `LinkedBlockingQueue<>(500)`. Switch it from `LinkedBlockingQueue<>(500)` to `ArrayBlockingQueue<>(100)` with `CallerRunsPolicy` to prevent OOM:

```java
@Bean(name = BizExecutors.CACHE_REBUILD_EXECUTOR)
public ExecutorService cacheRebuildExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(4);
    executor.setMaxPoolSize(8);
    executor.setKeepAliveSeconds(60);
    executor.setQueueCapacity(100);
    executor.setThreadFactory(new NamedThreadFactory("cache-rebuild"));
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
    executor.setTaskDecorator(taskDecorator());
    executor.initialize();
    return executor.getThreadPoolExecutor();
}
```

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/hmdp/utils/CacheClient.java src/main/java/com/hmdp/config/ThreadPoolConfig.java
git commit -m "fix: fix cache lock release — unlock in same thread, use bounded queue"
```

---

### Task 32: 缓存预热（P1）

**Files:**
- Create: `src/main/java/com/hmdp/config/CacheWarmupRunner.java`

- [ ] **Step 1: Create CacheWarmupRunner**

```java
package com.hmdp.config;

import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import com.hmdp.service.IShopTypeService;
import com.hmdp.utils.CacheClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TTL;

@Slf4j
@Component
@RequiredArgsConstructor
public class CacheWarmupRunner implements ApplicationRunner {

    private final IShopService shopService;
    private final CacheClient cacheClient;

    @Override
    public void run(ApplicationArguments args) {
        log.info("Starting cache warmup...");
        List<Shop> shops = shopService.list();
        int count = 0;
        for (Shop shop : shops) {
            try {
                cacheClient.set(CACHE_SHOP_KEY + shop.getId(), shop, CACHE_SHOP_TTL, TimeUnit.MINUTES);
                count++;
            } catch (Exception e) {
                log.warn("Failed to warm cache for shopId={}: {}", shop.getId(), e.getMessage());
            }
        }
        log.info("Cache warmup complete: {}/{} shops loaded", count, shops.size());
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/hmdp/config/CacheWarmupRunner.java
git commit -m "feat: add cache warmup on application startup"
```

---

### Task 33: 延迟双删（P1）

**Files:**
- Modify: `src/main/java/com/hmdp/service/impl/VoucherServiceImpl.java`
- Create: `src/main/java/com/hmdp/utils/DelayDoubleDelete.java`

- [ ] **Step 1: Create DelayDoubleDelete helper**

```java
package com.hmdp.utils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class DelayDoubleDelete {

    private final StringRedisTemplate stringRedisTemplate;

    @Async
    public void deleteWithDelay(String key, long delayMs) {
        try {
            Thread.sleep(delayMs);
            stringRedisTemplate.delete(key);
            log.debug("Delay double-delete: key={}, delayMs={}", key, delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
```

- [ ] **Step 2: Apply to VoucherServiceImpl for cache invalidation**

When Voucher data is updated (for future when Voucher is cached):

```java
@Transactional
public void updateVoucher(Voucher voucher) {
    updateById(voucher);
    stringRedisTemplate.delete(CACHE_VOUCHER_KEY + voucher.getId());
    delayDoubleDelete.deleteWithDelay(CACHE_VOUCHER_KEY + voucher.getId(), 200);
}
```

For Shop update (in `ShopServiceImpl.update`), keep the existing single-delete (Shop updates are infrequent, window is acceptable).

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/hmdp/utils/DelayDoubleDelete.java
git commit -m "feat: add delay double-delete for cache consistency on writes"
```

---

### Task 34: RedisConstants 改为枚举（P2）

**Files:**
- Modify: `src/main/java/com/hmdp/utils/RedisConstants.java`

- [ ] **Step 1: Refactor RedisConstants to enum**

```java
package com.hmdp.utils;

public enum RedisKey {
    LOGIN_CODE("login:code:", 2L),
    LOGIN_TOKEN("login:token:", 36000L),
    CACHE_SHOP("cache:shop:", 30L),
    CACHE_NULL("cache:null:", 2L),
    LOCK_SHOP("lock:shop:", 10L),
    SECKILL_STOCK("seckill:stock:", null),
    SECKILL_ORDER("seckill:order:", null),
    BLOG_LIKED("blog:liked:", null),
    FEED("feed:", null),
    SHOP_GEO("shop:geo:", null),
    USER_SIGN("sign:", null);

    private final String prefix;
    private final Long ttlMinutes;

    RedisKey(String prefix, Long ttlMinutes) {
        this.prefix = prefix;
        this.ttlMinutes = ttlMinutes;
    }

    public String key(Object id) { return prefix + id; }
    public String prefix() { return prefix; }
    public Long ttlMinutes() { return ttlMinutes; }
}
```

- [ ] **Step 2: Update all references**

Search-and-replace across codebase:
- `RedisConstants.CACHE_SHOP_KEY` → `RedisKey.CACHE_SHOP.prefix()`
- `RedisConstants.CACHE_SHOP_TTL` → `RedisKey.CACHE_SHOP.ttlMinutes()`
- `SECKILL_STOCK_KEY + voucherId` → `RedisKey.SECKILL_STOCK.key(voucherId)`
- etc.

- [ ] **Step 3: Remove old RedisConstants class (keep as deprecated with delegates in transition)**

First commit: keep both. Second commit: remove old class. This ensures non-breaking incremental change.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/hmdp/utils/RedisConstants.java
git commit -m "refactor: convert RedisConstants to enum-based RedisKey"
```

---

## P1/P2 后续 Task 概览

以下 Task 在 P0 完成后分批实施：

| # | Task | 所属层 |
|---|------|--------|
| 35 | GitHub Actions CI/CD pipeline (.github/workflows/) | 基础设施 |
| 36 | Prometheus AlertManager 告警规则配置 | 稳定性 |
| 37 | Nginx HTTPS/TLS 配置 | 安全 |
| 38 | AiSessionContextDTO 扩展为 5 轮对话摘要 | AI |
| 39 | Hybrid Search (BM25 + RRF) 实现 | AI |
| 40 | RAG evaluation script (50 labeled questions) | AI |
| 41 | Token usage tracking + daily report | AI |
| 42 | Redis Sentinel HA deployment | 缓存 |
| 43 | Caffeine L1 cache for hot keys | 缓存 |
| 44 | A/B testing framework for prompt templates | AI |

---

## 验证清单

每个 Task 完成后需验证：

- [ ] `./mvnw compile` 通过
- [ ] `./mvnw test` 通过（不引入新的失败）
- [ ] `./mvnw checkstyle:check` 通过（Task 9 之后）
- [ ] 相关功能的 curl 测试通过
- [ ] Git commit message 符合 `type: description` 格式
