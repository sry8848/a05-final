import test from 'node:test'
import assert from 'node:assert/strict'

import {
  getInterviewQuestionStatusText,
  getInterviewQuestionCommentaryFallback,
  mapInterviewReportSkillDomains
} from '../src/utils/interviewResultDisplay.js'

test('getInterviewQuestionStatusText should show pending questions as 未提交', () => {
  assert.equal(getInterviewQuestionStatusText('pending'), '未提交')
  assert.equal(getInterviewQuestionStatusText('answered'), '已完成')
  assert.equal(getInterviewQuestionStatusText('skipped'), '已跳过')
})

test('getInterviewQuestionCommentaryFallback should explain pending as not submitted', () => {
  assert.equal(
    getInterviewQuestionCommentaryFallback({ status: 'pending', score: null, commentary: '' }),
    '本题尚未提交，手动结束后不会自动补交。'
  )
})

test('mapInterviewReportSkillDomains should keep actual report score instead of delta', () => {
  const domains = mapInterviewReportSkillDomains([
    { domainName: 'Java 核心基础', score: 78.4, commentary: '集合和并发基础需要补强。' }
  ])

  assert.equal(domains.length, 1)
  assert.equal(domains[0].name, 'Java 核心基础')
  assert.equal(domains[0].score, 78)
  assert.equal(domains[0].scoreText, '78 分')
  assert.equal(domains[0].weakPoints, '集合和并发基础需要补强。')
})
