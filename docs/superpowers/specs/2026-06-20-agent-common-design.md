# sellm-agent-common 共享模块设计文档

> 文档日期:2026-06-20
> 状态:设计已确认,待用户复核
> 隶属:特殊教育垂直大模型平台 · Agent 脚手架共享层
> 触发:agent-teaching 终审 Important #2(qa+teaching 已两次复制 agent 脚手架,research/aids 会再复制 2 次)
> 复用模板来源:`docs/superpowers/specs/2026-06-20-agent-qa-design.md` / `2026-06-20-agent-teaching-design.md`

## 1. 目标与范围

消除 agent-qa 与 agent-teaching 之间重复的「Agent 业务服务脚手架」(异常类、属性、异常处理器、HTTP 智能层客户端的传输层),抽到新建共享模块 `sellm-agent-common`,使后续 research/aids Agent 复用而非再复制。**纯重构:行为不变,各 agent 测试保持全绿。**

### 1.1 范围

- **在范围**:新建 `sellm-agent-common` 模块;抽取 SmartLayerException / UnauthorizedException / SmartLayerProperties / AgentExceptionHandler / AbstractHttpSmartLayerClient;自动装配;迁移 qa + teaching 两个现存 agent。
- **不在范围(YAGNI)**:SmartLayerClient 接口不抽公共(各 agent typed `generate` 签名本就不同);不动 Python 侧;不改各 agent 的业务逻辑/实体/持久化;不引入新外部依赖。

### 1.2 重复盘点(已核实)

| 类 | qa vs teaching 差异 | 处置 |
|---|---|---|
| `SmartLayerException` | 0 行(仅 package) | 抽到 common,删两份 |
| `UnauthorizedException` | 0 行(仅 package) | 抽到 common,删两份 |
| `QaExceptionHandler` / `TeachingExceptionHandler` | 仅类名 | 抽成共享 `AgentExceptionHandler`,删两份 |
| `SmartLayerProperties` | 14 行(qa 有 topK;前缀 `sellm.qa.` vs `sellm.teaching.`) | 抽到 common,统一前缀 `sellm.smart-layer`,去 topK |
| `HttpSmartLayerClient` | 49 行(invoke 路径 / 请求体 / 返回类型不同) | 传输抽到 `AbstractHttpSmartLayerClient`;各 agent 留 typed 子类 |
| `SmartLayerClient`(接口) | 15 行(typed `generate` 签名各异) | **留各 agent**(不抽) |

## 2. 架构

```
sellm-common-core  ←─ sellm-agent-common ←─ sellm-agent-qa / sellm-agent-teaching / (后续 research/aids)
   (Result/枚举/             (Agent 脚手架:                  (各 agent:typed SmartLayerClient
    Anonymizer/                异常/属性/异常处理器/             + HttpXxxClient 继承基类
    ObjectStorage 自动装配)     抽象 HTTP 客户端 + 自动装配)        + 业务编排/持久化)
```

依赖方向单向无环:agent → sellm-agent-common → sellm-common-core。

## 3. 组件设计

### 3.1 sellm-agent-common(根包 com.sellm.agentcommon)

| 组件 | 职责 |
|---|---|
| `SmartLayerException` | 调 Python 智能层失败的运行时异常 |
| `UnauthorizedException` | 缺 X-User-Id 的运行时异常 |
| `SmartLayerProperties` | `@ConfigurationProperties("sellm.smart-layer")`,baseUrl + timeoutSeconds |
| `AbstractHttpSmartLayerClient` | 抽象基类:内建 HTTP/1.1 HttpClient(构造接收 SmartLayerProperties);`protected String send(String path, String jsonBody)`(POST,2xx 检查,异常包 SmartLayerException)。**非 bean**(抽象);各 agent 具体 client 继承 |
| `AgentExceptionHandler` | `@RestControllerAdvice`:UnauthorizedException→401(ErrorCode.UNAUTHORIZED);BusinessException→ACCESS_DENIED=403 / NOT_FOUND=404 / 其余=400 |
| `AgentCommonAutoConfiguration` | `@AutoConfiguration`:注册 AgentExceptionHandler bean + `@EnableConfigurationProperties(SmartLayerProperties.class)` |

### 3.2 各 agent 保留

- `SmartLayerClient`(接口):typed,各 agent 自己的签名(qa:`QaAnswer generate(String question, int topK)`;teaching:`String generate(String task, String content, String disorderType, String scene, String mode)`)。
- `HttpSmartLayerClient`(@Component):继承 `AbstractHttpSmartLayerClient`,实现各自 typed 方法(拼请求体 JSON → `send(path, body)` → 解析响应到 typed 返回)。

### 3.3 AbstractHttpSmartLayerClient 设计(抽象基类供传输)

```java
package com.sellm.agentcommon;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** 智能层 HTTP 客户端抽象基类:封装传输(HTTP/1.1 + send + 2xx 检查)。子类拼请求体/解析响应。 */
public abstract class AbstractHttpSmartLayerClient {

    protected final SmartLayerProperties props;
    private final HttpClient httpClient;

    protected AbstractHttpSmartLayerClient(SmartLayerProperties props) {
        this.props = props;
        this.httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(props.getTimeoutSeconds()))
            .build();
    }

    /**
     * POST jsonBody 到 props.baseUrl + path,返回响应体。
     * 强制 HTTP/1.1(JDK 默认 HTTP/2 与部分网关协商卡死)。失败包 SmartLayerException。
     */
    protected String send(String path, String jsonBody) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(props.getBaseUrl() + path))
                .timeout(Duration.ofSeconds(props.getTimeoutSeconds()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
            HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                throw new SmartLayerException("智能层返回非 2xx: " + resp.statusCode());
            }
            return resp.body();
        } catch (SmartLayerException e) {
            throw e;
        } catch (Exception e) {
            throw new SmartLayerException("智能层调用失败", e);
        }
    }
}
```

> 各 agent 子类示例(qa):
> ```java
> @Component
> public class HttpSmartLayerClient extends AbstractHttpSmartLayerClient implements SmartLayerClient {
>     private final ObjectMapper json = new ObjectMapper();
>     public HttpSmartLayerClient(SmartLayerProperties props) { super(props); }
>     @Override public QaAnswer generate(String question, int topK) {
>         ObjectNode body = json.createObjectNode();
>         body.put("question", question); body.put("topK", topK);
>         String resp = send("/v1/agents/qa/invoke", json.writeValueAsString(body)); // 解析略
>         ...
>     }
> }
> ```
> 测试仍可子类化覆写 `send(...)`(protected)注入假响应、不真连网。

### 3.4 SmartLayerProperties(统一前缀)

```java
package com.sellm.agentcommon;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sellm.smart-layer")
public class SmartLayerProperties {
    private String baseUrl = "http://localhost:8090";
    private int timeoutSeconds = 30;
    // getters/setters
}
```

所有 agent 共用一个 Python 智能层地址,一处配置 `sellm.smart-layer.base-url` / `timeout-seconds`(走 `${SELLM_SMARTLAYER_URL}` / `${SELLM_SMARTLAYER_TIMEOUT}`)。qa 原有的 `topK` 不再是属性:改为 qa 的 ask 请求体参数或 qa 本地常量(qa 业务特有,不属共享传输层)。

### 3.5 AgentExceptionHandler(共享)

```java
package com.sellm.agentcommon;

import com.sellm.common.BusinessException;
import com.sellm.common.ErrorCode;
import com.sellm.common.Result;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AgentExceptionHandler {

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<Result<Void>> handleUnauthorized(UnauthorizedException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Result.error(ErrorCode.UNAUTHORIZED));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusiness(BusinessException e) {
        HttpStatus status = e.getErrorCode() == ErrorCode.ACCESS_DENIED ? HttpStatus.FORBIDDEN
            : e.getErrorCode() == ErrorCode.NOT_FOUND ? HttpStatus.NOT_FOUND
            : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(Result.error(e.getErrorCode()));
    }
}
```

### 3.6 AgentCommonAutoConfiguration(自动装配)

```java
package com.sellm.agentcommon;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;

/** 自动装配 Agent 脚手架:异常处理器 + 智能层属性。依赖 sellm-agent-common 的模块零样板获得。 */
@AutoConfiguration
@EnableConfigurationProperties(SmartLayerProperties.class)
public class AgentCommonAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(AgentExceptionHandler.class)
    public AgentExceptionHandler agentExceptionHandler() {
        return new AgentExceptionHandler();
    }
}
```

注册进 `sellm-agent-common/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`:
```
com.sellm.agentcommon.AgentCommonAutoConfiguration
```

与 core 的 Anonymizer/Storage 自动装配模式一致:agent 依赖 sellm-agent-common 即自动获得异常处理器(免 @ComponentScan)+ SmartLayerProperties bean。

## 4. 迁移(两个现存 agent,行为不变)

### 4.1 agent-qa
- 删除:`SmartLayerException`、`UnauthorizedException`、`SmartLayerProperties`、`QaExceptionHandler`(四个,改用 common 的)。
- `SmartLayerClient` 接口保留(qa 包,typed)。
- `HttpSmartLayerClient`:改 `extends AbstractHttpSmartLayerClient`,构造调 `super(props)`,`generate` 内用 `send("/v1/agents/qa/invoke", body)`;删自建 HttpClient。
- import 改为 `com.sellm.agentcommon.*`(SmartLayerException/UnauthorizedException/SmartLayerProperties);QaController/QaAppService 引用这些类的 import 同步改。
- `QaApplication`:去掉 `@EnableConfigurationProperties(SmartLayerProperties.class)`(自动装配接管;若 QaApplication 无其他 properties 则整个注解删除)。
- 配置键:`application.yml` 的 `sellm.qa.smart-layer.{base-url,timeout-seconds}` → `sellm.smart-layer.{base-url,timeout-seconds}`;`sellm.qa.smart-layer.top-k`(及环境变量 `SELLM_SMARTLAYER_TOPK`)从 qa 配置移除。qa 的 topK 改为 QaAppService 本地常量 `private static final int DEFAULT_TOP_K = 5;`,`QaAppService:77` 的 `smartLayerClient.generate(text, props.getTopK())` 改为 `generate(text, DEFAULT_TOP_K)`(QaAppService 不再注入 SmartLayerProperties 取 topK;若它仅为 topK 注入 props,则去掉该注入)。SmartLayerClient 接口的 `generate(question, topK)` 签名不变(topK 仍是入参,只是来源从属性变常量)。
- pom:加 `sellm-agent-common` 依赖。
- 测试:`StubSmartLayerClient implements SmartLayerClient`(qa 接口)不变;脱敏硬阻断测试的 @Primary 注入不变。QaSanitizeHardBlockTest / QaAskApiTest 全绿。

### 4.2 agent-teaching
- 删除:`SmartLayerException`、`UnauthorizedException`、`SmartLayerProperties`、`TeachingExceptionHandler`。
- `SmartLayerClient` 接口保留(teaching 包,typed)。
- `HttpSmartLayerClient`:改 extends 基类,`send("/v1/agents/teaching/invoke", body)`。
- import 改 `com.sellm.agentcommon.*`;TeachingAppService/Controllers 同步。
- `TeachingApplication`:去掉 `@EnableConfigurationProperties(SmartLayerProperties.class)`。
- 配置键:`sellm.teaching.smart-layer.*` → `sellm.smart-layer.*`。
- pom:加 `sellm-agent-common` 依赖。
- 测试:TeachingApiTest / TeachingSanitizeHardBlockTest / TeachingRepositoryTest 全绿。

### 4.3 .env.example
`SELLM_SMARTLAYER_URL` / `SELLM_SMARTLAYER_TIMEOUT` 已存在;说明改为「所有 agent 共用 `sellm.smart-layer.*`」。

## 5. 错误处理 / 红线

- 纯结构重构:不改任何业务逻辑、脱敏、状态机、鉴权语义。三红线不触碰。
- AgentExceptionHandler 行为与原 Qa/TeachingExceptionHandler 完全一致(401/403/404/400 映射不变)。
- AbstractHttpSmartLayerClient.send 的 2xx 检查 + SmartLayerException 包装与原 HttpSmartLayerClient 一致。

## 6. 测试策略

- sellm-agent-common 自带测试:`AbstractHttpSmartLayerClient` 子类化覆写 send 验证 typed 调用路径(或验证 send 的 2xx/非2xx→SmartLayerException);AgentExceptionHandler 可单测异常→状态映射(或经 agent 集成测试覆盖)。
- **回归是核心**:qa 16 测试 + teaching 15 测试**迁移后保持全绿**(行为不变的证明);backend 242 不受影响;全 reactor 10 模块 `clean install` SUCCESS。
- 每步 `mvn clean install`(stale target 假绿)。

## 7. 模块清单

```
sellm-agent-common/                       (NEW 模块)
├── pom.xml(parent sellm-parent;依赖 sellm-common-core + spring-boot-starter-web + spring-boot-autoconfigure)
└── src/main/
    ├── java/com/sellm/agentcommon/
    │   ├── SmartLayerException.java
    │   ├── UnauthorizedException.java
    │   ├── SmartLayerProperties.java
    │   ├── AbstractHttpSmartLayerClient.java
    │   ├── AgentExceptionHandler.java
    │   └── AgentCommonAutoConfiguration.java
    └── resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
根 pom.xml                                 (MODIFY: +module +dependencyManagement 条目)
sellm-agent-qa/                            (迁移:删4类、改client继承、改配置键、+依赖)
sellm-agent-teaching/                      (迁移:同上)
.env.example                               (MODIFY: 说明统一前缀)
```

## 8. 实现前明确(不阻塞)

- jackson(ObjectMapper)各 agent 子类仍各自用(来自 spring-boot-starter-web);基类不碰 JSON(只收发 String),保持传输层纯净。
- 反应堆模块数从 9 → 10(加 sellm-agent-common)。
- spring-boot-autoconfigure 依赖:core 已加(qa 阶段),agent-common 也需(为 @AutoConfiguration);版本走 BOM。
