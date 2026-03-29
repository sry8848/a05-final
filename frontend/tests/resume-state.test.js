import test from 'node:test'
import assert from 'node:assert/strict'

import {
  buildUploadedResumeItem,
  detectResumeFileKind,
  patchResumeListItem,
  resolvePreferredResumeId
} from '../src/utils/resumeState.js'

test('resolvePreferredResumeId should keep remembered parsed resume when it is still valid', () => {
  const resumeId = resolvePreferredResumeId({
    rememberedResumeId: '12',
    resumes: [
      { id: 12, parseStatus: 'parsed', createdAt: '2026-03-25T10:00:00Z' },
      { id: 9, parseStatus: 'parsed', createdAt: '2026-03-26T10:00:00Z' }
    ]
  })

  assert.equal(resumeId, 12)
})

test('resolvePreferredResumeId should fallback to latest parsed resume when remembered one is missing or unusable', () => {
  const resumeId = resolvePreferredResumeId({
    rememberedResumeId: '12',
    resumes: [
      { id: 18, parseStatus: 'parsing', createdAt: '2026-03-26T12:00:00Z' },
      { id: 16, parseStatus: 'parsed', createdAt: '2026-03-25T08:00:00Z' },
      { id: 20, parseStatus: 'parsed', createdAt: '2026-03-26T09:00:00Z' }
    ]
  })

  assert.equal(resumeId, 20)
})

test('resolvePreferredResumeId should return null when no parsed resumes exist', () => {
  const resumeId = resolvePreferredResumeId({
    rememberedResumeId: '12',
    resumes: [
      { id: 12, parseStatus: 'failed', createdAt: '2026-03-24T10:00:00Z' },
      { id: 18, parseStatus: 'parsing', createdAt: '2026-03-26T12:00:00Z' }
    ]
  })

  assert.equal(resumeId, null)
})

test('buildUploadedResumeItem should create a new parsing item for the list head', () => {
  const item = buildUploadedResumeItem({
    resumeId: 101,
    fileName: '张三.pdf',
    createdAt: '2026-03-26T12:00:00Z'
  })

  assert.deepEqual(item, {
    id: 101,
    name: '张三.pdf',
    sourceType: 'file',
    parseStatus: 'parsing',
    isDefault: false,
    createdAt: '2026-03-26T12:00:00Z'
  })
})

test('patchResumeListItem should update only the matching resume item', () => {
  const next = patchResumeListItem([
    { id: 8, name: '旧简历', parseStatus: 'parsed' },
    { id: 9, name: '新简历', parseStatus: 'parsing' }
  ], 9, {
    parseStatus: 'parsed',
    name: '新简历_v2'
  })

  assert.deepEqual(next, [
    { id: 8, name: '旧简历', parseStatus: 'parsed' },
    { id: 9, name: '新简历_v2', parseStatus: 'parsed' }
  ])
})

test('detectResumeFileKind should identify markdown files separately from word documents', () => {
  assert.equal(detectResumeFileKind('candidate.MD'), 'markdown')
  assert.equal(detectResumeFileKind('candidate.docx'), 'word')
  assert.equal(detectResumeFileKind('candidate.pdf'), 'pdf')
})
