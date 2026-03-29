<template>
  <div class="login-container">
    <div class="background-decoration">
      <div class="blob blob-1"></div>
      <div class="blob blob-2"></div>
      <div class="blob blob-3"></div>
    </div>

    <div class="login-card glass-card">
      <div class="login-header">
        <div class="logo-icon">
          <i class="fas fa-robot"></i>
        </div>
        <h1 class="login-title">AI面试官</h1>
        <p class="login-subtitle">智能模拟面试系统</p>
      </div>

      <div class="login-tabs">
        <button 
          class="tab-btn" 
          :class="{ active: loginMode === 'password' }"
          @click="loginMode = 'password'"
        >
          密码登录
        </button>
        <button 
          class="tab-btn" 
          :class="{ active: loginMode === 'code' }"
          @click="loginMode = 'code'"
        >
          验证码登录
        </button>
      </div>

      <form class="login-form" @submit.prevent="handleLogin">
        <div v-if="loginMode === 'password'" class="form-group">
          <label>邮箱</label>
          <div class="input-wrapper">
            <i class="fas fa-envelope"></i>
            <input 
              v-model="passwordForm.email" 
              type="email" 
              class="glass-input" 
              placeholder="请输入邮箱"
              @blur="validatePasswordEmail"
            >
          </div>
          <span v-if="errors.passwordEmail" class="error-msg">{{ errors.passwordEmail }}</span>
        </div>

        <div v-if="loginMode === 'password'" class="form-group">
          <label>密码</label>
          <div class="input-wrapper">
            <i class="fas fa-lock"></i>
            <input 
              v-model="passwordForm.password" 
              :type="showPassword ? 'text' : 'password'" 
              class="glass-input" 
              placeholder="请输入密码"
              @blur="validatePassword"
            >
            <i 
              :class="showPassword ? 'fas fa-eye-slash' : 'fas fa-eye'" 
              class="toggle-password"
              @click="showPassword = !showPassword"
            ></i>
          </div>
          <span v-if="errors.password" class="error-msg">{{ errors.password }}</span>
        </div>

        <template v-if="loginMode === 'code'">
          <div class="form-group">
            <label>邮箱</label>
            <div class="input-wrapper">
              <i class="fas fa-envelope"></i>
              <input 
                v-model="codeForm.email" 
                type="email" 
                class="glass-input" 
                placeholder="请输入邮箱"
                @blur="validateCodeEmail"
              >
            </div>
            <span v-if="errors.codeEmail" class="error-msg">{{ errors.codeEmail }}</span>
          </div>

          <div class="form-group">
            <label>验证码</label>
            <div class="input-wrapper code-input">
              <i class="fas fa-shield-alt"></i>
              <input 
                v-model="codeForm.code" 
                type="text" 
                class="glass-input" 
                placeholder="请输入验证码"
                maxlength="6"
                @blur="validateCode"
              >
              <button 
                type="button" 
                class="send-code-btn"
                :disabled="countdown > 0 || sendingCode"
                @click="sendCode"
              >
                {{ sendingCode ? '发送中...' : countdown > 0 ? `${countdown}s` : '获取验证码' }}
              </button>
            </div>
            <span v-if="errors.code" class="error-msg">{{ errors.code }}</span>
          </div>
        </template>

        <div v-if="loginMode === 'password'" class="form-options">
          <label class="remember-me">
            <input type="checkbox" v-model="rememberMe">
            <span>记住我</span>
          </label>
          <a href="#" class="forgot-password">忘记密码？</a>
        </div>

        <button type="submit" class="btn btn-primary btn-large login-btn" :disabled="loading">
          <i class="fas fa-sign-in-alt"></i>
          {{ loading ? '登录中...' : '登录' }}
        </button>
      </form>

      <div class="divider">
        <span>其他登录方式</span>
      </div>

      <div class="social-login">
        <button class="social-btn github" @click="socialLogin('github')">
          <i class="fab fa-github"></i>
        </button>
        <button class="social-btn wechat" @click="socialLogin('wechat')">
          <i class="fab fa-weixin"></i>
        </button>
        <button class="social-btn google" @click="socialLogin('google')">
          <i class="fab fa-google"></i>
        </button>
      </div>

      <div class="register-link">
        还没有账号？<a href="#" @click.prevent="goToRegister">立即注册</a>
      </div>

      <div class="admin-switch">
        <button type="button" class="admin-btn" @click="goToAdminLogin">
          <i class="fas fa-user-shield"></i>
          管理端登录
        </button>
      </div>
    </div>
  </div>
</template>

<script>
import { ref, reactive } from 'vue'
import { sendEmailCode, loginByPassword, loginByEmailCode } from '@/api/auth'
import { getEmailValidationError } from '@/utils/emailValidation'
import { getPasswordValidationError, getCodeValidationError } from '@/utils/loginFieldValidation'

export default {
  name: 'LoginPage',
  emits: ['loginSuccess', 'goToRegister', 'goToAdminLogin'],
  setup(props, { emit }) {
    const loginMode = ref('password')
    const showPassword = ref(false)
    const rememberMe = ref(false)
    const countdown = ref(0)
    const loading = ref(false)
    const sendingCode = ref(false)

    const passwordForm = reactive({
      email: '',
      password: ''
    })

    const codeForm = reactive({
      email: '',
      code: ''
    })

    const errors = reactive({
      passwordEmail: '',
      password: '',
      codeEmail: '',
      code: ''
    })

    const validateEmailField = (value, key) => {
      errors[key] = getEmailValidationError(value)
      return !errors[key]
    }

    const validatePasswordEmail = () => validateEmailField(passwordForm.email, 'passwordEmail')

    const validatePassword = () => {
      errors.password = getPasswordValidationError(passwordForm.password)
      return !errors.password
    }

    const validateCodeEmail = () => validateEmailField(codeForm.email, 'codeEmail')

    const validateCode = () => {
      errors.code = getCodeValidationError(codeForm.code)
      return !errors.code
    }

    const handleLogin = async () => {
      if (loginMode.value === 'password') {
        if (!validatePasswordEmail() || !validatePassword()) {
          return
        }
      } else {
        if (!validateCodeEmail() || !validateCode()) {
          return
        }
      }
      loading.value = true
      try {
        let data
        if (loginMode.value === 'password') {
          data = await loginByPassword(passwordForm.email.trim(), passwordForm.password)
        } else {
          data = await loginByEmailCode(codeForm.email.trim(), codeForm.code.trim())
        }
        emit('loginSuccess', data)
      } catch (e) {
        alert(e.message || '登录失败')
      } finally {
        loading.value = false
      }
    }

    const sendCode = async () => {
      if (!validateCodeEmail()) {
        return
      }
      sendingCode.value = true
      try {
        await sendEmailCode(codeForm.email.trim(), 'login')
        countdown.value = 60
        const timer = setInterval(() => {
          countdown.value--
          if (countdown.value <= 0) clearInterval(timer)
        }, 1000)
      } catch (e) {
        alert(e.message || '发送验证码失败')
      } finally {
        sendingCode.value = false
      }
    }

    const socialLogin = (platform) => {
      console.log('第三方登录:', platform)
    }

    const goToRegister = () => {
      emit('goToRegister')
    }

    const goToAdminLogin = () => {
      emit('goToAdminLogin')
    }

    return {
      loginMode,
      showPassword,
      rememberMe,
      countdown,
      loading,
      sendingCode,
      passwordForm,
      codeForm,
      errors,
      validatePasswordEmail,
      validatePassword,
      validateCodeEmail,
      validateCode,
      handleLogin,
      sendCode,
      socialLogin,
      goToRegister,
      goToAdminLogin
    }
  }
}
</script>

<style scoped>
.login-container {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  position: relative;
}

.login-card {
  width: 100%;
  max-width: 420px;
  padding: 40px;
  position: relative;
  z-index: 1;
}

.login-header {
  text-align: center;
  margin-bottom: 32px;
}

.logo-icon {
  width: 64px;
  height: 64px;
  background: linear-gradient(135deg, var(--primary-color), var(--primary-light));
  border-radius: var(--radius-lg);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32px;
  color: white;
  margin: 0 auto 16px;
  box-shadow: 0 8px 24px rgba(59, 89, 152, 0.3);
}

.login-title {
  font-size: 28px;
  font-weight: 700;
  color: var(--text-primary);
  margin-bottom: 8px;
}

.login-subtitle {
  font-size: 14px;
  color: var(--text-secondary);
}

.login-tabs {
  display: flex;
  gap: 8px;
  margin-bottom: 24px;
}

.tab-btn {
  flex: 1;
  padding: 12px;
  background: var(--glass-bg);
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-md);
  font-size: 14px;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all var(--transition-normal);
  font-family: inherit;
}

.tab-btn:hover {
  background: rgba(59, 89, 152, 0.1);
}

.tab-btn.active {
  background: rgba(59, 89, 152, 0.2);
  border-color: var(--primary-color);
  color: var(--primary-color);
}

.login-form {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.form-group label {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-primary);
}

.error-msg {
  font-size: 12px;
  color: var(--danger-color);
}

.input-wrapper {
  position: relative;
  display: flex;
  align-items: center;
}

.input-wrapper i:first-child {
  position: absolute;
  left: 14px;
  color: var(--text-light);
  font-size: 16px;
}

.input-wrapper .glass-input {
  width: 100%;
  padding: 14px 16px 14px 44px;
  background: var(--glass-bg);
  backdrop-filter: blur(10px);
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-md);
  font-size: 14px;
  color: var(--text-primary);
  transition: all var(--transition-normal);
  font-family: inherit;
}

.input-wrapper .glass-input:focus {
  outline: none;
  border-color: var(--primary-color);
  box-shadow: 0 0 0 3px rgba(59, 89, 152, 0.2);
}

.input-wrapper .glass-input::placeholder {
  color: var(--text-light);
}

.toggle-password {
  position: absolute;
  right: 14px;
  color: var(--text-light);
  cursor: pointer;
  font-size: 16px;
}

.toggle-password:hover {
  color: var(--text-secondary);
}

.code-input .glass-input {
  padding-right: 120px;
}

.send-code-btn {
  position: absolute;
  right: 8px;
  padding: 8px 12px;
  background: linear-gradient(135deg, var(--primary-color), var(--primary-light));
  border: none;
  border-radius: var(--radius-sm);
  font-size: 12px;
  color: white;
  cursor: pointer;
  transition: all var(--transition-normal);
  font-family: inherit;
  white-space: nowrap;
}

.send-code-btn:disabled {
  background: var(--text-light);
  cursor: not-allowed;
}

.send-code-btn:not(:disabled):hover {
  transform: translateY(-1px);
}

.form-options {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 13px;
}

.remember-me {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--text-secondary);
  cursor: pointer;
}

.remember-me input {
  accent-color: var(--primary-color);
}

.forgot-password {
  color: var(--primary-color);
  text-decoration: none;
}

.forgot-password:hover {
  text-decoration: underline;
}

.login-btn {
  width: 100%;
  margin-top: 8px;
}

.divider {
  display: flex;
  align-items: center;
  margin: 24px 0;
}

.divider::before,
.divider::after {
  content: '';
  flex: 1;
  height: 1px;
  background: var(--glass-border);
}

.divider span {
  padding: 0 16px;
  font-size: 12px;
  color: var(--text-light);
}

.social-login {
  display: flex;
  justify-content: center;
  gap: 20px;
  margin-bottom: 24px;
}

.social-btn {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  border: 1px solid var(--glass-border);
  background: var(--glass-bg);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  cursor: pointer;
  transition: all var(--transition-normal);
}

.social-btn:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-medium);
}

.social-btn.github:hover {
  background: #24292e;
  border-color: #24292e;
  color: white;
}

.social-btn.wechat:hover {
  background: #07c160;
  border-color: #07c160;
  color: white;
}

.social-btn.google:hover {
  background: #ea4335;
  border-color: #ea4335;
  color: white;
}

.register-link {
  text-align: center;
  font-size: 14px;
  color: var(--text-secondary);
}

.register-link a {
  color: var(--primary-color);
  text-decoration: none;
  font-weight: 500;
}

.register-link a:hover {
  text-decoration: underline;
}

.admin-switch {
  margin-top: 24px;
  padding-top: 24px;
  border-top: 1px solid var(--glass-border);
  text-align: center;
}

.admin-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 12px 24px;
  background: transparent;
  border: 1px dashed var(--glass-border);
  border-radius: var(--radius-md);
  font-size: 14px;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all var(--transition-normal);
  font-family: inherit;
}

.admin-btn:hover {
  border-color: var(--primary-color);
  color: var(--primary-color);
  background: rgba(59, 89, 152, 0.05);
}

.admin-btn i {
  font-size: 16px;
}
</style>
