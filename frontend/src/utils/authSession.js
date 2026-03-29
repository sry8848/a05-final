export const USER_TOKEN_KEY = 'aiInterviewToken'
export const ADMIN_TOKEN_KEY = 'aiInterviewAdminToken'
export const LEGACY_USER_TOKEN_KEY = 'token'
export const SETTINGS_KEY = 'aiInterviewSettings'

function mergeStoredUser(settings, { nickname, email }) {
  const currentUser = settings.user && typeof settings.user === 'object'
    ? { ...settings.user }
    : {}
  if (nickname) {
    currentUser.name = nickname
  }
  if (email != null) {
    currentUser.email = email
  }
  return Object.keys(currentUser).length ? currentUser : settings.user
}

function readSettings(storage) {
  try {
    return JSON.parse(storage.getItem(SETTINGS_KEY) || '{}')
  } catch {
    return {}
  }
}

function writeSettings(storage, updater) {
  const next = updater(readSettings(storage))
  storage.setItem(SETTINGS_KEY, JSON.stringify(next))
  return next
}

export function persistUserSession(storage, { token, nickname, email }) {
  storage.setItem(USER_TOKEN_KEY, token)
  storage.setItem(LEGACY_USER_TOKEN_KEY, token)
  storage.removeItem(ADMIN_TOKEN_KEY)
  writeSettings(storage, (settings) => ({
    ...settings,
    isLoggedIn: true,
    user: mergeStoredUser(settings, { nickname, email })
  }))
}

export function persistAdminSession(storage, { token, username, displayName }) {
  storage.setItem(ADMIN_TOKEN_KEY, token)
  storage.removeItem(USER_TOKEN_KEY)
  storage.removeItem(LEGACY_USER_TOKEN_KEY)
  writeSettings(storage, (settings) => ({
    ...settings,
    isLoggedIn: true,
    user: { name: displayName || username || '管理员' }
  }))
}

export function clearPersistedAuthSession(storage) {
  storage.removeItem(USER_TOKEN_KEY)
  storage.removeItem(ADMIN_TOKEN_KEY)
  storage.removeItem(LEGACY_USER_TOKEN_KEY)
  writeSettings(storage, (settings) => ({
    ...settings,
    isLoggedIn: false
  }))
}

export async function restoreAuthSession({ storage, getCurrentAdmin, getCurrentUser }) {
  const adminToken = storage.getItem(ADMIN_TOKEN_KEY)
  if (adminToken) {
    try {
      const admin = await getCurrentAdmin(adminToken)
      persistAdminSession(storage, {
        token: adminToken,
        username: admin.username,
        displayName: admin.displayName
      })
      return {
        isLoggedIn: true,
        isAdmin: true,
        userName: admin.displayName || admin.username || '管理员'
      }
    } catch {
      storage.removeItem(ADMIN_TOKEN_KEY)
    }
  }

  const userToken = storage.getItem(USER_TOKEN_KEY)
  if (userToken) {
    try {
      const user = await getCurrentUser(userToken)
      const userEmail = user.email || ''
      persistUserSession(storage, {
        token: userToken,
        nickname: user.nickname || user.name,
        email: userEmail
      })
      const result = {
        isLoggedIn: true,
        isAdmin: false,
        userName: user.nickname || user.name || '面试者'
      }
      if (userEmail) {
        result.userEmail = userEmail
      }
      return result
    } catch {
      storage.removeItem(USER_TOKEN_KEY)
    }
  }

  clearPersistedAuthSession(storage)
  return {
    isLoggedIn: false,
    isAdmin: false,
    userName: ''
  }
}
