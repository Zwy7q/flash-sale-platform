import { ref } from 'vue'
import { listOrdersRequest, payOrderRequest, cancelOrderRequest } from '../api/orders.js'
import { errorMessage } from '../utils/http.js'

// 订单页的组合函数：统一查询、支付、取消和刷新，页面只负责展示。
export function createOrderListFlow() {
  const orders = ref([])
  const loading = ref(false)
  const error = ref('')
  const actionId = ref(null)
  const actionMessage = ref('')
  const stopped = ref(false)

  async function load() {
    if (stopped.value || loading.value) { return }
    loading.value = true
    error.value = ''
    try {
      const response = await listOrdersRequest()
      if (!stopped.value) { orders.value = response.data }
    } catch (exception) {
      if (!stopped.value) { error.value = errorMessage(exception, '订单加载失败，请稍后重试。') }
    } finally {
      loading.value = false
    }
  }

  function replaceOrder(updated) {
    const index = orders.value.findIndex((item) => item.id === updated.id)
    if (index >= 0) {
      orders.value[index] = updated
    } else {
      orders.value.unshift(updated)
    }
  }

  function canPay(order) { return order.status === 'PENDING_PAYMENT' }
  function canCancel(order) { return order.status === 'PENDING_PAYMENT' }

  async function operate(order, operation) {
    if (stopped.value) {
      return
    }
    if (actionId.value !== null) {
      return
    }
    if (operation === 'pay' && !canPay(order)) {
      return
    }
    if (operation === 'cancel' && !canCancel(order)) {
      return
    }
    actionId.value = order.id
    actionMessage.value = ''
    try {
      let response
      if (operation === 'pay') {
        response = await payOrderRequest(order.id)
      } else {
        response = await cancelOrderRequest(order.id)
      }
      if (!stopped.value) {
        replaceOrder(response.data)
        actionMessage.value = operation === 'pay' ? '模拟支付成功，订单状态已由服务端更新。' : '订单已取消；是否能重新购买以服务端资格校验为准。'
      }
    } catch (exception) {
      if (stopped.value) { return }
      const status = exception.response?.status
      const code = exception.response?.data?.code
      if (status === 409 || status === 503 || status === 404) {
        // 状态冲突时重新读服务端，不能在前端猜测状态。
        await load()
        if (code === 'STOCK_RELEASE_PENDING') {
          actionMessage.value = '订单已进入取消处理，但库存释放待确认，请稍后刷新。'
        } else if (operation === 'pay') {
          actionMessage.value = '订单状态已变化，页面已重新查询，请以最新状态为准。'
        } else {
          actionMessage.value = '订单当前不能取消，页面已重新查询，请以最新状态为准。'
        }
      } else {
        actionMessage.value = errorMessage(exception, '操作失败，请稍后重试。')
      }
    } finally {
      actionId.value = null
    }
  }

  function pay(order) { return operate(order, 'pay') }
  function cancel(order) { return operate(order, 'cancel') }
  function stop() { stopped.value = true }

  return { orders, loading, error, actionId, actionMessage, load, pay, cancel, canPay, canCancel, stop }
}
