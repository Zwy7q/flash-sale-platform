import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import axios from 'axios'
import { http, setUnauthorizedHandler } from './utils/http.js'
import { createSeckillFlow } from './composables/seckillFlow.js'

// 只替换 HTTP 传输，实际运行状态管理、Axios 和计时器。
function response(config, status, data) {
  return { config, status, data, headers: {}, statusText: 'test' }
}
function reject(config, status, data) {
  throw new axios.AxiosError('HTTP ' + status, '', config, null, response(config, status, data))
}
let flow
beforeEach(() => {
  vi.useFakeTimers()
  sessionStorage.clear()
  setUnauthorizedHandler(null)
  flow = createSeckillFlow(2, 14)
})
afterEach(() => { flow.stop(); vi.useRealTimers() })

it('202 后每秒查询，200 才显示订单，提交不发送用户或金额', async () => {
  const paths = []
  http.defaults.adapter = async (config) => {
    paths.push(config.url)
    if (config.method === 'post') {
      expect(config.data).toBeUndefined()
      return response(config, 202, { result: 'SUCCESS', requestId: 'req-1' })
    }
    return response(config, 200, { id: 224, productId: 14, amount: 299, status: 'PENDING_PAYMENT' })
  }
  await flow.submit()
  expect(flow.order.value).toBeNull()
  expect(flow.busy.value).toBe(true)
  await vi.advanceTimersByTimeAsync(999)
  expect(paths).toHaveLength(1)
  await vi.advanceTimersByTimeAsync(1)
  expect(flow.order.value.id).toBe(224)
  expect(flow.busy.value).toBe(false)
  expect(paths).toEqual(['/seckill/products/14', '/seckill/requests/req-1'])
})

it('连续未知最多查询 15 次，手动继续只 GET 不重新 POST', async () => {
  let posts = 0
  let gets = 0
  http.defaults.adapter = async (config) => {
    if (config.method === 'post') { posts += 1 } else { gets += 1 }
    return response(config, 202, { requestId: 'req-2', result: 'RESULT_UNKNOWN' })
  }
  await flow.submit()
  await vi.advanceTimersByTimeAsync(20000)
  expect(gets).toBe(15)
  expect(posts).toBe(1)
  expect(flow.phase.value).toBe('unknown')
  expect(flow.canSubmit.value).toBe(false)
  flow.checkAgain()
  await vi.advanceTimersByTimeAsync(1000)
  expect(gets).toBe(16)
  expect(posts).toBe(1)
})

it('PUBLISH_UNKNOWN 保存编号并查询，不当作确定失败', async () => {
  http.defaults.adapter = async (config) => {
    if (config.method === 'post') {
      return reject(config, 503, { result: 'PUBLISH_UNKNOWN', requestId: 'req-3' })
    }
    return response(config, 200, { id: 225 })
  }
  await flow.submit()
  expect(flow.requestId.value).toBe('req-3')
  await vi.advanceTimersByTimeAsync(1000)
  expect(flow.order.value.id).toBe(225)
})

it('网络失败没有编号时也保留待确认状态，刷新不自动重发', async () => {
  http.defaults.adapter = async () => { throw new Error('timeout') }
  await flow.submit()
  expect(flow.phase.value).toBe('unknown')
  expect(flow.canSubmit.value).toBe(false)
  flow.stop()
  flow = createSeckillFlow(2, 14)
  expect(flow.phase.value).toBe('unknown')
  expect(flow.canSubmit.value).toBe(false)
  const otherUser = createSeckillFlow(3, 14)
  expect(otherUser.canSubmit.value).toBe(true)
  otherUser.stop()
})

it('重复点击只提交一次；离开页面中断查询且忽略迟到响应', async () => {
  let finishPost
  let finishGet
  let querySignal
  let posts = 0
  http.defaults.adapter = (config) => new Promise((resolve) => {
    if (config.method === 'post') {
      posts += 1
      finishPost = () => resolve(response(config, 202, { requestId: 'req-4' }))
    } else {
      querySignal = config.signal
      finishGet = () => resolve(response(config, 200, { id: 226 }))
    }
  })
  const submitted = flow.submit()
  await Promise.resolve(); await Promise.resolve()
  await flow.submit()
  expect(posts).toBe(1)
  finishPost()
  await submitted
  await vi.advanceTimersByTimeAsync(1000)
  flow.stop()
  expect(querySignal.aborted).toBe(true)
  finishGet()
  await vi.advanceTimersByTimeAsync(10000)
  expect(flow.order.value).toBeNull()
})

it('慢查询未结束时不叠加下一次请求', async () => {
  let gets = 0
  let finish
  http.defaults.adapter = (config) => {
    if (config.method === 'post') { return Promise.resolve(response(config, 202, { requestId: 'slow' })) }
    gets += 1
    return new Promise((resolve) => { finish = () => resolve(response(config, 202, {})) })
  }
  await flow.submit()
  await vi.advanceTimersByTimeAsync(6000)
  expect(gets).toBe(1)
  finish()
  await vi.advanceTimersByTimeAsync(1000)
  expect(gets).toBe(2)
})

it.each([
  [400, 'SECKILL_NOT_ACTIVE', '活动'],
  [409, 'OUT_OF_STOCK', '售罄'],
  [409, 'DUPLICATE_PURCHASE', '重复'],
  [404, null, '下架'],
])('明确业务拒绝 %s %s 显示对应提示', async (status, result, text) => {
  http.defaults.adapter = async (config) => reject(config, status, { result })
  await flow.submit()
  expect(flow.phase.value).toBe('rejected')
  expect(flow.message.value).toContain(text)
  expect(flow.busy.value).toBe(false)
})

it('刷新保留 requestId，同一用户可继续查询', async () => {
  http.defaults.adapter = async (config) => response(config, 202, { requestId: 'saved' })
  await flow.submit()
  flow.stop()
  flow = createSeckillFlow(2, 14)
  expect(flow.requestId.value).toBe('saved')
  expect(flow.canSubmit.value).toBe(false)
  flow.checkAgain()
  await vi.advanceTimersByTimeAsync(1000)
  expect(flow.attempts.value).toBe(1)
})
