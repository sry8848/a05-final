import test from 'node:test'
import assert from 'node:assert/strict'

async function loadAdminDashboardApiModule() {
  try {
    return await import('../src/api/adminDashboard.js')
  } catch {
    return {}
  }
}

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

test('getAdminDashboardSystemPing should request public ping endpoint and return serverTime', async (t) => {
  const { getAdminDashboardSystemPing } = await loadAdminDashboardApiModule()

  assert.equal(
    typeof getAdminDashboardSystemPing,
    'function',
    'getAdminDashboardSystemPing must be implemented'
  )

  const originalFetch = global.fetch
  t.after(() => {
    global.fetch = originalFetch
  })

  global.fetch = async (url, options = {}) => {
    assert.equal(url, '/api/v1/system/ping')
    assert.equal(options.method, 'GET')
    assert.equal(options.cache, 'no-store')
    assert.equal(options.headers?.Authorization, undefined)

    return createJsonResponse({
      payload: {
        code: 0,
        data: {
          serverTime: '2026-03-22T02:30:00Z'
        }
      }
    })
  }

  const result = await getAdminDashboardSystemPing()
  assert.deepEqual(result, { serverTime: '2026-03-22T02:30:00Z' })
})

test('getAdminDashboardSystemPing should throw status-aware error for non-2xx responses', async (t) => {
  const { getAdminDashboardSystemPing } = await loadAdminDashboardApiModule()
  const originalFetch = global.fetch

  t.after(() => {
    global.fetch = originalFetch
  })

  global.fetch = async () => createJsonResponse({
    ok: false,
    status: 503,
    statusText: 'Service Unavailable',
    payload: {
      message: '系统接口暂时不可用'
    }
  })

  await assert.rejects(
    () => getAdminDashboardSystemPing(),
    (error) => {
      assert.equal(error.status, 503)
      assert.equal(error.message, '系统接口暂时不可用')
      return true
    }
  )
})

test('getAdminDashboardSystemPing should reject unexpected payloads', async (t) => {
  const { getAdminDashboardSystemPing } = await loadAdminDashboardApiModule()
  const originalFetch = global.fetch

  t.after(() => {
    global.fetch = originalFetch
  })

  global.fetch = async () => createJsonResponse({
    payload: {
      code: 0,
      data: {}
    }
  })

  await assert.rejects(
    () => getAdminDashboardSystemPing(),
    (error) => {
      assert.equal(error.status, 500)
      assert.equal(error.message, '系统接口返回了无效数据')
      return true
    }
  )
})
