# ASD 助手 — 计划四:前端管理端 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 搭建 Vue 3 管理端,让老师/管理者通过浏览器真实使用后端 API:登录、儿童建档、提交评估、生成与定稿报告/IEP、管理者建老师账号。后端加 dev profile(H2 文件库 + 种子数据)使其零安装即可 `spring-boot:run`,前端经 Vite 代理与之真实联调。

**Architecture:** 前端独立 `frontend/` 工程(Vue 3 + Vite 5 + Element Plus + Pinia + Vue Router + axios)。axios 统一封装:请求拦截器注入 JWT、响应拦截器解包 `Result`({code,message,data},code="0" 成功否则抛错)。路由守卫按登录态拦截。后端新增 `application-dev.yml`(H2 文件库、MySQL 兼容模式、启动跑 schema.sql + 种子 SQL),`spring-boot:run -Dspring-boot.run.profiles=dev` 起在 8080;Vite devServer 代理 `/api → localhost:8080`。

**Tech Stack:** 前端 Vue 3.4 + Vite 5 + Element Plus 2.x + Pinia 2 + Vue Router 4 + axios 1.x。Node 24(Vite 5 官方推 20/22,实测可用;若个别依赖告警按需处理)。后端复用计划一~三(master),仅加 dev profile 资源 + 种子。

**API 契约(后端 master,前端按此对接):**
- 统一返回 `Result<T>` = `{code:String, message:String, data:T}`,`code==="0"` 成功。失败 HTTP 400/401/403,body 仍是 Result(401/403 当前可能是 Spring 默认体)。
- `POST /api/auth/login` {username,password} → data:{token, role}
- `POST /api/auth/register` {username,password} → data:Long(仅产 PARENT,管理端登录用不到)
- `POST /api/users` {username,password,role}(仅 MANAGER)→ data:Long(新用户 id,orgId 取建者机构)
- `GET /api/children` → data:[{id,name,disorderType,orgId,guardianUserId}](行级过滤后)
- `GET /api/children/{id}` → data:同上单个
- `POST /api/children` {name,disorderType,guardianUserId?} → data:Long(orgId 取建者机构,忽略请求 orgId)
- `PUT /api/children/{id}` {name,disorderType} → data:null
- `DELETE /api/children/{id}` → data:null
- `POST /api/assessments` {childId,scaleId,answers:[{itemId,score}]} → data:{id,totalScore,bandLabel,interpretation}
- `POST /api/reports` {assessmentId} → data:{id,draft,finalizedContent,status}
- `GET /api/reports/{id}` → data:同上;`PUT /api/reports/{id}/finalize` {content} → data:同上(status FINALIZED)
- `POST /api/ieps` {reportId} → data:{id,draft,finalizedContent,status}
- `GET /api/ieps/{id}`;`PUT /api/ieps/{id}/finalize` {content} → data:同上

**已知 API 约束(影响前端设计):**
- 无"列出评估/报告/IEP"或"按 child 查其历史记录"的端点,只能按 id GET 单个。故主链路页面按**工作流式**设计:在一次操作里串起 child→评估(得 assessmentId)→报告(得 reportId)→IEP(得 iepId),前端在会话内持有这些 id。历史浏览列表留后续(需后端补 list 端点)。
- 量表:后端只有按 scaleId 取量表用于计分,无"列出量表/取量表题目"端点。前端第一版**硬编码 CARS 量表的题目结构**(scaleId=cars,q1/q2 两题,与种子一致),用于评估表单渲染。后端补量表查询端点后再改为动态。

---

## 文件结构(本计划范围)

```
backend/
  src/main/resources/
    application-dev.yml        # H2 文件库 dev profile(Task 1)
    seed-dev.sql               # 种子:机构 + MANAGER + CARS 量表(Task 1)
frontend/
  package.json / vite.config.js / index.html / .gitignore
  src/
    main.js                    # 挂载 app + ElementPlus + Pinia + Router
    App.vue
    api/
      http.js                  # axios 实例 + 拦截器(JWT 注入 / Result 解包)
      auth.js / children.js / assessments.js / reports.js / ieps.js / users.js
    stores/
      auth.js                  # Pinia:token/role/登录登出
    router/
      index.js                 # 路由 + 登录守卫
    layouts/
      MainLayout.vue           # 侧边导航 + 顶栏(登出)
    views/
      LoginView.vue
      ChildrenView.vue         # 儿童档案列表 + 新建/编辑/删除
      AssessmentView.vue       # 选童+选量表+答题→提交→得分
      ReportView.vue           # 由评估生成报告草稿→编辑定稿
      IepView.vue              # 由报告生成 IEP 草案→编辑定稿
      UsersView.vue            # MANAGER 建老师/管理者
    components/
       ...(按需小组件)
```

**为什么这样切:** api/ 每个后端资源一个模块,集中管理端点;stores 只放跨页共享的认证态;views 一页一文件,职责单一;layouts 提供统一框架。工作流式页面(评估→报告→IEP)各自独立但可通过路由 query 传递上一步的 id。

---

### Task 1: 后端 dev profile(H2 文件库 + 种子数据)

让后端零安装即可 `spring-boot:run`:dev profile 用 H2 文件库(MySQL 兼容、数据持久化到本地文件)、启动建表、并用一个 `DevSeeder`(@Profile("dev") CommandLineRunner)幂等预置:机构(id=1 阳光小学)、一个 MANAGER 账号(admin/admin123,orgId=1)、CARS 量表三件套。机构与量表用 SQL 种子;MANAGER 用 UserRepository.register 走真实 BCrypt(不在 SQL 写死哈希)。

**Files:**
- Create: `backend/src/main/resources/application-dev.yml`
- Create: `backend/src/main/resources/seed-dev.sql`(机构 + CARS 量表;幂等)
- Create: `backend/src/main/java/com/sellm/config/DevSeeder.java`
- Test: `backend/src/test/java/com/sellm/config/DevSeederSmokeTest.java`(可选轻量,见 Step 4)

- [ ] **Step 1: application-dev.yml**

`backend/src/main/resources/application-dev.yml`:
```yaml
spring:
  datasource:
    url: jdbc:h2:file:./data/asd_dev;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;AUTO_SERVER=TRUE
    username: sa
    password:
    driver-class-name: org.h2.Driver
  sql:
    init:
      mode: always
      schema-locations: classpath:schema.sql
      data-locations: classpath:seed-dev.sql
      continue-on-error: true   # 种子幂等(重复启动时 INSERT 冲突忽略)
  h2:
    console:
      enabled: true             # /h2-console 便于调试(dev only)

mybatis:
  mapper-locations: classpath:mybatis/*.xml
  configuration:
    map-underscore-to-camel-case: true

sellm:
  crypto:
    aes-key: "dev-aes-key-32bytes-0123456789ab"   # 恰 32 字节(dev 用)
  jwt:
    secret: "dev-jwt-secret-key-at-least-32-bytes-long-0123456789"
    expiration-minutes: 240
```
注:H2 文件库落在 `backend/data/`(下一步 gitignore)。`continue-on-error:true` 让幂等种子的重复 INSERT 不致启动失败。**校验 aes-key 恰好 32 字节**(UTF-8):`dev-aes-key-32bytes-0123456789ab` = 32 字符全 ASCII,满足 AesFieldCipher 的 32 字节要求(实现者务必数准,不足则启动失败)。

- [ ] **Step 2: seed-dev.sql(机构 + CARS 量表,幂等)**

`backend/src/main/resources/seed-dev.sql`(H2 用 `MERGE` 实现幂等 upsert;主键已知时 MERGE 比 INSERT 安全):
```sql
-- 机构
MERGE INTO organization (id, name, region) KEY(id) VALUES (1, '阳光小学', '南京');

-- CARS 量表三件套(scale 主键 scale_id;item/band 用固定 id 保证幂等)
MERGE INTO scale (scale_id, name, version) KEY(scale_id) VALUES ('cars', 'CARS', 'v1');
MERGE INTO scale_item (id, scale_id, item_id, stem, dimension) KEY(id)
    VALUES (1, 'cars', 'q1', '与人交往', '社交');
MERGE INTO scale_item (id, scale_id, item_id, stem, dimension) KEY(id)
    VALUES (2, 'cars', 'q2', '语言沟通', '沟通');
MERGE INTO score_band (id, scale_id, lower_bound, upper_bound, label, interpretation) KEY(id)
    VALUES (1, 'cars', 0, 3, '正常', '未见明显异常');
MERGE INTO score_band (id, scale_id, lower_bound, upper_bound, label, interpretation) KEY(id)
    VALUES (2, 'cars', 4, 7, '轻-中度', '建议进一步评估');
```
注:`MERGE ... KEY(...)` 是 H2 的幂等 upsert,重复启动不冲突。MySQL 生产环境用 `INSERT ... ON DUPLICATE KEY`,但 dev 只跑 H2,故用 MERGE。

- [ ] **Step 3: DevSeeder(种子 MANAGER,真实 BCrypt,幂等)**

`backend/src/main/java/com/sellm/config/DevSeeder.java`:
```java
package com.sellm.config;

import com.sellm.security.Role;
import com.sellm.user.AppUser;
import com.sellm.user.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile("dev")
@Component
public class DevSeeder implements CommandLineRunner {

    private final UserRepository userRepository;

    public DevSeeder(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public void run(String... args) {
        // 幂等:已存在则不重复创建
        AppUser existing = userRepository.findByUsername("admin");
        if (existing == null) {
            userRepository.register("admin", "admin123", Role.MANAGER, 1L);
        }
    }
}
```
注:@Profile("dev") 保证只在 dev 启动时跑,不影响测试(test profile)与生产。种子 MANAGER 走 register → BCrypt 哈希,登录可用。机构(orgId=1)由 seed-dev.sql 先建,与该 MANAGER 的 orgId 一致。

- [ ] **Step 4: gitignore + 轻量验证**

把 H2 文件库目录加进 `.gitignore`(根):追加一行 `backend/data/`。
轻量验证 DevSeeder 不破坏上下文(测试用 test profile,DevSeeder 不激活,但要确保它能编译且 @Profile 不误装配)。新建 `backend/src/test/java/com/sellm/config/DevSeederSmokeTest.java`:
```java
package com.sellm.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class DevSeederSmokeTest {

    @Autowired
    private ApplicationContext ctx;

    @Test
    void test_profile_下DevSeeder不装配() {
        // @Profile("dev") → test profile 下不应存在该 bean
        assertThat(ctx.containsBean("devSeeder")).isFalse();
    }
}
```

- [ ] **Step 5: 手动验证后端能 dev 启动(关键,联调前提)**

后台启动:`cd "D:/works/test/SELLM/backend" && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`(首次会建 data/asd_dev H2 文件)。等启动日志出现端口 8080 后,另开终端验证登录:
```bash
curl -s -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" -d '{"username":"admin","password":"admin123"}'
```
Expected: 返回 `{"code":"0",...,"data":{"token":"...","role":"MANAGER"}}`。验证完停掉后端(后续联调时再起)。
注:`spring-boot:run` 是前台阻塞进程,实现时用后台运行(run_in_background)或单独终端;验证完务必停掉,别占着 8080。

- [ ] **Step 6: 全量回归 + 提交**

Run: `cd "D:/works/test/SELLM/backend" && ./mvnw -q test`
Expected: 此前 78 + DevSeederSmokeTest 1 = 79 全绿(dev 资源不影响 test profile)。
```bash
cd "D:/works/test/SELLM" && git add backend/src/main/resources/application-dev.yml backend/src/main/resources/seed-dev.sql backend/src/main/java/com/sellm/config/DevSeeder.java backend/src/test/java/com/sellm/config/DevSeederSmokeTest.java .gitignore && git commit -q -m "feat(dev): 后端 dev profile(H2 文件库 + 种子 MANAGER/机构/CARS)供前端联调"
```

---
### Task 2: 前端脚手架 + axios 封装 + 认证 + 登录页

建 `frontend/` Vite 工程,装依赖,配 Vite 代理,做 axios 拦截器(JWT 注入 + Result 解包)、Pinia 认证 store、路由守卫、登录页 + 主框架布局。这一步完成后能登录进系统。

**Files:**(均在 `frontend/`)
- Create: `package.json`、`vite.config.js`、`index.html`、`.gitignore`
- Create: `src/main.js`、`src/App.vue`
- Create: `src/api/http.js`、`src/api/auth.js`
- Create: `src/stores/auth.js`
- Create: `src/router/index.js`
- Create: `src/layouts/MainLayout.vue`
- Create: `src/views/LoginView.vue`、`src/views/HomeView.vue`(占位首页)

- [ ] **Step 1: 初始化工程与依赖**

在 `D:/works/test/SELLM` 下创建 `frontend/`,写 `package.json`:
```json
{
  "name": "asd-assistant-admin",
  "private": true,
  "version": "0.1.0",
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "vite build",
    "preview": "vite preview"
  },
  "dependencies": {
    "axios": "^1.7.7",
    "element-plus": "^2.8.4",
    "pinia": "^2.2.4",
    "vue": "^3.5.12",
    "vue-router": "^4.4.5"
  },
  "devDependencies": {
    "@vitejs/plugin-vue": "^5.1.4",
    "vite": "^5.4.9"
  }
}
```
然后安装:`cd "D:/works/test/SELLM/frontend" && npm install`(联网装依赖,首次较慢)。

- [ ] **Step 2: vite.config.js(含 /api 代理)**

`frontend/vite.config.js`:
```js
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
```

- [ ] **Step 3: index.html + .gitignore**

`frontend/index.html`:
```html
<!DOCTYPE html>
<html lang="zh-CN">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>特教评估助手 · 管理端</title>
  </head>
  <body>
    <div id="app"></div>
    <script type="module" src="/src/main.js"></script>
  </body>
</html>
```
`frontend/.gitignore`:
```
node_modules/
dist/
*.local
.vite/
```

- [ ] **Step 4: axios 封装 http.js(JWT 注入 + Result 解包)**

`frontend/src/api/http.js`:
```js
import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'
import router from '../router'

const http = axios.create({ baseURL: '/api', timeout: 30000 })

// 请求拦截:注入 JWT
http.interceptors.request.use((config) => {
  const auth = useAuthStore()
  if (auth.token) {
    config.headers.Authorization = `Bearer ${auth.token}`
  }
  return config
})

// 响应拦截:解包 Result,code!=="0" 抛错;401→跳登录
http.interceptors.response.use(
  (resp) => {
    const body = resp.data
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code === '0') {
        return body.data
      }
      ElMessage.error(body.message || '请求失败')
      return Promise.reject(new Error(body.message || 'business error'))
    }
    return body
  },
  (error) => {
    const status = error.response?.status
    if (status === 401) {
      const auth = useAuthStore()
      auth.logout()
      router.push('/login')
      ElMessage.error('登录已过期,请重新登录')
    } else if (status === 403) {
      ElMessage.error('无权访问该资源')
    } else {
      const msg = error.response?.data?.message || error.message || '网络错误'
      ElMessage.error(msg)
    }
    return Promise.reject(error)
  }
)

export default http
```

- [ ] **Step 5: Pinia 认证 store**

`frontend/src/stores/auth.js`:
```js
import { defineStore } from 'pinia'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem('token') || '',
    role: localStorage.getItem('role') || ''
  }),
  getters: {
    isLoggedIn: (s) => !!s.token,
    isManager: (s) => s.role === 'MANAGER'
  },
  actions: {
    setAuth(token, role) {
      this.token = token
      this.role = role
      localStorage.setItem('token', token)
      localStorage.setItem('role', role)
    },
    logout() {
      this.token = ''
      this.role = ''
      localStorage.removeItem('token')
      localStorage.removeItem('role')
    }
  }
})
```

- [ ] **Step 6: auth API**

`frontend/src/api/auth.js`:
```js
import http from './http'

export function login(username, password) {
  return http.post('/auth/login', { username, password })
}
```

- [ ] **Step 7: 路由 + 登录守卫**

`frontend/src/router/index.js`:
```js
import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const routes = [
  { path: '/login', component: () => import('../views/LoginView.vue') },
  {
    path: '/',
    component: () => import('../layouts/MainLayout.vue'),
    children: [
      { path: '', redirect: '/children' },
      { path: 'children', component: () => import('../views/ChildrenView.vue') },
      { path: 'assessment', component: () => import('../views/AssessmentView.vue') },
      { path: 'report', component: () => import('../views/ReportView.vue') },
      { path: 'iep', component: () => import('../views/IepView.vue') },
      { path: 'users', component: () => import('../views/UsersView.vue') }
    ]
  }
]

const router = createRouter({ history: createWebHistory(), routes })

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.path !== '/login' && !auth.isLoggedIn) {
    return '/login'
  }
})

export default router
```
注:ChildrenView/AssessmentView/ReportView/IepView/UsersView 在后续任务创建;本任务先建占位空组件使路由可加载(或本任务只建 children 占位,其余 Task 创建时补)。**为避免本任务路由加载报错,本步同时创建这 5 个 view 的最小占位**(`<template><div>占位</div></template>`),后续任务替换为真实实现。

- [ ] **Step 8: main.js + App.vue + 占位 views + 布局 + 登录页**

`frontend/src/main.js`:
```js
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import App from './App.vue'
import router from './router'

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.use(ElementPlus)
app.mount('#app')
```
注意 main.js 里 pinia 必须在 router 之前 use(http.js/router 守卫用到 store)。若顺序导致 store 在 pinia 未装时被调用,axios 的 http.js 已是在请求时才 useAuthStore(),没问题;router 守卫同理在导航时调用。

`frontend/src/App.vue`:
```vue
<template>
  <router-view />
</template>
```

`frontend/src/layouts/MainLayout.vue`:
```vue
<template>
  <el-container style="height: 100vh">
    <el-aside width="200px">
      <el-menu :default-active="$route.path" router>
        <el-menu-item index="/children">儿童档案</el-menu-item>
        <el-menu-item index="/assessment">评估</el-menu-item>
        <el-menu-item index="/report">报告</el-menu-item>
        <el-menu-item index="/iep">IEP</el-menu-item>
        <el-menu-item v-if="auth.isManager" index="/users">用户管理</el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header style="display:flex;align-items:center;justify-content:space-between">
        <span>特殊教育评估助手 · 管理端</span>
        <el-button size="small" @click="onLogout">退出登录({{ auth.role }})</el-button>
      </el-header>
      <el-main>
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { useAuthStore } from '../stores/auth'
import { useRouter } from 'vue-router'
const auth = useAuthStore()
const router = useRouter()
function onLogout() {
  auth.logout()
  router.push('/login')
}
</script>
```

`frontend/src/views/LoginView.vue`:
```vue
<template>
  <div style="display:flex;align-items:center;justify-content:center;height:100vh;background:#f0f2f5">
    <el-card style="width:360px">
      <h2 style="text-align:center;margin-bottom:20px">特教评估助手 · 登录</h2>
      <el-form @submit.prevent="onSubmit">
        <el-form-item>
          <el-input v-model="username" placeholder="用户名" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="password" type="password" placeholder="密码" show-password />
        </el-form-item>
        <el-button type="primary" style="width:100%" :loading="loading" @click="onSubmit">登录</el-button>
      </el-form>
      <p style="color:#999;font-size:12px;margin-top:12px">dev 种子账号:admin / admin123</p>
    </el-card>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login } from '../api/auth'
import { useAuthStore } from '../stores/auth'

const username = ref('')
const password = ref('')
const loading = ref(false)
const router = useRouter()
const auth = useAuthStore()

async function onSubmit() {
  if (!username.value || !password.value) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    const data = await login(username.value, password.value)
    auth.setAuth(data.token, data.role)
    ElMessage.success('登录成功')
    router.push('/children')
  } catch (e) {
    // http 拦截器已弹错
  } finally {
    loading.value = false
  }
}
</script>
```

5 个占位 view(`ChildrenView.vue`/`AssessmentView.vue`/`ReportView.vue`/`IepView.vue`/`UsersView.vue`),每个内容先放:
```vue
<template><div>页面建设中</div></template>
```

- [ ] **Step 9: 构建验证**

Run: `cd "D:/works/test/SELLM/frontend" && npm run build`
Expected: 构建成功(vite build 通过,产出 dist/)。这验证所有组件/路由/依赖能正确编译。Node 24 下若 Vite 报引擎告警(非错误)可忽略;若真报错,记录现象。

- [ ] **Step 10: Commit**

```bash
cd "D:/works/test/SELLM" && git add frontend/ && git commit -q -m "feat(frontend): Vue3 脚手架 + axios 封装 + 认证 + 登录页"
```
注:`frontend/.gitignore` 已排除 node_modules,提交不含依赖。

---
### Task 3: 儿童档案页(列表 + 新建/编辑/删除)

**Files:**
- Create: `frontend/src/api/children.js`
- Replace: `frontend/src/views/ChildrenView.vue`(占位 → 真实)

- [ ] **Step 1: children API**

`frontend/src/api/children.js`:
```js
import http from './http'

export const listChildren = () => http.get('/children')
export const getChild = (id) => http.get(`/children/${id}`)
export const createChild = (payload) => http.post('/children', payload)
export const updateChild = (id, payload) => http.put(`/children/${id}`, payload)
export const deleteChild = (id) => http.delete(`/children/${id}`)
```

- [ ] **Step 2: ChildrenView.vue**

`frontend/src/views/ChildrenView.vue`:
```vue
<template>
  <div>
    <div style="margin-bottom:16px;display:flex;justify-content:space-between;align-items:center">
      <h3>儿童档案</h3>
      <el-button type="primary" @click="openCreate">新建档案</el-button>
    </div>
    <el-table :data="rows" v-loading="loading" border>
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="name" label="姓名" />
      <el-table-column prop="disorderType" label="障碍类型" />
      <el-table-column prop="guardianUserId" label="监护人(家长ID)" />
      <el-table-column label="操作" width="280">
        <template #default="{ row }">
          <el-button size="small" @click="goAssessment(row)">评估</el-button>
          <el-button size="small" @click="openEdit(row)">编辑</el-button>
          <el-button size="small" type="danger" @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑档案' : '新建档案'" width="420px">
      <el-form label-width="90px">
        <el-form-item label="姓名">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="障碍类型">
          <el-input v-model="form.disorderType" placeholder="如 ASD" />
        </el-form-item>
        <el-form-item v-if="!editing" label="家长账号ID">
          <el-input v-model="form.guardianUserId" placeholder="可空;家长用户的 id" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="onSubmit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listChildren, createChild, updateChild, deleteChild } from '../api/children'

const rows = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const editing = ref(false)
const editId = ref(null)
const form = reactive({ name: '', disorderType: '', guardianUserId: '' })
const router = useRouter()

async function load() {
  loading.value = true
  try {
    rows.value = await listChildren()
  } finally {
    loading.value = false
  }
}
onMounted(load)

function openCreate() {
  editing.value = false
  editId.value = null
  form.name = ''
  form.disorderType = ''
  form.guardianUserId = ''
  dialogVisible.value = true
}
function openEdit(row) {
  editing.value = true
  editId.value = row.id
  form.name = row.name
  form.disorderType = row.disorderType
  dialogVisible.value = true
}
async function onSubmit() {
  if (!form.name || !form.disorderType) {
    ElMessage.warning('请填写姓名和障碍类型')
    return
  }
  try {
    if (editing.value) {
      await updateChild(editId.value, { name: form.name, disorderType: form.disorderType })
    } else {
      const payload = { name: form.name, disorderType: form.disorderType }
      if (form.guardianUserId) payload.guardianUserId = Number(form.guardianUserId)
      await createChild(payload)
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    await load()
  } catch (e) { /* 拦截器已提示 */ }
}
async function onDelete(row) {
  try {
    await ElMessageBox.confirm(`确认删除 ${row.name} 的档案?`, '提示', { type: 'warning' })
  } catch { return }
  try {
    await deleteChild(row.id)
    ElMessage.success('已删除')
    await load()
  } catch (e) { /* 拦截器已提示 */ }
}
function goAssessment(row) {
  router.push({ path: '/assessment', query: { childId: row.id, childName: row.name } })
}
</script>
```

- [ ] **Step 3: 构建验证 + 提交**

Run: `cd "D:/works/test/SELLM/frontend" && npm run build`,Expected 构建成功。
```bash
cd "D:/works/test/SELLM" && git add frontend/src/api/children.js frontend/src/views/ChildrenView.vue && git commit -q -m "feat(frontend): 儿童档案页(CRUD)"
```

---

### Task 4: 评估页(选童 + 答 CARS + 提交得分)

**Files:**
- Create: `frontend/src/api/assessments.js`
- Replace: `frontend/src/views/AssessmentView.vue`

- [ ] **Step 1: assessments API**

`frontend/src/api/assessments.js`:
```js
import http from './http'

export const submitAssessment = (payload) => http.post('/assessments', payload)
```

- [ ] **Step 2: AssessmentView.vue**

第一版前端硬编码 CARS 量表题目(与种子一致:scaleId=cars,q1/q2),0-4 分。提交后展示得分,并提供"基于此评估生成报告"跳转。
`frontend/src/views/AssessmentView.vue`:
```vue
<template>
  <div>
    <h3>评估(CARS 量表)</h3>
    <el-form label-width="120px" style="max-width:640px">
      <el-form-item label="儿童ID">
        <el-input v-model="childId" placeholder="从儿童档案页点'评估'带入,或手填" />
      </el-form-item>
      <el-divider>量表作答(0-4 分)</el-divider>
      <el-form-item v-for="item in items" :key="item.itemId" :label="item.stem">
        <el-rate v-model="answers[item.itemId]" :max="4" show-score />
      </el-form-item>
      <el-button type="primary" :loading="loading" @click="onSubmit">提交评估</el-button>
    </el-form>

    <el-card v-if="result" style="margin-top:20px;max-width:640px">
      <h4>评估结果</h4>
      <p>总分:{{ result.totalScore }}</p>
      <p>分段:{{ result.bandLabel }}</p>
      <p>解读:{{ result.interpretation }}</p>
      <el-button type="success" @click="goReport">基于此评估生成报告</el-button>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { submitAssessment } from '../api/assessments'

// 第一版硬编码 CARS 题目(与后端种子 scaleId=cars 的 q1/q2 一致)
const items = [
  { itemId: 'q1', stem: '与人交往' },
  { itemId: 'q2', stem: '语言沟通' }
]
const route = useRoute()
const router = useRouter()
const childId = ref(route.query.childId || '')
const answers = reactive({ q1: 0, q2: 0 })
const loading = ref(false)
const result = ref(null)

async function onSubmit() {
  if (!childId.value) {
    ElMessage.warning('请填写儿童ID')
    return
  }
  loading.value = true
  try {
    const payload = {
      childId: Number(childId.value),
      scaleId: 'cars',
      answers: items.map((it) => ({ itemId: it.itemId, score: answers[it.itemId] }))
    }
    result.value = await submitAssessment(payload)
    ElMessage.success('评估已提交')
  } catch (e) { /* 拦截器已提示 */ } finally {
    loading.value = false
  }
}
function goReport() {
  router.push({ path: '/report', query: { assessmentId: result.value.id } })
}
</script>
```

- [ ] **Step 3: 构建验证 + 提交**

Run: `cd "D:/works/test/SELLM/frontend" && npm run build`,Expected 成功。
```bash
cd "D:/works/test/SELLM" && git add frontend/src/api/assessments.js frontend/src/views/AssessmentView.vue && git commit -q -m "feat(frontend): 评估页(CARS 答题→提交→得分)"
```

---
### Task 5: 报告页 + IEP 页(生成草稿 → 编辑 → 定稿)

报告与 IEP 结构同构:由上一步 id(assessmentId / reportId)生成草稿(DRAFT),展示草稿,老师编辑后定稿(FINALIZED)。报告定稿后可跳去生成 IEP。

**Files:**
- Create: `frontend/src/api/reports.js`、`frontend/src/api/ieps.js`
- Replace: `frontend/src/views/ReportView.vue`、`frontend/src/views/IepView.vue`

- [ ] **Step 1: reports / ieps API**

`frontend/src/api/reports.js`:
```js
import http from './http'

export const generateReport = (assessmentId) => http.post('/reports', { assessmentId })
export const getReport = (id) => http.get(`/reports/${id}`)
export const finalizeReport = (id, content) => http.put(`/reports/${id}/finalize`, { content })
```

`frontend/src/api/ieps.js`:
```js
import http from './http'

export const generateIep = (reportId) => http.post('/ieps', { reportId })
export const getIep = (id) => http.get(`/ieps/${id}`)
export const finalizeIep = (id, content) => http.put(`/ieps/${id}/finalize`, { content })
```

- [ ] **Step 2: ReportView.vue**

`frontend/src/views/ReportView.vue`:
```vue
<template>
  <div>
    <h3>评估报告</h3>
    <el-form label-width="120px" style="max-width:760px">
      <el-form-item label="评估ID">
        <el-input v-model="assessmentId" placeholder="从评估页带入,或手填">
          <template #append>
            <el-button :loading="genLoading" @click="onGenerate">生成报告草稿</el-button>
          </template>
        </el-input>
      </el-form-item>
    </el-form>

    <el-card v-if="report" style="margin-top:16px;max-width:760px">
      <div style="display:flex;justify-content:space-between;align-items:center">
        <span>报告 #{{ report.id }}</span>
        <el-tag :type="report.status === 'FINALIZED' ? 'success' : 'info'">{{ report.status }}</el-tag>
      </div>
      <el-divider>AI 草稿(仅供参考,需人工把关)</el-divider>
      <pre style="white-space:pre-wrap;background:#f7f7f7;padding:12px;border-radius:4px">{{ report.draft }}</pre>
      <el-divider>定稿内容</el-divider>
      <el-input v-model="finalContent" type="textarea" :rows="6"
                placeholder="在 AI 草稿基础上修改为最终报告" />
      <div style="margin-top:12px">
        <el-button type="primary" :loading="finLoading" @click="onFinalize">定稿</el-button>
        <el-button v-if="report.status === 'FINALIZED'" type="success" @click="goIep">基于此报告生成 IEP</el-button>
      </div>
      <p v-if="report.finalizedContent" style="margin-top:12px;color:#666">
        已定稿内容:{{ report.finalizedContent }}
      </p>
    </el-card>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { generateReport, finalizeReport } from '../api/reports'

const route = useRoute()
const router = useRouter()
const assessmentId = ref(route.query.assessmentId || '')
const report = ref(null)
const finalContent = ref('')
const genLoading = ref(false)
const finLoading = ref(false)

async function onGenerate() {
  if (!assessmentId.value) { ElMessage.warning('请填写评估ID'); return }
  genLoading.value = true
  try {
    report.value = await generateReport(Number(assessmentId.value))
    finalContent.value = report.value.draft  // 预填草稿供编辑
    ElMessage.success('已生成草稿')
  } catch (e) {} finally { genLoading.value = false }
}
async function onFinalize() {
  if (!report.value) return
  if (!finalContent.value) { ElMessage.warning('定稿内容不能为空'); return }
  finLoading.value = true
  try {
    report.value = await finalizeReport(report.value.id, finalContent.value)
    ElMessage.success('已定稿')
  } catch (e) {} finally { finLoading.value = false }
}
function goIep() {
  router.push({ path: '/iep', query: { reportId: report.value.id } })
}
</script>
```

- [ ] **Step 3: IepView.vue**

`frontend/src/views/IepView.vue`(结构同 Report,把 assessmentId→reportId、reports API→ieps API、文案改 IEP;定稿后无下一步跳转):
```vue
<template>
  <div>
    <h3>个别化教育计划(IEP)</h3>
    <el-form label-width="120px" style="max-width:760px">
      <el-form-item label="报告ID">
        <el-input v-model="reportId" placeholder="从报告页带入,或手填">
          <template #append>
            <el-button :loading="genLoading" @click="onGenerate">生成 IEP 草案</el-button>
          </template>
        </el-input>
      </el-form-item>
    </el-form>

    <el-card v-if="iep" style="margin-top:16px;max-width:760px">
      <div style="display:flex;justify-content:space-between;align-items:center">
        <span>IEP #{{ iep.id }}</span>
        <el-tag :type="iep.status === 'FINALIZED' ? 'success' : 'info'">{{ iep.status }}</el-tag>
      </div>
      <el-divider>AI 草案(仅供参考,需人工把关)</el-divider>
      <pre style="white-space:pre-wrap;background:#f7f7f7;padding:12px;border-radius:4px">{{ iep.draft }}</pre>
      <el-divider>定稿内容</el-divider>
      <el-input v-model="finalContent" type="textarea" :rows="6"
                placeholder="在 AI 草案基础上修改为最终 IEP" />
      <div style="margin-top:12px">
        <el-button type="primary" :loading="finLoading" @click="onFinalize">定稿</el-button>
      </div>
      <p v-if="iep.finalizedContent" style="margin-top:12px;color:#666">
        已定稿内容:{{ iep.finalizedContent }}
      </p>
    </el-card>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { generateIep, finalizeIep } from '../api/ieps'

const route = useRoute()
const reportId = ref(route.query.reportId || '')
const iep = ref(null)
const finalContent = ref('')
const genLoading = ref(false)
const finLoading = ref(false)

async function onGenerate() {
  if (!reportId.value) { ElMessage.warning('请填写报告ID'); return }
  genLoading.value = true
  try {
    iep.value = await generateIep(Number(reportId.value))
    finalContent.value = iep.value.draft
    ElMessage.success('已生成草案')
  } catch (e) {} finally { genLoading.value = false }
}
async function onFinalize() {
  if (!iep.value) return
  if (!finalContent.value) { ElMessage.warning('定稿内容不能为空'); return }
  finLoading.value = true
  try {
    iep.value = await finalizeIep(iep.value.id, finalContent.value)
    ElMessage.success('已定稿')
  } catch (e) {} finally { finLoading.value = false }
}
</script>
```

- [ ] **Step 4: 构建验证 + 提交**

Run: `cd "D:/works/test/SELLM/frontend" && npm run build`,Expected 成功。
```bash
cd "D:/works/test/SELLM" && git add frontend/src/api/reports.js frontend/src/api/ieps.js frontend/src/views/ReportView.vue frontend/src/views/IepView.vue && git commit -q -m "feat(frontend): 报告页与 IEP 页(生成草稿→编辑→定稿)"
```

---

### Task 6: 用户管理页(MANAGER 建老师/管理者)

**Files:**
- Create: `frontend/src/api/users.js`
- Replace: `frontend/src/views/UsersView.vue`

- [ ] **Step 1: users API**

`frontend/src/api/users.js`:
```js
import http from './http'

export const createUser = (payload) => http.post('/users', payload)
```

- [ ] **Step 2: UsersView.vue**

仅 MANAGER 可见(MainLayout 菜单已按 isManager 过滤;此页再防御一次)。建账号:username/password/role(TEACHER/MANAGER/PARENT);orgId 后端强制取建者机构,前端不传。
`frontend/src/views/UsersView.vue`:
```vue
<template>
  <div>
    <h3>用户管理(创建老师/管理者)</h3>
    <el-alert v-if="!auth.isManager" type="warning" :closable="false"
              title="仅管理者可创建账号" style="margin-bottom:16px" />
    <el-form label-width="100px" style="max-width:420px" :disabled="!auth.isManager">
      <el-form-item label="用户名">
        <el-input v-model="form.username" />
      </el-form-item>
      <el-form-item label="密码">
        <el-input v-model="form.password" type="password" show-password />
      </el-form-item>
      <el-form-item label="角色">
        <el-select v-model="form.role" placeholder="选择角色">
          <el-option label="老师/康复师 (TEACHER)" value="TEACHER" />
          <el-option label="管理者 (MANAGER)" value="MANAGER" />
          <el-option label="家长 (PARENT)" value="PARENT" />
        </el-select>
      </el-form-item>
      <el-button type="primary" :loading="loading" @click="onSubmit">创建账号</el-button>
    </el-form>
    <p style="color:#999;font-size:12px;margin-top:12px">
      新账号自动归属你所在机构。创建的老师/管理者可用其用户名密码登录。
    </p>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { createUser } from '../api/users'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const form = reactive({ username: '', password: '', role: 'TEACHER' })
const loading = ref(false)

async function onSubmit() {
  if (!form.username || !form.password || !form.role) {
    ElMessage.warning('请填写完整')
    return
  }
  loading.value = true
  try {
    const id = await createUser({ ...form })
    ElMessage.success(`账号创建成功(id=${id})`)
    form.username = ''
    form.password = ''
  } catch (e) {} finally { loading.value = false }
}
</script>
```

- [ ] **Step 3: 构建验证 + 提交**

Run: `cd "D:/works/test/SELLM/frontend" && npm run build`,Expected 成功。
```bash
cd "D:/works/test/SELLM" && git add frontend/src/api/users.js frontend/src/views/UsersView.vue && git commit -q -m "feat(frontend): 用户管理页(MANAGER 建老师/管理者)"
```

---

### Task 7: 端到端联调验证(前后端真实连通)

不写新代码,做真实联调:起 dev 后端 + 前端 dev server,用种子 admin 走完整流程,确认前后端契约连通、JWT/行级权限/主链路在浏览器侧真实可用。产出一份联调结果记录。

- [ ] **Step 1: 起后端(dev profile,后台)**

`cd "D:/works/test/SELLM/backend" && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`(后台运行 run_in_background)。等日志出现 Tomcat started on port 8080。

- [ ] **Step 2: 起前端 dev server(后台)**

`cd "D:/works/test/SELLM/frontend" && npm run dev`(后台运行)。等输出 Local: http://localhost:5173。

- [ ] **Step 3: 用 curl 走完整链路验证后端契约(经真实 HTTP,不经浏览器)**

按序执行(每步取上一步返回的 id/token):
```bash
# 登录拿 token
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" -d '{"username":"admin","password":"admin123"}' | grep -o '"token":"[^"]*"' | sed 's/"token":"//;s/"//')
echo "TOKEN=$TOKEN"
# 建档(admin 是 MANAGER,orgId=1)
curl -s -X POST http://localhost:8080/api/children -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"name":"小明","disorderType":"ASD"}'
# 列档案(应含小明,明文姓名)
curl -s http://localhost:8080/api/children -H "Authorization: Bearer $TOKEN"
# 提交评估(childId 用上一步返回的)、生成报告、生成 IEP —— 依次验证
```
Expected:登录得 token;建档返回 id;列表返回明文"小明";评估返回 bandLabel;报告/IEP 返回 DRAFT 草稿。**这验证了 dev 后端 + 全链路 + JWT + 加密落库读回明文在真实运行时成立。**

- [ ] **Step 4: 浏览器侧人工核对(描述预期,供执行者确认)**

打开 http://localhost:5173 → 用 admin/admin123 登录 → 儿童档案页能看到/新建档案 → 点"评估"带入 childId,答题提交得分 → "生成报告"→ 草稿展示、编辑、定稿 → "生成 IEP"→ 草案、定稿 → 用户管理页建一个 TEACHER 账号 → 登出、用新老师账号登录验证其只能看本机构档案。
执行者用 curl(Step 3)确认后端契约;浏览器人工步骤记录为"待人工验收"清单(自动化测试不强求,属联调验收)。

- [ ] **Step 5: 停服务 + 记录联调结果**

停掉后台的后端与前端进程。把 Step 3 的 curl 实际输出(脱敏后)整理成一段"联调结果"写入提交信息或一个简短 `frontend/INTEGRATION.md`(可选)。
```bash
cd "D:/works/test/SELLM" && git add -A frontend/ && git commit -q -m "docs(frontend): 端到端联调验证结果" --allow-empty
```

---

## 后续计划(不在本计划范围)

1. **历史浏览**:后端补"列出评估/报告/IEP、按 child 查历史记录"端点,前端加列表/详情/分页页。
2. **量表动态化**:后端补"列出量表 / 取量表题目"端点,前端评估表单改为动态渲染(去掉硬编码 CARS)。
3. **机构管理 / 家长关联**:Organization 管理页、给 child 选择/绑定家长账号的更友好交互。
4. **小程序家长端**:uni-app 家长端(查看 IEP/报告、居家建议、反馈、进度趋势)。
5. **真实 AI 接入**:后端 MockAiModel 换合规大模型 API(下一计划)。
6. **生产部署**:docker-compose(mysql/redis/minio/backend)+ 前端构建产物托管;dev profile 仅本地联调用。

---

## 自检结论

- **范围覆盖**:后端 dev profile(零安装联调)+ 登录 + 儿童档案 CRUD + 评估 + 报告(生成/定稿)+ IEP(生成/定稿)+ 用户管理(MANAGER 建老师)+ 端到端联调,覆盖"主链路 + 用户管理"。历史列表/量表动态化/机构管理列入后续(受后端现有端点约束)。
- **API 契约一致**:前端 api/ 各模块严格对齐后端真实端点(路径/方法/请求体/Result 解包),契约表已在计划头列出并据后端源码核对。
- **红线衔接**:报告/IEP 页明确标注"AI 草稿仅供参考、需人工把关",定稿是人工编辑后提交——呼应"AI 只产草案、人定稿";JWT 经 axios 拦截器注入,行级权限由后端 AccessGuard 保证,前端登出/401 跳登录;Child 姓名前端只经 API 拿明文(加解密在后端)。
- **占位符**:无 TBD/TODO。Task 2 Step 7 明确要求先建 5 个占位 view 以免路由加载报错,后续任务替换——这是有意的渐进,非占位符遗留。
- **类型/契约一致性**:登录 data.{token,role};children list 返回数组、create 返回 id;assessment 返回 {id,totalScore,bandLabel,interpretation};report/iep 返回 {id,draft,finalizedContent,status}——前端各页按此取值,跨页 id 经 router query 传递。
- **验证策略**:前端每个页面任务用 `npm run build` 验证可编译(无后端依赖);Task 7 用真实 dev 后端 + curl 验证端到端契约连通(后端运行时 H2 文件库,零安装)。Node 24 + Vite 5 若有引擎告警按现象处理。
