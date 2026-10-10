import { afterEach, beforeEach, expect, it } from 'vitest'
import axios from 'axios'
import { http, setUnauthorizedHandler } from './utils/http.js'
import { createAdminProductFlow } from './composables/adminProductsFlow.js'
import { validateProduct, productPayload } from './utils/productForm.js'

const sample = {
  id: 14, name: '原商品', description: '描述', price: 399, seckillPrice: 299,
  stock: 100, seckillStock: 20, status: 0,
  seckillStartAt: '2030-01-01T10:00:00', seckillEndAt: '2030-01-01T12:00:00',
}
function response(config, data) { return { config, data, status: 200, headers: {}, statusText: 'OK' } }
let flow
beforeEach(() => { setUnauthorizedHandler(null); flow = createAdminProductFlow() })
afterEach(() => flow.stop())

it('下架商品仍可读，编辑先获取最新详情，不修改列表对象', async () => {
  http.defaults.adapter = async (config) => {
    if (config.url === '/admin/products') { return response(config, [sample]) }
    return response(config, { ...sample, seckillStock: 17 })
  }
  await flow.load()
  await flow.edit(14)
  expect(flow.draft.value.seckillStock).toBe(17)
  flow.draft.value.name = '编辑中'
  expect(flow.products.value[0].name).toBe('原商品')
})

it('完整修改发送所有业务字段，没有 id 且日期保持本地时间', async () => {
  let saved
  http.defaults.adapter = async (config) => {
    if (config.method === 'put') {
      saved = JSON.parse(config.data)
      return response(config, { ...sample, ...saved })
    }
    if (config.url.endsWith('/14')) { return response(config, sample) }
    return response(config, [sample])
  }
  await flow.edit(14)
  flow.draft.value.name = '修改商品'
  await flow.save(flow.draft.value)
  expect(Object.keys(saved).sort()).toEqual(Object.keys(sample).filter((key) => key !== 'id').sort())
  expect(saved.seckillStartAt).toBe('2030-01-01T10:00:00')
  expect(flow.editorOpen.value).toBe(false)
})

it('新增使用 POST，快捷上架使用独立 status 接口', async () => {
  const writes = []
  http.defaults.adapter = async (config) => {
    if (config.method !== 'get') {
      writes.push([config.method, config.url, JSON.parse(config.data)])
      return response(config, { ...sample, status: 1 })
    }
    return response(config, [sample])
  }
  flow.create()
  await flow.save(sample)
  await flow.changeStatus(sample)
  expect(writes[0][0]).toBe('post')
  expect(writes[0][1]).toBe('/admin/products')
  expect(writes[1]).toEqual(['put', '/admin/products/14/status', { status: 1 }])
})

it('409 不自动重发，阻止旧表单继续保存，重新读取需要管理员主动确认', async () => {
  let puts = 0
  http.defaults.adapter = async (config) => {
    if (config.method === 'put') {
      puts += 1
      throw new axios.AxiosError('conflict', '', config, null, {
        status: 409, data: { code: 'STOCK_CHANGE_NOT_ALLOWED', message: '库存已变化' },
      })
    }
    if (config.url.endsWith('/14')) { return response(config, { ...sample, seckillStock: 16 }) }
    return response(config, [sample])
  }
  await flow.edit(14)
  flow.draft.value.seckillStock = 30
  await flow.save(flow.draft.value)
  await flow.save(flow.draft.value)
  expect(puts).toBe(1)
  expect(flow.conflict.value).toBe(true)
  expect(flow.draft.value.seckillStock).toBe(30)
  await flow.edit(14)
  expect(flow.draft.value.seckillStock).toBe(16)
  expect(flow.conflict.value).toBe(false)
})

it('离开页面中断读取且忽略迟到详情', async () => {
  let finish
  let signal
  http.defaults.adapter = (config) => new Promise((resolve) => {
    signal = config.signal
    finish = () => resolve(response(config, sample))
  })
  const editing = flow.edit(14)
  await Promise.resolve(); await Promise.resolve()
  flow.stop()
  expect(signal.aborted).toBe(true)
  finish()
  await editing
  expect(flow.editorOpen.value).toBe(false)
})

it('表单限制金额、整数库存和时间，错误输入不发送保存请求', async () => {
  expect(validateProduct(sample)).toBe('')
  expect(validateProduct({ ...sample, name: '' })).toContain('名称')
  expect(validateProduct({ ...sample, price: -1 })).toContain('价格')
  expect(validateProduct({ ...sample, price: 1.234 })).toContain('价格')
  expect(validateProduct({ ...sample, stock: 1.5 })).toContain('库存')
  expect(validateProduct({ ...sample, seckillEndAt: sample.seckillStartAt })).toContain('结束')
  expect(productPayload(sample)).not.toHaveProperty('id')
  let writes = 0
  http.defaults.adapter = async (config) => { writes += 1; return response(config, sample) }
  flow.create()
  await flow.save({ ...sample, stock: -1 })
  expect(writes).toBe(0)
})
