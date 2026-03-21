import test from 'node:test'
import assert from 'node:assert/strict'

import {
  buildPendingInterviewResult,
  mergeInterviewResultWithReport
} from '../src/utils/interviewResultState.js'

test('buildPendingInterviewResult should keep scoring fields empty before report is ready', () => {
  const result = buildPendingInterviewResult({
    answers: [
      { questionId: 1, question: 'Q1', answer: 'A1' },
      { questionId: 2, question: 'Q2', answer: '[skip]' }
    ],
    duration: '03:20'
  })

  assert.equal(result.score, null)
  assert.equal(result.correctCount, null)
  assert.equal(result.beatPercent, null)
  assert.equal(result.feedback, null)
  assert.equal(result.totalQuestions, 2)
  assert.equal(result.duration, '03:20')
})

test('mergeInterviewResultWithReport should use backend report as the only scoring source', () => {
  const merged = mergeInterviewResultWithReport(
    {
      answers: [
        { questionId: 101, question: 'Q1', answer: 'A1' },
        { questionId: 102, question: 'Q2', answer: 'A2' }
      ],
      score: null,
      correctCount: null,
      totalQuestions: 2,
      beatPercent: null,
      feedback: null
    },
    {
      sessionId: 99,
      overallScore: 81.6,
      summary: '正式报告总结',
      questions: [
        { questionId: 101, questionNo: 1, questionStem: 'Q1', status: 'answered', score: 90, commentary: '很好' },
        { questionId: 102, questionNo: 2, questionStem: 'Q2', status: 'answered', score: 55, commentary: '一般' }
      ]
    }
  )

  assert.equal(merged.score, 82)
  assert.equal(merged.correctCount, 1)
  assert.equal(merged.totalQuestions, 2)
  assert.equal(merged.feedback, '正式报告总结')
  assert.equal(merged.reportStatus, 'ready')
  assert.equal(merged.beatPercent, null)
})

test('mergeInterviewResultWithReport should not fabricate beatPercent without backend data', () => {
  const merged = mergeInterviewResultWithReport(
    {
      answers: [],
      beatPercent: null
    },
    {
      overallScore: 76,
      summary: '报告已生成',
      questions: []
    }
  )

  assert.equal(merged.beatPercent, null)
})
