function normalizePositiveInteger(value, fieldName) {
  const numeric = Number(value)
  if (!Number.isInteger(numeric) || numeric <= 0) {
    throw new Error(`后端题目缺少有效 ${fieldName}`)
  }
  return numeric
}

function normalizeRequiredText(value, fieldName) {
  const normalized = String(value || '').trim()
  if (!normalized) {
    throw new Error(`后端题目缺少有效 ${fieldName}`)
  }
  return normalized
}

function normalizeOptionalText(value) {
  if (value == null) return null
  const normalized = String(value).trim()
  return normalized || null
}

export function normalizeAuthoritativeQuestion(raw, { fallbackStem = '' } = {}) {
  if (!raw || typeof raw !== 'object') {
    throw new Error('后端题目权威快照缺失')
  }

  const questionId = normalizePositiveInteger(raw.questionId ?? raw.id, 'questionId')
  const questionNo = normalizePositiveInteger(raw.questionNo, 'questionNo')
  const questionType = normalizeRequiredText(raw.questionType, 'questionType')
  const stem = normalizeRequiredText(raw.stem ?? raw.question ?? fallbackStem, 'stem')

  return {
    id: questionId,
    questionId,
    questionNo,
    questionType,
    domainName: normalizeOptionalText(raw.domainName),
    question: stem,
    stem,
    targetSkill: normalizeOptionalText(raw.targetSkill),
    hintAvailable: raw.hintAvailable !== false,
    keywords: Array.isArray(raw.keywords) ? raw.keywords : []
  }
}

export function resolveNextStreamQuestion({
  donePayload = null,
  sessionDetail = null,
  fallbackStem = ''
} = {}) {
  const candidates = []

  if (donePayload?.question) {
    candidates.push({ label: 'done.question', value: donePayload.question })
  }
  if (sessionDetail?.currentQuestion) {
    candidates.push({ label: 'sessionDetail.currentQuestion', value: sessionDetail.currentQuestion })
  }

  let lastError = null
  for (const candidate of candidates) {
    try {
      return normalizeAuthoritativeQuestion(candidate.value, { fallbackStem })
    } catch (error) {
      lastError = new Error(`${candidate.label}: ${error.message}`)
    }
  }

  if (lastError) {
    throw lastError
  }
  throw new Error('下一题权威快照缺失')
}

export function resolveAsrQuestionType(question) {
  return normalizeRequiredText(question?.questionType, 'questionType')
}
