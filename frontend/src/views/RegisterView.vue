<template>
  <div style="display:flex;align-items:center;justify-content:center;min-height:100vh;background:#f0f2f5;padding:20px 0">
    <el-card style="width:440px">
      <h2 style="text-align:center;margin-bottom:20px">家长注册</h2>
      <el-form label-width="92px" @submit.prevent="onSubmit">
        <el-form-item label="家长姓名">
          <el-input v-model="form.name" placeholder="您的姓名" />
        </el-form-item>
        <el-form-item label="账号">
          <el-input v-model="form.username" placeholder="登录用户名" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" placeholder="密码" show-password />
        </el-form-item>
        <el-form-item label="儿童姓名">
          <el-input v-model="form.childName" placeholder="孩子姓名" />
        </el-form-item>
        <el-form-item label="残障类型">
          <el-select v-model="form.childDisorderType" placeholder="选择孩子的障碍类型" style="width:100%">
            <el-option v-for="d in DISORDER_TYPES" :key="d.code" :label="d.label" :value="d.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="亲戚关系">
          <el-select v-model="form.relationship" placeholder="与孩子的关系" style="width:100%">
            <el-option v-for="r in RELATIONSHIPS" :key="r.code" :label="r.label" :value="r.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="所在学校">
          <el-select v-model="form.orgId" placeholder="选择机构" style="width:100%" @change="onOrgChange">
            <el-option v-for="o in orgs" :key="o.id"
                       :label="o.region ? `${o.name}(${o.region})` : o.name" :value="o.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="所在班级">
          <el-select v-model="form.classId" placeholder="先选学校" style="width:100%"
                     :disabled="!form.orgId" @change="onClassChange">
            <el-option v-for="c in classes" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="审核老师">
          <el-select v-model="form.assignedTeacherId" placeholder="先选班级" style="width:100%"
                     :disabled="!form.classId">
            <el-option v-for="t in teachers" :key="t.userId" :label="t.username" :value="t.userId" />
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
import { publicOrgs, publicOrgClasses } from '../api/orgs'
import { publicClassTeachers } from '../api/classes'
import { DISORDER_TYPES, RELATIONSHIPS } from '../api/meta'

const form = reactive({
  name: '', username: '', password: '', childName: '', childDisorderType: '',
  relationship: '', orgId: null, classId: null, assignedTeacherId: null
})
const orgs = ref([])
const classes = ref([])
const teachers = ref([])
const loading = ref(false)
const router = useRouter()

onMounted(async () => {
  try { orgs.value = await publicOrgs() } catch (e) {}
})

async function onOrgChange() {
  // 重置下游选择
  form.classId = null
  form.assignedTeacherId = null
  classes.value = []
  teachers.value = []
  if (!form.orgId) return
  try { classes.value = await publicOrgClasses(form.orgId) } catch (e) {}
}

async function onClassChange() {
  form.assignedTeacherId = null
  teachers.value = []
  if (!form.classId) return
  try { teachers.value = await publicClassTeachers(form.classId) } catch (e) {}
}

async function onSubmit() {
  if (!form.username || !form.password || !form.orgId) {
    ElMessage.warning('请填写账号、密码并选择机构')
    return
  }
  if (!form.assignedTeacherId) {
    ElMessage.warning('请选择所在班级的审核老师')
    return
  }
  loading.value = true
  try {
    await register({
      name: form.name,
      username: form.username,
      password: form.password,
      childName: form.childName,
      childDisorderType: form.childDisorderType,
      relationship: form.relationship,
      orgId: form.orgId,
      classId: form.classId,
      assignedTeacherId: form.assignedTeacherId
    })
    ElMessage.success('注册成功,待老师审核后可登录')
    router.push('/login')
  } catch (e) {} finally { loading.value = false }
}
</script>
