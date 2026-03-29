import { ADMIN_TOKEN_KEY } from '../utils/authSession.js'
import { buildApiUrl } from './base.js'

function createRequestError(message, status) {
  const error = new Error(message || '请求失败')
  error.status = status
  return error
}

function getAdminToken() {
  return localStorage.getItem(ADMIN_TOKEN_KEY)
}

async function request(path, options = {}) {
  const token = getAdminToken()
  if (!token) {
    throw createRequestError('管理员登录已失效', 401)
  }

  const response = await fetch(buildApiUrl(path), {
    method: 'GET',
    cache: options.cache,
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json'
    }
  })

  const payload = await response.json().catch(() => ({}))

  if (!response.ok) {
    throw createRequestError(
      payload.message || response.statusText || '请求失败',
      response.status
    )
  }

  if (payload.code !== 0) {
    throw createRequestError(payload.message || '请求失败', response.status || 500)
  }

  return payload.data
}

async function requestPublic(path, options = {}) {
  const response = await fetch(buildApiUrl(path), {
    method: 'GET',
    cache: options.cache
  })

  const payload = await response.json().catch(() => ({}))

  if (!response.ok) {
    throw createRequestError(
      payload.message || response.statusText || '请求失败',
      response.status
    )
  }

  if (payload.code !== 0) {
    throw createRequestError(payload.message || '请求失败', response.status || 500)
  }

  return payload.data
}

export function getAdminDashboardOverview() {
  return request('/admin/dashboard/overview')
}

export function getAdminDashboardTrends(days = 7) {
  return request(`/admin/dashboard/trends?days=${encodeURIComponent(days)}`)
}

export function getAdminDashboardModels(windowMinutes = 15) {
  return request(`/admin/dashboard/models?windowMinutes=${encodeURIComponent(windowMinutes)}`)
}

export function getAdminDashboardPrompts() {
  return request('/admin/dashboard/prompts')
}

export async function getAdminDashboardSystemPing() {
  const data = await requestPublic('/system/ping', { cache: 'no-store' })

  if (!data || typeof data.serverTime !== 'string' || !data.serverTime.trim()) {
    throw createRequestError('系统接口返回了无效数据', 500)
  }

  return {
    serverTime: data.serverTime
  }
}
