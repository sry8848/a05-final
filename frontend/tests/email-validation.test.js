import test from 'node:test'
import assert from 'node:assert/strict'

async function loadEmailValidationModule() {
  try {
    return await import('../src/utils/emailValidation.js')
  } catch {
    return {}
  }
}

test('getEmailValidationError should require email input', async () => {
  const { getEmailValidationError } = await loadEmailValidationModule()

  assert.equal(typeof getEmailValidationError, 'function')
  assert.equal(getEmailValidationError(''), '请输入邮箱')
  assert.equal(getEmailValidationError('   '), '请输入邮箱')
})

test('getEmailValidationError should reject invalid email format after trimming', async () => {
  const { getEmailValidationError } = await loadEmailValidationModule()

  assert.equal(getEmailValidationError(' user@example '), '请输入正确的邮箱地址')
  assert.equal(getEmailValidationError('abc'), '请输入正确的邮箱地址')
})

test('getEmailValidationError should accept normal email input', async () => {
  const { getEmailValidationError } = await loadEmailValidationModule()

  assert.equal(getEmailValidationError(' user@example.com '), '')
})
