import { ref } from 'vue'
import { analyticsOverviewRequest, analyticsProductsRequest, analyticsProductRequest } from '../api/admin.js'
import { errorMessage } from '../utils/http.js'

export function createAnalyticsFlow() {
  const overview = ref(null)
  const products = ref([])
  const loading = ref(false)
  const overviewError = ref('')
  const productsError = ref('')
  const detail = ref(null)
  const detailLoading = ref(false)
  const detailError = ref('')
  const selectedId = ref(null)
  const controller = new AbortController()
  let stopped = false
  let detailVersion = 0

  function closeDetail() {
    // 即使旧详情请求稍后才返回，也不能覆盖新选择或已经关闭的面板。
    detailVersion += 1
    selectedId.value = null
    detail.value = null
    detailError.value = ''
    detailLoading.value = false
  }

  async function refresh() {
    if (stopped || loading.value) { return }
    loading.value = true
    overviewError.value = ''
    productsError.value = ''
    closeDetail()
    // 两个接口独立读取。一个失败时另一个仍可显示，不能伪造失败部分为全零。
    const results = await Promise.allSettled([
      analyticsOverviewRequest(controller.signal), analyticsProductsRequest(controller.signal),
    ])
    if (stopped) { return }
    if (results[0].status === 'fulfilled') {
      overview.value = results[0].value.data
    } else {
      overview.value = null
      overviewError.value = errorMessage(results[0].reason, '统计总览读取失败，请重试。')
    }
    if (results[1].status === 'fulfilled') {
      products.value = results[1].value.data
    } else {
      products.value = []
      productsError.value = errorMessage(results[1].reason, '商品统计读取失败，请重试。')
    }
    loading.value = false
  }

  async function selectProduct(id) {
    if (stopped || loading.value) { return }
    const version = ++detailVersion
    selectedId.value = id
    detail.value = null
    detailError.value = ''
    detailLoading.value = true
    try {
      const response = await analyticsProductRequest(id, controller.signal)
      if (!stopped && version === detailVersion) { detail.value = response.data }
    } catch (exception) {
      if (!stopped && version === detailVersion) {
        if (exception.response?.status === 404) {
          detailError.value = '该商品不存在，请刷新统计列表。'
        } else {
          detailError.value = errorMessage(exception, '商品统计详情读取失败。')
        }
      }
    } finally {
      if (!stopped && version === detailVersion) { detailLoading.value = false }
    }
  }

  function stop() {
    stopped = true
    controller.abort()
    // 页面卸载后不再应用返回数据，不启动任何统计轮询。
  }
  return { overview, products, loading, overviewError, productsError, detail,
    detailLoading, detailError, selectedId, refresh, selectProduct, closeDetail, stop }
}
