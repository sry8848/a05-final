import test from 'node:test'
import assert from 'node:assert/strict'

async function loadLoginFieldValidationModule() {
  try {
    return await import('../src/utils/loginFieldValidation.js')
  } catch {
    return {}
  }
}

test('getPasswordValidationError should require password input', async () => {
  const { getPasswordValidationError } = await loadLoginFieldValidationModule()

  assert.equal(typeof getPasswordValidationError, 'function')
  assert.equal(getPasswordValidationError(''), '请输入密码')
  assert.equal(getPasswordValidationError('   '), '请输入密码')
})

test('getCodeValidationError should require code input', async () => {
  const { getCodeValidationError } = await loadLoginFieldValidationModule()

  assert.equal(typeof getCodeValidationError, 'function')
  assert.equal(getCodeValidationError(''), '请输入验证码')
  assert.equal(getCodeValidationError('   '), '请输入验证码')
})

test('login field validation should accept non-empty trimmed input', async () => {
  const { getPasswordValidationError, getCodeValidationError } = await loadLoginFieldValidationModule()

  assert.equal(getPasswordValidationError(' secret '), '')
  assert.equal(getCodeValidationError(' 123456 '), '')
})
