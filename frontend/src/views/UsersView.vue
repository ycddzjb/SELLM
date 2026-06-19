<template>
  <div>
    <!-- 修改密码:所有角色都显示 -->
    <el-card style="max-width:520px;margin-bottom:24px">
      <template #header><span>修改密码</span></template>
      <el-form label-width="100px">
        <el-form-item label="旧密码">
          <el-input v-model="pwd.oldPassword" type="password" show-password />
        </el-form-item>
        <el-form-item label="新密码">
          <el-input v-model="pwd.newPassword" type="password" show-password />
        </el-form-item>
        <el-button type="primary" :loading="pwdLoading" @click="onChangePassword">保存新密码</el-button>
      </el-form>
    </el-card>

    <!-- ============ SUPER_ADMIN ============ -->
    <template v-if="auth.isSuperAdmin">
      <el-card style="max-width:520px;margin-bottom:24px">
        <template #header><span>创建机构</span></template>
        <el-form label-width="100px">
          <el-form-item label="机构名称">
            <el-input v-model="orgForm.name" />
          </el-form-item>
          <el-form-item label="地区">
            <el-input v-model="orgForm.region" />
          </el-form-item>
          <el-button type="primary" :loading="orgLoading" @click="onCreateOrg">创建机构</el-button>
        </el-form>
      </el-card>

      <el-card style="margin-bottom:24px">
        <template #header><span>机构列表</span></template>
        <el-table :data="orgs" size="small">
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column prop="name" label="名称" />
          <el-table-column prop="region" label="地区" />
        </el-table>
      </el-card>

      <el-card style="max-width:520px;margin-bottom:24px">
        <template #header><span>创建机构管理者 (MANAGER)</span></template>
        <el-form label-width="100px">
          <el-form-item label="所属机构">
            <el-select v-model="mgrForm.orgId" placeholder="选择机构" style="width:100%">
              <el-option v-for="o in orgs" :key="o.id" :label="o.name" :value="o.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="用户名">
            <el-input v-model="mgrForm.username" />
          </el-form-item>
          <el-form-item label="密码">
            <el-input v-model="mgrForm.password" type="password" show-password />
          </el-form-item>
          <el-button type="primary" :loading="mgrLoading" @click="onCreateManager">创建管理者</el-button>
        </el-form>
      </el-card>

      <el-card>
        <template #header><span>全部用户</span></template>
        <el-table :data="users" size="small">
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column prop="username" label="用户名" />
          <el-table-column label="角色">
            <template #default="{ row }">{{ roleLabel(row.role) }}</template>
          </el-table-column>
          <el-table-column prop="orgId" label="机构ID" width="100" />
          <el-table-column label="状态">
            <template #default="{ row }">
              <el-tag size="small" :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
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
          <el-button type="primary" :loading="userLoading" @click="onCreateUser">创建账号</el-button>
        </el-form>
        <p style="color:#999;font-size:12px;margin-top:8px">新账号自动归属你所在机构,创建后即可登录。</p>
      </el-card>

      <el-card style="margin-bottom:24px">
        <template #header><span>待审核家长</span></template>
        <el-table :data="pending" size="small">
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column prop="username" label="用户名" />
          <el-table-column label="状态">
            <template #default="{ row }">
              <el-tag size="small" type="warning">{{ statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="200">
            <template #default="{ row }">
              <el-button size="small" type="success" @click="onApprove(row.id)">通过</el-button>
              <el-button size="small" type="danger" @click="onReject(row.id)">拒绝</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!pending.length" description="暂无待审核家长" :image-size="60" />
      </el-card>

      <el-card>
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
    </template>
  </div>
</template>
<!-- PLACEHOLDER_SCRIPT -->
<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import {
  listUsers, createUser, listPendingParents, approveUser, rejectUser, changeMyPassword
} from '../api/users'
import { listOrgs, createOrg } from '../api/orgs'
import { useAuthStore } from '../stores/auth'

const ROLE_LABELS = {
  SUPER_ADMIN: '超级管理者',
  MANAGER: '管理者',
  TEACHER: '老师/康复师',
  PARENT: '家长'
}
const roleLabel = (r) => ROLE_LABELS[r] || r
const statusType = (s) => (s === 'ACTIVE' ? 'success' : s === 'PENDING' ? 'warning' : 'info')
const STATUS_LABELS = { ACTIVE: '已激活', PENDING: '待审核', REJECTED: '已拒绝' }
const statusLabel = (s) => STATUS_LABELS[s] || s

const auth = useAuthStore()

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
const orgForm = reactive({ name: '', region: '' })
const orgLoading = ref(false)
const mgrForm = reactive({ username: '', password: '', orgId: null })
const mgrLoading = ref(false)

async function loadOrgs() {
  try { orgs.value = await listOrgs() } catch (e) {}
}
async function onCreateOrg() {
  if (!orgForm.name) { ElMessage.warning('请填写机构名称'); return }
  orgLoading.value = true
  try {
    const id = await createOrg({ name: orgForm.name, region: orgForm.region })
    ElMessage.success(`机构创建成功(id=${id})`)
    orgForm.name = ''
    orgForm.region = ''
    await loadOrgs()
  } catch (e) {} finally { orgLoading.value = false }
}
async function onCreateManager() {
  if (!mgrForm.orgId || !mgrForm.username || !mgrForm.password) {
    ElMessage.warning('请选择机构并填写用户名密码')
    return
  }
  mgrLoading.value = true
  try {
    const id = await createUser({
      username: mgrForm.username, password: mgrForm.password,
      role: 'MANAGER', orgId: mgrForm.orgId
    })
    ElMessage.success(`管理者创建成功(id=${id})`)
    mgrForm.username = ''
    mgrForm.password = ''
    mgrForm.orgId = null
    await loadUsers()
  } catch (e) {} finally { mgrLoading.value = false }
}

// ---- 管理者 ----
const userForm = reactive({ username: '', password: '', role: 'TEACHER' })
const userLoading = ref(false)
const pending = ref([])

async function loadPending() {
  try { pending.value = await listPendingParents() } catch (e) {}
}
async function onCreateUser() {
  if (!userForm.username || !userForm.password || !userForm.role) {
    ElMessage.warning('请填写完整')
    return
  }
  userLoading.value = true
  try {
    const id = await createUser({ ...userForm })
    ElMessage.success(`账号创建成功(id=${id})`)
    userForm.username = ''
    userForm.password = ''
    await loadUsers()
  } catch (e) {} finally { userLoading.value = false }
}
async function onApprove(id) {
  try {
    await approveUser(id)
    ElMessage.success('已通过审核')
    await Promise.all([loadPending(), loadUsers()])
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
    loadPending()
    loadUsers()
  }
})
</script>
