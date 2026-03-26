import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const interviewPageSource = readFileSync(
  new URL('../src/components/InterviewPage.vue', import.meta.url),
  'utf8'
)

function getCssBlock(selector) {
  const normalizedSelector = selector.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  const match = interviewPageSource.match(new RegExp(`${normalizedSelector}\\s*\\{([\\s\\S]*?)\\}`))
  assert.ok(match, `expected to find CSS block for ${selector}`)
  return match[1]
}

test('user chat row should stay right-aligned when using row-reverse', () => {
  const userMessageBlock = getCssBlock('.message.user')

  assert.match(userMessageBlock, /flex-direction:\s*row-reverse/)
  assert.match(userMessageBlock, /justify-content:\s*flex-start/)
})

test('user chat bubble should keep max width without forcing a fixed width', () => {
  const userBubbleBlock = getCssBlock('.message.user .message-content')

  assert.match(userBubbleBlock, /max-width:\s*70%/)
  assert.doesNotMatch(userBubbleBlock, /(^|\n)\s*width\s*:/)
})
