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
      <RouterLink class="brand" to="/products"><span class="brand-mark">S</span>秒杀平台</RouterLink>
      <nav class="account-nav" aria-label="账户导航">
        <RouterLink to="/products">商品秒杀</RouterLink>
        <RouterLink to="/welcome">账户首页</RouterLink>
        <RouterLink v-if="auth.isAdmin" to="/admin">管理员身份</RouterLink>
        <span v-if="auth.user">{{ auth.user.nickname }}</span>
        <el-button @click="logout">退出登录</el-button>
      </nav>
    </header>
    <!-- 路径变化时重新创建页面，切换商品 ID 会触发旧页面卸载和轮询清理。 -->
    <RouterView :key="$route.path" />
    <footer>秒杀平台 · 限时商品与异步订单</footer>
  </div>
</template>
