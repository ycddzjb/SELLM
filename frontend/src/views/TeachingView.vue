<template>
  <div>
    <h3>智能教学</h3>
    <el-tabs v-model="mainTab">
      <el-tab-pane label="训练方案" name="plan">
        <LessonPanel type="PLAN" />
      </el-tab-pane>
      <el-tab-pane label="教案" name="lesson">
        <LessonPanel type="LESSON" />
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
import LessonPanel from '../components/LessonPanel.vue'

const mainTab = ref('plan')
</script>

