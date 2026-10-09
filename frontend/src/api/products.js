import { http } from '../utils/http.js'

// 列表只返回摘要，完整描述和库存需要另外查询详情。
export function productListRequest(signal) {
  return http.get('/products', { signal })
}
export function productDetailRequest(productId, signal) {
  return http.get('/products/' + encodeURIComponent(productId), { signal })
}
