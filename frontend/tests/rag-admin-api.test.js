import test from 'node:test'
import assert from 'node:assert/strict'

function createJsonResponse({ ok = true, status = 200, statusText = 'OK', payload = {} } = {}) {
  return {
    ok,
    status,
    statusText,
    async json() {
      return payload
    }
  }
}

test('importKnowledgeJsonl should send multipart request with admin token', async (t) => {
  const { importKnowledgeJsonl } = await import('../src/api/ragAdmin.js')

  const originalFetch = global.fetch
  const originalLocalStorage = global.localStorage
  t.after(() => {
    global.fetch = originalFetch
    global.localStorage = originalLocalStorage
  })

  global.localStorage = {
    getItem(key) {
      return key === 'aiInterviewAdminToken' ? 'admin-token' : null
    }
  }

  global.fetch = async (url, options = {}) => {
    assert.equal(url, '/api/v1/admin/knowledge/import-jsonl')
    assert.equal(options.method, 'POST')
    assert.equal(options.headers.Authorization, 'Bearer admin-token')
    assert.ok(options.body instanceof FormData)
    return createJsonResponse({
      payload: {
        code: 0,
        data: {
          fileName: 'cards.jsonl',
          totalLines: 2,
          validLines: 2,
          ingestedCount: 2
        }
      }
    })
  }

  const file = new File(['{"id":"q1"}'], 'cards.jsonl', { type: 'application/json' })
  const result = await importKnowledgeJsonl(file)

  assert.deepEqual(result, {
    fileName: 'cards.jsonl',
    totalLines: 2,
    validLines: 2,
    ingestedCount: 2
  })
})

test('importKnowledgeJsonl should reject when admin session is missing', async () => {
  const { importKnowledgeJsonl } = await import('../src/api/ragAdmin.js')
  const originalLocalStorage = global.localStorage

  global.localStorage = {
    getItem() {
      return null
    }
  }

  try {
    await assert.rejects(
      () => importKnowledgeJsonl(new File(['{}'], 'cards.jsonl', { type: 'application/json' })),
      (error) => {
        assert.equal(error.status, 401)
        assert.equal(error.message, '管理员登录已失效')
        return true
      }
    )
  } finally {
    global.localStorage = originalLocalStorage
  }
})

test('importKnowledgeJsonl should expose structured validation error payload', async (t) => {
  const { importKnowledgeJsonl } = await import('../src/api/ragAdmin.js')

  const originalFetch = global.fetch
  const originalLocalStorage = global.localStorage
  t.after(() => {
    global.fetch = originalFetch
    global.localStorage = originalLocalStorage
  })

  global.localStorage = {
    getItem(key) {
      return key === 'aiInterviewAdminToken' ? 'admin-token' : null
    }
  }

  global.fetch = async () => createJsonResponse({
    payload: {
      code: 400,
      message: 'JSONL校验失败',
      data: {
        fileName: 'cards.jsonl',
        totalLines: 2,
        validLines: 1,
        errors: [
          {
            scope: 'line',
            lineNo: 2,
            field: 'difficulty',
            reason: '字段值非法',
            expected: 'L1-L5'
          }
        ]
      }
    }
  })

  await assert.rejects(
    () => importKnowledgeJsonl(new File(['{}'], 'cards.jsonl', { type: 'application/json' })),
    (error) => {
      assert.equal(error.status, 400)
      assert.equal(error.message, 'JSONL校验失败')
      assert.deepEqual(error.result, {
        fileName: 'cards.jsonl',
        totalLines: 2,
        validLines: 1,
        errors: [
          {
            scope: 'line',
            lineNo: 2,
            field: 'difficulty',
            reason: '字段值非法',
            expected: 'L1-L5'
          }
        ]
      })
      return true
    }
  )
})
