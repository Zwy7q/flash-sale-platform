<script setup>
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth.js'
const auth = useAuthStore()
const router = useRouter()
function logout() {
  auth.logout()
  router.replace('/login')
}
</script>

<template>
  <div class="page">
    <header class="site-header">
      <RouterLink class="brand" to="/welcome"><span class="brand-mark">S</span>秒杀平台</RouterLink>
      <nav class="account-nav" aria-label="账户导航">
        <RouterLink to="/welcome">账户首页</RouterLink>
        <RouterLink v-if="auth.isAdmin" to="/admin">管理员身份</RouterLink>
        <span v-if="auth.user">{{ auth.user.nickname }}</span>
        <el-button @click="logout">退出登录</el-button>
      </nav>
    </header>
    <RouterView />
    <footer>阶段 15B · 账户与会话</footer>
  </div>
</template>
