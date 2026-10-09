import { http } from '../utils/http.js'

// skipAuth 表示公开入口，不要携带浏览器中可能已经过期的 Token。
export function loginRequest(credentials) {
  return http.post('/auth/login', credentials, { skipAuth: true })
}
export function registerRequest(form) {
  return http.post('/users', form, { skipAuth: true })
}
export function currentUserRequest() {
  return http.get('/users/me')
}
