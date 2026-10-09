import { createApp } from 'vue'
import './style.css'
import App from './App.vue'
import { createPinia } from 'pinia'
import ElementPlus, { ElMessage } from 'element-plus'
import 'element-plus/dist/index.css'
import { router, installAuthGuard } from './router/index.js'
import { useAuthStore } from './stores/auth.js'
import { setUnauthorizedHandler } from './utils/http.js'

// 创建 Vue 应用，把 App.vue 渲染到 index.html 的 id=app 容器。
const app = createApp(App)
// Pinia 先安装，后面的导航守卫才能安全访问会话 Store。
app.use(createPinia())
app.use(ElementPlus)
const auth = useAuthStore()
setUnauthorizedHandler(() => {
  const wasInitialized = auth.initialized
  const returnTo = router.currentRoute.value.fullPath
  auth.logout()
  // 首次恢复时由正在执行的守卫处理跳转，避免两次导航互相打断。
  if (wasInitialized && router.currentRoute.value.path !== '/login') {
    ElMessage.warning('登录已过期，请重新登录。')
    router.replace({ path: '/login', query: { redirect: returnTo } })
  }
})
installAuthGuard()
app.use(router)
// 路由完成首次会话恢复后才挂载，避免未恢复资料时闪现受保护页面。
await router.isReady()
app.mount('#app')
