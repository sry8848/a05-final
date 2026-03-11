<template>
  <div class="register-container">
    <div class="background-decoration">
      <div class="blob blob-1"></div>
      <div class="blob blob-2"></div>
      <div class="blob blob-3"></div>
    </div>

    <div class="register-card glass-card">
      <div class="register-header">
        <div class="logo-icon">
          <i class="fas fa-robot"></i>
        </div>
        <h1 class="register-title">创建账号</h1>
        <p class="register-subtitle">加入AI面试官，开启你的面试之旅</p>
      </div>

      <form class="register-form" @submit.prevent="handleRegister">
        <div class="form-group">
          <label>用户名</label>
          <div class="input-wrapper">
            <i class="fas fa-user"></i>
            <input 
              v-model="form.username" 
              type="text" 
              class="glass-input" 
              placeholder="请输入用户名"
              @blur="validateUsername"
            >
          </div>
          <span v-if="errors.username" class="error-msg">{{ errors.username }}</span>
        </div>

        <div class="form-group">
          <label>邮箱</label>
          <div class="input-wrapper">
            <i class="fas fa-envelope"></i>
            <input 
              v-model="form.email" 
              type="email" 
              class="glass-input" 
              placeholder="请输入邮箱"
              @blur="validateEmail"
            >
          </div>
          <span v-if="errors.email" class="error-msg">{{ errors.email }}</span>
        </div>

        <div class="form-group">
          <label>验证码</label>
          <div class="input-wrapper code-input">
            <i class="fas fa-shield-alt"></i>
            <input 
              v-model="form.code" 
              type="text" 
              class="glass-input" 
              placeholder="请输入验证码"
              maxlength="8"
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

        <div class="form-group">
          <label>密码</label>
          <div class="input-wrapper">
            <i class="fas fa-lock"></i>
            <input 
              v-model="form.password" 
              :type="showPassword ? 'text' : 'password'" 
              class="glass-input" 
              placeholder="请输入密码"
              @input="checkPasswordStrength"
              @blur="validatePassword"
            >
            <i 
              :class="showPassword ? 'fas fa-eye-slash' : 'fas fa-eye'" 
              class="toggle-password"
              @click="showPassword = !showPassword"
            ></i>
          </div>
          <div v-if="form.password" class="password-strength">
            <div class="strength-bars">
              <div 
                v-for="i in 4" 
                :key="i" 
                class="strength-bar"
                :class="{ 
                  active: i <= passwordStrength.level,
                  [passwordStrength.class]: i <= passwordStrength.level 
                }"
              ></div>
            </div>
            <span class="strength-text" :class="passwordStrength.class">{{ passwordStrength.text }}</span>
          </div>
          <span v-if="errors.password" class="error-msg">{{ errors.password }}</span>
        </div>

        <div class="form-group">
          <label>确认密码</label>
          <div class="input-wrapper">
            <i class="fas fa-lock"></i>
            <input 
              v-model="form.confirmPassword" 
              :type="showConfirmPassword ? 'text' : 'password'" 
              class="glass-input" 
              placeholder="请再次输入密码"
              @blur="validateConfirmPassword"
            >
            <i 
              :class="showConfirmPassword ? 'fas fa-eye-slash' : 'fas fa-eye'" 
              class="toggle-password"
              @click="showConfirmPassword = !showConfirmPassword"
            ></i>
          </div>
          <span v-if="errors.confirmPassword" class="error-msg">{{ errors.confirmPassword }}</span>
        </div>

        <div class="form-options">
          <label class="agree-terms">
            <input type="checkbox" v-model="form.agreeTerms">
            <span>我已阅读并同意 <a href="#">服务条款</a> 和 <a href="#">隐私政策</a></span>
          </label>
        </div>

        <button type="submit" class="btn btn-primary btn-large register-btn" :disabled="!form.agreeTerms || loading">
          <i class="fas fa-user-plus"></i>
          {{ loading ? '注册中...' : '注册' }}
        </button>
      </form>

      <div class="login-link">
        已有账号？<a href="#" @click.prevent="$emit('goToLogin')">立即登录</a>
      </div>
    </div>
  </div>
</template>

<script>
import { ref, reactive } from 'vue'
import { sendEmailCode, register } from '@/api/auth'

export default {
  name: 'RegisterPage',
  emits: ['registerSuccess', 'goToLogin'],
  setup(props, { emit }) {
    const showPassword = ref(false)
    const showConfirmPassword = ref(false)
    const countdown = ref(0)
    const loading = ref(false)
    const sendingCode = ref(false)

    const form = reactive({
      username: '',
      email: '',
      code: '',
      password: '',
      confirmPassword: '',
      agreeTerms: false
    })

    const errors = reactive({
      username: '',
      email: '',
      code: '',
      password: '',
      confirmPassword: ''
    })

    const passwordStrength = reactive({
      level: 0,
      text: '',
      class: ''
    })

    const validateUsername = () => {
      if (!form.username) {
        errors.username = '请输入用户名'
        return false
      }
      if (form.username.length < 3) {
        errors.username = '用户名至少3个字符'
        return false
      }
      if (form.username.length > 20) {
        errors.username = '用户名最多20个字符'
        return false
      }
      if (!/^[a-zA-Z0-9_\u4e00-\u9fa5]+$/.test(form.username)) {
        errors.username = '用户名只能包含字母、数字、下划线和中文'
        return false
      }
      errors.username = ''
      return true
    }

    const validateEmail = () => {
      if (!form.email) {
        errors.email = '请输入邮箱'
        return false
      }
      if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email)) {
        errors.email = '请输入有效的邮箱地址'
        return false
      }
      errors.email = ''
      return true
    }

    const validateCode = () => {
      if (!form.code || !form.code.trim()) {
        errors.code = '请输入验证码'
        return false
      }
      if (form.code.trim().length < 4 || form.code.trim().length > 8) {
        errors.code = '验证码为4-8位'
        return false
      }
      errors.code = ''
      return true
    }

    const validatePassword = () => {
      if (!form.password) {
        errors.password = '请输入密码'
        return false
      }
      if (form.password.length < 6) {
        errors.password = '密码至少6个字符'
        return false
      }
      if (form.password.length > 20) {
        errors.password = '密码最多20个字符'
        return false
      }
      errors.password = ''
      return true
    }

    const validateConfirmPassword = () => {
      if (!form.confirmPassword) {
        errors.confirmPassword = '请确认密码'
        return false
      }
      if (form.password !== form.confirmPassword) {
        errors.confirmPassword = '两次输入的密码不一致'
        return false
      }
      errors.confirmPassword = ''
      return true
    }

    const checkPasswordStrength = () => {
      const password = form.password
      let level = 0
      
      if (password.length >= 6) level++
      if (password.length >= 10) level++
      if (/[a-z]/.test(password) && /[A-Z]/.test(password)) level++
      if (/\d/.test(password)) level++
      if (/[!@#$%^&*(),.?":{}|<>]/.test(password)) level++

      level = Math.min(level, 4)

      const strengthMap = {
        0: { text: '', class: '' },
        1: { text: '弱', class: 'weak' },
        2: { text: '较弱', class: 'weak' },
        3: { text: '中等', class: 'medium' },
        4: { text: '强', class: 'strong' }
      }

      passwordStrength.level = level
      passwordStrength.text = strengthMap[level].text
      passwordStrength.class = strengthMap[level].class
    }

    const handleRegister = async () => {
      const isUsernameValid = validateUsername()
      const isEmailValid = validateEmail()
      const isCodeValid = validateCode()
      const isPasswordValid = validatePassword()
      const isConfirmValid = validateConfirmPassword()

      if (!isUsernameValid || !isEmailValid || !isCodeValid || !isPasswordValid || !isConfirmValid) {
        return
      }

      if (!form.agreeTerms) {
        alert('请先同意服务条款和隐私政策')
        return
      }

      loading.value = true
      try {
        await register({
          email: form.email,
          code: form.code.trim(),
          nickname: form.username.trim(),
          password: form.password
        })
        emit('registerSuccess', {
          username: form.username,
          email: form.email
        })
      } catch (e) {
        alert(e.message || '注册失败')
      } finally {
        loading.value = false
      }
    }

    const sendCode = async () => {
      if (!validateEmail()) return
      sendingCode.value = true
      try {
        const data = await sendEmailCode(form.email, 'register')
        countdown.value = 60
        const timer = setInterval(() => {
          countdown.value--
          if (countdown.value <= 0) clearInterval(timer)
        }, 1000)
        if (data && data.devCode) {
          alert('开发环境验证码：' + data.devCode)
        }
      } catch (e) {
        alert(e.message || '发送验证码失败')
      } finally {
        sendingCode.value = false
      }
    }

    return {
      showPassword,
      showConfirmPassword,
      countdown,
      loading,
      sendingCode,
      form,
      errors,
      passwordStrength,
      validateUsername,
      validateEmail,
      validateCode,
      validatePassword,
      validateConfirmPassword,
      checkPasswordStrength,
      handleRegister,
      sendCode
    }
  }
}
</script>

<style scoped>
.register-container {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  position: relative;
}

.register-card {
  width: 100%;
  max-width: 420px;
  padding: 40px;
  position: relative;
  z-index: 1;
}

.register-header {
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

.register-title {
  font-size: 28px;
  font-weight: 700;
  color: var(--text-primary);
  margin-bottom: 8px;
}

.register-subtitle {
  font-size: 14px;
  color: var(--text-secondary);
}

.register-form {
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

.error-msg {
  font-size: 12px;
  color: var(--danger-color);
}

.password-strength {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 4px;
}

.strength-bars {
  display: flex;
  gap: 4px;
}

.strength-bar {
  width: 40px;
  height: 4px;
  background: var(--glass-border);
  border-radius: 2px;
  transition: all var(--transition-normal);
}

.strength-bar.active.weak {
  background: #ef4444;
}

.strength-bar.active.medium {
  background: #f59e0b;
}

.strength-bar.active.strong {
  background: #10b981;
}

.strength-text {
  font-size: 12px;
  font-weight: 500;
}

.strength-text.weak {
  color: #ef4444;
}

.strength-text.medium {
  color: #f59e0b;
}

.strength-text.strong {
  color: #10b981;
}

.form-options {
  margin-top: -4px;
}

.agree-terms {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  font-size: 13px;
  color: var(--text-secondary);
  cursor: pointer;
  line-height: 1.5;
}

.agree-terms input {
  margin-top: 3px;
  accent-color: var(--primary-color);
}

.agree-terms a {
  color: var(--primary-color);
  text-decoration: none;
}

.agree-terms a:hover {
  text-decoration: underline;
}

.register-btn {
  width: 100%;
  margin-top: 8px;
}

.register-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.login-link {
  text-align: center;
  font-size: 14px;
  color: var(--text-secondary);
  margin-top: 24px;
}

.login-link a {
  color: var(--primary-color);
  text-decoration: none;
  font-weight: 500;
}

.login-link a:hover {
  text-decoration: underline;
}
</style>
