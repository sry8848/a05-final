const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

export function getEmailValidationError(value) {
  const normalized = String(value ?? '').trim()
  if (!normalized) {
    return '请输入邮箱'
  }
  if (!EMAIL_PATTERN.test(normalized)) {
    return '请输入正确的邮箱地址'
  }
  return ''
}
