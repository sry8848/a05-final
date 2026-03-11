<template>
  <section id="page-settings" class="page-section active">
    <h3 class="section-title">
      <i class="fas fa-cog"></i>
      个人设置
    </h3>
    
    <div class="settings-container glass-card">
      <div class="settings-group">
        <h4>基本信息</h4>
        <div class="setting-item">
          <label>用户名</label>
          <input type="text" class="glass-input" v-model="localUser.name">
        </div>
        <div class="setting-item">
          <label>邮箱</label>
          <input type="email" class="glass-input" v-model="localUser.email">
        </div>
        <div class="setting-item">
          <label>目标岗位</label>
          <select class="glass-select" v-model="localUser.targetJob">
            <option>前端开发工程师</option>
            <option>后端开发工程师</option>
            <option>全栈开发工程师</option>
          </select>
        </div>
      </div>
      
      <div class="settings-group">
        <h4>偏好设置</h4>
        <div class="setting-item toggle-item">
          <label>深色模式</label>
          <label class="toggle-switch">
            <input type="checkbox" :checked="isDarkMode" @change="$emit('toggleDarkMode')">
            <span class="toggle-slider"></span>
          </label>
        </div>
        <div class="setting-item toggle-item">
          <label>声音提醒</label>
          <label class="toggle-switch">
            <input type="checkbox" v-model="localUser.soundEnabled">
            <span class="toggle-slider"></span>
          </label>
        </div>
        <div class="setting-item toggle-item">
          <label>自动保存</label>
          <label class="toggle-switch">
            <input type="checkbox" v-model="localUser.autoSave">
            <span class="toggle-slider"></span>
          </label>
        </div>
      </div>
      
      <div class="settings-group">
        <h4>通知设置</h4>
        <div class="setting-item toggle-item">
          <label>面试提醒</label>
          <label class="toggle-switch">
            <input type="checkbox" v-model="localUser.interviewReminder">
            <span class="toggle-slider"></span>
          </label>
        </div>
        <div class="setting-item toggle-item">
          <label>学习报告</label>
          <label class="toggle-switch">
            <input type="checkbox" v-model="localUser.weeklyReport">
            <span class="toggle-slider"></span>
          </label>
        </div>
      </div>
      
      <div class="settings-actions">
        <button class="btn btn-secondary glass-btn" @click="resetSettings">
          <i class="fas fa-undo"></i>
          重置
        </button>
        <button class="btn btn-primary glass-btn" @click="saveSettings">
          <i class="fas fa-save"></i>
          保存设置
        </button>
      </div>
    </div>
  </section>
</template>

<script>
import { reactive, watch } from 'vue'

export default {
  name: 'SettingsPage',
  props: {
    isDarkMode: {
      type: Boolean,
      default: false
    },
    user: {
      type: Object,
      default: () => ({})
    }
  },
  emits: ['toggleDarkMode', 'saveSettings'],
  setup(props, { emit }) {
    const localUser = reactive({
      name: '面试者',
      email: 'user@example.com',
      targetJob: '前端开发工程师',
      soundEnabled: true,
      autoSave: true,
      interviewReminder: true,
      weeklyReport: false
    })

    watch(() => props.user, (newUser) => {
      if (newUser) {
        Object.assign(localUser, newUser)
      }
    }, { immediate: true })

    const resetSettings = () => {
      localUser.name = '面试者'
      localUser.email = 'user@example.com'
      localUser.targetJob = '前端开发工程师'
      localUser.soundEnabled = true
      localUser.autoSave = true
      localUser.interviewReminder = true
      localUser.weeklyReport = false
    }

    const saveSettings = () => {
      emit('saveSettings', { ...localUser })
    }

    return {
      localUser,
      resetSettings,
      saveSettings
    }
  }
}
</script>

<style scoped>
.settings-container {
  max-width: 600px;
  padding: 32px;
}

.settings-group {
  margin-bottom: 32px;
}

.settings-group:last-of-type {
  margin-bottom: 0;
}

.settings-group h4 {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 20px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--glass-border);
  color: var(--text-primary);
}

.setting-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 0;
}

.setting-item label {
  font-size: 14px;
  color: var(--text-primary);
}

.glass-input {
  padding: 10px 16px;
  background: var(--glass-bg);
  backdrop-filter: blur(10px);
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-md);
  font-size: 14px;
  color: var(--text-primary);
  width: 200px;
  transition: all var(--transition-normal);
}

.glass-input:focus {
  outline: none;
  border-color: var(--primary-color);
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.2);
}

.toggle-item {
  cursor: pointer;
}

.toggle-switch {
  position: relative;
  display: inline-block;
  width: 50px;
  height: 26px;
}

.toggle-switch input {
  opacity: 0;
  width: 0;
  height: 0;
}

.toggle-slider {
  position: absolute;
  cursor: pointer;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: var(--glass-bg);
  border: 1px solid var(--glass-border);
  border-radius: 26px;
  transition: 0.3s;
}

.toggle-slider::before {
  position: absolute;
  content: "";
  height: 20px;
  width: 20px;
  left: 2px;
  bottom: 2px;
  background-color: white;
  border-radius: 50%;
  transition: 0.3s;
}

.toggle-switch input:checked + .toggle-slider {
  background: linear-gradient(135deg, var(--primary-color), var(--primary-light));
  border-color: var(--primary-color);
}

.toggle-switch input:checked + .toggle-slider::before {
  transform: translateX(24px);
}

.settings-actions {
  display: flex;
  gap: 16px;
  justify-content: flex-end;
  margin-top: 32px;
  padding-top: 24px;
  border-top: 1px solid var(--glass-border);
}
</style>
