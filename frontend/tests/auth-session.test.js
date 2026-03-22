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

test('persistUserSession should store user token and clear admin token residue', async () => {
  const { persistUserSession } = await loadAuthSessionModule()

  assert.equal(typeof persistUserSession, 'function', 'persistUserSession must be implemented')

  const storage = createStorage({
    aiInterviewAdminToken: 'stale-admin-token',
    aiInterviewSettings: JSON.stringify({ isDarkMode: true })
  })

  persistUserSession(storage, {
    token: 'user-token',
    nickname: '普通用户'
  })

  assert.equal(storage.getItem('aiInterviewToken'), 'user-token')
  assert.equal(storage.getItem('token'), 'user-token')
  assert.equal(storage.getItem('aiInterviewAdminToken'), null)

  const settings = JSON.parse(storage.getItem('aiInterviewSettings'))
  assert.equal(settings.isLoggedIn, true)
  assert.deepEqual(settings.user, { name: '普通用户' })
})

test('persistAdminSession should store admin token and clear user token residue', async () => {
  const { persistAdminSession } = await loadAuthSessionModule()

  assert.equal(typeof persistAdminSession, 'function', 'persistAdminSession must be implemented')

  const storage = createStorage({
    aiInterviewToken: 'stale-user-token',
    aiInterviewSettings: JSON.stringify({ isDarkMode: false })
  })

  persistAdminSession(storage, {
    token: 'admin-token',
    username: 'super-admin',
    displayName: '超级管理员'
  })

  assert.equal(storage.getItem('aiInterviewAdminToken'), 'admin-token')
  assert.equal(storage.getItem('aiInterviewToken'), null)

  const settings = JSON.parse(storage.getItem('aiInterviewSettings'))
  assert.equal(settings.isLoggedIn, true)
  assert.deepEqual(settings.user, { name: '超级管理员' })
})

test('clearPersistedAuthSession should remove both tokens and reset login marker', async () => {
  const { clearPersistedAuthSession } = await loadAuthSessionModule()

  assert.equal(typeof clearPersistedAuthSession, 'function', 'clearPersistedAuthSession must be implemented')

  const storage = createStorage({
    aiInterviewToken: 'user-token',
    token: 'legacy-user-token',
    aiInterviewAdminToken: 'admin-token',
    aiInterviewSettings: JSON.stringify({ isLoggedIn: true, user: { name: '旧用户' } })
  })

  clearPersistedAuthSession(storage)

  assert.equal(storage.getItem('aiInterviewToken'), null)
  assert.equal(storage.getItem('token'), null)
  assert.equal(storage.getItem('aiInterviewAdminToken'), null)
  assert.equal(JSON.parse(storage.getItem('aiInterviewSettings')).isLoggedIn, false)
})

test('restoreAuthSession should prefer admin token and resolve admin identity from backend probe', async () => {
  const { restoreAuthSession } = await loadAuthSessionModule()

  assert.equal(typeof restoreAuthSession, 'function', 'restoreAuthSession must be implemented')

  const storage = createStorage({
    aiInterviewAdminToken: 'admin-token',
    aiInterviewToken: 'user-token'
  })

  const result = await restoreAuthSession({
    storage,
    getCurrentAdmin: async (token) => {
      assert.equal(token, 'admin-token')
      return { username: 'super-admin', displayName: '超级管理员' }
    },
    getCurrentUser: async () => {
      throw new Error('should not fallback while admin token is valid')
    }
  })

  assert.deepEqual(result, {
    isLoggedIn: true,
    isAdmin: true,
    userName: '超级管理员'
  })
})

test('restoreAuthSession should clear invalid admin token and fallback to valid user token', async () => {
  const { restoreAuthSession } = await loadAuthSessionModule()

  const storage = createStorage({
    aiInterviewAdminToken: 'expired-admin-token',
    aiInterviewToken: 'user-token'
  })

  const result = await restoreAuthSession({
    storage,
    getCurrentAdmin: async () => {
      throw new Error('401')
    },
    getCurrentUser: async (token) => {
      assert.equal(token, 'user-token')
      return { nickname: '普通用户' }
    }
  })

  assert.equal(storage.getItem('aiInterviewAdminToken'), null)
  assert.deepEqual(result, {
    isLoggedIn: true,
    isAdmin: false,
    userName: '普通用户'
  })
})
