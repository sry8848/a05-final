export function buildInterviewCreatePayload({
  config,
  roleMap,
  experienceMap
}) {
  const payload = {
    targetRole: roleMap[config.jobType] || 'FRONTEND',
    experienceLevel: experienceMap[config.experience] || 'JUNIOR',
    mode: config.interviewMode,
    jobDescription: config.jobDescription || null,
    resumeId: config.resumeId && config.resumeId !== 'default' ? Number(config.resumeId) : null,
    focusTopics: config.knowledgePoints?.length ? config.knowledgePoints.join(',') : null,
    rememberSettings: true,
    thinkTimeLimitSeconds: config.interviewMode === 'professional' ? 30 : null,
    answerTimeLimitSeconds: config.interviewMode === 'professional' ? 180 : null
  }
  return payload
}
