import assert from 'node:assert/strict'
import { AsrService } from '../src/services/AsrService.js'

function createStorage(seed = {}) {
  const store = new Map(Object.entries(seed))
  return {
    getItem(key) {
      return store.has(key) ? store.get(key) : null
    },
    setItem(key, value) {
      store.set(key, String(value))
    },
    removeItem(key) {
      store.delete(key)
    },
    clear() {
      store.clear()
    },
  }
}

globalThis.localStorage = createStorage({ aiInterviewToken: 'jwt-from-aiInterviewToken' })

const service = new AsrService()

assert.equal(
  service._getJwtFromStorage(),
  'jwt-from-aiInterviewToken',
  'AsrService should read the same token key used by LoginPage'
)

console.log('asr-service-storage test passed')
