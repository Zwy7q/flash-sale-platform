<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { registerRequest } from '../api/auth.js'
import { errorMessage } from '../utils/http.js'
import AuthLayout from '../components/AuthLayout.vue'

const route = useRoute()
const router = useRouter()
const form = reactive({ username: '', nickname: '', password: '', confirmPassword: '' })
const submitting = ref(false)
const error = ref('')
const registered = ref(false)

async function submit() {
  if (submitting.value || registered.value) { return }
  error.value = ''
  if (!form.username.trim() || !form.nickname.trim() || !form.password || !form.confirmPassword) {
    error.value = '请完整填写用户名、昵称和密码。'
    return
  }
  if (form.username.trim().length > 64 || form.nickname.trim().length > 64) {
    error.value = '用户名和昵称不能超过 64 个字符。'
    return
  }
  if (form.password !== form.confirmPassword) {
    error.value = '两次输入的密码不一致。'
    return
  }
  // BCrypt 接收的密码有 72 字节限制，中文字符可能占多个字节。
  if (new TextEncoder().encode(form.password).length > 72) {
    error.value = '密码不能超过 72 字节，请缩短后重试。'
    return
  }
  submitting.value = true
  try {
    await registerRequest({ username: form.username.trim(), nickname: form.nickname.trim(), password: form.password })
    form.password = ''
    form.confirmPassword = ''
    registered.value = true
  } catch (exception) {
    if (exception.response && exception.response.status === 409) {
      error.value = '这个用户名已存在，请换一个用户名。'
    } else {
      error.value = errorMessage(exception, '注册失败，请稍后重试。')
    }
  } finally {
    submitting.value = false
  }
}

function goLogin() {
  // 注册返回用户资料，没有 JWT，必须通过登录取得 Token。
  router.replace({ path: '/login', query: { redirect: route.query.redirect } })
}
</script>

<template>
  <AuthLayout>
    <p class="eyebrow">开始使用</p><h2>创建账户</h2>
    <p class="form-description">注册后使用用户名和密码登录。</p>
    <template v-if="registered">
      <el-alert title="注册成功，请前往登录。" type="success" :closable="false" show-icon />
      <el-button class="submit-button" type="primary" @click="goLogin">前往登录</el-button>
    </template>
    <template v-else>
      <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
      <el-form label-position="top" @submit.prevent="submit">
        <el-form-item label="用户名"><el-input v-model="form.username" aria-label="用户名" autocomplete="username" :disabled="submitting" /></el-form-item>
        <el-form-item label="昵称"><el-input v-model="form.nickname" aria-label="昵称" autocomplete="nickname" :disabled="submitting" /></el-form-item>
        <el-form-item label="密码"><el-input v-model="form.password" aria-label="密码" type="password" autocomplete="new-password" show-password :disabled="submitting" /></el-form-item>
        <el-form-item label="确认密码"><el-input v-model="form.confirmPassword" aria-label="确认密码" type="password" autocomplete="new-password" show-password :disabled="submitting" /></el-form-item>
        <el-button class="submit-button" type="primary" native-type="submit" :loading="submitting">注册</el-button>
      </el-form>
      <p class="form-switch">已有账户？<RouterLink :to="{ path: '/login', query: { redirect: route.query.redirect } }">前往登录</RouterLink></p>
    </template>
  </AuthLayout>
</template>
