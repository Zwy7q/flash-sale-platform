<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth.js'
import { safeRedirect } from '../router/guards.js'
import { errorMessage } from '../utils/http.js'
const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const checking = ref(false)
const message = ref('暂时无法恢复账户资料。会话已保留，请检查后端连接后重试。')
async function retry() {
  if (checking.value) { return }
  checking.value = true
  try {
    await auth.restoreSession()
    await router.replace(safeRedirect(route.query.redirect))
  } catch (error) {
    message.value = errorMessage(error, '暂时无法恢复会话，请稍后重试。')
  } finally {
    checking.value = false
  }
}
function logout() {
  auth.logout()
  router.replace('/login')
}
</script>

<template>
  <main class="welcome"><p class="eyebrow">连接待恢复</p><h1>暂时无法连接。</h1>
    <p role="alert" class="intro">{{ message }}</p>
    <el-button type="primary" :loading="checking" @click="retry">重新连接</el-button>
    <el-button @click="logout">返回登录</el-button>
  </main>
</template>
