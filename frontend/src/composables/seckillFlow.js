import { computed, ref } from 'vue'
import { submitSeckillRequest, seckillResultRequest } from '../api/seckill.js'
import { errorMessage } from '../utils/http.js'

// 组合函数负责秒杀状态和轮询，页面负责展示；生命周期由页面调用 stop 收尾。
export function createSeckillFlow(userId, productId) {
  const phase = ref('idle')
  const message = ref('提交后会查询订单结果，请勿重复提交。')
  const requestId = ref('')
  const order = ref(null)
  const attempts = ref(0)
  const busy = computed(() => phase.value === 'submitting' || phase.value === 'polling')
  const canSubmit = computed(() => phase.value === 'idle' || phase.value === 'rejected')
  // 按用户和商品隔离记录。刷新不能使一次未确认的提交变成“从未提交”。
  const storageKey = 'flash-sale:pending:' + userId + ':' + productId
  let timer = null
  let controller = null
  let stopped = false

  try {
    const saved = JSON.parse(sessionStorage.getItem(storageKey))
    if (saved) {
      requestId.value = saved.requestId || ''
      phase.value = 'unknown'
      message.value = '上次提交结果待确认，请继续查询，或通过订单接口核对。'
    }
  } catch {
    // 存储内容损坏时也不能假定之前没有提交。
    phase.value = 'unknown'
    message.value = '本地请求记录无法读取，请先通过订单接口核对。'
  }

  function rememberPending() {
    sessionStorage.setItem(storageKey, JSON.stringify({ requestId: requestId.value }))
  }

  function markUnknown(text) {
    phase.value = 'unknown'
    message.value = text
  }

  function scheduleQuery() {
    // setTimeout 在上一次查询完成后才安排下一次，慢请求不会叠加。
    timer = setTimeout(queryOnce, 1000)
  }

  function checkAgain() {
    if (stopped || busy.value || !requestId.value || order.value) { return }
    attempts.value = 0
    phase.value = 'polling'
    message.value = '正在查询订单结果……'
    scheduleQuery()
  }

  async function queryOnce() {
    if (stopped) { return }
    attempts.value += 1
    controller = new AbortController()
    try {
      const response = await seckillResultRequest(requestId.value, controller.signal)
      if (stopped) { return }
      if (response.status === 200) {
        order.value = response.data
        phase.value = 'success'
        message.value = '订单已创建，状态以服务端返回为准。'
        sessionStorage.removeItem(storageKey)
        return
      }
      if (response.status !== 202) {
        markUnknown('查询返回了未识别的状态，请稍后核对订单。')
        return
      }
      if (attempts.value >= 15) {
        markUnknown('已查询 15 次，结果仍待确认。可稍后继续查询，请勿重新提交。')
        return
      }
      message.value = '尚未查到订单，正在查询（' + attempts.value + '/15）……'
      scheduleQuery()
    } catch (error) {
      if (stopped) { return }
      markUnknown(errorMessage(error, '查询暂时失败，结果待确认，请稍后继续查询。'))
    }
  }

  async function submit() {
    if (stopped || !canSubmit.value) { return }
    phase.value = 'submitting'
    message.value = '正在提交，请稍候……'
    controller = new AbortController()
    try {
      // 先留记录再发请求；即使提交中关闭页面，也不会刷新后自动重发。
      rememberPending()
      const response = await submitSeckillRequest(productId, controller.signal)
      if (stopped) { return }
      requestId.value = response.data.requestId || ''
      rememberPending()
      markUnknown('请求已受理，订单尚未确认。')
      if (requestId.value) { checkAgain() }
    } catch (error) {
      if (stopped) { return }
      const status = error.response?.status
      const data = error.response?.data || {}
      const result = data.result
      // 这些响应能明确判断本次未受理；超时、500、发布失败不能做同样推断。
      if (status === 400 || status === 404 || status === 409 || status === 401 || status === 403 || result === 'STOCK_NOT_WARMED_UP') {
        sessionStorage.removeItem(storageKey)
        phase.value = 'rejected'
        if (result === 'OUT_OF_STOCK') {
          message.value = '商品已售罄。'
        } else if (result === 'DUPLICATE_PURCHASE') {
          message.value = '重复购买：你已有购买记录，请查询自己的订单。'
        } else if (status === 400) {
          message.value = '当前不在秒杀活动时间内。'
        } else if (status === 404) {
          message.value = '商品不存在或已下架。'
        } else {
          message.value = errorMessage(error, '请求被拒绝，请检查登录和权限。')
        }
        return
      }
      requestId.value = data.requestId || ''
      rememberPending()
      markUnknown('提交结果待确认，请勿再次提交。有请求编号可继续查询；没有编号请通过订单接口核对。')
      if (requestId.value) { checkAgain() }
    }
  }

  function stop() {
    stopped = true
    clearTimeout(timer)
    if (controller) { controller.abort() }
    // abort 只停止浏览器等待，不撤销后端扣库存或下单，因此保留未确认记录。
  }

  return { phase, message, requestId, order, attempts, busy, canSubmit, submit, checkAgain, stop }
}
