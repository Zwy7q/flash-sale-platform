import { http } from '../utils/http.js'

// /api 已包含在共享 baseURL 中；管理员权限始终由后端重新校验。
export function adminProductsRequest(signal) {
  return http.get('/admin/products', { signal })
}
export function adminProductRequest(id, signal) {
  return http.get('/admin/products/' + encodeURIComponent(id), { signal })
}
export function createProductRequest(payload, signal) {
  return http.post('/admin/products', payload, { signal })
}
export function updateProductRequest(id, payload, signal) {
  return http.put('/admin/products/' + encodeURIComponent(id), payload, { signal })
}
export function productStatusRequest(id, status, signal) {
  return http.put('/admin/products/' + encodeURIComponent(id) + '/status', { status }, { signal })
}

// 统计由后端聚合 MySQL 和 Redis，前端只读取和展示，不自行累计订单。
export function analyticsOverviewRequest(signal) {
  return http.get('/admin/analytics/overview', { signal })
}
export function analyticsProductsRequest(signal) {
  return http.get('/admin/analytics/products', { signal })
}
export function analyticsProductRequest(id, signal) {
  return http.get('/admin/analytics/products/' + encodeURIComponent(id), { signal })
}
