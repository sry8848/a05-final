import { ADMIN_TOKEN_KEY } from '../utils/authSession.js'
import { buildApiUrl } from './base.js'

function createRequestError(message, status, result = null) {
  const error = new Error(message || '请求失败')
  error.status = status
  error.result = result
  return error
}

function getAdminToken() {
  return localStorage.getItem(ADMIN_TOKEN_KEY)
}

export async function importKnowledgeJsonl(file) {
  const token = getAdminToken()
  if (!token) {
    throw createRequestError('管理员登录已失效', 401)
  }

  const formData = new FormData()
  formData.append('file', file)

  const response = await fetch(buildApiUrl('/admin/knowledge/import-jsonl'), {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${token}`
    },
    body: formData
  })

  const payload = await response.json().catch(() => ({}))

  if (!response.ok) {
    const authExpired = response.status === 401 || response.status === 403
    throw createRequestError(
      authExpired ? '管理员登录已失效' : (payload.message || response.statusText || '请求失败'),
      response.status,
      payload.data || null
    )
  }

  if (payload.code !== 0) {
    throw createRequestError(
      payload.message || '请求失败',
      payload.code || 500,
      payload.data || null
    )
  }

  return payload.data
}
