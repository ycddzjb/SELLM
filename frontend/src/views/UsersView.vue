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
