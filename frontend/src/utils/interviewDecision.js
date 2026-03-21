const VALID_INTERVIEW_DECISIONS = new Set(['continue', 'wrapup'])

export const DECISION_TEXT_MAP = {
  wrapup: '本轮问答已完成，正在生成你的面试报告。'
}

export function normalizeInterviewDecision(response) {
  const decision = String(response?.decision || '').trim().toLowerCase()
  if (!decision) {
    throw new Error('Interview decision payload is invalid: missing decision')
  }

  if (!VALID_INTERVIEW_DECISIONS.has(decision)) {
    throw new Error(
      `Interview decision payload is invalid: unsupported decision "${response.decision}"; expected continue or wrapup`
    )
  }

  return decision
}
