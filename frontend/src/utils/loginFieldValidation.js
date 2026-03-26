function getRequiredFieldError(value, message) {
  return String(value ?? '').trim() ? '' : message
}

export function getPasswordValidationError(value) {
  return getRequiredFieldError(value, '请输入密码')
}

export function getCodeValidationError(value) {
  return getRequiredFieldError(value, '请输入验证码')
}
