import { http } from '../utils/http.js'

export function listOrdersRequest(signal) {
  return http.get('/orders', { signal })
}

export function payOrderRequest(orderId, signal) {
  // 后端从 JWT 和数据库读取用户、金额，前端只提交路径中的订单 ID。
  return http.post('/orders/' + encodeURIComponent(orderId) + '/pay', undefined, { signal })
}

export function cancelOrderRequest(orderId, signal) {
  return http.post('/orders/' + encodeURIComponent(orderId) + '/cancel', undefined, { signal })
}
