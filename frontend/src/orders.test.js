import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import axios from 'axios'
import { http, setUnauthorizedHandler } from './utils/http.js'
import { createOrderListFlow } from './composables/ordersFlow.js'

function response(config, status, data) { return { config, status, data, headers: {}, statusText: 'test' } }
function failure(config, status, data) {
  throw new axios.AxiosError('HTTP ' + status, '', config, null, response(config, status, data))
}

let flow
const pending = { id: 10, productId: 14, amount: 299, status: 'PENDING_PAYMENT' }
beforeEach(() => {
  setUnauthorizedHandler(null)
  flow = createOrderListFlow()
})
afterEach(() => flow.stop())

it('加载当前用户订单，空数组也是成功结果', async () => {
  http.defaults.adapter = async (config) => response(config, 200, [])
  await flow.load()
  expect(flow.orders.value).toEqual([])
  expect(flow.loading.value).toBe(false)
})

it('支付只发送订单 ID，成功后用服务端返回状态替换订单', async () => {
  const calls = []
  http.defaults.adapter = async (config) => {
    calls.push(config.url)
    if (config.method === 'get') return response(config, 200, [pending])
    return response(config, 200, { ...pending, status: 'PAID' })
  }
  await flow.load()
  await flow.pay(pending)
  expect(calls).toEqual(['/orders', '/orders/10/pay'])
  expect(flow.orders.value[0].status).toBe('PAID')
})

it('取消成功后显示已取消并允许之后重新购买', async () => {
  http.defaults.adapter = async (config) => {
    if (config.method === 'get') return response(config, 200, [pending])
    return response(config, 200, { ...pending, status: 'CANCELLED' })
  }
  await flow.load()
  await flow.cancel(pending)
  expect(flow.orders.value[0].status).toBe('CANCELLED')
  expect(flow.canCancel(flow.orders.value[0])).toBe(false)
})

it('支付冲突会重新查询订单，不伪造支付成功', async () => {
  let gets = 0
  http.defaults.adapter = async (config) => {
    if (config.method === 'get') {
      gets += 1
      return response(config, 200, [{ ...pending, status: 'PAYMENT_TIMEOUT' }])
    }
    return failure(config, 409, { code: 'ORDER_NOT_PAYABLE', message: '订单已关闭，不能支付' })
  }
  await flow.load()
  await flow.pay(pending)
  expect(gets).toBe(2)
  expect(flow.orders.value[0].status).toBe('PAYMENT_TIMEOUT')
  expect(flow.actionMessage.value).toContain('订单状态已变化')
})

it('库存释放待确认时显示提示并刷新订单', async () => {
  let gets = 0
  http.defaults.adapter = async (config) => {
    if (config.method === 'get') { gets += 1; return response(config, 200, [pending]) }
    return failure(config, 503, { code: 'STOCK_RELEASE_PENDING', message: '库存释放尚未确认' })
  }
  await flow.load()
  await flow.cancel(pending)
  expect(gets).toBe(2)
  expect(flow.orders.value[0].status).toBe('PENDING_PAYMENT')
  expect(flow.actionMessage.value).toContain('释放待确认')
})

it('同一订单操作中禁止重复点击', async () => {
  let finish
  let calls = 0
  http.defaults.adapter = (config) => {
    if (config.method === 'get') return Promise.resolve(response(config, 200, [pending]))
    calls += 1
    return new Promise((resolve) => { finish = () => resolve(response(config, 200, { ...pending, status: 'PAID' })) })
  }
  await flow.load()
  const first = flow.pay(pending)
  await Promise.resolve()
  await Promise.resolve()
  await flow.pay(pending)
  expect(calls).toBe(1)
  finish()
  await first
  expect(flow.orders.value[0].status).toBe('PAID')
})
