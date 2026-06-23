# P3 地基收尾实现计划(backend 接 Nacos + 网关 Redis 限流)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 backend 单体(评估干预)注册到 Nacos 并被网关经 `lb://` 负载均衡路由(去掉硬编码 http://localhost:8080),并给 API 网关加 Redis 令牌桶限流过滤器,完成平台地基最后两项收尾。

**Architecture:** backend 加 spring-cloud + spring-cloud-alibaba BOM(它 parents spring-boot-starter-parent 无 dependencyManagement,需自行 import 两个 BOM)+ nacos-discovery 客户端,服务名设为 `agent-assessment`(对齐网关现有 route id);test profile 禁 discovery 保持 237 绿。网关加 spring-boot-starter-data-redis-reactive + 自研轻量令牌桶 GlobalFilter(按用户/IP 限流,Redis 计数,order 排在 JWT 过滤器之后),Redis 不可用时 fail-open 不阻断。

**Tech Stack:** Java 17 / Spring Boot 3.2.5 / Spring Cloud 2023.0.1 / Spring Cloud Alibaba 2023.0.1.0(Nacos)/ Spring Cloud Gateway(WebFlux)/ Redis(reactive)

## Global Constraints

- Java 17,Spring Boot 3.2.5,spring-cloud 2023.0.1,spring-cloud-alibaba 2023.0.1.0;依赖版本走 BOM/属性
- backend 237 测试全绿;全 reactor `clean install` 9 模块 SUCCESS;前端 build 绿
- **测试不依赖外部服务**:backend test profile 禁 Nacos discovery;网关限流测试用 mock/嵌入 Redis 或纯过滤器单测,不连真 Redis
- Maven 在 Git Bash:`export PATH="/c/tools/apache-maven-3.9.6/bin:$PATH"`;验证用 `mvn clean install`(stale target 假绿)
- 配置走 `${ENV:default}`(SELLM_NACOS_ADDR / SELLM_REDIS_HOST / SELLM_REDIS_PORT 已在 .env.example)
- 网关限流 fail-open:Redis 不可用不阻断请求(地基稳定性优先)
- 三条 AI/隐私红线不触碰
- 代码/配置变更后追加 `.claude/CLAUDE_CHANGES.md`

---

### Task 1: backend 接入 Nacos 服务发现

**Files:**
- Modify: `backend/pom.xml`(加 spring-cloud + spring-cloud-alibaba BOM 到 dependencyManagement;加 nacos-discovery 依赖)
- Modify: `backend/src/main/resources/application.yml`(spring.application.name 改 agent-assessment;加 nacos server-addr)
- Modify: `backend/src/test/resources/application-test.yml`(禁 discovery)
- Modify: `backend/src/main/resources/application-dev.yml`(若有 application.name 需对齐;加 nacos server-addr 走 env)
- Modify: `sellm-gateway/src/main/resources/application.yml`(assessment 路由 uri http://localhost:8080 → lb://agent-assessment)

**Interfaces:**
- Produces: backend 启动注册 Nacos,服务名 `agent-assessment`;网关经 `lb://agent-assessment` 路由到它
- Consumes: P0 docker-compose 的 nacos;P1 各 agent 的 Nacos 接入模式(照搬)

> **关键**:backend parents `spring-boot-starter-parent`,**没有** dependencyManagement,也没 spring-cloud BOM。所以不能像 agent 那样直接加 starter(agent 经 sellm-parent 拿到 BOM)。backend 要在自己 pom 的 `<dependencyManagement>` 里 import `spring-cloud-dependencies` + `spring-cloud-alibaba-dependencies` 两个 BOM,再加 nacos-discovery starter。
> **服务名**:backend 现 `spring.application.name=asd-assistant-backend`,但网关 route id 是 `agent-assessment`。改 backend 服务名为 `agent-assessment`,与网关 `lb://agent-assessment` 对齐(改名比改网关 + 所有引用更集中)。确认改名不影响测试(测试不依赖该名)。

- [x] **Step 1: backend/pom.xml 加两个 BOM 到 dependencyManagement**

在 `backend/pom.xml` 的 `<properties>` 后、`<dependencies>` 前插入(若已有 dependencyManagement 则合并):
```xml
    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.cloud</groupId>
                <artifactId>spring-cloud-dependencies</artifactId>
                <version>2023.0.1</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
            <dependency>
                <groupId>com.alibaba.cloud</groupId>
                <artifactId>spring-cloud-alibaba-dependencies</artifactId>
                <version>2023.0.1.0</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>
```

- [x] **Step 2: backend/pom.xml 加 nacos-discovery 依赖**

在 `backend/pom.xml` 的 `<dependencies>` 中加(版本由上面 BOM 管理):
```xml
        <dependency>
            <groupId>com.alibaba.cloud</groupId>
            <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
        </dependency>
```

- [x] **Step 3: backend 主 application.yml 改服务名 + 加 nacos**

修改 `backend/src/main/resources/application.yml`:把 `spring.application.name` 从 `asd-assistant-backend` 改为 `agent-assessment`;在 `spring:` 下加 nacos discovery(与现有 spring 子键合并,不破坏 datasource/sql 等):
```yaml
  application:
    name: agent-assessment
  cloud:
    nacos:
      discovery:
        server-addr: ${SELLM_NACOS_ADDR:127.0.0.1:8848}
```

- [x] **Step 4: backend test profile 禁 discovery**

修改 `backend/src/test/resources/application-test.yml`,在 `spring:` 下加(与现有 spring 子键合并):
```yaml
  cloud:
    nacos:
      discovery:
        enabled: false
    discovery:
      enabled: false
    service-registry:
      auto-registration:
        enabled: false
```

- [x] **Step 5: backend dev profile 加 nacos server-addr**

修改 `backend/src/main/resources/application-dev.yml`:若它覆盖了 spring.application.name 需同步改为 agent-assessment;在 `spring.cloud.nacos.discovery` 加 `server-addr: ${SELLM_NACOS_ADDR:127.0.0.1:8848}`(dev 起真 backend 时连本地 Nacos)。若 dev yml 无 cloud 段则新增该段。

- [x] **Step 6: 网关 assessment 路由改 lb://**

修改 `sellm-gateway/src/main/resources/application.yml` 的 agent-assessment 路由:
```yaml
        - id: agent-assessment
          uri: lb://agent-assessment
          predicates:
            - Path=/api/auth/**,/api/orgs/**,/api/classes/**,/api/children/**,/api/scales/**,/api/reports/**,/api/ieps/**,/api/family-ieps/**,/api/assessments/**,/api/users/**
```
把上方注释"backend 单体(assessment)尚未接入 Nacos,保留直连"改为"backend 经 Nacos 注册为 agent-assessment,lb 负载均衡"。

- [x] **Step 7: 验证 backend 237 全绿(无 Nacos 运行)**

```bash
export PATH="/c/tools/apache-maven-3.9.6/bin:$PATH"
cd /d/works/test/SELLM/dev_workspace
mvn -pl backend clean test 2>&1 | grep -E "Tests run:.*Failures|BUILD" | tail -3
```
Expected: `Tests run: 237, Failures: 0, Errors: 0` + `BUILD SUCCESS`。test profile 禁 discovery 后,backend 测试上下文不连 Nacos。

> 若测试启动卡住或报连 Nacos 失败,说明 test 禁用未生效——检查 application-test.yml 的 spring.cloud 段。报 BLOCKED。

- [x] **Step 8: 验证网关 contextLoads(lb 路由 + 无 Nacos)**

```bash
export PATH="/c/tools/apache-maven-3.9.6/bin:$PATH"
cd /d/works/test/SELLM/dev_workspace
mvn -pl sellm-gateway clean test 2>&1 | grep -E "Tests run:|BUILD" | tail -3
```
Expected: 网关测试全绿(gateway 已有 test yml 禁 discovery;改 lb:// 不影响 contextLoads,因为路由解析是运行期)。

- [x] **Step 9: Commit**

```bash
git add backend/pom.xml backend/src/main/resources/application.yml backend/src/main/resources/application-dev.yml backend/src/test/resources/application-test.yml sellm-gateway/src/main/resources/application.yml
git commit -m "feat(p3): backend 接入 Nacos(服务名 agent-assessment)+ 网关 assessment 改 lb 路由"
```

---

### Task 2: 网关 Redis 令牌桶限流过滤器

**Files:**
- Modify: `sellm-gateway/pom.xml`(加 spring-boot-starter-data-redis-reactive)
- Create: `sellm-gateway/src/main/java/com/sellm/gateway/RateLimitProperties.java`
- Create: `sellm-gateway/src/main/java/com/sellm/gateway/RateLimitGatewayFilter.java`
- Modify: `sellm-gateway/src/main/java/com/sellm/gateway/GatewayApplication.java`(@EnableConfigurationProperties 加 RateLimitProperties)
- Modify: `sellm-gateway/src/main/resources/application.yml`(redis 连接 + sellm.gateway.rate-limit 配置)
- Test: `sellm-gateway/src/test/java/com/sellm/gateway/RateLimitGatewayFilterTest.java`

**Interfaces:**
- Consumes: P1 的 JwtAuthGatewayFilter(限流过滤器 order 排在它之后,优先用注入的 X-User-Id 做限流 key,无则用 IP)
- Produces: 网关全局限流过滤器——每个 key(userId 或 IP)在窗口内超过阈值返回 429;Redis 不可用 fail-open 放行

> **设计**:用固定窗口计数(INCR + EXPIRE,简单够用,YAGNI 不上滑动窗口/Lua 令牌桶)。reactive Redis(`ReactiveStringRedisTemplate`)。key = `ratelimit:{userId 或 ip}:{epochSecond/windowSeconds}`。order 设为 0(JwtAuthGatewayFilter 是 -100,先鉴权注入 X-User-Id,再限流)。Redis 操作失败 `onErrorResume` 放行(fail-open)。测试:过滤器单测注入假 ReactiveStringRedisTemplate(或用计数桩)验证「未超限放行 / 超限 429 / Redis 异常放行」,不连真 Redis。

- [x] **Step 1: 写失败测试**

`sellm-gateway/src/test/java/com/sellm/gateway/RateLimitGatewayFilterTest.java`:
```java
package com.sellm.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RateLimitGatewayFilterTest {

    private RateLimitProperties props() {
        RateLimitProperties p = new RateLimitProperties();
        p.setEnabled(true);
        p.setLimit(2);
        p.setWindowSeconds(60);
        return p;
    }

    @SuppressWarnings("unchecked")
    private ReactiveStringRedisTemplate redisReturning(Long count) {
        ReactiveStringRedisTemplate tpl = mock(ReactiveStringRedisTemplate.class);
        ReactiveValueOperations<String, String> ops = mock(ReactiveValueOperations.class);
        when(tpl.opsForValue()).thenReturn(ops);
        when(ops.increment(anyString())).thenReturn(Mono.just(count));
        when(tpl.expire(anyString(), any(Duration.class))).thenReturn(Mono.just(true));
        return tpl;
    }

    @Test
    void 未超限放行() {
        RateLimitGatewayFilter filter = new RateLimitGatewayFilter(redisReturning(1L), props());
        MockServerHttpRequest req = MockServerHttpRequest.get("/api/teaching/x").header("X-User-Id", "42").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(req);
        final boolean[] passed = {false};
        GatewayFilterChain chain = e -> { passed[0] = true; return Mono.empty(); };
        filter.filter(exchange, chain).block();
        assertTrue(passed[0]);
        assertNotEquals(429, exchange.getResponse().getStatusCode() == null ? 0 : exchange.getResponse().getStatusCode().value());
    }

    @Test
    void 超限返回429() {
        RateLimitGatewayFilter filter = new RateLimitGatewayFilter(redisReturning(3L), props());
        MockServerHttpRequest req = MockServerHttpRequest.get("/api/teaching/x").header("X-User-Id", "42").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(req);
        GatewayFilterChain chain = e -> Mono.empty();
        filter.filter(exchange, chain).block();
        assertEquals(429, exchange.getResponse().getStatusCode().value());
    }

    @Test
    void redis异常放行failopen() {
        ReactiveStringRedisTemplate tpl = mock(ReactiveStringRedisTemplate.class);
        ReactiveValueOperations<String, String> ops = mock(ReactiveValueOperations.class);
        when(tpl.opsForValue()).thenReturn(ops);
        when(ops.increment(anyString())).thenReturn(Mono.error(new RuntimeException("redis down")));
        RateLimitGatewayFilter filter = new RateLimitGatewayFilter(tpl, props());
        MockServerHttpRequest req = MockServerHttpRequest.get("/api/teaching/x").header("X-User-Id", "42").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(req);
        final boolean[] passed = {false};
        GatewayFilterChain chain = e -> { passed[0] = true; return Mono.empty(); };
        filter.filter(exchange, chain).block();
        assertTrue(passed[0], "Redis 异常应 fail-open 放行");
    }
}
```

- [x] **Step 2: 网关加 redis-reactive 依赖**

`sellm-gateway/pom.xml` 的 `<dependencies>` 加:
```xml
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis-reactive</artifactId>
        </dependency>
```
(测试已用的 mockito 来自 spring-boot-starter-test,gateway 已依赖 test starter。)

- [x] **Step 3: 运行测试确认失败**

```bash
export PATH="/c/tools/apache-maven-3.9.6/bin:$PATH"
cd /d/works/test/SELLM/dev_workspace
mvn -pl sellm-gateway test -Dtest=RateLimitGatewayFilterTest 2>&1 | grep -E "ERROR|cannot find symbol|Tests run|BUILD" | tail -8
```
Expected: 编译失败(RateLimitGatewayFilter / RateLimitProperties 未定义)。

- [x] **Step 4: 实现 RateLimitProperties**

`sellm-gateway/src/main/java/com/sellm/gateway/RateLimitProperties.java`:
```java
package com.sellm.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sellm.gateway.rate-limit")
public class RateLimitProperties {
    /** 是否启用限流。 */
    private boolean enabled = true;
    /** 窗口内允许的最大请求数。 */
    private int limit = 100;
    /** 窗口秒数(固定窗口)。 */
    private int windowSeconds = 60;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getLimit() { return limit; }
    public void setLimit(int limit) { this.limit = limit; }
    public int getWindowSeconds() { return windowSeconds; }
    public void setWindowSeconds(int windowSeconds) { this.windowSeconds = windowSeconds; }
}
```

- [x] **Step 5: 实现 RateLimitGatewayFilter**

`sellm-gateway/src/main/java/com/sellm/gateway/RateLimitGatewayFilter.java`:
```java
package com.sellm.gateway;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Component
public class RateLimitGatewayFilter implements GlobalFilter, Ordered {

    private final ReactiveStringRedisTemplate redis;
    private final RateLimitProperties props;

    public RateLimitGatewayFilter(ReactiveStringRedisTemplate redis, RateLimitProperties props) {
        this.redis = redis;
        this.props = props;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!props.isEnabled()) {
            return chain.filter(exchange);
        }
        String id = resolveKey(exchange);
        long window = System.currentTimeMillis() / 1000L / props.getWindowSeconds();
        String key = "ratelimit:" + id + ":" + window;

        return redis.opsForValue().increment(key)
                .flatMap(count -> {
                    Mono<Void> proceed = redis.expire(key, Duration.ofSeconds(props.getWindowSeconds()))
                            .then(Mono.defer(() -> chain.filter(exchange)));
                    if (count != null && count > props.getLimit()) {
                        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                        return exchange.getResponse().setComplete();
                    }
                    return proceed;
                })
                .onErrorResume(e -> chain.filter(exchange)); // fail-open:Redis 异常不阻断
    }

    private String resolveKey(ServerWebExchange exchange) {
        String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
        if (userId != null && !userId.isEmpty()) {
            return "u:" + userId;
        }
        if (exchange.getRequest().getRemoteAddress() != null) {
            return "ip:" + exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
        }
        return "ip:unknown";
    }

    @Override
    public int getOrder() {
        return 0; // 在 JwtAuthGatewayFilter(-100)之后,X-User-Id 已注入
    }
}
```

- [x] **Step 6: GatewayApplication 注册 RateLimitProperties**

修改 `sellm-gateway/src/main/java/com/sellm/gateway/GatewayApplication.java` 的 `@EnableConfigurationProperties`,加入 RateLimitProperties:
```java
@EnableConfigurationProperties({GatewayJwtProperties.class, RateLimitProperties.class})
```
(若原来是单个 `@EnableConfigurationProperties(GatewayJwtProperties.class)`,改为上面的数组形式。)

- [x] **Step 7: 网关 application.yml 加 redis + rate-limit 配置**

`sellm-gateway/src/main/resources/application.yml`:在 `spring:` 下加 redis 连接:
```yaml
  data:
    redis:
      host: ${SELLM_REDIS_HOST:127.0.0.1}
      port: ${SELLM_REDIS_PORT:6379}
```
在末尾 `sellm.gateway` 下(与 jwt 同级)加:
```yaml
    rate-limit:
      enabled: ${SELLM_RATELIMIT_ENABLED:true}
      limit: ${SELLM_RATELIMIT_LIMIT:100}
      window-seconds: ${SELLM_RATELIMIT_WINDOW:60}
```

- [x] **Step 8: 运行测试确认通过**

```bash
export PATH="/c/tools/apache-maven-3.9.6/bin:$PATH"
cd /d/works/test/SELLM/dev_workspace
mvn -pl sellm-gateway clean test 2>&1 | grep -E "Tests run:|BUILD" | tail -5
```
Expected: RateLimitGatewayFilterTest 3 个测试通过 + 原有 JwtAuthGatewayFilterTest + contextLoads 通过,`BUILD SUCCESS`。

> 若 contextLoads 失败报缺 Redis 连接 bean:`spring-boot-starter-data-redis-reactive` 自动配置 ReactiveStringRedisTemplate,只要 redis host 配置存在(不实际连接,bean 创建不连接)即可。若仍失败检查 @EnableConfigurationProperties 是否含 RateLimitProperties。报 BLOCKED 附错误。

- [x] **Step 9: Commit**

```bash
git add sellm-gateway/
git commit -m "feat(p3): 网关 Redis 令牌桶限流过滤器(固定窗口,按用户/IP,fail-open)"
```

---

### Task 3: P3 全量回归 + .env/文档

**Files:**
- Modify: `.env.example`(补 SELLM_RATELIMIT_* 说明;确认 nacos/redis 配置齐全)
- Verification only(+ `.claude/CLAUDE_CHANGES.md`)

**Interfaces:**
- Produces: 确认 Nacos 接入 + 限流不破坏现有功能,全 reactor 9 模块绿

- [x] **Step 1: 全 reactor clean install(9 模块)**

```bash
export PATH="/c/tools/apache-maven-3.9.6/bin:$PATH"
cd /d/works/test/SELLM/dev_workspace
mvn clean install 2>&1 | grep -E "Reactor Summary|SUCCESS \[|FAILURE \[|BUILD SUCCESS|BUILD FAILURE|Tests run:.*Failures: [1-9]" | tail -15
```
Expected: 9 个模块全 SUCCESS,`BUILD SUCCESS`,无 Failures。

- [x] **Step 2: 确认 backend 接入 Nacos 但测试不连(已在 Task1 验证,这里复核全量)**

```bash
export PATH="/c/tools/apache-maven-3.9.6/bin:$PATH"
cd /d/works/test/SELLM/dev_workspace
mvn -pl backend test 2>&1 | grep -E "Tests run: 237|BUILD" | tail -2
```
Expected: `Tests run: 237, Failures: 0` + `BUILD SUCCESS`。

- [x] **Step 3: 前端 build**

```bash
cd /d/works/test/SELLM/dev_workspace/frontend
npm run build 2>&1 | grep -E "built in|error" | tail -2
```
Expected: `✓ built in X.XXs`。

- [x] **Step 4: .env.example 补限流配置说明**

在 `.env.example` 的 Redis 段附近追加:
```bash
# ── API 网关限流(P3,固定窗口,按用户/IP;Redis 不可用时 fail-open 放行)──
SELLM_RATELIMIT_ENABLED=true
SELLM_RATELIMIT_LIMIT=100
SELLM_RATELIMIT_WINDOW=60
```
(SELLM_REDIS_HOST/PORT、SELLM_NACOS_ADDR 已在 P0/P1 加,确认在即可。)

- [x] **Step 5: 追加变更记录 + 确认工作树**

向 `.claude/CLAUDE_CHANGES.md` 追加 P3 记录(FEATURE 类型)。
```bash
cd /d/works/test/SELLM/dev_workspace
git status --short
```
Expected: 仅 .env.example 待提交(.claude 不入库)。

- [x] **Step 6: Commit**

```bash
git add .env.example
git commit -m "docs(p3): .env.example 补网关限流配置说明"
```

---

## 文件清单总览

```
dev_workspace/
├── backend/
│   ├── pom.xml                      (MODIFY: +spring-cloud/alibaba BOM +nacos-discovery)
│   └── src/
│       ├── main/resources/application.yml      (MODIFY: name=agent-assessment +nacos)
│       ├── main/resources/application-dev.yml  (MODIFY: name对齐 +nacos server-addr)
│       └── test/resources/application-test.yml (MODIFY: 禁 discovery)
├── sellm-gateway/
│   ├── pom.xml                      (MODIFY: +data-redis-reactive)
│   └── src/
│       ├── main/java/com/sellm/gateway/
│       │   ├── GatewayApplication.java         (MODIFY: @EnableConfigurationProperties +RateLimitProperties)
│       │   ├── RateLimitProperties.java        (NEW)
│       │   └── RateLimitGatewayFilter.java     (NEW)
│       ├── main/resources/application.yml       (MODIFY: assessment lb:// + redis + rate-limit)
│       └── test/java/com/sellm/gateway/RateLimitGatewayFilterTest.java (NEW)
└── .env.example                     (MODIFY: 限流配置说明)
```
