export const USER_TOKEN_KEY = 'aiInterviewToken'
export const ADMIN_TOKEN_KEY = 'aiInterviewAdminToken'
export const LEGACY_USER_TOKEN_KEY = 'token'
export const SETTINGS_KEY = 'aiInterviewSettings'

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

export function persistUserSession(storage, { token, nickname }) {
  storage.setItem(USER_TOKEN_KEY, token)
  storage.setItem(LEGACY_USER_TOKEN_KEY, token)
  storage.removeItem(ADMIN_TOKEN_KEY)
  writeSettings(storage, (settings) => ({
    ...settings,
    isLoggedIn: true,
    user: nickname ? { name: nickname } : settings.user
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
      persistUserSession(storage, {
        token: userToken,
        nickname: user.nickname || user.name
      })
      return {
        isLoggedIn: true,
        isAdmin: false,
        userName: user.nickname || user.name || '面试者'
      }
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
