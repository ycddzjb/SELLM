# P0 平台地基实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将现有 SELLM 单体后端转化为 Maven 多模块微服务架构,搭建 API 网关 + Nacos + RabbitMQ + docker-compose 基础设施,初始化四个新 Agent 服务脚手架 + Python 智能层 + uni-app 小程序,确保现有 237 项测试仍全绿、前端 build 成功。

**Architecture:** Maven 父子多模块(sellm-parent → sellm-gateway / sellm-common / sellm-agent-assessment / sellm-agent-teaching / sellm-agent-research / sellm-agent-aids / sellm-agent-qa / sellm-ai-smart-layer(Python)/ miniprogram(uni-app))。网关 Spring Cloud Gateway 统一入口;各 Agent 注册 Nacos;RabbitMQ 事件总线;docker-compose 编排外部依赖。现有评估干预代码**整体平移**到 sellm-agent-assessment 模块,不改业务逻辑。

**Tech Stack:** Java 17 / Spring Boot 3.2.5 / Spring Cloud 2023.0.x / Spring Cloud Gateway / Spring Cloud Alibaba 2023.0.x (Nacos) / RabbitMQ / Maven 多模块 / Python 3.11 / FastAPI / LangGraph / uni-app(Vue3)

## Global Constraints

- Java 17,Spring Boot 3.2.5(`spring-cloud-dependencies 2023.0.1`, `spring-cloud-alibaba 2023.0.1.0`)
- 不删除/修改现有测试用例;不绕过认证授权机制
- 每次变更后后端 `./mvnw test` 全绿 + 前端 `npm run build` 成功
- PII/脱敏/AI 三条红线不触碰(P0 仅搭架,不改业务)
- 新增依赖锁定版本;dev profile 继续 H2 文件库(不依赖外部 MySQL/Nacos/RabbitMQ 跑测试)
- 所有配置走 `${ENV:default}` 环境变量模式
- 代码/配置变更后追加 `.claude/CLAUDE_CHANGES.md`

---

### Task 1: Maven 多模块父 POM + sellm-common 模块

**Files:**
- Create: `pom.xml` (根父 POM,替代现有 backend/pom.xml 为子模块入口)
- Create: `sellm-common/pom.xml`
- Move (logical): 现有 `backend/src/main/java/com/sellm/common/` → `sellm-common/src/main/java/com/sellm/common/`
- Move (logical): 现有 `backend/src/main/java/com/sellm/security/` → `sellm-common/src/main/java/com/sellm/security/`

**Interfaces:**
- Produces: `sellm-common` jar,供所有 Agent 模块依赖;包含 `Result<T>` 信封、`FieldCipher`、`AccessGuard`、JWT 过滤器、`SecurityConfig` 基类

> **重要设计决策**:P0 阶段**不做物理文件迁移**。Maven 多模块重组对现有 237 项测试和全部 import 路径影响巨大、风险高。采用**渐进策略**:
> - 根目录新增 Maven 父 POM(reactor 聚合),将 `backend/` 作为**第一个子模块 `sellm-agent-assessment`** 原封不动纳入(仅改其 pom.xml 加 parent 声明)。
> - `sellm-common` 先创建为**空壳模块**(仅 pom + package-info),代码实际仍在 assessment 模块内;后续阶段逐步抽离。
> - 四个新 Agent + 网关为**独立新空壳模块**,依赖 common(空壳),有最小 SpringBoot main + health endpoint。
> - 这确保现有测试零影响;后续 P1 再做真正的代码抽离(抽 common → 让 assessment 依赖 common → 测试仍绿)。

- [ ] **Step 1: 创建根父 POM**

在 worktree 根目录(即 `dev_workspace/`)创建 `pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.sellm</groupId>
    <artifactId>sellm-parent</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <packaging>pom</packaging>
    <name>sellm-parent</name>
    <description>特殊教育垂直大模型平台 — Maven Reactor Parent</description>

    <properties>
        <java.version>17</java.version>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <spring-boot.version>3.2.5</spring-boot.version>
        <spring-cloud.version>2023.0.1</spring-cloud.version>
        <spring-cloud-alibaba.version>2023.0.1.0</spring-cloud-alibaba.version>
        <mybatis-starter.version>3.0.3</mybatis-starter.version>
        <jjwt.version>0.12.6</jjwt.version>
        <minio.version>8.5.7</minio.version>
        <openhtmltopdf.version>1.0.10</openhtmltopdf.version>
    </properties>

    <modules>
        <module>backend</module>
        <module>sellm-common</module>
        <module>sellm-gateway</module>
        <module>sellm-agent-teaching</module>
        <module>sellm-agent-research</module>
        <module>sellm-agent-aids</module>
        <module>sellm-agent-qa</module>
    </modules>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-dependencies</artifactId>
                <version>${spring-boot.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
            <dependency>
                <groupId>org.springframework.cloud</groupId>
                <artifactId>spring-cloud-dependencies</artifactId>
                <version>${spring-cloud.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
            <dependency>
                <groupId>com.alibaba.cloud</groupId>
                <artifactId>spring-cloud-alibaba-dependencies</artifactId>
                <version>${spring-cloud-alibaba.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
            <!-- 项目内模块 -->
            <dependency>
                <groupId>com.sellm</groupId>
                <artifactId>sellm-common</artifactId>
                <version>${project.version}</version>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <build>
        <pluginManagement>
            <plugins>
                <plugin>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-maven-plugin</artifactId>
                    <version>${spring-boot.version}</version>
                </plugin>
            </plugins>
        </pluginManagement>
    </build>
</project>
```

- [ ] **Step 2: 确认 backend/pom.xml 保持不变**

不修改 `backend/pom.xml`。设计要点:
- `backend` 模块的 parent 保持 `spring-boot-starter-parent`(继承链不变,现有依赖与测试零影响)。
- 根 `pom.xml` 仅做 **reactor 聚合**(`<modules>` 列出子目录),reactor 聚合不要求子模块以聚合器为 parent。
- **新增**模块(common/gateway/4 个 Agent)才以 `sellm-parent` 为 parent,通过其 dependencyManagement 导入的 `spring-boot-dependencies` BOM 拿到版本管理。
- 因此存在两条 parent 链(backend → starter-parent;新模块 → sellm-parent),互不干扰,这是 P0 渐进策略的关键。

- [ ] **Step 3: 创建 sellm-common 空壳模块**

```
sellm-common/
├── pom.xml
└── src/main/java/com/sellm/common/package-info.java
```

`sellm-common/pom.xml`:
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

    <artifactId>sellm-common</artifactId>
    <name>sellm-common</name>
    <description>共享能力空壳模块(P0 阶段仅占位,P1 逐步抽离公共代码)</description>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-api</artifactId>
            <version>${jjwt.version}</version>
        </dependency>
    </dependencies>
</project>
```

`sellm-common/src/main/java/com/sellm/common/package-info.java`:
```java
/**
 * sellm-common: 共享能力层(P0 空壳占位)。
 * P1 阶段将从 sellm-agent-assessment 逐步抽离:
 * - Result / GlobalExceptionHandler (统一返回/异常)
 * - FieldCipher (PII 加密)
 * - AccessGuard (行级权限)
 * - JWT / SecurityConfig 基础
 */
package com.sellm.common;
```

- [ ] **Step 4: 验证 reactor 构建**

Run: `cd /d/works/test/SELLM/dev_workspace && mvn -pl sellm-common -am clean install -q 2>&1 | tail -5`

Expected: BUILD SUCCESS(sellm-common jar 安装到本地仓库)

Run: `cd /d/works/test/SELLM/dev_workspace && mvn -pl backend clean test -q 2>&1 | tail -5`

Expected: BUILD SUCCESS(现有 237 测试全绿,不受 reactor 父 POM 影响)

- [ ] **Step 5: Commit**

```bash
git add pom.xml sellm-common/
git commit -m "feat(p0): Maven reactor parent + sellm-common 空壳模块"
```

---

### Task 2: 四个新 Agent 服务脚手架(Java 空壳)

**Files:**
- Create: `sellm-agent-teaching/pom.xml`
- Create: `sellm-agent-teaching/src/main/java/com/sellm/teaching/TeachingApplication.java`
- Create: `sellm-agent-teaching/src/main/java/com/sellm/teaching/HealthController.java`
- Create: `sellm-agent-teaching/src/main/resources/application.yml`
- Create: `sellm-agent-teaching/src/test/java/com/sellm/teaching/TeachingApplicationTest.java`
- (同结构重复 research / aids / qa 四个)
- Create: `sellm-gateway/pom.xml`
- Create: `sellm-gateway/src/main/java/com/sellm/gateway/GatewayApplication.java`
- Create: `sellm-gateway/src/main/resources/application.yml`

**Interfaces:**
- Consumes: `sellm-common` (compile 依赖,P0 仅占位 jar)
- Produces: 每个 Agent 可独立 `mvn spring-boot:run` 启动并暴露 `GET /actuator/health` → 200

- [ ] **Step 1: 创建 Agent 模块模板(以 teaching 为例)**

`sellm-agent-teaching/pom.xml`:
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

    <artifactId>sellm-agent-teaching</artifactId>
    <name>sellm-agent-teaching</name>
    <description>教学训练 Agent 业务服务</description>

    <dependencies>
        <dependency>
            <groupId>com.sellm</groupId>
            <artifactId>sellm-common</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
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

`sellm-agent-teaching/src/main/java/com/sellm/teaching/TeachingApplication.java`:
```java
package com.sellm.teaching;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class TeachingApplication {
    public static void main(String[] args) {
        SpringApplication.run(TeachingApplication.class, args);
    }
}
```

`sellm-agent-teaching/src/main/java/com/sellm/teaching/HealthController.java`:
```java
package com.sellm.teaching;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
public class HealthController {
    @GetMapping("/api/teaching/health")
    public Map<String, String> health() {
        return Map.of("service", "agent-teaching", "status", "UP");
    }
}
```

`sellm-agent-teaching/src/main/resources/application.yml`:
```yaml
spring:
  application:
    name: agent-teaching
server:
  port: 8081

management:
  endpoints:
    web:
      exposure:
        include: health
```

`sellm-agent-teaching/src/test/java/com/sellm/teaching/TeachingApplicationTest.java`:
```java
package com.sellm.teaching;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class TeachingApplicationTest {
    @Test
    void contextLoads() {
    }
}
```

- [ ] **Step 2: 同结构创建 research / aids / qa 三个模块**

对每个模块替换:
- `teaching` → `research` / `aids` / `qa`
- `TeachingApplication` → `ResearchApplication` / `AidsApplication` / `QaApplication`
- 包名 `com.sellm.teaching` → `com.sellm.research` / `com.sellm.aids` / `com.sellm.qa`
- 端口:`8082`(research)/ `8083`(aids)/ `8084`(qa)
- service name:`agent-research` / `agent-aids` / `agent-qa`
- health path:`/api/research/health` / `/api/aids/health` / `/api/qa/health`

- [ ] **Step 3: 创建 API 网关模块**

`sellm-gateway/pom.xml`:
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

    <artifactId>sellm-gateway</artifactId>
    <name>sellm-gateway</name>
    <description>API Gateway — 统一入口/鉴权/路由/限流</description>

    <dependencies>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-gateway</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
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

`sellm-gateway/src/main/java/com/sellm/gateway/GatewayApplication.java`:
```java
package com.sellm.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class GatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
```

`sellm-gateway/src/main/resources/application.yml`:
```yaml
spring:
  application:
    name: sellm-gateway
  cloud:
    gateway:
      routes:
        - id: agent-assessment
          uri: http://localhost:8080
          predicates:
            - Path=/api/assessment/**,/api/auth/**,/api/admin/**
        - id: agent-teaching
          uri: http://localhost:8081
          predicates:
            - Path=/api/teaching/**
        - id: agent-research
          uri: http://localhost:8082
          predicates:
            - Path=/api/research/**
        - id: agent-aids
          uri: http://localhost:8083
          predicates:
            - Path=/api/aids/**
        - id: agent-qa
          uri: http://localhost:8084
          predicates:
            - Path=/api/qa/**

server:
  port: 9000

management:
  endpoints:
    web:
      exposure:
        include: health,gateway
```

> **Nacos 服务发现的 P0 取舍(显式声明,非遗漏)**:Spec 将 Nacos 列入 P0。本计划**基础设施层**(Task 3)用 docker-compose 拉起 Nacos,但**应用层路由**先用静态 `uri: http://localhost:PORT`,**不**在各 Agent/网关接入 `spring-cloud-starter-alibaba-nacos-discovery` 客户端。原因:接入发现客户端后,`@SpringBootTest` 的 `contextLoads` 在无 Nacos 运行时会失败或需额外 `spring.cloud.nacos.discovery.enabled=false` 测试配置,增加 P0 风险且与"测试不依赖外部服务"约束冲突。**P1 第一步**再接入发现客户端,届时网关路由改为 `uri: lb://agent-xxx`,并在各模块 `application-test.yml` 设 `spring.cloud.nacos.discovery.enabled=false`。此为有意分阶段,不是覆盖缺口。

- [ ] **Step 4: 验证全部 reactor 构建**

Run:
```bash
cd /d/works/test/SELLM/dev_workspace
mvn clean install -DskipTests -q 2>&1 | tail -10
```
Expected: BUILD SUCCESS(所有模块 jar 安装成功)

Run:
```bash
mvn -pl backend test -q 2>&1 | tail -5
```
Expected: BUILD SUCCESS(现有测试不受影响)

- [ ] **Step 5: Commit**

```bash
git add sellm-agent-teaching/ sellm-agent-research/ sellm-agent-aids/ sellm-agent-qa/ sellm-gateway/ pom.xml
git commit -m "feat(p0): 四个新Agent + API网关 空壳脚手架(health endpoint + reactor)"
```

---

### Task 3: Docker-Compose 基础设施编排

**Files:**
- Create: `docker/docker-compose.yml`
- Create: `docker/nacos/custom.properties`
- Create: `docker/.env.example`

**Interfaces:**
- Produces: `docker-compose up -d` 一键拉起 MySQL + Redis + Nacos + RabbitMQ + Milvus + MinIO;各 Agent 连接配置走环境变量

- [ ] **Step 1: 创建 docker-compose.yml**

`docker/docker-compose.yml`:
```yaml
version: "3.9"
services:
  mysql:
    image: mysql:8.0
    container_name: sellm-mysql
    environment:
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD:-sellm_dev}
      MYSQL_DATABASE: asd_assistant
    ports:
      - "3306:3306"
    volumes:
      - mysql_data:/var/lib/mysql
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost"]
      interval: 10s
      retries: 5

  redis:
    image: redis:7-alpine
    container_name: sellm-redis
    ports:
      - "6379:6379"
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 5s
      retries: 3

  nacos:
    image: nacos/nacos-server:v2.3.0
    container_name: sellm-nacos
    environment:
      MODE: standalone
      SPRING_DATASOURCE_PLATFORM: ""
      JVM_XMS: 256m
      JVM_XMX: 256m
    ports:
      - "8848:8848"
      - "9848:9848"
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8848/nacos/v1/console/health/liveness"]
      interval: 10s
      retries: 10

  rabbitmq:
    image: rabbitmq:3.13-management-alpine
    container_name: sellm-rabbitmq
    environment:
      RABBITMQ_DEFAULT_USER: ${RABBITMQ_USER:-sellm}
      RABBITMQ_DEFAULT_PASS: ${RABBITMQ_PASS:-sellm_dev}
    ports:
      - "5672:5672"
      - "15672:15672"
    healthcheck:
      test: ["CMD", "rabbitmq-diagnostics", "-q", "ping"]
      interval: 10s
      retries: 5

  milvus:
    image: milvusdb/milvus:v2.4.4
    container_name: sellm-milvus
    environment:
      ETCD_USE_EMBED: "true"
      COMMON_STORAGETYPE: local
    ports:
      - "19530:19530"
    volumes:
      - milvus_data:/var/lib/milvus

  minio:
    image: minio/minio:latest
    container_name: sellm-minio
    environment:
      MINIO_ROOT_USER: ${MINIO_USER:-minioadmin}
      MINIO_ROOT_PASSWORD: ${MINIO_PASS:-minioadmin}
    command: server /data --console-address ":9001"
    ports:
      - "9000:9000"
      - "9001:9001"
    volumes:
      - minio_data:/data

volumes:
  mysql_data:
  milvus_data:
  minio_data:
```

- [ ] **Step 2: 创建 docker/.env.example**

```bash
# docker-compose 环境变量(复制为 .env 后 docker-compose 自动加载)
MYSQL_ROOT_PASSWORD=sellm_dev
RABBITMQ_USER=sellm
RABBITMQ_PASS=sellm_dev
MINIO_USER=minioadmin
MINIO_PASS=minioadmin
```

- [ ] **Step 3: 验证 compose 配置语法**

Run:
```bash
cd /d/works/test/SELLM/dev_workspace/docker
docker compose config --quiet 2>&1 && echo "CONFIG VALID" || echo "CONFIG INVALID"
```
Expected: CONFIG VALID(或 docker-compose 未安装则跳过,打印提示)

- [ ] **Step 4: Commit**

```bash
git add docker/
git commit -m "feat(p0): docker-compose 基础设施(MySQL/Redis/Nacos/RabbitMQ/Milvus/MinIO)"
```

---

### Task 4: Python 智能层脚手架(FastAPI + LangGraph)

**Files:**
- Create: `ai-smart-layer/pyproject.toml`
- Create: `ai-smart-layer/requirements.txt`
- Create: `ai-smart-layer/app/__init__.py`
- Create: `ai-smart-layer/app/main.py`
- Create: `ai-smart-layer/app/config.py`
- Create: `ai-smart-layer/app/agents/__init__.py`
- Create: `ai-smart-layer/app/agents/teaching.py`
- Create: `ai-smart-layer/app/agents/research.py`
- Create: `ai-smart-layer/app/agents/aids.py`
- Create: `ai-smart-layer/app/agents/qa.py`
- Create: `ai-smart-layer/app/rag/__init__.py`
- Create: `ai-smart-layer/app/rag/pipeline.py`
- Create: `ai-smart-layer/app/adapters/__init__.py`
- Create: `ai-smart-layer/app/adapters/llm.py`
- Create: `ai-smart-layer/tests/__init__.py`
- Create: `ai-smart-layer/tests/test_health.py`
- Create: `ai-smart-layer/Dockerfile`

**Interfaces:**
- Produces: `uvicorn app.main:app` 启动,暴露 `GET /health` + `POST /v1/agents/{agent_name}/invoke`(空壳返回 mock)

- [ ] **Step 1: 创建 pyproject.toml + requirements.txt**

`ai-smart-layer/pyproject.toml`:
```toml
[project]
name = "sellm-ai-smart-layer"
version = "0.1.0"
description = "SELLM 特教平台 Python 智能层(LangGraph + LlamaIndex + FastAPI)"
requires-python = ">=3.11"
dependencies = [
    "fastapi==0.111.0",
    "uvicorn[standard]==0.30.1",
    "pydantic==2.7.4",
    "pydantic-settings==2.3.4",
    "langgraph==0.1.5",
    "langchain-core==0.2.10",
    "llama-index-core==0.10.50",
    "httpx==0.27.0",
    "python-dotenv==1.0.1",
]

[project.optional-dependencies]
dev = ["pytest==8.2.2", "pytest-asyncio==0.23.7", "httpx==0.27.0"]

[tool.pytest.ini_options]
testpaths = ["tests"]
asyncio_mode = "auto"
```

`ai-smart-layer/requirements.txt`:
```
fastapi==0.111.0
uvicorn[standard]==0.30.1
pydantic==2.7.4
pydantic-settings==2.3.4
langgraph==0.1.5
langchain-core==0.2.10
llama-index-core==0.10.50
httpx==0.27.0
python-dotenv==1.0.1
pytest==8.2.2
pytest-asyncio==0.23.7
```

- [ ] **Step 2: 创建 FastAPI 主入口**

`ai-smart-layer/app/__init__.py`:
```python
```

`ai-smart-layer/app/config.py`:
```python
"""配置:全部走环境变量,默认 Mock 不外联。"""
from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    # LLM
    ai_provider: str = "mock"  # mock | openai
    ai_base_url: str = ""
    ai_api_key: str = ""
    ai_model: str = "gpt-4o-mini"
    ai_timeout: int = 60

    # Milvus
    milvus_host: str = "localhost"
    milvus_port: int = 19530

    # Java 业务层 gRPC/REST
    assessment_service_url: str = "http://localhost:8080"
    teaching_service_url: str = "http://localhost:8081"

    class Config:
        env_prefix = "SELLM_"
        env_file = ".env"


settings = Settings()
```

`ai-smart-layer/app/main.py`:
```python
"""SELLM AI 智能层 — FastAPI 入口"""
from fastapi import FastAPI
from app.config import settings

app = FastAPI(title="SELLM AI Smart Layer", version="0.1.0")


@app.get("/health")
async def health():
    return {"service": "ai-smart-layer", "status": "UP", "ai_provider": settings.ai_provider}


@app.post("/v1/agents/{agent_name}/invoke")
async def invoke_agent(agent_name: str, payload: dict):
    """
    统一 Agent 调用入口(P0 空壳返回 mock)。
    Java 业务服务经此 endpoint 触发 LLM 编排。
    """
    return {
        "agent": agent_name,
        "status": "mock",
        "result": f"[MOCK] {agent_name} agent invoked with keys: {list(payload.keys())}",
    }
```

- [ ] **Step 3: 创建 Agent 占位模块**

`ai-smart-layer/app/agents/__init__.py`:
```python
```

`ai-smart-layer/app/agents/teaching.py`:
```python
"""教学训练 Agent — LangGraph 编排骨架(P0 空壳)"""


async def invoke_teaching(payload: dict) -> dict:
    """P0: 返回 mock;P3 阶段实现 LangGraph 状态图。"""
    return {"agent": "teaching", "mock": True, "input_keys": list(payload.keys())}
```

`ai-smart-layer/app/agents/research.py`:
```python
"""科研助手 Agent — LangGraph 编排骨架(P0 空壳)"""


async def invoke_research(payload: dict) -> dict:
    return {"agent": "research", "mock": True, "input_keys": list(payload.keys())}
```

`ai-smart-layer/app/agents/aids.py`:
```python
"""智能教具 Agent — LangGraph 编排骨架(P0 空壳)"""


async def invoke_aids(payload: dict) -> dict:
    return {"agent": "aids", "mock": True, "input_keys": list(payload.keys())}
```

`ai-smart-layer/app/agents/qa.py`:
```python
"""通用问答 Agent — LangGraph 编排骨架(P0 空壳)"""


async def invoke_qa(payload: dict) -> dict:
    return {"agent": "qa", "mock": True, "input_keys": list(payload.keys())}
```

- [ ] **Step 4: 创建 RAG 管线占位**

`ai-smart-layer/app/rag/__init__.py`:
```python
```

`ai-smart-layer/app/rag/pipeline.py`:
```python
"""RAG 检索管线骨架(P0 空壳;P2 阶段接入 Milvus + LlamaIndex)"""
from typing import List


async def retrieve(query: str, collection: str = "kb_special_edu", top_k: int = 5) -> List[dict]:
    """P0: 返回空列表 mock;真实实现将调用 Milvus 向量检索 + rerank。"""
    return []
```

- [ ] **Step 5: 创建 LLM 适配器占位**

`ai-smart-layer/app/adapters/__init__.py`:
```python
```

`ai-smart-layer/app/adapters/llm.py`:
```python
"""LLM 适配器 — 可切换(mock/openai),默认 mock 不外联。"""
from app.config import settings


class MockLLM:
    async def generate(self, prompt: str) -> str:
        return f"[MOCK LLM] received prompt of {len(prompt)} chars"


class OpenAILLM:
    """P1+ 阶段实现;强制 HTTP/1.1,发请求抽为 _send() 便于测试。"""

    async def _send(self, payload: dict) -> dict:
        raise NotImplementedError("OpenAI adapter not implemented in P0")

    async def generate(self, prompt: str) -> str:
        resp = await self._send({"messages": [{"role": "user", "content": prompt}]})
        return resp.get("choices", [{}])[0].get("message", {}).get("content", "")


def get_llm():
    if settings.ai_provider == "openai":
        return OpenAILLM()
    return MockLLM()
```

- [ ] **Step 6: 创建测试**

`ai-smart-layer/tests/__init__.py`:
```python
```

`ai-smart-layer/tests/test_health.py`:
```python
import pytest
from httpx import AsyncClient, ASGITransport
from app.main import app


@pytest.mark.asyncio
async def test_health():
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        resp = await client.get("/health")
    assert resp.status_code == 200
    data = resp.json()
    assert data["status"] == "UP"
    assert data["ai_provider"] == "mock"


@pytest.mark.asyncio
async def test_invoke_mock():
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        resp = await client.post("/v1/agents/teaching/invoke", json={"text": "hello"})
    assert resp.status_code == 200
    data = resp.json()
    assert data["agent"] == "teaching"
    assert data["status"] == "mock"
```

- [ ] **Step 7: 创建 Dockerfile**

`ai-smart-layer/Dockerfile`:
```dockerfile
FROM python:3.11-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY app/ app/
EXPOSE 8090
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8090"]
```

- [ ] **Step 8: 验证(本地无需 Python 环境也可跳过;有则运行)**

Run(如 Python 3.11+ 可用):
```bash
cd /d/works/test/SELLM/dev_workspace/ai-smart-layer
pip install -e ".[dev]" -q 2>&1 | tail -3
pytest tests/ -v 2>&1 | tail -10
```
Expected: 2 passed

- [ ] **Step 9: Commit**

```bash
git add ai-smart-layer/
git commit -m "feat(p0): Python 智能层脚手架(FastAPI + LangGraph/LlamaIndex 空壳 + mock LLM)"
```

---

### Task 5: 微信小程序脚手架(uni-app)

**Files:**
- Create: `miniprogram/package.json`
- Create: `miniprogram/src/main.js`
- Create: `miniprogram/src/App.vue`
- Create: `miniprogram/src/pages.json`
- Create: `miniprogram/src/pages/index/index.vue`
- Create: `miniprogram/src/store/user.js`
- Create: `miniprogram/src/utils/http.js`
- Create: `miniprogram/src/manifest.json`
- Create: `miniprogram/vite.config.js`
- Create: `miniprogram/index.html`

**Interfaces:**
- Produces: `npm run dev:mp-weixin` 编译到 `dist/dev/mp-weixin`;`npm run build:mp-weixin` 产出可导入微信开发者工具的产物

- [ ] **Step 1: 创建 package.json**

`miniprogram/package.json`:
```json
{
  "name": "sellm-miniprogram",
  "version": "0.1.0",
  "private": true,
  "scripts": {
    "dev:mp-weixin": "uni -p mp-weixin",
    "build:mp-weixin": "uni build -p mp-weixin"
  },
  "dependencies": {
    "@dcloudio/uni-app": "3.0.0-4020420240722001",
    "@dcloudio/uni-app-harmony": "3.0.0-4020420240722001",
    "@dcloudio/uni-components": "3.0.0-4020420240722001",
    "@dcloudio/uni-h5": "3.0.0-4020420240722001",
    "@dcloudio/uni-mp-weixin": "3.0.0-4020420240722001",
    "vue": "3.4.27",
    "pinia": "2.1.7"
  },
  "devDependencies": {
    "@dcloudio/uni-automator": "3.0.0-4020420240722001",
    "@dcloudio/uni-cli-shared": "3.0.0-4020420240722001",
    "@dcloudio/uni-stacktracey": "3.0.0-4020420240722001",
    "@dcloudio/vite-plugin-uni": "3.0.0-4020420240722001",
    "vite": "5.2.8"
  }
}
```

- [ ] **Step 2: 创建入口文件**

`miniprogram/index.html`:
```html
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>SELLM 特教助手</title>
</head>
<body>
    <div id="app"></div>
    <script type="module" src="/src/main.js"></script>
</body>
</html>
```

`miniprogram/vite.config.js`:
```javascript
import { defineConfig } from 'vite'
import uni from '@dcloudio/vite-plugin-uni'

export default defineConfig({
  plugins: [uni()],
})
```

`miniprogram/src/main.js`:
```javascript
import { createSSRApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'

export function createApp() {
  const app = createSSRApp(App)
  app.use(createPinia())
  return { app }
}
```

`miniprogram/src/App.vue`:
```vue
<script setup>
import { onLaunch } from '@dcloudio/uni-app'

onLaunch(() => {
  console.log('SELLM 小程序启动')
})
</script>

<style>
page {
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
  font-size: 28rpx;
  color: #333;
}
</style>
```

- [ ] **Step 3: 创建页面与路由**

`miniprogram/src/pages.json`:
```json
{
  "pages": [
    { "path": "pages/index/index", "style": { "navigationBarTitleText": "特教助手" } }
  ],
  "globalStyle": {
    "navigationBarTextStyle": "black",
    "navigationBarTitleText": "SELLM",
    "navigationBarBackgroundColor": "#FFFFFF",
    "backgroundColor": "#F8F8F8"
  }
}
```

`miniprogram/src/pages/index/index.vue`:
```vue
<template>
  <view class="container">
    <text class="title">SELLM 特教助手</text>
    <text class="subtitle">家长轻量终端 · P0 骨架</text>
  </view>
</template>

<script setup>
</script>

<style scoped>
.container {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding-top: 200rpx;
}
.title {
  font-size: 40rpx;
  font-weight: bold;
}
.subtitle {
  font-size: 28rpx;
  color: #999;
  margin-top: 20rpx;
}
</style>
```

- [ ] **Step 4: 创建 Pinia store + http 工具**

`miniprogram/src/store/user.js`:
```javascript
import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useUserStore = defineStore('user', () => {
  const token = ref('')
  const userInfo = ref(null)

  function setToken(t) { token.value = t }
  function setUser(u) { userInfo.value = u }
  function logout() { token.value = ''; userInfo.value = null }

  return { token, userInfo, setToken, setUser, logout }
})
```

`miniprogram/src/utils/http.js`:
```javascript
/**
 * uni-app 封装 request — 统一拦截、JWT 注入、错误处理。
 * 基地址通过环境变量或 manifest.json 注入(P0 占位)。
 */
const BASE_URL = 'http://localhost:9000' // 网关地址(dev)

export function request(options) {
  const { useUserStore } = require('../store/user')
  const userStore = useUserStore()

  return new Promise((resolve, reject) => {
    uni.request({
      url: BASE_URL + options.url,
      method: options.method || 'GET',
      data: options.data,
      header: {
        'Content-Type': 'application/json',
        ...(userStore.token ? { Authorization: `Bearer ${userStore.token}` } : {}),
        ...options.header,
      },
      success: (res) => {
        if (res.data.code === 0) {
          resolve(res.data.data)
        } else if (res.data.code >= 2000 && res.data.code < 3000) {
          userStore.logout()
          uni.navigateTo({ url: '/pages/login/login' })
          reject(res.data)
        } else {
          uni.showToast({ title: res.data.message || '请求失败', icon: 'none' })
          reject(res.data)
        }
      },
      fail: (err) => {
        uni.showToast({ title: '网络错误', icon: 'none' })
        reject(err)
      },
    })
  })
}
```

- [ ] **Step 5: 创建 manifest.json**

`miniprogram/src/manifest.json`:
```json
{
  "name": "SELLM 特教助手",
  "appid": "",
  "description": "特殊教育垂直大模型平台 — 家长端小程序",
  "versionName": "0.1.0",
  "versionCode": "100",
  "mp-weixin": {
    "appid": "",
    "setting": {
      "urlCheck": true,
      "es6": true,
      "minified": true
    },
    "usingComponents": true
  }
}
```

- [ ] **Step 6: 验证(npm install + build)**

Run:
```bash
cd /d/works/test/SELLM/dev_workspace/miniprogram
npm install 2>&1 | tail -5
npm run build:mp-weixin 2>&1 | tail -10
```
Expected: 产出 `dist/build/mp-weixin/` 目录(可导入微信开发者工具)

- [ ] **Step 7: Commit**

```bash
git add miniprogram/
git commit -m "feat(p0): uni-app 微信小程序脚手架(家长端骨架 + Pinia + http封装)"
```

---

### Task 6: RabbitMQ 事件总线共享契约 + .env.example 更新

**Files:**
- Create: `sellm-common/src/main/java/com/sellm/common/event/AgentEvent.java`
- Create: `sellm-common/src/main/java/com/sellm/common/event/EventConstants.java`
- Modify: `.env.example`(追加 Nacos/RabbitMQ/Redis/Milvus 配置段)

**Interfaces:**
- Produces: `AgentEvent` 共享 DTO(所有 Agent 模块引用)+ `EventConstants` routing key 常量

- [ ] **Step 1: 创建 AgentEvent DTO**

`sellm-common/src/main/java/com/sellm/common/event/AgentEvent.java`:
```java
package com.sellm.common.event;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * 跨 Agent 事件标准消息体。
 * payload 内不含明文 PII(只传 id + 脱敏字段)。
 */
public record AgentEvent(
        String eventId,
        String routingKey,
        Instant occurredAt,
        Long actorUserId,
        Long orgId,
        Map<String, Object> payload
) {
    public static AgentEvent of(String routingKey, Long actorUserId, Long orgId, Map<String, Object> payload) {
        return new AgentEvent(
                UUID.randomUUID().toString(),
                routingKey,
                Instant.now(),
                actorUserId,
                orgId,
                payload
        );
    }
}
```

- [ ] **Step 2: 创建 EventConstants**

`sellm-common/src/main/java/com/sellm/common/event/EventConstants.java`:
```java
package com.sellm.common.event;

/**
 * RabbitMQ 事件总线常量。
 * Exchange: sellm.agent.events (topic)
 * Routing key 规范: {agent}.{entity}.{action}
 */
public final class EventConstants {
    private EventConstants() {}

    public static final String EXCHANGE = "sellm.agent.events";
    public static final String DLQ = "sellm.agent.dlq";

    // 评估干预 Agent
    public static final String ASSESSMENT_REPORT_FINALIZED = "assessment.report.finalized";
    public static final String ASSESSMENT_IEP_FINALIZED = "assessment.iep.finalized";

    // 教学训练 Agent
    public static final String TEACHING_LESSON_PLAN_GENERATED = "teaching.lesson-plan.generated";

    // 智能教具 Agent
    public static final String AIDS_VIDEO_GENERATED = "aids.video.generated";
    public static final String AIDS_IMAGE_GENERATED = "aids.image.generated";

    // 科研助手 Agent
    public static final String RESEARCH_PROPOSAL_DRAFTED = "research.proposal.drafted";
}
```

- [ ] **Step 3: 更新 .env.example 追加平台基础设施配置**

在 `.env.example` 末尾追加:
```bash

# ══════════════════════════════════════════════════
# P0 平台基础设施(docker-compose 外部依赖连接)
# ══════════════════════════════════════════════════

# ── Nacos 注册发现/配置中心 ──
SELLM_NACOS_ADDR=127.0.0.1:8848

# ── RabbitMQ 事件总线 ──
SELLM_RABBITMQ_HOST=127.0.0.1
SELLM_RABBITMQ_PORT=5672
SELLM_RABBITMQ_USER=sellm
SELLM_RABBITMQ_PASS=sellm_dev

# ── Redis(网关限流/缓存/幂等去重)──
SELLM_REDIS_HOST=127.0.0.1
SELLM_REDIS_PORT=6379

# ── Milvus 向量库 ──
SELLM_MILVUS_HOST=127.0.0.1
SELLM_MILVUS_PORT=19530
```

- [ ] **Step 4: 验证 sellm-common 仍可编译**

Run:
```bash
cd /d/works/test/SELLM/dev_workspace
mvn -pl sellm-common clean compile -q && echo "COMPILE OK"
```
Expected: COMPILE OK

- [ ] **Step 5: Commit**

```bash
git add sellm-common/src/main/java/com/sellm/common/event/ .env.example
git commit -m "feat(p0): RabbitMQ 事件契约(AgentEvent + EventConstants) + .env.example 扩平台配置"
```

---

### Task 7: 前端 build 验证 + 最终基线回归

**Files:**
- No new files; verification only

**Interfaces:**
- Produces: 确认 P0 脚手架不破坏任何现有功能

- [ ] **Step 1: 后端全量测试回归**

Run:
```bash
cd /d/works/test/SELLM/dev_workspace
mvn -pl backend test -q 2>&1 | tail -5
```
Expected: BUILD SUCCESS(237 tests all pass)

- [ ] **Step 2: 前端 build 验证**

Run:
```bash
cd /d/works/test/SELLM/dev_workspace/frontend
npm run build 2>&1 | tail -5
```
Expected: `✓ built in X.XXs`

- [ ] **Step 3: 新 Agent 模块上下文加载测试**

Run:
```bash
cd /d/works/test/SELLM/dev_workspace
mvn -pl sellm-agent-teaching test -q 2>&1 | tail -3
mvn -pl sellm-agent-research test -q 2>&1 | tail -3
mvn -pl sellm-agent-aids test -q 2>&1 | tail -3
mvn -pl sellm-agent-qa test -q 2>&1 | tail -3
```
Expected: 每个 BUILD SUCCESS(contextLoads 通过)

- [ ] **Step 4: Reactor 全量构建**

Run:
```bash
cd /d/works/test/SELLM/dev_workspace
mvn clean install -q 2>&1 | tail -5
```
Expected: BUILD SUCCESS(所有模块)

- [ ] **Step 5: 记录变更日志**

追加到 `.claude/CLAUDE_CHANGES.md`(如 worktree 内有此文件)。

- [ ] **Step 6: 最终标记提交(如有未提交文件)**

```bash
git status --short
# 如有遗漏文件 git add + commit
```

---

## 文件清单总览

```
dev_workspace/                       (worktree root)
├── pom.xml                          (NEW: Maven reactor parent)
├── backend/                         (EXISTING: 评估干预 Agent,不改)
├── frontend/                        (EXISTING: Web 管理端,不改)
├── sellm-common/                    (NEW: 共享能力空壳)
│   ├── pom.xml
│   └── src/main/java/com/sellm/common/
│       ├── package-info.java
│       └── event/
│           ├── AgentEvent.java
│           └── EventConstants.java
├── sellm-gateway/                   (NEW: API 网关)
│   ├── pom.xml
│   └── src/main/java/com/sellm/gateway/
│       └── GatewayApplication.java
├── sellm-agent-teaching/            (NEW: 教学训练空壳)
├── sellm-agent-research/            (NEW: 科研助手空壳)
├── sellm-agent-aids/                (NEW: 智能教具空壳)
├── sellm-agent-qa/                  (NEW: 通用问答空壳)
│   (每个含 pom.xml + Application + HealthController + test + yml)
├── ai-smart-layer/                  (NEW: Python 智能层)
│   ├── pyproject.toml / requirements.txt / Dockerfile
│   ├── app/ (main.py / config.py / agents/ / rag/ / adapters/)
│   └── tests/
├── miniprogram/                     (NEW: uni-app 小程序)
│   ├── package.json / vite.config.js / index.html
│   └── src/ (main.js / App.vue / pages/ / store/ / utils/)
├── docker/                          (NEW: 基础设施编排)
│   ├── docker-compose.yml
│   └── .env.example
└── docs/spec/special_edu_llm_spec.md (EXISTING: 平台 Spec)
```
