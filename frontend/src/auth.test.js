import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import axios from 'axios'
import { http, setUnauthorizedHandler } from './utils/http.js'
import { useAuthStore } from './stores/auth.js'
import { TOKEN_KEY } from './utils/session.js'
import { createAuthGuard, safeRedirect } from './router/guards.js'

// 只替换 HTTP 传输，实际执行 Axios 拦截器与 Pinia 会话逻辑。
function success(data, status = 200) {
  return async (config) => ({ data, status, statusText: 'OK', headers: {}, config })
}
function failure(status) {
  return async (config) => {
    const response = { status, data: '', headers: {}, config }
    throw new axios.AxiosError('HTTP ' + status, 'ERR_BAD_RESPONSE', config, null, response)
  }
}

beforeEach(() => {
  sessionStorage.clear()
  setActivePinia(createPinia())
  setUnauthorizedHandler(null)
})

describe('HTTP 与会话边界', () => {
  it('公开登录不携带旧 Token，且保留完整 HTTP 状态', async () => {
    sessionStorage.setItem(TOKEN_KEY, 'old-token')
    const handle = vi.fn()
    setUnauthorizedHandler(handle)
    http.defaults.adapter = async (config) => {
      expect(config.headers.get('Authorization')).toBeUndefined()
      return success({ token: 'new-token' }, 202)(config)
    }
    const response = await http.post('/auth/login', {}, { skipAuth: true })
    expect(response.status).toBe(202)
    expect(handle).not.toHaveBeenCalled()
  })

  it('登录 401 与普通用户 403 不触发全局退出', async () => {
    sessionStorage.setItem(TOKEN_KEY, 'token')
    const handle = vi.fn()
    setUnauthorizedHandler(handle)
    http.defaults.adapter = failure(401)
    await expect(http.post('/auth/login', {}, { skipAuth: true })).rejects.toBeDefined()
    http.defaults.adapter = failure(403)
    await expect(http.get('/admin/analytics/overview')).rejects.toBeDefined()
    expect(handle).not.toHaveBeenCalled()
    expect(sessionStorage.getItem(TOKEN_KEY)).toBe('token')
  })

  it('并发受保护请求的 401 只触发一次退出', async () => {
    sessionStorage.setItem(TOKEN_KEY, 'expired')
    const handle = vi.fn(() => sessionStorage.removeItem(TOKEN_KEY))
    setUnauthorizedHandler(handle)
    http.defaults.adapter = failure(401)
    await Promise.allSettled([http.get('/users/me'), http.get('/products')])
    expect(handle).toHaveBeenCalledTimes(1)
  })

  it('刷新会话从 /me 恢复用户，而不信任浏览器保存的角色', async () => {
    sessionStorage.setItem(TOKEN_KEY, 'valid-token')
    const auth = useAuthStore()
    http.defaults.adapter = async (config) => {
      expect(config.url).toBe('/users/me')
      expect(config.headers.get('Authorization')).toBe('Bearer valid-token')
      return success({ id: 2, nickname: '用户', isAdmin: false })(config)
    }
    await auth.restoreSession()
    expect(auth.isAuthenticated).toBe(true)
    expect(auth.isAdmin).toBe(false)
    auth.logout()
    expect(auth.user).toBeNull()
    expect(sessionStorage.getItem(TOKEN_KEY)).toBeNull()
  })

  it('断网恢复失败保留 Token，允许重试', async () => {
    sessionStorage.setItem(TOKEN_KEY, 'keep-token')
    const auth = useAuthStore()
    http.defaults.adapter = async () => { throw new Error('network failure') }
    await expect(auth.restoreSession()).rejects.toBeDefined()
    expect(sessionStorage.getItem(TOKEN_KEY)).toBe('keep-token')
    http.defaults.adapter = success({ id: 2, isAdmin: false })
    await auth.restoreSession()
    expect(auth.isAuthenticated).toBe(true)
  })

  it('退出后迟到的 /me 响应不能恢复已退出的会话', async () => {
    sessionStorage.setItem(TOKEN_KEY, 'old-token')
    const auth = useAuthStore()
    let finishRequest
    http.defaults.adapter = (config) => new Promise((resolve) => {
      finishRequest = () => resolve({ data: { id: 1, isAdmin: true }, status: 200, headers: {}, config })
    })
    const restoring = auth.restoreSession()
    await vi.waitFor(() => expect(finishRequest).toBeTypeOf('function'))
    auth.logout()
    finishRequest()
    await restoring
    expect(auth.isAuthenticated).toBe(false)
    expect(auth.user).toBeNull()
  })

  it('旧请求的 401 不清除新登录的 Token', async () => {
    sessionStorage.setItem(TOKEN_KEY, 'old')
    let finishRequest
    const handle = vi.fn()
    setUnauthorizedHandler(handle)
    http.defaults.adapter = (config) => new Promise((resolve, reject) => {
      finishRequest = () => reject(new axios.AxiosError('old 401', 'ERR_BAD_RESPONSE', config, null, { status: 401 }))
    })
    const pending = http.get('/products').catch(() => {})
    await vi.waitFor(() => expect(finishRequest).toBeTypeOf('function'))
    sessionStorage.setItem(TOKEN_KEY, 'new')
    finishRequest()
    await pending
    expect(handle).not.toHaveBeenCalled()
    expect(sessionStorage.getItem(TOKEN_KEY)).toBe('new')
  })

  it('成功登录保存 Token 与服务端角色，失败登录不会伪造身份', async () => {
    const auth = useAuthStore()
    http.defaults.adapter = failure(401)
    await expect(auth.login({ username: 'wrong', password: 'wrong' })).rejects.toBeDefined()
    expect(auth.isAuthenticated).toBe(false)
    http.defaults.adapter = success({ token: 'new', user: { id: 1, isAdmin: true } })
    await auth.login({ username: 'admin', password: 'test' })
    expect(auth.isAdmin).toBe(true)
    expect(sessionStorage.getItem(TOKEN_KEY)).toBe('new')
  })

  it('无会话跳登录，普通用户进管理页被拒绝但不退出', async () => {
    const auth = useAuthStore()
    const guard = createAuthGuard(auth)
    expect(await guard({ fullPath: '/welcome', meta: {} })).toEqual({ path: '/login', query: { redirect: '/welcome' } })
    http.defaults.adapter = success({ token: 'normal', user: { id: 2, isAdmin: false } })
    await auth.login({ username: 'user', password: 'test' })
    expect(await guard({ fullPath: '/admin', meta: { admin: true } })).toEqual({ path: '/forbidden' })
    expect(auth.isAuthenticated).toBe(true)
  })

  it('会话恢复失败进入重试页，不清除 Token', async () => {
    sessionStorage.setItem(TOKEN_KEY, 'keep')
    const auth = useAuthStore()
    http.defaults.adapter = async () => { throw new Error('offline') }
    const result = await createAuthGuard(auth)({ fullPath: '/welcome', meta: {} })
    expect(result).toEqual({ path: '/session', query: { redirect: '/welcome' } })
    expect(sessionStorage.getItem(TOKEN_KEY)).toBe('keep')
    expect(safeRedirect('//external.example')).toBe('/welcome')
    expect(safeRedirect('/login?redirect=/login')).toBe('/welcome')
    expect(safeRedirect('/admin')).toBe('/admin')
  })
})
