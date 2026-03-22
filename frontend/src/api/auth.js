/**
 * 认证相关 API（登录、注册、发送验证码）。
 * 与后端 /api/v1/auth 约定：code=0 成功，非 0 为业务错误，message 为错误说明。
 */

const BASE = '/api/v1'

/**
 * 统一请求：解析 JSON，code 非 0 时抛出 Error(message)。
 * @param {string} path - 路径，如 '/auth/register'
 * @param {RequestInit} options - fetch 选项，可含 body（对象会 JSON.stringify）
 * @returns {Promise<any>} 成功时返回 data 字段
 */
async function request(path, options = {}) {
  const url = BASE + path
  const headers = { 'Content-Type': 'application/json', ...options.headers }
  if (options.authToken) {
    headers.Authorization = `Bearer ${options.authToken}`
  }
  let body = options.body
  if (body && typeof body === 'object' && !(body instanceof FormData)) {
    body = JSON.stringify(body)
  }
  const res = await fetch(url, { ...options, headers, body })
  const json = await res.json().catch(() => ({}))
  if (!res.ok) {
    throw new Error(json.message || res.statusText || '请求失败')
  }
  if (json.code !== 0) {
    throw new Error(json.message || '请求失败')
  }
  return json.data
}

/**
 * 发送邮箱验证码
 * @param {string} email
 * @param {'login'|'register'} scene
 * @returns {Promise<{ devCode?: string }>} 开发环境可能返回 devCode
 */
export function sendEmailCode(email, scene) {
  return request('/auth/email-code/send', {
    method: 'POST',
    body: { email: email.trim(), scene }
  })
}

/**
 * 邮箱 + 密码登录
 * @param {string} email
 * @param {string} password
 * @returns {Promise<{ token: string, userId: string, nickname: string }>}
 */
export function loginByPassword(email, password) {
  return request('/auth/login/password', {
    method: 'POST',
    body: { email: email.trim(), password }
  })
}

/**
 * 邮箱 + 验证码登录
 * @param {string} email
 * @param {string} code
 * @returns {Promise<{ token: string, userId: string, nickname: string }>}
 */
export function loginByEmailCode(email, code) {
  return request('/auth/login/email-code', {
    method: 'POST',
    body: { email: email.trim(), code: code.trim() }
  })
}

/**
 * 管理员账号 + 密码登录
 * @param {string} username
 * @param {string} password
 * @returns {Promise<{ token: string, username: string, displayName: string }>}
 */
export function loginAdmin(username, password) {
  return request('/auth/admin/login', {
    method: 'POST',
    body: { username: username.trim(), password }
  })
}

export function getCurrentUser(token) {
  return request('/auth/me', {
    method: 'GET',
    authToken: token
  })
}

export function getCurrentAdmin(token) {
  return request('/auth/admin/me', {
    method: 'GET',
    authToken: token
  })
}

export function logoutUser(token) {
  return request('/auth/logout', {
    method: 'POST',
    authToken: token
  })
}

export function logoutAdmin(token) {
  return request('/auth/admin/logout', {
    method: 'POST',
    authToken: token
  })
}

/**
 * 注册
 * @param {{ email: string, code: string, nickname: string, password: string }}
 */
export function register({ email, code, nickname, password }) {
  return request('/auth/register', {
    method: 'POST',
    body: {
      email: email.trim(),
      code: code.trim(),
      nickname: nickname.trim(),
      password
    }
  })
}
