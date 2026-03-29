import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const questionDetailSource = readFileSync(
  new URL('../src/components/QuestionDetailPage.vue', import.meta.url),
  'utf8'
)

test('question detail page should no longer use localStorage-backed consult mocks', () => {
  assert.doesNotMatch(questionDetailSource, /CONSULT_STORAGE_KEY/)
  assert.doesNotMatch(questionDetailSource, /buildAssistantReply/)
  assert.doesNotMatch(questionDetailSource, /localStorage\.getItem\(['"]questionConsultHistory['"]\)/)
})

test('question detail page should consume real consult APIs instead of local mock tip', () => {
  assert.match(questionDetailSource, /getQuestionConsultMessages/)
  assert.match(questionDetailSource, /createQuestionConsultMessage/)
  assert.match(questionDetailSource, /streamQuestionConsultMessage/)
  assert.doesNotMatch(questionDetailSource, /本区为本地模拟追问/)
})
