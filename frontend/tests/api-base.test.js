import test from 'node:test'
import assert from 'node:assert/strict'

async function loadApiBaseModule() {
  try {
    return await import(`../src/api/base.js?ts=${Date.now()}`)
  } catch {
    return {}
  }
}

test('resolveApiBaseUrl should default to relative /api/v1 when no env value is provided', async () => {
  const { resolveApiBaseUrl } = await loadApiBaseModule()

  assert.equal(
    typeof resolveApiBaseUrl,
    'function',
    'resolveApiBaseUrl must be implemented'
  )

  assert.equal(resolveApiBaseUrl(), '/api/v1')
  assert.equal(resolveApiBaseUrl('   '), '/api/v1')
})

test('resolveApiBaseUrl should accept either backend host or full /api/v1 path', async () => {
  const { resolveApiBaseUrl, buildApiUrl } = await loadApiBaseModule()

  assert.equal(resolveApiBaseUrl('http://localhost:8080'), 'http://localhost:8080/api/v1')
  assert.equal(
    resolveApiBaseUrl('https://demo.example.com/backend/api/v1/'),
    'https://demo.example.com/backend/api/v1'
  )
  assert.equal(
    buildApiUrl('/system/ping', 'http://localhost:8080'),
    'http://localhost:8080/api/v1/system/ping'
  )
})

test('resolveBackendUrl should expand backend relative resource paths but keep absolute URLs unchanged', async () => {
  const { resolveBackendUrl } = await loadApiBaseModule()

  assert.equal(
    resolveBackendUrl('/api/v1/profile/avatar/1/a.png', 'https://demo.example.com/backend/api/v1'),
    'https://demo.example.com/backend/api/v1/profile/avatar/1/a.png'
  )
  assert.equal(
    resolveBackendUrl('wss://demo.example.com/api/v1/asr/stream?ticket=1', 'https://demo.example.com/backend/api/v1'),
    'wss://demo.example.com/api/v1/asr/stream?ticket=1'
  )
})
