import { http } from '../utils/http.js'

export function submitSeckillRequest(productId, signal) {
  // 无请求体：用户来自 JWT，价格与库存由后端决定。POST 不做自动重试。
  return http.post('/seckill/products/' + encodeURIComponent(productId), undefined, { signal })
}
export function seckillResultRequest(requestId, signal) {
  return http.get('/seckill/requests/' + encodeURIComponent(requestId), { signal })
}
