<template>
  <div>
    <h3>智能教学</h3>
    <el-tabs v-model="mainTab">
      <el-tab-pane label="训练方案" name="plan">
        <TeachingContentPanel type="PLAN" />
      </el-tab-pane>
      <el-tab-pane label="教案" name="lesson">
        <TeachingContentPanel type="LESSON" />
      </el-tab-pane>
      <el-tab-pane label="课件" name="courseware">
        <TeachingContentPanel type="COURSEWARE" />
      </el-tab-pane>
      <el-tab-pane label="习题" name="exercise">
        <TeachingContentPanel type="EXERCISE" />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import TeachingContentPanel from '../components/TeachingContentPanel.vue'

const mainTab = ref('plan')
</script>
