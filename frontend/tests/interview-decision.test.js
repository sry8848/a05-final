import test from 'node:test'
import assert from 'node:assert/strict'

import {
  DECISION_TEXT_MAP,
  normalizeInterviewDecision
} from '../src/utils/interviewDecision.js'

test('normalizeInterviewDecision should prefer new decision field', () => {
  assert.equal(
    normalizeInterviewDecision({
      decision: 'continue',
      evaluationSignal: 'END'
    }),
    'continue'
  )
})

test('normalizeInterviewDecision should accept only current decision contract values', () => {
  assert.equal(normalizeInterviewDecision({ decision: 'continue' }), 'continue')
  assert.equal(normalizeInterviewDecision({ decision: ' WRAPUP ' }), 'wrapup')
})

test('normalizeInterviewDecision should ignore evaluationSignal when decision is present', () => {
  assert.equal(
    normalizeInterviewDecision({
      decision: 'wrapup',
      evaluationSignal: 'DEEPEN'
    }),
    'wrapup'
  )
})

test('normalizeInterviewDecision should throw when response is null or decision is missing', () => {
  assert.throws(() => normalizeInterviewDecision(null), /decision/i)
  assert.throws(() => normalizeInterviewDecision({}), /decision/i)
  assert.throws(() => normalizeInterviewDecision({ evaluationSignal: 'END' }), /decision/i)
})

test('normalizeInterviewDecision should throw when decision is blank or unsupported', () => {
  assert.throws(() => normalizeInterviewDecision({ decision: '   ' }), /decision/i)
  assert.throws(() => normalizeInterviewDecision({ decision: 'followup' }), /continue|wrapup/i)
  assert.throws(
    () => normalizeInterviewDecision({ decision: 'broaden', evaluationSignal: 'END' }),
    /continue|wrapup/i
  )
})

test('decision text map should only expose flow copy', () => {
  assert.equal(DECISION_TEXT_MAP.wrapup, '本轮问答已完成，正在生成你的面试报告。')
})
