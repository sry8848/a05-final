import test from 'node:test'
import assert from 'node:assert/strict'

import {
  buildFailureSummary,
  createUploadQueueItem,
  markFailure,
  markSuccess,
  markUploading
} from '../src/components/ragManagementImportState.js'

test('createUploadQueueItem should create pending queue item from file', () => {
  const file = { name: 'cards.jsonl', size: 2048 }
  const item = createUploadQueueItem(file)

  assert.equal(item.name, 'cards.jsonl')
  assert.equal(item.size, 2048)
  assert.equal(item.status, 'pending')
  assert.equal(item.progress, 0)
  assert.equal(item.summary, '等待上传')
})

test('markUploading should switch queue item to uploading state', () => {
  const updated = markUploading({
    id: '1',
    name: 'cards.jsonl',
    size: 10,
    progress: 0,
    status: 'pending',
    summary: '等待上传',
    result: null,
    expanded: false
  })

  assert.equal(updated.status, 'uploading')
  assert.equal(updated.progress, 35)
  assert.equal(updated.summary, '正在上传并校验...')
})

test('markSuccess should expose ingestion summary for queue item', () => {
  const updated = markSuccess({
    id: '1',
    name: 'cards.jsonl',
    size: 10,
    progress: 35,
    status: 'uploading',
    summary: '正在上传并校验...',
    result: null,
    expanded: false
  }, {
    fileName: 'cards.jsonl',
    totalLines: 5,
    validLines: 5,
    ingestedCount: 5
  })

  assert.equal(updated.status, 'success')
  assert.equal(updated.progress, 100)
  assert.equal(updated.summary, '导入成功，共 5 条')
  assert.equal(updated.result.ingestedCount, 5)
})

test('buildFailureSummary should prefer first line error over generic message', () => {
  const summary = buildFailureSummary(
    new Error('JSONL校验失败'),
    {
      errors: [
        { scope: 'line', lineNo: 18, reason: '字段值非法' }
      ]
    }
  )

  assert.equal(summary, '导入失败，第 18 行有错误')
})

test('markFailure should preserve structured errors for detail panel', () => {
  const updated = markFailure({
    id: '1',
    name: 'cards.jsonl',
    size: 10,
    progress: 35,
    status: 'uploading',
    summary: '正在上传并校验...',
    result: null,
    expanded: false
  }, {
    status: 400,
    message: 'JSONL校验失败',
    result: {
      fileName: 'cards.jsonl',
      totalLines: 20,
      validLines: 17,
      errors: [
        {
          scope: 'line',
          lineNo: 18,
          field: 'difficulty',
          reason: '字段值非法',
          expected: 'L1-L5'
        }
      ]
    }
  })

  assert.equal(updated.status, 'failed')
  assert.equal(updated.progress, 100)
  assert.equal(updated.summary, '导入失败，第 18 行有错误')
  assert.equal(updated.result.totalLines, 20)
  assert.equal(updated.result.errors[0].field, 'difficulty')
})
