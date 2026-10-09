<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth.js'
import { safeRedirect } from '../router/guards.js'
import { errorMessage } from '../utils/http.js'
import AuthLayout from '../components/AuthLayout.vue'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
// reactive 适合包含多个字段的表单；ref 适合单个状态值。
const form = reactive({ username: '', password: '' })
const submitting = ref(false)
const error = ref('')

async function submit() {
  if (submitting.value) { return }
  error.value = ''
  if (!form.username.trim() || !form.password) {
    error.value = '请填写用户名和密码。'
    return
  }
  submitting.value = true
  try {
    // 密码不 trim，用户输入的空格也可能是密码的一部分。
    const loggedIn = await auth.login({ username: form.username.trim(), password: form.password })
    if (loggedIn) {
      form.password = ''
      await router.replace(safeRedirect(route.query.redirect))
    }
  } catch (exception) {
    if (exception.response && exception.response.status === 401) {
      error.value = '用户名或密码错误，或账户已被禁用。'
    } else {
      error.value = errorMessage(exception, '登录失败，请稍后重试。')
    }
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <AuthLayout>
    <p class="eyebrow">欢迎回来</p>
    <h2>登录账户</h2>
    <p class="form-description">使用你的平台账号继续。</p>
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
    <!-- submit.prevent 阻止原生表单刷新整页，改由 JavaScript 发 HTTP 请求。 -->
    <el-form label-position="top" @submit.prevent="submit">
      <el-form-item label="用户名">
        <el-input v-model="form.username" aria-label="用户名" autocomplete="username" placeholder="请输入用户名" :disabled="submitting" />
      </el-form-item>
      <el-form-item label="密码">
        <el-input v-model="form.password" aria-label="密码" type="password" autocomplete="current-password" show-password placeholder="请输入密码" :disabled="submitting" />
      </el-form-item>
      <el-button class="submit-button" type="primary" native-type="submit" :loading="submitting">登录</el-button>
    </el-form>
    <p class="form-switch">还没有账户？<RouterLink :to="{ path: '/register', query: { redirect: route.query.redirect } }">创建账户</RouterLink></p>
  </AuthLayout>
</template>
