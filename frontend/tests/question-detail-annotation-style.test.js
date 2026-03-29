import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const questionDetailSource = readFileSync(
  new URL('../src/components/QuestionDetailPage.vue', import.meta.url),
  'utf8'
)

function getCssBlock(selector) {
  const normalizedSelector = selector.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  const match = questionDetailSource.match(new RegExp(`${normalizedSelector}\\s*\\{([\\s\\S]*?)\\}`))
  assert.ok(match, `expected to find CSS block for ${selector}`)
  return match[1]
}

test('annotated answer should use a light reading surface', () => {
  const answerBlock = getCssBlock('.annotated-answer')

  assert.doesNotMatch(answerBlock, /background:\s*rgba\(15,\s*23,\s*42,\s*0\.28\)/)
  assert.match(answerBlock, /background:\s*(#fff|#ffffff|rgba\(255,\s*255,\s*255,\s*0\.\d+\))/i)
  assert.match(answerBlock, /border:\s*1px solid/i)
  assert.match(answerBlock, /max-height\s*:/)
  assert.match(answerBlock, /overflow-y\s*:\s*auto/i)
})

test('highlighted answer segments should emphasize text without background blocks', () => {
  const strengthBlock = getCssBlock('.answer-segment.strength')
  const weaknessBlock = getCssBlock('.answer-segment.weakness')

  assert.doesNotMatch(strengthBlock, /background\s*:/)
  assert.doesNotMatch(weaknessBlock, /background\s*:/)
  assert.match(strengthBlock, /color\s*:/)
  assert.match(weaknessBlock, /color\s*:/)
})

test('annotation notes should render as helper cards with readable text colors', () => {
  const noteBlock = getCssBlock('.annotation-note')
  const strengthNoteBlock = getCssBlock('.annotation-note.strength')
  const weaknessNoteBlock = getCssBlock('.annotation-note.weakness')

  assert.match(noteBlock, /padding\s*:/)
  assert.match(noteBlock, /border-radius\s*:/)
  assert.match(strengthNoteBlock, /background\s*:/)
  assert.match(weaknessNoteBlock, /background\s*:/)
  assert.doesNotMatch(strengthNoteBlock, /color:\s*#34d399/i)
  assert.doesNotMatch(weaknessNoteBlock, /color:\s*#f87171/i)
})
