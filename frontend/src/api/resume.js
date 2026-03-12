/**
 * 简历管理相关 API，与 docs/api-design.md 第5 约定一致。
 * code=0 成功，非 0 报 Error(message)。
 */

const BASE = '/api/v1'

async function request(path, options = {}) {
  const url = BASE + path
  const headers = {
    'Content-Type': 'application/json',
    Authorization: getAuthHeader(),
    ...options.headers
  }
  let body = options.body
  if (body && typeof body === 'object' && !(body instanceof FormData)) {
    body = JSON.stringify(body)
  }
  const res = await fetch(url, { ...options, headers, body })
  const json = await res.json().catch(() => ({}))
  if (!res.ok) throw new Error(json.message || res.statusText || '请求失败')
  if (json.code !== 0) throw new Error(json.message || '请求失败')
  return json.data
}

/** 上传文件：不设置 Content-Type，由浏览器设置 multipart/form-data */
async function uploadRequest(path, formData) {
  const url = BASE + path
  const res = await fetch(url, {
    method: 'POST',
    headers: { Authorization: getAuthHeader() },
    body: formData
  })
  const json = await res.json().catch(() => ({}))
  const rawMessage = json.message || res.statusText || '上传失败'
  const message = toResumeUploadMessage(res.status, rawMessage)
  if (!res.ok) throw new Error(message)
  if (json.code !== 0) throw new Error(message)
  return json.data
}

/**
 * 将上传相关错误转为中文提示，便于前端展示
 * @param {number} status - HTTP 状态码
 * @param {string} rawMessage - 服务端或浏览器返回的原始文案
 */
function toResumeUploadMessage(status, rawMessage) {
  if (status === 413) return '上传文件过大，请选择不超过 20MB 的 PDF 或 DOCX 文件'
  const lower = (rawMessage || '').toLowerCase()
  if (/maximum.*size|size.*exceeded|exceeded.*size|file.*too large|payload too large/.test(lower)) {
    return '上传文件过大，请选择不超过 20MB 的 PDF 或 DOCX 文件'
  }
  return rawMessage || '上传失败'
}

function getAuthHeader() {
  const token = localStorage.getItem('aiInterviewToken') || localStorage.getItem('token')
  return token ? `Bearer ${token}` : ''
}

/**
 * 获取简历列表
 * @returns {Promise<Array<{ id: number, name: string, sourceType: string, parseStatus: string, isDefault: boolean, createdAt: string }>>}
 */
export function getResumes() {
  return request('/resumes', { method: 'GET' })
}

/**
 * 上传简历并发起解析
 * @param {File} file - PDF 或 DOCX
 * @returns {Promise<{ resumeId: number, parseStatus: string }>}
 */
export function uploadResume(file) {
  const formData = new FormData()
  formData.append('file', file)
  return uploadRequest('/resumes/upload', formData)
}

/**
 * 查询简历解析状态
 * @param {number} resumeId
 * @returns {Promise<{ resumeId: number, parseStatus: string, parsedTextPreview?: string }>}
 */
export function getParseStatus(resumeId) {
  return request(`/resumes/${resumeId}/parse-status`, { method: 'GET' })
}

/**
 * 获取简历详情（含 parsedText）
 * @param {number} resumeId
 */
export function getResume(resumeId) {
  return request(`/resumes/${resumeId}`, { method: 'GET' })
}

/**
 * 更新简历（名称、识别文本、是否默认）
 * @param {number} resumeId
 * @param {{ name?: string, parsedText?: string, isDefault?: boolean }} payload
 */
export function updateResume(resumeId, payload) {
  return request(`/resumes/${resumeId}`, {
    method: 'PUT',
    body: payload
  })
}

/** 设为默认简历 */
export function setDefaultResume(resumeId) {
  return request(`/resumes/${resumeId}/set-default`, { method: 'POST' })
}

/** 删除简历 */
export function deleteResume(resumeId) {
  return request(`/resumes/${resumeId}`, { method: 'DELETE' })
}

/** Create interview session */
export function createInterviewSession(payload) {
  return request('/interviews', {
    method: 'POST',
    body: payload
  })
}

/** Get interview session detail */
export function getInterviewSessionDetail(sessionId) {
  return request('/interviews/' + sessionId, { method: 'GET' })
}

/** Submit interview attempt */
export function submitInterviewAttempt(sessionId, payload) {
  return request('/interviews/' + sessionId + '/attempts', {
    method: 'POST',
    body: payload
  })
}

/** Finish interview session */
export function finishInterviewSession(sessionId) {
  return request('/interviews/' + sessionId + '/finish', { method: 'POST' })
}

/** Get interview report */
export function getInterviewReport(sessionId) {
  return request('/interviews/' + sessionId + '/report', { method: 'GET' })
}

/** Get learning recommendations for interview report */
export function getLearningRecommendations(sessionId) {
  return request('/interviews/' + sessionId + '/report/learning-recommendations', { method: 'GET' })
}

function parseSsePayload(raw) {
  if (!raw) return null
  try {
    return JSON.parse(raw)
  } catch (_) {
    return { text: raw }
  }
}

/** Stream interview question via fetch-based SSE */
export async function streamInterviewQuestion(sessionId, attemptId, handlers = {}, signal, options = {}) {
  const url = BASE + '/interviews/' + sessionId + '/questions/stream?attemptId=' + encodeURIComponent(attemptId)
  const headers = {
    Accept: 'text/event-stream',
    Authorization: getAuthHeader()
  }
  if (options.lastEventId != null && String(options.lastEventId).trim() !== '') {
    headers['Last-Event-ID'] = String(options.lastEventId)
  }

  const resp = await fetch(url, {
    method: 'GET',
    headers,
    signal
  })

  if (!resp.ok) {
    throw new Error('SSE connection failed: ' + resp.status)
  }
  if (!resp.body) {
    throw new Error('Streaming response is not supported in this browser')
  }

  const reader = resp.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let eventName = 'message'
  let eventId = ''
  let dataLines = []
  let terminalEvent = null
  let latestEventId = options.lastEventId ? String(options.lastEventId) : ''

  const dispatch = async () => {
    if (!dataLines.length) return
    if (eventId) {
      latestEventId = eventId
    }
    const payload = parseSsePayload(dataLines.join('\n'))
    const map = {
      start: handlers.onStart,
      delta: handlers.onDelta,
      tts_ready: handlers.onTtsReady,
      done: handlers.onDone,
      error: handlers.onError
    }
    const fn = map[eventName] || handlers.onMessage
    if (fn) {
      await fn(payload, eventName, eventId)
    }
    if (eventName === 'done' || eventName === 'error') {
      terminalEvent = eventName
    }
  }

  try {
    while (true) {
      const { value, done } = await reader.read()
      if (done) {
        await dispatch()
        break
      }
      buffer += decoder.decode(value, { stream: true })
      let lineEnd = buffer.indexOf('\n')
      while (lineEnd >= 0) {
        let line = buffer.slice(0, lineEnd)
        buffer = buffer.slice(lineEnd + 1)
        if (line.endsWith('\r')) line = line.slice(0, -1)

        if (!line) {
          await dispatch()
          eventName = 'message'
          eventId = ''
          dataLines = []
          lineEnd = buffer.indexOf('\n')
          continue
        }

        if (!line.startsWith(':')) {
          const colon = line.indexOf(':')
          const field = colon >= 0 ? line.slice(0, colon) : line
          const content = colon >= 0 ? line.slice(colon + 1).trimStart() : ''
          if (field === 'event') eventName = content
          if (field === 'id') eventId = content
          if (field === 'data') dataLines.push(content)
        }

        lineEnd = buffer.indexOf('\n')
      }
    }
  } finally {
    reader.releaseLock()
  }

  return {
    terminalEvent,
    lastEventId: latestEventId
  }
}
