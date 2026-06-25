# SELLM 部署方案

> SELLM 特殊教育垂直大模型平台(评估/教学/科研/教具/问答 5 Agent + 网关 + Python 智能层 + 管理端 + 小程序家长端)。
> 本文覆盖开发联调与生产部署。配置项清单见根目录 `.env.example`,各阶段联调记录见 `frontend/INTEGRATION.md`。

## 一、系统组成与端口

| 组件 | 说明 | 端口 |
|------|------|------|
| sellm-gateway | API 网关(JWT 鉴权 + 限流),统一入口 | 8888 |
| backend(agent-assessment) | 评估/报告/IEP/家庭IEP/用户/机构/班级/量表 | 8080 |
| sellm-agent-teaching | 教学训练 Agent(教案/课件) | 8081 |
| sellm-agent-research | 科研助手 Agent(信效度/课题书) | 8082 |
| sellm-agent-aids | 智能教具 Agent(推荐/文生素材) | 8083 |
| sellm-agent-qa | 通用问答 Agent(RAG) | 8084 |
| ai-smart-layer | Python 智能层(LLM/RAG/文生媒体适配器) | 8090 |
| frontend | Vue3 管理端 | 5173(dev)/Nginx(prod) |
| miniprogram | uni-app 小程序家长端(编 mp-weixin) | — |

**基础设施(docker-compose):** MySQL 8(3306)、Redis 7(6379)、Nacos 2.3(8848/9848)、RabbitMQ(5672/15672)、Milvus(19530)、MinIO(9000/9001)。

## 二、技术栈

- 后端:Java 17、Spring Boot 3.2.5、Spring Cloud Gateway、Spring Cloud Alibaba(Nacos)、MyBatis、Maven 多模块
- 数据库:生产 MySQL 8;dev/test H2
- 智能层:Python 3.11、FastAPI(适配器默认 mock 不外联)
- 管理端:Vue 3 + Vite 5 + Element Plus + Pinia
- 小程序:uni-app(Vue3 + Pinia)编译微信端

## 三、AI 隐私三红线(部署须知)

1. **出网必经脱敏,失败硬阻断**:所有外部模型/存储/脱敏服务都是可切换适配器,**默认 mock/noop 不外联**。仅在显式配 provider + key 时才真实出网,合规风险由配置方承担。
2. **AI 只产草案**:报告/IEP/教案等生成 DRAFT,人工 finalize 后冻结,PDF 仅 FINALIZED 可下载。
3. **PII 加密落库**:儿童/家长姓名经 AES-GCM 加密。

<!-- PLACEHOLDER_DEPLOY -->

## 四、开发联调(本地,H2 + Mock,零外联)

最小启动只需 JDK 17 + Maven + Node,无需 docker(dev 用 H2 文件库、各适配器 mock)。

```bash
# 1. 后端各服务(各自目录,dev profile = H2 文件库 + 种子)
#    网关:dev profile 提供开发 JWT 密钥(主 profile 空默认会 fail-fast)
cd sellm-gateway && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
cd backend       && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev   # 8080
cd sellm-agent-qa && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev  # 同理 teaching/research/aids

# 2. 管理端
cd frontend && npm install && npm run dev          # 5173,/api 代理到网关/后端

# 3. 小程序(编微信端)
cd miniprogram && npm install && npm run build:mp-weixin   # 产物 dist/dev/mp-weixin,用微信开发者工具打开

# 4. Python 智能层(可选,不起则 Agent 优雅降级)
cd ai-smart-layer && pip install -r requirements.txt && uvicorn app.main:app --port 8090
```

**dev 默认账号**(DevSeeder 自动种入,仅 dev profile):
- 超管:`admin / admin123`
- 家长:`parent_demo / secret123`(绑定孩子「小明」)

**dev 联调要点:**
- 起网关**必须**带 `-Dspring-boot.run.profiles=dev`(或注入 `SELLM_JWT_SECRET`),否则密钥 fail-fast 拒绝启动。
- dev 默认关 Nacos 注册(`SELLM_NACOS_ENABLED=false`);需验网关 lb 路由时,起 docker nacos 后设 `SELLM_NACOS_ENABLED=true` 并各服务重启。
- 仅验单服务无需 Nacos;走网关全链路才需要。

## 五、生产部署

### 5.1 前置:基础设施

```bash
docker compose -f docker/docker-compose.yml up -d   # mysql/redis/nacos/rabbitmq/milvus/minio
```
> 生产务必覆盖默认弱口令(MYSQL_ROOT_PASSWORD/MINIO_PASS/RABBITMQ_PASS 等)。

### 5.2 建库(DBA)

- backend 库 `asd_assistant`:按 `backend/src/main/resources/schema.sql` 建表。
- **4 个 Agent 各自独立库** `sellm_qa` / `sellm_research` / `sellm_teaching` / `sellm_aids`:按各模块 `schema.sql` 建表。
- ⚠️ **aids 的 schema.sql 用 H2 `MERGE` 语法装中文教具种子,不兼容 MySQL**,需另写一份 MySQL 教具种子迁移脚本。
- 字符集用 `utf8mb4`。

### 5.3 必配环境变量(生产)

| 变量 | 说明 |
|------|------|
| `SELLM_JWT_SECRET` | **必填,≥32 字节**(各服务同密钥;缺失则启动失败) |
| `SELLM_AES_KEY` | **必填,32 字节**(PII 加密) |
| `DB_USER`/`DB_PASSWORD` | backend MySQL |
| `SELLM_{QA,RESEARCH,TEACHING,AIDS}_DB_URL/_USER/_PASSWORD` | 各 Agent 独立库 |
| `SELLM_NACOS_ADDR` | Nacos 地址 |
| `SELLM_REDIS_HOST/PORT` | 网关限流用 |
| `SELLM_PDF_FONT_PATH` | CJK 字体路径(中文 PDF 必需) |

完整清单见 `.env.example`。所有外部模型/存储默认 mock/noop,需真实能力时才配对应 `SELLM_*_PROVIDER` + key。

### 5.4 启动顺序

Nacos/MySQL/Redis 就绪 → 各 Agent(注册 Nacos)→ 网关 → 智能层(可选)→ 管理端(Nginx 托管 `npm run build` 产物,反代 /api 到网关 8888)。

### 5.5 出网能力开关(默认全关)

| 能力 | 开启方式 | 红线 |
|------|---------|------|
| 文本 LLM | `SELLM_AI_PROVIDER=openai` + key | 出网前脱敏 |
| 文生图 | `SELLM_MEDIA_PROVIDER=wanx`(通义万相,base-url=https://dashscope.aliyuncs.com)+ key | prompt 脱敏 |
| 多模态 vision | `SELLM_MULTIMODAL_PROVIDER=openai` + key | 图像 + 文本笔记均脱敏 |
| 对象存储 | `SELLM_MINIO_PROVIDER=minio` + endpoint/key | — |

> 启用任一出网能力即代表已获监护人知情同意、自担合规风险。

## 六、健康检查与运维

- 各服务 `/actuator/health`;Nacos 控制台查服务注册。
- 限流:网关 Redis 令牌桶,`SELLM_RATELIMIT_*` 调参,Redis 异常 fail-open。
- 休眠/换网后网关变慢:查 Nacos 实例 IP 是否失效(开发机常见),重启对应服务重新注册。

## 七、剩余技术债(上线前评估)

actuator 端点鉴权、Nacos `prefer-ip-address`、schema 索引/外键、儿童 narrative 字段加密、Python 智能层鉴权、限流取 X-Forwarded-For 等。详见 `frontend/INTEGRATION.md` 安全加固段。

