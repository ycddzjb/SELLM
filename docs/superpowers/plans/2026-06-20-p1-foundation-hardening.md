# P1 平台地基硬化实现计划(底座抽离 + Nacos + 网关鉴权)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将平台底座共享能力(common/security/aigateway/anonymizer/rag/storage 共 38 个类)从 backend 单体抽离到 sellm-common 模块、接入 Nacos 服务发现、为 API 网关加 JWT 鉴权过滤器,且 backend 现有 237 项测试每批抽完后保持全绿。

**Architecture:** 底座抽离采用「同包名换坐标」策略——抽离的类保持 `com.sellm.*` 包名不变,只是物理移动到 sellm-common 模块的 jar,backend 加一条 `sellm-common` 依赖后 import 一行不用改(`@SpringBootApplication` 扫 `com.sellm.**`、`@MapperScan(basePackages="com.sellm")` 跨 jar 仍生效)。按依赖顺序分 4 批抽,每批后跑 `./mvnw test`。2 处轻耦合先小重构解耦(AccessGuard→ChildSubject 接口;GlobalExceptionHandler 拆出 handleScoring)。Nacos discovery 客户端接入各服务但 test profile 禁用;网关加 JWT 过滤器校验签名后注入用户上下文头。

**Tech Stack:** Java 17 / Spring Boot 3.2.5 / Maven 多模块 / Spring Cloud Alibaba Nacos 2023.0.1.0 / Spring Cloud Gateway / jjwt 0.12.6 / MyBatis

## Global Constraints

- Java 17,Spring Boot 3.2.5,spring-cloud 2023.0.1,spring-cloud-alibaba 2023.0.1.0,jjwt 0.12.6
- **每批抽离后 backend `./mvnw test` 必须 237/237 全绿**;不删改现有测试
- 抽离的类**保持 `com.sellm.*` 原包名不变**(只换 Maven 坐标),使 backend import 零改动
- Maven 在 Git Bash 需 `export PATH="/c/tools/apache-maven-3.9.6/bin:$PATH"`
- Nacos/鉴权接入**不破坏测试**:test profile 必须 `spring.cloud.nacos.discovery.enabled=false` + `spring.cloud.discovery.enabled=false`
- 三条 AI/隐私红线不触碰(本阶段是结构重构,不改业务逻辑)
- 依赖锁定版本;配置走 `${ENV:default}`
- 代码/配置变更后追加 `.claude/CLAUDE_CHANGES.md`
- 提交前确认 `git status` 不含 node_modules/dist/target

---

### Task 1: sellm-common pom 补依赖 + 抽离第 1 批(零耦合:common 工具类 + anonymizer + storage)

**Files:**
- Modify: `sellm-common/pom.xml`(加 mybatis/minio/jdbc 依赖 + 描述更新)
- Move: `backend/src/main/java/com/sellm/common/{Result,ErrorCode,BusinessException,DisorderType,LogType,Relationship}.java` → `sellm-common/src/main/java/com/sellm/common/`
- Move: `backend/src/main/java/com/sellm/common/crypto/{FieldCipher,AesFieldCipher}.java` → `sellm-common/src/main/java/com/sellm/common/crypto/`
- Move: `backend/src/main/java/com/sellm/anonymizer/*.java`(4 个)→ `sellm-common/src/main/java/com/sellm/anonymizer/`
- Move: `backend/src/main/java/com/sellm/storage/*.java`(5 个)→ `sellm-common/src/main/java/com/sellm/storage/`
- Modify: `backend/pom.xml`(加 sellm-common 依赖)
- Note: **不动** `backend/.../common/GlobalExceptionHandler.java`(第 3 批处理,有 ScoringException 耦合)

**Interfaces:**
- Produces: sellm-common 导出 `com.sellm.common.Result`, `ErrorCode`, `BusinessException`, `DisorderType`, `LogType`, `Relationship`, `com.sellm.common.crypto.{FieldCipher,AesFieldCipher}`, `com.sellm.anonymizer.{Anonymizer,AnonymizationResult,AnonymizationException,RegexAnonymizer}`, `com.sellm.storage.{ObjectStorage,StorageProperties,StorageConfig,NoopObjectStorage,MinioObjectStorage}`,包名全部不变
- Consumes: P0 的 sellm-common 模块(已有 event 包)

- [ ] **Step 1: 给 sellm-common 补齐依赖**

`sellm-common/pom.xml` 的 `<dependencies>` 加入(storage 用 minio,rag 第 2 批用 mybatis,crypto 用 spring `@Value` 已含在 web):
```xml
        <dependency>
            <groupId>org.mybatis.spring.boot</groupId>
            <artifactId>mybatis-spring-boot-starter</artifactId>
            <version>${mybatis-starter.version}</version>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-jdbc</artifactId>
        </dependency>
        <dependency>
            <groupId>io.minio</groupId>
            <artifactId>minio</artifactId>
            <version>${minio.version}</version>
        </dependency>
```
并把 `<description>` 改为 `共享能力层(平台底座:返回封装/异常/加密/脱敏/存储/AI网关/RAG/安全)`。

- [ ] **Step 2: 物理移动第 1 批文件(保持包名)**

用 git mv 保留历史。在 worktree 根执行:
```bash
# common 工具类(不含 GlobalExceptionHandler)
mkdir -p sellm-common/src/main/java/com/sellm/common/crypto
git mv backend/src/main/java/com/sellm/common/Result.java sellm-common/src/main/java/com/sellm/common/Result.java
git mv backend/src/main/java/com/sellm/common/ErrorCode.java sellm-common/src/main/java/com/sellm/common/ErrorCode.java
git mv backend/src/main/java/com/sellm/common/BusinessException.java sellm-common/src/main/java/com/sellm/common/BusinessException.java
git mv backend/src/main/java/com/sellm/common/DisorderType.java sellm-common/src/main/java/com/sellm/common/DisorderType.java
git mv backend/src/main/java/com/sellm/common/LogType.java sellm-common/src/main/java/com/sellm/common/LogType.java
git mv backend/src/main/java/com/sellm/common/Relationship.java sellm-common/src/main/java/com/sellm/common/Relationship.java
git mv backend/src/main/java/com/sellm/common/crypto/FieldCipher.java sellm-common/src/main/java/com/sellm/common/crypto/FieldCipher.java
git mv backend/src/main/java/com/sellm/common/crypto/AesFieldCipher.java sellm-common/src/main/java/com/sellm/common/crypto/AesFieldCipher.java
# anonymizer 全部
mkdir -p sellm-common/src/main/java/com/sellm/anonymizer
git mv backend/src/main/java/com/sellm/anonymizer/*.java sellm-common/src/main/java/com/sellm/anonymizer/
# storage 全部
mkdir -p sellm-common/src/main/java/com/sellm/storage
git mv backend/src/main/java/com/sellm/storage/*.java sellm-common/src/main/java/com/sellm/storage/
```
(包声明 `package com.sellm.common;` 等不变,无需改文件内容。)

- [ ] **Step 3: backend 加 sellm-common 依赖**

`backend/pom.xml` 的 `<dependencies>` 顶部加入:
```xml
        <dependency>
            <groupId>com.sellm</groupId>
            <artifactId>sellm-common</artifactId>
            <version>0.1.0-SNAPSHOT</version>
        </dependency>
```

- [ ] **Step 4: 验证 reactor + backend 测试全绿**

```bash
export PATH="/c/tools/apache-maven-3.9.6/bin:$PATH"
cd /d/works/test/SELLM/dev_workspace
mvn -pl sellm-common -am clean install -q && echo "COMMON OK"
mvn -pl backend test 2>&1 | grep -E "Tests run:|BUILD" | tail -3
```
Expected: `COMMON OK`;backend `Tests run: 237, Failures: 0, Errors: 0` + `BUILD SUCCESS`。

> 若出现 H2 "database has been closed" 偶发瞬时错误(项目已知 flake),单独重跑该测试类确认。

- [ ] **Step 5: Commit**

```bash
git add sellm-common/ backend/pom.xml backend/src/main/java/com/sellm/
git commit -m "refactor(p1): 抽离第1批底座到 sellm-common(common工具类/anonymizer/storage,同包名)"
```

---

### Task 2: 抽离第 2 批(aigateway + rag,含 MyBatis XML 迁移)

**Files:**
- Move: `backend/src/main/java/com/sellm/aigateway/*.java`(9 个)→ `sellm-common/src/main/java/com/sellm/aigateway/`
- Move: `backend/src/main/java/com/sellm/rag/*.java`(4 个)→ `sellm-common/src/main/java/com/sellm/rag/`
- Move: `backend/src/main/resources/mybatis/KnowledgeDocMapper.xml` → `sellm-common/src/main/resources/mybatis/KnowledgeDocMapper.xml`

**Interfaces:**
- Consumes: 第 1 批的 `com.sellm.anonymizer.*`(DefaultAiGateway 依赖 Anonymizer)
- Produces: `com.sellm.aigateway.{AiGateway,DefaultAiGateway,AiModel,MockAiModel,OpenAiCompatibleModel,AiProperties,AiModelConfig,PromptRequest,AiGatewayException}`, `com.sellm.rag.{RagRetriever,KnowledgeDoc,KnowledgeDocMapper,DbRagRetriever}`,包名不变

> **MyBatis 关键点**:backend 的 `application.yml` 有 `mybatis.mapper-locations: classpath:mybatis/*.xml`。KnowledgeDocMapper.xml 移到 sellm-common 的 `src/main/resources/mybatis/` 后仍在 classpath 的 `mybatis/` 目录(jar 合并),`classpath:mybatis/*.xml` 仍能扫到。`@MapperScan(basePackages="com.sellm")` 跨 jar 扫描接口也仍生效。

- [ ] **Step 1: 移动 aigateway + rag java 文件**

```bash
cd /d/works/test/SELLM/dev_workspace
mkdir -p sellm-common/src/main/java/com/sellm/aigateway
git mv backend/src/main/java/com/sellm/aigateway/*.java sellm-common/src/main/java/com/sellm/aigateway/
mkdir -p sellm-common/src/main/java/com/sellm/rag
git mv backend/src/main/java/com/sellm/rag/*.java sellm-common/src/main/java/com/sellm/rag/
```

- [ ] **Step 2: 移动 KnowledgeDocMapper.xml**

```bash
mkdir -p sellm-common/src/main/resources/mybatis
git mv backend/src/main/resources/mybatis/KnowledgeDocMapper.xml sellm-common/src/main/resources/mybatis/KnowledgeDocMapper.xml
```

- [ ] **Step 3: 验证(关注 RAG/AI 相关测试 + 全量)**

```bash
export PATH="/c/tools/apache-maven-3.9.6/bin:$PATH"
cd /d/works/test/SELLM/dev_workspace
mvn -pl sellm-common -am clean install -q && echo "COMMON OK"
mvn -pl backend test 2>&1 | grep -E "Tests run:|BUILD" | tail -3
```
Expected: `COMMON OK`;`Tests run: 237, Failures: 0, Errors: 0` + `BUILD SUCCESS`。

> 若 RAG 相关测试报 "Invalid bound statement" 或 mapper 找不到,说明 XML 未被扫到——检查 XML 是否在 `sellm-common/src/main/resources/mybatis/` 且 namespace 指向 `com.sellm.rag.KnowledgeDocMapper`。报 BLOCKED 并附错误。

- [ ] **Step 4: Commit**

```bash
git add sellm-common/ backend/src/main/java/com/sellm/ backend/src/main/resources/mybatis/
git commit -m "refactor(p1): 抽离第2批底座到 sellm-common(aigateway/rag + KnowledgeDocMapper.xml)"
```

---

### Task 3: 解耦 2 处轻耦合(AccessGuard→ChildSubject 接口;GlobalExceptionHandler 拆 handleScoring)

**Files:**
- Create: `sellm-common/src/main/java/com/sellm/security/ChildSubject.java`(新接口)
- Modify: `backend/src/main/java/com/sellm/security/AccessGuard.java`(改用 ChildSubject,去掉 import Child)
- Modify: `backend/src/main/java/com/sellm/child/Child.java`(implements ChildSubject)
- Create: `backend/src/main/java/com/sellm/scale/ScaleExceptionHandler.java`(承接 handleScoring)
- Modify: `backend/src/main/java/com/sellm/common/GlobalExceptionHandler.java`(去掉 handleScoring)
- Test: 复用现有 AccessGuard 相关测试 + scale 评分异常测试(验证不回归)

**Interfaces:**
- Produces: `com.sellm.security.ChildSubject`(接口,含 `Long getOrgId()` + `Long getGuardianUserId()`);`AccessGuard.checkChildAccess(AuthPrincipal, ChildSubject)` 与 `canAccess(AuthPrincipal, ChildSubject)`(签名从 Child 改为 ChildSubject)
- Consumes: 第 1 批的 `com.sellm.common.{BusinessException,ErrorCode}`

> 此任务为 Task 4(抽 security)铺路:AccessGuard 必须先不依赖 child.Child 才能进 sellm-common。`ChildSubject` 接口先放 backend 的 security 包还是 sellm-common?放 sellm-common(security 子包),这样 Task 4 抽 AccessGuard 时接口已在位。Child(backend)implements 它,依赖方向 backend→common 正确。

- [ ] **Step 1: 在 sellm-common 创建 ChildSubject 接口**

`sellm-common/src/main/java/com/sellm/security/ChildSubject.java`:
```java
package com.sellm.security;

/**
 * 行级权限判定所需的儿童最小视图。
 * 解耦 AccessGuard 与 backend 的 Child 实体,使 AccessGuard 可下沉到 sellm-common。
 * backend 的 Child 实现此接口。
 */
public interface ChildSubject {
    Long getOrgId();
    Long getGuardianUserId();
}
```

- [ ] **Step 2: AccessGuard 改用 ChildSubject**

修改 `backend/src/main/java/com/sellm/security/AccessGuard.java`:删除 `import com.sellm.child.Child;`,把两处 `Child child` 形参类型改为 `ChildSubject child`(同包无需 import)。完整新内容:
```java
package com.sellm.security;

import com.sellm.common.BusinessException;
import com.sellm.common.ErrorCode;
import org.springframework.stereotype.Component;

@Component
public class AccessGuard {

    /** 判定 principal 能否访问该 child;不能则抛 ACCESS_DENIED(→403)。 */
    public void checkChildAccess(AuthPrincipal principal, ChildSubject child) {
        if (canAccess(principal, child)) {
            return;
        }
        throw new BusinessException(ErrorCode.ACCESS_DENIED, "无权访问该儿童档案");
    }

    public boolean canAccess(AuthPrincipal principal, ChildSubject child) {
        if (child == null) {
            return false;
        }
        switch (principal.getRole()) {
            case SUPER_ADMIN:
                return true;  // 超管跨机构,可访问任意 child
            case MANAGER:
            case TEACHER:
                return principal.getOrgId() != null
                    && principal.getOrgId().equals(child.getOrgId());
            case PARENT:
                return principal.getUserId() != null
                    && principal.getUserId().equals(child.getGuardianUserId());
            default:
                return false;
        }
    }
}
```

- [ ] **Step 3: Child 实现 ChildSubject**

修改 `backend/src/main/java/com/sellm/child/Child.java` 第 3 行,把 `public class Child {` 改为:
```java
public class Child implements com.sellm.security.ChildSubject {
```
(Child 已有 `public Long getOrgId()` 和 `public Long getGuardianUserId()`,自动满足接口,无需加方法。)

- [ ] **Step 4: 验证 AccessGuard 解耦后编译 + 测试**

```bash
export PATH="/c/tools/apache-maven-3.9.6/bin:$PATH"
cd /d/works/test/SELLM/dev_workspace
mvn -pl sellm-common -am install -q && echo "COMMON OK"
mvn -pl backend test 2>&1 | grep -E "Tests run:|BUILD" | tail -3
```
Expected: 237 全绿(AccessGuard 行为不变,只是形参类型放宽到接口)。

- [ ] **Step 5: 拆出 ScaleExceptionHandler**

创建 `backend/src/main/java/com/sellm/scale/ScaleExceptionHandler.java`:
```java
package com.sellm.scale;

import com.sellm.common.ErrorCode;
import com.sellm.common.Result;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * scale 模块专属异常处理(从 GlobalExceptionHandler 拆出,
 * 使 GlobalExceptionHandler 不依赖 scale.ScoringException,可下沉 sellm-common)。
 */
@RestControllerAdvice
public class ScaleExceptionHandler {

    @ExceptionHandler(ScoringException.class)
    public ResponseEntity<Result<Void>> handleScoring(ScoringException e) {
        return ResponseEntity.badRequest().body(Result.error(ErrorCode.INVALID_INPUT));
    }
}
```

- [ ] **Step 6: GlobalExceptionHandler 去掉 handleScoring**

修改 `backend/src/main/java/com/sellm/common/GlobalExceptionHandler.java`,删除 `handleScoring` 方法(第 23-26 行)。新内容:
```java
package com.sellm.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusiness(BusinessException e) {
        HttpStatus status = e.getErrorCode() == ErrorCode.ACCESS_DENIED
            ? HttpStatus.FORBIDDEN : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(Result.error(e.getErrorCode()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Result<Void>> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Result.error(ErrorCode.INVALID_INPUT));
    }
}
```

- [ ] **Step 7: 验证拆分后评分异常仍返回 400**

```bash
export PATH="/c/tools/apache-maven-3.9.6/bin:$PATH"
cd /d/works/test/SELLM/dev_workspace
mvn -pl backend test 2>&1 | grep -E "Tests run:|BUILD" | tail -3
```
Expected: 237 全绿(scale 评分异常测试仍命中 400,只是改由 ScaleExceptionHandler 处理)。若有针对 ScoringException→400 的测试,确认其仍通过。

- [ ] **Step 8: Commit**

```bash
git add sellm-common/ backend/src/main/java/com/sellm/
git commit -m "refactor(p1): 解耦 AccessGuard(ChildSubject接口)+ 拆 ScaleExceptionHandler,为抽 security 铺路"
```

---

### Task 4: 抽离第 4 批(security 全部 + GlobalExceptionHandler)

**Files:**
- Move: `backend/src/main/java/com/sellm/security/{AccessGuard,AuthPrincipal,CurrentUser,JwtAuthFilter,JwtService,Role,SecurityConfig}.java`(7 个)→ `sellm-common/src/main/java/com/sellm/security/`
- Move: `backend/src/main/java/com/sellm/common/GlobalExceptionHandler.java` → `sellm-common/src/main/java/com/sellm/common/GlobalExceptionHandler.java`

**Interfaces:**
- Consumes: 第 1 批 `com.sellm.common.*`;Task 3 的 `ChildSubject`(已在 sellm-common)
- Produces: `com.sellm.security.{AccessGuard,AuthPrincipal,CurrentUser,JwtAuthFilter,JwtService,Role,SecurityConfig}` + `com.sellm.common.GlobalExceptionHandler` 全在 sellm-common,包名不变

> security 抽离后 backend 的 SecurityConfig 等仍被 `@SpringBootApplication`(扫 `com.sellm.**`)拾取。注意 SecurityConfig 的 `requestMatchers` 路径是字符串,不依赖业务类型。JwtAuthFilter 不加载用户(无 UserRepository 依赖,依赖测绘已确认)。

- [ ] **Step 1: 移动 security + GlobalExceptionHandler**

```bash
cd /d/works/test/SELLM/dev_workspace
mkdir -p sellm-common/src/main/java/com/sellm/security
git mv backend/src/main/java/com/sellm/security/*.java sellm-common/src/main/java/com/sellm/security/
git mv backend/src/main/java/com/sellm/common/GlobalExceptionHandler.java sellm-common/src/main/java/com/sellm/common/GlobalExceptionHandler.java
```

- [ ] **Step 2: 验证全量测试(security 是高危区,重点看认证/权限测试)**

```bash
export PATH="/c/tools/apache-maven-3.9.6/bin:$PATH"
cd /d/works/test/SELLM/dev_workspace
mvn -pl sellm-common -am clean install -q && echo "COMMON OK"
mvn -pl backend test 2>&1 | grep -E "Tests run:|BUILD" | tail -3
```
Expected: `COMMON OK`;`Tests run: 237, Failures: 0, Errors: 0` + `BUILD SUCCESS`。

> 若认证/RBAC 测试失败(401/403 行为变化),很可能是 SecurityConfig 或 JwtAuthFilter 未被组件扫描拾取——确认它们在 `com.sellm.security` 包(`com.sellm` 根下)。报 BLOCKED 附失败测试名。

- [ ] **Step 3: 确认 backend 已无残留底座包**

```bash
cd /d/works/test/SELLM/dev_workspace
echo "=== backend 应只剩业务模块,security/anonymizer/storage/aigateway/rag 应为空或不存在 ==="
for m in security anonymizer storage aigateway rag; do
  n=$(find backend/src/main/java/com/sellm/$m -name '*.java' 2>/dev/null | wc -l)
  echo "$m: $n java files (期望 0)"
done
echo "=== common 应只剩(若有)非抽离类 ==="
find backend/src/main/java/com/sellm/common -name '*.java' 2>/dev/null
```
Expected: 5 个底座包均为 0;common 包下应为空(全部已抽)。若 common 目录空了,用 `rmdir` 清理空目录(git 不跟踪空目录,通常自动消失)。

- [ ] **Step 4: Commit**

```bash
git add sellm-common/ backend/src/main/java/com/sellm/
git commit -m "refactor(p1): 抽离第4批底座(security全部 + GlobalExceptionHandler),底座抽离完成"
```

---

### Task 5: Nacos 服务发现接入(各服务注册 + test profile 禁用)

**Files:**
- Modify: `pom.xml`(根 dependencyManagement 已有 spring-cloud-alibaba BOM,确认)
- Modify: `sellm-gateway/pom.xml`(加 nacos-discovery + loadbalancer)
- Modify: `sellm-agent-teaching/pom.xml`、`sellm-agent-research/pom.xml`、`sellm-agent-aids/pom.xml`、`sellm-agent-qa/pom.xml`(各加 nacos-discovery)
- Modify: 各 agent + gateway 的 `application.yml`(加 nacos server-addr,走环境变量)
- Create: 各 agent + gateway 的 `src/test/resources/application-test.yml` 或在主 yml 加 test 禁用(见下)
- Modify: `sellm-gateway/src/main/resources/application.yml`(路由 uri 改 `lb://`)

**Interfaces:**
- Produces: 各 Agent 启动注册到 Nacos(service name = `spring.application.name`);网关经 `lb://agent-xxx` 负载均衡路由
- Consumes: P0 的 5 个服务模块 + docker-compose 的 nacos

> **测试不依赖 Nacos 是硬约束**:加 discovery 客户端后,`@SpringBootTest` 默认会尝试连 Nacos 导致 contextLoads 挂。必须在 test 时禁用。做法:每个模块 `src/test/resources/application.yml` 设 `spring.cloud.nacos.discovery.enabled: false` + `spring.cloud.discovery.enabled: false` + `spring.cloud.service-registry.auto-registration.enabled: false`。

- [ ] **Step 1: 网关加 nacos-discovery + loadbalancer 依赖**

`sellm-gateway/pom.xml` 的 `<dependencies>` 加:
```xml
        <dependency>
            <groupId>com.alibaba.cloud</groupId>
            <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-loadbalancer</artifactId>
        </dependency>
```

- [ ] **Step 2: 4 个 agent 各加 nacos-discovery 依赖**

对 `sellm-agent-teaching/pom.xml`、`research`、`aids`、`qa` 各自 `<dependencies>` 加:
```xml
        <dependency>
            <groupId>com.alibaba.cloud</groupId>
            <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
        </dependency>
```

- [ ] **Step 3: 各服务主 application.yml 加 nacos 配置**

对 5 个服务(gateway + 4 agent)的 `src/main/resources/application.yml`,在 `spring:` 下加(以 teaching 为例,其余同):
```yaml
  cloud:
    nacos:
      discovery:
        server-addr: ${SELLM_NACOS_ADDR:127.0.0.1:8848}
```
(gateway 已有 `spring.cloud.gateway`,在同级 `spring.cloud` 下加 `nacos.discovery.server-addr`,不要覆盖 gateway 段。)

- [ ] **Step 4: 各服务 test 禁用 discovery**

为 5 个服务各创建 `src/test/resources/application.yml`(若已存在则合并):
```yaml
spring:
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

- [ ] **Step 5: 网关路由改 lb://**

修改 `sellm-gateway/src/main/resources/application.yml` 的 routes,把 4 个 agent 的 `uri: http://localhost:80xx` 改为 `lb://agent-xxx`(service name)。assessment 暂保留 `http://localhost:8080`(backend 尚未接 nacos,见备注)。改后:
```yaml
        - id: agent-teaching
          uri: lb://agent-teaching
          predicates:
            - Path=/api/teaching/**
        - id: agent-research
          uri: lb://agent-research
          predicates:
            - Path=/api/research/**
        - id: agent-aids
          uri: lb://agent-aids
          predicates:
            - Path=/api/aids/**
        - id: agent-qa
          uri: lb://agent-qa
          predicates:
            - Path=/api/qa/**
```
(assessment 路由 uri 保持 `http://localhost:8080` 不变——backend 单体接 Nacos 属后续,本任务范围只让新模块注册。在该路由上方加注释说明。)

- [ ] **Step 6: 验证全部模块 contextLoads(无 Nacos 运行也应通过)**

```bash
export PATH="/c/tools/apache-maven-3.9.6/bin:$PATH"
cd /d/works/test/SELLM/dev_workspace
mvn -pl sellm-gateway,sellm-agent-teaching,sellm-agent-research,sellm-agent-aids,sellm-agent-qa test 2>&1 | grep -E "Tests run:|BUILD|Building " | tail -20
```
Expected: 5 个模块 contextLoads 各 `Tests run: 1, Failures: 0`,`BUILD SUCCESS`。

> 若某模块 contextLoads 卡住或报连 Nacos 失败,说明 test 禁用未生效——检查该模块 `src/test/resources/application.yml` 路径与内容。报 BLOCKED。

- [ ] **Step 7: Commit**

```bash
git add sellm-gateway/ sellm-agent-teaching/ sellm-agent-research/ sellm-agent-aids/ sellm-agent-qa/
git commit -m "feat(p1): Nacos 服务发现接入(5服务注册 + 网关 lb 路由 + test 禁用 discovery)"
```

---

### Task 6: 网关 JWT 鉴权过滤器

**Files:**
- Create: `sellm-gateway/src/main/java/com/sellm/gateway/JwtAuthGatewayFilter.java`(全局过滤器)
- Create: `sellm-gateway/src/main/java/com/sellm/gateway/GatewayJwtProperties.java`(读 jwt secret + 白名单)
- Modify: `sellm-gateway/pom.xml`(加 jjwt 依赖)
- Modify: `sellm-gateway/src/main/resources/application.yml`(jwt secret + 白名单路径)
- Test: `sellm-gateway/src/test/java/com/sellm/gateway/JwtAuthGatewayFilterTest.java`

**Interfaces:**
- Produces: 网关全局过滤器——校验 `Authorization: Bearer <jwt>` 签名+过期,通过则注入 `X-User-Id`(claim `uid`)/`X-User-Name`(subject=username)/`X-User-Role`(claim `role`)/`X-Org-Id`(claim `org`)到下游请求头;白名单路径(/api/auth/**、/actuator/health)放行;无效/缺失 token 对非白名单路径返回 401。
- Consumes: P0 网关 + Task 5 的路由

> **设计**:网关用 WebFlux(reactive),过滤器实现 `GlobalFilter` + `Ordered`。JWT 解析复用 jjwt(与 backend JwtService 同库同 secret),但网关不依赖 sellm-common 的 JwtService(那是 servlet 风格);网关自带轻量解析。secret 走 `${SELLM_JWT_SECRET}` 同一密钥。**token claims 与 backend JwtService 一致**:subject=username、claim `role`/`uid`(userId)/`org`(orgId);网关按这些 key 取值注入。**不校验用户是否存在**(那是各服务的事),网关只验签+注入。

- [ ] **Step 1: 写失败测试(过滤器放行白名单、拦截无 token)**

`sellm-gateway/src/test/java/com/sellm/gateway/JwtAuthGatewayFilterTest.java`:
```java
package com.sellm.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.*;

class JwtAuthGatewayFilterTest {

    private final GatewayJwtProperties props = new GatewayJwtProperties();
    {
        props.setSecret("test-secret-key-at-least-32-bytes-long-xx");
        props.setWhitelist(java.util.List.of("/api/auth/", "/actuator/health"));
    }

    @Test
    void 白名单路径无token放行() {
        JwtAuthGatewayFilter filter = new JwtAuthGatewayFilter(props);
        MockServerHttpRequest req = MockServerHttpRequest.get("/api/auth/login").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(req);
        GatewayFilterChain chain = e -> Mono.empty();

        Mono<Void> result = filter.filter(exchange, chain);
        result.block();
        // 放行:状态未被设为 401
        assertNotEquals(401, exchange.getResponse().getStatusCode() == null ? 0
            : exchange.getResponse().getStatusCode().value());
    }

    @Test
    void 非白名单缺token返回401() {
        JwtAuthGatewayFilter filter = new JwtAuthGatewayFilter(props);
        MockServerHttpRequest req = MockServerHttpRequest.get("/api/teaching/lesson-plans").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(req);
        GatewayFilterChain chain = e -> Mono.empty();

        filter.filter(exchange, chain).block();
        assertEquals(401, exchange.getResponse().getStatusCode().value());
    }
}
```

- [ ] **Step 2: 加 jjwt 依赖到网关**

`sellm-gateway/pom.xml` 的 `<dependencies>` 加:
```xml
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-api</artifactId>
            <version>${jjwt.version}</version>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-impl</artifactId>
            <version>${jjwt.version}</version>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-jackson</artifactId>
            <version>${jjwt.version}</version>
            <scope>runtime</scope>
        </dependency>
```

- [ ] **Step 3: 运行测试确认失败**

```bash
export PATH="/c/tools/apache-maven-3.9.6/bin:$PATH"
cd /d/works/test/SELLM/dev_workspace
mvn -pl sellm-gateway test -Dtest=JwtAuthGatewayFilterTest 2>&1 | grep -E "ERROR|cannot find symbol|Tests run|BUILD" | tail -10
```
Expected: 编译失败(JwtAuthGatewayFilter/GatewayJwtProperties 未定义)。

- [ ] **Step 4: 实现 GatewayJwtProperties**

`sellm-gateway/src/main/java/com/sellm/gateway/GatewayJwtProperties.java`:
```java
package com.sellm.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.List;

@ConfigurationProperties(prefix = "sellm.gateway.jwt")
public class GatewayJwtProperties {
    /** JWT 签名密钥(与各服务同一密钥)。 */
    private String secret = "";
    /** 免鉴权白名单路径前缀。 */
    private List<String> whitelist = List.of("/api/auth/", "/actuator/health");

    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }
    public List<String> getWhitelist() { return whitelist; }
    public void setWhitelist(List<String> whitelist) { this.whitelist = whitelist; }
}
```

- [ ] **Step 5: 实现 JwtAuthGatewayFilter**

`sellm-gateway/src/main/java/com/sellm/gateway/JwtAuthGatewayFilter.java`:
```java
package com.sellm.gateway;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

@Component
public class JwtAuthGatewayFilter implements GlobalFilter, Ordered {

    private final GatewayJwtProperties props;

    public JwtAuthGatewayFilter(GatewayJwtProperties props) {
        this.props = props;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (isWhitelisted(path)) {
            return chain.filter(exchange);
        }

        String auth = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            return unauthorized(exchange);
        }
        String token = auth.substring(7);

        try {
            SecretKey key = Keys.hmacShaKeyFor(props.getSecret().getBytes(StandardCharsets.UTF_8));
            Claims claims = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload();

            ServerHttpRequest mutated = exchange.getRequest().mutate()
                    .header("X-User-Id", claims.get("uid") == null ? "" : String.valueOf(claims.get("uid")))
                    .header("X-User-Name", claims.getSubject() == null ? "" : claims.getSubject())
                    .header("X-User-Role", claims.get("role") == null ? "" : String.valueOf(claims.get("role")))
                    .header("X-Org-Id", claims.get("org") == null ? "" : String.valueOf(claims.get("org")))
                    .build();
            return chain.filter(exchange.mutate().request(mutated).build());
        } catch (Exception e) {
            return unauthorized(exchange);
        }
    }

    private boolean isWhitelisted(String path) {
        return props.getWhitelist().stream().anyMatch(path::startsWith);
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    @Override
    public int getOrder() {
        return -100; // 在路由转发前执行
    }
}
```

- [ ] **Step 6: 启用配置属性 + 注入 secret**

修改 `sellm-gateway/src/main/java/com/sellm/gateway/GatewayApplication.java`,加 `@EnableConfigurationProperties`:
```java
package com.sellm.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(GatewayJwtProperties.class)
public class GatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
```

`sellm-gateway/src/main/resources/application.yml` 加(在 `spring:` 同级或末尾 `sellm:` 段):
```yaml
sellm:
  gateway:
    jwt:
      secret: ${SELLM_JWT_SECRET:dev-secret-key-please-change-in-prod-32b}
      whitelist:
        - /api/auth/
        - /actuator/health
```
并把 TODO(P1) 注释中"JWT 鉴权过滤器尚未接入"更新为"JWT 鉴权已接入;限流过滤器待 P2"。

- [ ] **Step 7: 运行测试确认通过**

```bash
export PATH="/c/tools/apache-maven-3.9.6/bin:$PATH"
cd /d/works/test/SELLM/dev_workspace
mvn -pl sellm-gateway test 2>&1 | grep -E "Tests run:|BUILD" | tail -5
```
Expected: `JwtAuthGatewayFilterTest` 2 个测试通过 + contextLoads 通过,`BUILD SUCCESS`。

> 若 contextLoads 失败(GatewayJwtProperties bean 未注册),确认 `@EnableConfigurationProperties` 已加。

- [ ] **Step 8: Commit**

```bash
git add sellm-gateway/
git commit -m "feat(p1): 网关 JWT 鉴权全局过滤器(验签+注入用户上下文头+白名单放行)"
```

---

### Task 7: P1 全量回归 + .env.example/文档更新

**Files:**
- Modify: `.env.example`(确认 nacos/jwt 配置齐全;补网关 jwt 白名单说明)
- Verification only(无新代码)

**Interfaces:**
- Produces: 确认 P1 三件硬化不破坏任何现有功能

- [ ] **Step 1: 后端全量测试回归**

```bash
export PATH="/c/tools/apache-maven-3.9.6/bin:$PATH"
cd /d/works/test/SELLM/dev_workspace
mvn -pl backend test 2>&1 | grep -E "Tests run:|BUILD" | tail -3
```
Expected: `Tests run: 237, Failures: 0, Errors: 0` + `BUILD SUCCESS`。

- [ ] **Step 2: 全 reactor clean install(所有模块 + 测试)**

```bash
export PATH="/c/tools/apache-maven-3.9.6/bin:$PATH"
cd /d/works/test/SELLM/dev_workspace
mvn clean install 2>&1 | grep -E "BUILD|Tests run:.*Failures|Reactor Summary" | tail -15
```
Expected: 全部模块 `BUILD SUCCESS`;无 Failures/Errors。

- [ ] **Step 3: 前端 build 验证(确认未受影响)**

```bash
cd /d/works/test/SELLM/dev_workspace/frontend
npm run build 2>&1 | grep -E "built in|error" | tail -3
```
Expected: `✓ built in X.XXs`。

- [ ] **Step 4: 确认 .env.example 配置齐全**

确认 `.env.example` 已含(P0 已加 Nacos/RabbitMQ/Redis/Milvus;本步确认 + 补网关 JWT 白名单说明)。在 JWT 段附近补注释:
```bash
# ── API 网关 JWT 鉴权(P1)──
# 网关用 SELLM_JWT_SECRET 同一密钥验签;白名单路径(/api/auth/、/actuator/health)免鉴权
# 网关验签通过后注入 X-User-Id / X-User-Role / X-Org-Id 到下游
```
(若 `.env.example` 已有 SELLM_JWT_SECRET,只追加上述说明注释,不重复 key。)

- [ ] **Step 5: 确认无未提交残留 + 底座抽离彻底**

```bash
cd /d/works/test/SELLM/dev_workspace
git status --short
echo "=== backend 残留底座包检查(应全 0)==="
for m in security anonymizer storage aigateway rag; do
  echo "$m: $(find backend/src/main/java/com/sellm/$m -name '*.java' 2>/dev/null | wc -l)"
done
```
Expected: working tree 干净(或仅 .env.example 待提交);5 个底座包均 0。

- [ ] **Step 6: Commit(如有 .env.example 改动)**

```bash
git add .env.example
git commit -m "docs(p1): .env.example 补网关 JWT 鉴权说明"
```

---

## 文件清单总览

```
dev_workspace/
├── sellm-common/                        (底座抽离目标)
│   ├── pom.xml                          (MODIFY: +mybatis/minio/jdbc)
│   └── src/main/
│       ├── java/com/sellm/
│       │   ├── common/                  (MOVE: Result/ErrorCode/BusinessException/
│       │   │   │                         DisorderType/LogType/Relationship/GlobalExceptionHandler)
│       │   │   └── crypto/              (MOVE: FieldCipher/AesFieldCipher)
│       │   ├── security/                (MOVE: 7类 + NEW: ChildSubject)
│       │   ├── anonymizer/              (MOVE: 4类)
│       │   ├── storage/                 (MOVE: 5类)
│       │   ├── aigateway/               (MOVE: 9类)
│       │   ├── rag/                      (MOVE: 4类)
│       │   └── event/                   (P0 已有)
│       └── resources/mybatis/
│           └── KnowledgeDocMapper.xml   (MOVE)
├── backend/
│   ├── pom.xml                          (MODIFY: +sellm-common 依赖)
│   └── src/main/java/com/sellm/
│       ├── child/Child.java             (MODIFY: implements ChildSubject)
│       ├── scale/ScaleExceptionHandler.java  (NEW: 承接 handleScoring)
│       └── (security/anonymizer/storage/aigateway/rag/common底座类 已移走)
├── sellm-gateway/                       (Nacos + JWT 鉴权)
│   ├── pom.xml                          (MODIFY: +nacos-discovery/loadbalancer/jjwt)
│   └── src/
│       ├── main/java/com/sellm/gateway/
│       │   ├── GatewayApplication.java  (MODIFY: @EnableConfigurationProperties)
│       │   ├── JwtAuthGatewayFilter.java(NEW)
│       │   └── GatewayJwtProperties.java(NEW)
│       ├── main/resources/application.yml (MODIFY: nacos + lb 路由 + jwt)
│       └── test/
│           ├── java/.../JwtAuthGatewayFilterTest.java (NEW)
│           └── resources/application.yml (NEW: 禁 discovery)
├── sellm-agent-{teaching,research,aids,qa}/  (各: +nacos-discovery, test 禁 discovery)
└── .env.example                         (MODIFY: 网关 JWT 说明)
```
