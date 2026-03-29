const DEFAULT_API_BASE_PATH = '/api/v1'

function stripTrailingSlashes(value) {
  return value.replace(/\/+$/, '')
}

function isAbsoluteUrl(value) {
  return /^[a-z][a-z\d+.-]*:/i.test(value) || value.startsWith('//')
}

function normalizePath(path) {
  const value = String(path || '').trim()
  if (!value) return ''
  return value.startsWith('/') ? value : `/${value}`
}

export function resolveApiBaseUrl(rawBase = import.meta.env?.VITE_API_BASE_URL) {
  const value = String(rawBase || '').trim()
  if (!value) {
    return DEFAULT_API_BASE_PATH
  }

  const normalized = stripTrailingSlashes(value)
  if (!normalized || normalized === '/') {
    return DEFAULT_API_BASE_PATH
  }
  if (normalized === DEFAULT_API_BASE_PATH || normalized.endsWith(DEFAULT_API_BASE_PATH)) {
    return normalized
  }
  return `${normalized}${DEFAULT_API_BASE_PATH}`
}

export function buildApiUrl(path, rawBase = import.meta.env?.VITE_API_BASE_URL) {
  const normalizedPath = normalizePath(path)
  if (!normalizedPath) {
    return resolveApiBaseUrl(rawBase)
  }
  return `${resolveApiBaseUrl(rawBase)}${normalizedPath}`
}

export function resolveBackendUrl(pathOrUrl, rawBase = import.meta.env?.VITE_API_BASE_URL) {
  const value = String(pathOrUrl || '').trim()
  if (!value) return ''
  if (isAbsoluteUrl(value)) return value

  const apiBase = resolveApiBaseUrl(rawBase)
  const backendRoot = apiBase.endsWith(DEFAULT_API_BASE_PATH)
    ? apiBase.slice(0, -DEFAULT_API_BASE_PATH.length)
    : ''

  if (value.startsWith(DEFAULT_API_BASE_PATH) || value.startsWith('/')) {
    return backendRoot ? `${backendRoot}${value}` : value
  }

  return buildApiUrl(value, rawBase)
}
