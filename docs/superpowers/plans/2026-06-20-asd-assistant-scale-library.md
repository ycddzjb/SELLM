# 阶段 B — 量表库管理 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 超管可在"量表库管理"中维护全品类(8 类障碍)量表的增删改查(量表定义 + 题目 + 计分规则/分段);前端评估页从硬编码 CARS 改为动态加载任意量表进行评分。

**Architecture:** 延续模块化单体。新增 `ScaleController`(REST CRUD, 超管限定);`ScaleRepository` 从只读升级为读写;`scale` 表加品类字段;`scale_item` 加排序/最大分;前端新增量表库管理页(超管)+ 评估页动态化(老师)。

**Tech Stack:** Java 17 / Spring Boot 3.2.5 / MyBatis / H2(test) / Vue 3 + Element Plus。

**Source spec:** `docs/superpowers/specs/2026-06-19-asd-assistant-v2-requirements.md` 第 5 节

---

## 设计决策

1. **Scale 品类(障碍类型)**: `scale` 表加 `disorder_type VARCHAR(32)`(单选,取值对应 DisorderType 枚举);便于按品类分组展示和过滤。加 `description VARCHAR(512)` 简述量表用途。
2. **ScaleItem 排序 + 最大分**: `scale_item` 加 `sort_order INT`(前端按此渲染题目顺序)+ `max_score DOUBLE`(每题最高分,前端 el-rate :max 动态取;默认 4)。
3. **CRUD 粒度**: 一个 Scale 的创建/更新以整体提交(头 + items[] + bands[]),后端事务原子操作——删旧 items/bands → 重插。避免复杂的增量 patch。
4. **ScaleController 权限**: POST/PUT/DELETE 仅 SUPER_ADMIN;GET(列表/详情)放开给所有已登录用户(老师评估时需动态获取量表定义)。
5. **前端动态化**: 评估页先 GET `/api/scales` 列表(按品类展示可选量表)→ 选择后 GET `/api/scales/{id}` 获取题目 → 动态渲染 el-rate 表单(max 取 item.maxScore)→ 提交答题。
6. **兼容性**: 现有种子 CARS 保留(加 disorder_type='ASD', sort_order, max_score);现有评估/计分流程不变(ScaleRepository.findById 返回结构兼容)。

---

## 与现状的关键变更

- `scale` 表加 `disorder_type`、`description` 列(ALTER IF NOT EXISTS)
- `scale_item` 表加 `sort_order`、`max_score` 列(ALTER IF NOT EXISTS)
- `ScaleMapper` 扩展:insert/update/delete scale + items + bands 的语句
- `ScaleRepository` 从只读变读写(save/update/delete + listAll/listByDisorderType)
- 新增 `ScaleController`(REST CRUD)
- 前端: 超管菜单"量表库管理"从禁用占位变实页;评估页从硬编码改为动态加载
- seed-dev.sql CARS 记录补 disorder_type/sort_order/max_score

---

## 文件结构(本计划范围)

```
backend/src/main/
  resources/
    schema.sql                      # scale 加列;scale_item 加列
    seed-dev.sql                    # CARS 补字段
    mybatis/ScaleMapper.xml         # 扩展 CRUD 语句
  java/com/sellm/scale/
    Scale.java                      # 加 disorderType/description 字段
    ScaleItem.java                  # 加 sortOrder/maxScore 字段
    ScaleMapper.java                # 新增写方法接口
    ScaleRepository.java            # 升级为读写
    ScaleController.java            # 新增(REST CRUD)
    dto/ScaleRequest.java           # 新增(创建/更新 DTO)
    dto/ScaleResponse.java          # 新增(列表/详情 DTO)
    dto/ScaleItemDto.java           # 新增(item 子 DTO)
    dto/ScoreBandDto.java           # 新增(band 子 DTO)
  java/com/sellm/security/
    SecurityConfig.java             # /api/scales 授权规则
backend/src/test/
  java/com/sellm/scale/
    ScaleApiTest.java               # 新增(CRUD + 权限测试)
    ScaleRepositoryTest.java        # 扩展(save/update/delete)
  resources/scale-seed.sql          # 补字段
frontend/src/
  api/scales.js                     # 新增
  views/ScaleLibraryView.vue        # 新增(超管量表库管理)
  views/AssessmentView.vue          # 改造(动态加载量表)
  router/index.js                   # /scale-library 路由
  layouts/MainLayout.vue            # 超管菜单从禁用变可点
```

---

### Task 1: Schema 扩字段 + seed 补全(TDD)

scale/scale_item 加列;CARS 种子补 disorder_type/sort_order/max_score。

**Files:** schema.sql, seed-dev.sql, scale-seed.sql(测试)

- [x] **Step 1:** schema.sql 追加 ALTER 语句
```sql
ALTER TABLE scale ADD COLUMN IF NOT EXISTS disorder_type VARCHAR(32);
ALTER TABLE scale ADD COLUMN IF NOT EXISTS description VARCHAR(512);
ALTER TABLE scale_item ADD COLUMN IF NOT EXISTS sort_order INT DEFAULT 0;
ALTER TABLE scale_item ADD COLUMN IF NOT EXISTS max_score DOUBLE DEFAULT 4;
```

- [x] **Step 2:** seed-dev.sql CARS MERGE 补 disorder_type='ASD', description;item 补 sort_order/max_score
- [x] **Step 3:** test scale-seed.sql 同步补字段
- [x] **Step 4:** `./mvnw -q test`(应用 context 加载测试) 全绿
- [x] **Step 5:** Commit

---

### Task 2: Scale / ScaleItem 实体扩字段

Scale 加 disorderType(String)/ description;ScaleItem 加 sortOrder(int)/ maxScore(double)。保留原构造器(兼容现有调用),加全参构造器。

**Files:** Scale.java, ScaleItem.java, ScoreRand.java(不变)

- [x] **Step 1:** Scale.java 加字段 + getter + 新构造器(7 参);旧 5 参委托新构造(disorder/desc=null)
- [x] **Step 2:** ScaleItem.java 加字段 + getter + 新构造器(5 参);旧 3 参委托新构造(sortOrder=0,maxScore=4)
- [x] **Step 3:** ScaleRepository.findById 读时填充新字段(改 mapper query + 组装)
- [x] **Step 4:** ScaleMapper.xml findScaleById 加 disorder_type/description;findItems 加 sort_order/max_score + ORDER BY sort_order
- [x] **Step 5:** 既有测试(ScaleRepositoryTest / DefaultScoringEngineTest)适配 + 全绿
- [x] **Step 6:** Commit

---

### Task 3: ScaleRepository 升级读写 + ScaleMapper 扩 CRUD

ScaleRepository 加 save / update / delete(事务原子:scale + items + bands 一起操作)。ScaleMapper 加 insertScale/insertItem/insertBand/deleteItemsByScale/deleteBandsByScale/deleteScale/findAll/findByDisorderType。

**Files:** ScaleMapper.java, ScaleMapper.xml, ScaleRepository.java

- [x] **Step 1:** ScaleMapper 接口加方法声明(insertScale/updateScale/deleteScale/insertItem/insertBand/deleteItemsByScale/deleteBandsByScale/findAll/findByDisorderType)
- [x] **Step 2:** ScaleMapper.xml 写对应 SQL(INSERT/UPDATE/DELETE)
- [x] **Step 3:** ScaleRepository 实现:
  - `save(Scale)`: insertScale → 遍历 items insertItem → 遍历 bands insertBand。validate disorder_type 合法(DisorderType.valueOf)
  - `update(Scale)`: updateScale头 → deleteItemsByScale → deleteByScale bands → 重插 items/bands
  - `deleteById(scaleId)`: deleteItems → deleteBands → deleteScale
  - `listAll()`: findAll → 组装列表(头信息,不含 items/bands)
  - `listByDisorderType(type)`: findByDisorderType → 组装列表
- [x] **Step 4:** ScaleRepositoryTest 扩展(save→findById 验证全字段 / update / delete / listAll / listByDisorderType)
- [x] **Step 5:** 全量回归绿 → Commit

---

### Task 4: ScaleController REST CRUD(TDD)

超管维护量表;已登录用户可读。DTO 定义 + Controller + SecurityConfig 授权 + 测试。

**Files:** ScaleController.java, dto/ScaleRequest.java, dto/ScaleResponse.java, dto/ScaleItemDto.java, dto/ScoreBandDto.java, SecurityConfig.java, ScaleApiTest.java

- [x] **Step 1:** DTO 定义
  - `ScaleRequest`: scaleId(创建时必填,不可更新), name, version, disorderType, description, items[](ScaleItemDto), bands[](ScoreBandDto)
  - `ScaleItemDto`: itemId, stem, dimension, sortOrder, maxScore
  - `ScoreBandDto`: lowerBound, upperBound, label, interpretation
  - `ScaleResponse`: scaleId, name, version, disorderType, description, items[], bands[]
  - `ScaleListItem`: scaleId, name, version, disorderType, description(列表轻量,不带 items/bands)

- [x] **Step 2:** ScaleController
```
GET    /api/scales                → listAll(可选 ?disorderType=ASD 过滤)
GET    /api/scales/{scaleId}      → 完整详情(含 items/bands)
POST   /api/scales                → 创建(超管)
PUT    /api/scales/{scaleId}      → 更新(超管)
DELETE /api/scales/{scaleId}      → 删除(超管)
```

- [x] **Step 3:** SecurityConfig:
  - GET /api/scales/** → authenticated(评估时老师需读取)
  - POST/PUT/DELETE /api/scales/** → hasRole("SUPER_ADMIN")

- [x] **Step 4:** ScaleApiTest:
  - 超管创建量表(含 items+bands)→ 200;GET 详情含全部 items/bands
  - 超管更新量表(改题目/加题目/改分段)→ 200
  - 超管删除量表 → 200;再 GET → 404/400
  - 列表:按 disorderType 过滤正确
  - MANAGER 调 POST → 403;TEACHER GET → 200
  - scaleId 已存在 → 409
  - 无效 disorderType → 400

- [x] **Step 5:** 全量回归绿 → Commit

---

### Task 5: 前端 — 量表库管理页(超管)

超管 CRUD 量表:列表(按品类分组/筛选)+ 创建/编辑 dialog(量表头 + 动态添加题目行 + 动态添加分段行)+ 删除确认。

**Files:** scales.js, ScaleLibraryView.vue, MainLayout.vue, router/index.js

- [x] **Step 1:** `frontend/src/api/scales.js`
```js
export const listScales = (disorderType) => http.get('/scales', disorderType ? { params: { disorderType } } : {})
export const getScale = (scaleId) => http.get(`/scales/${scaleId}`)
export const createScale = (payload) => http.post('/scales', payload)
export const updateScale = (scaleId, payload) => http.put(`/scales/${scaleId}`, payload)
export const deleteScale = (scaleId) => http.delete(`/scales/${scaleId}`)
```

- [x] **Step 2:** ScaleLibraryView.vue:
  - 顶部:品类筛选(el-select,DISORDER_TYPES + 全部)
  - 列表:el-table(scaleId/名称/版本/品类/题目数/操作)
  - 创建/编辑 dialog:分三区——量表基本信息(scaleId/name/version/disorderType/description)、题目列表(可动态增删行:itemId/stem/dimension/sortOrder/maxScore)、分段列表(可动态增删行:lowerBound/upperBound/label/interpretation)
  - 删除确认 MessageBox

- [x] **Step 3:** MainLayout.vue:超管"量表库管理"菜单从 `disabled` 改为正常可点(index="/scale-library")
- [x] **Step 4:** router/index.js:加 `{ path: 'scale-library', component: () => import('../views/ScaleLibraryView.vue') }`
- [x] **Step 5:** `npm run build` 通过 → Commit

---

### Task 6: 前端 — 评估页动态化(老师)

AssessmentView 从硬编码 CARS 改为:先选量表(从 GET /api/scales 列表选)→ 加载题目(GET /api/scales/{id})→ 动态渲染评分表单(el-rate :max=item.maxScore)→ 提交。

**Files:** AssessmentView.vue, assessments.js(不变)

- [x] **Step 1:** 加量表选择步骤(el-select 或 el-radio-group,选项来自 listScales)
- [x] **Step 2:** 选择后 getScale(scaleId) 获取 items → 动态生成 answers reactive 对象
- [x] **Step 3:** el-rate :max 取 item.maxScore(默认 4);题目按 sortOrder 排序
- [x] **Step 4:** 提交 payload.scaleId 改为动态选择的值
- [x] **Step 5:** 保留"从儿童档案页带入 childId"的 query 逻辑
- [x] **Step 6:** `npm run build` 通过 → Commit

---

### Task 7: 端到端联调

起 dev 后端 + curl:超管建量表(含题目+分段)→ 列表按品类过滤 → 详情 → 更新(加题目)→ 老师取量表定义 → 老师提交评估(新量表)→ 删除量表。记录 INTEGRATION.md。

- [x] **Step 1:** 起 dev 后端
- [x] **Step 2:** curl 链路:
  1. 超管登录 → POST /api/scales(创建一个"感统"量表 3 题 + 2 分段)→ 200
  2. GET /api/scales?disorderType=SENSORY_INTEGRATION → 包含新量表
  3. GET /api/scales/{id} → 含 3 items + 2 bands
  4. PUT /api/scales/{id}(改名 + 加第 4 题)→ 200
  5. 老师登录 → GET /api/scales/{id} → 200(验证 authenticated 可读)
  6. 老师 POST /api/assessments(用新量表 4 题作答)→ 200(计分正确)
  7. 超管 DELETE /api/scales/{id} → 200
  8. MANAGER POST /api/scales → 403
- [x] **Step 3:** 停服务,追加 INTEGRATION.md,提交

---

## 验证清单

1. **后端全量回归**: `./mvnw test` 全绿(现 142 + 新增约 12-15)
2. **前端 build**: `npm run build` 无错误
3. **端到端 curl**: 8 条路径全通
4. **兼容性**: 现有 CARS 评估流程不受影响(ScaleRepositoryTest / AssessmentApiTest 保绿)
5. **安全**: POST/PUT/DELETE scales 仅超管;GET authenticated;评估端点限老师/管理员(不变)

---

## 后续(不在本计划)

- 阶段 C:家长注册改造 + 老师审核
- 阶段 D:儿童档案大扩展
- 阶段 E:真实大模型接入(评估报告 / IEP 生成)
