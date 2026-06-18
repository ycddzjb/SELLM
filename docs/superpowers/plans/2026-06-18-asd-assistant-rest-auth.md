# ASD 助手 — 计划三:REST 层 + JWT 认证 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 给 ASD 助手后端加上认证与 HTTP API:JWT 无状态认证 + 三角色 RBAC,把"评估→报告→IEP 草案"主链路连同 Child 档案暴露为受保护的 REST 端点,并把 Assessment/Report/Iep 记录落库。

**Architecture:** 沿用方案一模块化单体。新增 `security` 包(Spring Security + JWT 过滤器)、`user` 包(User/机构、登录、BCrypt)。各业务包加 controller + 记录持久化(entity 落库 + service 编排)。复用计划一的领域服务(ReportService/IepService/DefaultScoringEngine)与计划二的持久化(ChildRepository/ScaleRepository/anonymizer/aigateway)。测试用 H2 + MockMvc 做端到端 HTTP 验证,延续"测试不依赖外部基础设施"。

**Tech Stack:** Java 17、Spring Boot 3.2.5、spring-boot-starter-web、spring-boot-starter-security、io.jsonwebtoken(jjwt)0.12.6、MyBatis、H2(test)、MockMvc。根包 `com.sellm`。不使用 Lombok。

**Source spec:** `docs/superpowers/specs/2026-06-18-asd-assessment-iep-assistant-design.md`
**Builds on:** 计划一(领域核心)、计划二(持久化层),均已在 master。

---

## 设计要点与红线衔接

1. **三角色**:`TEACHER`(老师/康复师)、`MANAGER`(管理者)、`PARENT`(家长)。本计划聚焦把链路跑通,RBAC 第一版按"端点级"粗粒度授权(老师/管理者可写评估与 IEP,家长只读),细粒度数据范围(家长只能看自己孩子)留后续。
2. **AI 红线延续**:报告/IEP 仍只产 DRAFT,经 AiGateway(内部脱敏);REST 层不绕过 service 直连模型。
3. **加密红线延续**:Child 姓名经 ChildRepository 加密落库;API 出入参用明文 DTO,加解密在 repository 层,controller/DTO 不碰密文。
4. **业务记录落库**:计划一的 Report/Iep 是内存对象、ReportService/IepService 直接 new 返回。本计划新增 assessment/report/iep 记录表与持久化,REST service 负责"读 Child/Scale → 计分 → 存 Assessment → 生成并存 Report/Iep 草稿"。
5. **统一返回**:复用计划一 `common.Result`,REST 控制器统一包 `Result<T>`;异常经全局 `@RestControllerAdvice` 转 `Result.error`。

---

## 文件结构(本计划范围)

```
backend/
  pom.xml                                          # 加 web/security/jjwt 依赖(Task 1)
  src/main/resources/
    schema.sql                                     # 追加 app_user/assessment/report/iep 表(Task 3/6/8/9)
  src/main/java/com/sellm/
    common/
      GlobalExceptionHandler.java                  # @RestControllerAdvice → Result(Task 2)
    security/
      JwtService.java                              # 签发/校验 JWT(Task 4)
      JwtAuthFilter.java                            # 解析 Bearer、设置 SecurityContext(Task 5)
      SecurityConfig.java                           # 过滤链、端点授权、BCrypt bean(Task 5)
      Role.java                                     # 枚举 TEACHER/MANAGER/PARENT(Task 3)
    user/
      AppUser.java / AppUserMapper.java / mybatis/AppUserMapper.xml
      UserRepository.java                           # 注册/查用户、BCrypt(Task 3)
      AuthController.java                           # /api/auth/register、/login(Task 6)
      dto/RegisterRequest.java / LoginRequest.java / LoginResponse.java
    child/
      ChildController.java                          # /api/children CRUD(Task 7)
      dto/ChildRequest.java / ChildResponse.java
    assessment/
      Assessment.java / AssessmentMapper.java / mybatis/AssessmentMapper.xml
      AssessmentRepository.java                     # 评估记录落库(Task 8)
      AssessmentAppService.java                     # 读Child/Scale→计分→存(Task 8)
      AssessmentController.java                     # /api/assessments(Task 8)
      dto/SubmitAssessmentRequest.java / AssessmentResponse.java
    report/
      ReportRecordMapper.java / mybatis/ReportRecordMapper.xml
      ReportAppService.java                         # 基于评估生成并存报告草稿、定稿(Task 9)
      ReportController.java                         # /api/reports(Task 9)
      dto/...
    iep/
      IepRecordMapper.java / mybatis/IepRecordMapper.xml
      IepAppService.java                            # 基于报告生成并存IEP草案、定稿(Task 10)
      IepController.java                            # /api/ieps(Task 10)
      dto/...
  src/test/java/com/sellm/
    security/JwtServiceTest.java                     # 单测(Task 4)
    auth/AuthApiTest.java                            # MockMvc:注册/登录/JWT(Task 6)
    security/AuthorizationTest.java                  # MockMvc:无token 401、越权 403(Task 11)
    child/ChildApiTest.java                          # MockMvc CRUD(Task 7)
    assessment/AssessmentApiTest.java                # MockMvc 提交评估(Task 8)
    flow/FullChainApiTest.java                       # MockMvc 端到端:登录→建档→评估→报告→IEP(Task 11)
```

**为什么这样切:** controller/dto 就近放各业务包(延续职责分包);security/user 独立成包;App-level service(AssessmentAppService 等)区别于计划一的领域 service(ReportService),前者管"持久化+编排",后者管"领域逻辑+AI",避免把持久化塞进领域服务。

---

### Task 1: 加 web/security/jjwt 依赖

**Files:**
- Modify: `backend/pom.xml`

- [ ] **Step 1: 加依赖**

在 `<dependencies>` 内追加(spring-boot-starter 之后任意位置):
```xml
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
            <version>0.12.6</version>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-impl</artifactId>
            <version>0.12.6</version>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-jackson</artifactId>
            <version>0.12.6</version>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.security</groupId>
            <artifactId>spring-security-test</artifactId>
            <scope>test</scope>
        </dependency>
```

- [ ] **Step 2: 加 JWT 与 security 配置项**

在 `application.yml` 的 `sellm:` 下追加(与 crypto 同级):
```yaml
  jwt:
    secret: ${SELLM_JWT_SECRET:}
    expiration-minutes: 120
```
在 `application-test.yml` 的 `sellm:` 下追加(测试固定密钥,至少 32 字节供 HS256):
```yaml
  jwt:
    secret: "test-jwt-secret-key-at-least-32-bytes-long-0123456789"
    expiration-minutes: 120
```

- [ ] **Step 3: 验证编译 + 全量回归**

引入 spring-security 后,默认会给所有端点加 Basic Auth;但当前无 controller、已有测试都不发 HTTP,SpringBootTest 上下文仍应能启动。先确认无回归:
Run: `cd "D:/works/test/SELLM/backend" && ./mvnw -q test`
Expected: 现有 41 测试仍全绿。若上下文测试因 security 自动配置失败,**先不要改测试**,在 Task 5 的 SecurityConfig 落地后再验证;本步若失败记录现象并继续 Task 2-5(它们会建立 security 配置),回到 Task 5 后再跑全量。

- [ ] **Step 4: Commit**

```bash
cd "D:/works/test/SELLM" && git add backend/pom.xml backend/src/main/resources/application.yml backend/src/test/resources/application-test.yml && git commit -q -m "chore: 加 web/security/jjwt 依赖与 JWT 配置项"
```

---

### Task 2: 全局异常处理 → Result

REST 控制器统一返回 `common.Result`;业务/校验异常经全局 advice 转成 `Result.error`,而非抛 500。

**Files:**
- Create: `backend/src/main/java/com/sellm/common/GlobalExceptionHandler.java`
- Test: `backend/src/test/java/com/sellm/common/GlobalExceptionHandlerTest.java`

- [ ] **Step 1: 复习 common 现状**

先 Read `backend/src/main/java/com/sellm/common/Result.java`、`ErrorCode.java`、`BusinessException.java`,确认 `Result.error(ErrorCode)` 与 `BusinessException.getErrorCode()` 签名(计划一已实现)。

- [ ] **Step 2: 写 advice**

`GlobalExceptionHandler.java`:
```java
package com.sellm.common;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleBusiness(BusinessException e) {
        return Result.error(e.getErrorCode());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleIllegalArgument(IllegalArgumentException e) {
        return Result.error(ErrorCode.SCORING_INVALID_INPUT);
    }
}
```
注:第一版用现有 ErrorCode 枚举映射;后续可细化。`IllegalArgumentException` 暂复用 SCORING_INVALID_INPUT 语义("输入校验失败")——若觉牵强,Task 实现者可在 ErrorCode 加一个 `INVALID_INPUT("C001","输入校验失败")` 通用码并改用之(属合理改进,允许)。

- [ ] **Step 3: 写测试(纯单元,不起 web)**

`GlobalExceptionHandlerTest.java`:
```java
package com.sellm.common;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void 业务异常转为对应错误码Result() {
        Result<Void> r = handler.handleBusiness(new BusinessException(ErrorCode.ANONYMIZATION_FAILED));
        assertThat(r.getCode()).isEqualTo(ErrorCode.ANONYMIZATION_FAILED.getCode());
        assertThat(r.getData()).isNull();
    }

    @Test
    void 非法参数转为输入校验Result() {
        Result<Void> r = handler.handleIllegalArgument(new IllegalArgumentException("bad"));
        assertThat(r.getCode()).isNotEqualTo(ErrorCode.OK.getCode());
    }
}
```

- [ ] **Step 4: 跑测试 + 提交**

Run: `cd "D:/works/test/SELLM/backend" && ./mvnw -q test -Dtest=GlobalExceptionHandlerTest`
Expected: 2 PASS。
```bash
cd "D:/works/test/SELLM" && git add backend/src/main/java/com/sellm/common/GlobalExceptionHandler.java backend/src/test/java/com/sellm/common/GlobalExceptionHandlerTest.java && git commit -q -m "feat(common): 全局异常处理转统一 Result"
```

---
### Task 3: User 实体 + 三角色 + UserRepository(BCrypt 落库,H2 集成)

**Files:**
- Modify: `backend/src/main/resources/schema.sql`(追加 app_user 表)
- Create: `backend/src/main/java/com/sellm/security/Role.java`
- Create: `backend/src/main/java/com/sellm/user/AppUser.java`
- Create: `backend/src/main/java/com/sellm/user/AppUserMapper.java`
- Create: `backend/src/main/resources/mybatis/AppUserMapper.xml`
- Create: `backend/src/main/java/com/sellm/user/UserRepository.java`
- Test: `backend/src/test/java/com/sellm/user/UserRepositoryTest.java`

- [ ] **Step 1: schema.sql 追加 app_user 表**

在 schema.sql 末尾追加(表名 app_user 避开 SQL 保留字 user):
```sql
CREATE TABLE IF NOT EXISTS app_user (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    username      VARCHAR(64)  NOT NULL UNIQUE,
    password_hash VARCHAR(128) NOT NULL,
    role          VARCHAR(16)  NOT NULL,
    org_id        BIGINT,
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

- [ ] **Step 2: Role 枚举**

`backend/src/main/java/com/sellm/security/Role.java`:
```java
package com.sellm.security;

public enum Role {
    TEACHER,   // 老师/康复师
    MANAGER,   // 管理者
    PARENT     // 家长
}
```

- [ ] **Step 3: AppUser 实体**

`backend/src/main/java/com/sellm/user/AppUser.java`:
```java
package com.sellm.user;

import com.sellm.security.Role;

public class AppUser {
    private Long id;
    private String username;
    private String passwordHash;
    private Role role;
    private Long orgId;

    public AppUser() {
    }

    public AppUser(Long id, String username, String passwordHash, Role role, Long orgId) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
        this.orgId = orgId;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public Long getOrgId() { return orgId; }
    public void setOrgId(Long orgId) { this.orgId = orgId; }
}
```

- [ ] **Step 4: AppUserMapper + XML**

`AppUserMapper.java`:
```java
package com.sellm.user;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.Map;

@Mapper
public interface AppUserMapper {
    void insert(Map<String, Object> row);
    Map<String, Object> findByUsername(@Param("username") String username);
}
```

`backend/src/main/resources/mybatis/AppUserMapper.xml`(用显式 resultMap 避 H2 大写坑):
```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.sellm.user.AppUserMapper">

    <resultMap id="userMap" type="map">
        <id column="id" property="id"/>
        <result column="username" property="username"/>
        <result column="password_hash" property="passwordHash"/>
        <result column="role" property="role"/>
        <result column="org_id" property="orgId"/>
    </resultMap>

    <insert id="insert" parameterType="map" useGeneratedKeys="true" keyProperty="id">
        INSERT INTO app_user (username, password_hash, role, org_id)
        VALUES (#{username}, #{passwordHash}, #{role}, #{orgId})
    </insert>

    <select id="findByUsername" parameterType="string" resultMap="userMap">
        SELECT id, username, password_hash, role, org_id
        FROM app_user WHERE username = #{username}
    </select>

</mapper>
```

- [ ] **Step 5: 写失败测试(H2 集成)**

`backend/src/test/java/com/sellm/user/UserRepositoryTest.java`:
```java
package com.sellm.user;

import com.sellm.security.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class UserRepositoryTest {

    @Autowired
    private UserRepository repository;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void 注册后能按用户名查到且密码已哈希() {
        jdbc.update("DELETE FROM app_user WHERE username = 't1'");
        AppUser saved = repository.register("t1", "secret123", Role.TEACHER, 1L);
        assertThat(saved.getId()).isNotNull();

        AppUser found = repository.findByUsername("t1");
        assertThat(found).isNotNull();
        assertThat(found.getRole()).isEqualTo(Role.TEACHER);
        // 库里存的是 BCrypt 哈希,不是明文
        assertThat(found.getPasswordHash()).isNotEqualTo("secret123");
        assertThat(found.getPasswordHash()).startsWith("$2");
    }

    @Test
    void 密码校验正确与错误() {
        jdbc.update("DELETE FROM app_user WHERE username = 't2'");
        repository.register("t2", "rightpass", Role.MANAGER, 1L);
        AppUser u = repository.findByUsername("t2");
        assertThat(repository.matches("rightpass", u.getPasswordHash())).isTrue();
        assertThat(repository.matches("wrongpass", u.getPasswordHash())).isFalse();
    }

    @Test
    void 用户名不存在返回null() {
        assertThat(repository.findByUsername("nobody-xyz")).isNull();
    }
}
```

- [ ] **Step 6: 运行测试确认失败** — `cd "D:/works/test/SELLM/backend" && ./mvnw -q test -Dtest=UserRepositoryTest`，编译失败(UserRepository 未建)。

- [ ] **Step 7: 实现 UserRepository**

`UserRepository.java`:
```java
package com.sellm.user;

import com.sellm.security.Role;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Repository;
import java.util.HashMap;
import java.util.Map;

@Repository
public class UserRepository {

    private final AppUserMapper mapper;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public UserRepository(AppUserMapper mapper) {
        this.mapper = mapper;
    }

    public AppUser register(String username, String rawPassword, Role role, Long orgId) {
        Map<String, Object> row = new HashMap<>();
        row.put("username", username);
        row.put("passwordHash", passwordEncoder.encode(rawPassword));
        row.put("role", role.name());
        row.put("orgId", orgId);
        mapper.insert(row);
        return new AppUser(((Number) row.get("id")).longValue(),
            username, (String) row.get("passwordHash"), role, orgId);
    }

    public AppUser findByUsername(String username) {
        Map<String, Object> row = mapper.findByUsername(username);
        if (row == null) {
            return null;
        }
        Long orgId = row.get("orgId") == null ? null : ((Number) row.get("orgId")).longValue();
        return new AppUser(((Number) row.get("id")).longValue(),
            (String) row.get("username"), (String) row.get("passwordHash"),
            Role.valueOf((String) row.get("role")), orgId);
    }

    public boolean matches(String rawPassword, String passwordHash) {
        return passwordEncoder.matches(rawPassword, passwordHash);
    }
}
```

- [ ] **Step 8: 跑测试确认通过** — 3 PASS。

- [ ] **Step 9: Commit**

```bash
cd "D:/works/test/SELLM" && git add backend/src/main/resources/schema.sql backend/src/main/java/com/sellm/security/Role.java backend/src/main/java/com/sellm/user/ backend/src/main/resources/mybatis/AppUserMapper.xml backend/src/test/java/com/sellm/user/ && git commit -q -m "feat(user): User 实体+三角色+BCrypt 落库"
```

---
### Task 4: JwtService(签发/校验 JWT,TDD)

**Files:**
- Create: `backend/src/main/java/com/sellm/security/JwtService.java`
- Test: `backend/src/test/java/com/sellm/security/JwtServiceTest.java`

- [ ] **Step 1: 写失败测试**

`JwtServiceTest.java`:
```java
package com.sellm.security;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class JwtServiceTest {

    // 至少 32 字节密钥供 HS256
    private final JwtService jwt =
        new JwtService("test-jwt-secret-key-at-least-32-bytes-long-0123456789", 120);

    @Test
    void 签发的token能解析出用户名与角色() {
        String token = jwt.issue("t1", "TEACHER");
        assertThat(jwt.extractUsername(token)).isEqualTo("t1");
        assertThat(jwt.extractRole(token)).isEqualTo("TEACHER");
    }

    @Test
    void 合法token校验通过() {
        String token = jwt.issue("t1", "TEACHER");
        assertThat(jwt.isValid(token)).isTrue();
    }

    @Test
    void 篡改的token校验失败() {
        String token = jwt.issue("t1", "TEACHER");
        assertThat(jwt.isValid(token + "x")).isFalse();
    }

    @Test
    void 已过期token校验失败() {
        JwtService expired =
            new JwtService("test-jwt-secret-key-at-least-32-bytes-long-0123456789", -1);
        String token = expired.issue("t1", "TEACHER");
        assertThat(expired.isValid(token)).isFalse();
    }
}
```

- [ ] **Step 2: 运行确认失败** — `./mvnw -q test -Dtest=JwtServiceTest`，编译失败。

- [ ] **Step 3: 实现 JwtService**

`JwtService.java`:
```java
package com.sellm.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtService {

    private final SecretKey key;
    private final long expirationMinutes;

    public JwtService(@Value("${sellm.jwt.secret:}") String secret,
                      @Value("${sellm.jwt.expiration-minutes:120}") long expirationMinutes) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("sellm.jwt.secret 必须至少 32 字节(HS256)");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMinutes = expirationMinutes;
    }

    public String issue(String username, String role) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + expirationMinutes * 60_000);
        return Jwts.builder()
            .subject(username)
            .claim("role", role)
            .issuedAt(now)
            .expiration(exp)
            .signWith(key)
            .compact();
    }

    public String extractUsername(String token) {
        return parse(token).getSubject();
    }

    public String extractRole(String token) {
        return parse(token).get("role", String.class);
    }

    public boolean isValid(String token) {
        try {
            parse(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build()
            .parseSignedClaims(token).getPayload();
    }
}
```

- [ ] **Step 4: 跑测试确认通过** — 4 PASS。注:过期测试用 -1 分钟使 token 立即过期。

- [ ] **Step 5: Commit**

```bash
cd "D:/works/test/SELLM" && git add backend/src/main/java/com/sellm/security/JwtService.java backend/src/test/java/com/sellm/security/JwtServiceTest.java && git commit -q -m "feat(security): JWT 签发与校验(HS256)"
```

---

### Task 5: SecurityConfig + JwtAuthFilter(过滤链与端点授权)

**Files:**
- Create: `backend/src/main/java/com/sellm/security/JwtAuthFilter.java`
- Create: `backend/src/main/java/com/sellm/security/SecurityConfig.java`

- [ ] **Step 1: JwtAuthFilter**

`JwtAuthFilter.java`(从 Bearer 头解析 token,有效则设置带角色的 Authentication):
```java
package com.sellm.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            if (jwtService.isValid(token)) {
                String username = jwtService.extractUsername(token);
                String role = jwtService.extractRole(token);
                var auth = new UsernamePasswordAuthenticationToken(
                    username, null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + role)));
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }
        chain.doFilter(request, response);
    }
}
```

- [ ] **Step 2: SecurityConfig**

`SecurityConfig.java`:
```java
package com.sellm.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()
                // 写评估/报告/IEP:仅 TEACHER、MANAGER
                .requestMatchers(HttpMethod.POST, "/api/assessments/**", "/api/reports/**", "/api/ieps/**")
                    .hasAnyRole("TEACHER", "MANAGER")
                .requestMatchers(HttpMethod.PUT, "/api/assessments/**", "/api/reports/**", "/api/ieps/**")
                    .hasAnyRole("TEACHER", "MANAGER")
                // 写 child:TEACHER、MANAGER
                .requestMatchers(HttpMethod.POST, "/api/children/**").hasAnyRole("TEACHER", "MANAGER")
                .requestMatchers(HttpMethod.PUT, "/api/children/**").hasAnyRole("TEACHER", "MANAGER")
                .requestMatchers(HttpMethod.DELETE, "/api/children/**").hasAnyRole("TEACHER", "MANAGER")
                // 其余 /api/** 需登录(GET 三角色都可)
                .requestMatchers("/api/**").authenticated()
                .anyRequest().permitAll())
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
```

- [ ] **Step 3: 验证编译 + 全量回归(关键检查点)**

Run: `cd "D:/works/test/SELLM/backend" && ./mvnw -q test`
Expected: 现有 41 + Task2(2)+ Task3(3)+ Task4(4)= 50 测试全绿。security 配置就位后,@SpringBootTest 上下文应正常启动(STATELESS + 无 controller 时不影响既有非 web 测试)。若 Task 1 Step 3 当时上下文测试受 security 影响失败,此处应恢复正常——确认 ApplicationContextTest/SchemaSmokeTest 仍绿。

- [ ] **Step 4: Commit**

```bash
cd "D:/works/test/SELLM" && git add backend/src/main/java/com/sellm/security/ && git commit -q -m "feat(security): JWT 过滤链与端点级 RBAC 授权"
```

---
### Task 6: AuthController(注册/登录 → JWT,MockMvc)

**Files:**
- Create: `backend/src/main/java/com/sellm/user/dto/RegisterRequest.java`
- Create: `backend/src/main/java/com/sellm/user/dto/LoginRequest.java`
- Create: `backend/src/main/java/com/sellm/user/dto/LoginResponse.java`
- Create: `backend/src/main/java/com/sellm/user/AuthController.java`
- Test: `backend/src/test/java/com/sellm/auth/AuthApiTest.java`

- [ ] **Step 1: DTOs**

`RegisterRequest.java`:
```java
package com.sellm.user.dto;

import com.sellm.security.Role;

public class RegisterRequest {
    private String username;
    private String password;
    private Role role;
    private Long orgId;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public Long getOrgId() { return orgId; }
    public void setOrgId(Long orgId) { this.orgId = orgId; }
}
```

`LoginRequest.java`:
```java
package com.sellm.user.dto;

public class LoginRequest {
    private String username;
    private String password;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
```

`LoginResponse.java`:
```java
package com.sellm.user.dto;

public class LoginResponse {
    private final String token;
    private final String role;

    public LoginResponse(String token, String role) {
        this.token = token;
        this.role = role;
    }

    public String getToken() { return token; }
    public String getRole() { return role; }
}
```

- [ ] **Step 2: AuthController**

`AuthController.java`:
```java
package com.sellm.user;

import com.sellm.common.BusinessException;
import com.sellm.common.ErrorCode;
import com.sellm.common.Result;
import com.sellm.security.JwtService;
import com.sellm.user.dto.LoginRequest;
import com.sellm.user.dto.LoginResponse;
import com.sellm.user.dto.RegisterRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final JwtService jwtService;

    public AuthController(UserRepository userRepository, JwtService jwtService) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
    }

    @PostMapping("/register")
    public Result<Long> register(@RequestBody RegisterRequest req) {
        if (userRepository.findByUsername(req.getUsername()) != null) {
            throw new BusinessException(ErrorCode.SCORING_INVALID_INPUT, "用户名已存在");
        }
        AppUser saved = userRepository.register(
            req.getUsername(), req.getPassword(), req.getRole(), req.getOrgId());
        return Result.ok(saved.getId());
    }

    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody LoginRequest req) {
        AppUser user = userRepository.findByUsername(req.getUsername());
        if (user == null || !userRepository.matches(req.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.SCORING_INVALID_INPUT, "用户名或密码错误");
        }
        String token = jwtService.issue(user.getUsername(), user.getRole().name());
        return Result.ok(new LoginResponse(token, user.getRole().name()));
    }
}
```
注:沿用现有 ErrorCode(SCORING_INVALID_INPUT 作通用输入错误)。若 Task 2 已新增通用 `INVALID_INPUT` 码,这里改用之。

- [ ] **Step 3: 写 MockMvc 测试**

`backend/src/test/java/com/sellm/auth/AuthApiTest.java`:
```java
package com.sellm.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthApiTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void 注册并登录拿到token() throws Exception {
        jdbc.update("DELETE FROM app_user WHERE username = 'api_t1'");

        mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                    "username", "api_t1", "password", "secret123",
                    "role", "TEACHER", "orgId", 1))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("0"));

        mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                    "username", "api_t1", "password", "secret123"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.token").isNotEmpty())
            .andExpect(jsonPath("$.data.role").value("TEACHER"));
    }

    @Test
    void 密码错误登录失败() throws Exception {
        jdbc.update("DELETE FROM app_user WHERE username = 'api_t2'");
        mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                    "username", "api_t2", "password", "right", "role", "TEACHER", "orgId", 1))))
            .andExpect(status().isOk());

        mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                    "username", "api_t2", "password", "wrong"))))
            .andExpect(status().isBadRequest());
    }
}
```

- [ ] **Step 4: 跑测试** — `./mvnw -q test -Dtest=AuthApiTest`，2 PASS。注:`/api/auth/**` 在 SecurityConfig 里 permitAll,登录失败经全局 advice 返回 400。

- [ ] **Step 5: Commit**

```bash
cd "D:/works/test/SELLM" && git add backend/src/main/java/com/sellm/user/ backend/src/test/java/com/sellm/auth/ && git commit -q -m "feat(auth): 注册/登录 REST 端点签发 JWT"
```

---
### Task 7: ChildController(CRUD,受保护,MockMvc)

**Files:**
- Create: `backend/src/main/java/com/sellm/child/dto/ChildRequest.java`
- Create: `backend/src/main/java/com/sellm/child/dto/ChildResponse.java`
- Create: `backend/src/main/java/com/sellm/child/ChildController.java`
- Modify: `backend/src/main/java/com/sellm/child/ChildMapper.java`(加 findAll/update/delete)
- Modify: `backend/src/main/resources/mybatis/ChildMapper.xml`
- Modify: `backend/src/main/java/com/sellm/child/ChildRepository.java`(加 findAll/update/delete)
- Create: `backend/src/test/java/com/sellm/support/AuthTestSupport.java`(测试取 token 辅助)
- Test: `backend/src/test/java/com/sellm/child/ChildApiTest.java`

- [ ] **Step 1: DTOs**

`ChildRequest.java`:
```java
package com.sellm.child.dto;

public class ChildRequest {
    private String name;
    private String disorderType;
    private Long orgId;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDisorderType() { return disorderType; }
    public void setDisorderType(String disorderType) { this.disorderType = disorderType; }
    public Long getOrgId() { return orgId; }
    public void setOrgId(Long orgId) { this.orgId = orgId; }
}
```

`ChildResponse.java`:
```java
package com.sellm.child.dto;

public class ChildResponse {
    private final Long id;
    private final String name;
    private final String disorderType;
    private final Long orgId;

    public ChildResponse(Long id, String name, String disorderType, Long orgId) {
        this.id = id;
        this.name = name;
        this.disorderType = disorderType;
        this.orgId = orgId;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getDisorderType() { return disorderType; }
    public Long getOrgId() { return orgId; }
}
```

- [ ] **Step 2: ChildMapper 加 findAll/update/delete**

在 `ChildMapper.java` 接口加:
```java
    java.util.List<java.util.Map<String, Object>> findAll();
    void update(java.util.Map<String, Object> row);
    void deleteById(@org.apache.ibatis.annotations.Param("id") Long id);
```
在 `ChildMapper.xml` 的 `</mapper>` 前加(沿用 childRowMap):
```xml
    <select id="findAll" resultMap="childRowMap">
        SELECT id, name_enc, disorder_type, org_id FROM child ORDER BY id
    </select>

    <update id="update" parameterType="map">
        UPDATE child SET name_enc = #{nameEnc}, disorder_type = #{disorderType}, org_id = #{orgId}
        WHERE id = #{id}
    </update>

    <delete id="deleteById" parameterType="long">
        DELETE FROM child WHERE id = #{id}
    </delete>
```

- [ ] **Step 3: ChildRepository 加 findAll/update/delete**

在 `ChildRepository.java` 加方法(复用现有 cipher 字段):
```java
    public java.util.List<Child> findAll() {
        java.util.List<Child> list = new java.util.ArrayList<>();
        for (java.util.Map<String, Object> row : mapper.findAll()) {
            Long orgId = row.get("orgId") == null ? null : ((Number) row.get("orgId")).longValue();
            list.add(new Child(((Number) row.get("id")).longValue(),
                cipher.decrypt((String) row.get("nameEnc")),
                (String) row.get("disorderType"), orgId));
        }
        return list;
    }

    public boolean update(Child child) {
        if (mapper.findById(child.getId()) == null) {
            return false;
        }
        java.util.Map<String, Object> row = new java.util.HashMap<>();
        row.put("id", child.getId());
        row.put("nameEnc", cipher.encrypt(child.getName()));
        row.put("disorderType", child.getDisorderType());
        row.put("orgId", child.getOrgId());
        mapper.update(row);
        return true;
    }

    public boolean deleteById(Long id) {
        if (mapper.findById(id) == null) {
            return false;
        }
        mapper.deleteById(id);
        return true;
    }
```

- [ ] **Step 4: ChildController**

`ChildController.java`:
```java
package com.sellm.child;

import com.sellm.child.dto.ChildRequest;
import com.sellm.child.dto.ChildResponse;
import com.sellm.common.BusinessException;
import com.sellm.common.ErrorCode;
import com.sellm.common.Result;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/children")
public class ChildController {

    private final ChildRepository repository;

    public ChildController(ChildRepository repository) {
        this.repository = repository;
    }

    @PostMapping
    public Result<Long> create(@RequestBody ChildRequest req) {
        Child saved = repository.save(new Child(null, req.getName(), req.getDisorderType(), req.getOrgId()));
        return Result.ok(saved.getId());
    }

    @GetMapping("/{id}")
    public Result<ChildResponse> get(@PathVariable Long id) {
        Child c = repository.findById(id);
        if (c == null) {
            throw new BusinessException(ErrorCode.SCORING_INVALID_INPUT, "儿童档案不存在");
        }
        return Result.ok(toResponse(c));
    }

    @GetMapping
    public Result<List<ChildResponse>> list() {
        List<ChildResponse> out = new ArrayList<>();
        for (Child c : repository.findAll()) {
            out.add(toResponse(c));
        }
        return Result.ok(out);
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody ChildRequest req) {
        boolean ok = repository.update(new Child(id, req.getName(), req.getDisorderType(), req.getOrgId()));
        if (!ok) {
            throw new BusinessException(ErrorCode.SCORING_INVALID_INPUT, "儿童档案不存在");
        }
        return Result.ok(null);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        if (!repository.deleteById(id)) {
            throw new BusinessException(ErrorCode.SCORING_INVALID_INPUT, "儿童档案不存在");
        }
        return Result.ok(null);
    }

    private ChildResponse toResponse(Child c) {
        return new ChildResponse(c.getId(), c.getName(), c.getDisorderType(), c.getOrgId());
    }
}
```

- [ ] **Step 5: 测试辅助类(取 token)**

`backend/src/test/java/com/sellm/support/AuthTestSupport.java`:
```java
package com.sellm.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

public final class AuthTestSupport {

    private AuthTestSupport() {}

    /** 注册(忽略已存在)并登录,返回 JWT。 */
    public static String registerAndLogin(MockMvc mvc, ObjectMapper json,
                                          String username, String password, String role) throws Exception {
        mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                    "username", username, "password", password, "role", role, "orgId", 1))));
        String body = mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                    "username", username, "password", password))))
            .andReturn().getResponse().getContentAsString();
        JsonNode node = json.readTree(body);
        return node.path("data").path("token").asText();
    }
}
```

- [ ] **Step 6: 写 ChildApiTest**

`backend/src/test/java/com/sellm/child/ChildApiTest.java`:
```java
package com.sellm.child;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sellm.support.AuthTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ChildApiTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;

    @Test
    void 老师可建档并按id读出明文姓名() throws Exception {
        String token = AuthTestSupport.registerAndLogin(mvc, json, "child_teacher", "pw123456", "TEACHER");

        String createBody = mvc.perform(post("/api/children")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                    "name", "小明", "disorderType", "ASD", "orgId", 1))))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        long id = json.readTree(createBody).path("data").asLong();

        mvc.perform(get("/api/children/" + id)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.name").value("小明"))
            .andExpect(jsonPath("$.data.disorderType").value("ASD"));
    }

    @Test
    void 无token访问受保护端点返回401() throws Exception {
        mvc.perform(get("/api/children"))
            .andExpect(status().isUnauthorized());
    }
}
```

- [ ] **Step 7: 跑测试** — `./mvnw -q test -Dtest=ChildApiTest`，2 PASS。
注:Spring Security 默认未认证返回 401(无 token 时 SecurityContext 无 Authentication → `authenticated()` 拒绝)。若实际返回 403,在 SecurityConfig 加 `.exceptionHandling(e -> e.authenticationEntryPoint((req,res,ex)->res.sendError(401)))` 明确 401——允许此调整。

- [ ] **Step 8: Commit**

```bash
cd "D:/works/test/SELLM" && git add backend/src/main/java/com/sellm/child/ backend/src/main/resources/mybatis/ChildMapper.xml backend/src/test/java/com/sellm/support/ backend/src/test/java/com/sellm/child/ChildApiTest.java && git commit -q -m "feat(child): Child CRUD REST 端点(受 JWT 保护)"
```

---
### Task 8: 评估提交端点 + Assessment 记录落库(MockMvc)

提交评估:给定 childId + scaleId + 各题作答 → 读 Child/Scale → DefaultScoringEngine 计分 → 存 assessment 记录(含总分/分段/解读)→ 返回评估结果。

**Files:**
- Modify: `backend/src/main/resources/schema.sql`(追加 assessment 表)
- Create: `backend/src/main/java/com/sellm/assessment/Assessment.java`
- Create: `backend/src/main/java/com/sellm/assessment/AssessmentMapper.java`
- Create: `backend/src/main/resources/mybatis/AssessmentMapper.xml`
- Create: `backend/src/main/java/com/sellm/assessment/AssessmentRepository.java`
- Create: `backend/src/main/java/com/sellm/assessment/AssessmentAppService.java`
- Create: `backend/src/main/java/com/sellm/assessment/AssessmentController.java`
- Create: `backend/src/main/java/com/sellm/assessment/dto/SubmitAssessmentRequest.java`
- Create: `backend/src/main/java/com/sellm/assessment/dto/AssessmentResponse.java`
- Test: `backend/src/test/java/com/sellm/assessment/AssessmentApiTest.java`

- [ ] **Step 1: schema.sql 追加 assessment 表**

```sql
CREATE TABLE IF NOT EXISTS assessment (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    child_id     BIGINT NOT NULL,
    scale_id     VARCHAR(64) NOT NULL,
    total_score  DOUBLE NOT NULL,
    band_label   VARCHAR(128) NOT NULL,
    interpretation VARCHAR(512),
    created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

- [ ] **Step 2: Assessment 实体**

`Assessment.java`:
```java
package com.sellm.assessment;

public class Assessment {
    private Long id;
    private Long childId;
    private String scaleId;
    private double totalScore;
    private String bandLabel;
    private String interpretation;

    public Assessment() {}

    public Assessment(Long id, Long childId, String scaleId, double totalScore,
                      String bandLabel, String interpretation) {
        this.id = id;
        this.childId = childId;
        this.scaleId = scaleId;
        this.totalScore = totalScore;
        this.bandLabel = bandLabel;
        this.interpretation = interpretation;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getChildId() { return childId; }
    public void setChildId(Long childId) { this.childId = childId; }
    public String getScaleId() { return scaleId; }
    public void setScaleId(String scaleId) { this.scaleId = scaleId; }
    public double getTotalScore() { return totalScore; }
    public void setTotalScore(double totalScore) { this.totalScore = totalScore; }
    public String getBandLabel() { return bandLabel; }
    public void setBandLabel(String bandLabel) { this.bandLabel = bandLabel; }
    public String getInterpretation() { return interpretation; }
    public void setInterpretation(String interpretation) { this.interpretation = interpretation; }
}
```

- [ ] **Step 3: Mapper + XML**

`AssessmentMapper.java`:
```java
package com.sellm.assessment;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.Map;

@Mapper
public interface AssessmentMapper {
    void insert(Map<String, Object> row);
    Map<String, Object> findById(@Param("id") Long id);
}
```

`backend/src/main/resources/mybatis/AssessmentMapper.xml`:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.sellm.assessment.AssessmentMapper">

    <resultMap id="assessmentMap" type="map">
        <id column="id" property="id"/>
        <result column="child_id" property="childId"/>
        <result column="scale_id" property="scaleId"/>
        <result column="total_score" property="totalScore"/>
        <result column="band_label" property="bandLabel"/>
        <result column="interpretation" property="interpretation"/>
    </resultMap>

    <insert id="insert" parameterType="map" useGeneratedKeys="true" keyProperty="id">
        INSERT INTO assessment (child_id, scale_id, total_score, band_label, interpretation)
        VALUES (#{childId}, #{scaleId}, #{totalScore}, #{bandLabel}, #{interpretation})
    </insert>

    <select id="findById" parameterType="long" resultMap="assessmentMap">
        SELECT id, child_id, scale_id, total_score, band_label, interpretation
        FROM assessment WHERE id = #{id}
    </select>

</mapper>
```

- [ ] **Step 4: AssessmentRepository**

`AssessmentRepository.java`:
```java
package com.sellm.assessment;

import org.springframework.stereotype.Repository;
import java.util.HashMap;
import java.util.Map;

@Repository
public class AssessmentRepository {

    private final AssessmentMapper mapper;

    public AssessmentRepository(AssessmentMapper mapper) {
        this.mapper = mapper;
    }

    public Assessment save(Assessment a) {
        Map<String, Object> row = new HashMap<>();
        row.put("childId", a.getChildId());
        row.put("scaleId", a.getScaleId());
        row.put("totalScore", a.getTotalScore());
        row.put("bandLabel", a.getBandLabel());
        row.put("interpretation", a.getInterpretation());
        mapper.insert(row);
        a.setId(((Number) row.get("id")).longValue());
        return a;
    }

    public Assessment findById(Long id) {
        Map<String, Object> row = mapper.findById(id);
        if (row == null) return null;
        return new Assessment(((Number) row.get("id")).longValue(),
            ((Number) row.get("childId")).longValue(),
            (String) row.get("scaleId"),
            ((Number) row.get("totalScore")).doubleValue(),
            (String) row.get("bandLabel"),
            (String) row.get("interpretation"));
    }
}
```

- [ ] **Step 5: DTOs**

`SubmitAssessmentRequest.java`:
```java
package com.sellm.assessment.dto;

import java.util.List;

public class SubmitAssessmentRequest {
    private Long childId;
    private String scaleId;
    private List<AnswerDto> answers;

    public Long getChildId() { return childId; }
    public void setChildId(Long childId) { this.childId = childId; }
    public String getScaleId() { return scaleId; }
    public void setScaleId(String scaleId) { this.scaleId = scaleId; }
    public List<AnswerDto> getAnswers() { return answers; }
    public void setAnswers(List<AnswerDto> answers) { this.answers = answers; }

    public static class AnswerDto {
        private String itemId;
        private double score;
        public String getItemId() { return itemId; }
        public void setItemId(String itemId) { this.itemId = itemId; }
        public double getScore() { return score; }
        public void setScore(double score) { this.score = score; }
    }
}
```

`AssessmentResponse.java`:
```java
package com.sellm.assessment.dto;

public class AssessmentResponse {
    private final Long id;
    private final double totalScore;
    private final String bandLabel;
    private final String interpretation;

    public AssessmentResponse(Long id, double totalScore, String bandLabel, String interpretation) {
        this.id = id;
        this.totalScore = totalScore;
        this.bandLabel = bandLabel;
        this.interpretation = interpretation;
    }

    public Long getId() { return id; }
    public double getTotalScore() { return totalScore; }
    public String getBandLabel() { return bandLabel; }
    public String getInterpretation() { return interpretation; }
}
```

- [ ] **Step 6: AssessmentAppService(编排:读 Child/Scale→计分→存)**

`AssessmentAppService.java`:
```java
package com.sellm.assessment;

import com.sellm.child.Child;
import com.sellm.child.ChildRepository;
import com.sellm.common.BusinessException;
import com.sellm.common.ErrorCode;
import com.sellm.scale.*;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class AssessmentAppService {

    private final ChildRepository childRepository;
    private final ScaleRepository scaleRepository;
    private final ScoringEngine scoringEngine;
    private final AssessmentRepository assessmentRepository;

    public AssessmentAppService(ChildRepository childRepository, ScaleRepository scaleRepository,
                                ScoringEngine scoringEngine, AssessmentRepository assessmentRepository) {
        this.childRepository = childRepository;
        this.scaleRepository = scaleRepository;
        this.scoringEngine = scoringEngine;
        this.assessmentRepository = assessmentRepository;
    }

    public Assessment submit(Long childId, String scaleId, List<Answer> answers) {
        Child child = childRepository.findById(childId);
        if (child == null) {
            throw new BusinessException(ErrorCode.SCORING_INVALID_INPUT, "儿童档案不存在");
        }
        Scale scale = scaleRepository.findById(scaleId);
        if (scale == null) {
            throw new BusinessException(ErrorCode.SCORING_INVALID_INPUT, "量表不存在");
        }
        AssessmentResult result = scoringEngine.score(scale, answers); // 校验失败抛 ScoringException
        return assessmentRepository.save(new Assessment(null, childId, scaleId,
            result.getTotalScore(), result.getBandLabel(), result.getInterpretation()));
    }
}
```
注:`scoringEngine.score` 在作答不全/规则缺失时抛 `ScoringException`(RuntimeException)。在 GlobalExceptionHandler 加一个对 `com.sellm.scale.ScoringException` 的处理(转 400 + SCORING_INVALID_INPUT)——本任务在 GlobalExceptionHandler 补这个 @ExceptionHandler(允许的小改),否则会变 500。

- [ ] **Step 7: 在 GlobalExceptionHandler 加 ScoringException 处理**

在 `GlobalExceptionHandler.java` 加:
```java
    @ExceptionHandler(com.sellm.scale.ScoringException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleScoring(com.sellm.scale.ScoringException e) {
        return Result.error(ErrorCode.SCORING_INVALID_INPUT);
    }
```

- [ ] **Step 8: AssessmentController**

`AssessmentController.java`:
```java
package com.sellm.assessment;

import com.sellm.assessment.dto.AssessmentResponse;
import com.sellm.assessment.dto.SubmitAssessmentRequest;
import com.sellm.common.Result;
import com.sellm.scale.Answer;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/assessments")
public class AssessmentController {

    private final AssessmentAppService appService;

    public AssessmentController(AssessmentAppService appService) {
        this.appService = appService;
    }

    @PostMapping
    public Result<AssessmentResponse> submit(@RequestBody SubmitAssessmentRequest req) {
        List<Answer> answers = new ArrayList<>();
        if (req.getAnswers() != null) {
            for (SubmitAssessmentRequest.AnswerDto a : req.getAnswers()) {
                answers.add(new Answer(a.getItemId(), a.getScore()));
            }
        }
        Assessment saved = appService.submit(req.getChildId(), req.getScaleId(), answers);
        return Result.ok(new AssessmentResponse(saved.getId(), saved.getTotalScore(),
            saved.getBandLabel(), saved.getInterpretation()));
    }
}
```

- [ ] **Step 9: 写 MockMvc 测试**

`backend/src/test/java/com/sellm/assessment/AssessmentApiTest.java`:
```java
package com.sellm.assessment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sellm.support.AuthTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AssessmentApiTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void seedScale() {
        jdbc.update("DELETE FROM score_band");
        jdbc.update("DELETE FROM scale_item");
        jdbc.update("DELETE FROM scale");
        jdbc.update("INSERT INTO scale(scale_id,name,version) VALUES ('cars','CARS','v1')");
        jdbc.update("INSERT INTO scale_item(scale_id,item_id,stem,dimension) VALUES ('cars','q1','社交','社交')");
        jdbc.update("INSERT INTO scale_item(scale_id,item_id,stem,dimension) VALUES ('cars','q2','沟通','沟通')");
        jdbc.update("INSERT INTO score_band(scale_id,lower_bound,upper_bound,label,interpretation) VALUES ('cars',0,3,'正常','未见明显异常')");
        jdbc.update("INSERT INTO score_band(scale_id,lower_bound,upper_bound,label,interpretation) VALUES ('cars',4,7,'轻-中度','建议进一步评估')");
    }

    @Test
    void 老师提交评估得到计分结果且落库() throws Exception {
        String token = AuthTestSupport.registerAndLogin(mvc, json, "asm_teacher", "pw123456", "TEACHER");
        // 建档
        String cb = mvc.perform(post("/api/children").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("name","小明","disorderType","ASD","orgId",1))))
            .andReturn().getResponse().getContentAsString();
        long childId = json.readTree(cb).path("data").asLong();

        mvc.perform(post("/api/assessments").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                    "childId", childId, "scaleId", "cars",
                    "answers", List.of(Map.of("itemId","q1","score",2),
                                       Map.of("itemId","q2","score",3))))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.bandLabel").value("轻-中度"))
            .andExpect(jsonPath("$.data.id").isNumber());
    }

    @Test
    void 家长提交评估被拒403() throws Exception {
        String token = AuthTestSupport.registerAndLogin(mvc, json, "asm_parent", "pw123456", "PARENT");
        mvc.perform(post("/api/assessments").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                    "childId", 1, "scaleId", "cars",
                    "answers", List.of(Map.of("itemId","q1","score",2))))))
            .andExpect(status().isForbidden());
    }
}
```

- [ ] **Step 10: 跑测试** — `./mvnw -q test -Dtest=AssessmentApiTest`，2 PASS。家长被 RBAC 拒(403)。

- [ ] **Step 11: Commit**

```bash
cd "D:/works/test/SELLM" && git add backend/src/main/resources/schema.sql backend/src/main/java/com/sellm/assessment/ backend/src/main/resources/mybatis/AssessmentMapper.xml backend/src/main/java/com/sellm/common/GlobalExceptionHandler.java backend/src/test/java/com/sellm/assessment/ && git commit -q -m "feat(assessment): 评估提交端点,计分并落库(RBAC 限老师/管理者)"
```

---
### Task 9: 报告生成端点 + Report 记录落库(MockMvc)

基于已存的 assessment 生成报告草稿(复用计划一 ReportService.generateDraft,内部经 AiGateway 脱敏),存 report 记录(状态 DRAFT),并支持定稿(PUT)。

**Files:**
- Modify: `backend/src/main/resources/schema.sql`(追加 report 表)
- Create: `backend/src/main/java/com/sellm/report/ReportRecord.java`
- Create: `backend/src/main/java/com/sellm/report/ReportRecordMapper.java`
- Create: `backend/src/main/resources/mybatis/ReportRecordMapper.xml`
- Create: `backend/src/main/java/com/sellm/report/ReportRecordRepository.java`
- Create: `backend/src/main/java/com/sellm/report/ReportAppService.java`
- Create: `backend/src/main/java/com/sellm/report/ReportController.java`
- Create: `backend/src/main/java/com/sellm/report/dto/GenerateReportRequest.java`
- Create: `backend/src/main/java/com/sellm/report/dto/FinalizeRequest.java`
- Create: `backend/src/main/java/com/sellm/report/dto/ReportResponse.java`
- Test: `backend/src/test/java/com/sellm/report/ReportApiTest.java`

- [ ] **Step 1: schema.sql 追加 report 表**

```sql
CREATE TABLE IF NOT EXISTS report (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    assessment_id BIGINT NOT NULL,
    child_id      BIGINT NOT NULL,
    draft         VARCHAR(8000) NOT NULL,
    finalized_content VARCHAR(8000),
    status        VARCHAR(16) NOT NULL,
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

- [ ] **Step 2: ReportRecord 实体**

`ReportRecord.java`:
```java
package com.sellm.report;

public class ReportRecord {
    private Long id;
    private Long assessmentId;
    private Long childId;
    private String draft;
    private String finalizedContent;
    private String status;   // DRAFT / FINALIZED

    public ReportRecord() {}

    public ReportRecord(Long id, Long assessmentId, Long childId, String draft,
                        String finalizedContent, String status) {
        this.id = id;
        this.assessmentId = assessmentId;
        this.childId = childId;
        this.draft = draft;
        this.finalizedContent = finalizedContent;
        this.status = status;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getAssessmentId() { return assessmentId; }
    public void setAssessmentId(Long assessmentId) { this.assessmentId = assessmentId; }
    public Long getChildId() { return childId; }
    public void setChildId(Long childId) { this.childId = childId; }
    public String getDraft() { return draft; }
    public void setDraft(String draft) { this.draft = draft; }
    public String getFinalizedContent() { return finalizedContent; }
    public void setFinalizedContent(String finalizedContent) { this.finalizedContent = finalizedContent; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
```

- [ ] **Step 3: Mapper + XML**

`ReportRecordMapper.java`:
```java
package com.sellm.report;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.Map;

@Mapper
public interface ReportRecordMapper {
    void insert(Map<String, Object> row);
    Map<String, Object> findById(@Param("id") Long id);
    void updateFinalized(@Param("id") Long id, @Param("content") String content);
}
```

`backend/src/main/resources/mybatis/ReportRecordMapper.xml`:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.sellm.report.ReportRecordMapper">

    <resultMap id="reportMap" type="map">
        <id column="id" property="id"/>
        <result column="assessment_id" property="assessmentId"/>
        <result column="child_id" property="childId"/>
        <result column="draft" property="draft"/>
        <result column="finalized_content" property="finalizedContent"/>
        <result column="status" property="status"/>
    </resultMap>

    <insert id="insert" parameterType="map" useGeneratedKeys="true" keyProperty="id">
        INSERT INTO report (assessment_id, child_id, draft, finalized_content, status)
        VALUES (#{assessmentId}, #{childId}, #{draft}, #{finalizedContent}, #{status})
    </insert>

    <select id="findById" parameterType="long" resultMap="reportMap">
        SELECT id, assessment_id, child_id, draft, finalized_content, status
        FROM report WHERE id = #{id}
    </select>

    <update id="updateFinalized">
        UPDATE report SET finalized_content = #{content}, status = 'FINALIZED' WHERE id = #{id}
    </update>

</mapper>
```

- [ ] **Step 4: ReportRecordRepository**

`ReportRecordRepository.java`:
```java
package com.sellm.report;

import org.springframework.stereotype.Repository;
import java.util.HashMap;
import java.util.Map;

@Repository
public class ReportRecordRepository {

    private final ReportRecordMapper mapper;

    public ReportRecordRepository(ReportRecordMapper mapper) {
        this.mapper = mapper;
    }

    public ReportRecord save(ReportRecord r) {
        Map<String, Object> row = new HashMap<>();
        row.put("assessmentId", r.getAssessmentId());
        row.put("childId", r.getChildId());
        row.put("draft", r.getDraft());
        row.put("finalizedContent", r.getFinalizedContent());
        row.put("status", r.getStatus());
        mapper.insert(row);
        r.setId(((Number) row.get("id")).longValue());
        return r;
    }

    public ReportRecord findById(Long id) {
        Map<String, Object> row = mapper.findById(id);
        if (row == null) return null;
        return new ReportRecord(((Number) row.get("id")).longValue(),
            ((Number) row.get("assessmentId")).longValue(),
            ((Number) row.get("childId")).longValue(),
            (String) row.get("draft"), (String) row.get("finalizedContent"),
            (String) row.get("status"));
    }

    public boolean finalizeReport(Long id, String content) {
        if (mapper.findById(id) == null) return false;
        mapper.updateFinalized(id, content);
        return true;
    }
}
```

- [ ] **Step 5: DTOs**

`GenerateReportRequest.java`:
```java
package com.sellm.report.dto;

public class GenerateReportRequest {
    private Long assessmentId;
    public Long getAssessmentId() { return assessmentId; }
    public void setAssessmentId(Long assessmentId) { this.assessmentId = assessmentId; }
}
```

`FinalizeRequest.java`:
```java
package com.sellm.report.dto;

public class FinalizeRequest {
    private String content;
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}
```

`ReportResponse.java`:
```java
package com.sellm.report.dto;

public class ReportResponse {
    private final Long id;
    private final String draft;
    private final String finalizedContent;
    private final String status;

    public ReportResponse(Long id, String draft, String finalizedContent, String status) {
        this.id = id;
        this.draft = draft;
        this.finalizedContent = finalizedContent;
        this.status = status;
    }

    public Long getId() { return id; }
    public String getDraft() { return draft; }
    public String getFinalizedContent() { return finalizedContent; }
    public String getStatus() { return status; }
}
```

- [ ] **Step 6: ReportAppService(读 assessment+child→调 ReportService→存记录)**

`ReportAppService.java`:
```java
package com.sellm.report;

import com.sellm.assessment.Assessment;
import com.sellm.assessment.AssessmentRepository;
import com.sellm.child.Child;
import com.sellm.child.ChildRepository;
import com.sellm.common.BusinessException;
import com.sellm.common.ErrorCode;
import com.sellm.scale.AssessmentResult;
import org.springframework.stereotype.Service;

@Service
public class ReportAppService {

    private final AssessmentRepository assessmentRepository;
    private final ChildRepository childRepository;
    private final ReportService reportService;          // 计划一领域服务(RAG+AI)
    private final ReportRecordRepository recordRepository;

    public ReportAppService(AssessmentRepository assessmentRepository, ChildRepository childRepository,
                            ReportService reportService, ReportRecordRepository recordRepository) {
        this.assessmentRepository = assessmentRepository;
        this.childRepository = childRepository;
        this.reportService = reportService;
        this.recordRepository = recordRepository;
    }

    public ReportRecord generate(Long assessmentId) {
        Assessment a = assessmentRepository.findById(assessmentId);
        if (a == null) {
            throw new BusinessException(ErrorCode.SCORING_INVALID_INPUT, "评估记录不存在");
        }
        Child child = childRepository.findById(a.getChildId());
        if (child == null) {
            throw new BusinessException(ErrorCode.SCORING_INVALID_INPUT, "儿童档案不存在");
        }
        // 用领域服务生成草稿(childName 作脱敏项;schoolName 第一版用占位,后续接机构名)
        AssessmentResult result = new AssessmentResult(
            a.getTotalScore(), a.getBandLabel(), a.getInterpretation());
        Report domain = reportService.generateDraft(
            child.getName(), "学校", a.getScaleId(), result);
        // domain.getDraft() 已还原明文,落库
        return recordRepository.save(new ReportRecord(null, assessmentId, a.getChildId(),
            domain.getDraft(), null, "DRAFT"));
    }

    public ReportRecord finalizeReport(Long reportId, String content) {
        if (!recordRepository.finalizeReport(reportId, content)) {
            throw new BusinessException(ErrorCode.SCORING_INVALID_INPUT, "报告不存在");
        }
        return recordRepository.findById(reportId);
    }
}
```
注:`new AssessmentResult(...)` 需确认计划一 AssessmentResult 有 `(double,String,String)` 公开构造器(计划一 Task4 已定义,有)。`Report` 是计划一 com.sellm.report.Report(领域对象,有 getDraft)。

- [ ] **Step 7: ReportController**

`ReportController.java`:
```java
package com.sellm.report;

import com.sellm.common.Result;
import com.sellm.report.dto.FinalizeRequest;
import com.sellm.report.dto.GenerateReportRequest;
import com.sellm.report.dto.ReportResponse;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportAppService appService;

    public ReportController(ReportAppService appService) {
        this.appService = appService;
    }

    @PostMapping
    public Result<ReportResponse> generate(@RequestBody GenerateReportRequest req) {
        ReportRecord r = appService.generate(req.getAssessmentId());
        return Result.ok(toResponse(r));
    }

    @GetMapping("/{id}")
    public Result<ReportResponse> get(@PathVariable Long id) {
        ReportRecord r = appService.finalizeReportOrGet(id);
        return Result.ok(toResponse(r));
    }

    @PutMapping("/{id}/finalize")
    public Result<ReportResponse> finalizeReport(@PathVariable Long id, @RequestBody FinalizeRequest req) {
        ReportRecord r = appService.finalizeReport(id, req.getContent());
        return Result.ok(toResponse(r));
    }

    private ReportResponse toResponse(ReportRecord r) {
        return new ReportResponse(r.getId(), r.getDraft(), r.getFinalizedContent(), r.getStatus());
    }
}
```
注:上面 GET 用到 `appService.finalizeReportOrGet` 是笔误——GET 应是只读查询。请在 ReportAppService 加一个 `public ReportRecord get(Long id)`(查不到抛 BusinessException),controller 的 GET 调 `appService.get(id)`。实现时按此修正(不要照抄笔误)。

- [ ] **Step 8: 写 MockMvc 测试**

`backend/src/test/java/com/sellm/report/ReportApiTest.java`:
```java
package com.sellm.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sellm.support.AuthTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReportApiTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void seedScale() {
        jdbc.update("DELETE FROM score_band");
        jdbc.update("DELETE FROM scale_item");
        jdbc.update("DELETE FROM scale");
        jdbc.update("INSERT INTO scale(scale_id,name,version) VALUES ('cars','CARS','v1')");
        jdbc.update("INSERT INTO scale_item(scale_id,item_id,stem,dimension) VALUES ('cars','q1','社交','社交')");
        jdbc.update("INSERT INTO scale_item(scale_id,item_id,stem,dimension) VALUES ('cars','q2','沟通','沟通')");
        jdbc.update("INSERT INTO score_band(scale_id,lower_bound,upper_bound,label,interpretation) VALUES ('cars',0,3,'正常','未见明显异常')");
        jdbc.update("INSERT INTO score_band(scale_id,lower_bound,upper_bound,label,interpretation) VALUES ('cars',4,7,'轻-中度','建议进一步评估')");
    }

    @Test
    void 生成报告草稿并定稿() throws Exception {
        String token = AuthTestSupport.registerAndLogin(mvc, json, "rep_teacher", "pw123456", "TEACHER");
        long childId = createChild(token, "小明");
        long assessmentId = submitAssessment(token, childId);

        // 生成报告草稿
        String rb = mvc.perform(post("/api/reports").header("Authorization","Bearer "+token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("assessmentId", assessmentId))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("DRAFT"))
            .andExpect(jsonPath("$.data.draft").isNotEmpty())
            .andReturn().getResponse().getContentAsString();
        long reportId = json.readTree(rb).path("data").path("id").asLong();

        // 定稿
        mvc.perform(put("/api/reports/" + reportId + "/finalize").header("Authorization","Bearer "+token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("content", "老师定稿内容"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("FINALIZED"))
            .andExpect(jsonPath("$.data.finalizedContent").value("老师定稿内容"));
    }

    private long createChild(String token, String name) throws Exception {
        String cb = mvc.perform(post("/api/children").header("Authorization","Bearer "+token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("name",name,"disorderType","ASD","orgId",1))))
            .andReturn().getResponse().getContentAsString();
        return json.readTree(cb).path("data").asLong();
    }

    private long submitAssessment(String token, long childId) throws Exception {
        String ab = mvc.perform(post("/api/assessments").header("Authorization","Bearer "+token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("childId",childId,"scaleId","cars",
                    "answers", List.of(Map.of("itemId","q1","score",2), Map.of("itemId","q2","score",3))))))
            .andReturn().getResponse().getContentAsString();
        return json.readTree(ab).path("data").path("id").asLong();
    }
}
```

- [ ] **Step 9: 跑测试** — `./mvnw -q test -Dtest=ReportApiTest`，1 PASS(用默认 MockAiModel,draft 非空)。

- [ ] **Step 10: Commit**

```bash
cd "D:/works/test/SELLM" && git add backend/src/main/resources/schema.sql backend/src/main/java/com/sellm/report/ backend/src/main/resources/mybatis/ReportRecordMapper.xml backend/src/test/java/com/sellm/report/ReportApiTest.java && git commit -q -m "feat(report): 报告生成/定稿 REST 端点,记录落库"
```

---
### Task 10: IEP 生成端点 + Iep 记录落库(MockMvc)

结构镜像 Task 9:基于已定稿(或已生成)的报告生成 IEP 草案(复用计划一 IepService.generateDraft),存 iep 记录(DRAFT),支持定稿。

**Files:**
- Modify: `backend/src/main/resources/schema.sql`(追加 iep 表)
- Create: `backend/src/main/java/com/sellm/iep/IepRecord.java`
- Create: `backend/src/main/java/com/sellm/iep/IepRecordMapper.java`
- Create: `backend/src/main/resources/mybatis/IepRecordMapper.xml`
- Create: `backend/src/main/java/com/sellm/iep/IepRecordRepository.java`
- Create: `backend/src/main/java/com/sellm/iep/IepAppService.java`
- Create: `backend/src/main/java/com/sellm/iep/IepController.java`
- Create: `backend/src/main/java/com/sellm/iep/dto/GenerateIepRequest.java`
- Create: `backend/src/main/java/com/sellm/iep/dto/IepFinalizeRequest.java`
- Create: `backend/src/main/java/com/sellm/iep/dto/IepResponse.java`
- Test: `backend/src/test/java/com/sellm/iep/IepApiTest.java`

- [ ] **Step 1: schema.sql 追加 iep 表**

```sql
CREATE TABLE IF NOT EXISTS iep (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    report_id     BIGINT NOT NULL,
    child_id      BIGINT NOT NULL,
    draft         VARCHAR(8000) NOT NULL,
    finalized_content VARCHAR(8000),
    status        VARCHAR(16) NOT NULL,
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

- [ ] **Step 2: IepRecord 实体**(字段同 ReportRecord,把 assessmentId 换成 reportId)

`IepRecord.java`:
```java
package com.sellm.iep;

public class IepRecord {
    private Long id;
    private Long reportId;
    private Long childId;
    private String draft;
    private String finalizedContent;
    private String status;

    public IepRecord() {}

    public IepRecord(Long id, Long reportId, Long childId, String draft,
                     String finalizedContent, String status) {
        this.id = id;
        this.reportId = reportId;
        this.childId = childId;
        this.draft = draft;
        this.finalizedContent = finalizedContent;
        this.status = status;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getReportId() { return reportId; }
    public void setReportId(Long reportId) { this.reportId = reportId; }
    public Long getChildId() { return childId; }
    public void setChildId(Long childId) { this.childId = childId; }
    public String getDraft() { return draft; }
    public void setDraft(String draft) { this.draft = draft; }
    public String getFinalizedContent() { return finalizedContent; }
    public void setFinalizedContent(String finalizedContent) { this.finalizedContent = finalizedContent; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
```

- [ ] **Step 3: Mapper + XML**

`IepRecordMapper.java`:
```java
package com.sellm.iep;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.Map;

@Mapper
public interface IepRecordMapper {
    void insert(Map<String, Object> row);
    Map<String, Object> findById(@Param("id") Long id);
    void updateFinalized(@Param("id") Long id, @Param("content") String content);
}
```

`backend/src/main/resources/mybatis/IepRecordMapper.xml`:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.sellm.iep.IepRecordMapper">

    <resultMap id="iepMap" type="map">
        <id column="id" property="id"/>
        <result column="report_id" property="reportId"/>
        <result column="child_id" property="childId"/>
        <result column="draft" property="draft"/>
        <result column="finalized_content" property="finalizedContent"/>
        <result column="status" property="status"/>
    </resultMap>

    <insert id="insert" parameterType="map" useGeneratedKeys="true" keyProperty="id">
        INSERT INTO iep (report_id, child_id, draft, finalized_content, status)
        VALUES (#{reportId}, #{childId}, #{draft}, #{finalizedContent}, #{status})
    </insert>

    <select id="findById" parameterType="long" resultMap="iepMap">
        SELECT id, report_id, child_id, draft, finalized_content, status
        FROM iep WHERE id = #{id}
    </select>

    <update id="updateFinalized">
        UPDATE iep SET finalized_content = #{content}, status = 'FINALIZED' WHERE id = #{id}
    </update>

</mapper>
```

- [ ] **Step 4: IepRecordRepository**(镜像 ReportRecordRepository)

`IepRecordRepository.java`:
```java
package com.sellm.iep;

import org.springframework.stereotype.Repository;
import java.util.HashMap;
import java.util.Map;

@Repository
public class IepRecordRepository {

    private final IepRecordMapper mapper;

    public IepRecordRepository(IepRecordMapper mapper) {
        this.mapper = mapper;
    }

    public IepRecord save(IepRecord r) {
        Map<String, Object> row = new HashMap<>();
        row.put("reportId", r.getReportId());
        row.put("childId", r.getChildId());
        row.put("draft", r.getDraft());
        row.put("finalizedContent", r.getFinalizedContent());
        row.put("status", r.getStatus());
        mapper.insert(row);
        r.setId(((Number) row.get("id")).longValue());
        return r;
    }

    public IepRecord findById(Long id) {
        Map<String, Object> row = mapper.findById(id);
        if (row == null) return null;
        return new IepRecord(((Number) row.get("id")).longValue(),
            ((Number) row.get("reportId")).longValue(),
            ((Number) row.get("childId")).longValue(),
            (String) row.get("draft"), (String) row.get("finalizedContent"),
            (String) row.get("status"));
    }

    public boolean finalizePlan(Long id, String content) {
        if (mapper.findById(id) == null) return false;
        mapper.updateFinalized(id, content);
        return true;
    }
}
```

- [ ] **Step 5: DTOs**

`GenerateIepRequest.java`:
```java
package com.sellm.iep.dto;

public class GenerateIepRequest {
    private Long reportId;
    public Long getReportId() { return reportId; }
    public void setReportId(Long reportId) { this.reportId = reportId; }
}
```

`IepFinalizeRequest.java`:
```java
package com.sellm.iep.dto;

public class IepFinalizeRequest {
    private String content;
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}
```

`IepResponse.java`:
```java
package com.sellm.iep.dto;

public class IepResponse {
    private final Long id;
    private final String draft;
    private final String finalizedContent;
    private final String status;

    public IepResponse(Long id, String draft, String finalizedContent, String status) {
        this.id = id;
        this.draft = draft;
        this.finalizedContent = finalizedContent;
        this.status = status;
    }

    public Long getId() { return id; }
    public String getDraft() { return draft; }
    public String getFinalizedContent() { return finalizedContent; }
    public String getStatus() { return status; }
}
```

- [ ] **Step 6: IepAppService(读 report+child→调 IepService→存)**

`IepAppService.java`:
```java
package com.sellm.iep;

import com.sellm.child.Child;
import com.sellm.child.ChildRepository;
import com.sellm.common.BusinessException;
import com.sellm.common.ErrorCode;
import com.sellm.report.ReportRecord;
import com.sellm.report.ReportRecordRepository;
import org.springframework.stereotype.Service;

@Service
public class IepAppService {

    private final ReportRecordRepository reportRepository;
    private final ChildRepository childRepository;
    private final IepService iepService;            // 计划一领域服务
    private final IepRecordRepository recordRepository;

    public IepAppService(ReportRecordRepository reportRepository, ChildRepository childRepository,
                         IepService iepService, IepRecordRepository recordRepository) {
        this.reportRepository = reportRepository;
        this.childRepository = childRepository;
        this.iepService = iepService;
        this.recordRepository = recordRepository;
    }

    public IepRecord generate(Long reportId) {
        ReportRecord report = reportRepository.findById(reportId);
        if (report == null) {
            throw new BusinessException(ErrorCode.SCORING_INVALID_INPUT, "报告不存在");
        }
        Child child = childRepository.findById(report.getChildId());
        if (child == null) {
            throw new BusinessException(ErrorCode.SCORING_INVALID_INPUT, "儿童档案不存在");
        }
        // 评估结论:优先用定稿内容,否则用草稿
        String conclusion = report.getFinalizedContent() != null
            ? report.getFinalizedContent() : report.getDraft();
        Iep domain = iepService.generateDraft(child.getName(), "学校", conclusion);
        return recordRepository.save(new IepRecord(null, reportId, report.getChildId(),
            domain.getDraft(), null, "DRAFT"));
    }

    public IepRecord get(Long id) {
        IepRecord r = recordRepository.findById(id);
        if (r == null) {
            throw new BusinessException(ErrorCode.SCORING_INVALID_INPUT, "IEP 不存在");
        }
        return r;
    }

    public IepRecord finalizePlan(Long id, String content) {
        if (!recordRepository.finalizePlan(id, content)) {
            throw new BusinessException(ErrorCode.SCORING_INVALID_INPUT, "IEP 不存在");
        }
        return recordRepository.findById(id);
    }
}
```

- [ ] **Step 7: IepController**

`IepController.java`:
```java
package com.sellm.iep;

import com.sellm.common.Result;
import com.sellm.iep.dto.GenerateIepRequest;
import com.sellm.iep.dto.IepFinalizeRequest;
import com.sellm.iep.dto.IepResponse;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ieps")
public class IepController {

    private final IepAppService appService;

    public IepController(IepAppService appService) {
        this.appService = appService;
    }

    @PostMapping
    public Result<IepResponse> generate(@RequestBody GenerateIepRequest req) {
        return Result.ok(toResponse(appService.generate(req.getReportId())));
    }

    @GetMapping("/{id}")
    public Result<IepResponse> get(@PathVariable Long id) {
        return Result.ok(toResponse(appService.get(id)));
    }

    @PutMapping("/{id}/finalize")
    public Result<IepResponse> finalizePlan(@PathVariable Long id, @RequestBody IepFinalizeRequest req) {
        return Result.ok(toResponse(appService.finalizePlan(id, req.getContent())));
    }

    private IepResponse toResponse(IepRecord r) {
        return new IepResponse(r.getId(), r.getDraft(), r.getFinalizedContent(), r.getStatus());
    }
}
```

- [ ] **Step 8: 写 MockMvc 测试**

`backend/src/test/java/com/sellm/iep/IepApiTest.java`(建档→评估→报告→生成 IEP→定稿;沿用 Task9 的 seed 与辅助方法写法):
```java
package com.sellm.iep;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sellm.support.AuthTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IepApiTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void seedScale() {
        jdbc.update("DELETE FROM score_band");
        jdbc.update("DELETE FROM scale_item");
        jdbc.update("DELETE FROM scale");
        jdbc.update("INSERT INTO scale(scale_id,name,version) VALUES ('cars','CARS','v1')");
        jdbc.update("INSERT INTO scale_item(scale_id,item_id,stem,dimension) VALUES ('cars','q1','社交','社交')");
        jdbc.update("INSERT INTO scale_item(scale_id,item_id,stem,dimension) VALUES ('cars','q2','沟通','沟通')");
        jdbc.update("INSERT INTO score_band(scale_id,lower_bound,upper_bound,label,interpretation) VALUES ('cars',0,3,'正常','未见明显异常')");
        jdbc.update("INSERT INTO score_band(scale_id,lower_bound,upper_bound,label,interpretation) VALUES ('cars',4,7,'轻-中度','建议进一步评估')");
    }

    @Test
    void 基于报告生成IEP草案并定稿() throws Exception {
        String token = AuthTestSupport.registerAndLogin(mvc, json, "iep_teacher", "pw123456", "TEACHER");
        long childId = post1("/api/children", token, Map.of("name","小明","disorderType","ASD","orgId",1), "$.data");
        long assessmentId = post1("/api/assessments", token, Map.of("childId",childId,"scaleId","cars",
            "answers", List.of(Map.of("itemId","q1","score",2), Map.of("itemId","q2","score",3))), "$.data.id");
        long reportId = post1("/api/reports", token, Map.of("assessmentId", assessmentId), "$.data.id");

        String ib = mvc.perform(post("/api/ieps").header("Authorization","Bearer "+token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("reportId", reportId))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("DRAFT"))
            .andExpect(jsonPath("$.data.draft").isNotEmpty())
            .andReturn().getResponse().getContentAsString();
        long iepId = json.readTree(ib).path("data").path("id").asLong();

        mvc.perform(put("/api/ieps/" + iepId + "/finalize").header("Authorization","Bearer "+token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("content", "IEP 定稿"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("FINALIZED"));
    }

    // 辅助:POST 后从 JSON 路径取 long(jsonPath 表达式简化为手工解析)
    private long post1(String url, String token, Map<String,Object> body, String path) throws Exception {
        String resp = mvc.perform(post(url).header("Authorization","Bearer "+token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        com.fasterxml.jackson.databind.JsonNode node = json.readTree(resp).path("data");
        return path.endsWith(".id") ? node.path("id").asLong() : node.asLong();
    }
}
```

- [ ] **Step 9: 跑测试** — `./mvnw -q test -Dtest=IepApiTest`，1 PASS。

- [ ] **Step 10: Commit**

```bash
cd "D:/works/test/SELLM" && git add backend/src/main/resources/schema.sql backend/src/main/java/com/sellm/iep/ backend/src/main/resources/mybatis/IepRecordMapper.xml backend/src/test/java/com/sellm/iep/IepApiTest.java && git commit -q -m "feat(iep): IEP 生成/定稿 REST 端点,记录落库"
```

---

### Task 11: 全链路端到端 + 授权矩阵(MockMvc)+ 全量回归

一个测试串起完整 HTTP 流程(登录→建档→评估→报告→IEP),一个测试覆盖授权矩阵(无 token 401、家长写被拒 403、家长读放行)。最后全量回归。

**Files:**
- Test: `backend/src/test/java/com/sellm/flow/FullChainApiTest.java`
- Test: `backend/src/test/java/com/sellm/security/AuthorizationTest.java`

- [ ] **Step 1: 全链路测试**

`backend/src/test/java/com/sellm/flow/FullChainApiTest.java`:
```java
package com.sellm.flow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sellm.support.AuthTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FullChainApiTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void seedScale() {
        jdbc.update("DELETE FROM score_band");
        jdbc.update("DELETE FROM scale_item");
        jdbc.update("DELETE FROM scale");
        jdbc.update("INSERT INTO scale(scale_id,name,version) VALUES ('cars','CARS','v1')");
        jdbc.update("INSERT INTO scale_item(scale_id,item_id,stem,dimension) VALUES ('cars','q1','社交','社交')");
        jdbc.update("INSERT INTO scale_item(scale_id,item_id,stem,dimension) VALUES ('cars','q2','沟通','沟通')");
        jdbc.update("INSERT INTO score_band(scale_id,lower_bound,upper_bound,label,interpretation) VALUES ('cars',0,3,'正常','未见明显异常')");
        jdbc.update("INSERT INTO score_band(scale_id,lower_bound,upper_bound,label,interpretation) VALUES ('cars',4,7,'轻-中度','建议进一步评估')");
    }

    @Test
    void 登录到IEP的完整链路() throws Exception {
        String token = AuthTestSupport.registerAndLogin(mvc, json, "flow_teacher", "pw123456", "TEACHER");

        long childId = dataLong(post("/api/children", token, Map.of("name","小明","disorderType","ASD","orgId",1)), false);
        long assessmentId = dataLong(post("/api/assessments", token, Map.of("childId",childId,"scaleId","cars",
            "answers", List.of(Map.of("itemId","q1","score",2), Map.of("itemId","q2","score",3))), "id"), true);
        long reportId = dataLong(post("/api/reports", token, Map.of("assessmentId", assessmentId)), true);
        long iepId = dataLong(post("/api/ieps", token, Map.of("reportId", reportId)), true);

        // 读出 IEP,确认 DRAFT 且 draft 含原始姓名(经网关脱敏后还原)
        mvc.perform(get("/api/ieps/" + iepId).header("Authorization","Bearer "+token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("DRAFT"))
            .andExpect(jsonPath("$.data.draft").value(org.hamcrest.Matchers.containsString("小明")));
    }

    private String post(String url, String token, Map<String,Object> body) throws Exception {
        return mvc.perform(post(url).header("Authorization","Bearer "+token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
    }

    private long dataLong(String respBody, boolean nestedId) throws Exception {
        JsonNode data = json.readTree(respBody).path("data");
        return nestedId ? data.path("id").asLong() : data.asLong();
    }
}
```

- [ ] **Step 2: 授权矩阵测试**

`backend/src/test/java/com/sellm/security/AuthorizationTest.java`:
```java
package com.sellm.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sellm.support.AuthTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthorizationTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;

    @Test
    void 无token访问受保护端点401() throws Exception {
        mvc.perform(get("/api/children")).andExpect(status().isUnauthorized());
    }

    @Test
    void 家长写child被拒403() throws Exception {
        String parent = AuthTestSupport.registerAndLogin(mvc, json, "authz_parent", "pw123456", "PARENT");
        mvc.perform(post("/api/children").header("Authorization","Bearer "+parent)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("name","x","disorderType","ASD","orgId",1))))
            .andExpect(status().isForbidden());
    }

    @Test
    void 家长读child列表放行200() throws Exception {
        String parent = AuthTestSupport.registerAndLogin(mvc, json, "authz_parent2", "pw123456", "PARENT");
        mvc.perform(get("/api/children").header("Authorization","Bearer "+parent))
            .andExpect(status().isOk());
    }
}
```

- [ ] **Step 3: 跑这两个测试** — `./mvnw -q test -Dtest="FullChainApiTest,AuthorizationTest"`，全 PASS。

- [ ] **Step 4: 全量回归**

Run: `cd "D:/works/test/SELLM/backend" && ./mvnw -q test`
Expected: 全部 PASS,无 Failure/Error。报告实际总数(此前 41 + 本计划各任务新增,粗略 60+)。

- [ ] **Step 5: Commit**

```bash
cd "D:/works/test/SELLM" && git add backend/src/test/java/com/sellm/flow/ backend/src/test/java/com/sellm/security/AuthorizationTest.java && git commit -q -m "test(e2e): 全链路 HTTP 端到端 + 授权矩阵"
```

---

## 后续计划(不在本计划范围)

1. **细粒度数据权限**:家长只能看自己孩子、老师只看本机构;按 orgId/关系做行级过滤(本计划是端点级粗粒度 RBAC)。
2. **业务记录的列表/查询/分页**:各记录的 list、按 child 聚合、分页。
3. **进度追踪与反馈**:ProgressRecord/ProgressTrend、Feedback、AIGenerationLog 回流。
4. **Vue 管理端 / uni-app 小程序家长端**:对接这些 API。
5. **真实 AI 接入 / 真实向量库**:替换 MockAiModel 与 DbRagRetriever 关键词检索。
6. **机构(Organization)实体**:目前 orgId 是裸字段无表;补 organization 表与机构维度。
7. **运行时配置硬化 / 防御式编程 / 计分引擎硬化 / IEP 实体不可变**:延续前两计划记录的待办。

---

## 自检结论

- **Spec/范围覆盖**:认证骨架(User/三角色/JWT/RBAC/BCrypt)、评估→报告→IEP 全链路 REST、业务记录落库(assessment/report/iep)均覆盖。细粒度数据权限、机构表、进度/反馈、前端列入后续。
- **红线衔接**:报告/IEP 仍只产 DRAFT、经 AiGateway 脱敏(复用计划一服务);Child 姓名加密落库经 ChildRepository(API 只见明文 DTO);写操作经端点级 RBAC 限老师/管理者。全链路测试断言 IEP draft 含还原后的"小明",印证脱敏→还原闭环跨 HTTP 仍成立。
- **占位符**:无 TBD/TODO。两处**有意标注的实现提示**已写明正确做法:Task9 ReportController 的 GET 笔误已指明改用 `appService.get(id)`;Task2/8 的 ErrorCode 复用已说明可选新增通用码。实现者须按提示修正,不照抄笔误。
- **类型一致性**:跨任务核对 —— `UserRepository.register/findByUsername/matches`、`JwtService.issue/extractUsername/extractRole/isValid`、`Result.ok/error`、`AssessmentResult(double,String,String)`(复用计划一)、各 AppService→领域 service(ReportService.generateDraft / IepService.generateDraft)签名与计划一一致;记录 Repository 的 save/findById/finalizeXxx 命名一致。
- **测试策略**:MockMvc + H2,端到端发真实 HTTP 过滤链(JWT 解析、RBAC、controller、service、repository、DB),非 mock 空测;授权矩阵覆盖 401/403/200。延续"测试不依赖外部基础设施"。
