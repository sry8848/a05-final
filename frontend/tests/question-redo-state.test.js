import test from 'node:test'
import assert from 'node:assert/strict'

import {
  REDO_DISABLED_REASON,
  normalizeQuestionRedoAttempt,
  shouldPollQuestionRedoAttempt,
  withQuestionRedoState
} from '../src/utils/questionRedoState.js'

test('withQuestionRedoState should enable redo when authoritative ids exist', () => {
  const detail = withQuestionRedoState({
    sessionId: 101,
    questionId: 202,
    questionStem: '请讲讲缓存击穿'
  }, { requested: true })

  assert.equal(detail.sessionId, '101')
  assert.equal(detail.questionId, '202')
  assert.equal(detail.canRedo, true)
  assert.equal(detail.redoRequested, true)
  assert.equal(detail.redoDisabledReason, '')
})

test('withQuestionRedoState should disable redo when authoritative ids are missing', () => {
  const detail = withQuestionRedoState({
    sessionId: null,
    questionId: '',
    questionStem: '本地旧题'
  }, { requested: true })

  assert.equal(detail.canRedo, false)
  assert.equal(detail.redoRequested, false)
  assert.equal(detail.redoDisabledReason, REDO_DISABLED_REASON)
})

test('normalizeQuestionRedoAttempt should keep backend result as authoritative latest redo state', () => {
  const attempt = normalizeQuestionRedoAttempt({
    redoAttemptId: 7001,
    evaluationStatus: 'ready',
    answerText: '我会按 parse、layout、paint 组织答案。',
    score: '91',
    commentary: '这次结构清楚很多。',
    strengthPoints: ['主流程完整'],
    weakPoints: ['性能边界还不够'],
    idealAnswerOutline: ['定义', '流程', '优化'],
    rewrittenAnswer: '参考重答',
    evaluatedDomains: [
      { domainCode: 'browser', domainName: '浏览器原理', score: '91', commentary: '主域表现稳定' }
    ],
    highlightedSegments: [
      { segment: '先说解析再说布局', label: 'strength', comment: '顺序合理' }
    ]
  })

  assert.equal(attempt.redoAttemptId, 7001)
  assert.equal(attempt.evaluationStatus, 'ready')
  assert.equal(attempt.score, 91)
  assert.equal(attempt.evaluatedDomains[0].domainName, '浏览器原理')
  assert.equal(attempt.highlightedSegments[0].segment, '先说解析再说布局')
})

test('shouldPollQuestionRedoAttempt should only poll pending or generating redo attempts', () => {
  assert.equal(shouldPollQuestionRedoAttempt({ evaluationStatus: 'pending' }), true)
  assert.equal(shouldPollQuestionRedoAttempt({ evaluationStatus: 'generating' }), true)
  assert.equal(shouldPollQuestionRedoAttempt({ evaluationStatus: 'ready' }), false)
  assert.equal(shouldPollQuestionRedoAttempt({ evaluationStatus: 'failed' }), false)
  assert.equal(shouldPollQuestionRedoAttempt(null), false)
})
