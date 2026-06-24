# 计划:超级管理员后台管理修订(5 项)

## 决策(已确认)
- 省市数据:**前端内置**(34 省 + 各省主要地市,数据源独立文件可热插拔,后续换县区级全量)。
- 删用户:**软删**(标记不可用,数据保留);编辑:改状态/角色(/机构)。

## 现状(已探查)
- 改密端点 `PUT /api/users/me/password` **已校验旧密码**(matches 不一致抛"原密码错误");前端 `oldPassword` 初始空——**问题是浏览器自动填充**,需 autocomplete=off。
- 创建机构:省/市现为 `el-input` 纯文本;管理员账号/密码已是空默认(符合需求)。
- "创建机构管理者"卡片独立存在(与创建机构含管理员重复)。
- 机构列表无筛选。全部用户列表只读。
- 后端**无删用户/编辑用户端点**(需新增);UserRepository 有 updateRoleOrgStatus/updateStatus 可复用。

## 实施

### 需求1:改密码不自动填旧密码
- 前端:改密表单加 `autocomplete="off"`,密码框 `autocomplete="new-password"`,防浏览器回填。
- 后端已校验旧密码,无需改。

### 需求2:创建机构 — 省市级联 + 管理员手填
- 新增 `frontend/src/api/regions.js`:内置省-市级联数据(省 code/name + 各省 cities[])。
- 创建机构表单:省份改 `el-select`(省列表),地市 `el-select`(随选中省动态出 cities),省变则清空市。
- 管理员账号/密码已空默认,保持。

### 需求3:取消"创建机构管理者"卡片
- 删除该 el-card(创建机构已含管理员,功能重复)。
- 其 mgrForm/onCreateManager 相关脚本一并移除。

### 需求4:机构列表按省/市/障碍类型筛选
- 机构列表上方加筛选行:省下拉 + 市下拉(随省联动)+ 障碍类型下拉。
- 纯前端过滤(computed):对已加载 orgs 按 province/city/disorderTypes 包含筛选。
- (数据量小,前端筛选足够;后续多再做后端分页筛选。)

### 需求5:全部用户 — 软删 + 编辑
- **后端**:UserManagementController 加
  - `DELETE /api/users/{id}`(软删:updateStatus → "DISABLED";SUPER_ADMIN;禁止删自己/最后一个超管)
  - `PUT /api/users/{id}`(编辑:改 status/role/orgId;SUPER_ADMIN)
  - UserRepository/Mapper 复用 updateRoleOrgStatus;ErrorCode 现有。
- **前端**:全部用户表加"操作"列(编辑/删除按钮)+ 编辑 dialog(状态/角色/机构);api/users.js 加 deleteUser/updateUser。
- 软删状态 "DISABLED" 加入 STATUS_LABELS。

### 验证 + 文档
- 后端:删用户/编辑用户测试(软删置 DISABLED、禁删自己、越权);全量 clean install。
- 前端 build;端到端:超管登录改密(错旧密码拒)、建机构省市级联、机构筛选、用户编辑/软删。
- 更新 CLAUDE_CHANGES.md。

## 不做(本期外)
- 县区级全量行政区划(先省+主要地市)。
- 用户硬删除、批量操作、用户搜索分页(数据量大时再做)。
- 编辑用户密码重置(改密走用户自己的改密;管理员重置密码可后续加)。

## 风险
- 软删后该用户登录应被拒(status != ACTIVE);需确认登录校验 status(若未校验,软删用户仍能登录是隐患,纳入实现核对)。
- 禁止删自己/最后超管,防自锁。
