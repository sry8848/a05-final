import test from 'node:test'
import assert from 'node:assert/strict'

import {
  createQuestionConsultMessage,
  getQuestionConsultMessages,
  streamQuestionConsultMessage
} from '../src/api/resume.js'

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

function createSseResponse(chunks) {
  const encoder = new TextEncoder()
  return {
    ok: true,
    status: 200,
    body: new ReadableStream({
      start(controller) {
        chunks.forEach((chunk) => controller.enqueue(encoder.encode(chunk)))
        controller.close()
      }
    })
  }
}

test('getQuestionConsultMessages should request authoritative backend history', async (t) => {
  const originalFetch = global.fetch
  const originalLocalStorage = global.localStorage

  global.localStorage = {
    getItem(key) {
      if (key === 'aiInterviewToken') return 'token'
      return null
    }
  }
  t.after(() => {
    global.fetch = originalFetch
    global.localStorage = originalLocalStorage
  })

  global.fetch = async (url, options = {}) => {
    assert.equal(url, '/api/v1/interviews/1/questions/2/ai-consult/messages')
    assert.equal(options.method, 'GET')
    assert.equal(options.headers?.Authorization, 'Bearer token')
    return createJsonResponse({
      payload: {
        code: 0,
        data: [{ id: 1, role: 'assistant', status: 'ready', content: '建议先补边界条件。' }]
      }
    })
  }

  const result = await getQuestionConsultMessages(1, 2)
  assert.equal(result.length, 1)
  assert.equal(result[0].role, 'assistant')
})

test('createQuestionConsultMessage should post consult content and return ids', async (t) => {
  const originalFetch = global.fetch
  const originalLocalStorage = global.localStorage

  global.localStorage = {
    getItem(key) {
      if (key === 'aiInterviewToken') return 'token'
      return null
    }
  }
  t.after(() => {
    global.fetch = originalFetch
    global.localStorage = originalLocalStorage
  })

  global.fetch = async (url, options = {}) => {
    assert.equal(url, '/api/v1/interviews/1/questions/2/ai-consult/messages')
    assert.equal(options.method, 'POST')
    assert.equal(JSON.parse(options.body).content, '为什么这题失分？')
    return createJsonResponse({
      payload: {
        code: 0,
        data: { userMessageId: 201, assistantMessageId: 202 }
      }
    })
  }

  const result = await createQuestionConsultMessage(1, 2, { content: '为什么这题失分？' })
  assert.deepEqual(result, { userMessageId: 201, assistantMessageId: 202 })
})

test('streamQuestionConsultMessage should parse start delta and done events in order', async (t) => {
  const originalFetch = global.fetch
  const originalLocalStorage = global.localStorage
  const seen = []

  global.localStorage = {
    getItem(key) {
      if (key === 'aiInterviewToken') return 'token'
      return null
    }
  }
  t.after(() => {
    global.fetch = originalFetch
    global.localStorage = originalLocalStorage
  })

  global.fetch = async (url, options = {}) => {
    assert.equal(url, '/api/v1/interviews/1/questions/2/ai-consult/messages/202/stream')
    assert.equal(options.method, 'GET')
    return createSseResponse([
      'event: start\ndata: {"assistantMessageId":202}\n\n',
      'event: delta\ndata: {"text":"主要是"}\n\n',
      'event: done\ndata: {"assistantMessageId":202,"content":"主要是边界条件没展开。","status":"ready"}\n\n'
    ])
  }

  const result = await streamQuestionConsultMessage(1, 2, 202, {
    onStart(payload) {
      seen.push(['start', payload.assistantMessageId])
    },
    onDelta(payload) {
      seen.push(['delta', payload.text])
    },
    onDone(payload) {
      seen.push(['done', payload.status])
    }
  })

  assert.deepEqual(seen, [
    ['start', 202],
    ['delta', '主要是'],
    ['done', 'ready']
  ])
  assert.equal(result.terminalEvent, 'done')
})
