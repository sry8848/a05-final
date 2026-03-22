export function resolveUserLoginViewState() {
  return {
    isLoggedIn: true,
    isAdmin: false,
    showAdminLogin: false,
    showRegister: false
  }
}

export function resolveUserRegisterViewState() {
  return resolveUserLoginViewState()
}

export function resolveLogoutViewState() {
  return {
    isLoggedIn: false,
    isAdmin: false,
    showAdminLogin: false,
    currentPage: 'growth'
  }
}
