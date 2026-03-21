export const REDO_DISABLED_REASON = '该旧记录缺少后端题目上下文，暂不支持单题重答'

function normalizeId(value) {
  if (value == null) return null
  const normalized = String(value).trim()
  return normalized || null
}

function normalizeStatus(value) {
  const normalized = String(value || '').trim().toLowerCase()
  if (['pending', 'generating', 'ready', 'failed'].includes(normalized)) {
    return normalized
  }
  return 'pending'
}

function normalizeNullableScore(value) {
  if (value == null || value === '') return null
  const numeric = Number(value)
  return Number.isFinite(numeric) ? numeric : null
}

function normalizeStringArray(value) {
  if (!Array.isArray(value)) return []
  return value
    .map((item) => String(item || '').trim())
    .filter(Boolean)
}

function normalizeEvaluatedDomains(value) {
  if (!Array.isArray(value)) return []
  return value
    .map((item) => {
      if (!item || typeof item !== 'object') return null
      const domainName = String(item.domainName || '').trim()
      const domainCode = String(item.domainCode || '').trim()
      if (!domainName && !domainCode) return null
      return {
        domainCode,
        domainName,
        score: normalizeNullableScore(item.score),
        commentary: String(item.commentary || '').trim()
      }
    })
    .filter(Boolean)
}

function normalizeHighlightedSegments(value) {
  if (!Array.isArray(value)) return []
  return value
    .map((item) => {
      if (!item || typeof item !== 'object') return null
      const segment = String(item.segment || '').trim()
      if (!segment) return null
      const label = String(item.label || '').trim().toLowerCase() === 'weakness'
        ? 'weakness'
        : 'strength'
      return {
        segment,
        label,
        comment: String(item.comment || '').trim()
      }
    })
    .filter(Boolean)
}

export function withQuestionRedoState(detail, { requested = false } = {}) {
  const normalized = { ...(detail || {}) }
  normalized.sessionId = normalizeId(normalized.sessionId)
  normalized.questionId = normalizeId(normalized.questionId)
  normalized.canRedo = Boolean(normalized.sessionId && normalized.questionId)
  normalized.redoRequested = Boolean(requested && normalized.canRedo)
  normalized.redoDisabledReason = normalized.canRedo ? '' : REDO_DISABLED_REASON
  return normalized
}

export function normalizeQuestionRedoAttempt(value) {
  if (!value || typeof value !== 'object') return null

  const redoAttemptId = value.redoAttemptId == null ? null : Number(value.redoAttemptId)
  return {
    redoAttemptId: Number.isFinite(redoAttemptId) ? redoAttemptId : null,
    evaluationStatus: normalizeStatus(value.evaluationStatus),
    answerText: String(value.answerText || '').trim(),
    score: normalizeNullableScore(value.score),
    commentary: String(value.commentary || '').trim(),
    strengthPoints: normalizeStringArray(value.strengthPoints),
    weakPoints: normalizeStringArray(value.weakPoints),
    idealAnswerOutline: normalizeStringArray(value.idealAnswerOutline),
    rewrittenAnswer: String(value.rewrittenAnswer || '').trim(),
    evaluatedDomains: normalizeEvaluatedDomains(value.evaluatedDomains),
    highlightedSegments: normalizeHighlightedSegments(value.highlightedSegments),
    createdAt: value.createdAt || null
  }
}

export function shouldPollQuestionRedoAttempt(value) {
  const normalized = normalizeQuestionRedoAttempt(value)
  if (!normalized) return false
  return normalized.evaluationStatus === 'pending' || normalized.evaluationStatus === 'generating'
}
