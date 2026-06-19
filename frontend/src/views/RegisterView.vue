<template>
  <div style="display:flex;align-items:center;justify-content:center;height:100vh;background:#f0f2f5">
    <el-card style="width:380px">
      <h2 style="text-align:center;margin-bottom:20px">家长注册</h2>
      <el-form @submit.prevent="onSubmit">
        <el-form-item>
          <el-input v-model="form.username" placeholder="用户名" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="form.password" type="password" placeholder="密码" show-password />
        </el-form-item>
        <el-form-item>
          <el-select v-model="form.orgId" placeholder="选择所属机构" style="width:100%">
            <el-option v-for="o in orgs" :key="o.id"
                       :label="o.region ? `${o.name}(${o.region})` : o.name" :value="o.id" />
          </el-select>
        </el-form-item>
        <el-button type="primary" style="width:100%" :loading="loading" @click="onSubmit">注册</el-button>
      </el-form>
      <p style="text-align:center;margin-top:12px">
        <router-link to="/login">已有账号?去登录</router-link>
      </p>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { register } from '../api/auth'
import { publicOrgs } from '../api/orgs'

const form = reactive({ username: '', password: '', orgId: null })
const orgs = ref([])
const loading = ref(false)
const router = useRouter()

onMounted(async () => {
  try { orgs.value = await publicOrgs() } catch (e) {}
})

async function onSubmit() {
  if (!form.username || !form.password || !form.orgId) {
    ElMessage.warning('请填写用户名、密码并选择机构')
    return
  }
  loading.value = true
  try {
    await register({ username: form.username, password: form.password, orgId: form.orgId })
    ElMessage.success('注册成功,待机构管理者审核后可登录')
    router.push('/login')
  } catch (e) {} finally { loading.value = false }
}
</script>
