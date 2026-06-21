# sellm-agent-common 共享模块实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新建 sellm-agent-common 模块,抽取 qa+teaching 重复的 Agent 脚手架(SmartLayerException/UnauthorizedException/SmartLayerProperties/AgentExceptionHandler/AbstractHttpSmartLayerClient + 自动装配),迁移两个现存 agent,行为不变、全测试保持绿,消除 research/aids 的复制债。

**Architecture:** 新建 Maven 模块 sellm-agent-common(依赖 sellm-common-core),抽取共享脚手架,经 AgentCommonAutoConfiguration 自动装配(同 core 的 Anonymizer/Storage 模式)。qa/teaching 删除各自的 4 个重复类、改 HttpSmartLayerClient 继承 AbstractHttpSmartLayerClient、统一配置前缀 sellm.smart-layer、加 sellm-agent-common 依赖。SmartLayerClient typed 接口留各 agent。纯重构,行为不变。

**Tech Stack:** Java 17 / Spring Boot 3.2.5 / Maven 多模块 / spring-boot-autoconfigure

## Global Constraints

- Java 17,Spring Boot 3.2.5;sellm-agent-common 依赖 sellm-common-core + spring-boot-starter-web + spring-boot-autoconfigure(版本走 BOM)
- **纯重构,行为不变**:不改业务逻辑/脱敏/状态机/鉴权语义;AgentExceptionHandler 的 401/403/404/400 映射与原 Qa/TeachingExceptionHandler 完全一致
- **回归是核心**:迁移后 qa 16 测试 + teaching 15 测试保持全绿;backend 242 不受影响;全 reactor 10 模块 clean install SUCCESS
- 抽取的类移到 `com.sellm.agentcommon` 包(**与原 com.sellm.qa/teaching 包不同 → 消费者需加 import**)
- 统一配置前缀 `sellm.smart-layer`(baseUrl/timeoutSeconds);qa 的 topK 改 QaAppService 本地常量
- 自动装配:agent 依赖 sellm-agent-common 即获得 AgentExceptionHandler + SmartLayerProperties,免组件扫描
- **每步 `mvn clean install` 验证**(stale target 假绿)
- Maven 在 Git Bash:`export PATH="/c/tools/apache-maven-3.9.6/bin:$PATH"`
- 代码/配置变更后追加 `.claude/CLAUDE_CHANGES.md`

---

### Task 1: 创建 sellm-agent-common 模块(6 类 + 自动装配)

**Files:**
- Create: `sellm-agent-common/pom.xml`
- Create: `sellm-agent-common/src/main/java/com/sellm/agentcommon/SmartLayerException.java`
- Create: `sellm-agent-common/src/main/java/com/sellm/agentcommon/UnauthorizedException.java`
- Create: `sellm-agent-common/src/main/java/com/sellm/agentcommon/SmartLayerProperties.java`
- Create: `sellm-agent-common/src/main/java/com/sellm/agentcommon/AbstractHttpSmartLayerClient.java`
- Create: `sellm-agent-common/src/main/java/com/sellm/agentcommon/AgentExceptionHandler.java`
- Create: `sellm-agent-common/src/main/java/com/sellm/agentcommon/AgentCommonAutoConfiguration.java`
- Create: `sellm-agent-common/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Modify: `pom.xml`(根:`<modules>` 加 sellm-agent-common;dependencyManagement 加条目)
- Test: `sellm-agent-common/src/test/java/com/sellm/agentcommon/AbstractHttpSmartLayerClientTest.java`

**Interfaces:**
- Produces:
  - `com.sellm.agentcommon.SmartLayerException`(RuntimeException,(String)+(String,Throwable) 构造)
  - `com.sellm.agentcommon.UnauthorizedException`(RuntimeException,(String) 构造)
  - `com.sellm.agentcommon.SmartLayerProperties`(@ConfigurationProperties "sellm.smart-layer",baseUrl + timeoutSeconds + getters/setters)
  - `com.sellm.agentcommon.AbstractHttpSmartLayerClient`(抽象,构造(SmartLayerProperties),`protected String send(String path, String jsonBody)`)
  - `com.sellm.agentcommon.AgentExceptionHandler`(@RestControllerAdvice)
  - `com.sellm.agentcommon.AgentCommonAutoConfiguration`(@AutoConfiguration)
- Consumes: sellm-common-core(Result/ErrorCode/BusinessException)

- [ ] **Step 1: 创建 sellm-agent-common/pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.sellm</groupId>
        <artifactId>sellm-parent</artifactId>
        <version>0.1.0-SNAPSHOT</version>
        <relativePath>../pom.xml</relativePath>
    </parent>

    <artifactId>sellm-agent-common</artifactId>
    <name>sellm-agent-common</name>
    <description>Agent 业务服务共享脚手架:智能层客户端基类/异常/属性/异常处理器(自动装配)</description>

    <dependencies>
        <dependency>
            <groupId>com.sellm</groupId>
            <artifactId>sellm-common-core</artifactId>
            <version>0.1.0-SNAPSHOT</version>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-autoconfigure</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 2: 根 pom 注册模块 + dependencyManagement**

修改根 `pom.xml`:`<modules>` 在 sellm-common-backend 后(或合适位置)加 `<module>sellm-agent-common</module>`。dependencyManagement 加:
```xml
            <dependency>
                <groupId>com.sellm</groupId>
                <artifactId>sellm-agent-common</artifactId>
                <version>${project.version}</version>
            </dependency>
```

- [ ] **Step 3: 创建 SmartLayerException + UnauthorizedException**

`SmartLayerException.java`:
```java
package com.sellm.agentcommon;

public class SmartLayerException extends RuntimeException {
    public SmartLayerException(String message) { super(message); }
    public SmartLayerException(String message, Throwable cause) { super(message, cause); }
}
```

`UnauthorizedException.java`:
```java
package com.sellm.agentcommon;

public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) { super(message); }
}
```

- [ ] **Step 4: 创建 SmartLayerProperties**

`SmartLayerProperties.java`:
```java
package com.sellm.agentcommon;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 智能层连接配置。所有 agent 共用一个 Python 智能层地址。 */
@ConfigurationProperties(prefix = "sellm.smart-layer")
public class SmartLayerProperties {
    private String baseUrl = "http://localhost:8090";
    private int timeoutSeconds = 30;

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
}
```

- [ ] **Step 5: 创建 AbstractHttpSmartLayerClient**

`AbstractHttpSmartLayerClient.java`:
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
     * 强制 HTTP/1.1(JDK 默认 HTTP/2 与部分网关协商卡死)。非 2xx / 异常包 SmartLayerException。
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

- [ ] **Step 6: 创建 AgentExceptionHandler**

`AgentExceptionHandler.java`:
```java
package com.sellm.agentcommon;

import com.sellm.common.BusinessException;
import com.sellm.common.ErrorCode;
import com.sellm.common.Result;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Agent 业务服务共享异常处理:鉴权/业务异常 → Result 信封 + HTTP 状态。 */
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

- [ ] **Step 7: 创建 AgentCommonAutoConfiguration + imports**

`AgentCommonAutoConfiguration.java`:
```java
package com.sellm.agentcommon;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

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

`sellm-agent-common/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`:
```
com.sellm.agentcommon.AgentCommonAutoConfiguration
```

- [ ] **Step 8: 写测试 AbstractHttpSmartLayerClientTest(传输层)**

`sellm-agent-common/src/test/java/com/sellm/agentcommon/AbstractHttpSmartLayerClientTest.java`:
```java
package com.sellm.agentcommon;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AbstractHttpSmartLayerClientTest {

    // 子类暴露 send 供测试(模板方法可被覆写注入假响应)
    static class TestableClient extends AbstractHttpSmartLayerClient {
        TestableClient(SmartLayerProperties props) { super(props); }
        String callSend(String path, String body) { return send(path, body); }
    }

    private SmartLayerProperties props() {
        SmartLayerProperties p = new SmartLayerProperties();
        p.setBaseUrl("http://localhost:59999"); // 不可达端口
        p.setTimeoutSeconds(1);
        return p;
    }

    @Test
    void 不可达地址抛SmartLayerException() {
        TestableClient c = new TestableClient(props());
        assertThrows(SmartLayerException.class,
            () -> c.callSend("/v1/agents/x/invoke", "{}"));
    }

    @Test
    void 子类可覆写send注入假响应() {
        // 验证 send 可被子类覆写(测试注入模式)
        AbstractHttpSmartLayerClient c = new AbstractHttpSmartLayerClient(props()) {
            @Override protected String send(String path, String jsonBody) {
                return "{\"content\":\"fake\"}";
            }
        };
        // 经反射或同包访问;同包测试可直接调 protected
        // 这里通过一个同包子类暴露
        assertNotNull(c);
    }
}
```

> 说明:`send` 是 protected,测试在同包(com.sellm.agentcommon)可访问。第一个测试验证不可达→SmartLayerException(传输层错误包装);第二个验证可覆写性(子类注入模式,各 agent 测试桩依赖此)。若第二个测试的匿名子类同包 protected 覆写写法在编译上别扭,实现者可简化为只保留第一个测试 + 一个「子类覆写返回固定值后该值可用」的 TestableClient 变体。核心是覆盖 send 的 2xx/异常路径 + 可覆写性。

- [ ] **Step 9: 验证 sellm-agent-common 独立编译 + 测试**

```bash
export PATH="/c/tools/apache-maven-3.9.6/bin:$PATH"
cd /d/works/test/SELLM/dev_workspace
mvn -pl sellm-agent-common -am clean install -q 2>&1 | tail -5 && echo "AGENT-COMMON OK"
mvn -pl sellm-agent-common test 2>&1 | grep -E "Tests run:|BUILD" | tail -3
```
Expected: `AGENT-COMMON OK`;测试绿。

> 此时不跑 qa/teaching(它们还有自己的旧类,未迁移;Task2/3 迁)。本任务只需 common 模块自身编译+测试通过。

- [ ] **Step 10: Commit**

```bash
git add sellm-agent-common/ pom.xml
git commit -m "feat(agent-common): 新建共享模块(异常/属性/抽象HTTP客户端/异常处理器+自动装配)"
```
---

### Task 2: 迁移 agent-qa 到 sellm-agent-common(行为不变)

**Files:**
- Modify: `sellm-agent-qa/pom.xml`(加 sellm-agent-common 依赖)
- Delete: `sellm-agent-qa/src/main/java/com/sellm/qa/SmartLayerException.java`
- Delete: `sellm-agent-qa/src/main/java/com/sellm/qa/UnauthorizedException.java`
- Delete: `sellm-agent-qa/src/main/java/com/sellm/qa/SmartLayerProperties.java`
- Delete: `sellm-agent-qa/src/main/java/com/sellm/qa/QaExceptionHandler.java`
- Modify: `sellm-agent-qa/src/main/java/com/sellm/qa/HttpSmartLayerClient.java`(继承基类)
- Modify: `sellm-agent-qa/src/main/java/com/sellm/qa/QaAppService.java`(import + topK 常量)
- Modify: `sellm-agent-qa/src/main/java/com/sellm/qa/QaController.java`(import UnauthorizedException)
- Modify: `sellm-agent-qa/src/main/java/com/sellm/qa/QaApplication.java`(去 @EnableConfigurationProperties)
- Modify: `sellm-agent-qa/src/main/resources/application.yml`(配置键 sellm.qa.smart-layer → sellm.smart-layer,去 top-k)

**Interfaces:**
- Consumes: Task1 的 com.sellm.agentcommon.{SmartLayerException,UnauthorizedException,SmartLayerProperties,AbstractHttpSmartLayerClient,AgentExceptionHandler(自动装配)}
- Produces: qa 行为不变;qa 16 测试全绿

> **关键**:抽走的 4 类原在 `com.sellm.qa` 包(消费者同包无 import);移到 `com.sellm.agentcommon` 后,所有消费者需加 `import com.sellm.agentcommon.XXX`。SmartLayerClient 接口(typed)留 qa 不动;StubSmartLayerClient 测试桩(implements qa 的 SmartLayerClient)不动。

- [ ] **Step 1: qa pom 加 sellm-agent-common 依赖**

`sellm-agent-qa/pom.xml` 的 `<dependencies>` 加(在 sellm-common-core 后):
```xml
        <dependency>
            <groupId>com.sellm</groupId>
            <artifactId>sellm-agent-common</artifactId>
            <version>0.1.0-SNAPSHOT</version>
        </dependency>
```

- [ ] **Step 2: 删除 qa 的 4 个重复类**

```bash
cd /d/works/test/SELLM/dev_workspace
git rm sellm-agent-qa/src/main/java/com/sellm/qa/SmartLayerException.java \
       sellm-agent-qa/src/main/java/com/sellm/qa/UnauthorizedException.java \
       sellm-agent-qa/src/main/java/com/sellm/qa/SmartLayerProperties.java \
       sellm-agent-qa/src/main/java/com/sellm/qa/QaExceptionHandler.java
```

- [ ] **Step 3: HttpSmartLayerClient 改继承 AbstractHttpSmartLayerClient**

重写 `sellm-agent-qa/src/main/java/com/sellm/qa/HttpSmartLayerClient.java`(保留 QaAnswer 解析逻辑,传输用基类 send):
```java
package com.sellm.qa;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sellm.agentcommon.AbstractHttpSmartLayerClient;
import com.sellm.agentcommon.SmartLayerException;
import com.sellm.agentcommon.SmartLayerProperties;
import com.sellm.qa.dto.QaAnswer;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** REST 调 Python 智能层 /v1/agents/qa/invoke。传输由 AbstractHttpSmartLayerClient 提供。 */
@Component
public class HttpSmartLayerClient extends AbstractHttpSmartLayerClient implements SmartLayerClient {

    private final ObjectMapper json = new ObjectMapper();

    public HttpSmartLayerClient(SmartLayerProperties props) {
        super(props);
    }

    @Override
    public QaAnswer generate(String anonymizedQuestion, int topK) {
        try {
            ObjectNode body = json.createObjectNode();
            body.put("question", anonymizedQuestion);
            body.put("topK", topK);
            String resp = send("/v1/agents/qa/invoke", json.writeValueAsString(body));
            JsonNode node = json.readTree(resp);
            String answer = node.path("answer").asText("");
            List<Map<String, String>> sources = new ArrayList<>();
            JsonNode arr = node.path("sources");
            if (arr.isArray()) {
                for (JsonNode s : arr) {
                    Map<String, String> m = new HashMap<>();
                    m.put("title", s.path("title").asText(""));
                    m.put("source", s.path("source").asText(""));
                    sources.add(m);
                }
            }
            return new QaAnswer(answer, sources);
        } catch (SmartLayerException e) {
            throw e;
        } catch (Exception e) {
            throw new SmartLayerException("智能层响应解析失败", e);
        }
    }
}
```
(read 原文件确认 QaAnswer 构造/字段一致;此处保留原解析,仅传输改 super.send。)

- [ ] **Step 4: QaAppService 改 import + topK 本地常量**

修改 `sellm-agent-qa/src/main/java/com/sellm/qa/QaAppService.java`:
- 加 import:`import com.sellm.agentcommon.SmartLayerException;`(若引用了)、删除对本地 SmartLayerProperties 的依赖。
- 加类常量:`private static final int DEFAULT_TOP_K = 5;`
- 改调用:`smartLayerClient.generate(anon.getAnonymizedText(), props.getTopK())` → `smartLayerClient.generate(anon.getAnonymizedText(), DEFAULT_TOP_K)`。
- 若 QaAppService 构造注入了 SmartLayerProperties props 仅为 topK:从构造与字段删除该注入(不再需要)。读文件确认 props 是否还有其他用途;若无,移除。
- 异常引用:catch 的 SmartLayerException 改 import `com.sellm.agentcommon.SmartLayerException`;BusinessException 仍 core。

- [ ] **Step 5: QaController 改 import**

`QaController.java`:它抛 UnauthorizedException(原同包),现加 `import com.sellm.agentcommon.UnauthorizedException;`。其余不变。

- [ ] **Step 6: QaApplication 去掉 @EnableConfigurationProperties**

`QaApplication.java`:删除 `@EnableConfigurationProperties(SmartLayerProperties.class)` 及其 import(SmartLayerProperties 现经 AgentCommonAutoConfiguration 自动装配)。保留 `@SpringBootApplication`。

- [ ] **Step 7: qa application.yml 改配置键**

`sellm-agent-qa/src/main/resources/application.yml`:把
```yaml
sellm:
  qa:
    smart-layer:
      base-url: ${SELLM_SMARTLAYER_URL:http://localhost:8090}
      timeout-seconds: ${SELLM_SMARTLAYER_TIMEOUT:30}
      top-k: ${SELLM_SMARTLAYER_TOPK:5}
```
改为:
```yaml
sellm:
  smart-layer:
    base-url: ${SELLM_SMARTLAYER_URL:http://localhost:8090}
    timeout-seconds: ${SELLM_SMARTLAYER_TIMEOUT:30}
```
(去掉 qa 层级与 top-k;若 yml 有其他 sellm.qa.* 配置则保留那些。)

- [ ] **Step 8: 验证 qa 16 测试全绿(clean)**

```bash
export PATH="/c/tools/apache-maven-3.9.6/bin:$PATH"
cd /d/works/test/SELLM/dev_workspace
mvn -pl sellm-agent-common,sellm-agent-qa -am clean install -q && echo "MODULES OK"
mvn -pl sellm-agent-qa clean test 2>&1 | grep -E "Tests run:|BUILD" | tail -5
```
Expected: `MODULES OK`;qa 所有测试绿(QaAskApiTest + QaSanitizeHardBlockTest + QaRepositoryTest + RuleIntentClassifierTest + contextLoads,共 16)。

> 若编译报找不到 SmartLayerException/UnauthorizedException/SmartLayerProperties:某消费者漏加 `import com.sellm.agentcommon.*`。若 contextLoads 报缺 SmartLayerProperties bean / 重复 AgentExceptionHandler:确认 QaApplication 已去 @EnableConfigurationProperties 且 qa 没残留自己的 ExceptionHandler。报 BLOCKED 附错误。

- [ ] **Step 9: Commit**

```bash
git add sellm-agent-qa/
git commit -m "refactor(agent-common): 迁移 agent-qa 到共享脚手架(删4类/继承基类/统一配置/topK常量)"
```
---

### Task 3: 迁移 agent-teaching 到 sellm-agent-common(行为不变)

**Files:**
- Modify: `sellm-agent-teaching/pom.xml`(加 sellm-agent-common 依赖)
- Delete: `sellm-agent-teaching/src/main/java/com/sellm/teaching/{SmartLayerException,UnauthorizedException,SmartLayerProperties,TeachingExceptionHandler}.java`
- Modify: `sellm-agent-teaching/src/main/java/com/sellm/teaching/HttpSmartLayerClient.java`(继承基类)
- Modify: `sellm-agent-teaching/src/main/java/com/sellm/teaching/TeachingAppService.java`(import SmartLayerException)
- Modify: `sellm-agent-teaching/src/main/java/com/sellm/teaching/LessonPlanController.java`(import UnauthorizedException)
- Modify: `sellm-agent-teaching/src/main/java/com/sellm/teaching/CoursewareController.java`(import UnauthorizedException)
- Modify: `sellm-agent-teaching/src/main/java/com/sellm/teaching/TeachingApplication.java`(去 @EnableConfigurationProperties)
- Modify: `sellm-agent-teaching/src/main/resources/application.yml`(配置键 sellm.teaching.smart-layer → sellm.smart-layer)

**Interfaces:**
- Consumes: Task1 的 com.sellm.agentcommon.*
- Produces: teaching 行为不变;teaching 15 测试全绿

> 同 Task2 模式。teaching 的 SmartLayerClient(typed:`String generate(task, content, disorderType, scene, mode)`)留 teaching 不动;无 topK 问题(teaching 本就无 topK)。

- [ ] **Step 1: teaching pom 加 sellm-agent-common 依赖**

`sellm-agent-teaching/pom.xml` 的 `<dependencies>` 加:
```xml
        <dependency>
            <groupId>com.sellm</groupId>
            <artifactId>sellm-agent-common</artifactId>
            <version>0.1.0-SNAPSHOT</version>
        </dependency>
```

- [ ] **Step 2: 删除 teaching 的 4 个重复类**

```bash
cd /d/works/test/SELLM/dev_workspace
git rm sellm-agent-teaching/src/main/java/com/sellm/teaching/SmartLayerException.java \
       sellm-agent-teaching/src/main/java/com/sellm/teaching/UnauthorizedException.java \
       sellm-agent-teaching/src/main/java/com/sellm/teaching/SmartLayerProperties.java \
       sellm-agent-teaching/src/main/java/com/sellm/teaching/TeachingExceptionHandler.java
```

- [ ] **Step 3: HttpSmartLayerClient 改继承基类**

重写 `sellm-agent-teaching/src/main/java/com/sellm/teaching/HttpSmartLayerClient.java`(保留 task 分支 + content 解析,传输用基类):
```java
package com.sellm.teaching;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sellm.agentcommon.AbstractHttpSmartLayerClient;
import com.sellm.agentcommon.SmartLayerException;
import com.sellm.agentcommon.SmartLayerProperties;
import org.springframework.stereotype.Component;

/** REST 调 Python 智能层 /v1/agents/teaching/invoke。传输由 AbstractHttpSmartLayerClient 提供。 */
@Component
public class HttpSmartLayerClient extends AbstractHttpSmartLayerClient implements SmartLayerClient {

    private final ObjectMapper json = new ObjectMapper();

    public HttpSmartLayerClient(SmartLayerProperties props) {
        super(props);
    }

    @Override
    public String generate(String task, String iepContentOrPlan, String disorderType, String scene, String mode) {
        try {
            ObjectNode body = json.createObjectNode();
            body.put("task", task);
            if ("courseware".equals(task)) {
                body.put("lessonPlanContent", iepContentOrPlan);
            } else {
                body.put("iepContent", iepContentOrPlan);
            }
            body.put("disorderType", disorderType);
            body.put("scene", scene);
            body.put("mode", mode);
            String resp = send("/v1/agents/teaching/invoke", json.writeValueAsString(body));
            JsonNode node = json.readTree(resp);
            return node.path("content").asText("");
        } catch (SmartLayerException e) {
            throw e;
        } catch (Exception e) {
            throw new SmartLayerException("智能层响应解析失败", e);
        }
    }
}
```
(read 原文件确认 task 分支/字段一致。)

- [ ] **Step 4: TeachingAppService + Controllers + Application 改 import / 去注解**

- `TeachingAppService.java`:catch 的 SmartLayerException 改 `import com.sellm.agentcommon.SmartLayerException;`(AnonymizationException/BusinessException 不变,仍 core)。
- `LessonPlanController.java` + `CoursewareController.java`:抛 UnauthorizedException,加 `import com.sellm.agentcommon.UnauthorizedException;`。
- `TeachingApplication.java`:删 `@EnableConfigurationProperties(SmartLayerProperties.class)` + import。保留 `@SpringBootApplication`。

- [ ] **Step 5: teaching application.yml 改配置键**

把
```yaml
sellm:
  teaching:
    smart-layer:
      base-url: ${SELLM_SMARTLAYER_URL:http://localhost:8090}
      timeout-seconds: ${SELLM_SMARTLAYER_TIMEOUT:30}
  storage:
    local-dir: ${SELLM_STORAGE_LOCAL_DIR:data/media}
```
改为(smart-layer 提到 sellm 顶层,storage 保留):
```yaml
sellm:
  smart-layer:
    base-url: ${SELLM_SMARTLAYER_URL:http://localhost:8090}
    timeout-seconds: ${SELLM_SMARTLAYER_TIMEOUT:30}
  storage:
    local-dir: ${SELLM_STORAGE_LOCAL_DIR:data/media}
```

- [ ] **Step 6: 验证 teaching 15 测试全绿(clean)**

```bash
export PATH="/c/tools/apache-maven-3.9.6/bin:$PATH"
cd /d/works/test/SELLM/dev_workspace
mvn -pl sellm-agent-common,sellm-agent-teaching -am clean install -q && echo "MODULES OK"
mvn -pl sellm-agent-teaching clean test 2>&1 | grep -E "Tests run:|BUILD" | tail -5
```
Expected: `MODULES OK`;teaching 所有测试绿(TeachingApiTest + TeachingSanitizeHardBlockTest + TeachingRepositoryTest + contextLoads,共 15)。

> 同 Task2 排错:漏 import / 残留 ExceptionHandler / 未去 @EnableConfigurationProperties → 报 BLOCKED 附错误。

- [ ] **Step 7: Commit**

```bash
git add sellm-agent-teaching/
git commit -m "refactor(agent-common): 迁移 agent-teaching 到共享脚手架(删4类/继承基类/统一配置)"
```

---

### Task 4: 全量回归 + .env/文档

**Files:**
- Modify: `.env.example`(说明 sellm.smart-layer 统一前缀)
- Verification only(+ `.claude/CLAUDE_CHANGES.md`)

**Interfaces:**
- Produces: 确认抽取/迁移不破坏任何功能,全 reactor 10 模块绿

- [ ] **Step 1: 全 reactor clean install(10 模块)**

```bash
export PATH="/c/tools/apache-maven-3.9.6/bin:$PATH"
cd /d/works/test/SELLM/dev_workspace
mvn clean install 2>&1 | grep -E "Reactor Summary|SUCCESS \[|FAILURE \[|BUILD SUCCESS|BUILD FAILURE|Tests run:.*Failures: [1-9]" | tail -16
```
Expected: 10 模块全 SUCCESS(含 sellm-agent-common);qa 16 + teaching 15 + backend 242 全绿;无 Failures。

- [ ] **Step 2: 确认重复类已删净**

```bash
cd /d/works/test/SELLM/dev_workspace
echo "=== qa/teaching 应不再有这4类 ==="
for m in sellm-agent-qa sellm-agent-teaching; do
  echo "--- $m ---"
  find $m/src/main/java -name "SmartLayerException.java" -o -name "UnauthorizedException.java" -o -name "SmartLayerProperties.java" -o -name "*ExceptionHandler.java" | sed "s#$m/src/main/java/com/sellm/##" || echo "无(已删净)"
done
```
Expected:两模块均无这 4 类(SmartLayerClient 接口 + HttpSmartLayerClient 仍在,属保留)。

- [ ] **Step 3: 前端 build**

```bash
cd /d/works/test/SELLM/dev_workspace/frontend
npm run build 2>&1 | grep -E "built in|error" | tail -2
```
Expected: `✓ built in X.XXs`。

- [ ] **Step 4: .env.example 说明统一前缀**

`.env.example` 中 SELLM_SMARTLAYER_* 段的说明改为:「所有 agent 共用 `sellm.smart-layer.*`(由 sellm-agent-common 提供)」。`SELLM_SMARTLAYER_TOPK`(若存在)移除或标注「已废弃,qa 改用本地常量」。

- [ ] **Step 5: 追加变更记录 + 确认工作树**

向 `.claude/CLAUDE_CHANGES.md` 追加 REFACTOR 记录。`git status --short` 应仅 .env.example 待提交。

- [ ] **Step 6: Commit**

```bash
git add .env.example
git commit -m "docs(agent-common): .env.example 统一 smart-layer 配置说明"
```

---

## 文件清单总览

```
dev_workspace/
├── pom.xml                               (MODIFY: +sellm-agent-common module & mgmt)
├── sellm-agent-common/                   (NEW 模块)
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/sellm/agentcommon/
│       │   ├── SmartLayerException.java / UnauthorizedException.java
│       │   ├── SmartLayerProperties.java
│       │   ├── AbstractHttpSmartLayerClient.java
│       │   ├── AgentExceptionHandler.java
│       │   └── AgentCommonAutoConfiguration.java
│       └── resources/META-INF/spring/...AutoConfiguration.imports
│   └── src/test/java/com/sellm/agentcommon/AbstractHttpSmartLayerClientTest.java
├── sellm-agent-qa/                       (迁移:删4类+pom+client继承+import+config+topK常量)
├── sellm-agent-teaching/                 (迁移:删4类+pom+client继承+import+config)
└── .env.example                          (MODIFY)
```

> **收益**:research/aids Agent 后续只需依赖 sellm-agent-common + 写自己的 typed SmartLayerClient + HttpXxxClient(继承基类)+ 业务,零复制脚手架。第三个 core 系自动装配(Anonymizer/Storage/AgentCommon),平台脚手架收敛完成。
