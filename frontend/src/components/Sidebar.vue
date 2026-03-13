<template>
  <aside class="sidebar glass-card">
    <div class="logo-section">
      <div class="logo-icon">
        <i class="fas fa-robot"></i>
      </div>
      <h1 class="logo-text">AI面试官</h1>
    </div>
    
    <nav class="nav-menu">
      <ul class="nav-list">
        <li 
          class="nav-item" 
          :class="{ active: currentPage === 'growth' }"
          @click="$emit('navigate', 'growth')"
        >
          <i class="fas fa-seedling"></i>
          <span>成长中心</span>
        </li>
        <li 
          class="nav-item" 
          :class="{ active: currentPage === 'interview' }"
          @click="$emit('navigate', 'interview')"
        >
          <i class="fas fa-comments"></i>
          <span>开始面试</span>
        </li>
        <li 
          class="nav-item" 
          :class="{ active: currentPage === 'history' }"
          @click="$emit('navigate', 'history')"
        >
          <i class="fas fa-history"></i>
          <span class="history-label">
            面试记录
            <span v-if="historyHasUnread" class="history-dot"></span>
          </span>
        </li>
        <li 
          class="nav-item" 
          :class="{ active: currentPage === 'questionBank' }"
          @click="$emit('navigate', 'questionBank')"
        >
          <i class="fas fa-book"></i>
          <span>成长问答库</span>
        </li>
        <li 
          class="nav-item" 
          :class="{ active: currentPage === 'resumes' }"
          @click="$emit('navigate', 'resumes')"
        >
          <i class="fas fa-file-alt"></i>
          <span>简历管理</span>
        </li>
        <li 
          class="nav-item" 
          :class="{ active: currentPage === 'analysis' }"
          @click="$emit('navigate', 'analysis')"
        >
          <i class="fas fa-chart-line"></i>
          <span>数据分析</span>
        </li>
        <li 
          class="nav-item" 
          :class="{ active: currentPage === 'settings' }"
          @click="$emit('navigate', 'settings')"
        >
          <i class="fas fa-cog"></i>
          <span>个人设置</span>
        </li>
      </ul>
    </nav>
    
    <div class="sidebar-bottom">
      <div class="user-card glass-card-inner">
        <div class="user-avatar">
          <i class="fas fa-user"></i>
        </div>
        <div class="user-info">
          <span class="user-name">{{ user.name }}</span>
          <span class="user-level">{{ user.level }}</span>
        </div>
      </div>
      
      <button class="logout-btn" @click="$emit('logout')">
        <i class="fas fa-sign-out-alt"></i>
        <span>退出登录</span>
      </button>
    </div>
  </aside>
</template>

<script>
export default {
  name: 'Sidebar',
  props: {
    currentPage: {
      type: String,
      default: 'growth'
    },
    user: {
      type: Object,
      default: () => ({
        name: '面试者',
        level: 'Lv.1 初级工程师'
      })
    },
    historyHasUnread: {
      type: Boolean,
      default: false
    }
  },
  emits: ['navigate', 'logout']
}
</script>

<style scoped>
.history-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.history-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #ef4444;
  box-shadow: 0 0 0 2px rgba(239, 68, 68, 0.2);
}

.sidebar-bottom {
  margin-top: auto;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.logout-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 12px 16px;
  background: rgba(197, 48, 48, 0.1);
  border: 1px solid rgba(197, 48, 48, 0.2);
  border-radius: var(--radius-md);
  color: var(--danger-color);
  font-size: 14px;
  cursor: pointer;
  transition: all var(--transition-normal);
  font-family: inherit;
}

.logout-btn:hover {
  background: rgba(197, 48, 48, 0.2);
  border-color: rgba(197, 48, 48, 0.3);
}
</style>
