import test from 'node:test'
import assert from 'node:assert/strict'

import { formatLoadingProgress } from '../src/utils/interviewLoadingProgress.js'

test('formatLoadingProgress should round floating progress for UI display', () => {
  assert.equal(formatLoadingProgress(43.199999999999996), 43)
  assert.equal(formatLoadingProgress(10.800000000000001), 11)
})

test('formatLoadingProgress should clamp invalid progress into a safe percentage range', () => {
  assert.equal(formatLoadingProgress(-1), 0)
  assert.equal(formatLoadingProgress(101.2), 100)
  assert.equal(formatLoadingProgress('not-a-number'), 0)
})
