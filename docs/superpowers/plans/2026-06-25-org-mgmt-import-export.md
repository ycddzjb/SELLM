# 计划:平台管理员用户中心 —— 机构批量导入/导出/编辑/删除 + 用户导出

> 分支 feature/special-edu-llm,worktree dev_workspace。前端(UsersView 超管段 + orgs api + xlsx)+ 后端(机构 update/delete/批量)。

## 决策(已确认)
- 需求(2)语义:**下拉实时同步**——机构增/删/改/导入后,页面内所有用机构名的下拉(用户筛选"按机构"、用户编辑的机构选择)实时刷新;"创建机构"名称仍手填(新机构)。
- 导出:**前端用 xlsx 库**把已加载的机构/用户表数据生成 Excel 下载(无需后端导出接口)。
- 批量导入:**仅机构**(含模板下载):上传 Excel → 前端解析 → 调后端批量建机构(含机构管理员)。
- 删除机构:**软删 + 非空拦截**(机构下有用户/儿童则阻止)。

## 现状
- 后端机构:仅 create(`POST /api/orgs` createWithManager 事务建机构+管理员)、list(`GET /api/orgs`)。**无 update/delete/批量**。
- org 表:id/name/region + disorder_types/province/city(ALTER 补列)。**无 deleted 列**。
- 前端 UsersView 超管段:创建机构表单(名称手填 el-input)+ 机构列表(只读表格:ID/名称/障碍/省/市)+ 全部用户(列表+筛选+编辑/软删/初始化密码)。机构列表无编辑删除。
- 前端**未装 xlsx**。
- 用户编辑对话框机构是手填 ID(`editUserForm.orgId` el-input,UsersView L126-127);用户筛选"按机构"下拉用 `orgs`(L82-84)。`orgs` 由 `listOrgs()` 加载。
- UserRepository 无按 orgId 计数;Child 有 orgId 字段。非空拦截需新增计数。

<!-- PLACEHOLDER -->

## 后端改动

### A. schema + 实体
- org 表加 `deleted TINYINT DEFAULT 0`(ALTER ADD COLUMN IF NOT EXISTS,同现有补列模式)。
- Organization 实体加 deleted 字段(可选,Repository 默认过滤 deleted=0)。
- Mapper XML:findAll/findById 加 `WHERE deleted = 0`(或 0/null 兼容);insert 不变。

### B. 机构 update / delete / 计数
- OrganizationMapper 加:update(id + name/region/disorderTypes/province/city)、softDelete(id 置 deleted=1)。
- OrganizationRepository 加 update/softDelete。
- 非空拦截计数:UserRepository 加 `countByOrg(orgId)`(活跃用户,排除已软删)、ChildRepository 加 `countByOrg(orgId)`。
  (Child 表 deleted 若有则排除;按现有 child 软删情况实现。)
- OrganizationAppService 加 `update(id, req)` 和 `delete(id)`:delete 前查 user/child 计数,>0 抛 BusinessException("机构下仍有用户/儿童,不可删除")。

### C. 批量导入机构
- OrganizationAppService 加 `batchCreate(List<CreateOrgRequest>)`:逐条复用 createWithManager 逻辑;
  整批事务(任一失败回滚)或逐条容错(返回成功/失败明细)——**取逐条容错**:返回 {成功数, 失败列表[{行号,原因}]},避免一条错全回滚。
- 校验:机构名/管理员账号/密码必填,管理员账号查重。

### D. Controller 端点(均限 SUPER_ADMIN,SecurityConfig 现有 `/api/orgs` POST 已限超管,补 PUT/DELETE)
- `PUT /api/orgs/{id}` 编辑机构(name/region/disorderTypes/province/city)。
- `DELETE /api/orgs/{id}` 软删(非空拦截)。
- `POST /api/orgs/batch` 批量建机构,返回成功/失败明细。
- SecurityConfig 加 PUT/DELETE `/api/orgs/**` hasRole SUPER_ADMIN(在 common-backend)。

## 前端改动

### E. 依赖
- `npm i xlsx`(SheetJS,锁版本)。封装 `utils/xlsxExport.js`:exportSheet(filename, sheetName, rows[], headers) + parseSheet(file) → rows[]。

### F. orgs api
- orgs.js 加:updateOrg(id, payload)、deleteOrg(id)、batchCreateOrgs(list)。

### G. UsersView 超管段
- **机构列表**:加操作列(编辑/删除按钮)+ 顶部工具栏(批量导入按钮 + 下载模板 + 导出 Excel)。
  - 编辑:弹窗改 name/障碍/省市 → updateOrg → 刷新 listOrgs。
  - 删除:二次确认 → deleteOrg → 刷新(失败提示非空拦截原因)。
  - 导出:exportSheet 把 filteredOrgs 导出(列:ID/名称/障碍/省/市)。
  - 模板下载:导出一个含表头+示例行的空模板(列:机构名称/省份/地市/障碍类型(逗号分隔)/管理员账号/管理员密码)。
  - 批量导入:el-upload 选 Excel → parseSheet → 组装 CreateOrgRequest[] → batchCreateOrgs → 弹窗显示成功N条/失败明细 → 刷新。
- **需求(2)下拉同步**:机构增删改导入后统一调 `reloadOrgs()`(重新 listOrgs 并赋值 orgs ref),用户筛选"按机构"下拉、用户编辑机构选择(把 L126 手填 ID 的 el-input **改为 el-select 选 orgs**)即随 orgs 自动刷新。
- **全部用户**:工具栏加"导出 Excel"按钮:exportSheet 把当前 filteredUsers 导出(列:ID/用户名/角色/机构名/状态)。

## 实施步骤(分步,带测试)
1. **后端机构 update/delete/batch + schema deleted + 计数**:schema/实体/Mapper/XML/Repository/AppService/Controller/SecurityConfig。测试:编辑、软删、非空拦截返400、批量导入成功/失败明细、删后 listOrgs 不含、RBAC(非超管 403)。
2. **前端 xlsx + orgs api + UsersView**:装 xlsx、utils/xlsxExport.js、orgs api 三方法、机构列表编辑/删除/导入/导出/模板、用户编辑机构改下拉、下拉同步、全部用户导出。npm run build。
3. **文档**:CLAUDE_CHANGES.md + INTEGRATION.md。

## 验证
- 后端 ./backend/mvnw test(机构新端点测试);全量 clean install。
- 前端 npm run build。
- 端到端:超管登录→建/编辑/软删机构、机构下有用户时删被拦、批量导入(模板)、机构列表与全部用户导出 Excel、下拉实时同步。

## 不做(本期外)
- 用户批量导入(仅导出);班级/教师批量导入导出;机构硬删;导出大数据分页/流式(前端一次性导出已加载数据)。

## 风险
- xlsx 是较大前端依赖,锁版本、确认 build 体积。
- 批量导入逐条容错:部分成功需清晰回显失败行,避免重复导入(管理员账号查重兜底)。
- 软删后历史数据(已属该机构的用户/儿童)处理:本期靠非空拦截"先清空再删",不做级联。
- org 表 deleted 列新增,dev H2 文件库需删 backend/data(或根 data)重建。

