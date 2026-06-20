# P2 拆分 sellm-common 实现计划(core / backend)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把单一的 sellm-common 模块拆成 sellm-common-core(轻:契约/枚举/工具/接口+DTO,仅 spring-context+jjwt-api)和 sellm-common-backend(重:实现+配置,带 web/security/mybatis/jdbc/minio),让 4 个轻量 Agent 只依赖 core,根治传递依赖过宽,并去掉 P1 的 DataSourceAutoConfiguration 排除 workaround。

**Architecture:** 新建两个模块。core 收纳零重依赖且 Agent 可能消费的契约类(Result/枚举/异常/event/security 的 Role+AuthPrincipal+ChildSubject+JwtService+AccessGuard/各能力接口与 DTO);backend 收纳依赖 web/security/mybatis/minio 的实现与配置(GlobalExceptionHandler/AesFieldCipher/CurrentUser/JwtAuthFilter/SecurityConfig/各能力实现+配置/KnowledgeDocMapper+XML)。**保持 com.sellm.* 原包名,只换模块坐标** —— backend 单体 import 零改动。依赖方向单向无环:agent→core;sellm-common-backend→core;backend 单体→core+backend。最后删除空的旧 sellm-common 模块。

**Tech Stack:** Java 17 / Spring Boot 3.2.5 / Maven 多模块 / spring-context / jjwt 0.12.6 / MyBatis / MinIO

## Global Constraints

- Java 17,Spring Boot 3.2.5;依赖版本走父 POM 的属性(${jjwt.version}/${mybatis-starter.version}/${minio.version})
- **保持 com.sellm.* 原包名不变**(只换模块坐标,backend 单体 import 零改动)
- **每步用 `mvn clean install` 验证**(底座抽离/迁移类任务 stale `target/` 会假绿;P1 已踩过此坑)
- backend 237 测试全绿;全 reactor `clean install` 8→9→8 模块 SUCCESS;前端 build 绿
- core 依赖:spring-context + jjwt-api(AccessGuard/JwtService 保留 @Component/@Value 不改写)
- backend(新模块)依赖:sellm-common-core + spring-boot-starter-web/security + mybatis-spring-boot-starter + jdbc + minio + jjwt-impl/jackson(runtime)
- Maven 在 Git Bash:`export PATH="/c/tools/apache-maven-3.9.6/bin:$PATH"`
- 代码/配置变更后追加 `.claude/CLAUDE_CHANGES.md`
- 提交前 `git status` 不含 target/

---

### Task 1: 创建 sellm-common-core 模块 + 迁入 core 类(保持包名)

**Files:**
- Create: `sellm-common-core/pom.xml`
- Modify: `pom.xml`(根 reactor:`<modules>` 加 sellm-common-core;dependencyManagement 加 sellm-common-core 条目)
- Move(git mv,保持包路径): 以下类从 `sellm-common/src/main/java/com/sellm/` → `sellm-common-core/src/main/java/com/sellm/`
  - common/: `Result, ErrorCode, BusinessException, DisorderType, LogType, Relationship, package-info`
  - common/crypto/: `FieldCipher`(仅接口)
  - common/event/: `AgentEvent, EventConstants`
  - security/: `Role, AuthPrincipal, ChildSubject, JwtService, AccessGuard`
  - anonymizer/: `Anonymizer, AnonymizationResult, AnonymizationException`
  - storage/: `ObjectStorage`
  - aigateway/: `AiGateway, AiModel, PromptRequest, AiGatewayException`
  - rag/: `RagRetriever, KnowledgeDoc`

**Interfaces:**
- Produces: `sellm-common-core` jar(artifactId sellm-common-core,version 0.1.0-SNAPSHOT),导出 25 个类(含 package-info),包名不变(com.sellm.common/security/anonymizer/storage/aigateway/rag + 子包)
- Consumes: P1 的 sellm-common(本任务从中迁出 core 类;sellm-common 暂保留剩余 backend 类,Task 2 处理)

> **设计**:本任务只建 core 模块并迁入 core 类。此刻 sellm-common 仍存在(含剩余 backend 类),backend 单体仍依赖 sellm-common —— 但 core 类已不在 sellm-common 里了,所以 sellm-common 此时无法独立编译(它的 backend 类如 DefaultAiGateway 依赖已迁走的 AiModel/Anonymizer)。**因此本任务的验证不是 backend 测试绿,而是 core 模块单独编译通过**;backend 全绿要等 Task 2 把 backend 类也迁走、Task 3 重新接线后才成立。这是有意的中间态(两个任务构成一次完整迁移)。

- [ ] **Step 1: 创建 sellm-common-core/pom.xml**

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

    <artifactId>sellm-common-core</artifactId>
    <name>sellm-common-core</name>
    <description>平台底座轻量层:契约/枚举/工具/能力接口与DTO(Agent 与 backend 共用,仅 spring-context + jjwt-api)</description>

    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-api</artifactId>
            <version>${jjwt.version}</version>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 2: 根 pom 注册 core 模块 + dependencyManagement**

修改根 `pom.xml`:`<modules>` 中在 `sellm-common` 行后加 `<module>sellm-common-core</module>`。
dependencyManagement 中,在现有 sellm-common 条目后加:
```xml
            <dependency>
                <groupId>com.sellm</groupId>
                <artifactId>sellm-common-core</artifactId>
                <version>${project.version}</version>
            </dependency>
```

- [ ] **Step 3: git mv core 类(common 基础 + crypto 接口 + event)**

```bash
cd /d/works/test/SELLM/dev_workspace
SRC=sellm-common/src/main/java/com/sellm
DST=sellm-common-core/src/main/java/com/sellm
mkdir -p "$DST/common/crypto" "$DST/common/event"
for f in Result ErrorCode BusinessException DisorderType LogType Relationship package-info; do
  git mv "$SRC/common/$f.java" "$DST/common/$f.java"
done
git mv "$SRC/common/crypto/FieldCipher.java" "$DST/common/crypto/FieldCipher.java"
git mv "$SRC/common/event/AgentEvent.java" "$DST/common/event/AgentEvent.java"
git mv "$SRC/common/event/EventConstants.java" "$DST/common/event/EventConstants.java"
```

- [ ] **Step 4: git mv core 类(security 契约 + JwtService + AccessGuard)**

```bash
cd /d/works/test/SELLM/dev_workspace
SRC=sellm-common/src/main/java/com/sellm
DST=sellm-common-core/src/main/java/com/sellm
mkdir -p "$DST/security"
for f in Role AuthPrincipal ChildSubject JwtService AccessGuard; do
  git mv "$SRC/security/$f.java" "$DST/security/$f.java"
done
```

- [ ] **Step 5: git mv core 类(各能力接口 + DTO)**

```bash
cd /d/works/test/SELLM/dev_workspace
SRC=sellm-common/src/main/java/com/sellm
DST=sellm-common-core/src/main/java/com/sellm
mkdir -p "$DST/anonymizer" "$DST/storage" "$DST/aigateway" "$DST/rag"
for f in Anonymizer AnonymizationResult AnonymizationException; do
  git mv "$SRC/anonymizer/$f.java" "$DST/anonymizer/$f.java"
done
git mv "$SRC/storage/ObjectStorage.java" "$DST/storage/ObjectStorage.java"
for f in AiGateway AiModel PromptRequest AiGatewayException; do
  git mv "$SRC/aigateway/$f.java" "$DST/aigateway/$f.java"
done
git mv "$SRC/rag/RagRetriever.java" "$DST/rag/RagRetriever.java"
git mv "$SRC/rag/KnowledgeDoc.java" "$DST/rag/KnowledgeDoc.java"
```

- [ ] **Step 6: 验证 core 模块独立编译通过**

```bash
export PATH="/c/tools/apache-maven-3.9.6/bin:$PATH"
cd /d/works/test/SELLM/dev_workspace
mvn -pl sellm-common-core clean install -q && echo "CORE OK"
```
Expected: `CORE OK`(core 25 类只依赖 spring-context + jjwt-api,应干净编译)。

> 不要在本任务跑 backend 测试或全 reactor —— sellm-common 现处于残缺中间态(Task 2 修复)。若 core 编译报某类仍引用已留在 sellm-common 的 backend 类,说明分类有误,报 BLOCKED 附错误。

- [ ] **Step 7: Commit**

```bash
git add sellm-common-core/ pom.xml sellm-common/
git commit -m "refactor(p2): 新建 sellm-common-core 并迁入 25 个轻量契约/接口类(同包名)"
```

---

### Task 2: 创建 sellm-common-backend 模块 + 迁入 backend 类 + 删除旧 sellm-common

**Files:**
- Create: `sellm-common-backend/pom.xml`
- Move(git mv): 以下类从 `sellm-common/src/main/java/com/sellm/` → `sellm-common-backend/src/main/java/com/sellm/`
  - common/: `GlobalExceptionHandler`
  - common/crypto/: `AesFieldCipher`
  - security/: `CurrentUser, JwtAuthFilter, SecurityConfig`
  - anonymizer/: `RegexAnonymizer`
  - storage/: `StorageProperties, StorageConfig, NoopObjectStorage, MinioObjectStorage`
  - aigateway/: `AiProperties, AiModelConfig, MockAiModel, OpenAiCompatibleModel, DefaultAiGateway`
  - rag/: `KnowledgeDocMapper, DbRagRetriever`
- Move: `sellm-common/src/main/resources/mybatis/KnowledgeDocMapper.xml` → `sellm-common-backend/src/main/resources/mybatis/KnowledgeDocMapper.xml`
- Modify: `pom.xml`(根:`<modules>` 加 sellm-common-backend + mgmt 条目;删除 `<module>sellm-common</module>`)
- Delete: `sellm-common/`(整个旧模块目录)

**Interfaces:**
- Consumes: Task 1 的 sellm-common-core(backend 实现类依赖 core 的接口/DTO:DefaultAiGateway→AiModel/Anonymizer/AiGateway;RegexAnonymizer→Anonymizer;MinioObjectStorage→ObjectStorage;DbRagRetriever→RagRetriever/KnowledgeDoc;AesFieldCipher→FieldCipher)
- Produces: `sellm-common-backend` jar,导出 17 个实现/配置类 + KnowledgeDocMapper.xml,包名不变

> **设计**:本任务把剩余 backend 类迁入新模块,并删除已空的旧 sellm-common。backend 单体此时仍依赖 `sellm-common`(坐标),编译会断 —— Task 3 修复 backend/agent 的依赖坐标。所以本任务验证仍是模块级编译(core + backend 两个新模块编译通过),不是 backend 全绿。

- [ ] **Step 1: 创建 sellm-common-backend/pom.xml**

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

    <artifactId>sellm-common-backend</artifactId>
    <name>sellm-common-backend</name>
    <description>平台底座重量层:实现与配置(web/security/mybatis/jdbc/minio),仅 backend 单体依赖</description>

    <dependencies>
        <dependency>
            <groupId>com.sellm</groupId>
            <artifactId>sellm-common-core</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>
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
    </dependencies>
</project>
```

- [ ] **Step 2: git mv backend 实现/配置类**

```bash
cd /d/works/test/SELLM/dev_workspace
SRC=sellm-common/src/main/java/com/sellm
DST=sellm-common-backend/src/main/java/com/sellm
mkdir -p "$DST/common/crypto" "$DST/security" "$DST/anonymizer" "$DST/storage" "$DST/aigateway" "$DST/rag"
git mv "$SRC/common/GlobalExceptionHandler.java" "$DST/common/GlobalExceptionHandler.java"
git mv "$SRC/common/crypto/AesFieldCipher.java" "$DST/common/crypto/AesFieldCipher.java"
for f in CurrentUser JwtAuthFilter SecurityConfig; do
  git mv "$SRC/security/$f.java" "$DST/security/$f.java"
done
git mv "$SRC/anonymizer/RegexAnonymizer.java" "$DST/anonymizer/RegexAnonymizer.java"
for f in StorageProperties StorageConfig NoopObjectStorage MinioObjectStorage; do
  git mv "$SRC/storage/$f.java" "$DST/storage/$f.java"
done
for f in AiProperties AiModelConfig MockAiModel OpenAiCompatibleModel DefaultAiGateway; do
  git mv "$SRC/aigateway/$f.java" "$DST/aigateway/$f.java"
done
git mv "$SRC/rag/KnowledgeDocMapper.java" "$DST/rag/KnowledgeDocMapper.java"
git mv "$SRC/rag/DbRagRetriever.java" "$DST/rag/DbRagRetriever.java"
```

- [ ] **Step 3: git mv MyBatis XML**

```bash
cd /d/works/test/SELLM/dev_workspace
mkdir -p sellm-common-backend/src/main/resources/mybatis
git mv sellm-common/src/main/resources/mybatis/KnowledgeDocMapper.xml \
       sellm-common-backend/src/main/resources/mybatis/KnowledgeDocMapper.xml
```

- [ ] **Step 4: 根 pom 注册 backend 模块 + 删除旧 sellm-common 模块声明**

修改根 `pom.xml`:
- `<modules>` 中删除 `<module>sellm-common</module>`(及其上方注释行),加 `<module>sellm-common-backend</module>`。
- dependencyManagement 中删除旧 sellm-common 条目,加:
```xml
            <dependency>
                <groupId>com.sellm</groupId>
                <artifactId>sellm-common-backend</artifactId>
                <version>${project.version}</version>
            </dependency>
```
(保留 Task 1 加的 sellm-common-core 条目。)

- [ ] **Step 5: 删除已空的旧 sellm-common 目录**

```bash
cd /d/works/test/SELLM/dev_workspace
# 确认 java/resources 已无文件(只剩空目录/pom)
find sellm-common/src -name '*.java' -o -name '*.xml' | grep -v target || echo "sellm-common src 已空"
git rm -r sellm-common/pom.xml
# 清理残留空目录(git 不跟踪空目录)
rm -rf sellm-common
echo "旧 sellm-common 已删除"
```

- [ ] **Step 6: 验证 core + backend 两模块编译通过**

```bash
export PATH="/c/tools/apache-maven-3.9.6/bin:$PATH"
cd /d/works/test/SELLM/dev_workspace
mvn -pl sellm-common-core,sellm-common-backend -am clean install -q && echo "CORE+BACKEND OK"
```
Expected: `CORE+BACKEND OK`(两模块编译 + 安装到本地仓库)。

> 不跑 backend 单体测试(它仍指向旧坐标,Task 3 修)。若 backend 模块编译报找不到 core 的某类,说明 Task 1 分类漏了某个类,报 BLOCKED。

- [ ] **Step 7: Commit**

```bash
git add sellm-common-backend/ pom.xml
git add -A sellm-common 2>/dev/null || true
git commit -m "refactor(p2): 新建 sellm-common-backend 迁入17实现类+XML,删除旧 sellm-common"
```

---

### Task 3: 重接线 backend 单体与 4 个 Agent 的依赖坐标 + 去除 workaround

**Files:**
- Modify: `backend/pom.xml`(sellm-common → sellm-common-core + sellm-common-backend)
- Modify: `sellm-agent-teaching/pom.xml`、`research`、`aids`、`qa`(sellm-common → sellm-common-core)
- Modify: 4 个 agent 的 `src/main/resources/application.yml`(去掉 DataSourceAutoConfiguration 排除)
- Modify: 4 个 agent 的 `src/test/resources/application.yml`(去掉 DataSourceAutoConfiguration + HibernateJpaAutoConfiguration 排除)

**Interfaces:**
- Consumes: Task 1/2 的 sellm-common-core + sellm-common-backend
- Produces: backend 单体依赖两个新模块、4 agent 仅依赖 core;全 reactor 编译测试通过

> **设计**:这是迁移的收尾任务 —— 把所有消费者从旧坐标 sellm-common 切到新坐标。backend 需要两个(core 给契约、backend 给实现);agent 只需 core(不再传递继承 mybatis/jdbc/minio/security,因此可删除 P1 加的排除 workaround)。本任务验证 backend 237 全绿 + 全 reactor SUCCESS。

- [ ] **Step 1: backend/pom.xml 换依赖**

把 `backend/pom.xml` 中的
```xml
        <dependency>
            <groupId>com.sellm</groupId>
            <artifactId>sellm-common</artifactId>
            <version>0.1.0-SNAPSHOT</version>
        </dependency>
```
替换为(版本走 dependencyManagement,可省略 version):
```xml
        <dependency>
            <groupId>com.sellm</groupId>
            <artifactId>sellm-common-core</artifactId>
            <version>0.1.0-SNAPSHOT</version>
        </dependency>
        <dependency>
            <groupId>com.sellm</groupId>
            <artifactId>sellm-common-backend</artifactId>
            <version>0.1.0-SNAPSHOT</version>
        </dependency>
```

- [ ] **Step 2: 4 个 agent pom 换依赖(sellm-common → sellm-common-core)**

对 `sellm-agent-teaching/pom.xml`、`sellm-agent-research/pom.xml`、`sellm-agent-aids/pom.xml`、`sellm-agent-qa/pom.xml`,把每个的
```xml
        <dependency>
            <groupId>com.sellm</groupId>
            <artifactId>sellm-common</artifactId>
        </dependency>
```
改为
```xml
        <dependency>
            <groupId>com.sellm</groupId>
            <artifactId>sellm-common-core</artifactId>
        </dependency>
```

- [ ] **Step 3: 去掉 4 个 agent 主 yml 的 DataSource 排除 workaround**

每个 agent 的 `src/main/resources/application.yml` 中删除这段(P1 加的 workaround,现 core 不再带 jdbc 故无需):
```yaml
  autoconfigure:
    exclude:
      - org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
```
保留 `spring.application.name` 和 `spring.cloud.nacos`。删除后 `spring:` 下只剩 application + cloud 两个子键。

- [ ] **Step 4: 去掉 4 个 agent 测试 yml 的 autoconfigure 排除**

每个 agent 的 `src/test/resources/application.yml` 中删除这段:
```yaml
  autoconfigure:
    exclude:
      - org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
      - org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration
```
保留 `spring.cloud.nacos.discovery.enabled: false` + `spring.cloud.discovery.enabled: false` + `spring.cloud.service-registry.auto-registration.enabled: false`。删除后 `spring:` 下只剩 `cloud`。

- [ ] **Step 5: 验证 backend 237 全绿(clean)**

```bash
export PATH="/c/tools/apache-maven-3.9.6/bin:$PATH"
cd /d/works/test/SELLM/dev_workspace
mvn -pl sellm-common-core,sellm-common-backend -am clean install -q && echo "MODULES OK"
mvn -pl backend clean test 2>&1 | grep -E "Tests run:.*Failures|BindingException|BUILD" | tail -5
```
Expected: `MODULES OK`;`Tests run: 237, Failures: 0, Errors: 0` + `BUILD SUCCESS`;无 BindingException(KnowledgeDocMapper.xml 现在 sellm-common-backend,backend 单体经 `classpath*:mybatis/*.xml` 跨 jar 仍能扫到)。

> 若报 BindingException(KnowledgeDocMapper.findAll not found):确认 backend 仍用 `classpath*:`(P1 已改)且依赖了 sellm-common-backend。若 agent 启动相关测试失败,确认主 yml 排除已删且 core 不带 jdbc。报 BLOCKED 附错误。

- [ ] **Step 6: 验证 4 个 agent contextLoads(无 workaround,无 Nacos)**

```bash
export PATH="/c/tools/apache-maven-3.9.6/bin:$PATH"
cd /d/works/test/SELLM/dev_workspace
mvn -pl sellm-agent-teaching,sellm-agent-research,sellm-agent-aids,sellm-agent-qa clean test 2>&1 | grep -E "Tests run:|BUILD|Building " | tail -16
```
Expected: 4 个模块各 `Tests run: 1, Failures: 0`,`BUILD SUCCESS`。这证明去掉 DataSource 排除后 agent 仍能启动(core 不再带 jdbc,无 DataSourceAutoConfiguration 触发)。

> 若某 agent contextLoads 报 "Failed to configure a DataSource",说明它仍传递到了 jdbc —— 检查该 agent pom 是否真的换成了 sellm-common-core(而非残留 sellm-common 或误依赖 backend)。报 BLOCKED。

- [ ] **Step 7: Commit**

```bash
git add backend/pom.xml sellm-agent-teaching/ sellm-agent-research/ sellm-agent-aids/ sellm-agent-qa/
git commit -m "refactor(p2): backend 依赖 core+backend,4 agent 仅依赖 core 并去除 DataSource 排除 workaround"
```

---

### Task 4: P2 全量回归 + 文档

**Files:**
- Verification only(+ 可能的 `.claude/CLAUDE_CHANGES.md` 追加)

**Interfaces:**
- Produces: 确认拆分不破坏任何功能,全 reactor 9 模块绿

- [ ] **Step 1: 全 reactor clean install(9 模块)**

```bash
export PATH="/c/tools/apache-maven-3.9.6/bin:$PATH"
cd /d/works/test/SELLM/dev_workspace
mvn clean install 2>&1 | grep -E "Reactor Summary|SUCCESS \[|FAILURE \[|BUILD SUCCESS|BUILD FAILURE" | tail -15
```
Expected: 9 个模块全 SUCCESS(sellm-parent, sellm-common-core, sellm-common-backend, asd-assistant-backend, sellm-gateway, 4 agents),`BUILD SUCCESS`。**确认列表中已无 sellm-common(旧模块已删)**。

- [ ] **Step 2: 确认 agent 不再传递 jdbc/mybatis/minio**

```bash
export PATH="/c/tools/apache-maven-3.9.6/bin:$PATH"
cd /d/works/test/SELLM/dev_workspace
mvn -pl sellm-agent-teaching dependency:tree 2>&1 | grep -E "mybatis|minio|jdbc|spring-security|spring-webflux|spring-boot-starter-web" || echo "AGENT 无重依赖(干净)"
```
Expected: `AGENT 无重依赖(干净)`(teaching agent 只应有 spring-boot-starter-web/actuator/nacos + sellm-common-core 带的 spring-context + jjwt-api;不应出现 mybatis/minio/jdbc/security)。

> 注:agent 自己声明了 spring-boot-starter-web(P0),所以 web 会有 —— grep 里 `spring-boot-starter-web` 命中属正常(agent 本就要 web)。重点是 mybatis/minio/jdbc/spring-security 应消失。若仍出现,说明 core 误带了重依赖或 agent 误依赖 backend,报 BLOCKED。

- [ ] **Step 3: 前端 build 验证**

```bash
cd /d/works/test/SELLM/dev_workspace/frontend
npm run build 2>&1 | grep -E "built in|error" | tail -3
```
Expected: `✓ built in X.XXs`。

- [ ] **Step 4: 追加变更记录**

向 `.claude/CLAUDE_CHANGES.md` 追加 P2 拆分记录(REFACTOR 类型,按项目格式:变更描述/位置/原因/影响范围/验证状态)。

- [ ] **Step 5: 确认工作树干净 + 最终提交**

```bash
cd /d/works/test/SELLM/dev_workspace
git status --short
# 若仅 .claude/CLAUDE_CHANGES.md 待提交(.claude 被 gitignore,通常无需提交);其余应为空
```
Expected: working tree 干净(.claude 不入库)。无遗漏文件。

---

## 文件清单总览

```
dev_workspace/
├── pom.xml                          (MODIFY: -sellm-common +core +backend modules & mgmt)
├── sellm-common/                    (DELETE 整个旧模块)
├── sellm-common-core/               (NEW: 25 类,spring-context+jjwt-api)
│   ├── pom.xml
│   └── src/main/java/com/sellm/
│       ├── common/{Result,ErrorCode,BusinessException,DisorderType,LogType,Relationship,package-info}
│       ├── common/crypto/FieldCipher
│       ├── common/event/{AgentEvent,EventConstants}
│       ├── security/{Role,AuthPrincipal,ChildSubject,JwtService,AccessGuard}
│       ├── anonymizer/{Anonymizer,AnonymizationResult,AnonymizationException}
│       ├── storage/ObjectStorage
│       ├── aigateway/{AiGateway,AiModel,PromptRequest,AiGatewayException}
│       └── rag/{RagRetriever,KnowledgeDoc}
├── sellm-common-backend/            (NEW: 17 类+XML,web/security/mybatis/jdbc/minio)
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/sellm/
│       │   ├── common/GlobalExceptionHandler
│       │   ├── common/crypto/AesFieldCipher
│       │   ├── security/{CurrentUser,JwtAuthFilter,SecurityConfig}
│       │   ├── anonymizer/RegexAnonymizer
│       │   ├── storage/{StorageProperties,StorageConfig,NoopObjectStorage,MinioObjectStorage}
│       │   ├── aigateway/{AiProperties,AiModelConfig,MockAiModel,OpenAiCompatibleModel,DefaultAiGateway}
│       │   └── rag/{KnowledgeDocMapper,DbRagRetriever}
│       └── resources/mybatis/KnowledgeDocMapper.xml
├── backend/pom.xml                  (MODIFY: sellm-common → core + backend)
└── sellm-agent-{teaching,research,aids,qa}/
    ├── pom.xml                      (MODIFY: sellm-common → sellm-common-core)
    └── src/{main,test}/resources/application.yml  (MODIFY: 删 DataSource 排除 workaround)
```
