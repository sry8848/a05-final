import test from 'node:test'
import assert from 'node:assert/strict'

import {
  getGrowthRequestPositionCodes,
  mapBackendHistoryStatus,
  normalizeStoredInterviewRecord,
  shouldUseBackendHistory,
  upsertReadyInterviewRecord
} from '../src/utils/growthHistoryState.js'

test('statistics request should stay unfiltered while skill overview follows selected position', () => {
  const positionCodeMap = {
    frontend: 'FRONTEND',
    backend: 'JAVA_BACKEND',
    fullstack: 'FRONTEND'
  }

  assert.deepEqual(
    getGrowthRequestPositionCodes('backend', positionCodeMap),
    {
      statisticsPositionCode: null,
      skillOverviewPositionCode: 'JAVA_BACKEND'
    }
  )
})

test('history should preserve new backend display statuses', () => {
  assert.equal(mapBackendHistoryStatus('ready'), 'ready')
  assert.equal(mapBackendHistoryStatus('generating'), 'generating')
  assert.equal(mapBackendHistoryStatus('failed'), 'failed')
  assert.equal(mapBackendHistoryStatus('completed'), 'ready')
  assert.equal(mapBackendHistoryStatus('report_generating'), 'generating')
  assert.equal(mapBackendHistoryStatus('aborted'), 'failed')
})

test('history should trust successful backend responses even when empty', () => {
  assert.equal(shouldUseBackendHistory({ items: [] }), true)
  assert.equal(shouldUseBackendHistory({ items: [{ sessionId: 1 }] }), true)
  assert.equal(shouldUseBackendHistory(null), false)
})

test('ready report should upsert local record to ready state', () => {
  const now = '2026-03-17T10:00:00.000Z'
  const report = {
    sessionId: 12,
    reportStatus: 'ready',
    overallScore: 87
  }

  const updated = upsertReadyInterviewRecord(
    [
      {
        id: 12,
        sessionId: '12',
        reportStatus: 'generating',
        score: 0
      }
    ],
    {
      id: 12,
      sessionId: '12',
      job: '后端开发工程师',
      reportStatus: 'generating'
    },
    report,
    now
  )

  assert.equal(updated.length, 1)
  assert.equal(updated[0].reportStatus, 'ready')
  assert.equal(updated[0].score, 87)
  assert.equal(updated[0].report, report)
  assert.equal(updated[0].reportReadyAt, now)
  assert.equal(updated[0].syncStatus, 'synced')
})

test('local ready placeholder without report should downgrade to generating', () => {
  const normalized = normalizeStoredInterviewRecord({
    sessionId: '54',
    reportStatus: 'ready',
    reportReadyAt: '2026-03-17T10:00:00.000Z'
  })

  assert.equal(normalized.reportStatus, 'generating')
  assert.equal(normalized.syncStatus, 'report_generating')
})

test('backend ready item without embedded report should remain ready', () => {
  const normalized = normalizeStoredInterviewRecord({
    sessionId: '54',
    reportStatus: 'ready'
  }, {
    allowReadyWithoutReportDowngrade: false
  })

  assert.equal(normalized.reportStatus, 'ready')
})
