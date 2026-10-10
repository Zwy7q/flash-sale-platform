// 空白表单不预设活动日期，避免误创建一个已经开始的活动。
export function emptyProduct() {
  return { name: '', description: '', price: 0, seckillPrice: 0, stock: 0,
    seckillStock: 0, status: 0, seckillStartAt: '', seckillEndAt: '' }
}

export function productPayload(value) {
  // 白名单复制：完整 PUT 的九个字段全部发送，不把 id 等服务器字段带入请求。
  return { name: value.name.trim(), description: value.description || '',
    price: value.price, seckillPrice: value.seckillPrice, stock: value.stock,
    seckillStock: value.seckillStock, status: value.status,
    seckillStartAt: value.seckillStartAt, seckillEndAt: value.seckillEndAt }
}

export function validateProduct(value) {
  if (typeof value.name !== 'string' || !value.name.trim() || value.name.trim().length > 128) {
    return '商品名称不能为空，且不能超过 128 个字符。'
  }
  if (value.description && value.description.length > 512) { return '商品描述不能超过 512 个字符。' }
  for (const field of ['price', 'seckillPrice']) {
    const amount = value[field]
    if (typeof amount !== 'number' || !Number.isFinite(amount) || amount < 0 || amount > 99999999.99
        || Math.abs(amount * 100 - Math.round(amount * 100)) > 0.000001) {
      return '价格必须是非负金额，最多两位小数，且不超过 99999999.99。'
    }
  }
  for (const field of ['stock', 'seckillStock']) {
    if (!Number.isInteger(value[field]) || value[field] < 0 || value[field] > 2147483647) {
      return '库存必须是非负整数，且不能超过 2147483647。'
    }
  }
  if (value.status !== 0 && value.status !== 1) { return '状态只能为上架或下架。' }
  // 日期控件输出无时区的本地 ISO 字符串，不使用 toISOString 转为 UTC。
  const pattern = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}$/
  if (!pattern.test(value.seckillStartAt) || !pattern.test(value.seckillEndAt)
      || !Number.isFinite(new Date(value.seckillStartAt).getTime())
      || !Number.isFinite(new Date(value.seckillEndAt).getTime())) {
    return '请完整选择活动开始和结束时间。'
  }
  if (value.seckillEndAt <= value.seckillStartAt) { return '结束时间必须晚于开始时间。' }
  return ''
}
