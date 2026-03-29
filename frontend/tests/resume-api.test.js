import test from 'node:test'
import assert from 'node:assert/strict'

import { toResumeUploadMessage } from '../src/api/resume.js'

test('toResumeUploadMessage should mention md files in oversize guidance', () => {
  assert.equal(
    toResumeUploadMessage(413, 'Payload Too Large'),
    '上传文件过大，请选择不超过 20MB 的 PDF、DOCX 或 MD 文件'
  )
})
