<template>
  <div class="prompt-lab-page">
    <div class="lab-header glass-card">
      <div class="header-info">
        <h3>
          <i class="fas fa-flask"></i>
          Prompt 实验室
        </h3>
        <p>Prompt版本管理、A/B测试与编辑发布</p>
      </div>
      <div class="header-actions">
        <button class="action-btn" @click="createNewVersion">
          <i class="fas fa-plus"></i>
          新建版本
        </button>
      </div>
    </div>

    <div class="lab-content">
      <div class="version-panel glass-card">
        <div class="panel-header">
          <h4>
            <i class="fas fa-code-branch"></i>
            版本控制
          </h4>
          <div class="filter-tabs">
            <button 
              v-for="status in statusFilters" 
              :key="status.value"
              :class="{ active: selectedStatus === status.value }"
              @click="selectedStatus = status.value"
            >
              {{ status.label }}
            </button>
          </div>
        </div>
        <div class="version-list">
          <div 
            v-for="version in filteredVersions" 
            :key="version.id"
            class="version-item"
            :class="{ active: selectedVersion?.id === version.id }"
            @click="selectVersion(version)"
          >
            <div class="version-header">
              <span class="version-name">{{ version.name }}</span>
              <span class="version-tag" :class="version.status">{{ getStatusLabel(version.status) }}</span>
            </div>
            <div class="version-meta">
              <span class="version-id">v{{ version.version }}</span>
              <span class="version-time">{{ version.createdAt }}</span>
            </div>
            <div class="version-stats" v-if="version.status === 'active'">
              <div class="stat">
                <i class="fas fa-users"></i>
                <span>{{ version.users }}用户</span>
              </div>
              <div class="stat">
                <i class="fas fa-chart-line"></i>
                <span>{{ version.successRate }}%</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="main-panel">
        <div class="editor-card glass-card">
          <div class="card-header">
            <div class="editor-title">
              <span v-if="selectedVersion">{{ selectedVersion.name }} - v{{ selectedVersion.version }}</span>
              <span v-else>选择一个版本进行编辑</span>
            </div>
            <div class="editor-actions" v-if="selectedVersion">
              <button class="btn-icon" title="保存草稿" @click="saveDraft">
                <i class="fas fa-save"></i>
              </button>
              <button class="btn-icon" title="发布" @click="publishVersion" v-if="selectedVersion.status === 'draft'">
                <i class="fas fa-rocket"></i>
              </button>
              <button class="btn-icon danger" title="删除" @click="deleteVersion">
                <i class="fas fa-trash"></i>
              </button>
            </div>
          </div>

          <div class="editor-body" v-if="selectedVersion">
            <div class="editor-section">
              <label>Prompt名称</label>
              <input type="text" v-model="editingVersion.name" class="glass-input" placeholder="输入Prompt名称">
            </div>

            <div class="editor-section">
              <label>Prompt类型</label>
              <select v-model="editingVersion.type" class="glass-select">
                <option value="interview">面试问答</option>
                <option value="evaluation">答案评估</option>
                <option value="hint">提示引导</option>
                <option value="summary">总结反馈</option>
              </select>
            </div>

            <div class="editor-section">
              <label>系统提示词 (System Prompt)</label>
              <textarea 
                v-model="editingVersion.systemPrompt" 
                class="glass-textarea code-editor"
                rows="6"
                placeholder="定义AI的角色、行为和能力..."
              ></textarea>
            </div>

            <div class="editor-section">
              <label>用户提示词模板 (User Prompt Template)</label>
              <textarea 
                v-model="editingVersion.userPrompt" 
                class="glass-textarea code-editor"
                rows="10"
                placeholder="使用 {{variable}} 插入变量，例如：{{question}}、{{answer}}"
              ></textarea>
            </div>

            <div class="editor-section">
              <label>模型参数</label>
              <div class="params-grid">
                <div class="param-item">
                  <span class="param-label">Temperature</span>
                  <div class="param-control">
                    <input type="range" v-model="editingVersion.temperature" min="0" max="2" step="0.1">
                    <span class="param-value">{{ editingVersion.temperature }}</span>
                  </div>
                </div>
                <div class="param-item">
                  <span class="param-label">Max Tokens</span>
                  <input type="number" v-model="editingVersion.maxTokens" class="glass-input small">
                </div>
                <div class="param-item">
                  <span class="param-label">Top P</span>
                  <div class="param-control">
                    <input type="range" v-model="editingVersion.topP" min="0" max="1" step="0.1">
                    <span class="param-value">{{ editingVersion.topP }}</span>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <div class="empty-state" v-else>
            <i class="fas fa-code-branch"></i>
            <p>从左侧选择一个版本开始编辑</p>
          </div>
        </div>

        <div class="ab-test-card glass-card">
          <div class="card-header">
            <h4>
              <i class="fas fa-flask"></i>
              A/B 测试配置
            </h4>
            <label class="toggle-switch">
              <input type="checkbox" v-model="abTest.enabled">
              <span class="toggle-slider"></span>
            </label>
          </div>

          <div class="ab-test-content" :class="{ disabled: !abTest.enabled }">
            <div class="test-versions">
              <div class="version-slot">
                <span class="slot-label">版本 A (对照组)</span>
                <select v-model="abTest.versionA" class="glass-select">
                  <option v-for="v in activeVersions" :key="v.id" :value="v.id">
                    {{ v.name }} v{{ v.version }}
                  </option>
                </select>
              </div>
              <div class="version-slot">
                <span class="slot-label">版本 B (实验组)</span>
                <select v-model="abTest.versionB" class="glass-select">
                  <option v-for="v in activeVersions" :key="v.id" :value="v.id">
                    {{ v.name }} v{{ v.version }}
                  </option>
                </select>
              </div>
            </div>

            <div class="traffic-config">
              <span class="config-label">流量分配</span>
              <div class="traffic-slider">
                <div class="traffic-bar">
                  <div class="traffic-a" :style="{ width: abTest.trafficA + '%' }">
                    <span>A: {{ abTest.trafficA }}%</span>
                  </div>
                  <div class="traffic-b" :style="{ width: (100 - abTest.trafficA) + '%' }">
                    <span>B: {{ 100 - abTest.trafficA }}%</span>
                  </div>
                </div>
                <input type="range" v-model="abTest.trafficA" min="0" max="100" step="5">
              </div>
            </div>

            <div class="metrics-config">
              <span class="config-label">测试指标</span>
              <div class="metrics-list">
                <label class="metric-checkbox" v-for="metric in availableMetrics" :key="metric.id">
                  <input type="checkbox" :value="metric.id" v-model="abTest.selectedMetrics">
                  <span class="checkbox-label">
                    <i :class="metric.icon"></i>
                    {{ metric.label }}
                  </span>
                </label>
              </div>
            </div>

            <div class="test-results" v-if="abTest.hasResults">
              <span class="config-label">测试结果</span>
              <div class="results-grid">
                <div class="result-item">
                  <span class="result-label">版本A胜出</span>
                  <span class="result-value">满意度 +12%</span>
                </div>
                <div class="result-item">
                  <span class="result-label">置信度</span>
                  <span class="result-value highlight">95.2%</span>
                </div>
                <div class="result-item">
                  <span class="result-label">样本量</span>
                  <span class="result-value">1,234</span>
                </div>
              </div>
            </div>

            <div class="test-actions">
              <button class="btn btn-secondary" @click="resetTest">重置</button>
              <button class="btn btn-primary" @click="startTest">开始测试</button>
            </div>
          </div>
        </div>
      </div>

      <div class="history-panel glass-card">
        <div class="panel-header">
          <h4>
            <i class="fas fa-history"></i>
            提交历史
          </h4>
        </div>
        <div class="history-list">
          <div v-for="commit in commitHistory" :key="commit.id" class="history-item">
            <div class="commit-icon" :class="commit.type">
              <i :class="getCommitIcon(commit.type)"></i>
            </div>
            <div class="commit-content">
              <span class="commit-message">{{ commit.message }}</span>
              <div class="commit-meta">
                <span class="commit-version">v{{ commit.version }}</span>
                <span class="commit-author">{{ commit.author }}</span>
                <span class="commit-time">{{ commit.time }}</span>
              </div>
            </div>
            <button class="btn-icon small" title="查看详情">
              <i class="fas fa-external-link-alt"></i>
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { ref, computed, reactive, watch } from 'vue'

export default {
  name: 'PromptLab',
  setup() {
    const selectedStatus = ref('all')
    const selectedVersion = ref(null)

    const statusFilters = [
      { value: 'all', label: '全部' },
      { value: 'active', label: '运行中' },
      { value: 'testing', label: '测试中' },
      { value: 'draft', label: '草稿' }
    ]

    const versions = ref([
      { 
        id: 1, 
        name: '技术面试评估', 
        version: '2.3.1', 
        status: 'active',
        type: 'evaluation',
        createdAt: '2024-03-07 14:30',
        users: 1247,
        successRate: 94.2,
        systemPrompt: '你是一位资深的技术面试官，专注于评估候选人的技术能力和问题解决能力...',
        userPrompt: '请评估以下回答：\n\n问题：{{question}}\n\n候选人回答：{{answer}}',
        temperature: 0.3,
        maxTokens: 2000,
        topP: 0.9
      },
      { 
        id: 2, 
        name: '技术面试评估', 
        version: '2.3.0', 
        status: 'active',
        type: 'evaluation',
        createdAt: '2024-03-05 10:15',
        users: 856,
        successRate: 91.8,
        systemPrompt: '你是一位资深的技术面试官...',
        userPrompt: '评估回答...',
        temperature: 0.4,
        maxTokens: 1500,
        topP: 0.85
      },
      { 
        id: 3, 
        name: '行为面试引导', 
        version: '1.8.0', 
        status: 'testing',
        type: 'hint',
        createdAt: '2024-03-06 16:45',
        users: 234,
        successRate: 88.5,
        systemPrompt: '你是一位耐心的行为面试导师...',
        userPrompt: '针对以下情况给出引导提示...',
        temperature: 0.5,
        maxTokens: 1000,
        topP: 0.9
      },
      { 
        id: 4, 
        name: '系统提示词优化', 
        version: '3.1.0', 
        status: 'draft',
        type: 'interview',
        createdAt: '2024-03-07 09:20',
        users: 0,
        successRate: 0,
        systemPrompt: '新版本系统提示词...',
        userPrompt: '新的用户提示词模板...',
        temperature: 0.7,
        maxTokens: 2500,
        topP: 0.95
      }
    ])

    const editingVersion = reactive({
      name: '',
      version: '',
      type: 'interview',
      systemPrompt: '',
      userPrompt: '',
      temperature: 0.7,
      maxTokens: 2000,
      topP: 0.9
    })

    const filteredVersions = computed(() => {
      if (selectedStatus.value === 'all') return versions.value
      return versions.value.filter(v => v.status === selectedStatus.value)
    })

    const activeVersions = computed(() => {
      return versions.value.filter(v => v.status === 'active' || v.status === 'testing')
    })

    const selectVersion = (version) => {
      selectedVersion.value = version
      Object.assign(editingVersion, JSON.parse(JSON.stringify(version)))
    }

    const createNewVersion = () => {
      const newVersion = {
        id: Date.now(),
        name: '新Prompt',
        version: '1.0.0',
        status: 'draft',
        type: 'interview',
        createdAt: new Date().toLocaleString('zh-CN'),
        users: 0,
        successRate: 0,
        systemPrompt: '',
        userPrompt: '',
        temperature: 0.7,
        maxTokens: 2000,
        topP: 0.9
      }
      versions.value.unshift(newVersion)
      selectVersion(newVersion)
    }

    const saveDraft = () => {
      if (selectedVersion.value) {
        Object.assign(selectedVersion.value, editingVersion)
        alert('草稿已保存')
      }
    }

    const publishVersion = () => {
      if (selectedVersion.value) {
        selectedVersion.value.status = 'active'
        alert('版本已发布')
      }
    }

    const deleteVersion = () => {
      if (confirm('确定要删除此版本吗？')) {
        const index = versions.value.findIndex(v => v.id === selectedVersion.value.id)
        if (index !== -1) {
          versions.value.splice(index, 1)
          selectedVersion.value = null
        }
      }
    }

    const getStatusLabel = (status) => {
      const labels = {
        active: '运行中',
        testing: '测试中',
        draft: '草稿',
        archived: '已归档'
      }
      return labels[status] || status
    }

    const abTest = reactive({
      enabled: true,
      versionA: 1,
      versionB: 3,
      trafficA: 50,
      selectedMetrics: ['satisfaction', 'accuracy'],
      hasResults: true
    })

    const availableMetrics = [
      { id: 'satisfaction', label: '用户满意度', icon: 'fas fa-smile' },
      { id: 'accuracy', label: '评估准确性', icon: 'fas fa-bullseye' },
      { id: 'latency', label: '响应延迟', icon: 'fas fa-clock' },
      { id: 'completion', label: '完成率', icon: 'fas fa-check-circle' }
    ]

    const startTest = () => {
      alert('A/B测试已启动')
    }

    const resetTest = () => {
      abTest.trafficA = 50
      abTest.selectedMetrics = []
    }

    const commitHistory = ref([
      { id: 1, message: '优化评分逻辑，提升准确性', version: '2.3.1', type: 'update', author: '张三', time: '2小时前' },
      { id: 2, message: '新增追问机制', version: '2.3.0', type: 'feature', author: '李四', time: '1天前' },
      { id: 3, message: '修复角色一致性问题', version: '2.2.1', type: 'fix', author: '张三', time: '2天前' },
      { id: 4, message: '更新系统提示词模板', version: '2.2.0', type: 'update', author: '王五', time: '3天前' },
      { id: 5, message: '新增代码评估能力', version: '2.1.0', type: 'feature', author: '李四', time: '5天前' },
      { id: 6, message: '初始化Prompt模板', version: '1.0.0', type: 'create', author: '张三', time: '1周前' }
    ])

    const getCommitIcon = (type) => {
      const icons = {
        update: 'fas fa-sync-alt',
        feature: 'fas fa-plus',
        fix: 'fas fa-bug',
        create: 'fas fa-code'
      }
      return icons[type] || 'fas fa-code-commit'
    }

    return {
      selectedStatus,
      selectedVersion,
      statusFilters,
      versions,
      editingVersion,
      filteredVersions,
      activeVersions,
      selectVersion,
      createNewVersion,
      saveDraft,
      publishVersion,
      deleteVersion,
      getStatusLabel,
      abTest,
      availableMetrics,
      startTest,
      resetTest,
      commitHistory,
      getCommitIcon
    }
  }
}
</script>

<style scoped>
.prompt-lab-page {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.lab-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px 24px;
}

.header-info h3 {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0 0 4px;
}

.header-info h3 i {
  color: var(--primary-color);
}

.header-info p {
  font-size: 13px;
  color: var(--text-secondary);
  margin: 0;
}

.action-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 20px;
  background: linear-gradient(135deg, var(--primary-color), var(--primary-light));
  border: none;
  border-radius: var(--radius-md);
  font-size: 14px;
  color: white;
  cursor: pointer;
  transition: all var(--transition-normal);
  font-family: inherit;
}

.action-btn:hover {
  transform: translateY(-2px);
}

.lab-content {
  display: grid;
  grid-template-columns: 280px 1fr 300px;
  gap: 24px;
}

.version-panel,
.history-panel {
  padding: 20px;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.panel-header h4 {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
}

.panel-header h4 i {
  color: var(--primary-color);
}

.filter-tabs {
  display: flex;
  gap: 4px;
}

.filter-tabs button {
  padding: 4px 10px;
  background: transparent;
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-sm);
  font-size: 11px;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all var(--transition-normal);
  font-family: inherit;
}

.filter-tabs button.active {
  background: var(--primary-color);
  border-color: var(--primary-color);
  color: white;
}

.version-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-height: calc(100vh - 300px);
  overflow-y: auto;
}

.version-item {
  padding: 14px;
  background: var(--glass-bg);
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: all var(--transition-normal);
}

.version-item:hover {
  border-color: var(--primary-color);
}

.version-item.active {
  border-color: var(--primary-color);
  background: rgba(59, 89, 152, 0.1);
}

.version-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.version-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-primary);
}

.version-tag {
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 10px;
  font-weight: 600;
}

.version-tag.active {
  background: rgba(16, 185, 129, 0.15);
  color: #10b981;
}

.version-tag.testing {
  background: rgba(245, 158, 11, 0.15);
  color: #f59e0b;
}

.version-tag.draft {
  background: rgba(107, 114, 128, 0.15);
  color: #6b7280;
}

.version-meta {
  display: flex;
  gap: 12px;
  font-size: 12px;
  color: var(--text-light);
  margin-bottom: 8px;
}

.version-stats {
  display: flex;
  gap: 12px;
  padding-top: 8px;
  border-top: 1px solid var(--glass-border);
}

.version-stats .stat {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: var(--text-secondary);
}

.version-stats .stat i {
  color: var(--primary-color);
}

.main-panel {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.editor-card,
.ab-test-card {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.card-header h4 {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
}

.card-header h4 i {
  color: var(--primary-color);
}

.editor-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}

.editor-actions {
  display: flex;
  gap: 8px;
}

.btn-icon {
  width: 36px;
  height: 36px;
  background: var(--glass-bg);
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-sm);
  color: var(--text-secondary);
  cursor: pointer;
  transition: all var(--transition-normal);
  display: flex;
  align-items: center;
  justify-content: center;
}

.btn-icon:hover {
  border-color: var(--primary-color);
  color: var(--primary-color);
}

.btn-icon.danger:hover {
  border-color: #ef4444;
  color: #ef4444;
}

.btn-icon.small {
  width: 28px;
  height: 28px;
  font-size: 12px;
}

.editor-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.editor-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.editor-section label {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-primary);
}

.glass-input {
  padding: 10px 14px;
  background: var(--glass-bg);
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-sm);
  font-size: 14px;
  color: var(--text-primary);
  font-family: inherit;
}

.glass-input.small {
  width: 120px;
}

.glass-input:focus {
  outline: none;
  border-color: var(--primary-color);
}

.glass-select {
  padding: 10px 14px;
  background: var(--glass-bg);
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-sm);
  font-size: 14px;
  color: var(--text-primary);
  font-family: inherit;
}

.glass-textarea {
  padding: 14px;
  background: var(--glass-bg);
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-md);
  font-size: 13px;
  color: var(--text-primary);
  resize: vertical;
  font-family: 'Fira Code', 'Consolas', monospace;
  line-height: 1.6;
}

.glass-textarea:focus {
  outline: none;
  border-color: var(--primary-color);
}

.code-editor {
  background: rgba(26, 26, 46, 0.3);
}

.params-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  padding: 16px;
  background: var(--glass-bg);
  border-radius: var(--radius-md);
}

.param-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.param-label {
  font-size: 12px;
  color: var(--text-secondary);
}

.param-control {
  display: flex;
  align-items: center;
  gap: 12px;
}

.param-control input[type="range"] {
  flex: 1;
}

.param-value {
  width: 36px;
  text-align: right;
  font-size: 13px;
  font-weight: 600;
  color: var(--primary-color);
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  color: var(--text-secondary);
}

.empty-state i {
  font-size: 48px;
  margin-bottom: 16px;
  opacity: 0.5;
}

.toggle-switch {
  position: relative;
  display: inline-block;
  width: 44px;
  height: 24px;
}

.toggle-switch input {
  opacity: 0;
  width: 0;
  height: 0;
}

.toggle-slider {
  position: absolute;
  cursor: pointer;
  inset: 0;
  background: var(--glass-bg);
  border: 1px solid var(--glass-border);
  border-radius: 24px;
  transition: all var(--transition-normal);
}

.toggle-slider::before {
  position: absolute;
  content: "";
  height: 18px;
  width: 18px;
  left: 2px;
  bottom: 2px;
  background: white;
  border-radius: 50%;
  transition: all var(--transition-normal);
}

.toggle-switch input:checked + .toggle-slider {
  background: var(--primary-color);
  border-color: var(--primary-color);
}

.toggle-switch input:checked + .toggle-slider::before {
  transform: translateX(20px);
}

.ab-test-content {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.ab-test-content.disabled {
  opacity: 0.5;
  pointer-events: none;
}

.test-versions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.version-slot {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.slot-label {
  font-size: 12px;
  font-weight: 500;
  color: var(--text-secondary);
}

.traffic-config,
.metrics-config,
.test-results {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.config-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-primary);
}

.traffic-slider {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.traffic-bar {
  display: flex;
  height: 32px;
  border-radius: var(--radius-sm);
  overflow: hidden;
}

.traffic-a,
.traffic-b {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 600;
  color: white;
  transition: width 0.3s ease;
}

.traffic-a {
  background: linear-gradient(135deg, var(--primary-color), var(--primary-light));
}

.traffic-b {
  background: linear-gradient(135deg, #10b981, #34d399);
}

.traffic-slider input[type="range"] {
  width: 100%;
}

.metrics-list {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;
}

.metric-checkbox {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  background: var(--glass-bg);
  border-radius: var(--radius-sm);
  cursor: pointer;
}

.metric-checkbox input {
  accent-color: var(--primary-color);
}

.checkbox-label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--text-primary);
}

.checkbox-label i {
  color: var(--primary-color);
}

.results-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}

.result-item {
  padding: 12px;
  background: var(--glass-bg);
  border-radius: var(--radius-sm);
  text-align: center;
}

.result-label {
  display: block;
  font-size: 11px;
  color: var(--text-secondary);
  margin-bottom: 4px;
}

.result-value {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}

.result-value.highlight {
  color: #10b981;
}

.test-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--glass-border);
}

.btn {
  padding: 10px 20px;
  border-radius: var(--radius-md);
  font-size: 14px;
  cursor: pointer;
  transition: all var(--transition-normal);
  font-family: inherit;
}

.btn-secondary {
  background: var(--glass-bg);
  border: 1px solid var(--glass-border);
  color: var(--text-secondary);
}

.btn-secondary:hover {
  border-color: var(--primary-color);
  color: var(--primary-color);
}

.btn-primary {
  background: linear-gradient(135deg, var(--primary-color), var(--primary-light));
  border: none;
  color: white;
}

.btn-primary:hover {
  transform: translateY(-2px);
}

.history-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-height: calc(100vh - 300px);
  overflow-y: auto;
}

.history-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 12px;
  background: var(--glass-bg);
  border-radius: var(--radius-md);
}

.commit-icon {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  flex-shrink: 0;
}

.commit-icon.update { background: rgba(59, 89, 152, 0.15); color: var(--primary-color); }
.commit-icon.feature { background: rgba(16, 185, 129, 0.15); color: #10b981; }
.commit-icon.fix { background: rgba(239, 68, 68, 0.15); color: #ef4444; }
.commit-icon.create { background: rgba(139, 92, 246, 0.15); color: #8b5cf6; }

.commit-content {
  flex: 1;
  min-width: 0;
}

.commit-message {
  display: block;
  font-size: 13px;
  font-weight: 500;
  color: var(--text-primary);
  margin-bottom: 4px;
}

.commit-meta {
  display: flex;
  gap: 8px;
  font-size: 11px;
  color: var(--text-light);
}

.commit-version {
  color: var(--primary-color);
  font-weight: 500;
}
</style>
