function normalizeResult(result) {
  if (!result || typeof result !== 'object') {
    return null
  }
  return {
    fileName: result.fileName || '',
    totalLines: Number(result.totalLines || 0),
    validLines: Number(result.validLines || 0),
    ingestedCount: Number(result.ingestedCount || 0),
    errors: Array.isArray(result.errors) ? result.errors : []
  }
}

export function createUploadQueueItem(file) {
  return {
    id: `${Date.now()}-${Math.random().toString(36).slice(2, 10)}`,
    name: file.name,
    size: file.size,
    progress: 0,
    status: 'pending',
    summary: '等待上传',
    result: null,
    expanded: false
  }
}

export function markUploading(item) {
  return {
    ...item,
    status: 'uploading',
    progress: 35,
    summary: '正在上传并校验...'
  }
}

export function markSuccess(item, result) {
  const normalized = normalizeResult(result)
  return {
    ...item,
    status: 'success',
    progress: 100,
    summary: `导入成功，共 ${normalized?.ingestedCount ?? 0} 条`,
    result: normalized,
    expanded: false
  }
}

export function markFailure(item, error) {
  const result = normalizeResult(error?.result)
  const summary = buildFailureSummary(error, result)
  return {
    ...item,
    status: 'failed',
    progress: 100,
    summary,
    result,
    expanded: false
  }
}

export function buildFailureSummary(error, result) {
  if (error?.status === 401 || error?.status === 403) {
    return '管理员登录已失效'
  }
  const firstError = result?.errors?.[0]
  if (firstError?.scope === 'line' && firstError.lineNo) {
    return `导入失败，第 ${firstError.lineNo} 行有错误`
  }
  if (firstError?.reason) {
    return firstError.reason
  }
  return error?.message || '导入失败'
}
