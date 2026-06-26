<template>
  <div>
    <!-- 修改密码:所有角色都显示 -->
    <el-card style="max-width:520px;margin-bottom:24px">
      <template #header><span>修改密码</span></template>
      <el-form label-width="100px" autocomplete="off">
        <el-form-item label="旧密码">
          <el-input v-model="pwd.oldPassword" type="password" show-password autocomplete="new-password" placeholder="请输入当前密码" />
        </el-form-item>
        <el-form-item label="新密码">
          <el-input v-model="pwd.newPassword" type="password" show-password autocomplete="new-password" placeholder="请输入新密码" />
        </el-form-item>
        <el-button type="primary" :loading="pwdLoading" @click="onChangePassword">保存新密码</el-button>
      </el-form>
    </el-card>

    <!-- ============ SUPER_ADMIN ============ -->
    <template v-if="auth.isSuperAdmin">
      <el-card style="max-width:520px;margin-bottom:24px">
        <template #header><span>创建机构(含机构管理员)</span></template>
        <el-form label-width="100px">
          <el-form-item label="机构名称">
            <el-select v-model="orgForm.name" filterable allow-create default-first-option
                       placeholder="选择已有机构或直接输入新机构名" style="width:100%">
              <el-option v-for="o in orgs" :key="o.id" :label="o.name" :value="o.name" />
            </el-select>
            <span style="color:#999;font-size:12px">选已有机构名则只新增管理员(不重复建机构),输入新名则建新机构</span>
          </el-form-item>
          <el-form-item label="障碍类型">
            <el-select v-model="orgForm.disorderCodes" multiple placeholder="可多选" style="width:100%">
              <el-option v-for="d in DISORDER_TYPES" :key="d.code" :label="d.label" :value="d.code" />
            </el-select>
          </el-form-item>
          <el-form-item label="省份">
            <el-select v-model="orgForm.province" placeholder="选择省份" filterable style="width:100%" @change="onOrgProvinceChange">
              <el-option v-for="p in PROVINCES" :key="p.name" :label="p.name" :value="p.name" />
            </el-select>
          </el-form-item>
          <el-form-item label="地市">
            <el-select v-model="orgForm.city" placeholder="先选省份" filterable :disabled="!orgForm.province" style="width:100%">
              <el-option v-for="c in orgCityOptions" :key="c" :label="c" :value="c" />
            </el-select>
          </el-form-item>
          <el-form-item label="管理员账号">
            <el-input v-model="orgForm.managerUsername" autocomplete="off" placeholder="新建管理员登录账号" />
          </el-form-item>
          <el-form-item label="管理员密码">
            <el-input v-model="orgForm.managerPassword" type="password" show-password autocomplete="new-password" placeholder="新建管理员密码" />
          </el-form-item>
          <el-button type="primary" :loading="orgLoading" @click="onCreateOrg">创建机构</el-button>
        </el-form>
        <p style="color:#999;font-size:12px;margin-top:8px">创建机构会同时创建该机构的管理员账号(即可登录)。</p>
      </el-card>

      <el-card style="margin-bottom:24px">
        <template #header>
          <div style="display:flex;justify-content:space-between;align-items:center">
            <span>机构列表</span>
            <div style="display:flex;gap:8px">
              <el-button size="small" @click="onDownloadOrgTemplate">下载导入模板</el-button>
              <el-upload :show-file-list="false" :before-upload="onImportOrgs" accept=".xlsx,.xls" style="display:inline-block">
                <el-button size="small" type="primary" :loading="orgImporting">批量导入</el-button>
              </el-upload>
              <el-button size="small" @click="onExportOrgs">导出 Excel</el-button>
            </div>
          </div>
        </template>
        <div style="display:flex;gap:12px;margin-bottom:12px;flex-wrap:wrap">
          <el-select v-model="orgFilter.province" placeholder="按省份" clearable filterable style="width:160px" @change="orgFilter.city=''">
            <el-option v-for="p in PROVINCES" :key="p.name" :label="p.name" :value="p.name" />
          </el-select>
          <el-select v-model="orgFilter.city" placeholder="按地市" clearable filterable :disabled="!orgFilter.province" style="width:160px">
            <el-option v-for="c in orgFilterCityOptions" :key="c" :label="c" :value="c" />
          </el-select>
          <el-select v-model="orgFilter.disorder" placeholder="按障碍类型" clearable style="width:180px">
            <el-option v-for="d in DISORDER_TYPES" :key="d.code" :label="d.label" :value="d.code" />
          </el-select>
        </div>
        <el-table :data="filteredOrgs" size="small">
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="name" label="名称" />
          <el-table-column label="障碍类型">
            <template #default="{ row }">{{ disorderCsvToLabels(row.disorderTypes) }}</template>
          </el-table-column>
          <el-table-column prop="province" label="省份" width="90" />
          <el-table-column prop="city" label="地市" width="90" />
          <el-table-column label="操作" width="140">
            <template #default="{ row }">
              <el-button size="small" @click="openEditOrg(row)">编辑</el-button>
              <el-button size="small" type="danger" @click="onDeleteOrg(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!filteredOrgs.length" description="无匹配机构" :image-size="60" />
      </el-card>

      <el-dialog v-model="editOrgDialog" title="编辑机构" width="460px">
        <el-form label-width="90px">
          <el-form-item label="机构名称"><el-input v-model="editOrgForm.name" /></el-form-item>
          <el-form-item label="障碍类型">
            <el-select v-model="editOrgForm.disorderCodes" multiple placeholder="可多选" style="width:100%">
              <el-option v-for="d in DISORDER_TYPES" :key="d.code" :label="d.label" :value="d.code" />
            </el-select>
          </el-form-item>
          <el-form-item label="省份">
            <el-select v-model="editOrgForm.province" placeholder="选择省份" filterable style="width:100%" @change="editOrgForm.city=''">
              <el-option v-for="p in PROVINCES" :key="p.name" :label="p.name" :value="p.name" />
            </el-select>
          </el-form-item>
          <el-form-item label="地市">
            <el-select v-model="editOrgForm.city" placeholder="先选省份" filterable :disabled="!editOrgForm.province" style="width:100%">
              <el-option v-for="c in editOrgCityOptions" :key="c" :label="c" :value="c" />
            </el-select>
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="editOrgDialog=false">取消</el-button>
          <el-button type="primary" :loading="editOrgLoading" @click="onSaveOrg">保存</el-button>
        </template>
      </el-dialog>

      <el-card>
        <template #header>
          <div style="display:flex;justify-content:space-between;align-items:center">
            <span>全部用户</span>
            <el-button size="small" @click="onExportUsers">导出 Excel</el-button>
          </div>
        </template>
        <div style="display:flex;gap:12px;margin-bottom:12px;flex-wrap:wrap">
          <el-select v-model="userFilter.province" placeholder="按地区(省)" clearable filterable style="width:150px">
            <el-option v-for="p in PROVINCES" :key="p.name" :label="p.name" :value="p.name" />
          </el-select>
          <el-select v-model="userFilter.orgId" placeholder="按机构" clearable filterable style="width:180px">
            <el-option v-for="o in orgs" :key="o.id" :label="o.name" :value="o.id" />
          </el-select>
          <el-select v-model="userFilter.role" placeholder="按角色" clearable style="width:150px">
            <el-option label="超级管理者" value="SUPER_ADMIN" />
            <el-option label="机构管理者" value="MANAGER" />
            <el-option label="老师/康复师" value="TEACHER" />
            <el-option label="家长" value="PARENT" />
          </el-select>
        </div>
        <el-table :data="filteredUsers" size="small">
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="username" label="用户名" />
          <el-table-column label="角色">
            <template #default="{ row }">{{ roleLabel(row.role) }}</template>
          </el-table-column>
          <el-table-column label="机构" width="150">
            <template #default="{ row }">{{ orgName(row.orgId) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="160">
            <template #default="{ row }">
              <el-button size="small" @click="openEditUser(row)">编辑</el-button>
              <el-button size="small" type="danger" :disabled="row.id === currentUid" @click="onDeleteUser(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>

      <el-dialog v-model="editUserDialog" title="编辑用户" width="420px">
        <el-form label-width="80px">
          <el-form-item label="用户名"><span>{{ editUserForm.username }}</span></el-form-item>
          <el-form-item label="角色">
            <el-select v-model="editUserForm.role" style="width:100%">
              <el-option label="超级管理者" value="SUPER_ADMIN" />
              <el-option label="机构管理者" value="MANAGER" />
              <el-option label="老师/康复师" value="TEACHER" />
              <el-option label="家长" value="PARENT" />
            </el-select>
          </el-form-item>
          <el-form-item label="机构">
            <el-select v-model="editUserForm.orgId" placeholder="选择机构(可空)" clearable filterable style="width:100%">
              <el-option v-for="o in orgs" :key="o.id" :label="o.name" :value="o.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="editUserForm.status" style="width:100%">
              <el-option label="已激活" value="ACTIVE" />
              <el-option label="待审核" value="PENDING" />
              <el-option label="已停用" value="DISABLED" />
            </el-select>
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button type="warning" plain :loading="resetPwdLoading" @click="onResetPassword">初始化密码</el-button>
          <el-button @click="editUserDialog = false">取消</el-button>
          <el-button type="primary" :loading="editUserLoading" @click="onSaveEditUser">保存</el-button>
        </template>
      </el-dialog>
    </template>

    <!-- ============ MANAGER ============ -->
    <template v-else-if="auth.isManager">
      <el-card style="max-width:520px;margin-bottom:24px">
        <template #header><span>创建账号(老师/家长)</span></template>
        <el-form label-width="100px">
          <el-form-item label="用户名">
            <el-input v-model="userForm.username" />
          </el-form-item>
          <el-form-item label="密码">
            <el-input v-model="userForm.password" type="password" show-password />
          </el-form-item>
          <el-form-item label="角色">
            <el-select v-model="userForm.role" placeholder="选择角色" style="width:100%">
              <el-option label="老师/康复师 (TEACHER)" value="TEACHER" />
              <el-option label="家长 (PARENT)" value="PARENT" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="userForm.role === 'TEACHER'" label="所属班级">
            <el-select v-model="userForm.classIds" multiple placeholder="可多选(本机构班级)" style="width:100%">
              <el-option v-for="c in classes" :key="c.id" :label="c.name" :value="c.id" />
            </el-select>
          </el-form-item>
          <el-button type="primary" :loading="userLoading" @click="onCreateUser">创建账号</el-button>
        </el-form>
        <p style="color:#999;font-size:12px;margin-top:8px">新账号自动归属你所在机构,创建后即可登录。</p>
      </el-card>

      <el-card style="margin-bottom:24px">
        <template #header><span>本机构用户</span></template>
        <el-table :data="users" size="small">
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column prop="username" label="用户名" />
          <el-table-column label="角色">
            <template #default="{ row }">{{ roleLabel(row.role) }}</template>
          </el-table-column>
          <el-table-column label="状态">
            <template #default="{ row }">
              <el-tag size="small" :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </el-card>

      <el-card>
        <template #header><span>本机构家长</span></template>
        <el-table :data="parents" size="small">
          <el-table-column prop="username" label="账号" />
          <el-table-column prop="name" label="家长姓名" />
          <el-table-column prop="childName" label="儿童姓名" />
          <el-table-column prop="relationshipLabel" label="关系" width="80" />
          <el-table-column prop="className" label="班级" />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!parents.length" description="暂无家长" :image-size="60" />
        <p style="color:#999;font-size:12px;margin-top:8px">家长注册后由所选老师审核;此处展示已注册家长信息。</p>
      </el-card>

      <el-card>
        <template #header><span>微信家长待激活</span></template>
        <el-table :data="pendingWeChat" size="small">
          <el-table-column prop="username" label="微信账号" />
          <el-table-column label="机构" width="100">
            <template #default="{ row }">{{ row.orgId ? row.orgId : '未分配' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="180">
            <template #default="{ row }">
              <el-button size="small" type="success" @click="openActivate(row)">激活</el-button>
              <el-button size="small" type="danger" @click="onRejectWeChat(row.id)">拒绝</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!pendingWeChat.length" description="暂无微信家长待激活" :image-size="60" />
        <p style="color:#999;font-size:12px;margin-top:8px">微信家长首次登录后落本机构(或未分配),激活时补孩子信息并建档,激活后即可使用。</p>
      </el-card>

      <el-dialog v-model="activateDialog" title="激活微信家长" width="460px">
        <el-form label-width="90px">
          <el-form-item label="孩子姓名">
            <el-input v-model="activateForm.childName" />
          </el-form-item>
          <el-form-item label="障碍类型">
            <el-select v-model="activateForm.disorderCodes" multiple placeholder="可多选" style="width:100%">
              <el-option v-for="d in DISORDER_TYPES" :key="d.code" :label="d.label" :value="d.code" />
            </el-select>
          </el-form-item>
          <el-form-item label="班级">
            <el-select v-model="activateForm.classId" clearable placeholder="可空(本机构班级)" style="width:100%">
              <el-option v-for="c in classes" :key="c.id" :label="c.name" :value="c.id" />
            </el-select>
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="activateDialog = false">取消</el-button>
          <el-button type="primary" :loading="activateLoading" @click="onActivate">确认激活</el-button>
        </template>
      </el-dialog>
    </template>

    <!-- ============ TEACHER ============ -->
    <template v-else-if="auth.isTeacher">
      <el-card>
        <template #header><span>待审核家长(分派给我)</span></template>
        <el-table :data="pending" size="small">
          <el-table-column prop="username" label="账号" />
          <el-table-column prop="name" label="家长姓名" />
          <el-table-column prop="childName" label="儿童姓名" />
          <el-table-column prop="relationshipLabel" label="关系" width="80" />
          <el-table-column label="操作" width="180">
            <template #default="{ row }">
              <el-button size="small" type="success" @click="onApprove(row.id)">通过</el-button>
              <el-button size="small" type="danger" @click="onReject(row.id)">拒绝</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!pending.length" description="暂无待审核家长" :image-size="60" />
        <p style="color:#999;font-size:12px;margin-top:8px">审核通过后将自动为该家长建立儿童档案,家长即可登录。</p>
      </el-card>
    </template>
  </div>
</template>
<!-- PLACEHOLDER_SCRIPT -->
<script setup>
import { reactive, ref, computed, watch, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listUsers, createUser, listPendingParents, listParents, approveUser, rejectUser, changeMyPassword,
  listPendingWeChat, activateWeChat, rejectWeChat, updateUser, deleteUser, resetUserPassword
} from '../api/users'
import { listOrgs, createOrg, updateOrg, deleteOrg, batchCreateOrgs } from '../api/orgs'
import { listClasses } from '../api/classes'
import { DISORDER_TYPES, disorderCsvToLabels } from '../api/meta'
import { PROVINCES } from '../api/regions'
import { exportSheet, exportTemplate, parseSheet } from '../utils/xlsxExport'
import { useAuthStore } from '../stores/auth'

const ROLE_LABELS = {
  SUPER_ADMIN: '超级管理者',
  MANAGER: '管理者',
  TEACHER: '老师/康复师',
  PARENT: '家长'
}
const roleLabel = (r) => ROLE_LABELS[r] || r
const statusType = (s) => (s === 'ACTIVE' ? 'success' : s === 'PENDING' ? 'warning' : s === 'DISABLED' ? 'danger' : 'info')
const STATUS_LABELS = { ACTIVE: '已激活', PENDING: '待审核', REJECTED: '已拒绝', DISABLED: '已停用' }
const statusLabel = (s) => STATUS_LABELS[s] || s

const auth = useAuthStore()
// auth store 未存当前 userId;禁删自己由后端兜底(JWT uid),前端不预禁用
const currentUid = null

// 改密码(全角色)
const pwd = reactive({ oldPassword: '', newPassword: '' })
const pwdLoading = ref(false)
async function onChangePassword() {
  if (!pwd.oldPassword || !pwd.newPassword) {
    ElMessage.warning('请填写旧密码和新密码')
    return
  }
  pwdLoading.value = true
  try {
    await changeMyPassword(pwd.oldPassword, pwd.newPassword)
    ElMessage.success('密码已修改')
    pwd.oldPassword = ''
    pwd.newPassword = ''
  } catch (e) {} finally { pwdLoading.value = false }
}

// 用户列表(超管/管理者共用)
const users = ref([])
async function loadUsers() {
  try { users.value = await listUsers() } catch (e) {}
}

// ---- 超管 ----
const orgs = ref([])
const orgForm = reactive({
  name: '', disorderCodes: [], province: '', city: '',
  managerUsername: '', managerPassword: ''
})
const orgLoading = ref(false)

// 创建机构:省→市级联
const orgCityOptions = computed(() => {
  const p = PROVINCES.find(x => x.name === orgForm.province)
  return p ? p.cities : []
})
function onOrgProvinceChange() { orgForm.city = '' }

// 下拉选中已有机构名时,同步回填该机构的障碍类型/省份/地市(手输新名则不回填)
watch(() => orgForm.name, (name) => {
  const o = orgs.value.find(x => x.name === name)
  if (!o) return
  orgForm.disorderCodes = (o.disorderTypes || '').split(',').map(s => s.trim()).filter(Boolean)
  orgForm.province = o.province || ''
  orgForm.city = o.city || ''
})

// 机构列表筛选(省/市/障碍类型)
const orgFilter = reactive({ province: '', city: '', disorder: '' })
const orgFilterCityOptions = computed(() => {
  const p = PROVINCES.find(x => x.name === orgFilter.province)
  return p ? p.cities : []
})
const filteredOrgs = computed(() => orgs.value.filter(o => {
  if (orgFilter.province && o.province !== orgFilter.province) return false
  if (orgFilter.city && o.city !== orgFilter.city) return false
  if (orgFilter.disorder && !(o.disorderTypes || '').split(',').map(s => s.trim()).includes(orgFilter.disorder)) return false
  return true
}))

// 编辑用户
const editUserDialog = ref(false)
const editUserLoading = ref(false)
const editUserForm = reactive({ id: null, username: '', role: '', orgId: null, status: '' })
function openEditUser(row) {
  editUserForm.id = row.id
  editUserForm.username = row.username
  editUserForm.role = row.role
  editUserForm.orgId = row.orgId
  editUserForm.status = row.status
  editUserDialog.value = true
}
async function onSaveEditUser() {
  editUserLoading.value = true
  try {
    await updateUser(editUserForm.id, {
      role: editUserForm.role,
      orgId: editUserForm.orgId === '' ? null : editUserForm.orgId,
      status: editUserForm.status
    })
    ElMessage.success('已保存')
    editUserDialog.value = false
    await loadUsers()
  } catch (e) {} finally { editUserLoading.value = false }
}
// 全部用户筛选(地区/机构/角色)+ 机构名映射
const userFilter = reactive({ province: '', orgId: null, role: '' })
const orgName = (orgId) => {
  const o = orgs.value.find(x => x.id === orgId)
  return o ? o.name : (orgId == null ? '—' : orgId)
}
const filteredUsers = computed(() => users.value.filter(u => {
  if (userFilter.role && u.role !== userFilter.role) return false
  if (userFilter.orgId && u.orgId !== userFilter.orgId) return false
  if (userFilter.province) {
    const o = orgs.value.find(x => x.id === u.orgId)
    if (!o || o.province !== userFilter.province) return false
  }
  return true
}))

// 初始化密码
const resetPwdLoading = ref(false)
async function onResetPassword() {
  try {
    await ElMessageBox.confirm(`确认将用户「${editUserForm.username}」的密码初始化为默认密码?`, '初始化密码', { type: 'warning' })
  } catch { return }
  resetPwdLoading.value = true
  try {
    const pwd = await resetUserPassword(editUserForm.id)
    ElMessageBox.alert(`已初始化。新密码:${pwd}\n请线下告知该用户并提示尽快修改。`, '初始化成功', { type: 'success' })
  } catch (e) {} finally { resetPwdLoading.value = false }
}

async function onDeleteUser(row) {
  try {
    await ElMessageBox.confirm(`确认删除用户「${row.username}」?删除后该账号将停用、无法登录。`, '确认删除', { type: 'warning' })
  } catch { return }
  try {
    await deleteUser(row.id)
    ElMessage.success('已删除(停用)')
    await loadUsers()
  } catch (e) {}
}

async function loadOrgs() {
  try { orgs.value = await listOrgs() } catch (e) {}
}
async function onCreateOrg() {
  if (!orgForm.name) { ElMessage.warning('请填写机构名称'); return }
  if (!orgForm.managerUsername || !orgForm.managerPassword) {
    ElMessage.warning('请填写机构管理员账号和密码')
    return
  }
  orgLoading.value = true
  try {
    const id = await createOrg({
      name: orgForm.name,
      disorderTypes: orgForm.disorderCodes.join(','),
      province: orgForm.province,
      city: orgForm.city,
      managerUsername: orgForm.managerUsername,
      managerPassword: orgForm.managerPassword
    })
    ElMessage.success(`机构创建成功(id=${id})`)
    orgForm.name = ''
    orgForm.disorderCodes = []
    orgForm.province = ''
    orgForm.city = ''
    orgForm.managerUsername = ''
    orgForm.managerPassword = ''
    await Promise.all([loadOrgs(), loadUsers()])   // 即时同步机构列表 + 全部用户(新管理员)
  } catch (e) {} finally { orgLoading.value = false }
}

// ---- 机构 编辑 / 删除 ----
const editOrgDialog = ref(false)
const editOrgLoading = ref(false)
const editOrgForm = reactive({ id: null, name: '', disorderCodes: [], province: '', city: '' })
const editOrgCityOptions = computed(() => {
  const p = PROVINCES.find(x => x.name === editOrgForm.province)
  return p ? p.cities : []
})
function openEditOrg(row) {
  editOrgForm.id = row.id
  editOrgForm.name = row.name
  editOrgForm.disorderCodes = (row.disorderTypes || '').split(',').map(s => s.trim()).filter(Boolean)
  editOrgForm.province = row.province || ''
  editOrgForm.city = row.city || ''
  editOrgDialog.value = true
}
async function onSaveOrg() {
  if (!editOrgForm.name) { ElMessage.warning('请填写机构名称'); return }
  editOrgLoading.value = true
  try {
    await updateOrg(editOrgForm.id, {
      name: editOrgForm.name,
      disorderTypes: editOrgForm.disorderCodes.join(','),
      province: editOrgForm.province,
      city: editOrgForm.city
    })
    ElMessage.success('已保存')
    editOrgDialog.value = false
    await loadOrgs()   // 同步:机构名下拉(用户筛选/编辑)随之刷新
  } catch (e) {} finally { editOrgLoading.value = false }
}
async function onDeleteOrg(row) {
  try {
    await ElMessageBox.confirm(`确认删除机构「${row.name}」?机构下有用户或儿童时将被拦截。`, '确认删除', { type: 'warning' })
  } catch { return }
  try {
    await deleteOrg(row.id)
    ElMessage.success('已删除')
    await loadOrgs()
  } catch (e) {}
}

// ---- 机构 批量导入 / 导出 / 模板 ----
const orgImporting = ref(false)
function onDownloadOrgTemplate() {
  exportTemplate('机构导入模板',
    ['机构名称', '省份', '地市', '障碍类型(逗号分隔代码)', '管理员账号', '管理员密码'],
    [['示例康复中心', '江苏省', '南京市', 'ASD,ADHD', 'mgr_demo', 'pwd123456']])
}
function onExportOrgs() {
  exportSheet('机构列表', '机构', filteredOrgs.value.map(o => ({
    id: o.id, name: o.name,
    disorderTypes: disorderCsvToLabels(o.disorderTypes),
    province: o.province, city: o.city
  })), [
    { key: 'id', label: 'ID' }, { key: 'name', label: '机构名称' },
    { key: 'disorderTypes', label: '障碍类型' },
    { key: 'province', label: '省份' }, { key: 'city', label: '地市' }
  ])
}
async function onImportOrgs(file) {
  orgImporting.value = true
  try {
    const rows = await parseSheet(file)
    const list = rows.map(r => ({
      name: (r['机构名称'] || '').toString().trim(),
      province: (r['省份'] || '').toString().trim(),
      city: (r['地市'] || '').toString().trim(),
      disorderTypes: (r['障碍类型(逗号分隔代码)'] || '').toString().trim(),
      managerUsername: (r['管理员账号'] || '').toString().trim(),
      managerPassword: (r['管理员密码'] || '').toString().trim()
    })).filter(o => o.name)
    if (!list.length) { ElMessage.warning('未解析到有效机构行'); return }
    const res = await batchCreateOrgs(list)
    const msg = `导入完成:成功 ${res.success} 条` +
      (res.failures && res.failures.length ? `,失败 ${res.failures.length} 条:\n${res.failures.join('\n')}` : '')
    ElMessageBox.alert(msg, '批量导入结果', { type: res.failures && res.failures.length ? 'warning' : 'success' })
    await loadOrgs()
  } catch (e) {
    ElMessage.error('导入失败,请检查文件格式')
  } finally { orgImporting.value = false }
  return false   // 阻止 el-upload 默认上传
}

// ---- 全部用户 导出 ----
function onExportUsers() {
  exportSheet('全部用户', '用户', filteredUsers.value.map(u => ({
    id: u.id, username: u.username, role: roleLabel(u.role),
    orgName: orgName(u.orgId), status: statusLabel(u.status)
  })), [
    { key: 'id', label: 'ID' }, { key: 'username', label: '用户名' },
    { key: 'role', label: '角色' }, { key: 'orgName', label: '机构' },
    { key: 'status', label: '状态' }
  ])
}

// ---- 管理者 ----
const userForm = reactive({ username: '', password: '', role: 'TEACHER', classIds: [] })
const userLoading = ref(false)
const pending = ref([])
const parents = ref([])
const classes = ref([])

async function loadPending() {
  try { pending.value = await listPendingParents() } catch (e) {}
}
async function loadParents() {
  try { parents.value = await listParents() } catch (e) {}
}
async function loadClasses() {
  try { classes.value = await listClasses() } catch (e) {}
}

// ---- 微信家长激活(管理者)----
const pendingWeChat = ref([])
const activateDialog = ref(false)
const activateLoading = ref(false)
const activateForm = reactive({ id: null, childName: '', disorderCodes: [], classId: null })

async function loadPendingWeChat() {
  try { pendingWeChat.value = await listPendingWeChat() } catch (e) {}
}
function openActivate(row) {
  activateForm.id = row.id
  activateForm.childName = ''
  activateForm.disorderCodes = []
  activateForm.classId = null
  activateDialog.value = true
}
async function onActivate() {
  if (!activateForm.childName) { ElMessage.warning('请填写孩子姓名'); return }
  activateLoading.value = true
  try {
    await activateWeChat(activateForm.id, {
      childName: activateForm.childName,
      childDisorderType: activateForm.disorderCodes.join(','),
      classId: activateForm.classId || null
    })
    ElMessage.success('已激活')
    activateDialog.value = false
    await Promise.all([loadPendingWeChat(), loadUsers()])
  } catch (e) {} finally { activateLoading.value = false }
}
async function onRejectWeChat(id) {
  try {
    await rejectWeChat(id)
    ElMessage.success('已拒绝')
    await loadPendingWeChat()
  } catch (e) {}
}
async function onCreateUser() {
  if (!userForm.username || !userForm.password || !userForm.role) {
    ElMessage.warning('请填写完整')
    return
  }
  userLoading.value = true
  try {
    const payload = {
      username: userForm.username, password: userForm.password, role: userForm.role
    }
    if (userForm.role === 'TEACHER' && userForm.classIds.length) {
      payload.classIds = userForm.classIds
    }
    const id = await createUser(payload)
    ElMessage.success(`账号创建成功(id=${id})`)
    userForm.username = ''
    userForm.password = ''
    userForm.classIds = []
    await Promise.all([loadUsers(), loadParents()])
  } catch (e) {} finally { userLoading.value = false }
}
async function onApprove(id) {
  try {
    await approveUser(id)
    ElMessage.success('已通过审核')
    await loadPending()
  } catch (e) {}
}
async function onReject(id) {
  try {
    await rejectUser(id)
    ElMessage.success('已拒绝')
    await loadPending()
  } catch (e) {}
}

onMounted(() => {
  if (auth.isSuperAdmin) {
    loadOrgs()
    loadUsers()
  } else if (auth.isManager) {
    loadUsers()
    loadParents()
    loadClasses()
    loadPendingWeChat()
  } else if (auth.isTeacher) {
    loadPending()
  }
})
</script>
