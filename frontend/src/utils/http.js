import axios from 'axios'
import { readToken } from './session.js'

// 所有业务 API 共用一个实例。baseURL 已有 /api，具体方法只写 /users/me 等后缀。
export const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 10000,
})

// main.js 注册退出处理，避免 HTTP 工具直接依赖 Pinia 和 Router 形成循环引用。
let unauthorizedHandler = null
export function setUnauthorizedHandler(handler) {
  unauthorizedHandler = handler
}

http.interceptors.request.use((config) => {
  if (!config.skipAuth) {
    const token = readToken()
    if (token) {
      config.headers.set('Authorization', 'Bearer ' + token)
      // 记录本次请求用的 Token，迟到的旧请求不能退出新登录会话。
      config.sessionToken = token
    }
  }
  return config
})

http.interceptors.response.use(
  (response) => response, // 保留 status 和 data，后续秒杀要区分 200 / 202。
  (error) => {
    const config = error.config
    if (error.response && error.response.status === 401 && config && !config.skipAuth) {
      const currentToken = readToken()
      if (currentToken && config.sessionToken === currentToken && unauthorizedHandler) {
        // 处理函数同步清理 Token，后来的并发 401 就不会重复触发。
        unauthorizedHandler()
      }
    }
    // 403、网络错误和公开登录的 401 交给页面处理，不自动清除会话。
    return Promise.reject(error)
  },
)

export function errorMessage(error, fallback) {
  if (error.response && error.response.data && error.response.data.message) {
    return error.response.data.message
  }
  if (!error.response) {
    return '连接失败或请求超时，请检查后端服务与网络。'
  }
  return fallback
}
