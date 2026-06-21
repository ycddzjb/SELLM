# agent-teaching 教学训练 Agent 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现教学训练 Agent —— Java sellm-agent-teaching(IEP→分层教案、适配课件的生成草案→编辑→定稿编排 + 持久化 + X-User-Id 鉴权 + 脱敏)+ Python 智能层 teaching 编排(mock LLM)+ 课件产物经对象存储落盘(默认 Noop),复用 qa 模板。

**Architecture:** 镜像 agent-qa 的 Java 编排 + Python 智能结构。教案/课件走 DRAFT→编辑→FINALIZED 状态机(复用 IEP 草案定稿模式)。IEP 内容由前端请求体传入(teaching 不调 assessment)。课件 finalize 时产物经 ObjectStorage 落盘。脱敏(Anonymizer 自动装配,已在 core)出网前必经。新增:ObjectStorage 在 core 自动装配(把 NoopObjectStorage 迁入 core),使 agent-teaching 免依赖 backend 即得存储 bean。

**Tech Stack:** Java 17 / Spring Boot 3.2.5 / MyBatis / H2(dev/test)/ Python 3.11 / FastAPI / httpx

## Global Constraints

- Java 17,Spring Boot 3.2.5;agent-teaching 依赖 **sellm-common-core**(不依赖 sellm-common-backend,无 spring-security)
- 用户身份来自网关注入 `X-User-Id` 头(`@RequestHeader`),非 SecurityContext;缺头→401
- 沿用 report/iep 四件套 MyBatis 风格(Mapper 收发 `Map<String,Object>`;XML resultMap snake→camel;insert useGeneratedKeys 写回 id;Repository 手动 `((Number)..).longValue()` 转换)
- 复用 qa 已沉淀:HttpSmartLayerClient(HTTP/1.1,protected send)、X-User-Id 鉴权、行级权限、QaExceptionHandler 同构、Anonymizer 自动装配(core 已有)
- 统一 `Result<T>` 信封;ErrorCode 用现有值(OK/ANONYMIZATION_FAILED/INVALID_INPUT/ACCESS_DENIED + qa 加的 NOT_FOUND/UNAUTHORIZED)
- 测试:`@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")`;H2 内存库
- **三红线**:AI 只产草案(DRAFT→编辑→FINALIZED,课件产物落存储仅 finalize)、出网必经脱敏(失败硬阻断 ANONYMIZATION_FAILED)、Python 不持明文/不还原
- mock 不外联:LLM mock、ObjectStorage 默认 Noop(本地);真实可切换默认不启用
- **每步 `mvn clean install` 验证**(stale target 假绿);全 reactor 9 模块 SUCCESS;**backend 仍绿**(NoopObjectStorage 移 core 后重点回归 StorageConfigTest)
- Maven 在 Git Bash:`export PATH="/c/tools/apache-maven-3.9.6/bin:$PATH"`
- Python 真 3.11 不可用则静态审查
- 代码/配置变更后追加 `.claude/CLAUDE_CHANGES.md`

---

### Task 1: ObjectStorage 自动装配(NoopObjectStorage 迁 core + StorageAutoConfiguration)

**Files:**
- Move(git mv): `sellm-common-backend/src/main/java/com/sellm/storage/NoopObjectStorage.java` → `sellm-common-core/src/main/java/com/sellm/storage/NoopObjectStorage.java`
- Create: `sellm-common-core/src/main/java/com/sellm/storage/StorageAutoConfiguration.java`
- Modify: `sellm-common-core/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`(加一行)
- Test: `sellm-common-core/src/test/java/com/sellm/storage/NoopObjectStorageTest.java`

**Interfaces:**
- Produces: 依赖 sellm-common-core 的模块自动获得 `ObjectStorage` bean(默认 NoopObjectStorage,localDir 来自 `${sellm.storage.local-dir:data/media}`);`@ConditionalOnMissingBean` 使 backend 自己的 StorageConfig @Bean 优先(backend 不受影响)
- Consumes: 已有 `ObjectStorage` 接口(core)、`AnonymizerAutoConfiguration` 模板

> **关键**:NoopObjectStorage 纯 JDK NIO、构造仅收 `String localDir`、不依赖 StorageProperties(已核实),可干净迁 core。迁移后 backend 的 `StorageConfig`(在 backend,`new NoopObjectStorage(props.getLocalDir())`)与 `StorageConfigTest`(`isInstanceOf(NoopObjectStorage.class)`)仍引用同包名 `com.sellm.storage.NoopObjectStorage`,经 core 传递解析,**import 不变、编译通过**。backend 经自身 StorageConfig 提供 ObjectStorage bean,core 的 auto-config `@ConditionalOnMissingBean` 退让 → backend 恰一个 bean。

- [ ] **Step 1: git mv NoopObjectStorage 到 core**

```bash
cd /d/works/test/SELLM/dev_workspace
git mv sellm-common-backend/src/main/java/com/sellm/storage/NoopObjectStorage.java \
       sellm-common-core/src/main/java/com/sellm/storage/NoopObjectStorage.java
```
(包声明 `package com.sellm.storage;` 不变,无需改文件内容。)

- [ ] **Step 2: 创建 StorageAutoConfiguration**

`sellm-common-core/src/main/java/com/sellm/storage/StorageAutoConfiguration.java`:
```java
package com.sellm.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * 自动装配默认对象存储(NoopObjectStorage,本地落盘)。
 * 任何依赖 sellm-common-core 的模块无需组件扫描即可获得 ObjectStorage。
 * 若上层已定义 ObjectStorage bean(如 backend 的 StorageConfig 可切 MinIO),则本默认退让。
 */
@AutoConfiguration
public class StorageAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(ObjectStorage.class)
    public ObjectStorage noopObjectStorage(
            @Value("${sellm.storage.local-dir:data/media}") String localDir) {
        return new NoopObjectStorage(localDir);
    }
}
```

- [ ] **Step 3: 注册进 AutoConfiguration.imports**

在 `sellm-common-core/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 追加一行(保留现有 AnonymizerAutoConfiguration 行):
```
com.sellm.storage.StorageAutoConfiguration
```
文件内容变为:
```
com.sellm.anonymizer.AnonymizerAutoConfiguration
com.sellm.storage.StorageAutoConfiguration
```

- [ ] **Step 4: 写 NoopObjectStorage 测试(确认迁移后行为不变)**

`sellm-common-core/src/test/java/com/sellm/storage/NoopObjectStorageTest.java`:
```java
package com.sellm.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class NoopObjectStorageTest {

    @Test
    void 存取往返一致(@TempDir Path tmp) {
        NoopObjectStorage storage = new NoopObjectStorage(tmp.toString());
        byte[] data = "hello".getBytes();
        String key = storage.put("k1.txt", data, "text/plain");
        assertNotNull(key);
        assertArrayEquals(data, storage.get(key));
    }

    @Test
    void 取不存在返回null(@TempDir Path tmp) {
        NoopObjectStorage storage = new NoopObjectStorage(tmp.toString());
        assertNull(storage.get("missing.txt"));
    }
}
```

> 若 core 测试无 JUnit5:core 已有 RegexAnonymizerTest(qa 阶段加),说明 core 测试栈可用;沿用即可。若 put 返回的 key 与传入 key 不同(实现细节),按实际调整断言(实现者先读 NoopObjectStorage 的 put 语义)。

- [ ] **Step 5: 验证 core + backend(重点回归 backend StorageConfigTest)**

```bash
export PATH="/c/tools/apache-maven-3.9.6/bin:$PATH"
cd /d/works/test/SELLM/dev_workspace
mvn -pl sellm-common-core test -q 2>&1 | grep -E "Tests run:|BUILD" | tail -3
mvn -pl sellm-common-core,sellm-common-backend -am clean install -q && echo "CORE+BACKEND OK"
mvn -pl backend test 2>&1 | grep -E "Tests run:.*Failures|BUILD" | tail -3
```
Expected: core 测试绿(含 NoopObjectStorageTest);CORE+BACKEND OK;backend 242(或现值)全绿——**StorageConfigTest 仍通过**(NoopObjectStorage 经 core 解析)。

> 若 backend 报找不到 NoopObjectStorage 或 StorageConfigTest 失败:确认 NoopObjectStorage 已在 core 且包名未变、backend 依赖 core。报 BLOCKED 附错误。

- [ ] **Step 6: Commit**

```bash
git add sellm-common-core/ sellm-common-backend/
git commit -m "feat(teaching): ObjectStorage 自动装配(NoopObjectStorage 迁 core + StorageAutoConfiguration)"
```
---

### Task 2: agent-teaching 持久化基建(依赖 + H2 + schema + lesson_plan/courseware 四件套)

**Files:**
- Modify: `sellm-agent-teaching/pom.xml`(加 mybatis-spring-boot-starter / spring-boot-starter-jdbc / h2)
- Modify: `sellm-agent-teaching/src/main/resources/application.yml`(H2 数据源 + mybatis + schema init,合并入现有 spring 节点)
- Modify: `sellm-agent-teaching/src/test/resources/application.yml`(同上,合并入现有 spring 节点)
- Create: `sellm-agent-teaching/src/main/resources/schema.sql`
- Create: `sellm-agent-teaching/src/main/java/com/sellm/teaching/LessonPlan.java`
- Create: `sellm-agent-teaching/src/main/java/com/sellm/teaching/Courseware.java`
- Create: `sellm-agent-teaching/src/main/java/com/sellm/teaching/LessonPlanMapper.java`
- Create: `sellm-agent-teaching/src/main/java/com/sellm/teaching/CoursewareMapper.java`
- Create: `sellm-agent-teaching/src/main/resources/mybatis/LessonPlanMapper.xml`
- Create: `sellm-agent-teaching/src/main/resources/mybatis/CoursewareMapper.xml`
- Create: `sellm-agent-teaching/src/main/java/com/sellm/teaching/LessonPlanRepository.java`
- Create: `sellm-agent-teaching/src/main/java/com/sellm/teaching/CoursewareRepository.java`
- Test: `sellm-agent-teaching/src/test/java/com/sellm/teaching/TeachingRepositoryTest.java`

**Interfaces:**
- Produces:
  - `LessonPlan`(id, ownerId, childId, classId, sourceIepId, scene, mode, disorderType, aiDraft, content, status)
  - `Courseware`(id, ownerId, lessonPlanId, disorderType, aiDraft, content, storageKey, format, status)
  - `LessonPlanRepository.save/findById/listByOwner/update`、`CoursewareRepository.save/findById/update`
- Consumes: 无(纯持久化)

> 镜像 agent-qa 的 Task 1 持久化模式 + report/iep 四件套。两张表两套四件套。

- [ ] **Step 1: 加持久化依赖到 sellm-agent-teaching/pom.xml**

`<dependencies>` 加(同 qa Task1):
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
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <scope>runtime</scope>
        </dependency>
```

- [ ] **Step 2: 创建 schema.sql**

`sellm-agent-teaching/src/main/resources/schema.sql`:
```sql
CREATE TABLE IF NOT EXISTS lesson_plan (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    owner_id BIGINT NOT NULL,
    child_id BIGINT,
    class_id BIGINT,
    source_iep_id BIGINT,
    scene VARCHAR(16),
    mode VARCHAR(16),
    disorder_type VARCHAR(32),
    ai_draft TEXT,
    content TEXT,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS courseware (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    owner_id BIGINT NOT NULL,
    lesson_plan_id BIGINT NOT NULL,
    disorder_type VARCHAR(32),
    ai_draft TEXT,
    content TEXT,
    storage_key VARCHAR(255),
    format VARCHAR(16),
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
);
```

- [ ] **Step 3: 配置 H2(main + test application.yml)**

`sellm-agent-teaching/src/main/resources/application.yml` 的 `spring:` 下加(合并,不破坏 application.name/cloud):
```yaml
  datasource:
    url: jdbc:h2:mem:teaching_db;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE
    username: sa
    password:
    driver-class-name: org.h2.Driver
  sql:
    init:
      mode: always
      schema-locations: classpath:schema.sql
mybatis:
  mapper-locations: classpath*:mybatis/*.xml
  configuration:
    map-underscore-to-camel-case: true
```
`sellm-agent-teaching/src/test/resources/application.yml` 的 `spring:` 下加同样 datasource + sql.init + 顶层 mybatis(合并入已有 spring.cloud 禁 discovery 节点)。

- [ ] **Step 4: 写失败测试 TeachingRepositoryTest**

`sellm-agent-teaching/src/test/java/com/sellm/teaching/TeachingRepositoryTest.java`:
```java
package com.sellm.teaching;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class TeachingRepositoryTest {

    @Autowired LessonPlanRepository planRepo;
    @Autowired CoursewareRepository cwRepo;

    @Test
    void 教案保存回填id并能按owner查询() {
        LessonPlan p = new LessonPlan();
        p.setOwnerId(5L); p.setSourceIepId(34L);
        p.setScene("SCHOOL"); p.setMode("ONE_ON_ONE");
        p.setDisorderType("ASD"); p.setAiDraft("draft"); p.setContent("draft");
        p.setStatus("DRAFT");
        LessonPlan saved = planRepo.save(p);
        assertNotNull(saved.getId());

        List<LessonPlan> mine = planRepo.listByOwner(5L);
        assertEquals(1, mine.size());
        assertEquals("SCHOOL", mine.get(0).getScene());
    }

    @Test
    void 教案更新content与status() {
        LessonPlan p = new LessonPlan();
        p.setOwnerId(5L); p.setScene("HOME"); p.setMode("GROUP");
        p.setStatus("DRAFT"); p.setContent("v1");
        Long id = planRepo.save(p).getId();

        LessonPlan loaded = planRepo.findById(id);
        loaded.setContent("v2"); loaded.setStatus("FINALIZED");
        planRepo.update(loaded);

        LessonPlan after = planRepo.findById(id);
        assertEquals("v2", after.getContent());
        assertEquals("FINALIZED", after.getStatus());
    }

    @Test
    void 课件保存回填id并回填storageKey() {
        Courseware c = new Courseware();
        c.setOwnerId(5L); c.setLessonPlanId(1L); c.setDisorderType("ASD");
        c.setContent("cw"); c.setFormat("TEXT"); c.setStatus("DRAFT");
        Long id = cwRepo.save(c).getId();
        assertNotNull(id);

        Courseware loaded = cwRepo.findById(id);
        loaded.setStorageKey("media/cw-1.txt"); loaded.setStatus("FINALIZED");
        cwRepo.update(loaded);
        assertEquals("media/cw-1.txt", cwRepo.findById(id).getStorageKey());
    }
}
```

- [ ] **Step 5: 运行测试确认失败**

```bash
export PATH="/c/tools/apache-maven-3.9.6/bin:$PATH"
cd /d/works/test/SELLM/dev_workspace
mvn -pl sellm-agent-teaching test -Dtest=TeachingRepositoryTest 2>&1 | grep -E "ERROR|cannot find symbol|Tests run|BUILD" | tail -8
```
Expected: 编译失败(LessonPlan/Courseware/Repository 未定义)。

- [ ] **Step 6: 创建实体 LessonPlan**

`sellm-agent-teaching/src/main/java/com/sellm/teaching/LessonPlan.java`:
```java
package com.sellm.teaching;

public class LessonPlan {
    private Long id;
    private Long ownerId;
    private Long childId;
    private Long classId;
    private Long sourceIepId;
    private String scene;        // HOME/SCHOOL/ORG
    private String mode;         // ONE_ON_ONE/GROUP
    private String disorderType;
    private String aiDraft;
    private String content;
    private String status;       // DRAFT/FINALIZED

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }
    public Long getChildId() { return childId; }
    public void setChildId(Long childId) { this.childId = childId; }
    public Long getClassId() { return classId; }
    public void setClassId(Long classId) { this.classId = classId; }
    public Long getSourceIepId() { return sourceIepId; }
    public void setSourceIepId(Long sourceIepId) { this.sourceIepId = sourceIepId; }
    public String getScene() { return scene; }
    public void setScene(String scene) { this.scene = scene; }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public String getDisorderType() { return disorderType; }
    public void setDisorderType(String disorderType) { this.disorderType = disorderType; }
    public String getAiDraft() { return aiDraft; }
    public void setAiDraft(String aiDraft) { this.aiDraft = aiDraft; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
```

- [ ] **Step 7: 创建实体 Courseware**

`sellm-agent-teaching/src/main/java/com/sellm/teaching/Courseware.java`:
```java
package com.sellm.teaching;

public class Courseware {
    private Long id;
    private Long ownerId;
    private Long lessonPlanId;
    private String disorderType;
    private String aiDraft;
    private String content;
    private String storageKey;
    private String format;       // TEXT/HTML
    private String status;       // DRAFT/FINALIZED

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }
    public Long getLessonPlanId() { return lessonPlanId; }
    public void setLessonPlanId(Long lessonPlanId) { this.lessonPlanId = lessonPlanId; }
    public String getDisorderType() { return disorderType; }
    public void setDisorderType(String disorderType) { this.disorderType = disorderType; }
    public String getAiDraft() { return aiDraft; }
    public void setAiDraft(String aiDraft) { this.aiDraft = aiDraft; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getStorageKey() { return storageKey; }
    public void setStorageKey(String storageKey) { this.storageKey = storageKey; }
    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
```

- [ ] **Step 8: 创建 Mapper 接口(2 个)**

`sellm-agent-teaching/src/main/java/com/sellm/teaching/LessonPlanMapper.java`:
```java
package com.sellm.teaching;

import org.apache.ibatis.annotations.Mapper;
import java.util.List;
import java.util.Map;

@Mapper
public interface LessonPlanMapper {
    void insert(Map<String, Object> row);
    Map<String, Object> findById(Long id);
    List<Map<String, Object>> findByOwnerId(Long ownerId);
    void update(Map<String, Object> row);
}
```

`sellm-agent-teaching/src/main/java/com/sellm/teaching/CoursewareMapper.java`:
```java
package com.sellm.teaching;

import org.apache.ibatis.annotations.Mapper;
import java.util.Map;

@Mapper
public interface CoursewareMapper {
    void insert(Map<String, Object> row);
    Map<String, Object> findById(Long id);
    void update(Map<String, Object> row);
}
```

- [ ] **Step 9: 创建 Mapper XML(2 个)**

`sellm-agent-teaching/src/main/resources/mybatis/LessonPlanMapper.xml`:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.sellm.teaching.LessonPlanMapper">
    <resultMap id="planMap" type="map">
        <id column="id" property="id"/>
        <result column="owner_id" property="ownerId"/>
        <result column="child_id" property="childId"/>
        <result column="class_id" property="classId"/>
        <result column="source_iep_id" property="sourceIepId"/>
        <result column="scene" property="scene"/>
        <result column="mode" property="mode"/>
        <result column="disorder_type" property="disorderType"/>
        <result column="ai_draft" property="aiDraft"/>
        <result column="content" property="content"/>
        <result column="status" property="status"/>
    </resultMap>
    <insert id="insert" parameterType="map" useGeneratedKeys="true" keyProperty="id">
        INSERT INTO lesson_plan (owner_id, child_id, class_id, source_iep_id, scene, mode, disorder_type, ai_draft, content, status)
        VALUES (#{ownerId}, #{childId}, #{classId}, #{sourceIepId}, #{scene}, #{mode}, #{disorderType}, #{aiDraft}, #{content}, #{status})
    </insert>
    <select id="findById" parameterType="long" resultMap="planMap">
        SELECT id, owner_id, child_id, class_id, source_iep_id, scene, mode, disorder_type, ai_draft, content, status
        FROM lesson_plan WHERE id = #{id} AND deleted = 0
    </select>
    <select id="findByOwnerId" parameterType="long" resultMap="planMap">
        SELECT id, owner_id, child_id, class_id, source_iep_id, scene, mode, disorder_type, ai_draft, content, status
        FROM lesson_plan WHERE owner_id = #{ownerId} AND deleted = 0 ORDER BY id DESC
    </select>
    <update id="update" parameterType="map">
        UPDATE lesson_plan SET content = #{content}, ai_draft = #{aiDraft}, status = #{status},
            updated_at = CURRENT_TIMESTAMP WHERE id = #{id}
    </update>
</mapper>
```

`sellm-agent-teaching/src/main/resources/mybatis/CoursewareMapper.xml`:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.sellm.teaching.CoursewareMapper">
    <resultMap id="cwMap" type="map">
        <id column="id" property="id"/>
        <result column="owner_id" property="ownerId"/>
        <result column="lesson_plan_id" property="lessonPlanId"/>
        <result column="disorder_type" property="disorderType"/>
        <result column="ai_draft" property="aiDraft"/>
        <result column="content" property="content"/>
        <result column="storage_key" property="storageKey"/>
        <result column="format" property="format"/>
        <result column="status" property="status"/>
    </resultMap>
    <insert id="insert" parameterType="map" useGeneratedKeys="true" keyProperty="id">
        INSERT INTO courseware (owner_id, lesson_plan_id, disorder_type, ai_draft, content, storage_key, format, status)
        VALUES (#{ownerId}, #{lessonPlanId}, #{disorderType}, #{aiDraft}, #{content}, #{storageKey}, #{format}, #{status})
    </insert>
    <select id="findById" parameterType="long" resultMap="cwMap">
        SELECT id, owner_id, lesson_plan_id, disorder_type, ai_draft, content, storage_key, format, status
        FROM courseware WHERE id = #{id} AND deleted = 0
    </select>
    <update id="update" parameterType="map">
        UPDATE courseware SET content = #{content}, ai_draft = #{aiDraft}, storage_key = #{storageKey},
            status = #{status}, updated_at = CURRENT_TIMESTAMP WHERE id = #{id}
    </update>
</mapper>
```

- [ ] **Step 10: 创建 Repository(2 个,镜像 report/qa)**

`sellm-agent-teaching/src/main/java/com/sellm/teaching/LessonPlanRepository.java`:
```java
package com.sellm.teaching;

import org.springframework.stereotype.Repository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class LessonPlanRepository {
    private final LessonPlanMapper mapper;
    public LessonPlanRepository(LessonPlanMapper mapper) { this.mapper = mapper; }

    public LessonPlan save(LessonPlan p) {
        Map<String, Object> row = toRow(p);
        mapper.insert(row);
        p.setId(((Number) row.get("id")).longValue());
        return p;
    }

    public LessonPlan findById(Long id) {
        Map<String, Object> row = mapper.findById(id);
        return row == null ? null : fromRow(row);
    }

    public List<LessonPlan> listByOwner(Long ownerId) {
        List<LessonPlan> out = new ArrayList<>();
        for (Map<String, Object> row : mapper.findByOwnerId(ownerId)) out.add(fromRow(row));
        return out;
    }

    public void update(LessonPlan p) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", p.getId());
        row.put("content", p.getContent());
        row.put("aiDraft", p.getAiDraft());
        row.put("status", p.getStatus());
        mapper.update(row);
    }

    private Map<String, Object> toRow(LessonPlan p) {
        Map<String, Object> row = new HashMap<>();
        row.put("ownerId", p.getOwnerId());
        row.put("childId", p.getChildId());
        row.put("classId", p.getClassId());
        row.put("sourceIepId", p.getSourceIepId());
        row.put("scene", p.getScene());
        row.put("mode", p.getMode());
        row.put("disorderType", p.getDisorderType());
        row.put("aiDraft", p.getAiDraft());
        row.put("content", p.getContent());
        row.put("status", p.getStatus());
        return row;
    }

    private LessonPlan fromRow(Map<String, Object> row) {
        LessonPlan p = new LessonPlan();
        p.setId(((Number) row.get("id")).longValue());
        p.setOwnerId(((Number) row.get("ownerId")).longValue());
        p.setChildId(row.get("childId") == null ? null : ((Number) row.get("childId")).longValue());
        p.setClassId(row.get("classId") == null ? null : ((Number) row.get("classId")).longValue());
        p.setSourceIepId(row.get("sourceIepId") == null ? null : ((Number) row.get("sourceIepId")).longValue());
        p.setScene((String) row.get("scene"));
        p.setMode((String) row.get("mode"));
        p.setDisorderType((String) row.get("disorderType"));
        p.setAiDraft((String) row.get("aiDraft"));
        p.setContent((String) row.get("content"));
        p.setStatus((String) row.get("status"));
        return p;
    }
}
```

`sellm-agent-teaching/src/main/java/com/sellm/teaching/CoursewareRepository.java`:
```java
package com.sellm.teaching;

import org.springframework.stereotype.Repository;
import java.util.HashMap;
import java.util.Map;

@Repository
public class CoursewareRepository {
    private final CoursewareMapper mapper;
    public CoursewareRepository(CoursewareMapper mapper) { this.mapper = mapper; }

    public Courseware save(Courseware c) {
        Map<String, Object> row = new HashMap<>();
        row.put("ownerId", c.getOwnerId());
        row.put("lessonPlanId", c.getLessonPlanId());
        row.put("disorderType", c.getDisorderType());
        row.put("aiDraft", c.getAiDraft());
        row.put("content", c.getContent());
        row.put("storageKey", c.getStorageKey());
        row.put("format", c.getFormat());
        row.put("status", c.getStatus());
        mapper.insert(row);
        c.setId(((Number) row.get("id")).longValue());
        return c;
    }

    public Courseware findById(Long id) {
        Map<String, Object> row = mapper.findById(id);
        if (row == null) return null;
        Courseware c = new Courseware();
        c.setId(((Number) row.get("id")).longValue());
        c.setOwnerId(((Number) row.get("ownerId")).longValue());
        c.setLessonPlanId(((Number) row.get("lessonPlanId")).longValue());
        c.setDisorderType((String) row.get("disorderType"));
        c.setAiDraft((String) row.get("aiDraft"));
        c.setContent((String) row.get("content"));
        c.setStorageKey((String) row.get("storageKey"));
        c.setFormat((String) row.get("format"));
        c.setStatus((String) row.get("status"));
        return c;
    }

    public void update(Courseware c) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", c.getId());
        row.put("content", c.getContent());
        row.put("aiDraft", c.getAiDraft());
        row.put("storageKey", c.getStorageKey());
        row.put("status", c.getStatus());
        mapper.update(row);
    }
}
```

- [ ] **Step 11: 运行测试确认通过**

```bash
export PATH="/c/tools/apache-maven-3.9.6/bin:$PATH"
cd /d/works/test/SELLM/dev_workspace
mvn -pl sellm-agent-teaching clean test 2>&1 | grep -E "Tests run:|BUILD" | tail -3
```
Expected: TeachingRepositoryTest 3 + 原 contextLoads 通过,`BUILD SUCCESS`。

- [ ] **Step 12: Commit**

```bash
git add sellm-agent-teaching/
git commit -m "feat(teaching): 持久化基建(H2+schema+lesson_plan/courseware 四件套)"
```
---

### Task 3: 智能层客户端 + TeachingAppService 编排 + Controllers(脱敏/状态机/鉴权/存储)

**Files:**
- Create: `sellm-agent-teaching/src/main/java/com/sellm/teaching/SmartLayerClient.java`(接口)
- Create: `sellm-agent-teaching/src/main/java/com/sellm/teaching/HttpSmartLayerClient.java`
- Create: `sellm-agent-teaching/src/main/java/com/sellm/teaching/SmartLayerProperties.java`
- Create: `sellm-agent-teaching/src/main/java/com/sellm/teaching/SmartLayerException.java`
- Create: `sellm-agent-teaching/src/main/java/com/sellm/teaching/dto/{GeneratePlanRequest,EditRequest,GenerateCoursewareRequest,PlanResponse,CoursewareResponse}.java`
- Create: `sellm-agent-teaching/src/main/java/com/sellm/teaching/TeachingAppService.java`
- Create: `sellm-agent-teaching/src/main/java/com/sellm/teaching/LessonPlanController.java`
- Create: `sellm-agent-teaching/src/main/java/com/sellm/teaching/CoursewareController.java`
- Create: `sellm-agent-teaching/src/main/java/com/sellm/teaching/UnauthorizedException.java`
- Create: `sellm-agent-teaching/src/main/java/com/sellm/teaching/TeachingExceptionHandler.java`
- Modify: `sellm-agent-teaching/src/main/java/com/sellm/teaching/TeachingApplication.java`(@EnableConfigurationProperties)
- Modify: `sellm-agent-teaching/src/main/resources/application.yml`(sellm.teaching.smart-layer 配置)
- Test: `sellm-agent-teaching/src/test/java/com/sellm/teaching/TeachingApiTest.java`
- Test: `sellm-agent-teaching/src/test/java/com/sellm/teaching/TeachingSanitizeHardBlockTest.java`

**Interfaces:**
- Consumes: Task2 实体/Repository,core 的 `Anonymizer`(自动装配)/`ObjectStorage`(Task1 自动装配)/`Result`/`ErrorCode`/`BusinessException`
- Produces: 教案/课件 REST 端点 + 编排(脱敏→Python→还原→状态机→存储)

> **复用 qa 模板**:SmartLayerClient/HttpSmartLayerClient/SmartLayerProperties/SmartLayerException/UnauthorizedException/TeachingExceptionHandler 与 qa 同构(包名换 teaching)。脱敏:`anonymizer.anonymize(text, List.of(), List.of())`(空 names/schools + 内置正则;已含 ID/手机/邮箱);AnonymizationException→硬阻断 ANONYMIZATION_FAILED。鉴权:`@RequestHeader("X-User-Id") Long userId`,缺→401;行级权限:资源 ownerId==userId,否则 403。

- [ ] **Step 1: 写失败测试 TeachingApiTest(教案+课件主流程)**

`sellm-agent-teaching/src/test/java/com/sellm/teaching/TeachingApiTest.java`:
```java
package com.sellm.teaching;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TeachingApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired StubSmartLayerClient stub;

    @TestConfiguration
    static class Stubs {
        @Bean @Primary
        StubSmartLayerClient stubSmartLayerClient() { return new StubSmartLayerClient(); }
    }

    static class StubSmartLayerClient implements SmartLayerClient {
        final AtomicReference<String> lastIep = new AtomicReference<>();
        volatile boolean throwError = false;
        @Override public String generate(String task, String iepContentOrPlan, String disorderType,
                                         String scene, String mode) {
            if (throwError) throw new SmartLayerException("down");
            lastIep.set(iepContentOrPlan);
            return "[AI 生成] " + task + " 文本";
        }
    }

    private Long createFinalizedPlan(long userId) throws Exception {
        var res = mvc.perform(post("/api/teaching/lesson-plans")
                .header("X-User-Id", String.valueOf(userId))
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of(
                    "scene", "SCHOOL", "mode", "ONE_ON_ONE", "disorderType", "ASD",
                    "iepContent", "长期目标:共同注意"))))
            .andExpect(status().isOk())
            .andReturn();
        Long id = json.readTree(res.getResponse().getContentAsString()).path("data").path("id").asLong();
        mvc.perform(post("/api/teaching/lesson-plans/" + id + "/finalize").header("X-User-Id", String.valueOf(userId)))
            .andExpect(status().isOk());
        return id;
    }

    @Test
    void 生成教案草案为DRAFT并调Python() throws Exception {
        stub.throwError = false;
        mvc.perform(post("/api/teaching/lesson-plans")
                .header("X-User-Id", "5")
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of(
                    "scene", "SCHOOL", "mode", "ONE_ON_ONE", "disorderType", "ASD",
                    "iepContent", "长期目标:提升共同注意"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("DRAFT"))
            .andExpect(jsonPath("$.data.content").value("[AI 生成] lesson_plan 文本"));
    }

    @Test
    void 教案编辑后定稿为FINALIZED() throws Exception {
        Long id = createFinalizedPlan(5L);
        mvc.perform(get("/api/teaching/lesson-plans/" + id).header("X-User-Id", "5"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("FINALIZED"));
    }

    @Test
    void 课件须基于定稿教案否则400() throws Exception {
        // 建草案教案(未定稿)
        var res = mvc.perform(post("/api/teaching/lesson-plans")
                .header("X-User-Id", "5").contentType("application/json")
                .content(json.writeValueAsString(Map.of("scene","HOME","mode","GROUP","disorderType","ASD","iepContent","x"))))
            .andReturn();
        Long planId = json.readTree(res.getResponse().getContentAsString()).path("data").path("id").asLong();
        mvc.perform(post("/api/teaching/courseware")
                .header("X-User-Id", "5").contentType("application/json")
                .content(json.writeValueAsString(Map.of("lessonPlanId", planId))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void 课件基于定稿教案生成并finalize落存储() throws Exception {
        Long planId = createFinalizedPlan(5L);
        var res = mvc.perform(post("/api/teaching/courseware")
                .header("X-User-Id", "5").contentType("application/json")
                .content(json.writeValueAsString(Map.of("lessonPlanId", planId))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("DRAFT"))
            .andReturn();
        Long cwId = json.readTree(res.getResponse().getContentAsString()).path("data").path("id").asLong();
        mvc.perform(post("/api/teaching/courseware/" + cwId + "/finalize").header("X-User-Id", "5"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("FINALIZED"))
            .andExpect(jsonPath("$.data.storageKey").isNotEmpty());
    }

    @Test
    void 他人教案被拒403() throws Exception {
        Long id = createFinalizedPlan(5L);
        mvc.perform(get("/api/teaching/lesson-plans/" + id).header("X-User-Id", "8"))
            .andExpect(status().isForbidden());
    }

    @Test
    void 缺X_User_Id返401() throws Exception {
        mvc.perform(post("/api/teaching/lesson-plans")
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("scene","HOME","mode","GROUP","disorderType","ASD","iepContent","x"))))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void Python不可用时降级DRAFT保留() throws Exception {
        stub.throwError = true;
        mvc.perform(post("/api/teaching/lesson-plans")
                .header("X-User-Id", "5").contentType("application/json")
                .content(json.writeValueAsString(Map.of("scene","HOME","mode","GROUP","disorderType","ASD","iepContent","x"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("DRAFT"))
            .andExpect(jsonPath("$.data.content").value(org.hamcrest.Matchers.containsString("失败")));
        stub.throwError = false;
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

```bash
export PATH="/c/tools/apache-maven-3.9.6/bin:$PATH"
cd /d/works/test/SELLM/dev_workspace
mvn -pl sellm-agent-teaching test -Dtest=TeachingApiTest 2>&1 | grep -E "ERROR|cannot find symbol|Tests run|BUILD" | tail -8
```
Expected: 编译失败(Controller/AppService/SmartLayerClient/DTO 未定义)。

- [ ] **Step 3: 创建 SmartLayer 客户端组件(镜像 qa)**

`SmartLayerClient.java`:
```java
package com.sellm.teaching;

/** 调 Python 智能层生成教案/课件文本。task=lesson_plan|courseware。 */
public interface SmartLayerClient {
    /**
     * @param task "lesson_plan" 或 "courseware"
     * @param iepContentOrPlan 已脱敏:lesson_plan 传 IEP 内容;courseware 传定稿教案文本
     */
    String generate(String task, String iepContentOrPlan, String disorderType, String scene, String mode);
}
```

`SmartLayerProperties.java`:
```java
package com.sellm.teaching;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sellm.teaching.smart-layer")
public class SmartLayerProperties {
    private String baseUrl = "http://localhost:8090";
    private int timeoutSeconds = 30;

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
}
```

`SmartLayerException.java`:
```java
package com.sellm.teaching;

public class SmartLayerException extends RuntimeException {
    public SmartLayerException(String message) { super(message); }
    public SmartLayerException(String message, Throwable cause) { super(message, cause); }
}
```

`HttpSmartLayerClient.java`:
```java
package com.sellm.teaching;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** REST 调 Python /v1/agents/teaching/invoke。强制 HTTP/1.1。 */
@Component
public class HttpSmartLayerClient implements SmartLayerClient {

    private final SmartLayerProperties props;
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient httpClient;

    public HttpSmartLayerClient(SmartLayerProperties props) {
        this.props = props;
        this.httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(props.getTimeoutSeconds()))
            .build();
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
            String resp = send(json.writeValueAsString(body));
            JsonNode node = json.readTree(resp);
            return node.path("content").asText("");
        } catch (Exception e) {
            throw new SmartLayerException("智能层调用失败", e);
        }
    }

    protected String send(String requestBody) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(props.getBaseUrl() + "/v1/agents/teaching/invoke"))
            .timeout(Duration.ofSeconds(props.getTimeoutSeconds()))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();
        HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2) {
            throw new SmartLayerException("智能层返回非 2xx: " + resp.statusCode());
        }
        return resp.body();
    }
}
```

- [ ] **Step 4: 创建 DTO(5 个)**

`dto/GeneratePlanRequest.java`:
```java
package com.sellm.teaching.dto;

public class GeneratePlanRequest {
    private Long childId;
    private Long classId;
    private Long sourceIepId;
    private String scene;
    private String mode;
    private String disorderType;
    private String iepContent;

    public Long getChildId() { return childId; }
    public void setChildId(Long childId) { this.childId = childId; }
    public Long getClassId() { return classId; }
    public void setClassId(Long classId) { this.classId = classId; }
    public Long getSourceIepId() { return sourceIepId; }
    public void setSourceIepId(Long sourceIepId) { this.sourceIepId = sourceIepId; }
    public String getScene() { return scene; }
    public void setScene(String scene) { this.scene = scene; }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public String getDisorderType() { return disorderType; }
    public void setDisorderType(String disorderType) { this.disorderType = disorderType; }
    public String getIepContent() { return iepContent; }
    public void setIepContent(String iepContent) { this.iepContent = iepContent; }
}
```

`dto/EditRequest.java`:
```java
package com.sellm.teaching.dto;

public class EditRequest {
    private String content;
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}
```

`dto/GenerateCoursewareRequest.java`:
```java
package com.sellm.teaching.dto;

public class GenerateCoursewareRequest {
    private Long lessonPlanId;
    private String format; // 可空,默认 TEXT
    public Long getLessonPlanId() { return lessonPlanId; }
    public void setLessonPlanId(Long lessonPlanId) { this.lessonPlanId = lessonPlanId; }
    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }
}
```

`dto/PlanResponse.java`:
```java
package com.sellm.teaching.dto;

public class PlanResponse {
    private Long id;
    private String status;
    private String content;
    private String aiDraft;

    public PlanResponse(Long id, String status, String content, String aiDraft) {
        this.id = id; this.status = status; this.content = content; this.aiDraft = aiDraft;
    }
    public Long getId() { return id; }
    public String getStatus() { return status; }
    public String getContent() { return content; }
    public String getAiDraft() { return aiDraft; }
}
```

`dto/CoursewareResponse.java`:
```java
package com.sellm.teaching.dto;

public class CoursewareResponse {
    private Long id;
    private Long lessonPlanId;
    private String status;
    private String content;
    private String storageKey;
    private String format;

    public CoursewareResponse(Long id, Long lessonPlanId, String status, String content, String storageKey, String format) {
        this.id = id; this.lessonPlanId = lessonPlanId; this.status = status;
        this.content = content; this.storageKey = storageKey; this.format = format;
    }
    public Long getId() { return id; }
    public Long getLessonPlanId() { return lessonPlanId; }
    public String getStatus() { return status; }
    public String getContent() { return content; }
    public String getStorageKey() { return storageKey; }
    public String getFormat() { return format; }
}
```

- [ ] **Step 5: 创建 UnauthorizedException + TeachingExceptionHandler(镜像 qa)**

`UnauthorizedException.java`:
```java
package com.sellm.teaching;

public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) { super(message); }
}
```

`TeachingExceptionHandler.java`:
```java
package com.sellm.teaching;

import com.sellm.common.BusinessException;
import com.sellm.common.ErrorCode;
import com.sellm.common.Result;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class TeachingExceptionHandler {

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

- [ ] **Step 6: 实现 TeachingAppService**

`sellm-agent-teaching/src/main/java/com/sellm/teaching/TeachingAppService.java`:
```java
package com.sellm.teaching;

import com.sellm.anonymizer.AnonymizationException;
import com.sellm.anonymizer.AnonymizationResult;
import com.sellm.anonymizer.Anonymizer;
import com.sellm.common.BusinessException;
import com.sellm.common.ErrorCode;
import com.sellm.storage.ObjectStorage;
import com.sellm.teaching.dto.*;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class TeachingAppService {

    private final LessonPlanRepository planRepo;
    private final CoursewareRepository cwRepo;
    private final SmartLayerClient smartLayer;
    private final Anonymizer anonymizer;
    private final ObjectStorage storage;

    public TeachingAppService(LessonPlanRepository planRepo, CoursewareRepository cwRepo,
                              SmartLayerClient smartLayer, Anonymizer anonymizer, ObjectStorage storage) {
        this.planRepo = planRepo;
        this.cwRepo = cwRepo;
        this.smartLayer = smartLayer;
        this.anonymizer = anonymizer;
        this.storage = storage;
    }

    // ---- 教案 ----
    public PlanResponse generatePlan(Long userId, GeneratePlanRequest req) {
        if (req.getIepContent() == null || req.getIepContent().isBlank())
            throw new BusinessException(ErrorCode.INVALID_INPUT, "IEP 内容不能为空");
        // 1. 先落 DRAFT(content 空)
        LessonPlan p = new LessonPlan();
        p.setOwnerId(userId);
        p.setChildId(req.getChildId());
        p.setClassId(req.getClassId());
        p.setSourceIepId(req.getSourceIepId());
        p.setScene(req.getScene());
        p.setMode(req.getMode());
        p.setDisorderType(req.getDisorderType());
        p.setStatus("DRAFT");
        planRepo.save(p);
        // 2. 脱敏 → Python → 还原
        String content;
        try {
            AnonymizationResult anon = anonymizer.anonymize(req.getIepContent(), List.of(), List.of());
            String aiText = smartLayer.generate("lesson_plan", anon.getAnonymizedText(),
                req.getDisorderType(), req.getScene(), req.getMode());
            content = anonymizer.restore(aiText, anon.getRestoreMap());
        } catch (AnonymizationException ae) {
            throw new BusinessException(ErrorCode.ANONYMIZATION_FAILED, "脱敏校验未通过,已阻断出网");
        } catch (SmartLayerException se) {
            content = "AI 生成失败,可重试或手动撰写。";
        }
        p.setAiDraft(content);
        p.setContent(content);
        planRepo.update(p);
        return new PlanResponse(p.getId(), p.getStatus(), p.getContent(), p.getAiDraft());
    }

    public PlanResponse editPlan(Long userId, Long id, EditRequest req) {
        LessonPlan p = requireOwnedPlan(userId, id);
        p.setContent(req.getContent());
        planRepo.update(p);
        return new PlanResponse(p.getId(), p.getStatus(), p.getContent(), p.getAiDraft());
    }

    public PlanResponse finalizePlan(Long userId, Long id) {
        LessonPlan p = requireOwnedPlan(userId, id);
        p.setStatus("FINALIZED");
        planRepo.update(p);
        return new PlanResponse(p.getId(), p.getStatus(), p.getContent(), p.getAiDraft());
    }

    public PlanResponse getPlan(Long userId, Long id) {
        LessonPlan p = requireOwnedPlan(userId, id);
        return new PlanResponse(p.getId(), p.getStatus(), p.getContent(), p.getAiDraft());
    }

    public List<PlanResponse> listPlans(Long userId) {
        return planRepo.listByOwner(userId).stream()
            .map(p -> new PlanResponse(p.getId(), p.getStatus(), p.getContent(), p.getAiDraft()))
            .toList();
    }

    // ---- 课件 ----
    public CoursewareResponse generateCourseware(Long userId, GenerateCoursewareRequest req) {
        LessonPlan plan = requireOwnedPlan(userId, req.getLessonPlanId());
        if (!"FINALIZED".equals(plan.getStatus()))
            throw new BusinessException(ErrorCode.INVALID_INPUT, "课件须基于已定稿教案");
        Courseware c = new Courseware();
        c.setOwnerId(userId);
        c.setLessonPlanId(plan.getId());
        c.setDisorderType(plan.getDisorderType());
        c.setFormat(req.getFormat() == null ? "TEXT" : req.getFormat());
        c.setStatus("DRAFT");
        cwRepo.save(c);
        String content;
        try {
            AnonymizationResult anon = anonymizer.anonymize(plan.getContent(), List.of(), List.of());
            String aiText = smartLayer.generate("courseware", anon.getAnonymizedText(),
                plan.getDisorderType(), plan.getScene(), plan.getMode());
            content = anonymizer.restore(aiText, anon.getRestoreMap());
        } catch (AnonymizationException ae) {
            throw new BusinessException(ErrorCode.ANONYMIZATION_FAILED, "脱敏校验未通过,已阻断出网");
        } catch (SmartLayerException se) {
            content = "AI 生成失败,可重试或手动撰写。";
        }
        c.setAiDraft(content);
        c.setContent(content);
        cwRepo.update(c);
        return toCwResponse(c);
    }

    public CoursewareResponse editCourseware(Long userId, Long id, EditRequest req) {
        Courseware c = requireOwnedCourseware(userId, id);
        c.setContent(req.getContent());
        cwRepo.update(c);
        return toCwResponse(c);
    }

    public CoursewareResponse finalizeCourseware(Long userId, Long id) {
        Courseware c = requireOwnedCourseware(userId, id);
        // 产物落对象存储(仅 finalize)
        String key = "courseware/" + c.getId() + "." + c.getFormat().toLowerCase();
        storage.put(key, c.getContent().getBytes(StandardCharsets.UTF_8),
            "HTML".equalsIgnoreCase(c.getFormat()) ? "text/html" : "text/plain");
        c.setStorageKey(key);
        c.setStatus("FINALIZED");
        cwRepo.update(c);
        return toCwResponse(c);
    }

    public CoursewareResponse getCourseware(Long userId, Long id) {
        return toCwResponse(requireOwnedCourseware(userId, id));
    }

    // ---- helpers ----
    private LessonPlan requireOwnedPlan(Long userId, Long id) {
        LessonPlan p = planRepo.findById(id);
        if (p == null) throw new BusinessException(ErrorCode.NOT_FOUND, "教案不存在");
        if (!p.getOwnerId().equals(userId)) throw new BusinessException(ErrorCode.ACCESS_DENIED, "无权访问该教案");
        return p;
    }

    private Courseware requireOwnedCourseware(Long userId, Long id) {
        Courseware c = cwRepo.findById(id);
        if (c == null) throw new BusinessException(ErrorCode.NOT_FOUND, "课件不存在");
        if (!c.getOwnerId().equals(userId)) throw new BusinessException(ErrorCode.ACCESS_DENIED, "无权访问该课件");
        return c;
    }

    private CoursewareResponse toCwResponse(Courseware c) {
        return new CoursewareResponse(c.getId(), c.getLessonPlanId(), c.getStatus(),
            c.getContent(), c.getStorageKey(), c.getFormat());
    }
}
```

- [ ] **Step 7: 创建 Controllers**

`LessonPlanController.java`:
```java
package com.sellm.teaching;

import com.sellm.common.Result;
import com.sellm.teaching.dto.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/teaching/lesson-plans")
public class LessonPlanController {

    private final TeachingAppService appService;
    public LessonPlanController(TeachingAppService appService) { this.appService = appService; }

    @PostMapping
    public Result<PlanResponse> generate(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                                         @RequestBody GeneratePlanRequest req) {
        requireUser(userId);
        return Result.ok(appService.generatePlan(userId, req));
    }

    @PutMapping("/{id}")
    public Result<PlanResponse> edit(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                                     @PathVariable("id") Long id, @RequestBody EditRequest req) {
        requireUser(userId);
        return Result.ok(appService.editPlan(userId, id, req));
    }

    @PostMapping("/{id}/finalize")
    public Result<PlanResponse> finalizePlan(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                                             @PathVariable("id") Long id) {
        requireUser(userId);
        return Result.ok(appService.finalizePlan(userId, id));
    }

    @GetMapping("/{id}")
    public Result<PlanResponse> get(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                                    @PathVariable("id") Long id) {
        requireUser(userId);
        return Result.ok(appService.getPlan(userId, id));
    }

    @GetMapping
    public Result<List<PlanResponse>> list(@RequestHeader(value = "X-User-Id", required = false) Long userId) {
        requireUser(userId);
        return Result.ok(appService.listPlans(userId));
    }

    private void requireUser(Long userId) {
        if (userId == null) throw new UnauthorizedException("缺少 X-User-Id");
    }
}
```

`CoursewareController.java`:
```java
package com.sellm.teaching;

import com.sellm.common.Result;
import com.sellm.teaching.dto.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/teaching/courseware")
public class CoursewareController {

    private final TeachingAppService appService;
    public CoursewareController(TeachingAppService appService) { this.appService = appService; }

    @PostMapping
    public Result<CoursewareResponse> generate(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                                               @RequestBody GenerateCoursewareRequest req) {
        requireUser(userId);
        return Result.ok(appService.generateCourseware(userId, req));
    }

    @PutMapping("/{id}")
    public Result<CoursewareResponse> edit(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                                           @PathVariable("id") Long id, @RequestBody EditRequest req) {
        requireUser(userId);
        return Result.ok(appService.editCourseware(userId, id, req));
    }

    @PostMapping("/{id}/finalize")
    public Result<CoursewareResponse> finalizeCw(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                                                 @PathVariable("id") Long id) {
        requireUser(userId);
        return Result.ok(appService.finalizeCourseware(userId, id));
    }

    @GetMapping("/{id}")
    public Result<CoursewareResponse> get(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                                          @PathVariable("id") Long id) {
        requireUser(userId);
        return Result.ok(appService.getCourseware(userId, id));
    }

    private void requireUser(Long userId) {
        if (userId == null) throw new UnauthorizedException("缺少 X-User-Id");
    }
}
```

- [ ] **Step 8: TeachingApplication 启用 Properties + yml 配置**

修改 `TeachingApplication.java`:加 `@EnableConfigurationProperties(SmartLayerProperties.class)`(import org.springframework.boot.context.properties.EnableConfigurationProperties)。
`application.yml` 末尾加(顶层):
```yaml
sellm:
  teaching:
    smart-layer:
      base-url: ${SELLM_SMARTLAYER_URL:http://localhost:8090}
      timeout-seconds: ${SELLM_SMARTLAYER_TIMEOUT:30}
  storage:
    local-dir: ${SELLM_STORAGE_LOCAL_DIR:data/media}
```

- [ ] **Step 9: 写脱敏硬阻断独立测试 TeachingSanitizeHardBlockTest**

`sellm-agent-teaching/src/test/java/com/sellm/teaching/TeachingSanitizeHardBlockTest.java`:
```java
package com.sellm.teaching;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sellm.anonymizer.AnonymizationException;
import com.sellm.anonymizer.AnonymizationResult;
import com.sellm.anonymizer.Anonymizer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TeachingSanitizeHardBlockTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired LocalStub stub;

    @TestConfiguration
    static class Cfg {
        @Bean @Primary
        Anonymizer throwingAnonymizer() {
            return new Anonymizer() {
                public AnonymizationResult anonymize(String text, List<String> names, List<String> schools) {
                    throw new AnonymizationException("forced");
                }
                public String restore(String text, Map<String, String> map) { return text; }
            };
        }
        @Bean @Primary
        LocalStub localStub() { return new LocalStub(); }
    }

    static class LocalStub implements SmartLayerClient {
        volatile boolean called = false;
        @Override public String generate(String task, String c, String d, String s, String m) {
            called = true; return "should-not-be-called";
        }
    }

    @Test
    void 脱敏失败返400且不调Python() throws Exception {
        mvc.perform(post("/api/teaching/lesson-plans")
                .header("X-User-Id", "5").contentType("application/json")
                .content(json.writeValueAsString(Map.of("scene","HOME","mode","GROUP","disorderType","ASD","iepContent","x"))))
            .andExpect(status().isBadRequest());
        assertFalse(stub.called, "脱敏失败应硬阻断,不调 Python");
    }
}
```

- [ ] **Step 10: 运行测试确认通过**

```bash
export PATH="/c/tools/apache-maven-3.9.6/bin:$PATH"
cd /d/works/test/SELLM/dev_workspace
mvn -pl sellm-agent-teaching clean test 2>&1 | grep -E "Tests run:|BUILD" | tail -5
```
Expected: TeachingApiTest 7 + TeachingSanitizeHardBlockTest 1 + TeachingRepositoryTest 3 + contextLoads 全绿,`BUILD SUCCESS`。

> 若 ObjectStorage bean 缺失(finalize 课件时):确认 Task1 的 StorageAutoConfiguration 已注册到 core 的 imports;agent-teaching 经 core 自动装配获得 NoopObjectStorage。若仍缺报 BLOCKED。

- [ ] **Step 11: Commit**

```bash
git add sellm-agent-teaching/
git commit -m "feat(teaching): TeachingAppService 编排(脱敏/状态机/课件落存储)+ Controllers(X-User-Id 鉴权)"
```
---

### Task 4: Python 智能层 teaching 编排(mock LLM)

**Files:**
- Create: `ai-smart-layer/app/agents/teaching.py`(替换 P0 空壳:真实编排)
- Modify: `ai-smart-layer/app/main.py`(新增 POST /v1/agents/teaching/invoke)
- Test: `ai-smart-layer/tests/test_teaching.py`

**Interfaces:**
- Consumes: `app/adapters/llm.py` 的 `get_llm()`(MockLLM)
- Produces:
  - `agents.teaching.invoke_teaching(payload: dict) -> dict`({content, mock})
  - main `POST /v1/agents/teaching/invoke` 返回 {content, mock}

> teaching 编排首版纯 prompt 拼装 + mock LLM(不挂 RAG,与 qa 区分)。task=lesson_plan 用 iepContent;task=courseware 用 lessonPlanContent。Python 只见脱敏文本,不还原。

- [ ] **Step 1: 写失败测试 test_teaching.py**

`ai-smart-layer/tests/test_teaching.py`:
```python
import pytest
from httpx import AsyncClient, ASGITransport
from app.main import app
from app.agents.teaching import invoke_teaching


@pytest.mark.asyncio
async def test_invoke_lesson_plan():
    out = await invoke_teaching({
        "task": "lesson_plan", "iepContent": "长期目标:共同注意",
        "disorderType": "ASD", "scene": "SCHOOL", "mode": "ONE_ON_ONE",
    })
    assert "content" in out and isinstance(out["content"], str) and out["content"]
    assert out.get("mock") is True


@pytest.mark.asyncio
async def test_invoke_courseware():
    out = await invoke_teaching({
        "task": "courseware", "lessonPlanContent": "教案正文",
        "disorderType": "ASD", "scene": "HOME", "mode": "GROUP",
    })
    assert "content" in out and isinstance(out["content"], str) and out["content"]


@pytest.mark.asyncio
async def test_teaching_invoke_endpoint():
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        resp = await client.post("/v1/agents/teaching/invoke",
                                 json={"task": "lesson_plan", "iepContent": "[儿童1] 的目标",
                                       "disorderType": "ASD", "scene": "SCHOOL", "mode": "ONE_ON_ONE"})
    assert resp.status_code == 200
    assert "content" in resp.json()


@pytest.mark.asyncio
async def test_python_no_restore():
    # Python 不还原占位符
    out = await invoke_teaching({
        "task": "lesson_plan", "iepContent": "[儿童1] 的目标",
        "disorderType": "ASD", "scene": "SCHOOL", "mode": "ONE_ON_ONE",
    })
    assert isinstance(out["content"], str)  # 结构合规;无 restore 逻辑
```

- [ ] **Step 2: 运行测试确认失败(或无 Python 静态)**

```bash
cd /d/works/test/SELLM/dev_workspace/ai-smart-layer
pytest tests/test_teaching.py -v 2>&1 | tail -10 || echo "(deferred:无 Python 3.11)"
```
Expected(有 Python):FAIL(invoke_teaching 旧空壳签名不符 / 端点不存在)。无 Python:静态审查。

- [ ] **Step 3: 实现 agents/teaching.py**

`ai-smart-layer/app/agents/teaching.py`(替换 P0 空壳):
```python
"""教学训练 Agent — 教案/课件生成(mock LLM,不挂 RAG)。Python 只见脱敏文本,不还原。"""
from app.adapters.llm import get_llm


async def invoke_teaching(payload: dict) -> dict:
    task = payload.get("task", "lesson_plan")
    disorder = payload.get("disorderType", "")
    scene = payload.get("scene", "")
    mode = payload.get("mode", "")
    if task == "courseware":
        source = payload.get("lessonPlanContent", "")
        prompt = (
            f"你是特殊教育课件设计助手。基于以下教案,为 {disorder} 儿童设计适配课件大纲"
            f"(场景 {scene},{mode})。教案:\n{source}\n课件大纲:"
        )
    else:
        source = payload.get("iepContent", "")
        prompt = (
            f"你是特殊教育备课助手。基于以下 IEP,为 {disorder} 儿童设计分层教案"
            f"(场景 {scene},{mode})。IEP:\n{source}\n分层教案:"
        )
    llm = get_llm()
    content = await llm.generate(prompt)
    return {"content": content, "mock": True}
```

- [ ] **Step 4: main.py 加 teaching 端点**

修改 `ai-smart-layer/app/main.py`,加(import + endpoint):
```python
from app.agents.teaching import invoke_teaching


@app.post("/v1/agents/teaching/invoke")
async def teaching_invoke(payload: dict):
    """教学训练 Agent 教案/课件生成(接收脱敏后文本)。"""
    return await invoke_teaching(payload)
```
(保留现有 /health、/v1/agents/qa/invoke、通用 invoke。)

- [ ] **Step 5: 运行测试确认通过(或静态)**

```bash
cd /d/works/test/SELLM/dev_workspace/ai-smart-layer
pytest tests/test_teaching.py tests/test_qa.py tests/test_health.py -v 2>&1 | tail -12 || echo "(deferred:无 Python 3.11,静态审查)"
```
Expected(有 Python):test_teaching 4 + 既有 qa/health 全通过。无 Python:静态审查 invoke_teaching 签名/await/返回结构与测试断言一致,报告 deferred。

- [ ] **Step 6: Commit**

```bash
cd /d/works/test/SELLM/dev_workspace
git add ai-smart-layer/
git commit -m "feat(teaching): Python 智能层 teaching 编排(mock LLM 教案/课件)+ /v1/agents/teaching/invoke"
```

---

### Task 5: agent-teaching 全量回归 + .env/文档

**Files:**
- Modify: `.env.example`(补 SELLM_STORAGE_LOCAL_DIR 说明)
- Verification only(+ `.claude/CLAUDE_CHANGES.md`)

**Interfaces:**
- Produces: 确认 agent-teaching 不破坏全 reactor;9 模块绿

- [ ] **Step 1: 全 reactor clean install(9 模块)**

```bash
export PATH="/c/tools/apache-maven-3.9.6/bin:$PATH"
cd /d/works/test/SELLM/dev_workspace
mvn clean install 2>&1 | grep -E "Reactor Summary|SUCCESS \[|FAILURE \[|BUILD SUCCESS|BUILD FAILURE|Tests run:.*Failures: [1-9]" | tail -15
```
Expected: 9 模块全 SUCCESS;backend 仍绿(StorageConfigTest 经 core 解析 NoopObjectStorage);agent-teaching 测试全绿。

- [ ] **Step 2: agent-teaching 测试汇总**

```bash
export PATH="/c/tools/apache-maven-3.9.6/bin:$PATH"
cd /d/works/test/SELLM/dev_workspace
mvn -pl sellm-agent-teaching clean test 2>&1 | grep -E "Tests run:.*Failures|BUILD" | tail -3
```
Expected: 全绿(Repository 3 + TeachingApi 7 + SanitizeHardBlock 1 + contextLoads 1)。

- [ ] **Step 3: 前端 build**

```bash
cd /d/works/test/SELLM/dev_workspace/frontend
npm run build 2>&1 | grep -E "built in|error" | tail -2
```
Expected: `✓ built in X.XXs`。

- [ ] **Step 4: .env.example 补存储配置**

在 `.env.example` 末尾(智能层段附近)追加:
```bash
# ── agent-teaching 课件产物存储(P5,默认 Noop 本地落盘)──
SELLM_STORAGE_LOCAL_DIR=data/media
```
(SELLM_SMARTLAYER_URL/TIMEOUT 已在 qa 阶段加,teaching 复用同名,确认在即可。)

- [ ] **Step 5: 追加变更记录 + 确认工作树**

向 `.claude/CLAUDE_CHANGES.md` 追加 P5 记录(FEATURE 类型)。`git status --short` 应仅 .env.example 待提交。

- [ ] **Step 6: Commit**

```bash
git add .env.example
git commit -m "docs(teaching): .env.example 补 agent-teaching 存储配置"
```

---

## 文件清单总览

```
dev_workspace/
├── sellm-common-core/                    (Task1: 存储自动装配)
│   ├── src/main/java/com/sellm/storage/
│   │   ├── NoopObjectStorage.java        (MOVE from backend)
│   │   └── StorageAutoConfiguration.java (NEW)
│   ├── src/main/resources/META-INF/spring/...AutoConfiguration.imports (MODIFY +1行)
│   └── src/test/java/com/sellm/storage/NoopObjectStorageTest.java (NEW)
├── sellm-common-backend/                 (Task1: NoopObjectStorage 移走;StorageConfig 经 core 解析)
├── sellm-agent-teaching/                 (主体)
│   ├── pom.xml                           (MODIFY: +mybatis/jdbc/h2)
│   └── src/
│       ├── main/java/com/sellm/teaching/
│       │   ├── TeachingApplication.java  (MODIFY: @EnableConfigurationProperties)
│       │   ├── LessonPlanController.java / CoursewareController.java (NEW)
│       │   ├── TeachingAppService.java   (NEW)
│       │   ├── SmartLayerClient/HttpSmartLayerClient/SmartLayerProperties/SmartLayerException.java (NEW,镜像 qa)
│       │   ├── LessonPlan/Courseware.java (NEW 实体)
│       │   ├── LessonPlanMapper/CoursewareMapper.java (NEW)
│       │   ├── LessonPlanRepository/CoursewareRepository.java (NEW)
│       │   ├── UnauthorizedException/TeachingExceptionHandler.java (NEW)
│       │   └── dto/{GeneratePlanRequest,EditRequest,GenerateCoursewareRequest,PlanResponse,CoursewareResponse}.java (NEW)
│       ├── main/resources/{application.yml(MODIFY), schema.sql(NEW), mybatis/{LessonPlan,Courseware}Mapper.xml(NEW)}
│       └── test/{java TeachingRepositoryTest/TeachingApiTest/TeachingSanitizeHardBlockTest, resources/application.yml(MODIFY)}
├── ai-smart-layer/app/
│   ├── agents/teaching.py                (MODIFY: 真实编排)
│   └── main.py                           (MODIFY: /v1/agents/teaching/invoke)
└── .env.example                          (MODIFY: 存储配置)
```

> **复用 qa 模板说明**:SmartLayerClient/HttpSmartLayerClient/SmartLayerProperties/SmartLayerException/UnauthorizedException/TeachingExceptionHandler 与 agent-qa 同构(仅包名 + smart-layer 配置前缀不同)。这是有意复制(每 Agent 独立模块、独立 HTTP 客户端),非可抽公共库的过度设计——若未来 3+ Agent 都用,可考虑抽到 core,但首版 YAGNI 各自一份。
