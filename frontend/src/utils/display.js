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
