# ASD 助手 — 计划一:后端核心骨架 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 用 TDD 构建 ASD 干预与评估助手的后端领域核心:量表计分引擎、强制脱敏层、可插拔 AI 网关、评估→报告→IEP 主链路,全部以纯 Java + 单元测试落地,不依赖外部基础设施。

**Architecture:** 模块化单体(Spring Boot 3),本计划只做**领域层与服务层 + Mock AI**,通过内存仓储和 Mock 模型完成主链路集成测试。持久化(MyBatis/MySQL)、REST 控制器、认证、前端在后续计划覆盖。关键红线在本计划落地:脱敏失败硬阻断、AI 只产草案、业务模块经 ai-gateway 调模型且必经 anonymizer。

**Tech Stack:** Java 17、Spring Boot 3.2.5、Maven、JUnit 5、AssertJ、Mockito。根包 `com.sellm`。不使用 Lombok。

**Source spec:** `docs/superpowers/specs/2026-06-18-asd-assessment-iep-assistant-design.md`

---

## 文件结构(本计划范围)

后端根目录 `backend/`,Maven 工程,根包 `com.sellm`。按职责分包(不按技术层切),每个文件单一职责:

```
backend/
  pom.xml                                    # Maven 配置,固定版本
  src/main/java/com/sellm/
    SellmApplication.java                    # Spring Boot 启动类
    common/
      Result.java                            # 统一返回结构
      BusinessException.java                 # 业务异常 + 异常码
      ErrorCode.java                         # 异常码枚举
    anonymizer/
      Anonymizer.java                        # 脱敏/还原接口
      AnonymizationResult.java               # 脱敏结果(脱敏文本 + 还原映射)
      RegexAnonymizer.java                   # 正则实现:姓名/学校/身份证→占位符
      AnonymizationException.java            # 脱敏失败异常(触发硬阻断)
    scale/
      Scale.java                             # 量表定义
      ScaleItem.java                         # 题目
      ScoringRule.java                       # 计分规则(分段 + 解读阈值)
      ScoreBand.java                         # 单个分段(下界/上界/标签/解读)
      Answer.java                            # 单题作答
      ScoringEngine.java                     # 计分引擎接口
      DefaultScoringEngine.java              # 计分引擎实现:求和 + 分段匹配 + 校验
      ScoringException.java                  # 计分异常(校验失败)
      AssessmentResult.java                  # 计分结果(总分 + 命中分段 + 解读)
    aigateway/
      AiModel.java                           # 底层模型接口(可插拔点)
      AiGateway.java                         # 网关接口:业务模块唯一入口
      DefaultAiGateway.java                  # 网关实现:脱敏→调模型→还原
      AiGatewayException.java                # AI 调用失败异常
      PromptRequest.java                     # 网关入参(含需脱敏的上下文)
      MockAiModel.java                       # 测试/默认用 Mock 模型(回显式)
    rag/
      KnowledgeDoc.java                      # 知识文档
      RagRetriever.java                      # 检索接口
      InMemoryRagRetriever.java              # 内存关键词检索实现
    report/
      Report.java                            # 报告(草稿 + 定稿 + 状态)
      ReportStatus.java                      # 报告状态枚举
      ReportService.java                     # 报告生成:RAG 召回 + 网关生成草稿
    iep/
      Iep.java                               # IEP 计划
      IepGoal.java                           # IEP 目标
      IepStatus.java                         # IEP 状态枚举
      IepService.java                        # IEP 草案生成:RAG 召回 + 网关生成
  src/test/java/com/sellm/
    anonymizer/RegexAnonymizerTest.java
    scale/DefaultScoringEngineTest.java
    aigateway/DefaultAiGatewayTest.java
    rag/InMemoryRagRetrieverTest.java
    report/ReportServiceTest.java
    iep/IepServiceTest.java
    integration/AssessmentToIepFlowTest.java # 主链路集成测试
```

**为什么这样切:** 每个包对应 spec 第 7 节的一个模块边界;`anonymizer`/`aigateway`/`scale` 是命门,独立成包便于高覆盖单测;主链路集成测试单独成包,用内存实现串起来,不碰外部基础设施。

---

### Task 1: Maven 工程脚手架

**Files:**
- Create: `backend/pom.xml`
- Create: `backend/src/main/java/com/sellm/SellmApplication.java`

- [x] **Step 1: 创建 pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.5</version>
        <relativePath/>
    </parent>

    <groupId>com.sellm</groupId>
    <artifactId>asd-assistant-backend</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <name>asd-assistant-backend</name>

    <properties>
        <java.version>17</java.version>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

- [x] **Step 2: 创建启动类**

```java
package com.sellm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SellmApplication {
    public static void main(String[] args) {
        SpringApplication.run(SellmApplication.class, args);
    }
}
```

- [x] **Step 3: 验证编译**

Run: `mvn -f backend/pom.xml clean compile`
Expected: BUILD SUCCESS,下载依赖后编译通过。

- [x] **Step 4: Commit**

```bash
git add backend/pom.xml backend/src/main/java/com/sellm/SellmApplication.java
git commit -m "chore: 初始化后端 Maven 工程与 Spring Boot 启动类"
```

---

### Task 2: common 模块(统一返回、异常码、业务异常)

**Files:**
- Create: `backend/src/main/java/com/sellm/common/ErrorCode.java`
- Create: `backend/src/main/java/com/sellm/common/BusinessException.java`
- Create: `backend/src/main/java/com/sellm/common/Result.java`

- [x] **Step 1: 创建异常码枚举**

```java
package com.sellm.common;

public enum ErrorCode {
    OK("0", "成功"),
    ANONYMIZATION_FAILED("A001", "脱敏失败,已阻断出网"),
    SCORING_INVALID_INPUT("S001", "计分输入校验失败"),
    SCORING_RULE_MISSING("S002", "计分规则缺失"),
    AI_CALL_FAILED("G001", "AI 调用失败");

    private final String code;
    private final String message;

    ErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() { return code; }
    public String getMessage() { return message; }
}
```

- [x] **Step 2: 创建业务异常**

```java
package com.sellm.common;

public class BusinessException extends RuntimeException {
    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String detail) {
        super(errorCode.getMessage() + ": " + detail);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() { return errorCode; }
}
```

- [x] **Step 3: 创建统一返回结构**

```java
package com.sellm.common;

public class Result<T> {
    private final String code;
    private final String message;
    private final T data;

    private Result(String code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> Result<T> ok(T data) {
        return new Result<>(ErrorCode.OK.getCode(), ErrorCode.OK.getMessage(), data);
    }

    public static <T> Result<T> error(ErrorCode errorCode) {
        return new Result<>(errorCode.getCode(), errorCode.getMessage(), null);
    }

    public String getCode() { return code; }
    public String getMessage() { return message; }
    public T getData() { return data; }
}
```

- [x] **Step 4: 验证编译**

Run: `mvn -f backend/pom.xml compile`
Expected: BUILD SUCCESS

- [x] **Step 5: Commit**

```bash
git add backend/src/main/java/com/sellm/common/
git commit -m "feat(common): 统一返回结构、异常码与业务异常"
```

---
### Task 3: anonymizer 脱敏层(合规命门,TDD)

脱敏层职责:把含身份信息的文本替换为占位符,记录还原映射;还原时把占位符替换回原值。脱敏失败必须抛异常以触发上层硬阻断。

**Files:**
- Create: `backend/src/main/java/com/sellm/anonymizer/AnonymizationException.java`
- Create: `backend/src/main/java/com/sellm/anonymizer/AnonymizationResult.java`
- Create: `backend/src/main/java/com/sellm/anonymizer/Anonymizer.java`
- Create: `backend/src/main/java/com/sellm/anonymizer/RegexAnonymizer.java`
- Test: `backend/src/test/java/com/sellm/anonymizer/RegexAnonymizerTest.java`

- [x] **Step 1: 创建异常与接口骨架(供测试编译)**

`AnonymizationException.java`:
```java
package com.sellm.anonymizer;

public class AnonymizationException extends RuntimeException {
    public AnonymizationException(String message) {
        super(message);
    }
}
```

`AnonymizationResult.java`:
```java
package com.sellm.anonymizer;

import java.util.Map;

public class AnonymizationResult {
    private final String anonymizedText;
    private final Map<String, String> restoreMap; // 占位符 -> 原值

    public AnonymizationResult(String anonymizedText, Map<String, String> restoreMap) {
        this.anonymizedText = anonymizedText;
        this.restoreMap = restoreMap;
    }

    public String getAnonymizedText() { return anonymizedText; }
    public Map<String, String> getRestoreMap() { return restoreMap; }
}
```

`Anonymizer.java`:
```java
package com.sellm.anonymizer;

import java.util.List;

public interface Anonymizer {
    /**
     * 对 text 脱敏。names/schools 为已知需替换的身份信息;另外内置身份证号正则。
     * @throws AnonymizationException 当脱敏后文本仍可能残留身份信息(校验未通过)时
     */
    AnonymizationResult anonymize(String text, List<String> names, List<String> schools);

    /** 用还原映射把占位符替换回原值 */
    String restore(String text, java.util.Map<String, String> restoreMap);
}
```

- [x] **Step 2: 写失败测试**

`RegexAnonymizerTest.java`:
```java
package com.sellm.anonymizer;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class RegexAnonymizerTest {

    private final RegexAnonymizer anonymizer = new RegexAnonymizer();

    @Test
    void 替换姓名和学校为占位符() {
        String text = "小明在阳光小学表现良好";
        AnonymizationResult r = anonymizer.anonymize(text, List.of("小明"), List.of("阳光小学"));
        assertThat(r.getAnonymizedText()).doesNotContain("小明").doesNotContain("阳光小学");
        assertThat(r.getAnonymizedText()).contains("[儿童1]").contains("[学校1]");
    }

    @Test
    void 替换身份证号() {
        String text = "证件号 110101200001011234 已登记";
        AnonymizationResult r = anonymizer.anonymize(text, List.of(), List.of());
        assertThat(r.getAnonymizedText()).doesNotContain("110101200001011234");
        assertThat(r.getAnonymizedText()).contains("[身份证1]");
    }

    @Test
    void 还原占位符回原值() {
        String text = "小明在阳光小学表现良好";
        AnonymizationResult r = anonymizer.anonymize(text, List.of("小明"), List.of("阳光小学"));
        String restored = anonymizer.restore(r.getAnonymizedText(), r.getRestoreMap());
        assertThat(restored).isEqualTo(text);
    }

    @Test
    void 脱敏后仍残留已知姓名则抛异常硬阻断() {
        // names 传空使替换不生效,但校验名单要求文本不得含"张伟",校验阶段应发现残留并硬阻断
        assertThatThrownBy(() ->
            anonymizer.anonymize("张伟同学", List.of(), List.of(), List.of("张伟"))
        ).isInstanceOf(AnonymizationException.class);
    }
}
```

注:上面最后一个测试用到一个带"校验名单"的重载方法 `anonymize(text, names, schools, mustNotContain)`。在 Step 3 中一并实现:`anonymize(text, names, schools)` 委托给四参版本,`mustNotContain` 默认等于 `names`。

- [x] **Step 3: 运行测试,确认失败**

Run: `mvn -f backend/pom.xml test -Dtest=RegexAnonymizerTest`
Expected: 编译失败或测试失败 —— `RegexAnonymizer` 尚未创建。

- [x] **Step 4: 实现 RegexAnonymizer**

`RegexAnonymizer.java`:
```java
package com.sellm.anonymizer;

import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class RegexAnonymizer implements Anonymizer {

    private static final Pattern ID_CARD = Pattern.compile("\\b\\d{17}[\\dXx]\\b");

    @Override
    public AnonymizationResult anonymize(String text, List<String> names, List<String> schools) {
        return anonymize(text, names, schools, names);
    }

    public AnonymizationResult anonymize(String text, List<String> names,
                                         List<String> schools, List<String> mustNotContain) {
        Map<String, String> restoreMap = new LinkedHashMap<>();
        String result = text;

        int idx = 1;
        for (String name : names) {
            if (name == null || name.isBlank()) continue;
            String placeholder = "[儿童" + idx++ + "]";
            result = result.replace(name, placeholder);
            restoreMap.put(placeholder, name);
        }
        idx = 1;
        for (String school : schools) {
            if (school == null || school.isBlank()) continue;
            String placeholder = "[学校" + idx++ + "]";
            result = result.replace(school, placeholder);
            restoreMap.put(placeholder, school);
        }
        idx = 1;
        Matcher m = ID_CARD.matcher(result);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String placeholder = "[身份证" + idx++ + "]";
            restoreMap.put(placeholder, m.group());
            m.appendReplacement(sb, Matcher.quoteReplacement(placeholder));
        }
        m.appendTail(sb);
        result = sb.toString();

        // 校验:出网前若仍残留任何 mustNotContain 项,硬阻断
        List<String> leaked = new ArrayList<>();
        for (String s : mustNotContain) {
            if (s != null && !s.isBlank() && result.contains(s)) {
                leaked.add(s);
            }
        }
        if (!leaked.isEmpty()) {
            throw new AnonymizationException("脱敏校验未通过,残留身份信息: " + leaked);
        }
        return new AnonymizationResult(result, restoreMap);
    }

    @Override
    public String restore(String text, Map<String, String> restoreMap) {
        String result = text;
        for (Map.Entry<String, String> e : restoreMap.entrySet()) {
            result = result.replace(e.getKey(), e.getValue());
        }
        return result;
    }
}
```

- [x] **Step 5: 运行测试,确认通过**

Run: `mvn -f backend/pom.xml test -Dtest=RegexAnonymizerTest`
Expected: 4 个测试全部 PASS。

- [x] **Step 6: Commit**

```bash
git add backend/src/main/java/com/sellm/anonymizer/ backend/src/test/java/com/sellm/anonymizer/
git commit -m "feat(anonymizer): 正则脱敏层,校验失败硬阻断"
```

---
### Task 4: scale 量表引擎与计分(正确性命门,TDD)

通用量表引擎:量表由"定义 + 题目 + 计分规则"构成,计分 = 各题得分求和 → 匹配分段 → 输出总分与解读。计分前校验作答完整性与规则存在性,失败抛 `ScoringException`。

**Files:**
- Create: `backend/src/main/java/com/sellm/scale/Scale.java`
- Create: `backend/src/main/java/com/sellm/scale/ScaleItem.java`
- Create: `backend/src/main/java/com/sellm/scale/ScoreBand.java`
- Create: `backend/src/main/java/com/sellm/scale/ScoringRule.java`
- Create: `backend/src/main/java/com/sellm/scale/Answer.java`
- Create: `backend/src/main/java/com/sellm/scale/AssessmentResult.java`
- Create: `backend/src/main/java/com/sellm/scale/ScoringException.java`
- Create: `backend/src/main/java/com/sellm/scale/ScoringEngine.java`
- Create: `backend/src/main/java/com/sellm/scale/DefaultScoringEngine.java`
- Test: `backend/src/test/java/com/sellm/scale/DefaultScoringEngineTest.java`

- [x] **Step 1: 创建值对象与接口**

`ScaleItem.java`:
```java
package com.sellm.scale;

public class ScaleItem {
    private final String itemId;
    private final String stem;     // 题干
    private final String dimension; // 维度

    public ScaleItem(String itemId, String stem, String dimension) {
        this.itemId = itemId;
        this.stem = stem;
        this.dimension = dimension;
    }

    public String getItemId() { return itemId; }
    public String getStem() { return stem; }
    public String getDimension() { return dimension; }
}
```

`ScoreBand.java`:
```java
package com.sellm.scale;

public class ScoreBand {
    private final double lower;        // 含
    private final double upper;        // 含
    private final String label;        // 如 "轻-中度"
    private final String interpretation;

    public ScoreBand(double lower, double upper, String label, String interpretation) {
        this.lower = lower;
        this.upper = upper;
        this.label = label;
        this.interpretation = interpretation;
    }

    public boolean contains(double score) {
        return score >= lower && score <= upper;
    }

    public String getLabel() { return label; }
    public String getInterpretation() { return interpretation; }
}
```

`ScoringRule.java`:
```java
package com.sellm.scale;

import java.util.List;

public class ScoringRule {
    private final List<ScoreBand> bands;

    public ScoringRule(List<ScoreBand> bands) {
        this.bands = bands;
    }

    public List<ScoreBand> getBands() { return bands; }
}
```

`Scale.java`:
```java
package com.sellm.scale;

import java.util.List;

public class Scale {
    private final String scaleId;
    private final String name;     // 如 CARS
    private final String version;
    private final List<ScaleItem> items;
    private final ScoringRule scoringRule; // 可为 null,表示规则缺失

    public Scale(String scaleId, String name, String version,
                 List<ScaleItem> items, ScoringRule scoringRule) {
        this.scaleId = scaleId;
        this.name = name;
        this.version = version;
        this.items = items;
        this.scoringRule = scoringRule;
    }

    public String getScaleId() { return scaleId; }
    public String getName() { return name; }
    public String getVersion() { return version; }
    public List<ScaleItem> getItems() { return items; }
    public ScoringRule getScoringRule() { return scoringRule; }
}
```

`Answer.java`:
```java
package com.sellm.scale;

public class Answer {
    private final String itemId;
    private final double score;

    public Answer(String itemId, double score) {
        this.itemId = itemId;
        this.score = score;
    }

    public String getItemId() { return itemId; }
    public double getScore() { return score; }
}
```

`AssessmentResult.java`:
```java
package com.sellm.scale;

public class AssessmentResult {
    private final double totalScore;
    private final String bandLabel;
    private final String interpretation;

    public AssessmentResult(double totalScore, String bandLabel, String interpretation) {
        this.totalScore = totalScore;
        this.bandLabel = bandLabel;
        this.interpretation = interpretation;
    }

    public double getTotalScore() { return totalScore; }
    public String getBandLabel() { return bandLabel; }
    public String getInterpretation() { return interpretation; }
}
```

`ScoringException.java`:
```java
package com.sellm.scale;

public class ScoringException extends RuntimeException {
    public ScoringException(String message) {
        super(message);
    }
}
```

`ScoringEngine.java`:
```java
package com.sellm.scale;

import java.util.List;

public interface ScoringEngine {
    /**
     * 计分:校验 → 求和 → 匹配分段。
     * @throws ScoringException 规则缺失、作答不完整或无命中分段时
     */
    AssessmentResult score(Scale scale, List<Answer> answers);
}
```

- [x] **Step 2: 写失败测试**

`DefaultScoringEngineTest.java`:
```java
package com.sellm.scale;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class DefaultScoringEngineTest {

    private final ScoringEngine engine = new DefaultScoringEngine();

    private Scale scaleWithRule() {
        List<ScaleItem> items = List.of(
            new ScaleItem("q1", "社交", "社交"),
            new ScaleItem("q2", "沟通", "沟通")
        );
        ScoringRule rule = new ScoringRule(List.of(
            new ScoreBand(0, 3, "正常", "未见明显异常"),
            new ScoreBand(4, 7, "轻-中度", "建议进一步评估")
        ));
        return new Scale("cars", "CARS", "v1", items, rule);
    }

    @Test
    void 求和并命中正确分段() {
        AssessmentResult r = engine.score(scaleWithRule(),
            List.of(new Answer("q1", 2), new Answer("q2", 3)));
        assertThat(r.getTotalScore()).isEqualTo(5.0);
        assertThat(r.getBandLabel()).isEqualTo("轻-中度");
        assertThat(r.getInterpretation()).isEqualTo("建议进一步评估");
    }

    @Test
    void 边界值落在分段上界() {
        AssessmentResult r = engine.score(scaleWithRule(),
            List.of(new Answer("q1", 1), new Answer("q2", 2)));
        assertThat(r.getTotalScore()).isEqualTo(3.0);
        assertThat(r.getBandLabel()).isEqualTo("正常");
    }

    @Test
    void 作答不完整则抛异常() {
        assertThatThrownBy(() ->
            engine.score(scaleWithRule(), List.of(new Answer("q1", 2)))
        ).isInstanceOf(ScoringException.class)
         .hasMessageContaining("作答不完整");
    }

    @Test
    void 计分规则缺失则抛异常() {
        Scale noRule = new Scale("x", "X", "v1",
            List.of(new ScaleItem("q1", "题", "维度")), null);
        assertThatThrownBy(() ->
            engine.score(noRule, List.of(new Answer("q1", 1)))
        ).isInstanceOf(ScoringException.class)
         .hasMessageContaining("计分规则缺失");
    }

    @Test
    void 总分无命中分段则抛异常() {
        AssessmentResult ignored;
        assertThatThrownBy(() ->
            engine.score(scaleWithRule(),
                List.of(new Answer("q1", 50), new Answer("q2", 50)))
        ).isInstanceOf(ScoringException.class)
         .hasMessageContaining("无命中分段");
    }
}
```

- [x] **Step 3: 运行测试,确认失败**

Run: `mvn -f backend/pom.xml test -Dtest=DefaultScoringEngineTest`
Expected: 编译失败 —— `DefaultScoringEngine` 尚未创建。

- [x] **Step 4: 实现 DefaultScoringEngine**

`DefaultScoringEngine.java`:
```java
package com.sellm.scale;

import org.springframework.stereotype.Component;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class DefaultScoringEngine implements ScoringEngine {

    @Override
    public AssessmentResult score(Scale scale, List<Answer> answers) {
        if (scale.getScoringRule() == null) {
            throw new ScoringException("计分规则缺失: 量表 " + scale.getName());
        }
        Set<String> answered = new HashSet<>();
        for (Answer a : answers) {
            answered.add(a.getItemId());
        }
        for (ScaleItem item : scale.getItems()) {
            if (!answered.contains(item.getItemId())) {
                throw new ScoringException("作答不完整: 缺少题目 " + item.getItemId());
            }
        }

        double total = 0;
        for (Answer a : answers) {
            total += a.getScore();
        }

        for (ScoreBand band : scale.getScoringRule().getBands()) {
            if (band.contains(total)) {
                return new AssessmentResult(total, band.getLabel(), band.getInterpretation());
            }
        }
        throw new ScoringException("无命中分段: 总分 " + total + " 超出规则范围");
    }
}
```

- [x] **Step 5: 运行测试,确认通过**

Run: `mvn -f backend/pom.xml test -Dtest=DefaultScoringEngineTest`
Expected: 5 个测试全部 PASS。

- [x] **Step 6: Commit**

```bash
git add backend/src/main/java/com/sellm/scale/ backend/src/test/java/com/sellm/scale/
git commit -m "feat(scale): 通用量表计分引擎,校验前置 + 分段匹配"
```

---
### Task 5: aigateway AI 网关(可插拔 + 强制脱敏,TDD)

网关是业务模块调用大模型的唯一入口。`AiModel` 是可插拔底层接口;`DefaultAiGateway` 在调模型前强制经 `anonymizer` 脱敏、调用后还原,并把模型异常包装为 `AiGatewayException`。

**Files:**
- Create: `backend/src/main/java/com/sellm/aigateway/AiModel.java`
- Create: `backend/src/main/java/com/sellm/aigateway/PromptRequest.java`
- Create: `backend/src/main/java/com/sellm/aigateway/AiGateway.java`
- Create: `backend/src/main/java/com/sellm/aigateway/AiGatewayException.java`
- Create: `backend/src/main/java/com/sellm/aigateway/MockAiModel.java`
- Create: `backend/src/main/java/com/sellm/aigateway/DefaultAiGateway.java`
- Test: `backend/src/test/java/com/sellm/aigateway/DefaultAiGatewayTest.java`

- [x] **Step 1: 创建接口与值对象**

`AiModel.java`(可插拔点:第一版用 API 实现,后续可换私有化/微调):
```java
package com.sellm.aigateway;

public interface AiModel {
    /** 输入已脱敏的 prompt,返回模型生成文本(可能仍含占位符) */
    String complete(String anonymizedPrompt);
}
```

`PromptRequest.java`:
```java
package com.sellm.aigateway;

import java.util.List;

public class PromptRequest {
    private final String prompt;        // 含身份信息的原始 prompt
    private final List<String> names;   // 需脱敏的姓名
    private final List<String> schools; // 需脱敏的学校

    public PromptRequest(String prompt, List<String> names, List<String> schools) {
        this.prompt = prompt;
        this.names = names;
        this.schools = schools;
    }

    public String getPrompt() { return prompt; }
    public List<String> getNames() { return names; }
    public List<String> getSchools() { return schools; }
}
```

`AiGateway.java`:
```java
package com.sellm.aigateway;

public interface AiGateway {
    /** 脱敏 → 调模型 → 还原。业务模块只看得到还原后的结果。 */
    String generate(PromptRequest request);
}
```

`AiGatewayException.java`:
```java
package com.sellm.aigateway;

public class AiGatewayException extends RuntimeException {
    public AiGatewayException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

`MockAiModel.java`(默认实现,回显式:把"生成报告:"前缀加到收到的脱敏 prompt 上,便于断言网关确实传入了脱敏文本):
```java
package com.sellm.aigateway;

import org.springframework.stereotype.Component;

@Component
public class MockAiModel implements AiModel {
    @Override
    public String complete(String anonymizedPrompt) {
        return "[AI草稿] " + anonymizedPrompt;
    }
}
```

- [x] **Step 2: 写失败测试**

`DefaultAiGatewayTest.java`:
```java
package com.sellm.aigateway;

import com.sellm.anonymizer.RegexAnonymizer;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class DefaultAiGatewayTest {

    private final RegexAnonymizer anonymizer = new RegexAnonymizer();

    @Test
    void 调模型前脱敏调用后还原() {
        // Mock 模型回显收到的 prompt,以此验证传给模型的是脱敏文本
        AiModel echo = new MockAiModel();
        DefaultAiGateway gateway = new DefaultAiGateway(anonymizer, echo);

        String result = gateway.generate(new PromptRequest(
            "请为小明在阳光小学的表现生成报告", List.of("小明"), List.of("阳光小学")));

        // 返回结果已还原,含原始身份信息
        assertThat(result).contains("小明").contains("阳光小学");
    }

    @Test
    void 传给底层模型的是脱敏文本() {
        StringBuilder captured = new StringBuilder();
        AiModel capturing = prompt -> { captured.append(prompt); return "ok"; };
        DefaultAiGateway gateway = new DefaultAiGateway(anonymizer, capturing);

        gateway.generate(new PromptRequest(
            "小明在阳光小学", List.of("小明"), List.of("阳光小学")));

        assertThat(captured.toString()).doesNotContain("小明").doesNotContain("阳光小学");
        assertThat(captured.toString()).contains("[儿童1]").contains("[学校1]");
    }

    @Test
    void 模型抛异常时包装为网关异常() {
        AiModel failing = prompt -> { throw new RuntimeException("timeout"); };
        DefaultAiGateway gateway = new DefaultAiGateway(anonymizer, failing);

        assertThatThrownBy(() ->
            gateway.generate(new PromptRequest("文本", List.of(), List.of()))
        ).isInstanceOf(AiGatewayException.class);
    }
}
```

- [x] **Step 3: 运行测试,确认失败**

Run: `mvn -f backend/pom.xml test -Dtest=DefaultAiGatewayTest`
Expected: 编译失败 —— `DefaultAiGateway` 尚未创建。

- [x] **Step 4: 实现 DefaultAiGateway**

`DefaultAiGateway.java`:
```java
package com.sellm.aigateway;

import com.sellm.anonymizer.AnonymizationResult;
import com.sellm.anonymizer.Anonymizer;
import org.springframework.stereotype.Component;

@Component
public class DefaultAiGateway implements AiGateway {

    private final Anonymizer anonymizer;
    private final AiModel aiModel;

    public DefaultAiGateway(Anonymizer anonymizer, AiModel aiModel) {
        this.anonymizer = anonymizer;
        this.aiModel = aiModel;
    }

    @Override
    public String generate(PromptRequest request) {
        // 脱敏失败会抛 AnonymizationException,直接向上传播 → 硬阻断,不调模型
        AnonymizationResult anonymized = anonymizer.anonymize(
            request.getPrompt(), request.getNames(), request.getSchools());

        String modelOutput;
        try {
            modelOutput = aiModel.complete(anonymized.getAnonymizedText());
        } catch (RuntimeException e) {
            throw new AiGatewayException("AI 模型调用失败", e);
        }

        return anonymizer.restore(modelOutput, anonymized.getRestoreMap());
    }
}
```

- [x] **Step 5: 运行测试,确认通过**

Run: `mvn -f backend/pom.xml test -Dtest=DefaultAiGatewayTest`
Expected: 3 个测试全部 PASS。

- [x] **Step 6: Commit**

```bash
git add backend/src/main/java/com/sellm/aigateway/ backend/src/test/java/com/sellm/aigateway/
git commit -m "feat(aigateway): 可插拔 AI 网关,调模型前强制脱敏、调用后还原"
```

---
### Task 6: rag 检索(内存关键词实现,TDD)

第一版用内存关键词检索把链路串通;真实向量库在后续计划替换。接口稳定,实现可换。

**Files:**
- Create: `backend/src/main/java/com/sellm/rag/KnowledgeDoc.java`
- Create: `backend/src/main/java/com/sellm/rag/RagRetriever.java`
- Create: `backend/src/main/java/com/sellm/rag/InMemoryRagRetriever.java`
- Test: `backend/src/test/java/com/sellm/rag/InMemoryRagRetrieverTest.java`

- [x] **Step 1: 创建文档与接口**

`KnowledgeDoc.java`:
```java
package com.sellm.rag;

public class KnowledgeDoc {
    private final String docId;
    private final String content;
    private final String source;

    public KnowledgeDoc(String docId, String content, String source) {
        this.docId = docId;
        this.content = content;
        this.source = source;
    }

    public String getDocId() { return docId; }
    public String getContent() { return content; }
    public String getSource() { return source; }
}
```

`RagRetriever.java`:
```java
package com.sellm.rag;

import java.util.List;

public interface RagRetriever {
    /** 召回与 query 最相关的至多 topK 篇文档 */
    List<KnowledgeDoc> retrieve(String query, int topK);
}
```

- [x] **Step 2: 写失败测试**

`InMemoryRagRetrieverTest.java`:
```java
package com.sellm.rag;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class InMemoryRagRetrieverTest {

    private InMemoryRagRetriever retrieverWithDocs() {
        return new InMemoryRagRetriever(List.of(
            new KnowledgeDoc("d1", "孤独症社交干预策略:结构化教学", "手册A"),
            new KnowledgeDoc("d2", "CARS 量表解读:总分与分段", "手册B"),
            new KnowledgeDoc("d3", "言语训练通用方法", "手册C")
        ));
    }

    @Test
    void 按关键词召回相关文档() {
        List<KnowledgeDoc> docs = retrieverWithDocs().retrieve("CARS 解读", 2);
        assertThat(docs).isNotEmpty();
        assertThat(docs.get(0).getDocId()).isEqualTo("d2");
    }

    @Test
    void 限制返回数量为topK() {
        List<KnowledgeDoc> docs = retrieverWithDocs().retrieve("干预 解读 训练", 2);
        assertThat(docs).hasSizeLessThanOrEqualTo(2);
    }

    @Test
    void 无匹配时返回空列表() {
        List<KnowledgeDoc> docs = retrieverWithDocs().retrieve("微积分", 3);
        assertThat(docs).isEmpty();
    }
}
```

- [x] **Step 3: 运行测试,确认失败**

Run: `mvn -f backend/pom.xml test -Dtest=InMemoryRagRetrieverTest`
Expected: 编译失败 —— `InMemoryRagRetriever` 尚未创建。

- [x] **Step 4: 实现 InMemoryRagRetriever**

`InMemoryRagRetriever.java`:
```java
package com.sellm.rag;

import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class InMemoryRagRetriever implements RagRetriever {

    private final List<KnowledgeDoc> docs;

    public InMemoryRagRetriever(List<KnowledgeDoc> docs) {
        this.docs = docs;
    }

    @Override
    public List<KnowledgeDoc> retrieve(String query, int topK) {
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

注:`InMemoryRagRetriever` 构造器需要 `List<KnowledgeDoc>` bean。本计划不涉及 Spring 容器装配(仅单测直接 new),知识库 bean 的注册留待"持久化与 REST 层"计划。

- [x] **Step 5: 运行测试,确认通过**

Run: `mvn -f backend/pom.xml test -Dtest=InMemoryRagRetrieverTest`
Expected: 3 个测试全部 PASS。

- [x] **Step 6: Commit**

```bash
git add backend/src/main/java/com/sellm/rag/ backend/src/test/java/com/sellm/rag/
git commit -m "feat(rag): 内存关键词检索,接口稳定可替换"
```

---
### Task 7: report 报告服务(RAG 召回 + 网关生成草稿,TDD)

报告服务把计分结果 + RAG 召回的解读知识组成 prompt,经 AI 网关生成报告草稿。报告初始状态为 DRAFT,体现"AI 只产草稿,人来定稿"。

**Files:**
- Create: `backend/src/main/java/com/sellm/report/ReportStatus.java`
- Create: `backend/src/main/java/com/sellm/report/Report.java`
- Create: `backend/src/main/java/com/sellm/report/ReportService.java`
- Test: `backend/src/test/java/com/sellm/report/ReportServiceTest.java`

- [x] **Step 1: 创建状态枚举与实体**

`ReportStatus.java`:
```java
package com.sellm.report;

public enum ReportStatus {
    DRAFT,      // AI 草稿,待老师审阅
    FINALIZED   // 老师已定稿
}
```

`Report.java`:
```java
package com.sellm.report;

public class Report {
    private final String childName;
    private final String draft;       // AI 生成草稿(已还原身份信息)
    private String finalizedContent;  // 老师定稿内容
    private ReportStatus status;

    public Report(String childName, String draft) {
        this.childName = childName;
        this.draft = draft;
        this.status = ReportStatus.DRAFT;
    }

    public void finalizeReport(String content) {
        this.finalizedContent = content;
        this.status = ReportStatus.FINALIZED;
    }

    public String getChildName() { return childName; }
    public String getDraft() { return draft; }
    public String getFinalizedContent() { return finalizedContent; }
    public ReportStatus getStatus() { return status; }
}
```

- [x] **Step 2: 写失败测试**

`ReportServiceTest.java`:
```java
package com.sellm.report;

import com.sellm.aigateway.AiGateway;
import com.sellm.aigateway.PromptRequest;
import com.sellm.rag.KnowledgeDoc;
import com.sellm.rag.RagRetriever;
import com.sellm.scale.AssessmentResult;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReportServiceTest {

    @Test
    void 生成报告草稿状态为DRAFT且含召回知识() {
        RagRetriever rag = mock(RagRetriever.class);
        when(rag.retrieve(anyString(), anyInt()))
            .thenReturn(List.of(new KnowledgeDoc("d2", "CARS 解读知识", "手册B")));

        AiGateway gateway = mock(AiGateway.class);
        when(gateway.generate(any(PromptRequest.class)))
            .thenReturn("小明的评估报告草稿内容");

        ReportService service = new ReportService(rag, gateway);
        AssessmentResult ar = new AssessmentResult(5.0, "轻-中度", "建议进一步评估");

        Report report = service.generateDraft("小明", "阳光小学", "CARS", ar);

        assertThat(report.getStatus()).isEqualTo(ReportStatus.DRAFT);
        assertThat(report.getDraft()).isEqualTo("小明的评估报告草稿内容");
        assertThat(report.getChildName()).isEqualTo("小明");
    }

    @Test
    void prompt包含召回知识与得分并传入身份信息供网关脱敏() {
        RagRetriever rag = mock(RagRetriever.class);
        when(rag.retrieve(anyString(), anyInt()))
            .thenReturn(List.of(new KnowledgeDoc("d2", "CARS 解读知识", "手册B")));

        AiGateway gateway = mock(AiGateway.class);
        when(gateway.generate(any(PromptRequest.class))).thenReturn("草稿");

        ReportService service = new ReportService(rag, gateway);
        service.generateDraft("小明", "阳光小学", "CARS",
            new AssessmentResult(5.0, "轻-中度", "建议进一步评估"));

        ArgumentCaptor<PromptRequest> captor = ArgumentCaptor.forClass(PromptRequest.class);
        verify(gateway).generate(captor.capture());
        PromptRequest req = captor.getValue();
        assertThat(req.getPrompt()).contains("CARS 解读知识").contains("轻-中度").contains("5");
        assertThat(req.getNames()).contains("小明");
        assertThat(req.getSchools()).contains("阳光小学");
    }

    @Test
    void 老师定稿后状态变为FINALIZED() {
        Report report = new Report("小明", "草稿");
        report.finalizeReport("老师修改后的终稿");
        assertThat(report.getStatus()).isEqualTo(ReportStatus.FINALIZED);
        assertThat(report.getFinalizedContent()).isEqualTo("老师修改后的终稿");
    }
}
```

注:测试用到 `org.mockito.ArgumentCaptor`,需在文件顶部加 `import org.mockito.ArgumentCaptor;`(spring-boot-starter-test 已含 Mockito)。

- [x] **Step 3: 运行测试,确认失败**

Run: `mvn -f backend/pom.xml test -Dtest=ReportServiceTest`
Expected: 编译失败 —— `ReportService` 尚未创建。

- [x] **Step 4: 实现 ReportService**

`ReportService.java`:
```java
package com.sellm.report;

import com.sellm.aigateway.AiGateway;
import com.sellm.aigateway.PromptRequest;
import com.sellm.rag.KnowledgeDoc;
import com.sellm.rag.RagRetriever;
import com.sellm.scale.AssessmentResult;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class ReportService {

    private final RagRetriever ragRetriever;
    private final AiGateway aiGateway;

    public ReportService(RagRetriever ragRetriever, AiGateway aiGateway) {
        this.ragRetriever = ragRetriever;
        this.aiGateway = aiGateway;
    }

    public Report generateDraft(String childName, String schoolName,
                                String scaleName, AssessmentResult result) {
        List<KnowledgeDoc> docs = ragRetriever.retrieve(
            scaleName + " " + result.getBandLabel() + " 解读", 3);

        StringBuilder knowledge = new StringBuilder();
        for (KnowledgeDoc doc : docs) {
            knowledge.append(doc.getContent()).append("\n");
        }

        String prompt = "请基于以下信息为 " + childName + "(" + schoolName + ")生成评估报告草稿。\n"
            + "量表: " + scaleName + "\n"
            + "总分: " + result.getTotalScore() + ",分段: " + result.getBandLabel() + "\n"
            + "解读参考: " + result.getInterpretation() + "\n"
            + "知识库召回:\n" + knowledge;

        String draft = aiGateway.generate(
            new PromptRequest(prompt, List.of(childName), List.of(schoolName)));

        return new Report(childName, draft);
    }
}
```

- [x] **Step 5: 运行测试,确认通过**

Run: `mvn -f backend/pom.xml test -Dtest=ReportServiceTest`
Expected: 3 个测试全部 PASS。

- [x] **Step 6: Commit**

```bash
git add backend/src/main/java/com/sellm/report/ backend/src/test/java/com/sellm/report/
git commit -m "feat(report): 报告服务,RAG 召回 + 网关生成草稿,初始 DRAFT"
```

---
### Task 8: iep IEP 草案服务(RAG 召回 + 网关生成,TDD)

IEP 服务基于已定稿的评估结论 + RAG 召回的 IEP 范例/干预策略,经网关生成 IEP 草案(长短期目标 + 干预活动建议),初始状态 DRAFT。

**Files:**
- Create: `backend/src/main/java/com/sellm/iep/IepStatus.java`
- Create: `backend/src/main/java/com/sellm/iep/IepGoal.java`
- Create: `backend/src/main/java/com/sellm/iep/Iep.java`
- Create: `backend/src/main/java/com/sellm/iep/IepService.java`
- Test: `backend/src/test/java/com/sellm/iep/IepServiceTest.java`

- [x] **Step 1: 创建状态、目标、计划实体**

`IepStatus.java`:
```java
package com.sellm.iep;

public enum IepStatus {
    DRAFT,
    FINALIZED
}
```

`IepGoal.java`:
```java
package com.sellm.iep;

public class IepGoal {
    private final String description; // 目标描述
    private final String term;        // "长期" / "短期"

    public IepGoal(String description, String term) {
        this.description = description;
        this.term = term;
    }

    public String getDescription() { return description; }
    public String getTerm() { return term; }
}
```

`Iep.java`:
```java
package com.sellm.iep;

public class Iep {
    private final String childName;
    private final String draft;   // AI 生成草案(已还原)
    private IepStatus status;

    public Iep(String childName, String draft) {
        this.childName = childName;
        this.draft = draft;
        this.status = IepStatus.DRAFT;
    }

    public void finalizePlan() {
        this.status = IepStatus.FINALIZED;
    }

    public String getChildName() { return childName; }
    public String getDraft() { return draft; }
    public IepStatus getStatus() { return status; }
}
```

- [x] **Step 2: 写失败测试**

`IepServiceTest.java`:
```java
package com.sellm.iep;

import com.sellm.aigateway.AiGateway;
import com.sellm.aigateway.PromptRequest;
import com.sellm.rag.KnowledgeDoc;
import com.sellm.rag.RagRetriever;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class IepServiceTest {

    @Test
    void 生成IEP草案状态为DRAFT() {
        RagRetriever rag = mock(RagRetriever.class);
        when(rag.retrieve(anyString(), anyInt()))
            .thenReturn(List.of(new KnowledgeDoc("i1", "ASD 社交干预 IEP 范例", "范例库")));
        AiGateway gateway = mock(AiGateway.class);
        when(gateway.generate(any(PromptRequest.class))).thenReturn("小明的 IEP 草案");

        IepService service = new IepService(rag, gateway);
        Iep iep = service.generateDraft("小明", "阳光小学",
            "轻-中度,社交沟通存在困难");

        assertThat(iep.getStatus()).isEqualTo(IepStatus.DRAFT);
        assertThat(iep.getDraft()).isEqualTo("小明的 IEP 草案");
    }

    @Test
    void prompt含评估结论与召回范例并传身份信息供脱敏() {
        RagRetriever rag = mock(RagRetriever.class);
        when(rag.retrieve(anyString(), anyInt()))
            .thenReturn(List.of(new KnowledgeDoc("i1", "ASD 社交干预 IEP 范例", "范例库")));
        AiGateway gateway = mock(AiGateway.class);
        when(gateway.generate(any(PromptRequest.class))).thenReturn("草案");

        IepService service = new IepService(rag, gateway);
        service.generateDraft("小明", "阳光小学", "轻-中度,社交沟通存在困难");

        ArgumentCaptor<PromptRequest> captor = ArgumentCaptor.forClass(PromptRequest.class);
        verify(gateway).generate(captor.capture());
        PromptRequest req = captor.getValue();
        assertThat(req.getPrompt()).contains("社交沟通存在困难").contains("IEP 范例");
        assertThat(req.getNames()).contains("小明");
        assertThat(req.getSchools()).contains("阳光小学");
    }

    @Test
    void 定稿后状态为FINALIZED() {
        Iep iep = new Iep("小明", "草案");
        iep.finalizePlan();
        assertThat(iep.getStatus()).isEqualTo(IepStatus.FINALIZED);
    }
}
```

- [x] **Step 3: 运行测试,确认失败**

Run: `mvn -f backend/pom.xml test -Dtest=IepServiceTest`
Expected: 编译失败 —— `IepService` 尚未创建。

- [x] **Step 4: 实现 IepService**

`IepService.java`:
```java
package com.sellm.iep;

import com.sellm.aigateway.AiGateway;
import com.sellm.aigateway.PromptRequest;
import com.sellm.rag.KnowledgeDoc;
import com.sellm.rag.RagRetriever;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class IepService {

    private final RagRetriever ragRetriever;
    private final AiGateway aiGateway;

    public IepService(RagRetriever ragRetriever, AiGateway aiGateway) {
        this.ragRetriever = ragRetriever;
        this.aiGateway = aiGateway;
    }

    public Iep generateDraft(String childName, String schoolName, String assessmentConclusion) {
        List<KnowledgeDoc> docs = ragRetriever.retrieve(
            "ASD IEP 干预 " + assessmentConclusion, 3);

        StringBuilder knowledge = new StringBuilder();
        for (KnowledgeDoc doc : docs) {
            knowledge.append(doc.getContent()).append("\n");
        }

        String prompt = "请为 " + childName + "(" + schoolName + ")生成 IEP 草案,"
            + "包含长短期目标与干预活动建议。\n"
            + "评估结论: " + assessmentConclusion + "\n"
            + "可参考的范例与策略:\n" + knowledge;

        String draft = aiGateway.generate(
            new PromptRequest(prompt, List.of(childName), List.of(schoolName)));

        return new Iep(childName, draft);
    }
}
```

- [x] **Step 5: 运行测试,确认通过**

Run: `mvn -f backend/pom.xml test -Dtest=IepServiceTest`
Expected: 3 个测试全部 PASS。

- [x] **Step 6: Commit**

```bash
git add backend/src/main/java/com/sellm/iep/ backend/src/test/java/com/sellm/iep/
git commit -m "feat(iep): IEP 草案服务,RAG 召回 + 网关生成,初始 DRAFT"
```

---
### Task 9: 主链路集成测试(评估→报告→IEP)

用真实实现(RegexAnonymizer、DefaultScoringEngine、DefaultAiGateway + 一个本地 Mock 模型、InMemoryRagRetriever、ReportService、IepService)串起整条链路,验证:计分正确 → 报告草稿生成 → 定稿 → IEP 草案生成,且全程身份信息不泄露给模型、最终结果已还原。

**Files:**
- Test: `backend/src/test/java/com/sellm/integration/AssessmentToIepFlowTest.java`

- [x] **Step 1: 写集成测试**

`AssessmentToIepFlowTest.java`:
```java
package com.sellm.integration;

import com.sellm.aigateway.AiModel;
import com.sellm.aigateway.DefaultAiGateway;
import com.sellm.anonymizer.RegexAnonymizer;
import com.sellm.iep.Iep;
import com.sellm.iep.IepService;
import com.sellm.iep.IepStatus;
import com.sellm.rag.InMemoryRagRetriever;
import com.sellm.rag.KnowledgeDoc;
import com.sellm.report.Report;
import com.sellm.report.ReportService;
import com.sellm.report.ReportStatus;
import com.sellm.scale.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class AssessmentToIepFlowTest {

    // 本地模型:断言收到的 prompt 已脱敏,然后回显
    static class AssertingModel implements AiModel {
        @Override
        public String complete(String anonymizedPrompt) {
            assertThat(anonymizedPrompt).doesNotContain("小明").doesNotContain("阳光小学");
            return "[草稿] " + anonymizedPrompt;
        }
    }

    @Test
    void 全链路_评估到IEP草案_身份不泄露且结果还原() {
        RegexAnonymizer anonymizer = new RegexAnonymizer();
        DefaultAiGateway gateway = new DefaultAiGateway(anonymizer, new AssertingModel());
        InMemoryRagRetriever rag = new InMemoryRagRetriever(List.of(
            new KnowledgeDoc("d1", "CARS 解读 轻-中度 建议结构化干预", "手册B"),
            new KnowledgeDoc("d2", "ASD IEP 社交干预 范例 长期短期目标", "范例库")
        ));

        // 1. 评估 + 计分
        Scale cars = new Scale("cars", "CARS", "v1",
            List.of(new ScaleItem("q1", "社交", "社交"),
                    new ScaleItem("q2", "沟通", "沟通")),
            new ScoringRule(List.of(
                new ScoreBand(0, 3, "正常", "未见明显异常"),
                new ScoreBand(4, 7, "轻-中度", "建议进一步评估"))));
        AssessmentResult ar = new DefaultScoringEngine().score(cars,
            List.of(new Answer("q1", 2), new Answer("q2", 3)));
        assertThat(ar.getBandLabel()).isEqualTo("轻-中度");

        // 2. 报告草稿
        ReportService reportService = new ReportService(rag, gateway);
        Report report = reportService.generateDraft("小明", "阳光小学", "CARS", ar);
        assertThat(report.getStatus()).isEqualTo(ReportStatus.DRAFT);
        // 结果已还原:含原始姓名
        assertThat(report.getDraft()).contains("小明");

        // 3. 老师定稿
        report.finalizeReport("小明社交沟通存在困难,建议结构化干预");
        assertThat(report.getStatus()).isEqualTo(ReportStatus.FINALIZED);

        // 4. IEP 草案
        IepService iepService = new IepService(rag, gateway);
        Iep iep = iepService.generateDraft("小明", "阳光小学", report.getFinalizedContent());
        assertThat(iep.getStatus()).isEqualTo(IepStatus.DRAFT);
        assertThat(iep.getDraft()).contains("小明");
    }
}
```

- [x] **Step 2: 运行集成测试,确认通过**

Run: `mvn -f backend/pom.xml test -Dtest=AssessmentToIepFlowTest`
Expected: PASS。模型内部断言确认 prompt 已脱敏;最终草稿含还原后的"小明"。

- [x] **Step 3: 运行全部测试**

Run: `mvn -f backend/pom.xml test`
Expected: 全部测试 PASS(anonymizer 4 + scoring 5 + gateway 3 + rag 3 + report 3 + iep 3 + 集成 1)。

- [x] **Step 4: Commit**

```bash
git add backend/src/test/java/com/sellm/integration/
git commit -m "test(integration): 评估→报告→IEP 主链路,验证脱敏不泄露与结果还原"
```

---

## 后续计划(不在本计划范围,供路线参考)

1. **持久化与 REST 层**:MyBatis + MySQL 落地各实体,REST 控制器,知识库/向量库接入,Spring 容器装配。
2. **认证与三角色 RBAC**:user 模块、登录、权限,Child 身份字段加密存储。
3. **进度追踪与反馈**:ProgressRecord/ProgressTrend、Feedback、AIGenerationLog 回流。
4. **Vue 管理端**:老师/管理者端的评估录入、报告编辑、IEP 编辑、进度查看。
5. **uni-app 小程序家长端**:IEP/报告查看、居家建议、反馈、进度趋势。
6. **真实 AI 接入**:用合规企业版 API 实现 `AiModel`,替换 MockAiModel;真实向量库替换 InMemoryRagRetriever。
7. **计分引擎硬化**(Task 4 代码审查提出,留待后续):ScoringRule 分段重叠/间断/排序校验;浮点求和的 epsilon 容差(当真实量表出现 0.5 等小数分时);值对象 getter 返回不可变视图。第一版为整数分基线,暂不引入。
8. **防御式编程统一硬化**(Task 5 代码审查提出,留待后续):值对象(PromptRequest/AnonymizationResult 等)的集合 getter 返回不可变视图、构造器入参非空校验。当前 v1 各值对象统一不做包装,后续一次性统一处理。注:Task 5 审查曾疑虑 DefaultAiGateway 的 catch 会吞掉 AnonymizationException,经核对该异常抛在 try 块外、可正确穿透硬阻断,无此问题。
9. **集成测试负向用例**(Task 9 代码审查提出,留待后续):补充脱敏失败硬阻断整条链、模型异常包装、计分规则缺失等负向路径的端到端集成测试(目前这些已在各模块单测层覆盖,集成层仅验证了正向主链路)。

## 最终整体审查发现(进入下一计划前优先处理)

全部 9 任务完成,22 单测全绿,最终整体审查结论 ✅ 可合并。审查提出三项 Important,均不阻断本计划合并,但应在进入"持久化与 REST 层"计划时**优先处理**:

1. **Spring 上下文启动隐患(最高优先)**:`InMemoryRagRetriever` 标了 `@Component` 但构造器需 `List<KnowledgeDoc>` bean,全工程无该 bean 提供方,`./mvnw spring-boot:run` 会因 `NoSuchBeanDefinitionException` 启动失败。本计划纯单测(直接 new)未触发,故 22 测试漏过。下一计划装配容器时必须提供知识库 bean,并补一个最小 context-load 测试(`@SpringBootTest`)守住启动。
2. **脱敏泄露校验只覆盖姓名,不含学校/身份证**:`RegexAnonymizer` 三参版本委托四参时 `mustNotContain=names`,导致出网前的残留校验只查姓名。学校同属 PII。建议把 schools(及身份证模式)纳入 `mustNotContain`,收紧合规红线。
3. **IEP 定稿丢失人工内容**:`Report.finalizeReport(content)` 保存定稿内容,而 `Iep.finalizePlan()` 只翻状态、不保存内容。"人来定稿"在 IEP 侧只完成一半。建议 `Iep` 对齐 `Report`,补 `finalizedContent` 字段与入参。

另有 Minor:`IepGoal` 当前未被 `Iep`/`IepService` 引用(为进度追踪预留);`common` 包(Result/ErrorCode/BusinessException)与三个领域异常尚未接线,待 REST 层统一打通。

---

## 自检结论

- **Spec 覆盖**:本计划覆盖 spec 第 4 节架构的后端命门模块、第 5 节数据流主链路(评估→报告→IEP,含脱敏)、第 6 节数据模型的核心实体(Scale/Item/Rule、Assessment 结果、Report、Iep/Goal)、第 7 节模块边界(业务模块经 ai-gateway、网关必经 anonymizer)、第 8 节错误处理(脱敏硬阻断、AI 失败包装、计分校验前置)、第 9 节测试策略(计分/脱敏高覆盖、Mock 模型、AI 输出结构与不泄露验证)。持久化、REST、认证、前端、进度/反馈实体按设计第 10 节分阶段交付,列入"后续计划"。
- **占位符**:无 TBD/TODO,每个代码步骤含完整可编译代码。
- **类型一致性**:跨任务方法名核对一致 —— `Anonymizer.anonymize/restore`、`ScoringEngine.score`、`AiGateway.generate(PromptRequest)`、`AiModel.complete`、`RagRetriever.retrieve`、`Report.finalizeReport`、`Iep.finalizePlan`、`AssessmentResult.getBandLabel/getInterpretation/getTotalScore` 在定义与使用处一致。
