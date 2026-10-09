// 回跳只能使用站内路径，排除公开会话页，避免循环跳转或外部地址。
export function safeRedirect(value) {
  if (typeof value !== 'string' || !value.startsWith('/') || value.startsWith('//') || value.includes('\\')) {
    return '/welcome'
  }
  if (/^\/(login|register|session)(\/|\?|$)/.test(value)) {
    return '/welcome'
  }
  return value
}

// 独立函数方便测试。导航守卫只改善体验，后端仍会重新检查 JWT 和权限。
export function createAuthGuard(auth) {
  return async (to) => {
    if (to.meta.public) {
      return true
    }
    try {
      await auth.restoreSession()
    } catch {
      return { path: '/session', query: { redirect: to.fullPath } }
    }
    if (!auth.isAuthenticated) {
      return { path: '/login', query: { redirect: to.fullPath } }
    }
    if (to.meta.admin && !auth.isAdmin) {
      return { path: '/forbidden' }
    }
    return true
  }
}
