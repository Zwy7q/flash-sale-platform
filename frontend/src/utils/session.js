// sessionStorage 只保存 Token，不保存可被篡改的管理员角色资料。
export const TOKEN_KEY = 'flash-sale-token'

export function readToken() {
  return sessionStorage.getItem(TOKEN_KEY) || ''
}

export function saveToken(token) {
  sessionStorage.setItem(TOKEN_KEY, token)
}

export function clearToken() {
  sessionStorage.removeItem(TOKEN_KEY)
}
