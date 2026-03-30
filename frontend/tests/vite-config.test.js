import test from 'node:test'
import assert from 'node:assert/strict'

test('vite dev proxy should forward websocket upgrades for /api', async () => {
  const configModule = await import(`../vite.config.js?ts=${Date.now()}`)
  const config = typeof configModule.default === 'function'
    ? await configModule.default({})
    : configModule.default

  assert.equal(
    config?.server?.proxy?.['/api']?.ws,
    true,
    'Vite proxy must forward WebSocket upgrades for /api in remote debugging scenarios'
  )
})
