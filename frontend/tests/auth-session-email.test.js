import test from 'node:test'
import assert from 'node:assert/strict'

async function loadAuthSessionModule() {
  try {
    return await import('../src/utils/authSession.js')
  } catch {
    return {}
  }
}

function createStorage(initial = {}) {
  const state = new Map(Object.entries(initial))
  return {
    getItem(key) {
      return state.has(key) ? state.get(key) : null
    },
    setItem(key, value) {
      state.set(key, String(value))
    },
    removeItem(key) {
      state.delete(key)
    }
  }
}

test('persistUserSession should store user email in settings when provided', async () => {
  const { persistUserSession } = await loadAuthSessionModule()

  const storage = createStorage({
    aiInterviewSettings: JSON.stringify({ isDarkMode: true })
  })

  persistUserSession(storage, {
    token: 'user-token',
    nickname: '普通用户',
    email: 'user@example.com'
  })

  const settings = JSON.parse(storage.getItem('aiInterviewSettings'))
  assert.deepEqual(settings.user, {
    name: '普通用户',
    email: 'user@example.com'
  })
})

test('restoreAuthSession should expose user email returned by backend probe', async () => {
  const { restoreAuthSession } = await loadAuthSessionModule()

  const storage = createStorage({
    aiInterviewToken: 'user-token'
  })

  const result = await restoreAuthSession({
    storage,
    getCurrentAdmin: async () => {
      throw new Error('should not query admin endpoint')
    },
    getCurrentUser: async (token) => {
      assert.equal(token, 'user-token')
      return { nickname: '普通用户', email: 'user@example.com' }
    }
  })

  assert.deepEqual(result, {
    isLoggedIn: true,
    isAdmin: false,
    userName: '普通用户',
    userEmail: 'user@example.com'
  })
})
