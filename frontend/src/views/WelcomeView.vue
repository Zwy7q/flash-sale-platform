<script setup>
import { ref } from 'vue'
import { useAuthStore } from '../stores/auth.js'
import { http, errorMessage } from '../utils/http.js'
const auth = useAuthStore()
const health = ref('尚未检查')
const checking = ref(false)
async function checkHealth() {
  if (checking.value) { return }
  checking.value = true
  try {
    const response = await http.get('/health', { skipAuth: true, responseType: 'text' })
    if (response.data.trim() === 'OK') { health.value = '连接成功 · OK' }
    else { health.value = '健康接口返回内容异常' }
  } catch (error) {
    health.value = errorMessage(error, '后端连接失败')
  } finally {
    checking.value = false
  }
}
</script>

<template>
  <!-- 退出会话到路由切换之间可能有一次渲染，避免读取已经清空的 user。 -->
  <main v-if="auth.user" class="welcome">
    <p class="eyebrow">账户首页</p>
    <h1>欢迎回来，{{ auth.user.nickname }}。</h1>
    <p class="intro">你已成功登录。商品展示和秒杀功能将在下一阶段接入。</p>
    <section class="connection-card">
      <h2>当前账户</h2>
      <dl class="connection-details">
        <div><dt>用户名</dt><dd>{{ auth.user.username }}</dd></div>
        <div><dt>用户编号</dt><dd>{{ auth.user.id }}</dd></div>
        <div><dt>身份</dt><dd><el-tag v-if="auth.isAdmin" type="success">管理员</el-tag><el-tag v-else type="info">普通用户</el-tag></dd></div>
      </dl>
      <p class="status-message">刷新页面会从后端恢复资料，退出登录会清理当前标签页会话。</p>
    </section>
    <section class="connection-card">
      <h2>后端连接</h2><p role="status" class="status-message">{{ health }}</p>
      <el-button :loading="checking" @click="checkHealth">检查连接</el-button>
    </section>
  </main>
</template>
