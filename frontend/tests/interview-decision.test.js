import test from 'node:test'
import assert from 'node:assert/strict'

import {
  DECISION_SCORE_MAP,
  DECISION_TEXT_MAP,
  mapDecisionScore,
  normalizeInterviewDecision
} from '../src/utils/interviewDecision.js'

test('normalizeInterviewDecision should prefer new decision field', () => {
  assert.equal(
    normalizeInterviewDecision({
      decision: 'followup',
      evaluationSignal: 'END'
    }),
    'followup'
  )
})

test('normalizeInterviewDecision should map legacy evaluationSignal to new decision', () => {
  assert.equal(normalizeInterviewDecision({ evaluationSignal: 'DEEPEN' }), 'followup')
  assert.equal(normalizeInterviewDecision({ evaluationSignal: 'RETRY_SAME_DOMAIN' }), 'rescue')
  assert.equal(normalizeInterviewDecision({ evaluationSignal: 'NEXT_DOMAIN' }), 'broaden')
  assert.equal(normalizeInterviewDecision({ evaluationSignal: 'END' }), 'wrapup')
})

test('normalizeInterviewDecision should fallback to broaden for unknown payload', () => {
  assert.equal(normalizeInterviewDecision(null), 'broaden')
  assert.equal(normalizeInterviewDecision({}), 'broaden')
  assert.equal(normalizeInterviewDecision({ evaluationSignal: 'WEIRD' }), 'broaden')
})

test('decision score and text maps should use new decision semantics', () => {
  assert.equal(mapDecisionScore('followup'), DECISION_SCORE_MAP.followup)
  assert.equal(mapDecisionScore('wrapup'), DECISION_SCORE_MAP.wrapup)
  assert.equal(mapDecisionScore('rescue', true), 0)
  assert.equal(DECISION_TEXT_MAP.wrapup, '本轮问答已完成，正在生成你的面试报告。')
  assert.equal(DECISION_TEXT_MAP.followup, '')
})
