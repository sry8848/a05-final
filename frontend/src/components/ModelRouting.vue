<template>
  <div class="model-routing-page">
    <div class="page-header glass-card">
      <div class="header-info">
        <h3>
          <i class="fas fa-route"></i>
          模型路由与成本监控
        </h3>
        <p>监控模型API健康状态、管理成本与降级策略</p>
      </div>
    </div>

    <div class="api-health-section glass-card">
      <div class="section-header">
        <h4>
          <i class="fas fa-heartbeat"></i>
          模型API健康状态
        </h4>
        <span class="update-time">更新于 {{ lastUpdateTime }}</span>
      </div>
      <div class="api-grid">
        <div v-for="api in apiHealthList" :key="api.name" class="api-card" :class="api.status">
          <div class="api-header">
            <div class="api-icon" :style="{ background: api.color }">
              <i :class="api.icon"></i>
            </div>
            <div class="api-info">
              <span class="api-name">{{ api.name }}</span>
              <span class="api-model">{{ api.model }}</span>
            </div>
            <div class="status-indicator" :class="api.status">
              <span class="pulse"></span>
              <span>{{ getStatusText(api.status) }}</span>
            </div>
          </div>
          <div class="api-metrics">
            <div class="metric-item">
              <div class="metric-icon latency">
                <i class="fas fa-clock"></i>
              </div>
              <div class="metric-content">
                <span class="metric-label">延迟</span>
                <span class="metric-value" :class="getLatencyClass(api.latency)">{{ api.latency }}ms</span>
              </div>
              <div class="metric-bar">
                <div class="bar-fill" :style="{ width: Math.min(api.latency / 5, 100) + '%' }" :class="getLatencyClass(api.latency)"></div>
              </div>
            </div>
            <div class="metric-item">
              <div class="metric-icon error">
                <i class="fas fa-exclamation-triangle"></i>
              </div>
              <div class="metric-content">
                <span class="metric-label">错误率</span>
                <span class="metric-value" :class="getErrorClass(api.errorRate)">{{ api.errorRate }}%</span>
              </div>
              <div class="metric-bar">
                <div class="bar-fill" :style="{ width: api.errorRate * 10 + '%' }" :class="getErrorClass(api.errorRate)"></div>
              </div>
            </div>
            <div class="metric-item">
              <div class="metric-icon requests">
                <i class="fas fa-chart-line"></i>
              </div>
              <div class="metric-content">
                <span class="metric-label">请求/分钟</span>
                <span class="metric-value">{{ api.requestsPerMin }}</span>
              </div>
            </div>
            <div class="metric-item">
              <div class="metric-icon tokens">
                <i class="fas fa-coins"></i>
              </div>
              <div class="metric-content">
                <span class="metric-label">今日Token</span>
                <span class="metric-value">{{ formatNumber(api.todayTokens) }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="main-content">
      <div class="cost-section glass-card">
        <div class="section-header">
          <h4>
            <i class="fas fa-chart-area"></i>
            Token消耗成本趋势
          </h4>
          <div class="period-tabs">
            <button 
              v-for="period in periods" 
              :key="period.value"
              :class="{ active: selectedPeriod === period.value }"
              @click="selectedPeriod = period.value"
            >
              {{ period.label }}
            </button>
          </div>
        </div>
        
        <div class="cost-overview">
          <div class="cost-stat">
            <div class="stat-icon total">
              <i class="fas fa-dollar-sign"></i>
            </div>
            <div class="stat-content">
              <span class="stat-value">${{ costStats.totalCost }}</span>
              <span class="stat-label">总成本</span>
            </div>
          </div>
          <div class="cost-stat">
            <div class="stat-icon tokens">
              <i class="fas fa-coins"></i>
            </div>
            <div class="stat-content">
              <span class="stat-value">{{ formatNumber(costStats.totalTokens) }}</span>
              <span class="stat-label">总Token</span>
            </div>
          </div>
          <div class="cost-stat">
            <div class="stat-icon avg">
              <i class="fas fa-calculator"></i>
            </div>
            <div class="stat-content">
              <span class="stat-value">${{ costStats.avgCostPerDay }}</span>
              <span class="stat-label">日均成本</span>
            </div>
          </div>
          <div class="cost-stat">
            <div class="stat-icon trend">
              <i class="fas fa-arrow-down"></i>
            </div>
            <div class="stat-content">
              <span class="stat-value">{{ costStats.trend }}%</span>
              <span class="stat-label">较上周</span>
            </div>
          </div>
        </div>

        <div class="cost-chart">
          <svg viewBox="0 0 600 200" class="chart-svg">
            <defs>
              <linearGradient id="costGradient" x1="0%" y1="0%" x2="0%" y2="100%">
                <stop offset="0%" stop-color="rgba(59, 89, 152, 0.4)" />
                <stop offset="100%" stop-color="rgba(59, 89, 152, 0.05)" />
              </linearGradient>
            </defs>
            <g class="grid-lines">
              <line v-for="i in 4" :key="i" x1="40" :y1="20 + (i-1) * 40" x2="580" :y2="20 + (i-1) * 40" stroke="rgba(59, 89, 152, 0.1)" />
            </g>
            <g class="y-labels">
              <text v-for="(label, i) in costYLabels" :key="i" x="35" :y="25 + i * 40" text-anchor="end" font-size="11" fill="var(--text-secondary)">${{ label }}</text>
            </g>
            <path :d="costAreaPath" fill="url(#costGradient)" />
            <path :d="costLinePath" fill="none" stroke="var(--primary-color)" stroke-width="2" />
            <circle v-for="(point, index) in costPoints" :key="index" :cx="point.x" :cy="point.y" r="4" fill="var(--primary-color)" />
            <g class="x-labels">
              <text v-for="(label, index) in costXLabels" :key="index" :x="60 + index * 50" y="195" text-anchor="middle" font-size="11" fill="var(--text-secondary)">{{ label }}</text>
            </g>
          </svg>
        </div>

        <div class="model-cost-breakdown">
          <span class="breakdown-title">各模型成本占比</span>
          <div class="breakdown-bars">
            <div v-for="item in modelCostBreakdown" :key="item.name" class="breakdown-item">
              <div class="breakdown-header">
                <span class="model-name">{{ item.name }}</span>
                <span class="model-cost">${{ item.cost }}</span>
              </div>
              <div class="breakdown-bar">
                <div class="bar-fill" :style="{ width: item.percentage + '%', background: item.color }"></div>
              </div>
              <span class="breakdown-percent">{{ item.percentage }}%</span>
            </div>
          </div>
        </div>
      </div>

      <div class="degradation-section glass-card">
        <div class="section-header">
          <h4>
            <i class="fas fa-shield-alt"></i>
            动态降级策略
          </h4>
          <label class="toggle-switch">
            <input type="checkbox" v-model="degradationEnabled">
            <span class="toggle-slider"></span>
          </label>
        </div>

        <div class="degradation-content" :class="{ disabled: !degradationEnabled }">
          <div class="strategy-list">
            <div v-for="strategy in degradationStrategies" :key="strategy.id" class="strategy-item">
              <div class="strategy-header">
                <div class="strategy-icon" :class="strategy.type">
                  <i :class="getStrategyIcon(strategy.type)"></i>
                </div>
                <div class="strategy-info">
                  <span class="strategy-name">{{ strategy.name }}</span>
                  <span class="strategy-desc">{{ strategy.description }}</span>
                </div>
                <label class="toggle-switch small">
                  <input type="checkbox" v-model="strategy.enabled">
                  <span class="toggle-slider"></span>
                </label>
              </div>
              <div class="strategy-config" v-if="strategy.enabled">
                <div class="config-row">
                  <span class="config-label">触发条件</span>
                  <div class="config-inputs">
                    <select v-model="strategy.triggerMetric" class="glass-select small">
                      <option value="latency">延迟</option>
                      <option value="errorRate">错误率</option>
                      <option value="cost">成本</option>
                    </select>
                    <select v-model="strategy.triggerOperator" class="glass-select small">
                      <option value="gt">大于</option>
                      <option value="lt">小于</option>
                      <option value="eq">等于</option>
                    </select>
                    <input type="number" v-model="strategy.triggerValue" class="glass-input small">
                  </div>
                </div>
                <div class="config-row">
                  <span class="config-label">降级目标</span>
                  <select v-model="strategy.fallbackModel" class="glass-select">
                    <option v-for="model in availableModels" :key="model.id" :value="model.id">{{ model.name }}</option>
                  </select>
                </div>
                <div class="config-row">
                  <span class="config-label">冷却时间</span>
                  <div class="config-inputs">
                    <input type="number" v-model="strategy.cooldown" class="glass-input small">
                    <span class="config-unit">秒</span>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <div class="add-strategy">
            <button class="add-btn" @click="addStrategy">
              <i class="fas fa-plus"></i>
              添加降级策略
            </button>
          </div>

          <div class="degradation-log">
            <span class="log-title">最近降级记录</span>
            <div class="log-list">
              <div v-for="log in degradationLogs" :key="log.id" class="log-item">
                <div class="log-icon" :class="log.type">
                  <i :class="log.type === 'auto' ? 'fas fa-robot' : 'fas fa-hand-pointer'"></i>
                </div>
                <div class="log-content">
                  <span class="log-message">{{ log.message }}</span>
                  <span class="log-time">{{ log.time }}</span>
                </div>
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
  name: 'ModelRouting',
  setup() {
    const lastUpdateTime = ref('刚刚')
    const selectedPeriod = ref('week')
    const degradationEnabled = ref(true)

    const periods = [
      { value: 'day', label: '今日' },
      { value: 'week', label: '本周' },
      { value: 'month', label: '本月' }
    ]

    const apiHealthList = ref([
      { 
        name: 'OpenAI', 
        model: 'GPT-4 / GPT-3.5 Turbo',
        icon: 'fas fa-robot',
        color: '#10a37f',
        status: 'healthy',
        latency: 245,
        errorRate: 0.1,
        requestsPerMin: 1250,
        todayTokens: 456789
      },
      { 
        name: 'Anthropic', 
        model: 'Claude 3 Opus / Sonnet',
        icon: 'fas fa-brain',
        color: '#d97706',
        status: 'healthy',
        latency: 312,
        errorRate: 0.2,
        requestsPerMin: 890,
        todayTokens: 234567
      },
      { 
        name: 'Google', 
        model: 'Gemini Pro',
        icon: 'fab fa-google',
        color: '#4285f4',
        status: 'warning',
        latency: 520,
        errorRate: 1.5,
        requestsPerMin: 456,
        todayTokens: 123456
      },
      { 
        name: 'Azure', 
        model: 'OpenAI Services',
        icon: 'fab fa-microsoft',
        color: '#0078d4',
        status: 'healthy',
        latency: 156,
        errorRate: 0.0,
        requestsPerMin: 2100,
        todayTokens: 567890
      }
    ])

    const costStats = ref({
      totalCost: '1,234.56',
      totalTokens: 1382702,
      avgCostPerDay: '176.37',
      trend: -8.5
    })

    const costData = ref([180, 210, 195, 245, 220, 260, 234, 280, 250, 290, 270, 234])
    const costXLabels = ['3/1', '3/2', '3/3', '3/4', '3/5', '3/6', '3/7', '3/8', '3/9', '3/10', '3/11', '3/12']
    const costYLabels = ['300', '200', '100', '0']

    const costPoints = computed(() => {
      const maxVal = Math.max(...costData.value)
      return costData.value.map((val, index) => ({
        x: 60 + (index * 45),
        y: 180 - (val / maxVal) * 140
      }))
    })

    const costLinePath = computed(() => {
      return costPoints.value.map((p, i) => `${i === 0 ? 'M' : 'L'} ${p.x} ${p.y}`).join(' ')
    })

    const costAreaPath = computed(() => {
      const points = costPoints.value
      const lastX = points[points.length - 1].x
      return `${costLinePath.value} L ${lastX} 180 L 60 180 Z`
    })

    const modelCostBreakdown = ref([
      { name: 'GPT-4', cost: '523.45', percentage: 42, color: '#3b5998' },
      { name: 'Claude 3', cost: '398.77', percentage: 32, color: '#d97706' },
      { name: 'GPT-3.5', cost: '186.34', percentage: 15, color: '#10a37f' },
      { name: 'Gemini', cost: '126.00', percentage: 11, color: '#4285f4' }
    ])

    const degradationStrategies = ref([
      {
        id: 1,
        name: '延迟降级',
        description: '当主模型延迟过高时自动切换',
        type: 'latency',
        enabled: true,
        triggerMetric: 'latency',
        triggerOperator: 'gt',
        triggerValue: 500,
        fallbackModel: 'gpt-35',
        cooldown: 60
      },
      {
        id: 2,
        name: '错误率降级',
        description: '当错误率超过阈值时切换备用模型',
        type: 'error',
        enabled: true,
        triggerMetric: 'errorRate',
        triggerOperator: 'gt',
        triggerValue: 5,
        fallbackModel: 'claude-3',
        cooldown: 120
      },
      {
        id: 3,
        name: '成本控制降级',
        description: '当日成本超过预算时切换低成本模型',
        type: 'cost',
        enabled: false,
        triggerMetric: 'cost',
        triggerOperator: 'gt',
        triggerValue: 500,
        fallbackModel: 'gpt-35',
        cooldown: 300
      }
    ])

    const availableModels = ref([
      { id: 'gpt-4', name: 'GPT-4' },
      { id: 'gpt-35', name: 'GPT-3.5 Turbo' },
      { id: 'claude-3', name: 'Claude 3' },
      { id: 'gemini', name: 'Gemini Pro' }
    ])

    const degradationLogs = ref([
      { id: 1, type: 'auto', message: 'GPT-4 延迟过高(520ms)，自动切换至 GPT-3.5', time: '10分钟前' },
      { id: 2, type: 'auto', message: 'Gemini 错误率上升(2.1%)，已切换至 Claude 3', time: '1小时前' },
      { id: 3, type: 'manual', message: '手动切换主模型为 GPT-4', time: '2小时前' },
      { id: 4, type: 'auto', message: '成本降级策略触发，切换至 GPT-3.5', time: '3小时前' }
    ])

    const getStatusText = (status) => {
      const texts = {
        healthy: '正常',
        warning: '警告',
        error: '异常'
      }
      return texts[status] || status
    }

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

    const getStrategyIcon = (type) => {
      const icons = {
        latency: 'fas fa-clock',
        error: 'fas fa-exclamation-triangle',
        cost: 'fas fa-dollar-sign'
      }
      return icons[type] || 'fas fa-cog'
    }

    const addStrategy = () => {
      degradationStrategies.value.push({
        id: Date.now(),
        name: '新降级策略',
        description: '自定义降级策略',
        type: 'custom',
        enabled: false,
        triggerMetric: 'latency',
        triggerOperator: 'gt',
        triggerValue: 500,
        fallbackModel: 'gpt-35',
        cooldown: 60
      })
    }

    const formatNumber = (num) => {
      if (num >= 1000000) return (num / 1000000).toFixed(1) + 'M'
      if (num >= 1000) return (num / 1000).toFixed(1) + 'K'
      return num.toString()
    }

    return {
      lastUpdateTime,
      selectedPeriod,
      degradationEnabled,
      periods,
      apiHealthList,
      costStats,
      costData,
      costXLabels,
      costYLabels,
      costPoints,
      costLinePath,
      costAreaPath,
      modelCostBreakdown,
      degradationStrategies,
      availableModels,
      degradationLogs,
      getStatusText,
      getLatencyClass,
      getErrorClass,
      getStrategyIcon,
      addStrategy,
      formatNumber
    }
  }
}
</script>

<style scoped>
.model-routing-page {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.page-header {
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

.api-health-section {
  padding: 24px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.section-header h4 {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
}

.section-header h4 i {
  color: var(--primary-color);
}

.update-time {
  font-size: 12px;
  color: var(--text-light);
}

.api-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 20px;
}

.api-card {
  padding: 20px;
  background: var(--glass-bg);
  border-radius: var(--radius-lg);
  border: 1px solid var(--glass-border);
}

.api-card.healthy { border-left: 4px solid #10b981; }
.api-card.warning { border-left: 4px solid #f59e0b; }
.api-card.error { border-left: 4px solid #ef4444; }

.api-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.api-icon {
  width: 44px;
  height: 44px;
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 20px;
}

.api-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.api-name {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
}

.api-model {
  font-size: 12px;
  color: var(--text-secondary);
}

.status-indicator {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  border-radius: var(--radius-sm);
  font-size: 12px;
  font-weight: 500;
}

.status-indicator.healthy { background: rgba(16, 185, 129, 0.15); color: #10b981; }
.status-indicator.warning { background: rgba(245, 158, 11, 0.15); color: #f59e0b; }
.status-indicator.error { background: rgba(239, 68, 68, 0.15); color: #ef4444; }

.pulse {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: currentColor;
  animation: pulse 1.5s infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

.api-metrics {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}

.metric-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px;
  background: rgba(255, 255, 255, 0.03);
  border-radius: var(--radius-sm);
}

.metric-icon {
  width: 32px;
  height: 32px;
  border-radius: var(--radius-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
}

.metric-icon.latency { background: rgba(59, 89, 152, 0.15); color: var(--primary-color); }
.metric-icon.error { background: rgba(239, 68, 68, 0.15); color: #ef4444; }
.metric-icon.requests { background: rgba(16, 185, 129, 0.15); color: #10b981; }
.metric-icon.tokens { background: rgba(245, 158, 11, 0.15); color: #f59e0b; }

.metric-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.metric-label {
  font-size: 11px;
  color: var(--text-secondary);
}

.metric-value {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}

.metric-value.fast { color: #10b981; }
.metric-value.normal { color: var(--text-primary); }
.metric-value.slow { color: #f59e0b; }
.metric-value.good { color: #10b981; }
.metric-value.warning { color: #f59e0b; }
.metric-value.danger { color: #ef4444; }

.metric-bar {
  width: 60px;
  height: 4px;
  background: rgba(59, 89, 152, 0.1);
  border-radius: 2px;
  overflow: hidden;
}

.metric-bar .bar-fill {
  height: 100%;
  border-radius: 2px;
  transition: width 0.3s ease;
}

.metric-bar .bar-fill.fast { background: #10b981; }
.metric-bar .bar-fill.normal { background: var(--primary-color); }
.metric-bar .bar-fill.slow { background: #f59e0b; }
.metric-bar .bar-fill.good { background: #10b981; }
.metric-bar .bar-fill.warning { background: #f59e0b; }
.metric-bar .bar-fill.danger { background: #ef4444; }

.main-content {
  display: grid;
  grid-template-columns: 1fr 400px;
  gap: 24px;
}

.cost-section,
.degradation-section {
  padding: 24px;
}

.period-tabs {
  display: flex;
  gap: 4px;
}

.period-tabs button {
  padding: 6px 14px;
  background: transparent;
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-sm);
  font-size: 12px;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all var(--transition-normal);
  font-family: inherit;
}

.period-tabs button.active {
  background: var(--primary-color);
  border-color: var(--primary-color);
  color: white;
}

.cost-overview {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 24px;
}

.cost-stat {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  background: var(--glass-bg);
  border-radius: var(--radius-md);
}

.cost-stat .stat-icon {
  width: 40px;
  height: 40px;
  border-radius: var(--radius-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 16px;
}

.cost-stat .stat-icon.total { background: linear-gradient(135deg, #3b5998, #5a7ab8); }
.cost-stat .stat-icon.tokens { background: linear-gradient(135deg, #f59e0b, #fbbf24); }
.cost-stat .stat-icon.avg { background: linear-gradient(135deg, #10b981, #34d399); }
.cost-stat .stat-icon.trend { background: linear-gradient(135deg, #8b5cf6, #a78bfa); }

.cost-stat .stat-content {
  display: flex;
  flex-direction: column;
}

.cost-stat .stat-value {
  font-size: 18px;
  font-weight: 700;
  color: var(--text-primary);
}

.cost-stat .stat-label {
  font-size: 12px;
  color: var(--text-secondary);
}

.cost-chart {
  margin-bottom: 24px;
}

.chart-svg {
  width: 100%;
  height: 200px;
}

.model-cost-breakdown {
  padding-top: 20px;
  border-top: 1px solid var(--glass-border);
}

.breakdown-title {
  display: block;
  font-size: 13px;
  font-weight: 500;
  color: var(--text-primary);
  margin-bottom: 16px;
}

.breakdown-bars {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.breakdown-item {
  display: flex;
  align-items: center;
  gap: 12px;
}

.breakdown-header {
  width: 140px;
  display: flex;
  justify-content: space-between;
}

.model-name {
  font-size: 13px;
  color: var(--text-primary);
}

.model-cost {
  font-size: 12px;
  color: var(--text-secondary);
}

.breakdown-bar {
  flex: 1;
  height: 8px;
  background: rgba(59, 89, 152, 0.1);
  border-radius: 4px;
  overflow: hidden;
}

.breakdown-bar .bar-fill {
  height: 100%;
  border-radius: 4px;
  transition: width 0.5s ease;
}

.breakdown-percent {
  width: 40px;
  font-size: 12px;
  font-weight: 600;
  color: var(--text-primary);
  text-align: right;
}

.degradation-content {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.degradation-content.disabled {
  opacity: 0.5;
  pointer-events: none;
}

.strategy-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.strategy-item {
  padding: 16px;
  background: var(--glass-bg);
  border-radius: var(--radius-md);
  border: 1px solid var(--glass-border);
}

.strategy-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.strategy-icon {
  width: 36px;
  height: 36px;
  border-radius: var(--radius-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
}

.strategy-icon.latency { background: rgba(59, 89, 152, 0.15); color: var(--primary-color); }
.strategy-icon.error { background: rgba(239, 68, 68, 0.15); color: #ef4444; }
.strategy-icon.cost { background: rgba(245, 158, 11, 0.15); color: #f59e0b; }
.strategy-icon.custom { background: rgba(139, 92, 246, 0.15); color: #8b5cf6; }

.strategy-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.strategy-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-primary);
}

.strategy-desc {
  font-size: 12px;
  color: var(--text-secondary);
}

.toggle-switch {
  position: relative;
  display: inline-block;
  width: 44px;
  height: 24px;
}

.toggle-switch.small {
  width: 36px;
  height: 20px;
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

.toggle-switch.small .toggle-slider::before {
  height: 14px;
  width: 14px;
}

.toggle-switch input:checked + .toggle-slider {
  background: var(--primary-color);
  border-color: var(--primary-color);
}

.toggle-switch input:checked + .toggle-slider::before {
  transform: translateX(20px);
}

.toggle-switch.small input:checked + .toggle-slider::before {
  transform: translateX(16px);
}

.strategy-config {
  padding-top: 12px;
  border-top: 1px solid var(--glass-border);
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.config-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.config-label {
  width: 80px;
  font-size: 12px;
  color: var(--text-secondary);
}

.config-inputs {
  display: flex;
  align-items: center;
  gap: 8px;
}

.glass-select {
  padding: 8px 12px;
  background: var(--glass-bg);
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-sm);
  font-size: 13px;
  color: var(--text-primary);
  font-family: inherit;
}

.glass-select.small {
  padding: 6px 10px;
  font-size: 12px;
}

.glass-input {
  padding: 8px 12px;
  background: var(--glass-bg);
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-sm);
  font-size: 13px;
  color: var(--text-primary);
  font-family: inherit;
}

.glass-input.small {
  width: 80px;
  padding: 6px 10px;
  font-size: 12px;
}

.config-unit {
  font-size: 12px;
  color: var(--text-secondary);
}

.add-strategy {
  padding: 12px;
  border: 1px dashed var(--glass-border);
  border-radius: var(--radius-md);
  text-align: center;
}

.add-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 20px;
  background: transparent;
  border: none;
  border-radius: var(--radius-md);
  font-size: 14px;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all var(--transition-normal);
  font-family: inherit;
}

.add-btn:hover {
  color: var(--primary-color);
}

.degradation-log {
  padding-top: 16px;
  border-top: 1px solid var(--glass-border);
}

.log-title {
  display: block;
  font-size: 13px;
  font-weight: 500;
  color: var(--text-primary);
  margin-bottom: 12px;
}

.log-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 200px;
  overflow-y: auto;
}

.log-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 10px;
  background: var(--glass-bg);
  border-radius: var(--radius-sm);
}

.log-icon {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  flex-shrink: 0;
}

.log-icon.auto { background: rgba(59, 89, 152, 0.15); color: var(--primary-color); }
.log-icon.manual { background: rgba(16, 185, 129, 0.15); color: #10b981; }

.log-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.log-message {
  font-size: 12px;
  color: var(--text-primary);
}

.log-time {
  font-size: 11px;
  color: var(--text-light);
}
</style>
