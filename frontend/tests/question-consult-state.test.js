import test from 'node:test'
import assert from 'node:assert/strict'

import {
  appendQuestionConsultDelta,
  buildOptimisticQuestionConsultMessages,
  normalizeQuestionConsultMessage,
  replaceQuestionConsultMessage
} from '../src/utils/questionConsultState.js'

test('normalizeQuestionConsultMessage should keep authoritative backend message fields', () => {
  const normalized = normalizeQuestionConsultMessage({
    id: 202,
    role: 'assistant',
    status: 'ready',
    content: '建议先补边界条件。',
    replyToMessageId: 201,
    createdAt: '2026-03-29T12:30:00'
  })

  assert.equal(normalized.id, 202)
  assert.equal(normalized.role, 'assistant')
  assert.equal(normalized.status, 'ready')
  assert.equal(normalized.content, '建议先补边界条件。')
  assert.equal(normalized.replyToMessageId, 201)
})

test('buildOptimisticQuestionConsultMessages should create user and generating assistant placeholders', () => {
  const created = buildOptimisticQuestionConsultMessages(
    '为什么这题失分？',
    { userMessageId: 201, assistantMessageId: 202 }
  )

  assert.equal(created.length, 2)
  assert.equal(created[0].id, 201)
  assert.equal(created[0].role, 'user')
  assert.equal(created[0].status, 'ready')
  assert.equal(created[1].id, 202)
  assert.equal(created[1].role, 'assistant')
  assert.equal(created[1].status, 'generating')
  assert.equal(created[1].replyToMessageId, 201)
})

test('appendQuestionConsultDelta should append streamed text to matching assistant message only', () => {
  const messages = buildOptimisticQuestionConsultMessages(
    '为什么这题失分？',
    { userMessageId: 201, assistantMessageId: 202 }
  )

  const updated = appendQuestionConsultDelta(messages, 202, '主要是')
  const appended = appendQuestionConsultDelta(updated, 202, '边界条件没展开。')

  assert.equal(appended[1].content, '主要是边界条件没展开。')
  assert.equal(appended[0].content, '为什么这题失分？')
})

test('replaceQuestionConsultMessage should mark streamed assistant as completed or failed', () => {
  const messages = buildOptimisticQuestionConsultMessages(
    '为什么这题失分？',
    { userMessageId: 201, assistantMessageId: 202 }
  )

  const ready = replaceQuestionConsultMessage(messages, 202, {
    content: '主要是边界条件没展开。',
    status: 'ready'
  })
  assert.equal(ready[1].status, 'ready')
  assert.equal(ready[1].content, '主要是边界条件没展开。')

  const failed = replaceQuestionConsultMessage(ready, 202, {
    status: 'failed'
  })
  assert.equal(failed[1].status, 'failed')
})
