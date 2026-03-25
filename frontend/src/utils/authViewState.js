function mergeViewState(current, next) {
  return {
    ...(current || {}),
    ...next
  }
}

export function resolveUserLoginViewState(current) {
  return mergeViewState(current, {
    isLoggedIn: true,
    isAdmin: false,
    showAdminLogin: false,
    showRegister: false
  })
}

export function resolveUserRegisterViewState(current) {
  return resolveUserLoginViewState(current)
}

export function resolveLogoutViewState(current) {
  return mergeViewState(current, {
    isLoggedIn: false,
    isAdmin: false,
    showAdminLogin: false,
    currentPage: 'growth'
  })
}
