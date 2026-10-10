// null 表示未知，不能用 value || 0 把未知数据假装成零。
export function money(value) {
  if (value === null || value === undefined) { return '—' }
  return Number(value).toFixed(2)
}
export function localTime(value) {
  if (!value) { return '未设置' }
  // 后端活动时间是无时区的本地时间，直接展示，避免转 UTC 后改变排期。
  return value.replace('T', ' ')
}
export function orderStatus(value) {
  const labels = { PENDING_PAYMENT: '待支付', PAID: '已支付', CANCELLED: '已取消', PAYMENT_TIMEOUT: '超时关闭' }
  return labels[value] || value
}

// null 是服务器无法确认的值；0 是已确认的零，必须分别显示。
export function statisticNumber(value) {
  if (value === null || value === undefined) { return '未知' }
  return String(value)
}
export function statisticPercent(value) {
  if (value === null || value === undefined) { return '未知' }
  return Number(value).toFixed(2) + '%'
}
export function redisStockLabel(status) {
  const labels = { AVAILABLE: '可读取', NOT_WARMED_UP: '未预热', UNAVAILABLE: '读取不可用', INVALID: '数据异常' }
  return labels[status] || '状态未知'
}
export function requestStatusLabel(status) {
  const labels = { AVAILABLE: '采集可用', NOT_STARTED: '尚无采集记录', UNAVAILABLE: '采集不可用', INVALID: '数据异常' }
  return labels[status] || '状态未知'
}
export function utcTime(value) {
  // 故障时也会返回 null，不能据此断言“从未采集”。是否开始看 requests.status。
  if (!value) { return '时间未知' }
  const date = new Date(value)
  if (!Number.isFinite(date.getTime())) { return '时间未知' }
  // 统计时间带 Z（UTC）；与商品 LocalDateTime 不同，指定上海时区转换显示。
  return new Intl.DateTimeFormat('zh-CN', { timeZone: 'Asia/Shanghai', year: 'numeric',
    month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit',
    hourCycle: 'h23' }).format(date)
}
