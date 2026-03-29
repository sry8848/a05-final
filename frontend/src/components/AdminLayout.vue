<template>
  <div class="admin-container">
    <aside class="admin-sidebar glass-card">
      <div class="admin-logo">
        <div class="logo-icon">
          <i class="fas fa-user-shield"></i>
        </div>
        <div class="logo-text">
          <span class="logo-title">AI面试官</span>
          <span class="logo-subtitle">管理后台</span>
        </div>
      </div>

      <nav class="admin-nav">
        <div class="nav-section">
          <span class="nav-section-title">核心功能</span>
          <div 
            v-for="item in mainNavItems" 
            :key="item.id"
            class="nav-item"
            :class="{ active: currentPage === item.id }"
            @click="navigateTo(item.id)"
          >
            <i :class="item.icon"></i>
            <span>{{ item.label }}</span>
          </div>
        </div>

        <div class="nav-section">
          <span class="nav-section-title">系统管理</span>
          <div 
            v-for="item in systemNavItems" 
            :key="item.id"
            class="nav-item"
            :class="{ active: currentPage === item.id }"
            @click="navigateTo(item.id)"
          >
            <i :class="item.icon"></i>
            <span>{{ item.label }}</span>
          </div>
        </div>
      </nav>

      <div class="admin-user" @click="showProfileModal = true">
        <div class="user-avatar">
          <i class="fas fa-user-tie"></i>
        </div>
        <div class="user-info">
          <span class="user-name">{{ user.name }}</span>
          <span class="user-role">超级管理员</span>
        </div>
        <button class="logout-btn" @click.stop="handleLogout">
          <i class="fas fa-sign-out-alt"></i>
        </button>
      </div>
    </aside>

    <div class="profile-modal-overlay" v-if="showProfileModal" @click="showProfileModal = false">
      <div class="profile-modal modal-panel" @click.stop>
        <div class="modal-header">
          <h3>
            <i class="fas fa-user-cog"></i>
            管理员资料编辑
          </h3>
          <button class="close-btn" @click="showProfileModal = false">
            <i class="fas fa-times"></i>
          </button>
        </div>

        <div class="modal-body">
          <div class="avatar-section">
            <div class="avatar-preview">
              <i class="fas fa-user-tie"></i>
            </div>
            <button class="change-avatar-btn">
              <i class="fas fa-camera"></i>
              更换头像
            </button>
          </div>

          <div class="form-section">
            <div class="form-group">
              <label class="form-label">
                <i class="fas fa-user"></i>
                用户名
              </label>
              <input type="text" class="form-input" v-model="profileForm.username" placeholder="请输入用户名">
            </div>

            <div class="form-group">
              <label class="form-label">
                <i class="fas fa-envelope"></i>
                邮箱
              </label>
              <input type="email" class="form-input" v-model="profileForm.email" placeholder="请输入邮箱">
            </div>

            <div class="form-group">
              <label class="form-label">
                <i class="fas fa-phone"></i>
                手机号
              </label>
              <input type="tel" class="form-input" v-model="profileForm.phone" placeholder="请输入手机号">
            </div>

            <div class="form-group">
              <label class="form-label">
                <i class="fas fa-building"></i>
                部门
              </label>
              <input type="text" class="form-input" v-model="profileForm.department" placeholder="请输入部门">
            </div>

            <div class="form-group">
              <label class="form-label">
                <i class="fas fa-id-badge"></i>
                职位
              </label>
              <input type="text" class="form-input" v-model="profileForm.position" placeholder="请输入职位">
            </div>
          </div>

          <div class="password-section">
            <div class="section-title">
              <i class="fas fa-lock"></i>
              修改密码
            </div>
            <div class="form-group">
              <label class="form-label">当前密码</label>
              <input type="password" class="form-input" v-model="passwordForm.current" placeholder="请输入当前密码">
            </div>
            <div class="form-group">
              <label class="form-label">新密码</label>
              <input type="password" class="form-input" v-model="passwordForm.new" placeholder="请输入新密码">
            </div>
            <div class="form-group">
              <label class="form-label">确认新密码</label>
              <input type="password" class="form-input" v-model="passwordForm.confirm" placeholder="请再次输入新密码">
            </div>
          </div>

          <div class="permissions-section">
            <div class="section-title">
              <i class="fas fa-shield-alt"></i>
              权限信息
            </div>
            <div class="permissions-list">
              <div class="permission-item">
                <i class="fas fa-check-circle"></i>
                <span>仪表盘查看</span>
              </div>
              <div class="permission-item">
                <i class="fas fa-check-circle"></i>
                <span>Prompt管理</span>
              </div>
              <div class="permission-item">
                <i class="fas fa-check-circle"></i>
                <span>模型配置</span>
              </div>
              <div class="permission-item">
                <i class="fas fa-check-circle"></i>
                <span>RAG语料管理</span>
              </div>
              <div class="permission-item">
                <i class="fas fa-check-circle"></i>
                <span>系统监控</span>
              </div>
              <div class="permission-item">
                <i class="fas fa-check-circle"></i>
                <span>数据分析</span>
              </div>
              <div class="permission-item">
                <i class="fas fa-check-circle"></i>
                <span>用户管理</span>
              </div>
              <div class="permission-item">
                <i class="fas fa-check-circle"></i>
                <span>系统设置</span>
              </div>
            </div>
          </div>
        </div>

        <div class="modal-footer">
          <button class="cancel-btn" @click="showProfileModal = false">取消</button>
          <button class="save-btn" @click="saveProfile">
            <i class="fas fa-save"></i>
            保存修改
          </button>
        </div>
      </div>
    </div>

    <main class="admin-content">
      <header class="admin-header glass-card">
        <div class="header-left">
          <h2 class="page-title">
            <i :class="currentPageIcon"></i>
            {{ currentPageTitle }}
          </h2>
        </div>
        <div class="header-right">
          <div class="header-stats">
            <div class="stat-item">
              <i class="fas fa-user-tie"></i>
              <span>当前管理员: {{ currentAdminName }}</span>
            </div>
            <div class="stat-item">
              <i class="fas fa-clock"></i>
              <span>最近刷新: {{ lastDashboardRefreshLabel }}</span>
            </div>
          </div>
          <div class="header-actions">
            <button class="action-btn" @click="toggleDarkMode">
              <i :class="isDarkMode ? 'fas fa-sun' : 'fas fa-moon'"></i>
            </button>
            <button
              v-if="currentPage === 'dashboard'"
              class="action-btn refresh-btn"
              :disabled="dashboardRefreshMeta.loading"
              @click="refreshDashboard"
            >
              <i :class="dashboardRefreshMeta.loading ? 'fas fa-spinner fa-spin' : 'fas fa-sync-alt'"></i>
              <span>{{ dashboardRefreshMeta.loading ? '刷新中' : '刷新' }}</span>
            </button>
          </div>
        </div>
      </header>

      <div class="admin-page-content">
        <AdminDashboard
          v-if="currentPage === 'dashboard'"
          :refreshNonce="dashboardRefreshNonce"
          :currentAdminName="currentAdminName"
          @refresh-meta="handleDashboardRefreshMeta"
          @auth-expired="handleAuthExpired"
        />
        <PromptLab v-else-if="currentPage === 'prompt'" />
        <ModelRouting v-else-if="currentPage === 'model'" />
        <RagManagement v-else-if="currentPage === 'rag'" />
        <SystemMonitor v-else-if="currentPage === 'monitor'" />
        <DataAnalysis v-else-if="currentPage === 'analysis'" />
      </div>
    </main>
  </div>
</template>

<script>
import { ref, computed, watch } from 'vue'
import AdminDashboard from './AdminDashboard.vue'
import PromptLab from './PromptLab.vue'
import ModelRouting from './ModelRouting.vue'
import RagManagement from './RagManagement.vue'
import SystemMonitor from './SystemMonitor.vue'
import DataAnalysis from './DataAnalysis.vue'

function formatRefreshTime(value) {
  if (!value) {
    return '未刷新'
  }

  const match = String(value).match(/^(\d{4}-\d{2}-\d{2})T(\d{2}:\d{2})/)
  if (match) {
    return `${match[1]} ${match[2]}`
  }

  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return '未刷新'
  }

  return date
    .toLocaleString('zh-CN', {
      hour12: false,
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit'
    })
    .replace(/\//g, '-')
}

export default {
  name: 'AdminLayout',
  components: {
    AdminDashboard,
    PromptLab,
    ModelRouting,
    RagManagement,
    SystemMonitor,
    DataAnalysis
  },
  props: {
    user: {
      type: Object,
      default: () => ({ name: '管理员' })
    },
    isDarkMode: {
      type: Boolean,
      default: false
    }
  },
  emits: ['logout', 'toggleDarkMode'],
  setup(props, { emit }) {
    const currentPage = ref('dashboard')
    const dashboardRefreshNonce = ref(0)
    const dashboardRefreshMeta = ref({
      lastRefreshedAt: null,
      loading: false
    })
    const showProfileModal = ref(false)

    const profileForm = ref({
      username: props.user?.name || 'admin',
      email: 'admin@example.com',
      phone: '138****8888',
      department: '技术部',
      position: '系统管理员'
    })

    const passwordForm = ref({
      current: '',
      new: '',
      confirm: ''
    })

    const mainNavItems = [
      { id: 'dashboard', label: '仪表盘', icon: 'fas fa-tachometer-alt' },
      { id: 'prompt', label: 'Prompt实验室', icon: 'fas fa-flask' },
      { id: 'model', label: '模型路由与成本', icon: 'fas fa-route' },
      { id: 'rag', label: 'RAG语料管理', icon: 'fas fa-database' }
    ]

    const systemNavItems = [
      { id: 'monitor', label: '系统监控', icon: 'fas fa-heartbeat' },
      { id: 'analysis', label: '数据分析', icon: 'fas fa-chart-bar' }
    ]

    const currentPageTitle = computed(() => {
      const allItems = [...mainNavItems, ...systemNavItems]
      const item = allItems.find(i => i.id === currentPage.value)
      return item ? item.label : '仪表盘'
    })

    const currentPageIcon = computed(() => {
      const allItems = [...mainNavItems, ...systemNavItems]
      const item = allItems.find(i => i.id === currentPage.value)
      return item ? item.icon : 'fas fa-tachometer-alt'
    })

    const currentAdminName = computed(() => props.user?.name || '管理员')

    const lastDashboardRefreshLabel = computed(() => (
      formatRefreshTime(dashboardRefreshMeta.value.lastRefreshedAt)
    ))

    const navigateTo = (page) => {
      currentPage.value = page
    }

    const handleLogout = () => {
      emit('logout')
    }

    const toggleDarkMode = () => {
      emit('toggleDarkMode')
    }

    const refreshDashboard = () => {
      dashboardRefreshNonce.value += 1
    }

    const handleDashboardRefreshMeta = (meta) => {
      dashboardRefreshMeta.value = {
        ...dashboardRefreshMeta.value,
        ...meta
      }
    }

    const handleAuthExpired = () => {
      emit('logout')
    }

    const saveProfile = () => {
      console.log('Saving profile:', profileForm.value)
      console.log('Password change:', passwordForm.value)
      showProfileModal.value = false
    }

    watch(() => props.user?.name, (name) => {
      profileForm.value.username = name || 'admin'
    }, { immediate: true })

    return {
      currentPage,
      currentAdminName,
      dashboardRefreshMeta,
      dashboardRefreshNonce,
      handleAuthExpired,
      handleDashboardRefreshMeta,
      mainNavItems,
      systemNavItems,
      currentPageTitle,
      currentPageIcon,
      lastDashboardRefreshLabel,
      navigateTo,
      handleLogout,
      refreshDashboard,
      toggleDarkMode,
      showProfileModal,
      profileForm,
      passwordForm,
      saveProfile
    }
  }
}
</script>

<style scoped>
.admin-container {
  display: flex;
  min-height: 100vh;
  position: relative;
  z-index: 1;
}

.admin-sidebar {
  width: 260px;
  padding: 20px 16px;
  display: flex;
  flex-direction: column;
  position: fixed;
  left: 0;
  top: 0;
  height: 100vh;
  z-index: 100;
  border-radius: 0;
  border-right: 1px solid var(--glass-border);
}

.admin-logo {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 12px;
  margin-bottom: 32px;
}

.admin-logo .logo-icon {
  width: 44px;
  height: 44px;
  background: linear-gradient(135deg, #1a1a2e, #3b5998);
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  color: white;
}

.logo-text {
  display: flex;
  flex-direction: column;
}

.logo-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--text-primary);
}

.logo-subtitle {
  font-size: 12px;
  color: var(--text-secondary);
}

.admin-nav {
  flex: 1;
  overflow-y: auto;
}

.nav-section {
  margin-bottom: 24px;
}

.nav-section-title {
  display: block;
  font-size: 11px;
  font-weight: 600;
  color: var(--text-light);
  text-transform: uppercase;
  letter-spacing: 1px;
  padding: 0 16px;
  margin-bottom: 8px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: all var(--transition-normal);
  color: var(--text-secondary);
  margin-bottom: 4px;
}

.nav-item:hover {
  background: rgba(59, 89, 152, 0.1);
  color: var(--primary-color);
}

.nav-item.active {
  background: rgba(59, 89, 152, 0.15);
  color: var(--primary-color);
  border-left: 3px solid var(--primary-color);
}

.nav-item i {
  font-size: 16px;
  width: 20px;
  text-align: center;
}

.nav-item span {
  font-size: 14px;
  font-weight: 500;
}

.admin-user {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  border-top: 1px solid var(--glass-border);
  margin-top: auto;
}

.user-avatar {
  width: 40px;
  height: 40px;
  background: linear-gradient(135deg, #1a1a2e, #3b5998);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 16px;
}

.user-info {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.user-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}

.user-role {
  font-size: 12px;
  color: var(--text-secondary);
}

.logout-btn {
  width: 36px;
  height: 36px;
  border: none;
  background: rgba(239, 68, 68, 0.1);
  border-radius: var(--radius-sm);
  color: #ef4444;
  cursor: pointer;
  transition: all var(--transition-normal);
  display: flex;
  align-items: center;
  justify-content: center;
}

.logout-btn:hover {
  background: rgba(239, 68, 68, 0.2);
}

.admin-content {
  flex: 1;
  margin-left: 260px;
  display: flex;
  flex-direction: column;
  min-height: 100vh;
}

.admin-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 24px;
  margin: 16px 24px 0;
  border-radius: var(--radius-lg);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.page-title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
}

.page-title i {
  color: var(--primary-color);
}

.header-right {
  display: flex;
  align-items: center;
  gap: 24px;
}

.header-stats {
  display: flex;
  gap: 24px;
}

.header-stats .stat-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--text-secondary);
}

.header-stats .stat-item i {
  color: var(--primary-color);
}

.header-actions {
  display: flex;
  gap: 8px;
}

.action-btn {
  position: relative;
  width: 40px;
  height: 40px;
  border: 1px solid var(--glass-border);
  background: var(--glass-bg);
  border-radius: var(--radius-sm);
  color: var(--text-secondary);
  cursor: pointer;
  transition: all var(--transition-normal);
  display: flex;
  align-items: center;
  justify-content: center;
}

.action-btn:hover {
  border-color: var(--primary-color);
  color: var(--primary-color);
}

.action-btn:disabled {
  cursor: not-allowed;
  opacity: 0.65;
}

.refresh-btn {
  width: auto;
  padding: 0 14px;
  gap: 8px;
}

.refresh-btn span {
  font-size: 13px;
  font-weight: 500;
}

.admin-page-content {
  flex: 1;
  padding: 24px;
  overflow-y: auto;
}

.profile-modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  backdrop-filter: blur(4px);
}

.profile-modal {
  width: 480px;
  max-height: 90vh;
  overflow-y: auto;
  border-radius: var(--radius-lg);
  padding: 0;
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px 24px;
  border-bottom: 1px solid var(--modal-surface-border);
}

.modal-header h3 {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 16px;
  font-weight: 600;
  color: var(--modal-text-primary);
  margin: 0;
}

.modal-header h3 i {
  color: var(--primary-color);
}

.close-btn {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: transparent;
  color: var(--modal-text-secondary);
  cursor: pointer;
  border-radius: var(--radius-sm);
}

.close-btn:hover {
  background: rgba(239, 68, 68, 0.1);
  color: #ef4444;
}

.modal-body {
  padding: 24px;
}

.avatar-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  margin-bottom: 24px;
  padding-bottom: 24px;
  border-bottom: 1px solid rgba(59, 89, 152, 0.15);
}

.avatar-preview {
  width: 80px;
  height: 80px;
  background: linear-gradient(135deg, #1a1a2e, #3b5998);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32px;
  color: white;
}

.change-avatar-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  background: rgba(59, 89, 152, 0.1);
  border: 1px solid rgba(59, 89, 152, 0.3);
  border-radius: var(--radius-sm);
  font-size: 13px;
  color: #3b5998;
  cursor: pointer;
  font-family: inherit;
}

.change-avatar-btn:hover {
  border-color: var(--primary-color);
  color: var(--primary-color);
}

.form-section {
  display: flex;
  flex-direction: column;
  gap: 16px;
  margin-bottom: 24px;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.form-label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: var(--modal-text-secondary);
}

.form-label i {
  color: var(--primary-color);
  font-size: 12px;
}

.form-input {
  padding: 10px 14px;
  background: var(--modal-input-bg);
  border: 1px solid rgba(148, 163, 184, 0.35);
  border-radius: var(--radius-sm);
  font-size: 14px;
  color: var(--modal-text-primary);
  font-family: inherit;
  transition: all 0.2s ease;
}

.form-input:focus {
  outline: none;
  border-color: var(--primary-color);
  background: #ffffff;
}

.form-input::placeholder {
  color: #9ca3af;
}

.password-section {
  padding: 20px;
  background: var(--modal-section-bg);
  border-radius: var(--radius-md);
  margin-bottom: 24px;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: var(--modal-text-primary);
  margin-bottom: 16px;
}

.section-title i {
  color: var(--primary-color);
}

.password-section .form-group {
  margin-bottom: 12px;
}

.password-section .form-group:last-child {
  margin-bottom: 0;
}

.permissions-section {
  padding: 20px;
  background: var(--modal-section-bg);
  border-radius: var(--radius-md);
}

.permissions-list {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}

.permission-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--modal-text-secondary);
}

.permission-item i {
  color: #10b981;
  font-size: 12px;
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 16px 24px;
  border-top: 1px solid var(--modal-surface-border);
}

.cancel-btn {
  padding: 10px 20px;
  background: transparent;
  border: 1px solid rgba(59, 89, 152, 0.3);
  border-radius: var(--radius-sm);
  font-size: 14px;
  color: var(--modal-text-secondary);
  cursor: pointer;
  font-family: inherit;
}

.cancel-btn:hover {
  border-color: #4b5563;
  color: var(--modal-text-primary);
}

.save-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 20px;
  background: linear-gradient(135deg, var(--primary-color), var(--primary-light));
  border: none;
  border-radius: var(--radius-sm);
  font-size: 14px;
  color: white;
  cursor: pointer;
  font-family: inherit;
}

.save-btn:hover {
  opacity: 0.9;
}
</style>
