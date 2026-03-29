<template>
  <div class="dashboard-page">
    <div class="overview-section">
      <div class="overview-cards">
        <div
          v-for="card in viewModel.overviewStats"
          :key="card.key"
          class="overview-card glass-card"
          :class="{ highlight: card.highlight }"
        >
          <div class="card-icon" :class="card.iconClass">
            <i :class="getOverviewIcon(card.key)"></i>
          </div>
          <div class="card-content">
            <span class="card-value" :class="{ live: card.key === 'activeInterviews' }">
              {{ getOverviewValue(card) }}
            </span>
            <span class="card-label">{{ card.label }}</span>
          </div>

          <div v-if="card.meta.tone === 'live'" class="live-indicator">
            <span class="pulse"></span>
            <span>{{ hasOverviewData ? card.meta.value : '加载中' }}</span>
          </div>

          <div v-else class="card-trend" :class="card.meta.tone">
            <i v-if="card.meta.tone === 'up'" class="fas fa-arrow-up"></i>
            <span>{{ getOverviewMetaValue(card) }}</span>
            <span class="trend-label">{{ card.meta.label }}</span>
          </div>
        </div>
      </div>
      <div v-if="panelState.overview.error" class="section-note" :class="{ stale: hasOverviewData }">
        {{ hasOverviewData ? '概览接口暂时失败，当前展示上次结果' : panelState.overview.error }}
      </div>
    </div>

    <div class="main-grid">
      <div class="left-section">
        <div v-for="chart in chartCards" :key="chart.key" class="chart-card glass-card">
          <div class="card-header">
            <h4>
              <i :class="chart.icon"></i>
              {{ chart.title }}
            </h4>
            <span class="chart-badge">最近7天</span>
          </div>

          <div v-if="panelState.trends.error" class="card-note" :class="{ stale: hasTrendsData }">
            {{ hasTrendsData ? '趋势接口暂时失败，当前展示上次结果' : panelState.trends.error }}
          </div>

          <div v-if="panelState.trends.loading && !hasTrendsData" class="panel-message">
            趋势数据加载中...
          </div>
          <div v-else-if="panelState.trends.empty" class="panel-message">
            最近7天暂无数据
          </div>
          <template v-else>
            <div class="chart-container">
              <svg viewBox="0 0 350 150" class="trend-chart">
                <defs>
                  <linearGradient :id="chart.gradientId" x1="0%" y1="0%" x2="0%" y2="100%">
                    <stop offset="0%" :stop-color="chart.gradientStart" />
                    <stop offset="100%" :stop-color="chart.gradientEnd" />
                  </linearGradient>
                </defs>
                <g class="grid-lines">
                  <line
                    v-for="i in 4"
                    :key="i"
                    x1="30"
                    :y1="20 + (i - 1) * 30"
                    x2="340"
                    :y2="20 + (i - 1) * 30"
                    :stroke="chart.gridColor"
                  />
                </g>
                <path :d="chart.areaPath" :fill="`url(#${chart.gradientId})`" />
                <path :d="chart.linePath" fill="none" :stroke="chart.stroke" stroke-width="2" />
                <circle
                  v-for="point in chart.points"
                  :key="point.key"
                  :cx="point.x"
                  :cy="point.y"
                  r="4"
                  :fill="chart.stroke"
                />
              </svg>
              <div class="chart-x-labels">
                <span v-for="label in viewModel.dateLabels" :key="label">{{ label }}</span>
              </div>
            </div>
            <div class="chart-summary">
              <div class="summary-item">
                <span class="summary-label">{{ chart.totalLabel }}</span>
                <span class="summary-value">{{ chart.summary.total }}</span>
              </div>
              <div class="summary-item">
                <span class="summary-label">{{ chart.avgLabel }}</span>
                <span class="summary-value">{{ chart.summary.avg }}</span>
              </div>
            </div>
          </template>
        </div>
      </div>

      <div class="right-section">
        <div class="api-status-card glass-card">
          <div class="card-header">
            <h4>
              <i class="fas fa-plug"></i>
              模型API状态
            </h4>
            <span class="status-badge" :class="viewModel.modelPanel.badge.tone">
              {{ viewModel.modelPanel.badge.text }}
            </span>
          </div>

          <div v-if="panelState.models.error" class="card-note" :class="{ stale: hasModelsData }">
            {{ hasModelsData ? '模型状态接口暂时失败，当前展示上次结果' : panelState.models.error }}
          </div>

          <div v-if="panelState.models.loading && !hasModelsData" class="panel-message">
            模型状态加载中...
          </div>
          <div v-else-if="viewModel.modelPanel.items.length === 0" class="panel-message">
            最近15分钟暂无调用
          </div>
          <div v-else class="api-list">
            <div v-for="api in viewModel.modelPanel.items" :key="api.key" class="api-item">
              <div class="api-info">
                <div class="api-icon" :style="{ background: getProviderVisual(api.providerLabel).color }">
                  <i :class="getProviderVisual(api.providerLabel).icon"></i>
                </div>
                <div class="api-details">
                  <span class="api-name">{{ api.providerLabel }}</span>
                  <span class="api-model">{{ api.modelName }}</span>
                </div>
              </div>
              <div class="api-metrics">
                <div class="metric">
                  <span class="metric-label">延迟</span>
                  <span class="metric-value" :class="getLatencyClass(api.latencyText)">{{ api.latencyText }}</span>
                </div>
                <div class="metric">
                  <span class="metric-label">错误率</span>
                  <span class="metric-value" :class="getErrorClass(api.errorRateText)">{{ api.errorRateText }}</span>
                </div>
                <div class="metric subtle">
                  <span class="metric-label">请求量</span>
                  <span class="metric-value">{{ api.requestCountText }}</span>
                </div>
                <div class="status-dot" :class="api.status"></div>
              </div>
            </div>
          </div>
        </div>

        <div class="queue-status-card glass-card">
          <div class="card-header">
            <h4>
              <i class="fas fa-server"></i>
              系统摘要
            </h4>
          </div>

          <div class="system-status-line" :class="viewModel.systemSummaryPanel.statusTone">
            {{ viewModel.systemSummaryPanel.statusLine }}
          </div>

          <div v-if="systemSummaryNotice" class="card-note" :class="{ stale: true }">
            {{ systemSummaryNotice }}
          </div>

          <div class="summary-grid">
            <div
              v-for="item in viewModel.systemSummaryPanel.items"
              :key="item.label"
              class="summary-grid-item"
            >
              <span class="summary-grid-label">{{ item.label }}</span>
              <span class="summary-grid-value">{{ item.value }}</span>
            </div>
          </div>
        </div>

        <div class="prompt-updates-card glass-card">
          <div class="card-header">
            <h4>
              <i class="fas fa-history"></i>
              Prompt摘要
            </h4>
          </div>

          <div v-if="panelState.prompts.error" class="card-note" :class="{ stale: hasPromptsData }">
            {{ hasPromptsData ? 'Prompt 摘要接口暂时失败，当前展示上次结果' : panelState.prompts.error }}
          </div>

          <div v-if="panelState.prompts.loading && !hasPromptsData" class="panel-message">
            Prompt 摘要加载中...
          </div>
          <div v-else-if="viewModel.promptSummaryPanel.items.length === 0" class="panel-message">
            当前没有可展示的 Prompt 摘要
          </div>
          <div v-else class="updates-list">
            <div
              v-for="item in viewModel.promptSummaryPanel.items"
              :key="item.key"
              class="update-item"
              :class="{ warning: item.status === 'warning' }"
            >
              <div class="update-icon" :class="item.status">
                <i :class="item.status === 'warning' ? 'fas fa-exclamation-triangle' : 'fas fa-file-alt'"></i>
              </div>
              <div class="update-content">
                <span class="update-title">{{ item.title }}</span>
                <span class="update-desc">{{ item.subtitle }}</span>
                <span class="update-subdesc">{{ item.usageText }}</span>
              </div>
              <div class="update-meta">
                <span class="update-version">v{{ item.versionText }}</span>
                <span class="update-time">{{ item.timeText }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import {
  getAdminDashboardModels,
  getAdminDashboardOverview,
  getAdminDashboardPrompts,
  getAdminDashboardSystemPing,
  getAdminDashboardTrends
} from '@/api/adminDashboard.js'
import { buildAdminDashboardViewModel } from '@/utils/adminDashboardViewModel.js'

function createPanelState(empty = false) {
  return {
    loading: false,
    error: '',
    empty
  }
}

function generatePoints(series) {
  if (!Array.isArray(series) || series.length === 0) {
    return []
  }

  const maxVal = Math.max(...series, 1)
  const startX = 50
  const endX = 340
  const step = series.length === 1 ? 0 : (endX - startX) / (series.length - 1)

  return series.map((value, index) => ({
    key: `${index}-${value}`,
    x: startX + index * step,
    y: 130 - (Math.max(0, Number(value) || 0) / maxVal) * 100
  }))
}

function generateLinePath(points) {
  if (!points.length) {
    return ''
  }

  return points.map((point, index) => `${index === 0 ? 'M' : 'L'} ${point.x} ${point.y}`).join(' ')
}

function generateAreaPath(points) {
  if (!points.length) {
    return ''
  }

  const linePath = generateLinePath(points)
  return `${linePath} L ${points[points.length - 1].x} 130 L ${points[0].x} 130 Z`
}

function isUnauthorized(error) {
  return error?.status === 401 || error?.status === 403
}

function getErrorMessage(error) {
  return error?.message || '加载失败'
}

function hasTrendData(data) {
  return Array.isArray(data?.dates) && data.dates.length > 0
}

function hasSystemPingData(data) {
  return Boolean(data && typeof data.serverTime === 'string' && data.serverTime.trim())
}

function getProviderVisual(providerLabel) {
  const normalized = String(providerLabel || '').toLowerCase()

  if (normalized === 'openai') {
    return { icon: 'fas fa-robot', color: '#10a37f' }
  }

  if (normalized === 'mock') {
    return { icon: 'fas fa-vial', color: '#64748b' }
  }

  return { icon: 'fas fa-microchip', color: '#3b5998' }
}

export default {
  name: 'AdminDashboard',
  props: {
    refreshNonce: {
      type: Number,
      default: 0
    },
    currentAdminName: {
      type: String,
      default: '管理员'
    }
  },
  emits: ['refresh-meta', 'auth-expired'],
  setup(props, { emit }) {
    const overview = ref(null)
    const trends = ref(null)
    const models = ref([])
    const prompts = ref([])
    const systemPing = ref(null)
    const lastRefreshedAt = ref(null)
    const activeRequestId = ref(0)
    let realtimeTimerId = null
    let visibilityChangeHandler = null

    const panelState = reactive({
      overview: createPanelState(),
      trends: createPanelState(true),
      models: createPanelState(true),
      prompts: createPanelState(true),
      system: createPanelState(true)
    })

    const hasOverviewData = computed(() => overview.value !== null)
    const hasTrendsData = computed(() => hasTrendData(trends.value))
    const hasModelsData = computed(() => Array.isArray(models.value) && models.value.length > 0)
    const hasPromptsData = computed(() => Array.isArray(prompts.value) && prompts.value.length > 0)
    const hasSystemStatusData = computed(() => hasSystemPingData(systemPing.value))

    const anyLoading = computed(() =>
      Object.values(panelState).some((state) => state.loading)
    )

    const viewModel = computed(() =>
      buildAdminDashboardViewModel({
        overview: overview.value || {},
        trends: trends.value || {},
        models: models.value || [],
        prompts: prompts.value || [],
        systemPing: systemPing.value,
        systemPingState: panelState.system,
        currentAdminName: props.currentAdminName,
        lastRefreshedAt: lastRefreshedAt.value
      })
    )

    const chartCards = computed(() => {
      const cards = [
        {
          key: 'users',
          title: '新增用户趋势',
          icon: 'fas fa-user-plus',
          gradientId: 'userGradient',
          gradientStart: 'rgba(59, 89, 152, 0.4)',
          gradientEnd: 'rgba(59, 89, 152, 0.05)',
          gridColor: 'rgba(59, 89, 152, 0.1)',
          stroke: 'var(--primary-color)',
          totalLabel: '本周新增',
          avgLabel: '日均增长',
          summary: viewModel.value.weeklyUserStats,
          series: viewModel.value.userSeries
        },
        {
          key: 'interviews',
          title: '面试场次趋势',
          icon: 'fas fa-calendar-check',
          gradientId: 'interviewGradient',
          gradientStart: 'rgba(16, 185, 129, 0.4)',
          gradientEnd: 'rgba(16, 185, 129, 0.05)',
          gridColor: 'rgba(16, 185, 129, 0.1)',
          stroke: '#10b981',
          totalLabel: '本周场次',
          avgLabel: '日均场次',
          summary: viewModel.value.weeklyInterviewStats,
          series: viewModel.value.interviewSeries
        },
        {
          key: 'tokens',
          title: 'Token消耗趋势',
          icon: 'fas fa-coins',
          gradientId: 'tokenGradient',
          gradientStart: 'rgba(245, 158, 11, 0.4)',
          gradientEnd: 'rgba(245, 158, 11, 0.05)',
          gridColor: 'rgba(245, 158, 11, 0.1)',
          stroke: '#f59e0b',
          totalLabel: '本周消耗',
          avgLabel: '日均消耗',
          summary: viewModel.value.weeklyTokenStats,
          series: viewModel.value.tokenSeries
        }
      ]

      return cards.map((card) => {
        const points = generatePoints(card.series)
        return {
          ...card,
          points,
          linePath: generateLinePath(points),
          areaPath: generateAreaPath(points)
        }
      })
    })

    const systemSummaryNotice = computed(() => {
      const notices = []

      if (panelState.models.error) {
        notices.push(hasModelsData.value ? '模型数使用上次结果' : '模型数暂不可用')
      }

      if (panelState.prompts.error) {
        notices.push(hasPromptsData.value ? 'Prompt 数使用上次结果' : 'Prompt 数暂不可用')
      }

      return notices.join('，')
    })

    function emitRefreshMeta() {
      emit('refresh-meta', {
        lastRefreshedAt: lastRefreshedAt.value,
        loading: anyLoading.value
      })
    }

    function updatePanelState(key, { loading, error, empty }) {
      panelState[key].loading = loading
      panelState[key].error = error
      panelState[key].empty = empty
    }

    function isPageVisible() {
      if (typeof document === 'undefined') {
        return true
      }

      return document.visibilityState === 'visible'
    }

    function buildTaskDefinitions() {
      return [
        { key: 'overview', authProtected: true, fetcher: () => getAdminDashboardOverview() },
        { key: 'trends', authProtected: true, fetcher: () => getAdminDashboardTrends(7) },
        { key: 'models', authProtected: true, fetcher: () => getAdminDashboardModels(15) },
        { key: 'prompts', authProtected: true, fetcher: () => getAdminDashboardPrompts() },
        { key: 'system', authProtected: false, fetcher: () => getAdminDashboardSystemPing() }
      ]
    }

    function applyTaskSuccess(taskKey, value) {
      if (taskKey === 'overview') {
        overview.value = value
        updatePanelState('overview', {
          loading: false,
          error: '',
          empty: false
        })
        return
      }

      if (taskKey === 'trends') {
        trends.value = value
        updatePanelState('trends', {
          loading: false,
          error: '',
          empty: !hasTrendData(value)
        })
        return
      }

      if (taskKey === 'models') {
        models.value = value
        updatePanelState('models', {
          loading: false,
          error: '',
          empty: !Array.isArray(value) || value.length === 0
        })
        return
      }

      if (taskKey === 'prompts') {
        prompts.value = value
        updatePanelState('prompts', {
          loading: false,
          error: '',
          empty: !Array.isArray(value) || value.length === 0
        })
        return
      }

      if (taskKey === 'system') {
        systemPing.value = value
        updatePanelState('system', {
          loading: false,
          error: '',
          empty: !hasSystemPingData(value)
        })
      }
    }

    function applyTaskError(taskKey, error) {
      if (taskKey === 'overview') {
        updatePanelState('overview', {
          loading: false,
          error: getErrorMessage(error),
          empty: !hasOverviewData.value
        })
        return
      }

      if (taskKey === 'trends') {
        updatePanelState('trends', {
          loading: false,
          error: getErrorMessage(error),
          empty: !hasTrendsData.value
        })
        return
      }

      if (taskKey === 'models') {
        updatePanelState('models', {
          loading: false,
          error: getErrorMessage(error),
          empty: !hasModelsData.value
        })
        return
      }

      if (taskKey === 'prompts') {
        updatePanelState('prompts', {
          loading: false,
          error: getErrorMessage(error),
          empty: !hasPromptsData.value
        })
        return
      }

      if (taskKey === 'system') {
        updatePanelState('system', {
          loading: false,
          error: getErrorMessage(error),
          empty: !hasSystemStatusData.value
        })
      }
    }

    async function runTaskGroup(taskKeys) {
      if (anyLoading.value) {
        return
      }

      const taskDefinitions = buildTaskDefinitions().filter((task) => taskKeys.includes(task.key))
      const requestId = activeRequestId.value + 1
      activeRequestId.value = requestId

      taskKeys.forEach((taskKey) => {
        updatePanelState(taskKey, {
          loading: true,
          error: '',
          empty: panelState[taskKey].empty
        })
      })
      emitRefreshMeta()

      const results = await Promise.allSettled(taskDefinitions.map((task) => task.fetcher()))

      if (requestId !== activeRequestId.value) {
        return
      }

      let shouldLogout = false
      let hasSuccessfulUpdate = false

      results.forEach((result, index) => {
        const task = taskDefinitions[index]

        if (result.status === 'fulfilled') {
          hasSuccessfulUpdate = true
          applyTaskSuccess(task.key, result.value)
          return
        }

        const error = result.reason
        shouldLogout = shouldLogout || (task.authProtected && isUnauthorized(error))
        applyTaskError(task.key, error)
      })

      if (hasSuccessfulUpdate) {
        lastRefreshedAt.value = new Date().toISOString()
      }

      emitRefreshMeta()

      if (shouldLogout) {
        emit('auth-expired')
      }
    }

    async function loadAllPanels() {
      await runTaskGroup(['overview', 'trends', 'models', 'prompts', 'system'])
    }

    async function loadRealtimePanels() {
      if (!isPageVisible()) {
        return
      }

      await runTaskGroup(['overview', 'models', 'system'])
    }

    function startRealtimePolling() {
      if (typeof window !== 'undefined') {
        realtimeTimerId = window.setInterval(() => {
          if (!isPageVisible()) {
            return
          }

          loadRealtimePanels()
        }, 30000)
      }

      if (typeof document !== 'undefined') {
        visibilityChangeHandler = () => {
          if (document.visibilityState === 'visible') {
            loadRealtimePanels()
          }
        }

        document.addEventListener('visibilitychange', visibilityChangeHandler)
      }
    }

    function stopRealtimePolling() {
      if (realtimeTimerId != null && typeof window !== 'undefined') {
        window.clearInterval(realtimeTimerId)
        realtimeTimerId = null
      }

      if (visibilityChangeHandler && typeof document !== 'undefined') {
        document.removeEventListener('visibilitychange', visibilityChangeHandler)
        visibilityChangeHandler = null
      }
    }

    function getOverviewIcon(key) {
      if (key === 'totalUsers') return 'fas fa-users'
      if (key === 'totalInterviews') return 'fas fa-comments'
      if (key === 'activeInterviews') return 'fas fa-video'
      return 'fas fa-coins'
    }

    function getOverviewValue(card) {
      if (!hasOverviewData.value && panelState.overview.loading) {
        return '--'
      }

      if (!hasOverviewData.value && panelState.overview.error) {
        return '--'
      }

      return card.value
    }

    function getOverviewMetaValue(card) {
      if (!hasOverviewData.value && panelState.overview.loading) {
        return '加载中'
      }

      if (!hasOverviewData.value && panelState.overview.error) {
        return '--'
      }

      return card.meta.value
    }

    function getLatencyClass(latencyText) {
      const latency = Number.parseInt(String(latencyText).replace('ms', ''), 10)
      if (latency < 200) return 'fast'
      if (latency < 400) return 'normal'
      if (latency < 1500) return 'good'
      return 'slow'
    }

    function getErrorClass(errorRateText) {
      const rate = Number.parseFloat(String(errorRateText).replace('%', ''))
      if (rate < 1) return 'good'
      if (rate < 5) return 'warning'
      return 'danger'
    }

    watch(() => props.refreshNonce, () => {
      loadAllPanels()
    })

    watch(anyLoading, () => {
      emitRefreshMeta()
    })

    onMounted(() => {
      loadAllPanels()
      startRealtimePolling()
    })

    onBeforeUnmount(() => {
      stopRealtimePolling()
    })

    return {
      chartCards,
      getErrorClass,
      getLatencyClass,
      getOverviewIcon,
      getOverviewValue,
      getOverviewMetaValue,
      getProviderVisual,
      hasModelsData,
      hasOverviewData,
      hasPromptsData,
      hasSystemStatusData,
      hasTrendsData,
      panelState,
      systemSummaryNotice,
      viewModel
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

.card-trend.neutral {
  background: rgba(245, 158, 11, 0.12);
  color: #d97706;
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

.section-note,
.card-note {
  font-size: 12px;
  color: #b45309;
  background: rgba(245, 158, 11, 0.12);
  border: 1px solid rgba(245, 158, 11, 0.18);
  border-radius: var(--radius-sm);
  padding: 8px 10px;
}

.section-note {
  margin-top: 12px;
}

.card-note {
  margin-bottom: 14px;
}

.section-note.stale,
.card-note.stale {
  color: #92400e;
}

.panel-message {
  min-height: 140px;
  display: flex;
  align-items: center;
  justify-content: center;
  text-align: center;
  color: var(--text-secondary);
  font-size: 13px;
  border: 1px dashed var(--glass-border);
  border-radius: var(--radius-md);
  background: rgba(59, 89, 152, 0.04);
}

.main-grid {
  display: grid;
  grid-template-columns: 1fr 380px;
  gap: 24px;
}

.left-section,
.right-section {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.chart-card,
.api-status-card,
.queue-status-card,
.prompt-updates-card {
  padding: 20px;
}

.prompt-updates-card {
  flex: 1;
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

.chart-badge,
.status-badge {
  padding: 4px 10px;
  border-radius: var(--radius-sm);
  font-size: 11px;
  font-weight: 500;
}

.chart-badge {
  background: rgba(59, 89, 152, 0.1);
  color: var(--primary-color);
}

.status-badge.healthy {
  background: rgba(16, 185, 129, 0.15);
  color: #10b981;
}

.status-badge.warning {
  background: rgba(245, 158, 11, 0.15);
  color: #f59e0b;
}

.status-badge.error {
  background: rgba(239, 68, 68, 0.15);
  color: #ef4444;
}

.status-badge.neutral {
  background: rgba(100, 116, 139, 0.15);
  color: #64748b;
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

.api-list,
.updates-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.api-item,
.update-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 12px;
  background: var(--glass-bg);
  border-radius: var(--radius-md);
}

.api-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.api-icon,
.update-icon {
  width: 36px;
  height: 36px;
  border-radius: var(--radius-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  flex-shrink: 0;
}

.api-details,
.update-content {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.api-name,
.update-title {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-primary);
}

.api-model,
.update-desc,
.update-subdesc {
  font-size: 11px;
  color: var(--text-secondary);
}

.api-metrics {
  display: flex;
  align-items: center;
  gap: 14px;
}

.metric {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
}

.metric.subtle .metric-value {
  font-size: 12px;
  color: var(--text-secondary);
}

.metric-label {
  font-size: 10px;
  color: var(--text-light);
}

.metric-value {
  font-size: 13px;
  font-weight: 600;
}

.metric-value.fast,
.metric-value.good {
  color: #10b981;
}

.metric-value.normal {
  color: var(--text-primary);
}

.metric-value.slow,
.metric-value.warning {
  color: #f59e0b;
}

.metric-value.danger {
  color: #ef4444;
}

.status-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
}

.status-dot.healthy { background: #10b981; box-shadow: 0 0 8px rgba(16, 185, 129, 0.5); }
.status-dot.warning { background: #f59e0b; box-shadow: 0 0 8px rgba(245, 158, 11, 0.5); }
.status-dot.error { background: #ef4444; box-shadow: 0 0 8px rgba(239, 68, 68, 0.5); }

.summary-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}

.system-status-line {
  margin-bottom: 14px;
  padding: 10px 12px;
  border-radius: var(--radius-sm);
  font-size: 12px;
  line-height: 1.5;
  border: 1px solid transparent;
}

.system-status-line.healthy {
  color: #047857;
  background: rgba(16, 185, 129, 0.12);
  border-color: rgba(16, 185, 129, 0.18);
}

.system-status-line.warning {
  color: #b45309;
  background: rgba(245, 158, 11, 0.12);
  border-color: rgba(245, 158, 11, 0.18);
}

.system-status-line.neutral {
  color: #475569;
  background: rgba(100, 116, 139, 0.1);
  border-color: rgba(100, 116, 139, 0.16);
}

.summary-grid-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 14px;
  background: var(--glass-bg);
  border-radius: var(--radius-md);
}

.summary-grid-label {
  font-size: 12px;
  color: var(--text-secondary);
}

.summary-grid-value {
  font-size: 18px;
  font-weight: 700;
  color: var(--text-primary);
}

.update-item {
  align-items: flex-start;
}

.update-item.warning {
  border: 1px solid rgba(245, 158, 11, 0.25);
  background: rgba(245, 158, 11, 0.08);
}

.update-icon.healthy {
  background: rgba(16, 185, 129, 0.15);
  color: #10b981;
}

.update-icon.warning {
  background: rgba(245, 158, 11, 0.18);
  color: #f59e0b;
}

.update-subdesc {
  margin-top: 4px;
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

@media (max-width: 1280px) {
  .overview-cards {
    grid-template-columns: repeat(2, 1fr);
  }

  .main-grid {
    grid-template-columns: 1fr;
  }

  .right-section {
    order: -1;
  }
}

@media (max-width: 768px) {
  .overview-cards,
  .summary-grid {
    grid-template-columns: 1fr;
  }

  .overview-card,
  .api-item,
  .update-item {
    flex-direction: column;
    align-items: flex-start;
  }

  .api-metrics {
    width: 100%;
    justify-content: space-between;
  }

  .chart-summary {
    flex-direction: column;
    gap: 12px;
  }
}
</style>
