export const LEGACY_SIGNAL_TO_DECISION_MAP = {
  DEEPEN: 'followup',
  RETRY_SAME_DOMAIN: 'rescue',
  NEXT_DOMAIN: 'broaden',
  END: 'wrapup'
}

export const DECISION_SCORE_MAP = {
  broaden: 78,
  rescue: 72,
  followup: 84,
  probe: 80,
  wrapup: 80
}

export const DECISION_TEXT_MAP = {
  broaden: '',
  rescue: '',
  followup: '',
  probe: '',
  wrapup: '本轮问答已完成，正在生成你的面试报告。'
}

export function normalizeInterviewDecision(response) {
  const decision = String(response?.decision || '').trim().toLowerCase()
  if (decision) {
    return decision
  }

  const legacySignal = String(response?.evaluationSignal || '').trim().toUpperCase()
  return LEGACY_SIGNAL_TO_DECISION_MAP[legacySignal] || 'broaden'
}

export function mapDecisionScore(decision, isSkip = false) {
  if (isSkip) return 0
  return DECISION_SCORE_MAP[decision] || 70
}
