/**
 * 绠€鍘嗙鐞嗙浉鍏?API锛屼笌 docs/api-design.md 搂5 绾﹀畾涓€鑷淬€? * code=0 鎴愬姛锛岄潪 0 鎶?Error(message)銆? */

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
  if (!res.ok) throw new Error(json.message || res.statusText || '璇锋眰澶辫触')
  if (json.code !== 0) throw new Error(json.message || '璇锋眰澶辫触')
  return json.data
}

/** 涓婁紶鏂囦欢锛氫笉璁剧疆 Content-Type锛岀敱娴忚鍣ㄨ缃?multipart/form-data */
async function uploadRequest(path, formData) {
  const url = BASE + path
  const res = await fetch(url, {
    method: 'POST',
    headers: { Authorization: getAuthHeader() },
    body: formData
  })
  const json = await res.json().catch(() => ({}))
  const rawMessage = json.message || res.statusText || '涓婁紶澶辫触'
  const message = toResumeUploadMessage(res.status, rawMessage)
  if (!res.ok) throw new Error(message)
  if (json.code !== 0) throw new Error(message)
  return json.data
}

/**
 * 灏嗕笂浼犵浉鍏抽敊璇浆涓轰腑鏂囨彁绀猴紝渚夸簬鍓嶇灞曠ず
 * @param {number} status - HTTP 鐘舵€佺爜
 * @param {string} rawMessage - 鏈嶅姟绔垨娴忚鍣ㄨ繑鍥炵殑鍘熷鏂囨
 */
function toResumeUploadMessage(status, rawMessage) {
  if (status === 413) return '涓婁紶鏂囦欢杩囧ぇ锛岃閫夋嫨涓嶈秴杩?20MB 鐨?PDF 鎴?DOCX 鏂囦欢'
  const lower = (rawMessage || '').toLowerCase()
  if (/maximum.*size|size.*exceeded|exceeded.*size|file.*too large|payload too large/.test(lower)) {
    return '涓婁紶鏂囦欢杩囧ぇ锛岃閫夋嫨涓嶈秴杩?20MB 鐨?PDF 鎴?DOCX 鏂囦欢'
  }
  return rawMessage || '涓婁紶澶辫触'
}

function getAuthHeader() {
  const token = localStorage.getItem('aiInterviewToken') || localStorage.getItem('token')
  return token ? `Bearer ${token}` : ''
}

/**
 * 鑾峰彇绠€鍘嗗垪琛? * @returns {Promise<Array<{ id: number, name: string, sourceType: string, parseStatus: string, isDefault: boolean, createdAt: string }>>
 */
export function getResumes() {
  return request('/resumes', { method: 'GET' })
}

/**
 * 涓婁紶绠€鍘嗗苟鍙戣捣瑙ｆ瀽
 * @param {File} file - PDF 鎴?DOCX
 * @returns {Promise<{ resumeId: number, parseStatus: string }>}
 */
export function uploadResume(file) {
  const formData = new FormData()
  formData.append('file', file)
  return uploadRequest('/resumes/upload', formData)
}

/**
 * 鏌ヨ绠€鍘嗚В鏋愮姸鎬? * @param {number} resumeId
 * @returns {Promise<{ resumeId: number, parseStatus: string, parsedTextPreview?: string }>}
 */
export function getParseStatus(resumeId) {
  return request(`/resumes/${resumeId}/parse-status`, { method: 'GET' })
}

/**
 * 鑾峰彇绠€鍘嗚鎯咃紙鍚?parsedText锛? * @param {number} resumeId
 */
export function getResume(resumeId) {
  return request(`/resumes/${resumeId}`, { method: 'GET' })
}

/**
 * 鏇存柊绠€鍘嗭紙鍚嶇О銆佽瘑鍒枃鏈€佹槸鍚﹂粯璁わ級
 * @param {number} resumeId
 * @param {{ name?: string, parsedText?: string, isDefault?: boolean }} payload
 */
export function updateResume(resumeId, payload) {
  return request(`/resumes/${resumeId}`, {
    method: 'PUT',
    body: payload
  })
}

/** 璁句负榛樿绠€鍘?*/
export function setDefaultResume(resumeId) {
  return request(`/resumes/${resumeId}/set-default`, { method: 'POST' })
}

/** 鍒犻櫎绠€鍘?*/
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
export async function streamInterviewQuestion(sessionId, attemptId, handlers = {}, signal) {
  const url = BASE + '/interviews/' + sessionId + '/questions/stream?attemptId=' + encodeURIComponent(attemptId)
  const resp = await fetch(url, {
    method: 'GET',
    headers: {
      Accept: 'text/event-stream',
      Authorization: getAuthHeader()
    },
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

  const dispatch = async () => {
    if (!dataLines.length) return
    const payload = parseSsePayload(dataLines.join('\n'))
    const map = {
      start: handlers.onStart,
      delta: handlers.onDelta,
      done: handlers.onDone,
      error: handlers.onError
    }
    const fn = map[eventName] || handlers.onMessage
    if (fn) {
      await fn(payload, eventName, eventId)
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
}









