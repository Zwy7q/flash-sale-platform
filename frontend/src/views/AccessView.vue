<script setup>
import { useRoute } from 'vue-router'
const route = useRoute()
</script>

<template>
  <main class="welcome">
    <template v-if="route.meta.missing"><h1>页面不存在</h1><p class="intro">请检查地址或返回账户首页。</p></template>
    <template v-else-if="route.meta.denied"><h1>没有访问权限</h1><p class="intro">此页面需要管理员身份。你的登录会话仍然有效。</p></template>
    <template v-else><p class="eyebrow">管理员身份确认</p><h1>管理员身份已确认。</h1><p class="intro">本阶段只验证访问权限。商品管理和统计看板按 15E、15F 逐步接入。</p></template>
    <RouterLink class="text-link" to="/welcome">返回账户首页 →</RouterLink>
  </main>
</template>
