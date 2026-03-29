function normalizeResumeId(value) {
  if (value == null || value === '') return null
  return String(value)
}

function normalizeParseStatus(value) {
  return String(value || '').trim().toLowerCase()
}

function toCreatedAtMs(value) {
  const parsed = Date.parse(value || '')
  return Number.isNaN(parsed) ? 0 : parsed
}

export function detectResumeFileKind(fileName) {
  const lower = String(fileName || '').trim().toLowerCase()
  if (lower.endsWith('.pdf')) return 'pdf'
  if (lower.endsWith('.docx')) return 'word'
  if (lower.endsWith('.md')) return 'markdown'
  return 'file'
}

export function isResumeParsed(resume) {
  return Boolean(resume && resume.id != null && normalizeParseStatus(resume.parseStatus) === 'parsed')
}

export function resolvePreferredResumeId({ resumes, rememberedResumeId }) {
  const list = Array.isArray(resumes) ? resumes : []
  const rememberedId = normalizeResumeId(rememberedResumeId)
  if (rememberedId) {
    const rememberedResume = list.find((resume) => (
      normalizeResumeId(resume?.id) === rememberedId && isResumeParsed(resume)
    ))
    if (rememberedResume) {
      return rememberedResume.id
    }
  }

  const latestParsedResume = list
    .filter(isResumeParsed)
    .sort((left, right) => toCreatedAtMs(right?.createdAt) - toCreatedAtMs(left?.createdAt))[0]

  return latestParsedResume ? latestParsedResume.id : null
}

export function buildUploadedResumeItem({ resumeId, fileName, parseStatus = 'parsing', createdAt }) {
  return {
    id: resumeId,
    name: fileName || '',
    sourceType: 'file',
    parseStatus,
    isDefault: false,
    createdAt: createdAt || new Date().toISOString()
  }
}

export function patchResumeListItem(list, resumeId, patch) {
  const targetResumeId = normalizeResumeId(resumeId)
  if (!Array.isArray(list)) return []
  if (!targetResumeId) return [...list]

  return list.map((item) => {
    if (normalizeResumeId(item?.id) !== targetResumeId) {
      return item
    }
    return {
      ...item,
      ...(patch || {})
    }
  })
}
