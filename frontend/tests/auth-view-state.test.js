import test from 'node:test'
import assert from 'node:assert/strict'

async function loadAuthViewStateModule() {
  try {
    return await import('../src/utils/authViewState.js')
  } catch {
    return {}
  }
}

test('user login should clear stale admin view state', async () => {
  const { resolveUserLoginViewState } = await loadAuthViewStateModule()

  assert.equal(
    typeof resolveUserLoginViewState,
    'function',
    'resolveUserLoginViewState must be implemented'
  )

  assert.deepEqual(
    resolveUserLoginViewState({
      isLoggedIn: false,
      isAdmin: true,
      showAdminLogin: true,
      showRegister: true
    }),
    {
      isLoggedIn: true,
      isAdmin: false,
      showAdminLogin: false,
      showRegister: false
    }
  )
})

test('register success should also return to user-side state', async () => {
  const { resolveUserRegisterViewState } = await loadAuthViewStateModule()

  assert.equal(
    typeof resolveUserRegisterViewState,
    'function',
    'resolveUserRegisterViewState must be implemented'
  )

  assert.deepEqual(
    resolveUserRegisterViewState({
      isLoggedIn: false,
      isAdmin: true,
      showAdminLogin: true,
      showRegister: true
    }),
    {
      isLoggedIn: true,
      isAdmin: false,
      showAdminLogin: false,
      showRegister: false
    }
  )
})

test('logout should clear admin role residue and return to user login entry', async () => {
  const { resolveLogoutViewState } = await loadAuthViewStateModule()

  assert.equal(
    typeof resolveLogoutViewState,
    'function',
    'resolveLogoutViewState must be implemented'
  )

  assert.deepEqual(
    resolveLogoutViewState({
      isLoggedIn: true,
      isAdmin: true,
      showAdminLogin: true,
      currentPage: 'analysis'
    }),
    {
      isLoggedIn: false,
      isAdmin: false,
      showAdminLogin: false,
      currentPage: 'growth'
    }
  )
})
