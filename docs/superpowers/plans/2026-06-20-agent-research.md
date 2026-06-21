# agent-research 科研助手 Agent 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现科研助手 Agent —— Java sellm-agent-research(信效度计算[纯 Java:Cronbach α/分半/项总相关] + 课题申报书[mock LLM 草案定稿,复用 agent-common + teaching 模板])+ Python research 编排(mock LLM,仅课题书),复用 sellm-agent-common 零复制脚手架。

**Architecture:** agent-research 依赖 sellm-agent-common(异常/属性/AbstractHttpSmartLayerClient/AgentExceptionHandler 自动装配)+ 经它传递 sellm-common-core。信效度=纯本地算法(ReliabilityService 无网络/无 LLM/无脱敏),课题书=镜像 teaching 的草案定稿(脱敏→Python→还原→DRAFT/FINALIZED/冻结)。两套四件套持久化(reliability_calc + research_proposal)。

**Tech Stack:** Java 17 / Spring Boot 3.2.5 / MyBatis / H2 / Python 3.11 / FastAPI

## Global Constraints

- Java 17,Spring Boot 3.2.5;agent-research 依赖 **sellm-agent-common**(经它得 core + 脚手架 + 自动装配)
- 用户身份来自网关注入 `X-User-Id` 头(@RequestHeader);缺头→401(抛 com.sellm.agentcommon.UnauthorizedException)
- 沿用 report/qa/teaching 四件套 MyBatis 风格(Mapper 收发 Map<String,Object>;XML resultMap snake→camel;insert useGeneratedKeys;Repository 手动 ((Number)..).longValue())
- 异常/属性/异常处理器/HTTP 基类**全来自 sellm-agent-common,不在 research 重写**;SmartLayerClient typed 接口 + HttpResearchSmartLayerClient(继承基类)留 research
- 统一 `Result<T>` 信封;ErrorCode 用现有值(INVALID_INPUT/ANONYMIZATION_FAILED/ACCESS_DENIED/NOT_FOUND/UNAUTHORIZED);配置 `sellm.smart-layer.*`(agent-common 提供)
- **红线**:课题书 AI 只产草案(DRAFT→FINALIZED 冻结)、出网脱敏(失败硬阻断)、Python 不持明文;**信效度无网络面**(纯本地数值,输入约定匿名分数矩阵)
- **Cronbach α 方差约定一致**:题方差与总分方差同用总体方差(/N),勿混 /N 与 /(N-1)
- 测试:`@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")`;H2;ReliabilityService 纯单测对标已知值
- **每步 `mvn clean install` 验证**(stale target 假绿);全 reactor 10 模块 SUCCESS;qa16/teaching15/backend242 不受影响
- Maven 在 Git Bash:`export PATH="/c/tools/apache-maven-3.9.6/bin:$PATH"`;无真 Python 3.11 则静态审查
- 代码/配置变更后追加 `.claude/CLAUDE_CHANGES.md`

---

### Task 1: 持久化基建(依赖 + H2 + schema + reliability_calc/research_proposal 四件套)

**Files:**
- Modify: `sellm-agent-research/pom.xml`(加 sellm-agent-common + mybatis + jdbc + h2)
- Modify: `sellm-agent-research/src/main/resources/application.yml`(H2 + mybatis + schema init + sellm.smart-layer,合并 spring 节点)
- Modify: `sellm-agent-research/src/test/resources/application.yml`(H2 + mybatis,合并)
- Create: `sellm-agent-research/src/main/resources/schema.sql`
- Create: `sellm-agent-research/src/main/java/com/sellm/research/ReliabilityCalc.java`
- Create: `sellm-agent-research/src/main/java/com/sellm/research/ResearchProposal.java`
- Create: `sellm-agent-research/src/main/java/com/sellm/research/ReliabilityCalcMapper.java`
- Create: `sellm-agent-research/src/main/java/com/sellm/research/ResearchProposalMapper.java`
- Create: `sellm-agent-research/src/main/resources/mybatis/ReliabilityCalcMapper.xml`
- Create: `sellm-agent-research/src/main/resources/mybatis/ResearchProposalMapper.xml`
- Create: `sellm-agent-research/src/main/java/com/sellm/research/ReliabilityCalcRepository.java`
- Create: `sellm-agent-research/src/main/java/com/sellm/research/ResearchProposalRepository.java`
- Test: `sellm-agent-research/src/test/java/com/sellm/research/ResearchRepositoryTest.java`

**Interfaces:**
- Produces:
  - `ReliabilityCalc`(id, ownerId, dataset, method, result)
  - `ResearchProposal`(id, ownerId, topic, aiDraft, content, status)
  - `ReliabilityCalcRepository.save/findById/listByOwner`
  - `ResearchProposalRepository.save/findById/listByOwner/update`
- Consumes: 无(纯持久化)

- [ ] **Step 1: 加依赖到 sellm-agent-research/pom.xml**

`<dependencies>` 加(sellm-agent-common 在前,带出 core + 脚手架):
```xml
        <dependency>
            <groupId>com.sellm</groupId>
            <artifactId>sellm-agent-common</artifactId>
            <version>0.1.0-SNAPSHOT</version>
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
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <scope>runtime</scope>
        </dependency>
```
(sellm-common-core 依赖已在;sellm-agent-common 也依赖 core,重复声明无害但可保留现有 core 行。)

- [ ] **Step 2: 创建 schema.sql**

`sellm-agent-research/src/main/resources/schema.sql`:
```sql
CREATE TABLE IF NOT EXISTS reliability_calc (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    owner_id BIGINT NOT NULL,
    dataset TEXT,
    method VARCHAR(64),
    result TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS research_proposal (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    owner_id BIGINT NOT NULL,
    topic VARCHAR(512),
    ai_draft TEXT,
    content TEXT,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
);
```

- [ ] **Step 3: 配置 H2(main + test application.yml)**

`sellm-agent-research/src/main/resources/application.yml` 的 `spring:` 下加(合并,不破坏 application.name/cloud.nacos):
```yaml
  datasource:
    url: jdbc:h2:mem:research_db;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE
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
并在文件末尾加(顶层 sellm,smart-layer 由 agent-common 自动装配读取):
```yaml
sellm:
  smart-layer:
    base-url: ${SELLM_SMARTLAYER_URL:http://localhost:8090}
    timeout-seconds: ${SELLM_SMARTLAYER_TIMEOUT:30}
```
`sellm-agent-research/src/test/resources/application.yml` 的 `spring:` 下加同样 datasource + sql.init + 顶层 mybatis(合并入已有 spring.cloud 禁 discovery 节点)。

- [ ] **Step 4: 写失败测试 ResearchRepositoryTest**

`sellm-agent-research/src/test/java/com/sellm/research/ResearchRepositoryTest.java`:
```java
package com.sellm.research;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ResearchRepositoryTest {

    @Autowired ReliabilityCalcRepository calcRepo;
    @Autowired ResearchProposalRepository proposalRepo;

    @Test
    void 信效度记录保存回填id并按owner查询() {
        ReliabilityCalc c = new ReliabilityCalc();
        c.setOwnerId(7L); c.setDataset("[[1,2],[3,4]]");
        c.setMethod("cronbach+splithalf+itemtotal"); c.setResult("{\"alpha\":0.8}");
        ReliabilityCalc saved = calcRepo.save(c);
        assertNotNull(saved.getId());
        List<ReliabilityCalc> mine = calcRepo.listByOwner(7L);
        assertEquals(1, mine.size());
        assertEquals("cronbach+splithalf+itemtotal", mine.get(0).getMethod());
    }

    @Test
    void 课题书保存更新content与status() {
        ResearchProposal p = new ResearchProposal();
        p.setOwnerId(7L); p.setTopic("融合教育研究");
        p.setStatus("DRAFT"); p.setContent("v1");
        Long id = proposalRepo.save(p).getId();
        ResearchProposal loaded = proposalRepo.findById(id);
        loaded.setContent("v2"); loaded.setStatus("FINALIZED");
        proposalRepo.update(loaded);
        ResearchProposal after = proposalRepo.findById(id);
        assertEquals("v2", after.getContent());
        assertEquals("FINALIZED", after.getStatus());
    }
}
```

- [ ] **Step 5: 运行测试确认失败**

```bash
export PATH="/c/tools/apache-maven-3.9.6/bin:$PATH"
cd /d/works/test/SELLM/dev_workspace
mvn -pl sellm-agent-research test -Dtest=ResearchRepositoryTest 2>&1 | grep -E "ERROR|cannot find symbol|Tests run|BUILD" | tail -8
```
Expected: 编译失败(ReliabilityCalc/ResearchProposal/Repository 未定义)。

- [ ] **Step 6: 创建实体 ReliabilityCalc**

`sellm-agent-research/src/main/java/com/sellm/research/ReliabilityCalc.java`:
```java
package com.sellm.research;

public class ReliabilityCalc {
    private Long id;
    private Long ownerId;
    private String dataset;   // 输入矩阵 JSON
    private String method;
    private String result;    // 结果 JSON

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }
    public String getDataset() { return dataset; }
    public void setDataset(String dataset) { this.dataset = dataset; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
}
```

- [ ] **Step 7: 创建实体 ResearchProposal**

`sellm-agent-research/src/main/java/com/sellm/research/ResearchProposal.java`:
```java
package com.sellm.research;

public class ResearchProposal {
    private Long id;
    private Long ownerId;
    private String topic;
    private String aiDraft;
    private String content;
    private String status;   // DRAFT/FINALIZED

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }
    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }
    public String getAiDraft() { return aiDraft; }
    public void setAiDraft(String aiDraft) { this.aiDraft = aiDraft; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
```

- [ ] **Step 8: 创建 Mapper 接口(2 个)**

`ReliabilityCalcMapper.java`:
```java
package com.sellm.research;

import org.apache.ibatis.annotations.Mapper;
import java.util.List;
import java.util.Map;

@Mapper
public interface ReliabilityCalcMapper {
    void insert(Map<String, Object> row);
    Map<String, Object> findById(Long id);
    List<Map<String, Object>> findByOwnerId(Long ownerId);
}
```

`ResearchProposalMapper.java`:
```java
package com.sellm.research;

import org.apache.ibatis.annotations.Mapper;
import java.util.List;
import java.util.Map;

@Mapper
public interface ResearchProposalMapper {
    void insert(Map<String, Object> row);
    Map<String, Object> findById(Long id);
    List<Map<String, Object>> findByOwnerId(Long ownerId);
    void update(Map<String, Object> row);
}
```

- [ ] **Step 9: 创建 Mapper XML(2 个)**

`sellm-agent-research/src/main/resources/mybatis/ReliabilityCalcMapper.xml`:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.sellm.research.ReliabilityCalcMapper">
    <resultMap id="calcMap" type="map">
        <id column="id" property="id"/>
        <result column="owner_id" property="ownerId"/>
        <result column="dataset" property="dataset"/>
        <result column="method" property="method"/>
        <result column="result" property="result"/>
    </resultMap>
    <insert id="insert" parameterType="map" useGeneratedKeys="true" keyProperty="id">
        INSERT INTO reliability_calc (owner_id, dataset, method, result)
        VALUES (#{ownerId}, #{dataset}, #{method}, #{result})
    </insert>
    <select id="findById" parameterType="long" resultMap="calcMap">
        SELECT id, owner_id, dataset, method, result FROM reliability_calc WHERE id = #{id} AND deleted = 0
    </select>
    <select id="findByOwnerId" parameterType="long" resultMap="calcMap">
        SELECT id, owner_id, dataset, method, result FROM reliability_calc WHERE owner_id = #{ownerId} AND deleted = 0 ORDER BY id DESC
    </select>
</mapper>
```

`sellm-agent-research/src/main/resources/mybatis/ResearchProposalMapper.xml`:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.sellm.research.ResearchProposalMapper">
    <resultMap id="proposalMap" type="map">
        <id column="id" property="id"/>
        <result column="owner_id" property="ownerId"/>
        <result column="topic" property="topic"/>
        <result column="ai_draft" property="aiDraft"/>
        <result column="content" property="content"/>
        <result column="status" property="status"/>
    </resultMap>
    <insert id="insert" parameterType="map" useGeneratedKeys="true" keyProperty="id">
        INSERT INTO research_proposal (owner_id, topic, ai_draft, content, status)
        VALUES (#{ownerId}, #{topic}, #{aiDraft}, #{content}, #{status})
    </insert>
    <select id="findById" parameterType="long" resultMap="proposalMap">
        SELECT id, owner_id, topic, ai_draft, content, status FROM research_proposal WHERE id = #{id} AND deleted = 0
    </select>
    <select id="findByOwnerId" parameterType="long" resultMap="proposalMap">
        SELECT id, owner_id, topic, ai_draft, content, status FROM research_proposal WHERE owner_id = #{ownerId} AND deleted = 0 ORDER BY id DESC
    </select>
    <update id="update" parameterType="map">
        UPDATE research_proposal SET content = #{content}, ai_draft = #{aiDraft}, status = #{status},
            updated_at = CURRENT_TIMESTAMP WHERE id = #{id}
    </update>
</mapper>
```

- [ ] **Step 10: 创建 Repository(2 个)**

`ReliabilityCalcRepository.java`:
```java
package com.sellm.research;

import org.springframework.stereotype.Repository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class ReliabilityCalcRepository {
    private final ReliabilityCalcMapper mapper;
    public ReliabilityCalcRepository(ReliabilityCalcMapper mapper) { this.mapper = mapper; }

    public ReliabilityCalc save(ReliabilityCalc c) {
        Map<String, Object> row = new HashMap<>();
        row.put("ownerId", c.getOwnerId());
        row.put("dataset", c.getDataset());
        row.put("method", c.getMethod());
        row.put("result", c.getResult());
        mapper.insert(row);
        c.setId(((Number) row.get("id")).longValue());
        return c;
    }

    public ReliabilityCalc findById(Long id) {
        Map<String, Object> row = mapper.findById(id);
        return row == null ? null : fromRow(row);
    }

    public List<ReliabilityCalc> listByOwner(Long ownerId) {
        List<ReliabilityCalc> out = new ArrayList<>();
        for (Map<String, Object> row : mapper.findByOwnerId(ownerId)) out.add(fromRow(row));
        return out;
    }

    private ReliabilityCalc fromRow(Map<String, Object> row) {
        ReliabilityCalc c = new ReliabilityCalc();
        c.setId(((Number) row.get("id")).longValue());
        c.setOwnerId(((Number) row.get("ownerId")).longValue());
        c.setDataset((String) row.get("dataset"));
        c.setMethod((String) row.get("method"));
        c.setResult((String) row.get("result"));
        return c;
    }
}
```

`ResearchProposalRepository.java`:
```java
package com.sellm.research;

import org.springframework.stereotype.Repository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class ResearchProposalRepository {
    private final ResearchProposalMapper mapper;
    public ResearchProposalRepository(ResearchProposalMapper mapper) { this.mapper = mapper; }

    public ResearchProposal save(ResearchProposal p) {
        Map<String, Object> row = new HashMap<>();
        row.put("ownerId", p.getOwnerId());
        row.put("topic", p.getTopic());
        row.put("aiDraft", p.getAiDraft());
        row.put("content", p.getContent());
        row.put("status", p.getStatus());
        mapper.insert(row);
        p.setId(((Number) row.get("id")).longValue());
        return p;
    }

    public ResearchProposal findById(Long id) {
        Map<String, Object> row = mapper.findById(id);
        return row == null ? null : fromRow(row);
    }

    public List<ResearchProposal> listByOwner(Long ownerId) {
        List<ResearchProposal> out = new ArrayList<>();
        for (Map<String, Object> row : mapper.findByOwnerId(ownerId)) out.add(fromRow(row));
        return out;
    }

    public void update(ResearchProposal p) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", p.getId());
        row.put("content", p.getContent());
        row.put("aiDraft", p.getAiDraft());
        row.put("status", p.getStatus());
        mapper.update(row);
    }

    private ResearchProposal fromRow(Map<String, Object> row) {
        ResearchProposal p = new ResearchProposal();
        p.setId(((Number) row.get("id")).longValue());
        p.setOwnerId(((Number) row.get("ownerId")).longValue());
        p.setTopic((String) row.get("topic"));
        p.setAiDraft((String) row.get("aiDraft"));
        p.setContent((String) row.get("content"));
        p.setStatus((String) row.get("status"));
        return p;
    }
}
```

- [ ] **Step 11: 运行测试确认通过**

```bash
export PATH="/c/tools/apache-maven-3.9.6/bin:$PATH"
cd /d/works/test/SELLM/dev_workspace
mvn -pl sellm-agent-research -am clean install -q && echo "MODULES OK"
mvn -pl sellm-agent-research clean test 2>&1 | grep -E "Tests run:|BUILD" | tail -3
```
Expected: `MODULES OK`;ResearchRepositoryTest 2 + contextLoads 通过(contextLoads 验证 agent-common 自动装配在 research 上下文工作)。

- [ ] **Step 12: Commit**

```bash
git add sellm-agent-research/
git commit -m "feat(research): 持久化基建(H2+schema+reliability_calc/research_proposal 四件套)+ 接 agent-common"
```
---

### Task 2: ReliabilityService 纯算法(Cronbach α / 分半 / 项总相关,TDD)

**Files:**
- Create: `sellm-agent-research/src/main/java/com/sellm/research/ReliabilityResult.java`(结果 DTO)
- Create: `sellm-agent-research/src/main/java/com/sellm/research/ReliabilityService.java`(纯算法,@Component)
- Test: `sellm-agent-research/src/test/java/com/sellm/research/ReliabilityServiceTest.java`

**Interfaces:**
- Produces:
  - `ReliabilityResult`(Double alpha, Double splitHalf, double[] itemTotal, int itemCount, int subjectCount, List<String> notes)
  - `ReliabilityService.compute(double[][] scores) -> ReliabilityResult`(纯函数,无 IO;非矩形/空抛 IllegalArgumentException;可算但无意义的统计量置 null + note)
- Consumes: 无(纯 JDK + 数值)

> **算法约定(关键)**:scores 行=被试,列=题目。方差统一用**总体方差(/N)**——题方差与总分方差同约定(α 是两者之比,一致即可)。Pearson 相关分母为 0(零方差)时该相关置 null + note,不传 NaN。边界:itemCount<2 → alpha/splitHalf null;subjectCount<2 → 全 null;非矩形/空 → IllegalArgumentException(AppService 转 INVALID_INPUT)。

- [ ] **Step 1: 写失败测试 ReliabilityServiceTest(对标已知值)**

`sellm-agent-research/src/test/java/com/sellm/research/ReliabilityServiceTest.java`:
```java
package com.sellm.research;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ReliabilityServiceTest {

    private final ReliabilityService svc = new ReliabilityService();

    @Test
    void cronbach_alpha_对标手算值() {
        // 4 被试 × 3 题。手算:
        // 题方差(总体/N): 列方差之和;总分方差(总体/N)
        // 被试总分: [9,6,9,6]? 用一个非平凡矩阵
        double[][] scores = {
            {5, 4, 3},
            {4, 4, 3},
            {3, 2, 2},
            {2, 1, 1}
        };
        ReliabilityResult r = svc.compute(scores);
        assertEquals(3, r.getItemCount());
        assertEquals(4, r.getSubjectCount());
        assertNotNull(r.getAlpha());
        // α 应在合理范围(此矩阵题间高度一致,α 偏高);对标重算值
        // 总体方差(/N):ΣitemVar=3.625, totalVar=10.25, α=(3/2)(1−3.625/10.25)=0.969512(容差 1e-3)
        assertEquals(0.9695, r.getAlpha(), 1e-3);
    }

    @Test
    void 项总相关每题一个值且范围合理() {
        double[][] scores = {
            {5, 4, 3},
            {4, 4, 3},
            {3, 2, 2},
            {2, 1, 1}
        };
        ReliabilityResult r = svc.compute(scores);
        assertEquals(3, r.getItemTotal().length);
        for (double v : r.getItemTotal()) {
            assertTrue(v >= -1.0 && v <= 1.0, "相关应在 [-1,1]");
        }
    }

    @Test
    void 分半信度可算且范围合理() {
        double[][] scores = {
            {5, 4, 3, 5},
            {4, 4, 3, 4},
            {3, 2, 2, 3},
            {2, 1, 1, 2}
        };
        ReliabilityResult r = svc.compute(scores);
        assertNotNull(r.getSplitHalf());
        assertTrue(r.getSplitHalf() <= 1.0 + 1e-9);
    }

    @Test
    void 题数不足alpha为null并有note() {
        double[][] scores = { {5}, {4}, {3} }; // K=1
        ReliabilityResult r = svc.compute(scores);
        assertNull(r.getAlpha());
        assertFalse(r.getNotes().isEmpty());
    }

    @Test
    void 被试不足全null并有note() {
        double[][] scores = { {5, 4, 3} }; // N=1
        ReliabilityResult r = svc.compute(scores);
        assertNull(r.getAlpha());
        assertNull(r.getSplitHalf());
        assertFalse(r.getNotes().isEmpty());
    }

    @Test
    void 某题零方差该项总相关为NaN置null安全() {
        // 第3题全同分 → 零方差 → 项总相关分母0
        double[][] scores = {
            {5, 4, 2},
            {4, 3, 2},
            {3, 2, 2},
            {2, 1, 2}
        };
        ReliabilityResult r = svc.compute(scores);
        // itemTotal[2] 应为 NaN 或被安全处理(不抛异常);约定:零方差项置 Double.NaN 不可,改置 0 或标记
        // 本设计:零方差相关置 0.0 并加 note(或单独 nullable 数组);这里断言不抛 + 长度对
        assertEquals(3, r.getItemTotal().length);
        assertFalse(r.getNotes().isEmpty());
    }

    @Test
    void 非矩形矩阵抛IllegalArgumentException() {
        double[][] scores = { {5, 4, 3}, {4, 3} }; // 行长不一
        assertThrows(IllegalArgumentException.class, () -> svc.compute(scores));
    }

    @Test
    void 空矩阵抛IllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> svc.compute(new double[0][]));
    }
}
```

> **实现者注意**:cronbach α=0.9695 已用独立途径(总体方差 /N:ΣitemVar=3.625,totalVar=10.25,α=1.5×(1−3.625/10.25)=0.969512)核算过,是这个固定 4×3 矩阵的真值。实现你的 ReliabilityService 后该断言应通过(容差 1e-3);若不通过,**先信此核算值、查你的实现**(尤其方差是否一致用 /N),不要为通过而调容差或改预期。itemTotal 零方差项的处置(本计划实现里置 0.0 + note):测试断言「不抛异常 + notes 非空」即可。

- [ ] **Step 2: 运行测试确认失败**

```bash
export PATH="/c/tools/apache-maven-3.9.6/bin:$PATH"
cd /d/works/test/SELLM/dev_workspace
mvn -pl sellm-agent-research test -Dtest=ReliabilityServiceTest 2>&1 | grep -E "ERROR|cannot find symbol|Tests run|BUILD" | tail -8
```
Expected: 编译失败(ReliabilityService/ReliabilityResult 未定义)。

- [ ] **Step 3: 创建 ReliabilityResult**

`sellm-agent-research/src/main/java/com/sellm/research/ReliabilityResult.java`:
```java
package com.sellm.research;

import java.util.ArrayList;
import java.util.List;

public class ReliabilityResult {
    private Double alpha;         // null = 不可算
    private Double splitHalf;     // null = 不可算
    private double[] itemTotal;   // 每题一个(零方差项按策略处置)
    private int itemCount;
    private int subjectCount;
    private List<String> notes = new ArrayList<>();

    public Double getAlpha() { return alpha; }
    public void setAlpha(Double alpha) { this.alpha = alpha; }
    public Double getSplitHalf() { return splitHalf; }
    public void setSplitHalf(Double splitHalf) { this.splitHalf = splitHalf; }
    public double[] getItemTotal() { return itemTotal; }
    public void setItemTotal(double[] itemTotal) { this.itemTotal = itemTotal; }
    public int getItemCount() { return itemCount; }
    public void setItemCount(int itemCount) { this.itemCount = itemCount; }
    public int getSubjectCount() { return subjectCount; }
    public void setSubjectCount(int subjectCount) { this.subjectCount = subjectCount; }
    public List<String> getNotes() { return notes; }
    public void setNotes(List<String> notes) { this.notes = notes; }
}
```

- [ ] **Step 4: 实现 ReliabilityService**

`sellm-agent-research/src/main/java/com/sellm/research/ReliabilityService.java`:
```java
package com.sellm.research;

import org.springframework.stereotype.Component;

/** 信效度纯算法:Cronbach α / 分半信度(Spearman-Brown)/ 项总相关。总体方差(/N),约定一致。 */
@Component
public class ReliabilityService {

    public ReliabilityResult compute(double[][] scores) {
        validate(scores);
        int n = scores.length;          // 被试数
        int k = scores[0].length;       // 题目数
        ReliabilityResult r = new ReliabilityResult();
        r.setSubjectCount(n);
        r.setItemCount(k);

        if (n < 2) {
            r.getNotes().add("被试数不足(N<2),无法计算方差/相关");
            r.setItemTotal(new double[k]);
            return r;
        }

        // 被试总分
        double[] totals = new double[n];
        for (int i = 0; i < n; i++) {
            double s = 0;
            for (int j = 0; j < k; j++) s += scores[i][j];
            totals[i] = s;
        }

        // Cronbach α(需 K≥2)
        if (k >= 2) {
            double sumItemVar = 0;
            for (int j = 0; j < k; j++) {
                double[] col = column(scores, j);
                sumItemVar += variance(col);
            }
            double totalVar = variance(totals);
            if (totalVar == 0.0) {
                r.getNotes().add("总分方差为 0,Cronbach α 无法计算");
            } else {
                double alpha = (k / (double) (k - 1)) * (1 - sumItemVar / totalVar);
                r.setAlpha(alpha);
            }
        } else {
            r.getNotes().add("题数不足(K<2),Cronbach α 无法计算");
        }

        // 项总相关
        double[] itemTotal = new double[k];
        for (int j = 0; j < k; j++) {
            Double corr = pearson(column(scores, j), totals);
            if (corr == null) {
                itemTotal[j] = 0.0;
                r.getNotes().add("第 " + (j + 1) + " 题方差为 0,项总相关置 0");
            } else {
                itemTotal[j] = corr;
            }
        }
        r.setItemTotal(itemTotal);

        // 分半信度(Spearman-Brown):奇偶分半
        if (k >= 2) {
            double[] half1 = new double[n], half2 = new double[n];
            for (int i = 0; i < n; i++) {
                double a = 0, b = 0;
                for (int j = 0; j < k; j++) {
                    if (j % 2 == 0) a += scores[i][j]; else b += scores[i][j];
                }
                half1[i] = a; half2[i] = b;
            }
            Double rHalf = pearson(half1, half2);
            if (rHalf == null) {
                r.getNotes().add("分半得分方差为 0,分半信度无法计算");
            } else {
                r.setSplitHalf(2 * rHalf / (1 + rHalf));
            }
        }

        return r;
    }

    private void validate(double[][] scores) {
        if (scores == null || scores.length == 0 || scores[0] == null || scores[0].length == 0) {
            throw new IllegalArgumentException("分数矩阵不能为空");
        }
        int k = scores[0].length;
        for (double[] row : scores) {
            if (row == null || row.length != k) {
                throw new IllegalArgumentException("分数矩阵必须为矩形(各被试题数一致)");
            }
        }
    }

    private double[] column(double[][] m, int j) {
        double[] c = new double[m.length];
        for (int i = 0; i < m.length; i++) c[i] = m[i][j];
        return c;
    }

    /** 总体方差(/N)。 */
    private double variance(double[] x) {
        double mean = 0;
        for (double v : x) mean += v;
        mean /= x.length;
        double s = 0;
        for (double v : x) s += (v - mean) * (v - mean);
        return s / x.length;
    }

    /** Pearson 相关;任一方差为 0(分母 0)返回 null。 */
    private Double pearson(double[] x, double[] y) {
        int n = x.length;
        double mx = 0, my = 0;
        for (int i = 0; i < n; i++) { mx += x[i]; my += y[i]; }
        mx /= n; my /= n;
        double sxy = 0, sxx = 0, syy = 0;
        for (int i = 0; i < n; i++) {
            double dx = x[i] - mx, dy = y[i] - my;
            sxy += dx * dy; sxx += dx * dx; syy += dy * dy;
        }
        if (sxx == 0.0 || syy == 0.0) return null;
        return sxy / Math.sqrt(sxx * syy);
    }
}
```

- [ ] **Step 5: 实现者先确立 α 真实预期值,再运行测试**

实现者先对 Step1 测试里那个固定 4×3 矩阵,用手算或可信工具算出真实 Cronbach α(按总体方差 /N),把 `cronbach_alpha_对标手算值` 的预期值改为真值(容差 1e-3)。然后:
```bash
export PATH="/c/tools/apache-maven-3.9.6/bin:$PATH"
cd /d/works/test/SELLM/dev_workspace
mvn -pl sellm-agent-research test -Dtest=ReliabilityServiceTest 2>&1 | grep -E "Tests run:|BUILD|expected:|but was:" | tail -8
```
Expected: 8 测试全通过。若 α 断言失败,核对是预期值算错还是实现错——**先信手算,再查实现**(不可为通过而调容差掩盖)。零方差项策略与测试断言一致。

- [ ] **Step 6: Commit**

```bash
git add sellm-agent-research/
git commit -m "feat(research): ReliabilityService 纯算法(Cronbach α/分半/项总相关 + 边界处置)"
```
---

### Task 3: 信效度 API + 课题书编排 + Controllers + 智能层客户端

**Files:**
- Create: `sellm-agent-research/src/main/java/com/sellm/research/dto/{ReliabilityRequest,ReliabilityResponse,GenerateProposalRequest,EditRequest,ProposalResponse}.java`
- Create: `sellm-agent-research/src/main/java/com/sellm/research/ReliabilityAppService.java`
- Create: `sellm-agent-research/src/main/java/com/sellm/research/ReliabilityController.java`
- Create: `sellm-agent-research/src/main/java/com/sellm/research/SmartLayerClient.java`(typed 接口)
- Create: `sellm-agent-research/src/main/java/com/sellm/research/HttpResearchSmartLayerClient.java`(继承 AbstractHttpSmartLayerClient)
- Create: `sellm-agent-research/src/main/java/com/sellm/research/ProposalAppService.java`
- Create: `sellm-agent-research/src/main/java/com/sellm/research/ProposalController.java`
- Test: `sellm-agent-research/src/test/java/com/sellm/research/ReliabilityApiTest.java`
- Test: `sellm-agent-research/src/test/java/com/sellm/research/ProposalApiTest.java`
- Test: `sellm-agent-research/src/test/java/com/sellm/research/ProposalSanitizeHardBlockTest.java`

**Interfaces:**
- Consumes: Task1 Repository,Task2 ReliabilityService/ReliabilityResult,core 的 Anonymizer(自动装配)/Result/ErrorCode/BusinessException,agent-common 的 AbstractHttpSmartLayerClient/SmartLayerProperties/SmartLayerException/UnauthorizedException/AgentExceptionHandler(自动装配)
- Produces: 信效度 + 课题书 REST 端点

> 信效度:Controller→AppService→ReliabilityService(本地)→存 reliability_calc。课题书:镜像 teaching 的 generate/edit/finalize(脱敏→Python→还原→DRAFT/FINALIZED/冻结)。鉴权 X-User-Id;行级权限 ownerId。异常处理器来自 agent-common(不写)。

- [ ] **Step 1: 写失败测试(3 个测试类)**

`ReliabilityApiTest.java`:
```java
package com.sellm.research;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReliabilityApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void 计算信效度返回结果并落库() throws Exception {
        Map<String, Object> body = Map.of("scores",
            new int[][]{{5,4,3},{4,4,3},{3,2,2},{2,1,1}});
        mvc.perform(post("/api/research/reliability")
                .header("X-User-Id", "7").contentType("application/json")
                .content(json.writeValueAsString(body)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").isNumber())
            .andExpect(jsonPath("$.data.result.alpha").isNumber())
            .andExpect(jsonPath("$.data.result.itemCount").value(3))
            .andExpect(jsonPath("$.data.result.subjectCount").value(4));
    }

    @Test
    void 非法矩阵返400() throws Exception {
        Map<String, Object> body = Map.of("scores", new int[][]{{5,4,3},{4,3}}); // 非矩形
        mvc.perform(post("/api/research/reliability")
                .header("X-User-Id", "7").contentType("application/json")
                .content(json.writeValueAsString(body)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void 他人信效度记录403() throws Exception {
        var res = mvc.perform(post("/api/research/reliability")
                .header("X-User-Id", "7").contentType("application/json")
                .content(json.writeValueAsString(Map.of("scores", new int[][]{{5,4,3},{2,1,1}}))))
            .andReturn();
        long id = json.readTree(res.getResponse().getContentAsString()).path("data").path("id").asLong();
        mvc.perform(get("/api/research/reliability/" + id).header("X-User-Id", "8"))
            .andExpect(status().isForbidden());
    }

    @Test
    void 缺X_User_Id返401() throws Exception {
        mvc.perform(post("/api/research/reliability")
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("scores", new int[][]{{1,2},{3,4}}))))
            .andExpect(status().isUnauthorized());
    }
}
```

`ProposalApiTest.java`(镜像 teaching TeachingApiTest,用 StubSmartLayerClient @Primary):
```java
package com.sellm.research;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
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
class ProposalApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired StubClient stub;

    @TestConfiguration
    static class Cfg {
        @Bean @Primary StubClient stubClient() { return new StubClient(); }
    }
    static class StubClient implements SmartLayerClient {
        final AtomicReference<String> lastTopic = new AtomicReference<>();
        volatile boolean throwError = false;
        @Override public String generate(String topic) {
            if (throwError) throw new com.sellm.agentcommon.SmartLayerException("down");
            lastTopic.set(topic);
            return "[AI 生成] 课题申报书";
        }
    }

    private long createDraft(long uid) throws Exception {
        var res = mvc.perform(post("/api/research/proposals")
                .header("X-User-Id", String.valueOf(uid)).contentType("application/json")
                .content(json.writeValueAsString(Map.of("topic", "融合教育师资研究"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("DRAFT"))
            .andReturn();
        return json.readTree(res.getResponse().getContentAsString()).path("data").path("id").asLong();
    }

    @Test
    void 生成课题书草案DRAFT() throws Exception {
        stub.throwError = false;
        createDraft(7L);
    }

    @Test
    void 编辑后定稿FINALIZED且冻结() throws Exception {
        long id = createDraft(7L);
        mvc.perform(put("/api/research/proposals/" + id).header("X-User-Id","7")
                .contentType("application/json").content(json.writeValueAsString(Map.of("content","改稿"))))
            .andExpect(status().isOk());
        mvc.perform(post("/api/research/proposals/" + id + "/finalize").header("X-User-Id","7"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("FINALIZED"));
        // 已定稿编辑→400
        mvc.perform(put("/api/research/proposals/" + id).header("X-User-Id","7")
                .contentType("application/json").content(json.writeValueAsString(Map.of("content","再改"))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void 他人课题书403() throws Exception {
        long id = createDraft(7L);
        mvc.perform(get("/api/research/proposals/" + id).header("X-User-Id","8"))
            .andExpect(status().isForbidden());
    }

    @Test
    void Python不可用降级DRAFT保留() throws Exception {
        stub.throwError = true;
        mvc.perform(post("/api/research/proposals").header("X-User-Id","7")
                .contentType("application/json").content(json.writeValueAsString(Map.of("topic","x"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("DRAFT"))
            .andExpect(jsonPath("$.data.content").value(org.hamcrest.Matchers.containsString("失败")));
        stub.throwError = false;
    }
}
```

`ProposalSanitizeHardBlockTest.java`(独立类,@Primary 抛 AnonymizationException + LocalStub):
```java
package com.sellm.research;

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
class ProposalSanitizeHardBlockTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired LocalStub stub;

    @TestConfiguration
    static class Cfg {
        @Bean @Primary Anonymizer throwingAnonymizer() {
            return new Anonymizer() {
                public AnonymizationResult anonymize(String t, List<String> n, List<String> s) {
                    throw new AnonymizationException("forced");
                }
                public String restore(String t, Map<String,String> m) { return t; }
            };
        }
        @Bean @Primary LocalStub localStub() { return new LocalStub(); }
    }
    static class LocalStub implements SmartLayerClient {
        volatile boolean called = false;
        @Override public String generate(String topic) { called = true; return "x"; }
    }

    @Test
    void 脱敏失败返400且不调Python() throws Exception {
        mvc.perform(post("/api/research/proposals").header("X-User-Id","7")
                .contentType("application/json").content(json.writeValueAsString(Map.of("topic","x"))))
            .andExpect(status().isBadRequest());
        assertFalse(stub.called, "脱敏失败应硬阻断");
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

```bash
export PATH="/c/tools/apache-maven-3.9.6/bin:$PATH"
cd /d/works/test/SELLM/dev_workspace
mvn -pl sellm-agent-research test -Dtest='ReliabilityApiTest,ProposalApiTest' 2>&1 | grep -E "ERROR|cannot find symbol|BUILD" | tail -8
```
Expected: 编译失败(Controller/AppService/DTO/SmartLayerClient 未定义)。

- [ ] **Step 3: 创建 DTO(5 个)**

`dto/ReliabilityRequest.java`:
```java
package com.sellm.research.dto;

public class ReliabilityRequest {
    private String method;     // 可空
    private double[][] scores;
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public double[][] getScores() { return scores; }
    public void setScores(double[][] scores) { this.scores = scores; }
}
```

`dto/ReliabilityResponse.java`:
```java
package com.sellm.research.dto;

import com.sellm.research.ReliabilityResult;

public class ReliabilityResponse {
    private Long id;
    private ReliabilityResult result;
    public ReliabilityResponse(Long id, ReliabilityResult result) { this.id = id; this.result = result; }
    public Long getId() { return id; }
    public ReliabilityResult getResult() { return result; }
}
```

`dto/GenerateProposalRequest.java`:
```java
package com.sellm.research.dto;

public class GenerateProposalRequest {
    private String topic;
    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }
}
```

`dto/EditRequest.java`:
```java
package com.sellm.research.dto;

public class EditRequest {
    private String content;
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}
```

`dto/ProposalResponse.java`:
```java
package com.sellm.research.dto;

public class ProposalResponse {
    private Long id;
    private String status;
    private String content;
    private String aiDraft;
    public ProposalResponse(Long id, String status, String content, String aiDraft) {
        this.id = id; this.status = status; this.content = content; this.aiDraft = aiDraft;
    }
    public Long getId() { return id; }
    public String getStatus() { return status; }
    public String getContent() { return content; }
    public String getAiDraft() { return aiDraft; }
}
```

- [ ] **Step 4: 创建 SmartLayerClient 接口 + HttpResearchSmartLayerClient**

`SmartLayerClient.java`:
```java
package com.sellm.research;

/** 调 Python 智能层生成课题申报书。 */
public interface SmartLayerClient {
    /** @param topic 已脱敏的课题主题 */
    String generate(String topic);
}
```

`HttpResearchSmartLayerClient.java`:
```java
package com.sellm.research;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sellm.agentcommon.AbstractHttpSmartLayerClient;
import com.sellm.agentcommon.SmartLayerException;
import com.sellm.agentcommon.SmartLayerProperties;
import org.springframework.stereotype.Component;

/** REST 调 Python /v1/agents/research/invoke。传输由 AbstractHttpSmartLayerClient 提供。 */
@Component
public class HttpResearchSmartLayerClient extends AbstractHttpSmartLayerClient implements SmartLayerClient {

    private final ObjectMapper json = new ObjectMapper();

    public HttpResearchSmartLayerClient(SmartLayerProperties props) {
        super(props);
    }

    @Override
    public String generate(String topic) {
        try {
            ObjectNode body = json.createObjectNode();
            body.put("topic", topic);
            String resp = send("/v1/agents/research/invoke", json.writeValueAsString(body));
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

- [ ] **Step 5: 实现 ReliabilityAppService + ReliabilityController**

`ReliabilityAppService.java`:
```java
package com.sellm.research;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sellm.common.BusinessException;
import com.sellm.common.ErrorCode;
import com.sellm.research.dto.ReliabilityRequest;
import com.sellm.research.dto.ReliabilityResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ReliabilityAppService {

    private final ReliabilityService reliabilityService;
    private final ReliabilityCalcRepository repo;
    private final ObjectMapper json = new ObjectMapper();

    public ReliabilityAppService(ReliabilityService reliabilityService, ReliabilityCalcRepository repo) {
        this.reliabilityService = reliabilityService;
        this.repo = repo;
    }

    public ReliabilityResponse compute(Long userId, ReliabilityRequest req) {
        ReliabilityResult result;
        try {
            result = reliabilityService.compute(req.getScores());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, e.getMessage());
        }
        ReliabilityCalc c = new ReliabilityCalc();
        c.setOwnerId(userId);
        c.setMethod(req.getMethod() == null ? "cronbach+splithalf+itemtotal" : req.getMethod());
        try {
            c.setDataset(json.writeValueAsString(req.getScores()));
            c.setResult(json.writeValueAsString(result));
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "序列化失败");
        }
        repo.save(c);
        return new ReliabilityResponse(c.getId(), result);
    }

    public ReliabilityResponse get(Long userId, Long id) {
        ReliabilityCalc c = repo.findById(id);
        if (c == null) throw new BusinessException(ErrorCode.NOT_FOUND, "记录不存在");
        if (!c.getOwnerId().equals(userId)) throw new BusinessException(ErrorCode.ACCESS_DENIED, "无权访问");
        ReliabilityResult result;
        try { result = json.readValue(c.getResult(), ReliabilityResult.class); }
        catch (Exception e) { throw new BusinessException(ErrorCode.INVALID_INPUT, "结果反序列化失败"); }
        return new ReliabilityResponse(c.getId(), result);
    }

    public List<ReliabilityCalc> listMine(Long userId) {
        return repo.listByOwner(userId);
    }
}
```

`ReliabilityController.java`:
```java
package com.sellm.research;

import com.sellm.agentcommon.UnauthorizedException;
import com.sellm.common.Result;
import com.sellm.research.dto.ReliabilityRequest;
import com.sellm.research.dto.ReliabilityResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/research/reliability")
public class ReliabilityController {

    private final ReliabilityAppService appService;
    public ReliabilityController(ReliabilityAppService appService) { this.appService = appService; }

    @PostMapping
    public Result<ReliabilityResponse> compute(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                                               @RequestBody ReliabilityRequest req) {
        requireUser(userId);
        return Result.ok(appService.compute(userId, req));
    }

    @GetMapping("/{id}")
    public Result<ReliabilityResponse> get(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                                           @PathVariable("id") Long id) {
        requireUser(userId);
        return Result.ok(appService.get(userId, id));
    }

    @GetMapping
    public Result<List<ReliabilityCalc>> mine(@RequestHeader(value = "X-User-Id", required = false) Long userId) {
        requireUser(userId);
        return Result.ok(appService.listMine(userId));
    }

    private void requireUser(Long userId) {
        if (userId == null) throw new UnauthorizedException("缺少 X-User-Id");
    }
}
```

- [ ] **Step 6: 实现 ProposalAppService + ProposalController(镜像 teaching)**

`ProposalAppService.java`:
```java
package com.sellm.research;

import com.sellm.agentcommon.SmartLayerException;
import com.sellm.anonymizer.AnonymizationException;
import com.sellm.anonymizer.AnonymizationResult;
import com.sellm.anonymizer.Anonymizer;
import com.sellm.common.BusinessException;
import com.sellm.common.ErrorCode;
import com.sellm.research.dto.EditRequest;
import com.sellm.research.dto.GenerateProposalRequest;
import com.sellm.research.dto.ProposalResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProposalAppService {

    private final ResearchProposalRepository repo;
    private final SmartLayerClient smartLayer;
    private final Anonymizer anonymizer;

    public ProposalAppService(ResearchProposalRepository repo, SmartLayerClient smartLayer, Anonymizer anonymizer) {
        this.repo = repo;
        this.smartLayer = smartLayer;
        this.anonymizer = anonymizer;
    }

    public ProposalResponse generate(Long userId, GenerateProposalRequest req) {
        if (req.getTopic() == null || req.getTopic().isBlank())
            throw new BusinessException(ErrorCode.INVALID_INPUT, "课题主题不能为空");
        ResearchProposal p = new ResearchProposal();
        p.setOwnerId(userId);
        p.setTopic(req.getTopic());
        p.setStatus("DRAFT");
        repo.save(p);
        String content;
        try {
            AnonymizationResult anon = anonymizer.anonymize(req.getTopic(), List.of(), List.of());
            String aiText = smartLayer.generate(anon.getAnonymizedText());
            content = anonymizer.restore(aiText, anon.getRestoreMap());
        } catch (AnonymizationException ae) {
            throw new BusinessException(ErrorCode.ANONYMIZATION_FAILED, "脱敏校验未通过,已阻断出网");
        } catch (SmartLayerException se) {
            content = "AI 生成失败,可重试或手动撰写。";
        }
        p.setAiDraft(content);
        p.setContent(content);
        repo.update(p);
        return resp(p);
    }

    public ProposalResponse edit(Long userId, Long id, EditRequest req) {
        ResearchProposal p = requireOwned(userId, id);
        if ("FINALIZED".equals(p.getStatus()))
            throw new BusinessException(ErrorCode.INVALID_INPUT, "已定稿不可编辑");
        if (req.getContent() == null || req.getContent().isBlank())
            throw new BusinessException(ErrorCode.INVALID_INPUT, "内容不能为空");
        p.setContent(req.getContent());
        repo.update(p);
        return resp(p);
    }

    public ProposalResponse finalizeProposal(Long userId, Long id) {
        ResearchProposal p = requireOwned(userId, id);
        p.setStatus("FINALIZED");
        repo.update(p);
        return resp(p);
    }

    public ProposalResponse get(Long userId, Long id) {
        return resp(requireOwned(userId, id));
    }

    public List<ResearchProposal> listMine(Long userId) {
        return repo.listByOwner(userId);
    }

    private ResearchProposal requireOwned(Long userId, Long id) {
        ResearchProposal p = repo.findById(id);
        if (p == null) throw new BusinessException(ErrorCode.NOT_FOUND, "课题书不存在");
        if (!p.getOwnerId().equals(userId)) throw new BusinessException(ErrorCode.ACCESS_DENIED, "无权访问");
        return p;
    }

    private ProposalResponse resp(ResearchProposal p) {
        return new ProposalResponse(p.getId(), p.getStatus(), p.getContent(), p.getAiDraft());
    }
}
```

`ProposalController.java`:
```java
package com.sellm.research;

import com.sellm.agentcommon.UnauthorizedException;
import com.sellm.common.Result;
import com.sellm.research.dto.EditRequest;
import com.sellm.research.dto.GenerateProposalRequest;
import com.sellm.research.dto.ProposalResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/research/proposals")
public class ProposalController {

    private final ProposalAppService appService;
    public ProposalController(ProposalAppService appService) { this.appService = appService; }

    @PostMapping
    public Result<ProposalResponse> generate(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                                             @RequestBody GenerateProposalRequest req) {
        requireUser(userId);
        return Result.ok(appService.generate(userId, req));
    }

    @PutMapping("/{id}")
    public Result<ProposalResponse> edit(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                                         @PathVariable("id") Long id, @RequestBody EditRequest req) {
        requireUser(userId);
        return Result.ok(appService.edit(userId, id, req));
    }

    @PostMapping("/{id}/finalize")
    public Result<ProposalResponse> finalizeProposal(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                                                     @PathVariable("id") Long id) {
        requireUser(userId);
        return Result.ok(appService.finalizeProposal(userId, id));
    }

    @GetMapping("/{id}")
    public Result<ProposalResponse> get(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                                        @PathVariable("id") Long id) {
        requireUser(userId);
        return Result.ok(appService.get(userId, id));
    }

    @GetMapping
    public Result<List<ResearchProposal>> mine(@RequestHeader(value = "X-User-Id", required = false) Long userId) {
        requireUser(userId);
        return Result.ok(appService.listMine(userId));
    }

    private void requireUser(Long userId) {
        if (userId == null) throw new UnauthorizedException("缺少 X-User-Id");
    }
}
```

- [ ] **Step 7: 运行测试确认通过**

```bash
export PATH="/c/tools/apache-maven-3.9.6/bin:$PATH"
cd /d/works/test/SELLM/dev_workspace
mvn -pl sellm-agent-research clean test 2>&1 | grep -E "Tests run:|BUILD" | tail -5
```
Expected: 全绿(ReliabilityServiceTest 8 + ReliabilityApiTest 4 + ProposalApiTest 4 + ProposalSanitizeHardBlockTest 1 + ResearchRepositoryTest 2 + contextLoads)。

> AgentExceptionHandler 来自 agent-common 自动装配——若 401/403/404 映射不生效,确认 agent-research 依赖了 sellm-agent-common 且没自己的 ExceptionHandler。脱敏/SmartLayer 异常的 import 来自 com.sellm.agentcommon。报 BLOCKED 附错误。

- [ ] **Step 8: Commit**

```bash
git add sellm-agent-research/
git commit -m "feat(research): 信效度API + 课题书编排(脱敏/草案定稿/冻结)+ Controllers + 智能层客户端"
```
---

### Task 4: Python 智能层 research 编排(mock LLM,仅课题书)

**Files:**
- Create: `ai-smart-layer/app/agents/research.py`(替换 P0 空壳)
- Modify: `ai-smart-layer/app/main.py`(加 POST /v1/agents/research/invoke)
- Test: `ai-smart-layer/tests/test_research.py`

**Interfaces:**
- Consumes: app/adapters/llm.py 的 get_llm()
- Produces: `agents.research.invoke_research(payload)→{content, mock}`;main `/v1/agents/research/invoke`

> 仅课题书走 Python(信效度纯 Java 不调 Python)。research 编排:topic → mock LLM 生成课题申报书文本。Python 不持明文/不还原。

- [ ] **Step 1: 写失败测试 test_research.py**

`ai-smart-layer/tests/test_research.py`:
```python
import pytest
from httpx import AsyncClient, ASGITransport
from app.main import app
from app.agents.research import invoke_research


@pytest.mark.asyncio
async def test_invoke_research():
    out = await invoke_research({"topic": "特殊教育融合班级师资配置研究"})
    assert "content" in out and isinstance(out["content"], str) and out["content"]
    assert out.get("mock") is True


@pytest.mark.asyncio
async def test_research_invoke_endpoint():
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        resp = await client.post("/v1/agents/research/invoke", json={"topic": "[学校1] 的融合教育研究"})
    assert resp.status_code == 200
    assert "content" in resp.json()


@pytest.mark.asyncio
async def test_python_no_restore():
    out = await invoke_research({"topic": "[学校1] 研究"})
    assert isinstance(out["content"], str)  # 无 restore 逻辑
```

- [ ] **Step 2: 运行测试确认失败(或无 Python 静态)**

```bash
cd /d/works/test/SELLM/dev_workspace/ai-smart-layer
pytest tests/test_research.py -v 2>&1 | tail -10 || echo "(deferred:无 Python 3.11)"
```
Expected(有 Python):FAIL(invoke_research 旧空壳 / 端点不存在)。无 Python:静态审查。

- [ ] **Step 3: 实现 agents/research.py**

`ai-smart-layer/app/agents/research.py`(替换 P0 空壳):
```python
"""科研助手 Agent — 课题申报书生成(mock LLM,不挂 RAG)。Python 只见脱敏文本,不还原。"""
from app.adapters.llm import get_llm


async def invoke_research(payload: dict) -> dict:
    topic = payload.get("topic", "")
    prompt = (
        "你是特殊教育科研助手。基于以下课题主题,撰写一份课题申报书草案"
        "(含研究背景、研究目标、研究方法、预期成果)。\n"
        f"课题主题:{topic}\n申报书草案:"
    )
    llm = get_llm()
    content = await llm.generate(prompt)
    return {"content": content, "mock": True}
```

- [ ] **Step 4: main.py 加 research 端点**

修改 `ai-smart-layer/app/main.py`,加(import + endpoint;保留 /health、qa、teaching、通用 invoke):
```python
from app.agents.research import invoke_research


@app.post("/v1/agents/research/invoke")
async def research_invoke(payload: dict):
    """科研助手 Agent 课题申报书生成(接收脱敏后 topic)。"""
    return await invoke_research(payload)
```

- [ ] **Step 5: 运行测试确认通过(或静态)**

```bash
cd /d/works/test/SELLM/dev_workspace/ai-smart-layer
pytest tests/test_research.py tests/test_teaching.py tests/test_qa.py tests/test_health.py -v 2>&1 | tail -12 || echo "(deferred:无 Python 3.11,静态审查)"
```
Expected(有 Python):test_research 3 + 既有全通过。无 Python:静态审查 invoke_research 签名/await/返回结构与测试断言一致,报告 deferred。

- [ ] **Step 6: Commit**

```bash
cd /d/works/test/SELLM/dev_workspace
git add ai-smart-layer/
git commit -m "feat(research): Python 智能层 research 编排(mock LLM 课题书)+ /v1/agents/research/invoke"
```

---

### Task 5: agent-research 全量回归 + 文档

**Files:**
- Verification only(+ `.claude/CLAUDE_CHANGES.md`)

**Interfaces:**
- Produces: 确认 agent-research 不破坏全 reactor;10 模块绿

- [ ] **Step 1: 全 reactor clean install(10 模块)**

```bash
export PATH="/c/tools/apache-maven-3.9.6/bin:$PATH"
cd /d/works/test/SELLM/dev_workspace
mvn clean install 2>&1 | grep -E "Reactor Summary|SUCCESS \[|FAILURE \[|BUILD SUCCESS|BUILD FAILURE|Tests run:.*Failures: [1-9]" | tail -16
```
Expected: 10 模块全 SUCCESS;agent-research 测试全绿;qa 16 / teaching 15 / backend 242 不受影响。

- [ ] **Step 2: agent-research 测试汇总**

```bash
export PATH="/c/tools/apache-maven-3.9.6/bin:$PATH"
cd /d/works/test/SELLM/dev_workspace
mvn -pl sellm-agent-research clean test 2>&1 | grep -E "Tests run:.*Failures|BUILD" | tail -3
```
Expected: 全绿(Reliability 8 + ReliabilityApi 4 + ProposalApi 4 + SanitizeHardBlock 1 + Repository 2 + contextLoads 1)。

- [ ] **Step 3: 前端 build**

```bash
cd /d/works/test/SELLM/dev_workspace/frontend
npm run build 2>&1 | grep -E "built in|error" | tail -2
```
Expected: `✓ built in X.XXs`。

- [ ] **Step 4: 追加变更记录 + 确认工作树**

向 `.claude/CLAUDE_CHANGES.md` 追加 FEATURE 记录(信效度纯算法 + 课题书复用模板)。`git status --short` 应干净(无待提交,或仅文档)。

> `.env.example` 无需改(SELLM_SMARTLAYER_* 已统一,research 共用)。若无文件改动,本任务为纯验证,无提交。

---

## 文件清单总览

```
dev_workspace/
├── sellm-agent-research/                 (主体,P0 空壳填充)
│   ├── pom.xml                           (MODIFY: +agent-common/mybatis/jdbc/h2)
│   └── src/
│       ├── main/java/com/sellm/research/
│       │   ├── ReliabilityService.java   (NEW 纯算法)
│       │   ├── ReliabilityResult.java    (NEW)
│       │   ├── ReliabilityAppService.java / ReliabilityController.java (NEW)
│       │   ├── ProposalAppService.java / ProposalController.java (NEW)
│       │   ├── SmartLayerClient.java / HttpResearchSmartLayerClient.java (NEW)
│       │   ├── ReliabilityCalc.java / ResearchProposal.java (NEW 实体)
│       │   ├── *Mapper.java / *Repository.java (NEW 四件套)
│       │   └── dto/{ReliabilityRequest,ReliabilityResponse,GenerateProposalRequest,EditRequest,ProposalResponse}.java (NEW)
│       ├── main/resources/{application.yml(MODIFY), schema.sql(NEW), mybatis/*.xml(NEW)}
│       └── test/{Reliability*Test, Proposal*Test, ResearchRepositoryTest, resources/application.yml(MODIFY)}
├── ai-smart-layer/app/
│   ├── agents/research.py                (MODIFY: 真实编排)
│   └── main.py                           (MODIFY: /v1/agents/research/invoke)
└── (.env.example 无需改)
```

> **收益验证**:agent-research 是 agent-common 抽取后**第一个复用模板的新 agent**——课题书部分零复制脚手架(只写 typed SmartLayerClient + HttpResearchSmartLayerClient 继承基类 + 业务),印证收敛成效。信效度展示了 Agent 也能承载纯本地确定性算法(不出网、不调 Python)。
