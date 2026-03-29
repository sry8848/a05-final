import test from 'node:test'
import assert from 'node:assert/strict'

import { buildQuestionDetailRenderSlices } from '../src/utils/questionDetailAnnotationRender.js'

test('buildQuestionDetailRenderSlices should keep full answer text and mark highlighted ranges only', () => {
  const slices = buildQuestionDetailRenderSlices({
    answerText: '我们遵循的核心原则是尽量消除共享，必须共享时确保原子性。',
    highlightedAnnotations: [
      { start: 10, end: 16, quote: '尽量消除共享', label: 'strength', comment: '抓住原则' }
    ]
  })

  assert.deepEqual(
    slices.map((item) => ({ type: item.type, text: item.text })),
    [
      { type: 'plain', text: '我们遵循的核心原则是' },
      { type: 'strength', text: '尽量消除共享' },
      { type: 'plain', text: '，必须共享时确保原子性。' }
    ]
  )
})

test('buildQuestionDetailRenderSlices should fallback to legacy highlighted segments when annotations are absent', () => {
  const slices = buildQuestionDetailRenderSlices({
    answerText: '主流程清晰，边界条件不足。',
    highlightedSegments: [
      { segment: '主流程清晰', label: 'strength', comment: '主线明确' },
      { segment: '边界条件不足', label: 'weakness', comment: '还需补充' }
    ]
  })

  assert.equal(slices.every((item) => item.type !== 'plain'), true)
  assert.equal(slices[0].type, 'strength')
  assert.equal(slices[1].type, 'weakness')
})
