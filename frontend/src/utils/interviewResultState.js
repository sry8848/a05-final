function hasOwn(obj, key) {
  return Object.prototype.hasOwnProperty.call(obj || {}, key)
}

function normalizeQuestionId(value) {
  if (value == null) return null
  const normalized = String(value).trim()
  return normalized || null
}

function normalizeQuestionNo(value) {
  const numeric = Number(value)
  return Number.isInteger(numeric) && numeric > 0 ? numeric : null
}

function normalizeQuestionStatus(value) {
  const normalized = String(value || '').trim().toLowerCase()
  if (['answered', 'skipped', 'pending'].includes(normalized)) {
    return normalized
  }
  return 'pending'
}

function normalizeNullableScore(value) {
  if (value == null || value === '') return null
  const numeric = Number(value)
  return Number.isFinite(numeric) ? Math.round(numeric) : null
}

function deriveLocalAnswerStatus(answer) {
  if (!answer || typeof answer !== 'object') return 'pending'
  const answerText = String(answer.answer ?? answer.userAnswer ?? '').trim()
  if (!answerText) return 'pending'
  if (answerText === '[skip]') return 'skipped'
  return 'answered'
}

export function buildResultAnswersFromReport(reportQuestions = [], localAnswers = []) {
  if (!Array.isArray(reportQuestions) || !reportQuestions.length) {
    return Array.isArray(localAnswers) ? localAnswers.slice() : []
  }

  const localByQuestionId = new Map()
  const localByQuestionNo = new Map()
  ;(Array.isArray(localAnswers) ? localAnswers : []).forEach((item) => {
    const questionId = normalizeQuestionId(item?.questionId)
    const questionNo = normalizeQuestionNo(item?.questionNo)
    if (questionId) localByQuestionId.set(questionId, item)
    if (questionNo != null) localByQuestionNo.set(String(questionNo), item)
  })

  const normalized = []
  reportQuestions.forEach((raw) => {
    const reportQuestionId = normalizeQuestionId(raw?.questionId)
    const reportQuestionNo = normalizeQuestionNo(raw?.questionNo)
    if (!reportQuestionId && reportQuestionNo == null) return

    let matchedLocal = null
    if (reportQuestionId && localByQuestionId.has(reportQuestionId)) {
      matchedLocal = localByQuestionId.get(reportQuestionId)
    } else if (reportQuestionNo != null) {
      matchedLocal = localByQuestionNo.get(String(reportQuestionNo)) || null
    }

    const status = hasOwn(raw, 'status')
      ? normalizeQuestionStatus(raw.status)
      : deriveLocalAnswerStatus(matchedLocal)
    const score = hasOwn(raw, 'score') ? normalizeNullableScore(raw.score) : null
    const questionStem = String(raw?.questionStem ?? '').trim()

    const localNonCore = {}
    if (matchedLocal && typeof matchedLocal === 'object') {
      Object.keys(matchedLocal).forEach((key) => {
        if (['questionId', 'questionNo', 'questionStem', 'question', 'status', 'score'].includes(key)) return
        localNonCore[key] = matchedLocal[key]
      })
    }

    normalized.push({
      ...localNonCore,
      questionId: reportQuestionId,
      questionNo: reportQuestionNo,
      questionStem,
      question: questionStem,
      status,
      score,
      commentary: hasOwn(raw, 'commentary') ? raw.commentary : null
    })
  })

  return normalized
}

export function buildPendingInterviewResult({ answers = [], duration = '' } = {}) {
  const totalQuestions = Array.isArray(answers) ? answers.length : 0
  return {
    score: null,
    correctCount: null,
    totalQuestions,
    beatPercent: null,
    feedback: null,
    duration
  }
}

export function mergeInterviewResultWithReport(baseResult = {}, report = {}) {
  const merged = {
    ...(baseResult || {})
  }

  const scoreNum = Number(report?.overallScore)
  const score = Number.isFinite(scoreNum) ? Math.round(scoreNum) : null
  const reportQuestions = Array.isArray(report?.questions) ? report.questions : []
  const mergedAnswers = buildResultAnswersFromReport(reportQuestions, merged.answers)
  const useReportQuestionSource = reportQuestions.length > 0 && mergedAnswers.length > 0

  merged.score = score
  merged.correctCount = useReportQuestionSource
    ? mergedAnswers.filter((item) => Number(item?.score) >= 60).length
    : null
  merged.totalQuestions = useReportQuestionSource
    ? mergedAnswers.length
    : (Number(merged.totalQuestions) || (Array.isArray(merged.answers) ? merged.answers.length : 0))
  merged.feedback = String(report?.summary || '').trim() || null
  merged.reportStatus = 'ready'
  merged.report = report
  merged.answers = useReportQuestionSource ? mergedAnswers : (Array.isArray(merged.answers) ? merged.answers : [])
  merged.sessionId = normalizeQuestionId(merged.sessionId) || normalizeQuestionId(report?.sessionId)
  merged.beatPercent = Number.isFinite(Number(report?.beatPercent))
    ? Math.round(Number(report.beatPercent))
    : null

  return merged
}
