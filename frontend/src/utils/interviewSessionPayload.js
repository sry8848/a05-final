function normalizePayloadResumeId(value) {
  if (value == null || String(value).trim() === '') return null
  const numeric = Number(value)
  return Number.isFinite(numeric) && numeric > 0 ? numeric : null
}

export function buildInterviewCreatePayload({
  config,
  roleMap,
  experienceMap
}) {
  const payload = {
    positionCode: roleMap[config.jobType] || 'FRONTEND',
    experienceLevel: experienceMap[config.experience] || 'JUNIOR',
    mode: config.interviewMode,
    jobDescription: config.jobDescription || null,
    resumeId: normalizePayloadResumeId(config.resumeId),
    focusTopics: config.knowledgePoints?.length ? config.knowledgePoints.join(',') : null,
    rememberSettings: true,
    thinkTimeLimitSeconds: config.interviewMode === 'professional' ? 30 : null,
    answerTimeLimitSeconds: config.interviewMode === 'professional' ? 180 : null
  }
  return payload
}
