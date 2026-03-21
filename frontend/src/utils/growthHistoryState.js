export function normalizeDisplayReportStatus(status) {
  const normalized = String(status || '').trim().toLowerCase()
  if (normalized === 'ready' || normalized === 'generating' || normalized === 'failed') {
    return normalized
  }
  return 'ready'
}

export function normalizeStoredInterviewRecord(
  record,
  options = {}
) {
  if (!record || typeof record !== 'object') return record

  const {
    allowReadyWithoutReportDowngrade = true,
    nowIso = new Date().toISOString()
  } = options

  const sessionId = record?.sessionId == null ? null : String(record.sessionId).trim()
  const reportStatus = normalizeDisplayReportStatus(record?.reportStatus)
  const hasReportPayload = record?.report && typeof record.report === 'object'

  if (allowReadyWithoutReportDowngrade && sessionId && reportStatus === 'ready' && !hasReportPayload) {
    return {
      ...record,
      sessionId,
      reportStatus: 'generating',
      reportReadyAt: null,
      reportFailedAt: null,
      reportStartedAt: record?.reportStartedAt || record?.reportReadyAt || nowIso,
      syncStatus: 'report_generating'
    }
  }

  return {
    ...record,
    sessionId,
    reportStatus
  }
}

export function mapBackendHistoryStatus(status) {
  const normalized = String(status || '').trim().toLowerCase()
  if (normalized === 'ready' || normalized === 'generating' || normalized === 'failed') {
    return normalized
  }
  if (normalized === 'completed') return 'ready'
  if (normalized === 'aborted') return 'failed'
  if (normalized === 'report_generating' || normalized === 'planning' || normalized === 'in_progress') {
    return 'generating'
  }
  return 'ready'
}

export function shouldUseBackendHistory(page) {
  return Array.isArray(page?.items)
}

export function getGrowthRequestPositionCodes(selectedPosition, positionCodeMap = {}) {
  return {
    statisticsPositionCode: null,
    skillOverviewPositionCode: positionCodeMap[selectedPosition] || null
  }
}

export function applyReadyReportToInterviewRecord(record, report, nowIso = new Date().toISOString()) {
  const updated = { ...(record || {}) }
  const scoreNum = Number(report?.overallScore)
  updated.score = Number.isFinite(scoreNum) ? Math.round(scoreNum) : null
  updated.feedback = typeof report?.summary === 'string' && report.summary.trim() ? report.summary.trim() : null
  updated.beatPercent = Number.isFinite(Number(report?.beatPercent))
    ? Math.round(Number(report.beatPercent))
    : null
  if (Array.isArray(report?.questions) && report.questions.length > 0) {
    updated.questions = report.questions.length
    updated.correct = report.questions.filter((item) => Number(item?.score) >= 60).length
  } else {
    updated.correct = null
  }
  updated.reportStatus = 'ready'
  updated.reportStartedAt = updated.reportStartedAt || nowIso
  updated.reportReadyAt = nowIso
  updated.reportFailedAt = null
  updated.report = report || null
  updated.syncStatus = 'synced'
  return updated
}

export function upsertReadyInterviewRecord(records, fallbackRecord, report, nowIso = new Date().toISOString()) {
  const sessionId = String(report?.sessionId || fallbackRecord?.sessionId || fallbackRecord?.id || '').trim()
  if (!sessionId) {
    return Array.isArray(records) ? records.slice() : []
  }

  const list = Array.isArray(records) ? records.slice() : []
  const index = list.findIndex((item) => String(item?.sessionId || item?.id || '').trim() === sessionId)
  const baseRecord = index >= 0 ? list[index] : {
    ...(fallbackRecord || {}),
    id: fallbackRecord?.id || report?.sessionId || sessionId,
    sessionId
  }

  const updatedRecord = applyReadyReportToInterviewRecord(baseRecord, report, nowIso)
  if (index >= 0) {
    list[index] = updatedRecord
  } else {
    list.unshift(updatedRecord)
  }
  return list
}
