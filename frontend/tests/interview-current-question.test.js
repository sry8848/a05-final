import test from 'node:test'
import assert from 'node:assert/strict'

import {
  normalizeAuthoritativeQuestion,
  resolveAsrQuestionType,
  resolveNextStreamQuestion
} from '../src/utils/interviewCurrentQuestion.js'

test('normalizeAuthoritativeQuestion should accept a valid backend question snapshot', () => {
  const normalized = normalizeAuthoritativeQuestion({
    questionId: 12,
    questionNo: 3,
    questionType: 'PROJECT_DEEP_DIVE',
    domainName: '缓存与中间件',
    stem: '结合项目讲讲缓存击穿的处理。',
    targetSkill: '缓存击穿',
    hintAvailable: true
  })

  assert.equal(normalized.questionId, 12)
  assert.equal(normalized.questionNo, 3)
  assert.equal(normalized.questionType, 'PROJECT_DEEP_DIVE')
  assert.equal(normalized.domainName, '缓存与中间件')
  assert.equal(normalized.targetSkill, '缓存击穿')
  assert.equal(normalized.question, '结合项目讲讲缓存击穿的处理。')
})

test('normalizeAuthoritativeQuestion should throw when required metadata is missing', () => {
  assert.throws(
    () => normalizeAuthoritativeQuestion({
      questionId: 12,
      questionNo: 3,
      stem: '请讲讲缓存击穿'
    }),
    /questionType/
  )
})

test('resolveNextStreamQuestion should prefer done question snapshot', () => {
  const question = resolveNextStreamQuestion({
    donePayload: {
      questionId: 20,
      question: {
        questionId: 20,
        questionNo: 4,
        questionType: 'BEHAVIORAL',
        domainName: '协作沟通',
        stem: '分享一次跨团队推进的经历。',
        targetSkill: '跨团队协作'
      }
    },
    sessionDetail: {
      currentQuestion: {
        questionId: 21,
        questionNo: 5,
        questionType: 'PRINCIPLE',
        stem: '不应使用这道题'
      }
    }
  })

  assert.equal(question.questionId, 20)
  assert.equal(question.questionType, 'BEHAVIORAL')
})

test('resolveNextStreamQuestion should fallback to session detail currentQuestion', () => {
  const question = resolveNextStreamQuestion({
    donePayload: {
      questionId: 33
    },
    sessionDetail: {
      currentQuestion: {
        questionId: 33,
        questionNo: 6,
        questionType: 'PROJECT_DEEP_DIVE',
        stem: '结合秒杀项目讲讲限流方案。',
        targetSkill: '限流与降级'
      }
    }
  })

  assert.equal(question.questionId, 33)
  assert.equal(question.questionType, 'PROJECT_DEEP_DIVE')
})

test('resolveNextStreamQuestion should throw when no authoritative question snapshot exists', () => {
  assert.throws(
    () => resolveNextStreamQuestion({
      donePayload: { questionId: 44 }
    }),
    /权威快照缺失/
  )
})

test('resolveAsrQuestionType should use real questionType and reject missing metadata', () => {
  assert.equal(resolveAsrQuestionType({ questionType: 'BEHAVIORAL' }), 'BEHAVIORAL')
  assert.throws(
    () => resolveAsrQuestionType({ questionType: '' }),
    /questionType/
  )
})
