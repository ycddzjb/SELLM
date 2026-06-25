# ASD 助手 — 计划二:后端持久化层 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 ASD 助手后端核心补上持久化层:用 MyBatis 把 Child(含身份字段加密)、Scale 量表三件套、KnowledgeDoc 知识库落到关系库,并让 RAG 检索从数据库加载知识(修复计划一遗留的 Spring 上下文启动隐患),同时修复整体审查的三个优先项。

**Architecture:** 沿用方案一模块化单体。新增 `persistence` / `config` 支撑,各业务包内增加 entity 的持久化(Mapper + 建表)。测试用 H2 内存库(MySQL 兼容模式),运行时通过 profile 切到真实 MySQL —— 延续计划一"测试不依赖外部基础设施"的精神。身份信息(姓名等)在写库前用 AES 加密、读出时解密,与脱敏层(出网用)解耦:加密保护静态存储,脱敏保护出网。

**Tech Stack:** Java 17、Spring Boot 3.2.5、MyBatis-Spring-Boot-Starter 3.0.3、H2(test)、mysql-connector-j(runtime)、JUnit 5、AssertJ。根包 `com.sellm`。不使用 Lombok。

**Source spec:** `docs/superpowers/specs/2026-06-18-asd-assessment-iep-assistant-design.md`
**Builds on:** 计划一(master,后端核心骨架)

---

## 前置:计划一整体审查遗留的三个优先项

本计划在相应任务中一并修复:
1. **Spring 上下文启动隐患** —— `InMemoryRagRetriever` 的 `@Component` 缺 `List<KnowledgeDoc>` bean。Task 6 让 RAG 从数据库加载知识,并补 `@SpringBootTest` 上下文测试守住启动。
2. **脱敏泄露校验只覆盖姓名** —— Task 1 把 schools 纳入 `RegexAnonymizer` 的 `mustNotContain` 校验。
3. **IEP 定稿丢失人工内容** —— Task 2 给 `Iep` 补 `finalizedContent` 字段与 `finalizePlan(String)` 入参,对齐 `Report`。

---

## 文件结构(本计划范围)

```
backend/
  pom.xml                                         # 加 mybatis/h2/mysql 依赖(Task 3)
  src/main/resources/
    application.yml                               # 默认 + mysql profile 配置(Task 3)
    application-test.yml                           # H2 测试配置(Task 3)
    schema.sql                                     # 建表 DDL,H2/MySQL 兼容(Task 4/6/7)
  src/main/java/com/sellm/
    config/
      MyBatisConfig.java                           # @MapperScan(Task 3)
    common/
      crypto/
        FieldCipher.java                           # 字段级加密接口(Task 4)
        AesFieldCipher.java                        # AES-GCM 实现(Task 4)
    child/
      Child.java                                   # 儿童档案实体(Task 5)
      ChildMapper.java                             # MyBatis Mapper(Task 5)
      ChildRepository.java                         # 仓储:加密写/解密读(Task 5)
    rag/
      KnowledgeDocMapper.java                      # 知识文档 Mapper(Task 6)
      DbRagRetriever.java                          # 从 DB 加载并检索(Task 6,替代 InMemory 作为 @Primary)
    scale/
      ScaleMapper.java                             # 量表 Mapper(Task 7)
      ScaleRepository.java                          # 组装 Scale 聚合(Task 7)
  src/main/resources/mybatis/
    ChildMapper.xml                                # (Task 5)
    KnowledgeDocMapper.xml                          # (Task 6)
    ScaleMapper.xml                                 # (Task 7)
  src/test/java/com/sellm/
    anonymizer/RegexAnonymizerTest.java            # 增 schools 校验测试(Task 1)
    iep/IepTest.java                                # 新增:定稿保存内容(Task 2)
    iep/IepServiceTest.java                         # 适配 finalizePlan 签名(Task 2)
    common/crypto/AesFieldCipherTest.java          # 加解密往返(Task 4)
    child/ChildRepositoryTest.java                  # H2 集成:加密落库/解密读出(Task 5)
    rag/DbRagRetrieverTest.java                     # H2 集成:从库检索(Task 6)
    scale/ScaleRepositoryTest.java                  # H2 集成:组装量表(Task 7)
    context/ApplicationContextTest.java             # @SpringBootTest 启动守护(Task 8)
  src/test/resources/
    application-test.yml                            # (Task 3)
    test-data.sql                                   # 测试种子数据(按需,Task 6/7)
```

**为什么这样切:** 持久化按"实体归属的业务包"就近放(child/rag/scale),符合设计第 7 节"按职责而非技术分层";加密能力放 common/crypto 供跨模块复用;config 放容器装配。Assessment/Report/Iep 的**记录持久化**与 User/Org/认证留到后续计划(它们与 REST、认证强绑定),本计划聚焦"主数据 + 知识库 + 修复启动"。

---

### Task 1: 修复脱敏校验只覆盖姓名(审查项 #2)

把 schools 纳入 `RegexAnonymizer` 三参 `anonymize` 的出网泄露校验名单。当前三参委托四参时 `mustNotContain = names`,学校名残留不会被拦截;改为 `names ∪ schools`。

说明(诚实记录设计边界):四参 `anonymize(text, names, schools, mustNotContain)` 早已正确遍历 `mustNotContain` 做拦截(计划一已实现并测过 names 路径)。本任务只改三参委托,使其默认把 schools 也送进校验名单。因替换与校验对 schools 用的是同一份列表(对称),三参在"正常替换"下不会误伤;真正的价值是与四参语义一致、对调用方少传项时多一层兜底。测试用四参锁定"schools 进入校验名单 → 残留即硬阻断"这一契约(镜像计划一的 names 测试)。

**Files:**
- Modify: `backend/src/main/java/com/sellm/anonymizer/RegexAnonymizer.java`
- Modify: `backend/src/test/java/com/sellm/anonymizer/RegexAnonymizerTest.java`

- [x] **Step 1: 先加契约测试(四参锁定 schools 校验)+ 三参 happy-path 测试**

在 `RegexAnonymizerTest.java` 末尾最后一个 `}` 之前追加两个测试(文件已 import `java.util.List`):
```java
    @Test
    void 四参版本将学校名纳入校验名单残留则硬阻断() {
        // schools 替换列表为空使"阳光小学"未被替换,mustNotContain 含它 → 校验阶段硬阻断
        assertThatThrownBy(() ->
            anonymizer.anonymize("就读于阳光小学", List.of(), List.of(),
                List.of("阳光小学"))
        ).isInstanceOf(AnonymizationException.class);
    }

    @Test
    void 三参版本学校被正常替换且不误伤() {
        AnonymizationResult r = anonymizer.anonymize(
            "就读于阳光小学", List.of(), List.of("阳光小学"));
        assertThat(r.getAnonymizedText()).doesNotContain("阳光小学").contains("[学校1]");
        assertThat(anonymizer.restore(r.getAnonymizedText(), r.getRestoreMap()))
            .isEqualTo("就读于阳光小学");
    }
```

- [x] **Step 2: 运行测试,确认现状**

Run: `cd "D:/works/test/SELLM/backend" && ./mvnw -q test -Dtest=RegexAnonymizerTest`
Expected: 两个新测试都 PASS(四参契约本就成立,三参 happy-path 也成立)。这一步确认新测试不误判;真正的行为改动由 Step 3 落实,Step 4 加针对三参委托的回归测试。

- [x] **Step 3: 修改三参委托,把 schools 并入 mustNotContain**

把 `RegexAnonymizer.java` 第 17-19 行的三参方法改为:
```java
    @Override
    public AnonymizationResult anonymize(String text, List<String> names, List<String> schools) {
        List<String> mustNotContain = new ArrayList<>();
        mustNotContain.addAll(names);
        mustNotContain.addAll(schools);
        return anonymize(text, names, schools, mustNotContain);
    }
```
文件顶部已 import `java.util.ArrayList` 与 `java.util.List`,无需新增。

- [x] **Step 4: 加三参委托回归测试**

在 `RegexAnonymizerTest.java` 再追加(验证三参确实把 schools 送进了校验:构造 names 替换在前、把学校名"挤裂"导致 schools 精确串虽不残留但我们换一个可判定场景——直接验证当 names 命中替换、schools 列表为空白项被跳过时,文本里残留的学校名由 schools-in-mustNotContain 兜底。注:空白项在校验侧也被跳过,故改用"schools 含真实校名但文本另有同名未被传入替换"不可行。最终采用可判定写法:三参传入真实 names+schools,断言正常脱敏且校验通过,锁定委托不破坏既有行为):
```java
    @Test
    void 三参委托传入姓名与学校均被脱敏且校验通过() {
        AnonymizationResult r = anonymizer.anonymize(
            "小明就读于阳光小学", List.of("小明"), List.of("阳光小学"));
        assertThat(r.getAnonymizedText())
            .doesNotContain("小明").doesNotContain("阳光小学")
            .contains("[儿童1]").contains("[学校1]");
    }
```

- [x] **Step 5: 运行测试,确认全部通过**

Run: `cd "D:/works/test/SELLM/backend" && ./mvnw -q test -Dtest=RegexAnonymizerTest`
Expected: 原 4 个 + 新增 3 个 = 7 个全部 PASS。

- [x] **Step 6: Commit**

```bash
cd "D:/works/test/SELLM" && git add backend/src/main/java/com/sellm/anonymizer/RegexAnonymizer.java backend/src/test/java/com/sellm/anonymizer/RegexAnonymizerTest.java && git commit -q -m "fix(anonymizer): 三参脱敏将学校名并入出网校验名单(审查项#2)"
```

---
### Task 2: IEP 定稿保存人工内容(审查项 #3)

`Iep.finalizePlan()` 当前只翻状态、不保存内容,与 `Report.finalizeReport(content)` 不对称。给 `Iep` 补 `finalizedContent` 字段与 `finalizePlan(String content)` 入参。

**Files:**
- Modify: `backend/src/main/java/com/sellm/iep/Iep.java`
- Create: `backend/src/test/java/com/sellm/iep/IepTest.java`

- [x] **Step 1: 写失败测试**

新建 `backend/src/test/java/com/sellm/iep/IepTest.java`:
```java
package com.sellm.iep;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class IepTest {

    @Test
    void 初始状态为DRAFT() {
        Iep iep = new Iep("小明", "草案");
        assertThat(iep.getStatus()).isEqualTo(IepStatus.DRAFT);
        assertThat(iep.getFinalizedContent()).isNull();
    }

    @Test
    void 定稿保存内容并置为FINALIZED() {
        Iep iep = new Iep("小明", "草案");
        iep.finalizePlan("老师修改后的IEP终稿");
        assertThat(iep.getStatus()).isEqualTo(IepStatus.FINALIZED);
        assertThat(iep.getFinalizedContent()).isEqualTo("老师修改后的IEP终稿");
    }
}
```

- [x] **Step 2: 运行测试,确认失败**

Run: `cd "D:/works/test/SELLM/backend" && ./mvnw -q test -Dtest=IepTest`
Expected: 编译失败 —— `Iep` 无 `getFinalizedContent()`,且 `finalizePlan` 当前无参。

- [x] **Step 3: 修改 Iep 实体**

把 `Iep.java` 整体替换为:
```java
package com.sellm.iep;

public class Iep {
    private final String childName;
    private final String draft;       // AI 生成草案(已还原)
    private String finalizedContent;  // 老师定稿内容
    private IepStatus status;

    public Iep(String childName, String draft) {
        this.childName = childName;
        this.draft = draft;
        this.status = IepStatus.DRAFT;
    }

    public void finalizePlan(String content) {
        this.finalizedContent = content;
        this.status = IepStatus.FINALIZED;
    }

    public String getChildName() { return childName; }
    public String getDraft() { return draft; }
    public String getFinalizedContent() { return finalizedContent; }
    public IepStatus getStatus() { return status; }
}
```

- [x] **Step 4: 修复受影响的既有测试**

`IepServiceTest.java` 中 `定稿后状态为FINALIZED` 测试调用了无参 `finalizePlan()`,现在签名变了需同步。把该测试体改为:
```java
    @Test
    void 定稿后状态为FINALIZED() {
        Iep iep = new Iep("小明", "草案");
        iep.finalizePlan("终稿内容");
        assertThat(iep.getStatus()).isEqualTo(IepStatus.FINALIZED);
    }
```

- [x] **Step 5: 运行 iep 包全部测试,确认通过**

Run: `cd "D:/works/test/SELLM/backend" && ./mvnw -q test -Dtest="com.sellm.iep.*"`
Expected: IepTest(2)+ IepServiceTest(3)全部 PASS。

- [x] **Step 6: Commit**

```bash
cd "D:/works/test/SELLM" && git add backend/src/main/java/com/sellm/iep/Iep.java backend/src/test/java/com/sellm/iep/ && git commit -q -m "fix(iep): 定稿保存人工内容,对齐 Report(审查项#3)"
```

---
### Task 3: 持久化基建(MyBatis + H2 测试 + MySQL 运行时)

加依赖与配置:测试用 H2(MySQL 兼容模式 + 自动建表),运行时用 mysql profile 连真实库。建立 schema.sql 雏形与 MyBatis 装配。本任务不含具体实体,只把"能连库、能建表、能装配 Mapper"跑通,用一个最小冒烟测试守住。

**Files:**
- Modify: `backend/pom.xml`
- Create: `backend/src/main/resources/application.yml`
- Create: `backend/src/main/resources/schema.sql`
- Create: `backend/src/main/java/com/sellm/config/MyBatisConfig.java`
- Create: `backend/src/test/resources/application-test.yml`
- Create: `backend/src/test/java/com/sellm/persistence/SchemaSmokeTest.java`

- [x] **Step 1: 加依赖到 pom.xml**

在 `<dependencies>` 内、`spring-boot-starter-test` 之后追加:
```xml
        <dependency>
            <groupId>org.mybatis.spring.boot</groupId>
            <artifactId>mybatis-spring-boot-starter</artifactId>
            <version>3.0.3</version>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-jdbc</artifactId>
        </dependency>
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <scope>test</scope>
        </dependency>
```
(mysql-connector-j 与 h2 版本由 spring-boot-starter-parent 的 BOM 统一管理,无需写 version。)

- [x] **Step 2: 创建主配置 application.yml**

`backend/src/main/resources/application.yml`:
```yaml
spring:
  application:
    name: asd-assistant-backend
  sql:
    init:
      mode: never          # 默认不自动建表;由各环境显式开启
  datasource:
    url: jdbc:mysql://localhost:3306/asd_assistant?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=utf8
    username: ${DB_USER:root}
    password: ${DB_PASSWORD:}
    driver-class-name: com.mysql.cj.jdbc.Driver

mybatis:
  mapper-locations: classpath:mybatis/*.xml
  configuration:
    map-underscore-to-camel-case: true

sellm:
  crypto:
    aes-key: ${SELLM_AES_KEY:}   # 运行时必须由环境变量提供;为空时启动应失败(见 Task 4)
```

- [x] **Step 3: 创建 schema.sql 雏形(空占位,后续任务追加表)**

`backend/src/main/resources/schema.sql`:
```sql
-- ASD 助手持久化建表脚本(H2 与 MySQL 8 兼容子集)
-- 各持久化任务在此追加 CREATE TABLE。使用 IF NOT EXISTS 保证可重复执行。

-- 占位语句:保证脚本非空(Spring ScriptUtils 对纯注释脚本会抛异常)。
-- Task 5 落地首个 CREATE TABLE 后可移除本行。
SELECT 1;

-- (Task 5 追加 child 表)
-- (Task 6 追加 knowledge_doc 表)
-- (Task 7 追加 scale / scale_item / score_band 表)
```

- [x] **Step 4: 创建 MyBatis 装配**

`backend/src/main/java/com/sellm/config/MyBatisConfig.java`:
```java
package com.sellm.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan(basePackages = "com.sellm", annotationClass = org.apache.ibatis.annotations.Mapper.class)
public class MyBatisConfig {
}
```
**重要**:`@MapperScan` 必须带 `annotationClass = Mapper.class`,**只扫描标了 `@Mapper` 的接口**。否则会把 `com.sellm` 下所有接口(FieldCipher、Anonymizer、AiModel 等)都误注册为 MyBatis mapper bean,与真实 @Component 实现产生注入歧义,导致上下文启动失败。(此点在 Task 5 实现时发现并修正,计划已同步。)

- [x] **Step 5: 创建测试配置 application-test.yml**

`backend/src/test/resources/application-test.yml`:
```yaml
spring:
  datasource:
    url: jdbc:h2:mem:asd_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE
    username: sa
    password:
    driver-class-name: org.h2.Driver
  sql:
    init:
      mode: always         # 测试启动时执行 schema.sql 自动建表
      schema-locations: classpath:schema.sql

mybatis:
  mapper-locations: classpath:mybatis/*.xml
  configuration:
    map-underscore-to-camel-case: true

sellm:
  crypto:
    aes-key: "0123456789abcdef0123456789abcdef"   # 测试固定 32 字节密钥(AES-256)
```

- [x] **Step 6: 写冒烟测试**

`backend/src/test/java/com/sellm/persistence/SchemaSmokeTest.java`:
```java
package com.sellm.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import javax.sql.DataSource;
import java.sql.Connection;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class SchemaSmokeTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void 能拿到H2数据源连接() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            assertThat(conn.isValid(2)).isTrue();
            assertThat(conn.getMetaData().getDatabaseProductName()).isEqualTo("H2");
        }
    }
}
```

注:本任务 schema.sql 为空,`@SpringBootTest` 加载完整上下文。完整上下文含计划一的 `ReportService`/`IepService`,它们构造器注入 `RagRetriever`;当前唯一实现 `InMemoryRagRetriever`(@Component)又需要注入 `List<KnowledgeDoc>` bean —— 这正是计划一遗留的启动隐患(无该 bean 则上下文启动失败)。因此 **Step 7 先提供一个空的 `knowledgeDocs` bean** 作为脚手架,让 InMemory 能装配、上下文能启动;Task 6 再引入 `DbRagRetriever`(@Primary)作为真正的 DB 实现。

- [x] **Step 7: 提供空的 knowledgeDocs bean(脚手架,解除上下文阻塞)**

新建 `backend/src/main/java/com/sellm/config/RagConfig.java`:
```java
package com.sellm.config;

import com.sellm.rag.KnowledgeDoc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.ArrayList;
import java.util.List;

@Configuration
public class RagConfig {

    /**
     * 脚手架 bean:满足 InMemoryRagRetriever 的构造依赖,使上下文可启动。
     * Task 6 引入 DbRagRetriever(@Primary)后,知识来源改为数据库;
     * 本空列表与 InMemoryRagRetriever 退为非默认实现(future:可移除)。
     */
    @Bean
    public List<KnowledgeDoc> knowledgeDocs() {
        return new ArrayList<>();
    }
}
```
`InMemoryRagRetriever` 本身不改动。提供该 bean 后,InMemory 用空列表装配,`ReportService`/`IepService` 的 RagRetriever 依赖得到满足,上下文启动成功。

- [x] **Step 8: 运行冒烟测试,确认通过**

Run: `cd "D:/works/test/SELLM/backend" && ./mvnw -q test -Dtest=SchemaSmokeTest`
Expected: PASS —— 上下文启动成功(InMemory 用空 knowledgeDocs 装配),H2 数据源可连。

- [x] **Step 9: 运行全量测试,确认无回归**

Run: `cd "D:/works/test/SELLM/backend" && ./mvnw -q test`
Expected: 计划一的 22 个 + Task1/2 新增 + 本任务冒烟,全部 PASS。

- [x] **Step 10: Commit**

```bash
cd "D:/works/test/SELLM" && git add backend/pom.xml backend/src/main/resources/ backend/src/main/java/com/sellm/config/ backend/src/test/resources/ backend/src/test/java/com/sellm/persistence/ && git commit -q -m "feat(persistence): MyBatis+H2/MySQL 基建与上下文冒烟测试"
```

---
### Task 4: 字段级加密 FieldCipher(身份信息静态加密,TDD)

Child 身份字段写库前加密、读出后解密,保护静态存储(与脱敏层解耦:脱敏管出网,加密管落库)。用 AES-256-GCM。密钥从配置 `sellm.crypto.aes-key` 读取(32 字节),缺失则启动失败(不允许无密钥跑)。

**Files:**
- Create: `backend/src/main/java/com/sellm/common/crypto/FieldCipher.java`
- Create: `backend/src/main/java/com/sellm/common/crypto/AesFieldCipher.java`
- Test: `backend/src/test/java/com/sellm/common/crypto/AesFieldCipherTest.java`

- [x] **Step 1: 创建接口**

`FieldCipher.java`:
```java
package com.sellm.common.crypto;

public interface FieldCipher {
    /** 加密明文,返回 Base64 编码的密文(含 IV) */
    String encrypt(String plaintext);

    /** 解密 Base64 密文,返回明文 */
    String decrypt(String ciphertext);
}
```

- [x] **Step 2: 写失败测试**

`AesFieldCipherTest.java`:
```java
package com.sellm.common.crypto;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AesFieldCipherTest {

    // 32 字节(AES-256)测试密钥
    private final AesFieldCipher cipher =
        new AesFieldCipher("0123456789abcdef0123456789abcdef");

    @Test
    void 加密后密文不等于明文且能解密还原() {
        String plain = "小明";
        String enc = cipher.encrypt(plain);
        assertThat(enc).isNotEqualTo(plain);
        assertThat(cipher.decrypt(enc)).isEqualTo(plain);
    }

    @Test
    void 相同明文两次加密密文不同_因随机IV() {
        String plain = "张伟";
        assertThat(cipher.encrypt(plain)).isNotEqualTo(cipher.encrypt(plain));
    }

    @Test
    void 空字符串可加解密往返() {
        assertThat(cipher.decrypt(cipher.encrypt(""))).isEqualTo("");
    }

    @Test
    void 密钥长度非法则构造失败() {
        assertThatThrownBy(() -> new AesFieldCipher("tooshort"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
```

- [x] **Step 3: 运行测试,确认失败**

Run: `cd "D:/works/test/SELLM/backend" && ./mvnw -q test -Dtest=AesFieldCipherTest`
Expected: 编译失败 —— `AesFieldCipher` 尚未创建。

- [x] **Step 4: 实现 AesFieldCipher**

`AesFieldCipher.java`:
```java
package com.sellm.common.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class AesFieldCipher implements FieldCipher {

    private static final int IV_LENGTH = 12;       // GCM 推荐 12 字节
    private static final int TAG_LENGTH_BIT = 128;  // GCM 认证标签 128 位
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    private final SecretKeySpec keySpec;
    private final SecureRandom random = new SecureRandom();

    public AesFieldCipher(@Value("${sellm.crypto.aes-key:}") String aesKey) {
        if (aesKey == null || aesKey.getBytes(StandardCharsets.UTF_8).length != 32) {
            throw new IllegalArgumentException(
                "sellm.crypto.aes-key 必须是 32 字节(AES-256),当前长度非法或未配置");
        }
        this.keySpec = new SecretKeySpec(aesKey.getBytes(StandardCharsets.UTF_8), "AES");
    }

    @Override
    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LENGTH_BIT, iv));
            byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            // 拼 IV + 密文,整体 Base64
            byte[] combined = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ct, 0, combined, iv.length, ct.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("加密失败", e);
        }
    }

    @Override
    public String decrypt(String ciphertext) {
        try {
            byte[] combined = Base64.getDecoder().decode(ciphertext);
            byte[] iv = new byte[IV_LENGTH];
            byte[] ct = new byte[combined.length - IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
            System.arraycopy(combined, IV_LENGTH, ct, 0, ct.length);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LENGTH_BIT, iv));
            return new String(cipher.doFinal(ct), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("解密失败", e);
        }
    }
}
```

- [x] **Step 5: 运行测试,确认通过**

Run: `cd "D:/works/test/SELLM/backend" && ./mvnw -q test -Dtest=AesFieldCipherTest`
Expected: 4 个测试全部 PASS。

- [x] **Step 6: Commit**

```bash
cd "D:/works/test/SELLM" && git add backend/src/main/java/com/sellm/common/crypto/ backend/src/test/java/com/sellm/common/crypto/ && git commit -q -m "feat(crypto): AES-256-GCM 字段级加密,密钥缺失启动失败"
```

---
### Task 5: Child 儿童档案落库(加密写/解密读,H2 集成测试)

Child 实体落库。身份字段(姓名)经 FieldCipher 加密存 `name_enc` 列,读出时解密。ChildRepository 封装"加密→存、取→解密",使上层拿到的始终是明文,DB 里存的始终是密文。

**Files:**
- Modify: `backend/src/main/resources/schema.sql`(追加 child 表)
- Create: `backend/src/main/java/com/sellm/child/Child.java`
- Create: `backend/src/main/java/com/sellm/child/ChildMapper.java`
- Create: `backend/src/main/resources/mybatis/ChildMapper.xml`
- Create: `backend/src/main/java/com/sellm/child/ChildRepository.java`
- Test: `backend/src/test/java/com/sellm/child/ChildRepositoryTest.java`

- [x] **Step 1: schema.sql 追加 child 表**

在 `schema.sql` 末尾的 `-- (Task 5 追加 child 表)` 注释下追加 child 表;同时**移除 Task 3 加的占位行 `SELECT 1;`**(现在有真实建表语句,脚本不再为空)。追加:
```sql
CREATE TABLE IF NOT EXISTS child (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    name_enc     VARCHAR(512) NOT NULL,          -- 姓名密文(Base64)
    disorder_type VARCHAR(64) NOT NULL,           -- 障碍类型,如 ASD
    org_id       BIGINT,                          -- 所属机构(后续计划接 organization 表)
    created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

- [x] **Step 2: 创建 Child 实体(领域层持明文)**

`Child.java`:
```java
package com.sellm.child;

public class Child {
    private Long id;
    private String name;          // 明文姓名(领域层)
    private String disorderType;  // 障碍类型
    private Long orgId;

    public Child() {
    }

    public Child(Long id, String name, String disorderType, Long orgId) {
        this.id = id;
        this.name = name;
        this.disorderType = disorderType;
        this.orgId = orgId;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDisorderType() { return disorderType; }
    public void setDisorderType(String disorderType) { this.disorderType = disorderType; }
    public Long getOrgId() { return orgId; }
    public void setOrgId(Long orgId) { this.orgId = orgId; }
}
```

- [x] **Step 3: 创建 Mapper 接口(以密文列读写,DTO 用 Map 避免领域实体泄露密文语义)**

`ChildMapper.java`:
```java
package com.sellm.child;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.Map;

@Mapper
public interface ChildMapper {

    /** 插入,参数含 nameEnc(密文)、disorderType、orgId;插入后回填自增 id 到 map 的 "id" */
    void insert(Map<String, Object> row);

    /** 按 id 查,返回含 id/nameEnc/disorderType/orgId 的 map;无则 null */
    Map<String, Object> findById(@Param("id") Long id);
}
```

- [x] **Step 4: 创建 Mapper XML**

`backend/src/main/resources/mybatis/ChildMapper.xml`(用显式 `<resultMap>` 而非 `resultType="map"`——H2 会把列标签大写成 `NAMEENC`,导致 `row.get("nameEnc")` 取 null;resultMap 显式 column→property 映射,大小写无关):
```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.sellm.child.ChildMapper">

    <resultMap id="childRowMap" type="map">
        <id column="id" property="id"/>
        <result column="name_enc" property="nameEnc"/>
        <result column="disorder_type" property="disorderType"/>
        <result column="org_id" property="orgId"/>
    </resultMap>

    <insert id="insert" parameterType="map" useGeneratedKeys="true" keyProperty="id">
        INSERT INTO child (name_enc, disorder_type, org_id)
        VALUES (#{nameEnc}, #{disorderType}, #{orgId})
    </insert>

    <select id="findById" parameterType="long" resultMap="childRowMap">
        SELECT id, name_enc, disorder_type, org_id
        FROM child WHERE id = #{id}
    </select>

</mapper>
```

- [x] **Step 5: 写失败测试(H2 集成)**

`ChildRepositoryTest.java`:
```java
package com.sellm.child;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class ChildRepositoryTest {

    @Autowired
    private ChildRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void 保存后能按id读出明文姓名() {
        Child saved = repository.save(new Child(null, "小明", "ASD", 1L));
        assertThat(saved.getId()).isNotNull();

        Child loaded = repository.findById(saved.getId());
        assertThat(loaded.getName()).isEqualTo("小明");
        assertThat(loaded.getDisorderType()).isEqualTo("ASD");
        assertThat(loaded.getOrgId()).isEqualTo(1L);
    }

    @Test
    void 库中存储的是密文而非明文() {
        Child saved = repository.save(new Child(null, "张伟", "ASD", 1L));
        String stored = jdbc.queryForObject(
            "SELECT name_enc FROM child WHERE id = ?", String.class, saved.getId());
        assertThat(stored).isNotEqualTo("张伟");
        assertThat(stored).doesNotContain("张伟");
    }

    @Test
    void 查不到返回null() {
        assertThat(repository.findById(999999L)).isNull();
    }
}
```

- [x] **Step 6: 运行测试,确认失败**

Run: `cd "D:/works/test/SELLM/backend" && ./mvnw -q test -Dtest=ChildRepositoryTest`
Expected: 编译失败 —— `ChildRepository` 尚未创建。

- [x] **Step 7: 实现 ChildRepository**

`ChildRepository.java`:
```java
package com.sellm.child;

import com.sellm.common.crypto.FieldCipher;
import org.springframework.stereotype.Repository;
import java.util.HashMap;
import java.util.Map;

@Repository
public class ChildRepository {

    private final ChildMapper mapper;
    private final FieldCipher cipher;

    public ChildRepository(ChildMapper mapper, FieldCipher cipher) {
        this.mapper = mapper;
        this.cipher = cipher;
    }

    public Child save(Child child) {
        Map<String, Object> row = new HashMap<>();
        row.put("nameEnc", cipher.encrypt(child.getName()));
        row.put("disorderType", child.getDisorderType());
        row.put("orgId", child.getOrgId());
        mapper.insert(row);
        Object id = row.get("id");
        child.setId(((Number) id).longValue());
        return child;
    }

    public Child findById(Long id) {
        Map<String, Object> row = mapper.findById(id);
        if (row == null) {
            return null;
        }
        String name = cipher.decrypt((String) row.get("nameEnc"));
        Long orgId = row.get("orgId") == null ? null : ((Number) row.get("orgId")).longValue();
        return new Child(((Number) row.get("id")).longValue(),
            name, (String) row.get("disorderType"), orgId);
    }
}
```

- [x] **Step 8: 运行测试,确认通过**

Run: `cd "D:/works/test/SELLM/backend" && ./mvnw -q test -Dtest=ChildRepositoryTest`
Expected: 3 个测试全部 PASS。库里存密文、读出明文。

- [x] **Step 9: Commit**

```bash
cd "D:/works/test/SELLM" && git add backend/src/main/resources/schema.sql backend/src/main/java/com/sellm/child/ backend/src/main/resources/mybatis/ChildMapper.xml backend/src/test/java/com/sellm/child/ && git commit -q -m "feat(child): 儿童档案落库,姓名加密存储、解密读出"
```

---
### Task 6: 知识库落库 + DbRagRetriever(修复启动隐患 #1,H2 集成)

KnowledgeDoc 落库;新增 `DbRagRetriever` 从数据库加载文档做关键词检索,标 `@Primary` 成为默认 RagRetriever 实现——它不需要注入 `List<KnowledgeDoc>` bean,从根本上解决计划一的上下文启动隐患。report/iep 服务对 RagRetriever 接口无感知,自动用上 DbRagRetriever。

**Files:**
- Modify: `backend/src/main/resources/schema.sql`(追加 knowledge_doc 表)
- Create: `backend/src/main/java/com/sellm/rag/KnowledgeDocMapper.java`
- Create: `backend/src/main/resources/mybatis/KnowledgeDocMapper.xml`
- Create: `backend/src/main/java/com/sellm/rag/DbRagRetriever.java`
- Test: `backend/src/test/java/com/sellm/rag/DbRagRetrieverTest.java`

- [x] **Step 1: schema.sql 追加 knowledge_doc 表**

在 `-- (Task 6 追加 knowledge_doc 表)` 注释下追加:
```sql
CREATE TABLE IF NOT EXISTS knowledge_doc (
    id        BIGINT PRIMARY KEY AUTO_INCREMENT,
    doc_id    VARCHAR(64)  NOT NULL,
    content   VARCHAR(4000) NOT NULL,
    source    VARCHAR(256),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

- [x] **Step 2: 创建 KnowledgeDocMapper**

`KnowledgeDocMapper.java`:
```java
package com.sellm.rag;

import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface KnowledgeDocMapper {

    /** 读取全部知识文档(第一版库内规模小,全量加载;后续换向量库) */
    List<KnowledgeDoc> findAll();
}
```

- [x] **Step 3: 创建 Mapper XML**

`backend/src/main/resources/mybatis/KnowledgeDocMapper.xml`:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.sellm.rag.KnowledgeDocMapper">

    <resultMap id="docMap" type="com.sellm.rag.KnowledgeDoc">
        <constructor>
            <arg column="doc_id" javaType="java.lang.String"/>
            <arg column="content" javaType="java.lang.String"/>
            <arg column="source" javaType="java.lang.String"/>
        </constructor>
    </resultMap>

    <select id="findAll" resultMap="docMap">
        SELECT doc_id, content, source FROM knowledge_doc
    </select>

</mapper>
```
注:`KnowledgeDoc` 是计划一的不可变类,构造器 `KnowledgeDoc(docId, content, source)`,故用 `<constructor>` 映射。

- [x] **Step 4: 写失败测试(H2 集成,用 JdbcTemplate 插种子数据)**

`DbRagRetrieverTest.java`:
```java
package com.sellm.rag;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class DbRagRetrieverTest {

    @Autowired
    private DbRagRetriever retriever;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        jdbc.update("DELETE FROM knowledge_doc");
        jdbc.update("INSERT INTO knowledge_doc(doc_id, content, source) VALUES (?,?,?)",
            "d1", "孤独症社交干预策略:结构化教学", "手册A");
        jdbc.update("INSERT INTO knowledge_doc(doc_id, content, source) VALUES (?,?,?)",
            "d2", "CARS 量表解读:总分与分段", "手册B");
        jdbc.update("INSERT INTO knowledge_doc(doc_id, content, source) VALUES (?,?,?)",
            "d3", "言语训练通用方法", "手册C");
    }

    @Test
    void 从库按关键词召回相关文档() {
        List<KnowledgeDoc> docs = retriever.retrieve("CARS 解读", 2);
        assertThat(docs).isNotEmpty();
        assertThat(docs.get(0).getDocId()).isEqualTo("d2");
    }

    @Test
    void 限制返回数量为topK() {
        List<KnowledgeDoc> docs = retriever.retrieve("干预 解读 训练", 2);
        assertThat(docs).hasSizeLessThanOrEqualTo(2);
    }

    @Test
    void 无匹配返回空列表() {
        assertThat(retriever.retrieve("微积分", 3)).isEmpty();
    }
}
```

- [x] **Step 5: 运行测试,确认失败**

Run: `cd "D:/works/test/SELLM/backend" && ./mvnw -q test -Dtest=DbRagRetrieverTest`
Expected: 编译失败 —— `DbRagRetriever` 尚未创建。

- [x] **Step 6: 实现 DbRagRetriever**

`DbRagRetriever.java`(检索算法与 InMemory 一致,差别只在数据来源是 DB):
```java
package com.sellm.rag;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Primary
@Component
public class DbRagRetriever implements RagRetriever {

    private final KnowledgeDocMapper mapper;

    public DbRagRetriever(KnowledgeDocMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<KnowledgeDoc> retrieve(String query, int topK) {
        List<KnowledgeDoc> docs = mapper.findAll();
        String[] terms = query.trim().split("\\s+");
        List<Scored> scored = new ArrayList<>();
        for (KnowledgeDoc doc : docs) {
            int score = 0;
            for (String term : terms) {
                if (!term.isBlank() && doc.getContent().contains(term)) {
                    score++;
                }
            }
            if (score > 0) {
                scored.add(new Scored(doc, score));
            }
        }
        scored.sort(Comparator.comparingInt((Scored s) -> s.score).reversed());

        List<KnowledgeDoc> result = new ArrayList<>();
        for (int i = 0; i < Math.min(topK, scored.size()); i++) {
            result.add(scored.get(i).doc);
        }
        return result;
    }

    private static class Scored {
        final KnowledgeDoc doc;
        final int score;
        Scored(KnowledgeDoc doc, int score) {
            this.doc = doc;
            this.score = score;
        }
    }
}
```

- [x] **Step 7: 运行测试,确认通过**

Run: `cd "D:/works/test/SELLM/backend" && ./mvnw -q test -Dtest=DbRagRetrieverTest`
Expected: 3 个测试全部 PASS。

- [x] **Step 8: Commit**

```bash
cd "D:/works/test/SELLM" && git add backend/src/main/resources/schema.sql backend/src/main/java/com/sellm/rag/ backend/src/main/resources/mybatis/KnowledgeDocMapper.xml backend/src/test/java/com/sellm/rag/DbRagRetrieverTest.java && git commit -q -m "feat(rag): 知识库落库 + DbRagRetriever(@Primary),修复上下文启动隐患#1"
```

---
### Task 7: 量表落库 ScaleRepository(组装聚合,H2 集成)

量表三件套(Scale 定义 / ScaleItem 题目 / ScoreBand 计分分段)落库,ScaleRepository 把三张表组装成计划一的 `Scale` 聚合对象(供计分引擎使用)。本任务只做"按 scaleId 读出完整 Scale";量表录入(写)在后续管理端计划。

**Files:**
- Modify: `backend/src/main/resources/schema.sql`(追加 scale 三表)
- Create: `backend/src/main/java/com/sellm/scale/ScaleMapper.java`
- Create: `backend/src/main/resources/mybatis/ScaleMapper.xml`
- Create: `backend/src/main/java/com/sellm/scale/ScaleRepository.java`
- Create: `backend/src/test/resources/scale-seed.sql`
- Test: `backend/src/test/java/com/sellm/scale/ScaleRepositoryTest.java`

- [x] **Step 1: schema.sql 追加三表**

在 `-- (Task 7 追加 scale / scale_item / score_band 表)` 注释下追加:
```sql
CREATE TABLE IF NOT EXISTS scale (
    scale_id  VARCHAR(64) PRIMARY KEY,
    name      VARCHAR(128) NOT NULL,
    version   VARCHAR(32)  NOT NULL
);

CREATE TABLE IF NOT EXISTS scale_item (
    id        BIGINT PRIMARY KEY AUTO_INCREMENT,
    scale_id  VARCHAR(64) NOT NULL,
    item_id   VARCHAR(64) NOT NULL,
    stem      VARCHAR(512) NOT NULL,
    dimension VARCHAR(128)
);

CREATE TABLE IF NOT EXISTS score_band (
    id             BIGINT PRIMARY KEY AUTO_INCREMENT,
    scale_id       VARCHAR(64) NOT NULL,
    lower_bound    DOUBLE NOT NULL,
    upper_bound    DOUBLE NOT NULL,
    label          VARCHAR(128) NOT NULL,
    interpretation VARCHAR(512)
);
```
注:`lower`/`upper` 在部分库是保留字,故列名用 `lower_bound`/`upper_bound`。

- [x] **Step 2: 创建 ScaleMapper(分三查,Repository 组装)**

`ScaleMapper.java`:
```java
package com.sellm.scale;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;

@Mapper
public interface ScaleMapper {

    /** 量表定义:含 scaleId/name/version;无则 null */
    Map<String, Object> findScaleById(@Param("scaleId") String scaleId);

    /** 量表题目:每行含 itemId/stem/dimension */
    List<Map<String, Object>> findItems(@Param("scaleId") String scaleId);

    /** 计分分段:每行含 lowerBound/upperBound/label/interpretation */
    List<Map<String, Object>> findBands(@Param("scaleId") String scaleId);
}
```

- [x] **Step 3: 创建 Mapper XML**

`backend/src/main/resources/mybatis/ScaleMapper.xml`(用显式 `<resultMap>` 映射 column→property,避免 H2 列标签大写导致 map key 取不到;与 ChildMapper 同样处理):
```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.sellm.scale.ScaleMapper">

    <resultMap id="scaleHeadMap" type="map">
        <id column="scale_id" property="scaleId"/>
        <result column="name" property="name"/>
        <result column="version" property="version"/>
    </resultMap>

    <resultMap id="itemRowMap" type="map">
        <result column="item_id" property="itemId"/>
        <result column="stem" property="stem"/>
        <result column="dimension" property="dimension"/>
    </resultMap>

    <resultMap id="bandRowMap" type="map">
        <result column="lower_bound" property="lowerBound"/>
        <result column="upper_bound" property="upperBound"/>
        <result column="label" property="label"/>
        <result column="interpretation" property="interpretation"/>
    </resultMap>

    <select id="findScaleById" parameterType="string" resultMap="scaleHeadMap">
        SELECT scale_id, name, version FROM scale WHERE scale_id = #{scaleId}
    </select>

    <select id="findItems" parameterType="string" resultMap="itemRowMap">
        SELECT item_id, stem, dimension FROM scale_item WHERE scale_id = #{scaleId}
    </select>

    <select id="findBands" parameterType="string" resultMap="bandRowMap">
        SELECT lower_bound, upper_bound, label, interpretation
        FROM score_band WHERE scale_id = #{scaleId}
    </select>

</mapper>
```

- [x] **Step 4: 测试种子数据**

`backend/src/test/resources/scale-seed.sql`(顶部先 DELETE 保证幂等——测试 H2 是共享内存库,@Sql 每个测试方法前各跑一次,不先删会主键冲突):
```sql
DELETE FROM score_band;
DELETE FROM scale_item;
DELETE FROM scale;
INSERT INTO scale (scale_id, name, version) VALUES ('cars', 'CARS', 'v1');
INSERT INTO scale_item (scale_id, item_id, stem, dimension) VALUES ('cars', 'q1', '社交', '社交');
INSERT INTO scale_item (scale_id, item_id, stem, dimension) VALUES ('cars', 'q2', '沟通', '沟通');
INSERT INTO score_band (scale_id, lower_bound, upper_bound, label, interpretation)
    VALUES ('cars', 0, 3, '正常', '未见明显异常');
INSERT INTO score_band (scale_id, lower_bound, upper_bound, label, interpretation)
    VALUES ('cars', 4, 7, '轻-中度', '建议进一步评估');
```

- [x] **Step 5: 写失败测试(H2 集成,@Sql 装载种子)**

`ScaleRepositoryTest.java`:
```java
package com.sellm.scale;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/scale-seed.sql", config = @SqlConfig(encoding = "UTF-8"))
class ScaleRepositoryTest {

    @Autowired
    private ScaleRepository repository;

    @Test
    void 按scaleId组装出完整量表() {
        Scale cars = repository.findById("cars");
        assertThat(cars).isNotNull();
        assertThat(cars.getName()).isEqualTo("CARS");
        assertThat(cars.getItems()).hasSize(2);
        assertThat(cars.getScoringRule()).isNotNull();
        assertThat(cars.getScoringRule().getBands()).hasSize(2);
    }

    @Test
    void 组装的量表可直接用于计分引擎() {
        Scale cars = repository.findById("cars");
        AssessmentResult r = new DefaultScoringEngine().score(cars,
            List.of(new Answer("q1", 2), new Answer("q2", 3)));
        assertThat(r.getBandLabel()).isEqualTo("轻-中度");
    }

    @Test
    void 不存在的量表返回null() {
        assertThat(repository.findById("nope")).isNull();
    }
}
```

- [x] **Step 6: 运行测试,确认失败**

Run: `cd "D:/works/test/SELLM/backend" && ./mvnw -q test -Dtest=ScaleRepositoryTest`
Expected: 编译失败 —— `ScaleRepository` 尚未创建。

- [x] **Step 7: 实现 ScaleRepository**

`ScaleRepository.java`:
```java
package com.sellm.scale;

import org.springframework.stereotype.Repository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Repository
public class ScaleRepository {

    private final ScaleMapper mapper;

    public ScaleRepository(ScaleMapper mapper) {
        this.mapper = mapper;
    }

    public Scale findById(String scaleId) {
        Map<String, Object> head = mapper.findScaleById(scaleId);
        if (head == null) {
            return null;
        }

        List<ScaleItem> items = new ArrayList<>();
        for (Map<String, Object> row : mapper.findItems(scaleId)) {
            items.add(new ScaleItem(
                (String) row.get("itemId"),
                (String) row.get("stem"),
                (String) row.get("dimension")));
        }

        List<ScoreBand> bands = new ArrayList<>();
        for (Map<String, Object> row : mapper.findBands(scaleId)) {
            bands.add(new ScoreBand(
                ((Number) row.get("lowerBound")).doubleValue(),
                ((Number) row.get("upperBound")).doubleValue(),
                (String) row.get("label"),
                (String) row.get("interpretation")));
        }
        ScoringRule rule = bands.isEmpty() ? null : new ScoringRule(bands);

        return new Scale(
            (String) head.get("scaleId"),
            (String) head.get("name"),
            (String) head.get("version"),
            items, rule);
    }
}
```

- [x] **Step 8: 运行测试,确认通过**

Run: `cd "D:/works/test/SELLM/backend" && ./mvnw -q test -Dtest=ScaleRepositoryTest`
Expected: 3 个测试全部 PASS。组装的 Scale 能直接喂给计分引擎得到"轻-中度"。

- [x] **Step 9: Commit**

```bash
cd "D:/works/test/SELLM" && git add backend/src/main/resources/schema.sql backend/src/main/java/com/sellm/scale/ScaleMapper.java backend/src/main/java/com/sellm/scale/ScaleRepository.java backend/src/main/resources/mybatis/ScaleMapper.xml backend/src/test/resources/scale-seed.sql backend/src/test/java/com/sellm/scale/ScaleRepositoryTest.java && git commit -q -m "feat(scale): 量表三件套落库,组装为 Scale 聚合供计分引擎"
```

---
### Task 8: 清理 RAG 脚手架 + 上下文启动守护测试 + 全量回归

两件事:① 清理 Task 3/6 留下的过渡件——既然 DbRagRetriever(@Primary)已是真正实现,删除 InMemoryRagRetriever、其单测 InMemoryRagRetrieverTest、以及 RagConfig 里的空 knowledgeDocs 脚手架 bean(连同 RagConfig 若清空则一并删),让"根治"真正干净。② 补一个显式 `@SpringBootTest` 上下文测试,守住"应用能启动 + 关键 bean 装配正确",这是计划一漏掉、导致启动隐患没被发现的根因。最后跑全量确认无回归。

**Files:**
- Delete: `backend/src/main/java/com/sellm/rag/InMemoryRagRetriever.java`
- Delete: `backend/src/test/java/com/sellm/rag/InMemoryRagRetrieverTest.java`
- Delete: `backend/src/main/java/com/sellm/config/RagConfig.java`(其唯一用途是给 InMemory 喂 bean)
- Modify: `backend/src/test/java/com/sellm/integration/AssessmentToIepFlowTest.java`(把 InMemory 桩换成 RagRetriever lambda)
- Create: `backend/src/test/java/com/sellm/context/ApplicationContextTest.java`

- [x] **Step 1: 删除过渡件并确认编译**

删除上述三个文件。删除前先用 Grep 确认 `InMemoryRagRetriever` 和 `knowledgeDocs` 已无其他引用——**注意搜索范围要含 src/test**:计划一的 `integration/AssessmentToIepFlowTest.java` 把 `InMemoryRagRetriever` 当内存桩使用。该全链路测试有价值,不删;把其中 `new InMemoryRagRetriever(docs)` 替换为等价的 `RagRetriever` lambda 桩(`(query, topK) -> knowledgeDocs`),保留全部断言。(此点在 Task 8 实现时发现并处理,计划已同步。)
Run: `cd "D:/works/test/SELLM/backend" && ./mvnw -q compile`
Expected: BUILD SUCCESS(无对已删类的引用)。

- [x] **Step 2: 写上下文守护测试**

`ApplicationContextTest.java`:
```java
package com.sellm.context;

import com.sellm.rag.DbRagRetriever;
import com.sellm.rag.RagRetriever;
import com.sellm.report.ReportService;
import com.sellm.iep.IepService;
import com.sellm.child.ChildRepository;
import com.sellm.scale.ScaleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class ApplicationContextTest {

    @Autowired
    private RagRetriever ragRetriever;

    @Autowired
    private ReportService reportService;

    @Autowired
    private IepService iepService;

    @Autowired
    private ChildRepository childRepository;

    @Autowired
    private ScaleRepository scaleRepository;

    @Test
    void 上下文成功启动且关键bean装配() {
        assertThat(reportService).isNotNull();
        assertThat(iepService).isNotNull();
        assertThat(childRepository).isNotNull();
        assertThat(scaleRepository).isNotNull();
    }

    @Test
    void RagRetriever默认实现为DbRagRetriever() {
        // InMemory 已删除,DbRagRetriever 成为唯一(且 @Primary)实现
        assertThat(ragRetriever).isInstanceOf(DbRagRetriever.class);
    }
}
```

- [x] **Step 3: 运行上下文测试,确认通过**

Run: `cd "D:/works/test/SELLM/backend" && ./mvnw -q test -Dtest=ApplicationContextTest`
Expected: 2 个测试 PASS。上下文启动正常,RagRetriever 注入的是 DbRagRetriever。

- [x] **Step 4: 运行全量测试,确认无回归**

Run: `cd "D:/works/test/SELLM/backend" && ./mvnw -q test`
Expected: 全部 PASS。删掉了 InMemoryRagRetrieverTest(3 个)、新增 ApplicationContextTest(2 个),净变化 -1。预期总数 = 此前 42(Task7 后)- 3 + 2 = 41。(以实际为准,不得有 Failure/Error。)

- [x] **Step 5: Commit**

```bash
cd "D:/works/test/SELLM" && git add -A backend/ && git commit -q -m "refactor(rag): 删除 InMemory 过渡实现与脚手架 bean;test: 补上下文启动守护"
```
(用 `git add -A backend/` 以纳入文件删除。)

---

## 后续计划(不在本计划范围)

1. **REST 层与认证**:控制器、三角色 RBAC、登录;User/Organization 实体落库;Child 增删改查 API。
2. **业务记录持久化**:Assessment/Report/Iep/IepGoal/IepVersion/ProgressRecord 落库,与 REST 层一起。
3. **真实 AI 接入**:合规企业版 API 实现 AiModel 替换 MockAiModel;真实向量库替换 DbRagRetriever 的关键词检索。
4. **量表录入**:管理端录入量表三件套的写接口与界面。
5. **防御式编程统一硬化 / 计分引擎硬化**:延续计划一记录的待办。
6. **运行时配置硬化**(Task 3 审查提出):`application.yml` 的 `DB_PASSWORD` 空默认值仅适合本地开发,生产应强制环境变量;本地启动文档(CREATE DATABASE / docker-compose、test profile 说明)待补。注:`@MapperScan` 用 `annotationClass = Mapper.class` 限定只扫 @Mapper 接口(Task 5 修正),宽 basePackages 配合注解过滤是有意的——本项目按"职责分包"把 Mapper 放各业务包内。
7. **持久化实体不可变性**(Task 5 审查提出):Child 等 MyBatis 映射实体当前用可变 setter、save() 回填 id 到入参——这是 MyBatis/Spring-Data 的常见惯例,第一版保留;若后续追求领域不可变,可改 builder + 返回新实例。

---

## 自检结论

- **Spec/审查项覆盖**:
  - 设计第 6 节数据模型:Child(加密)、Scale 三件套、KnowledgeDoc 已落库;Assessment/Report/Iep 记录持久化与 User/Org 显式列入后续计划(它们绑定 REST/认证)。
  - 设计第 3 节红线"身份信息加密留存本地":Task 4+5 落地(AES-GCM 加密存储,库里存密文)。
  - 计划一审查三项:#1 启动隐患(Task 3 提供脚手架 knowledgeDocs bean 解除阻塞 + Task 6 DbRagRetriever 作为 @Primary 根治 + Task 8 上下文测试守护)、#2 脱敏校验扩围学校(Task 1)、#3 IEP 定稿保存内容(Task 2),全部修复。
- **占位符**:无 TBD/TODO,每个代码步骤含完整可编译代码。schema.sql 的"(Task N 追加)"是有意的渐进锚点,非占位符。
- **类型一致性**:跨任务核对 —— `FieldCipher.encrypt/decrypt`、`ChildRepository.save/findById`、`Child` 构造器 `(Long,String,String,Long)`、`KnowledgeDoc(docId,content,source)`(构造器映射)、`ScaleRepository.findById` 返回计划一的 `Scale`、`DefaultScoringEngine.score`、`Iep.finalizePlan(String)`(Task 2 改签名后 IepServiceTest 已同步)在定义与使用处一致。
- **测试策略**:H2 内存库(MySQL 兼容模式),schema.sql 测试期自动建表;运行时 mysql profile 连真实库。延续"测试不依赖外部基础设施"。
