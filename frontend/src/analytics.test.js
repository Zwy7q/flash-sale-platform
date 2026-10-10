import { afterEach, beforeEach, expect, it } from 'vitest'
import axios from 'axios'
import { http, setUnauthorizedHandler } from './utils/http.js'
import { createAnalyticsFlow } from './composables/analyticsFlow.js'
import { statisticNumber, statisticPercent, redisStockLabel, requestStatusLabel, utcTime } from './utils/display.js'

const orders = { createdOrderCount: 4, pendingPaymentOrderCount: 1, paidOrderCount: 1,
  cancelledOrderCount: 1, paymentTimeoutOrderCount: 1, paidAmount: 99, paymentConversionRatePercent: 25 }
const requests = { status: 'NOT_STARTED', requestCount: 0, acceptedRequestCount: 0,
  successRatePercent: 0, collectionStartedAt: null }
const product = { productId: 14, name: '测试商品', status: 0, ordinaryStock: 10,
  databaseSeckillStock: 5, totalStock: 15, redisSeckillStock: null,
  redisStockStatus: 'NOT_WARMED_UP', orders, requests }
const overview = { productCount: 1, onSaleProductCount: 0, ordinaryStock: 10,
  databaseSeckillStock: 5, totalStock: 15, redisSeckillStock: null,
  knownRedisStockProductCount: 0, orders, requests, generatedAt: '2026-10-10T00:00:00Z' }
function response(config, data) { return { config, data, status: 200, headers: {}, statusText: 'OK' } }
let flow
let adapter
beforeEach(() => { adapter = http.defaults.adapter; setUnauthorizedHandler(null); flow = createAnalyticsFlow() })
afterEach(() => { flow.stop(); http.defaults.adapter = adapter })

it('零是真实数字，未知库存和故障计数不能显示为零，UTC 时间转为北京时间', () => {
  expect(statisticNumber(0)).toBe('0')
  expect(statisticNumber(null)).toBe('未知')
  expect(statisticPercent(0)).toBe('0.00%')
  expect(statisticPercent(null)).toBe('未知')
  expect(redisStockLabel('NOT_WARMED_UP')).toBe('未预热')
  expect(redisStockLabel('UNAVAILABLE')).toBe('读取不可用')
  expect(requestStatusLabel('NOT_STARTED')).toBe('尚无采集记录')
  expect(utcTime('2026-10-10T00:00:00Z')).toContain('08:00:00')
  expect(utcTime(null)).toBe('时间未知')
})

it('读取后端聚合数据，保留 null、零和下架商品，不发送写请求', async () => {
  const calls = []
  http.defaults.adapter = async (config) => {
    calls.push([config.method, config.url])
    if (config.url.endsWith('/overview')) { return response(config, overview) }
    return response(config, [product])
  }
  await flow.refresh()
  expect(flow.overview.value.orders.paidAmount).toBe(99)
  expect(flow.overview.value.redisSeckillStock).toBeNull()
  expect(flow.products.value[0].requests.requestCount).toBe(0)
  expect(calls).toEqual([['get', '/admin/analytics/overview'], ['get', '/admin/analytics/products']])
})

it('其中一个接口失败时清除该区旧数据，另一部分仍可展示，手动刷新可恢复', async () => {
  http.defaults.adapter = async (config) => response(config, config.url.endsWith('/overview') ? overview : [product])
  await flow.refresh()
  http.defaults.adapter = async (config) => {
    if (config.url.endsWith('/overview')) { throw new axios.AxiosError('network', '', config) }
    return response(config, [])
  }
  await flow.refresh()
  expect(flow.overview.value).toBeNull()
  expect(flow.overviewError.value).toContain('连接失败')
  expect(flow.products.value).toEqual([])
  expect(flow.productsError.value).toBe('')
  http.defaults.adapter = async (config) => response(config, config.url.endsWith('/overview') ? overview : [product])
  await flow.refresh()
  expect(flow.overviewError.value).toBe('')
})

it('商品统计详情走独立 GET，404 清除旧详情，不展示为零', async () => {
  http.defaults.adapter = async (config) => {
    if (config.url.endsWith('/15')) {
      throw new axios.AxiosError('missing', '', config, null, { status: 404, data: '' })
    }
    return response(config, product)
  }
  await flow.selectProduct(14)
  expect(flow.detail.value.productId).toBe(14)
  await flow.selectProduct(15)
  expect(flow.detail.value).toBeNull()
  expect(flow.detailError.value).toContain('不存在')
})

it('刷新期间重复点击不重复请求，离开页面中断并忽略迟到响应', async () => {
  const pending = []
  http.defaults.adapter = (config) => new Promise((resolve) => pending.push({ config, resolve }))
  const loading = flow.refresh()
  await Promise.resolve(); await Promise.resolve()
  await flow.refresh()
  expect(pending).toHaveLength(2)
  flow.stop()
  for (const item of pending) {
    expect(item.config.signal.aborted).toBe(true)
    item.resolve(response(item.config, overview))
  }
  await loading
  expect(flow.overview.value).toBeNull()
})

it('切换商品后旧详情响应不能覆盖新选择', async () => {
  let finishOld
  http.defaults.adapter = async (config) => {
    if (config.url.endsWith('/14')) { return new Promise((resolve) => { finishOld = () => resolve(response(config, product)) }) }
    return response(config, { ...product, productId: 15 })
  }
  const old = flow.selectProduct(14)
  await Promise.resolve(); await Promise.resolve()
  await flow.selectProduct(15)
  finishOld()
  await old
  expect(flow.detail.value.productId).toBe(15)
})
