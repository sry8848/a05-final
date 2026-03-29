<template>
  <div class="admin-login-container">
    <div class="background-decoration">
      <div class="blob blob-1"></div>
      <div class="blob blob-2"></div>
      <div class="blob blob-3"></div>
    </div>

    <div class="admin-login-card glass-card">
      <div class="admin-login-header">
        <div class="admin-logo-icon">
          <i class="fas fa-user-shield"></i>
        </div>
        <h1 class="admin-login-title">管理后台</h1>
        <p class="admin-login-subtitle">AI面试官管理系统</p>
      </div>

      <form class="admin-login-form" @submit.prevent="handleAdminLogin">
        <div class="form-group">
          <label>管理员账号</label>
          <div class="input-wrapper">
            <i class="fas fa-user-tie"></i>
            <input 
              v-model="loginForm.username" 
              type="text" 
              class="glass-input" 
              placeholder="请输入管理员账号"
            >
          </div>
        </div>

        <div class="form-group">
          <label>密码</label>
          <div class="input-wrapper">
            <i class="fas fa-lock"></i>
            <input 
              v-model="loginForm.password" 
              :type="showPassword ? 'text' : 'password'" 
              class="glass-input" 
              placeholder="请输入密码"
            >
            <i 
              :class="showPassword ? 'fas fa-eye-slash' : 'fas fa-eye'" 
              class="toggle-password"
              @click="showPassword = !showPassword"
            ></i>
          </div>
        </div>

        <div class="form-options">
          <label class="remember-me">
            <input type="checkbox" v-model="rememberMe">
            <span>记住我</span>
          </label>
        </div>

        <button type="submit" class="btn btn-primary btn-large login-btn" :disabled="loading">
          <i class="fas fa-sign-in-alt"></i>
          {{ loading ? '登录中...' : '登录' }}
        </button>
      </form>

      <div class="user-switch">
        <button type="button" class="user-btn" @click="goToUserLogin">
          <i class="fas fa-arrow-left"></i>
          返回用户端登录
        </button>
      </div>
    </div>
  </div>
</template>

<script>
import { ref, reactive } from 'vue'
import { loginAdmin } from '@/api/auth'

export default {
  name: 'AdminLoginPage',
  emits: ['loginSuccess', 'goToUserLogin'],
  setup(props, { emit }) {
    const showPassword = ref(false)
    const rememberMe = ref(false)
    const loading = ref(false)

    const loginForm = reactive({
      username: '',
      password: ''
    })

    const handleAdminLogin = async () => {
      if (!loginForm.username || !loginForm.password) {
        alert('请填写完整的登录信息')
        return
      }

      loading.value = true
      try {
        const data = await loginAdmin(loginForm.username, loginForm.password)
        emit('loginSuccess', data)
      } catch (e) {
        alert(e.message || '管理端登录失败')
      } finally {
        loading.value = false
      }
    }

    const goToUserLogin = () => {
      emit('goToUserLogin')
    }

    return {
      showPassword,
      rememberMe,
      loading,
      loginForm,
      handleAdminLogin,
      goToUserLogin
    }
  }
}
</script>

<style scoped>
.admin-login-container {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  position: relative;
}

.admin-login-card {
  width: 100%;
  max-width: 420px;
  padding: 40px;
  position: relative;
  z-index: 1;
}

.admin-login-header {
  text-align: center;
  margin-bottom: 32px;
}

.admin-logo-icon {
  width: 64px;
  height: 64px;
  background: linear-gradient(135deg, #1a1a2e, #3b5998);
  border-radius: var(--radius-lg);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32px;
  color: white;
  margin: 0 auto 16px;
  box-shadow: 0 8px 24px rgba(26, 26, 46, 0.4);
}

.admin-login-title {
  font-size: 28px;
  font-weight: 700;
  color: var(--text-primary);
  margin-bottom: 8px;
}

.admin-login-subtitle {
  font-size: 14px;
  color: var(--text-secondary);
}

.admin-login-form {
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

.captcha-input .glass-input {
  padding-right: 110px;
}

.captcha-box {
  position: absolute;
  right: 8px;
  width: 90px;
  height: 36px;
  background: linear-gradient(135deg, #f0f0f0, #e0e0e0);
  border-radius: var(--radius-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  user-select: none;
}

.captcha-text {
  font-size: 18px;
  font-weight: 700;
  letter-spacing: 4px;
  color: #3b5998;
  font-family: 'Courier New', monospace;
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

.login-btn {
  width: 100%;
  margin-top: 8px;
}

.user-switch {
  margin-top: 24px;
  padding-top: 24px;
  border-top: 1px solid var(--glass-border);
  text-align: center;
}

.user-btn {
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

.user-btn:hover {
  border-color: var(--primary-color);
  color: var(--primary-color);
  background: rgba(59, 89, 152, 0.05);
}

.user-btn i {
  font-size: 16px;
}
</style>
