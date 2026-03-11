<template>
  <div class="dashboard-page">
    <div class="overview-section">
      <div class="overview-cards">
        <div class="overview-card glass-card">
          <div class="card-icon users">
            <i class="fas fa-users"></i>
          </div>
          <div class="card-content">
            <span class="card-value">{{ overviewStats.totalUsers }}</span>
            <span class="card-label">总用户数</span>
          </div>
          <div class="card-trend up">
            <i class="fas fa-arrow-up"></i>
            +{{ overviewStats.newUsersToday }}
            <span class="trend-label">今日新增</span>
          </div>
        </div>

        <div class="overview-card glass-card">
          <div class="card-icon interviews">
            <i class="fas fa-comments"></i>
          </div>
          <div class="card-content">
            <span class="card-value">{{ overviewStats.totalInterviews }}</span>
            <span class="card-label">面试总场次</span>
          </div>
          <div class="card-trend up">
            <i class="fas fa-arrow-up"></i>
            +{{ overviewStats.interviewsToday }}
            <span class="trend-label">今日场次</span>
          </div>
        </div>

        <div class="overview-card glass-card highlight">
          <div class="card-icon active">
            <i class="fas fa-video"></i>
          </div>
          <div class="card-content">
            <span class="card-value live">{{ overviewStats.activeRooms }}</span>
            <span class="card-label">活跃面试房间</span>
          </div>
          <div class="live-indicator">
            <span class="pulse"></span>
            <span>进行中</span>
          </div>
        </div>

        <div class="overview-card glass-card">
          <div class="card-icon tokens">
            <i class="fas fa-coins"></i>
          </div>
          <div class="card-content">
            <span class="card-value">${{ overviewStats.todayCost }}</span>
            <span class="card-label">今日Token成本</span>
          </div>
          <div class="card-trend down">
            <i class="fas fa-arrow-down"></i>
            -3.2%
            <span class="trend-label">较昨日</span>
          </div>
        </div>
      </div>
    </div>

    <div class="main-grid">
      <div class="left-section">
        <div class="chart-card glass-card">
          <div class="card-header">
            <h4>
              <i class="fas fa-user-plus"></i>
              新增用户趋势
            </h4>
            <span class="chart-badge">最近7天</span>
          </div>
          <div class="chart-container">
            <svg viewBox="0 0 350 150" class="trend-chart">
              <defs>
                <linearGradient id="userGradient" x1="0%" y1="0%" x2="0%" y2="100%">
                  <stop offset="0%" stop-color="rgba(59, 89, 152, 0.4)" />
                  <stop offset="100%" stop-color="rgba(59, 89, 152, 0.05)" />
                </linearGradient>
              </defs>
              <g class="grid-lines">
                <line v-for="i in 4" :key="i" x1="30" :y1="20 + (i-1) * 30" x2="340" :y2="20 + (i-1) * 30" stroke="rgba(59, 89, 152, 0.1)" />
              </g>
              <path :d="userAreaPath" fill="url(#userGradient)" />
              <path :d="userLinePath" fill="none" stroke="var(--primary-color)" stroke-width="2" />
              <circle v-for="(point, index) in userPoints" :key="index" :cx="point.x" :cy="point.y" r="4" fill="var(--primary-color)" />
            </svg>
            <div class="chart-x-labels">
              <span v-for="label in dateLabels" :key="label">{{ label }}</span>
            </div>
          </div>
          <div class="chart-summary">
            <div class="summary-item">
              <span class="summary-label">本周新增</span>
              <span class="summary-value">{{ weeklyUserStats.total }}</span>
            </div>
            <div class="summary-item">
              <span class="summary-label">日均增长</span>
              <span class="summary-value">{{ weeklyUserStats.avg }}</span>
            </div>
          </div>
        </div>

        <div class="chart-card glass-card">
          <div class="card-header">
            <h4>
              <i class="fas fa-calendar-check"></i>
              面试场次趋势
            </h4>
            <span class="chart-badge">最近7天</span>
          </div>
          <div class="chart-container">
            <svg viewBox="0 0 350 150" class="trend-chart">
              <defs>
                <linearGradient id="interviewGradient" x1="0%" y1="0%" x2="0%" y2="100%">
                  <stop offset="0%" stop-color="rgba(16, 185, 129, 0.4)" />
                  <stop offset="100%" stop-color="rgba(16, 185, 129, 0.05)" />
                </linearGradient>
              </defs>
              <g class="grid-lines">
                <line v-for="i in 4" :key="i" x1="30" :y1="20 + (i-1) * 30" x2="340" :y2="20 + (i-1) * 30" stroke="rgba(16, 185, 129, 0.1)" />
              </g>
              <path :d="interviewAreaPath" fill="url(#interviewGradient)" />
              <path :d="interviewLinePath" fill="none" stroke="#10b981" stroke-width="2" />
              <circle v-for="(point, index) in interviewPoints" :key="index" :cx="point.x" :cy="point.y" r="4" fill="#10b981" />
            </svg>
            <div class="chart-x-labels">
              <span v-for="label in dateLabels" :key="label">{{ label }}</span>
            </div>
          </div>
          <div class="chart-summary">
            <div class="summary-item">
              <span class="summary-label">本周场次</span>
              <span class="summary-value">{{ weeklyInterviewStats.total }}</span>
            </div>
            <div class="summary-item">
              <span class="summary-label">日均场次</span>
              <span class="summary-value">{{ weeklyInterviewStats.avg }}</span>
            </div>
          </div>
        </div>

        <div class="chart-card glass-card">
          <div class="card-header">
            <h4>
              <i class="fas fa-coins"></i>
              Token消耗成本
            </h4>
            <span class="chart-badge">最近7天</span>
          </div>
          <div class="chart-container">
            <svg viewBox="0 0 350 150" class="trend-chart">
              <defs>
                <linearGradient id="costGradient" x1="0%" y1="0%" x2="0%" y2="100%">
                  <stop offset="0%" stop-color="rgba(245, 158, 11, 0.4)" />
                  <stop offset="100%" stop-color="rgba(245, 158, 11, 0.05)" />
                </linearGradient>
              </defs>
              <g class="grid-lines">
                <line v-for="i in 4" :key="i" x1="30" :y1="20 + (i-1) * 30" x2="340" :y2="20 + (i-1) * 30" stroke="rgba(245, 158, 11, 0.1)" />
              </g>
              <path :d="costAreaPath" fill="url(#costGradient)" />
              <path :d="costLinePath" fill="none" stroke="#f59e0b" stroke-width="2" />
              <circle v-for="(point, index) in costPoints" :key="index" :cx="point.x" :cy="point.y" r="4" fill="#f59e0b" />
            </svg>
            <div class="chart-x-labels">
              <span v-for="label in dateLabels" :key="label">{{ label }}</span>
            </div>
          </div>
          <div class="chart-summary">
            <div class="summary-item">
              <span class="summary-label">本周消耗</span>
              <span class="summary-value">${{ weeklyCostStats.total }}</span>
            </div>
            <div class="summary-item">
              <span class="summary-label">日均成本</span>
              <span class="summary-value">${{ weeklyCostStats.avg }}</span>
            </div>
          </div>
        </div>
      </div>

      <div class="right-section">
        <div class="api-status-card glass-card">
          <div class="card-header">
            <h4>
              <i class="fas fa-plug"></i>
              模型API状态
            </h4>
            <span class="status-badge healthy">全部正常</span>
          </div>
          <div class="api-list">
            <div v-for="api in apiStatusList" :key="api.name" class="api-item">
              <div class="api-info">
                <div class="api-icon" :style="{ background: api.color }">
                  <i :class="api.icon"></i>
                </div>
                <div class="api-details">
                  <span class="api-name">{{ api.name }}</span>
                  <span class="api-model">{{ api.model }}</span>
                </div>
              </div>
              <div class="api-metrics">
                <div class="metric">
                  <span class="metric-label">延迟</span>
                  <span class="metric-value" :class="getLatencyClass(api.latency)">{{ api.latency }}ms</span>
                </div>
                <div class="metric">
                  <span class="metric-label">错误率</span>
                  <span class="metric-value" :class="getErrorClass(api.errorRate)">{{ api.errorRate }}%</span>
                </div>
                <div class="status-dot" :class="api.status"></div>
              </div>
            </div>
          </div>
        </div>

        <div class="queue-status-card glass-card">
          <div class="card-header">
            <h4>
              <i class="fas fa-tasks"></i>
              任务队列状态
            </h4>
          </div>
          <div class="queue-stats">
            <div class="queue-item">
              <div class="queue-icon pending">
                <i class="fas fa-clock"></i>
              </div>
              <div class="queue-info">
                <span class="queue-value">{{ queueStats.pending }}</span>
                <span class="queue-label">等待中</span>
              </div>
            </div>
            <div class="queue-item">
              <div class="queue-icon processing">
                <i class="fas fa-spinner"></i>
              </div>
              <div class="queue-info">
                <span class="queue-value">{{ queueStats.processing }}</span>
                <span class="queue-label">处理中</span>
              </div>
            </div>
            <div class="queue-item">
              <div class="queue-icon completed">
                <i class="fas fa-check"></i>
              </div>
              <div class="queue-info">
                <span class="queue-value">{{ queueStats.completed }}</span>
                <span class="queue-label">已完成</span>
              </div>
            </div>
            <div class="queue-item">
              <div class="queue-icon failed">
                <i class="fas fa-times"></i>
              </div>
              <div class="queue-info">
                <span class="queue-value">{{ queueStats.failed }}</span>
                <span class="queue-label">失败</span>
              </div>
            </div>
          </div>
          <div class="queue-progress">
            <div class="progress-header">
              <span>队列处理进度</span>
              <span>{{ queueStats.progress }}%</span>
            </div>
            <div class="progress-bar">
              <div class="progress-fill" :style="{ width: queueStats.progress + '%' }"></div>
            </div>
          </div>
        </div>

        <div class="prompt-updates-card glass-card">
          <div class="card-header">
            <h4>
              <i class="fas fa-history"></i>
              Prompt版本更新
            </h4>
            <button class="view-all-btn">
              查看全部
              <i class="fas fa-chevron-right"></i>
            </button>
          </div>
          <div class="updates-list">
            <div v-for="update in promptUpdates" :key="update.id" class="update-item">
              <div class="update-icon" :class="update.type">
                <i :class="getUpdateIcon(update.type)"></i>
              </div>
              <div class="update-content">
                <span class="update-title">{{ update.title }}</span>
                <span class="update-desc">{{ update.description }}</span>
              </div>
              <div class="update-meta">
                <span class="update-version">v{{ update.version }}</span>
                <span class="update-time">{{ update.time }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { ref, computed } from 'vue'

export default {
  name: 'AdminDashboard',
  setup() {
    const overviewStats = ref({
      totalUsers: 2847,
      newUsersToday: 156,
      totalInterviews: 15632,
      interviewsToday: 423,
      activeRooms: 23,
      todayCost: 234.56
    })

    const dateLabels = ['3/1', '3/2', '3/3', '3/4', '3/5', '3/6', '3/7']

    const userData = ref([120, 145, 132, 178, 156, 189, 156])
    const interviewData = ref([320, 380, 350, 420, 390, 450, 423])
    const costData = ref([180, 210, 195, 245, 220, 260, 234])

    const generatePoints = (data) => {
      const maxVal = Math.max(...data)
      return data.map((val, index) => ({
        x: 50 + (index * 45),
        y: 130 - (val / maxVal) * 100
      }))
    }

    const generateLinePath = (points) => {
      return points.map((p, i) => `${i === 0 ? 'M' : 'L'} ${p.x} ${p.y}`).join(' ')
    }

    const generateAreaPath = (points) => {
      const linePath = generateLinePath(points)
      const lastX = points[points.length - 1].x
      return `${linePath} L ${lastX} 130 L 50 130 Z`
    }

    const userPoints = computed(() => generatePoints(userData.value))
    const userLinePath = computed(() => generateLinePath(userPoints.value))
    const userAreaPath = computed(() => generateAreaPath(userPoints.value))

    const interviewPoints = computed(() => generatePoints(interviewData.value))
    const interviewLinePath = computed(() => generateLinePath(interviewPoints.value))
    const interviewAreaPath = computed(() => generateAreaPath(interviewPoints.value))

    const costPoints = computed(() => generatePoints(costData.value))
    const costLinePath = computed(() => generateLinePath(costPoints.value))
    const costAreaPath = computed(() => generateAreaPath(costPoints.value))

    const weeklyUserStats = computed(() => ({
      total: userData.value.reduce((a, b) => a + b, 0),
      avg: Math.round(userData.value.reduce((a, b) => a + b, 0) / 7)
    }))

    const weeklyInterviewStats = computed(() => ({
      total: interviewData.value.reduce((a, b) => a + b, 0),
      avg: Math.round(interviewData.value.reduce((a, b) => a + b, 0) / 7)
    }))

    const weeklyCostStats = computed(() => ({
      total: costData.value.reduce((a, b) => a + b, 0).toFixed(2),
      avg: (costData.value.reduce((a, b) => a + b, 0) / 7).toFixed(2)
    }))

    const apiStatusList = ref([
      { name: 'OpenAI', model: 'GPT-4 / GPT-3.5', icon: 'fas fa-robot', color: '#10a37f', latency: 245, errorRate: 0.1, status: 'healthy' },
      { name: 'Anthropic', model: 'Claude 3', icon: 'fas fa-brain', color: '#d97706', latency: 312, errorRate: 0.2, status: 'healthy' },
      { name: 'Google', model: 'Gemini Pro', icon: 'fab fa-google', color: '#4285f4', latency: 189, errorRate: 0.3, status: 'healthy' },
      { name: 'Azure', model: 'OpenAI Services', icon: 'fab fa-microsoft', color: '#0078d4', latency: 156, errorRate: 0.0, status: 'healthy' }
    ])

    const queueStats = ref({
      pending: 45,
      processing: 12,
      completed: 1892,
      failed: 3,
      progress: 76
    })

    const promptUpdates = ref([
      { id: 1, title: '技术面试评估模板', description: '优化了评分逻辑和反馈生成', version: '2.3.1', type: 'update', time: '2小时前' },
      { id: 2, title: '行为面试引导', description: '新增追问机制和深度挖掘', version: '1.8.0', type: 'feature', time: '5小时前' },
      { id: 3, title: '系统提示词', description: '修复了角色扮演的一致性问题', version: '3.1.2', type: 'fix', time: '1天前' },
      { id: 4, title: '答案评估模板', description: '提升了代码评估的准确性', version: '2.0.0', type: 'update', time: '2天前' }
    ])

    const getLatencyClass = (latency) => {
      if (latency < 200) return 'fast'
      if (latency < 400) return 'normal'
      return 'slow'
    }

    const getErrorClass = (rate) => {
      if (rate < 0.5) return 'good'
      if (rate < 2) return 'warning'
      return 'danger'
    }

    const getUpdateIcon = (type) => {
      const icons = {
        update: 'fas fa-sync-alt',
        feature: 'fas fa-plus',
        fix: 'fas fa-bug'
      }
      return icons[type] || 'fas fa-code'
    }

    return {
      overviewStats,
      dateLabels,
      userPoints,
      userLinePath,
      userAreaPath,
      interviewPoints,
      interviewLinePath,
      interviewAreaPath,
      costPoints,
      costLinePath,
      costAreaPath,
      weeklyUserStats,
      weeklyInterviewStats,
      weeklyCostStats,
      apiStatusList,
      queueStats,
      promptUpdates,
      getLatencyClass,
      getErrorClass,
      getUpdateIcon
    }
  }
}
</script>

<style scoped>
.dashboard-page {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.overview-section {
  margin-bottom: 8px;
}

.overview-cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
}

.overview-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px 24px;
}

.overview-card.highlight {
  background: linear-gradient(135deg, rgba(59, 89, 152, 0.15), rgba(59, 89, 152, 0.05));
  border: 1px solid rgba(59, 89, 152, 0.3);
}

.card-icon {
  width: 52px;
  height: 52px;
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  color: white;
  flex-shrink: 0;
}

.card-icon.users { background: linear-gradient(135deg, #3b5998, #5a7ab8); }
.card-icon.interviews { background: linear-gradient(135deg, #10b981, #34d399); }
.card-icon.active { background: linear-gradient(135deg, #ef4444, #f87171); }
.card-icon.tokens { background: linear-gradient(135deg, #f59e0b, #fbbf24); }

.card-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.card-value {
  font-size: 28px;
  font-weight: 700;
  color: var(--text-primary);
}

.card-value.live {
  color: #ef4444;
}

.card-label {
  font-size: 13px;
  color: var(--text-secondary);
}

.card-trend {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  border-radius: var(--radius-sm);
  font-size: 12px;
  font-weight: 500;
  flex-shrink: 0;
}

.card-trend.up {
  background: rgba(16, 185, 129, 0.15);
  color: #10b981;
}

.card-trend.down {
  background: rgba(239, 68, 68, 0.15);
  color: #ef4444;
}

.trend-label {
  font-size: 11px;
  opacity: 0.8;
}

.live-indicator {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  background: rgba(239, 68, 68, 0.15);
  border-radius: var(--radius-sm);
  font-size: 12px;
  color: #ef4444;
  font-weight: 500;
}

.pulse {
  width: 8px;
  height: 8px;
  background: #ef4444;
  border-radius: 50%;
  animation: pulse 1.5s infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.5; transform: scale(1.2); }
}

.main-grid {
  display: grid;
  grid-template-columns: 1fr 380px;
  gap: 24px;
}

.left-section {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.right-section {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.chart-card {
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
  gap: 10px;
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
}

.card-header h4 i {
  color: var(--primary-color);
}

.chart-badge {
  padding: 4px 10px;
  background: rgba(59, 89, 152, 0.1);
  border-radius: var(--radius-sm);
  font-size: 11px;
  color: var(--primary-color);
  font-weight: 500;
}

.chart-container {
  height: 150px;
  margin-bottom: 12px;
}

.trend-chart {
  width: 100%;
  height: 100%;
}

.chart-x-labels {
  display: flex;
  justify-content: space-around;
  padding: 8px 40px 0;
  font-size: 11px;
  color: var(--text-secondary);
}

.chart-summary {
  display: flex;
  gap: 24px;
  padding-top: 12px;
  border-top: 1px solid var(--glass-border);
}

.summary-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.summary-label {
  font-size: 12px;
  color: var(--text-secondary);
}

.summary-value {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
}

.api-status-card {
  padding: 20px;
}

.status-badge {
  padding: 4px 10px;
  border-radius: var(--radius-sm);
  font-size: 11px;
  font-weight: 500;
}

.status-badge.healthy {
  background: rgba(16, 185, 129, 0.15);
  color: #10b981;
}

.api-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.api-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px;
  background: var(--glass-bg);
  border-radius: var(--radius-md);
}

.api-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.api-icon {
  width: 36px;
  height: 36px;
  border-radius: var(--radius-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 16px;
}

.api-details {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.api-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-primary);
}

.api-model {
  font-size: 11px;
  color: var(--text-secondary);
}

.api-metrics {
  display: flex;
  align-items: center;
  gap: 16px;
}

.metric {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
}

.metric-label {
  font-size: 10px;
  color: var(--text-light);
}

.metric-value {
  font-size: 13px;
  font-weight: 600;
}

.metric-value.fast { color: #10b981; }
.metric-value.normal { color: var(--text-primary); }
.metric-value.slow { color: #f59e0b; }
.metric-value.good { color: #10b981; }
.metric-value.warning { color: #f59e0b; }
.metric-value.danger { color: #ef4444; }

.status-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
}

.status-dot.healthy { background: #10b981; box-shadow: 0 0 8px rgba(16, 185, 129, 0.5); }
.status-dot.warning { background: #f59e0b; box-shadow: 0 0 8px rgba(245, 158, 11, 0.5); }
.status-dot.error { background: #ef4444; box-shadow: 0 0 8px rgba(239, 68, 68, 0.5); }

.queue-status-card {
  padding: 20px;
}

.queue-stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-bottom: 16px;
}

.queue-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 12px;
  background: var(--glass-bg);
  border-radius: var(--radius-md);
}

.queue-icon {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
}

.queue-icon.pending { background: rgba(245, 158, 11, 0.15); color: #f59e0b; }
.queue-icon.processing { background: rgba(59, 89, 152, 0.15); color: var(--primary-color); }
.queue-icon.completed { background: rgba(16, 185, 129, 0.15); color: #10b981; }
.queue-icon.failed { background: rgba(239, 68, 68, 0.15); color: #ef4444; }

.queue-info {
  text-align: center;
}

.queue-value {
  display: block;
  font-size: 18px;
  font-weight: 700;
  color: var(--text-primary);
}

.queue-label {
  font-size: 11px;
  color: var(--text-secondary);
}

.queue-progress {
  padding-top: 12px;
  border-top: 1px solid var(--glass-border);
}

.progress-header {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: var(--text-secondary);
  margin-bottom: 8px;
}

.progress-bar {
  height: 6px;
  background: rgba(59, 89, 152, 0.1);
  border-radius: 3px;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, var(--primary-color), var(--primary-light));
  border-radius: 3px;
  transition: width 0.5s ease;
}

.prompt-updates-card {
  padding: 20px;
  flex: 1;
}

.view-all-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  background: transparent;
  border: none;
  border-radius: var(--radius-sm);
  font-size: 12px;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all var(--transition-normal);
  font-family: inherit;
}

.view-all-btn:hover {
  color: var(--primary-color);
}

.updates-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.update-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 12px;
  background: var(--glass-bg);
  border-radius: var(--radius-md);
}

.update-icon {
  width: 32px;
  height: 32px;
  border-radius: var(--radius-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  flex-shrink: 0;
}

.update-icon.update { background: rgba(59, 89, 152, 0.15); color: var(--primary-color); }
.update-icon.feature { background: rgba(16, 185, 129, 0.15); color: #10b981; }
.update-icon.fix { background: rgba(239, 68, 68, 0.15); color: #ef4444; }

.update-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.update-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-primary);
}

.update-desc {
  font-size: 12px;
  color: var(--text-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.update-meta {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 2px;
  flex-shrink: 0;
}

.update-version {
  font-size: 11px;
  font-weight: 600;
  color: var(--primary-color);
}

.update-time {
  font-size: 11px;
  color: var(--text-light);
}
</style>
