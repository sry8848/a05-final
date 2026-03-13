<template>
  <section id="page-growth" class="page-section active">
    <header class="welcome-header">
      <div class="welcome-text">
        <h2>欢迎回来，{{ displayName }}</h2>
        <p>准备好开始你的AI模拟面试之旅了吗？</p>
        <p v-if="loadError" class="header-error">{{ loadError }}</p>
      </div>
      <div class="quick-actions">
        <button class="btn btn-primary glass-btn" @click="goToInterview()">
          <i class="fas fa-play"></i>
          开始面试
        </button>
      </div>
    </header>

    <div class="stats-grid">
      <div class="stat-card glass-card">
        <div class="stat-icon blue">
          <i class="fas fa-calendar-check"></i>
        </div>
        <div class="stat-content">
          <span class="stat-number">{{ stats.totalInterviews }}</span>
          <span class="stat-label">总面试次数</span>
        </div>
      </div>
      <div class="stat-card glass-card">
        <div class="stat-icon green">
          <i class="fas fa-star"></i>
        </div>
        <div class="stat-content">
          <span class="stat-number">{{ stats.avgScore }}</span>
          <span class="stat-label">平均得分</span>
        </div>
      </div>
      <div class="stat-card glass-card">
        <div class="stat-icon purple">
          <i class="fas fa-clock"></i>
        </div>
        <div class="stat-content">
          <span class="stat-number">{{ stats.totalHours }}</span>
          <span class="stat-label">学习时长(h)</span>
        </div>
      </div>
      <div class="stat-card glass-card">
        <div class="stat-icon orange">
          <i class="fas fa-coins"></i>
        </div>
        <div class="stat-content">
          <span class="stat-number">{{ stats.points }}</span>
          <span class="stat-label">我的积分</span>
        </div>
      </div>
    </div>

    <div class="chart-block glass-card">
      <div class="chart-block-left">
        <h3 class="block-title"><i class="fas fa-chart-radar"></i> 面试能力雷达图</h3>
        <div class="radar-wrap">
          <svg viewBox="0 0 380 380" class="radar-svg">
            <defs>
              <linearGradient id="radarGrad" x1="0%" y1="0%" x2="100%" y2="100%">
                <stop offset="0%" stop-color="rgba(59, 89, 152, 0.6)" />
                <stop offset="100%" stop-color="rgba(102, 126, 234, 0.4)" />
              </linearGradient>
            </defs>
            <polygon
              v-for="(level, i) in radarLevels"
              :key="'grid-' + i"
              :points="getGridPoints(level)"
              class="radar-grid"
            />
            <polygon :points="radarPointsStr" class="radar-area" fill="url(#radarGrad)" />
            <circle
              v-for="(val, i) in radarData"
              :key="'pt-' + i"
              :cx="getPointPosition(i, val).x"
              :cy="getPointPosition(i, val).y"
              r="8"
              class="radar-point"
            />
          </svg>
          <div class="radar-labels">
            <span
              v-for="(label, i) in radarLabels"
              :key="'l-' + i"
              class="radar-label"
              :style="getLabelStyle(i)"
            >
              {{ label }} {{ radarData[i] }}
            </span>
          </div>
        </div>
      </div>
      <div class="chart-block-right">
        <h3 class="block-title"><i class="fas fa-chart-line"></i> 成长趋势</h3>
        <div class="trend-dimension-select">
          <CustomSelect v-model="selectedTrendDimension" :options="trendDimensionOptions" />
        </div>
        <div class="trend-chart-wrap">
          <svg viewBox="0 0 400 180" preserveAspectRatio="xMidYMid meet" class="trend-svg">
            <defs>
              <linearGradient id="trendGrad" x1="0%" y1="0%" x2="0%" y2="100%">
                <stop offset="0%" stop-color="rgba(16, 185, 129, 0.4)" />
                <stop offset="100%" stop-color="rgba(16, 185, 129, 0.02)" />
              </linearGradient>
            </defs>
            <path :d="trendAreaPath" fill="url(#trendGrad)" class="trend-area" />
            <path :d="trendLinePath" fill="none" stroke="#10b981" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" class="trend-line" />
            <circle
              v-for="(p, i) in trendChartPoints"
              :key="'tc-' + i"
              :cx="p.x"
              :cy="p.y"
              r="5"
              fill="#10b981"
              stroke="white"
              stroke-width="2"
            />
          </svg>
          <div class="trend-x-labels">
            <span v-for="(lb, i) in trendXLabels" :key="'xl-' + i">{{ lb }}</span>
          </div>
        </div>
      </div>
    </div>

    <div class="section-container">
      <div class="section-header-row">
        <h3 class="section-title">
          <i class="fas fa-list-ol"></i> 知识域能力
        </h3>
        <div class="position-select-wrap">
          <span class="position-select-label">选择岗位</span>
          <CustomSelect v-model="selectedPosition" :options="positionOptions" />
        </div>
      </div>
      <div class="rank-container">
        <div class="rank-card red glass-card">
          <div class="rank-header">
            <i class="fas fa-fire"></i>
            <h4>技术强项 TOP 3</h4>
          </div>
          <div class="rank-list">
            <div v-for="(item, idx) in topStrengths" :key="'s-' + idx" class="rank-item">
              <span class="rank-position">{{ idx + 1 }}</span>
              <span class="rank-name">{{ item.name }}</span>
              <div class="rank-bar">
                <div class="rank-fill" :style="{ width: item.score + '%' }"></div>
              </div>
              <span class="rank-score">{{ item.score }}</span>
              <button type="button" class="rank-action-btn" @click="goToInterview(item.code, selectedPosition)">
                去练习
              </button>
            </div>
          </div>
        </div>
        <div class="rank-card black glass-card">
          <div class="rank-header">
            <i class="fas fa-exclamation-triangle"></i>
            <h4>待提升项 TOP 3</h4>
          </div>
          <div class="rank-list">
            <div v-for="(item, idx) in topWeaknesses" :key="'w-' + idx" class="rank-item weak">
              <span class="rank-position">{{ idx + 1 }}</span>
              <span class="rank-name">{{ item.name }}</span>
              <div class="rank-bar">
                <div class="rank-fill weak" :style="{ width: item.score + '%' }"></div>
              </div>
              <span class="rank-score">{{ item.score }}</span>
              <button type="button" class="rank-action-btn" @click="goToInterview(item.code, selectedPosition)">
                去练习
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>

<script>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { getProfile, getProfileSkillOverview, getProfileStatistics } from '../api/resume'
import CustomSelect from './CustomSelect.vue'

export default {
  name: 'GrowthCenterPage',
  components: { CustomSelect },
  props: {
    user: {
      type: Object,
      default: () => ({
        name: '面试者',
        totalInterviews: 12,
        avgScore: 85,
        totalHours: 36,
        points: 0
      })
    }
  },
  emits: ['navigate', 'goToInterview'],
  setup(props, { emit }) {
    const loading = ref(false)
    const loadError = ref('')
    const profileNickname = ref(props.user.name || '面试者')
    const displayName = computed(() => profileNickname.value || props.user.name || '面试者')

    const stats = reactive({
      totalInterviews: props.user.totalInterviews ?? 0,
      avgScore: props.user.avgScore ?? '--',
      totalHours: props.user.totalHours ?? 0,
      points: props.user.points ?? 0
    })

    const skillDomains = ref([])
    const trendPoints = ref([])

    const radarDomainSlots = computed(() => {
      const sorted = [...skillDomains.value].sort((a, b) => Number(b.averageScore || 0) - Number(a.averageScore || 0))
      const top = sorted.slice(0, 5)
      while (top.length < 5) {
        top.push({
          domainCode: `none-${top.length}`,
          domainName: '暂无数据',
          averageScore: 0
        })
      }
      return top
    })
    const radarLabels = computed(() => radarDomainSlots.value.map((item) => item.domainName || '暂无数据'))
    const radarData = computed(() => radarDomainSlots.value.map((item) => Math.max(0, Math.min(100, Number(item.averageScore || 0)))))
    const radarLevels = [20, 40, 60, 80, 100]

    const selectedTrendDimension = ref('all')
    const trendDimensionOptions = [
      { value: 'all', label: '综合评分' }
    ]
    const trendSeries = computed(() => trendPoints.value.map((item) => Number(item.score || 0)))
    const trendXLabels = computed(() => trendPoints.value.map((item) => String(item.date || '').slice(5)))

    const trendChartPoints = computed(() => {
      const data = trendSeries.value
      if (!data.length) return []
      const w = 400
      const h = 160
      const padding = 20
      const max = Math.max(...data)
      const min = Math.min(...data)
      const range = max - min || 1
      const step = data.length > 1 ? (w - 2 * padding) / (data.length - 1) : 0
      return data.map((v, i) => ({
        x: data.length > 1 ? padding + i * step : w / 2,
        y: padding + (h - 2 * padding) * (1 - (v - min) / range),
        value: v
      }))
    })

    const trendLinePath = computed(() => {
      const pts = trendChartPoints.value
      if (!pts.length) return ''
      return pts.map((p, i) => (i === 0 ? `M ${p.x} ${p.y}` : `L ${p.x} ${p.y}`)).join(' ')
    })

    const trendAreaPath = computed(() => {
      const pts = trendChartPoints.value
      if (!pts.length) return ''
      const w = 400
      const h = 160
      const padding = 20
      const line = trendLinePath.value
      return `${line} L ${pts[pts.length - 1].x} ${h - padding} L ${pts[0].x} ${h - padding} Z`
    })

    const radarCenter = 190
    const radarRadius = 140
    const radarInner = 25

    function getPointPosition(index, value) {
      const angle = (index * 72 - 90) * (Math.PI / 180)
      const r = radarInner + (value / 100) * (radarRadius - radarInner)
      return {
        x: radarCenter + r * Math.cos(angle),
        y: radarCenter + r * Math.sin(angle)
      }
    }

    function getGridPoints(level) {
      const r = radarInner + (level / 100) * (radarRadius - radarInner)
      const points = []
      for (let i = 0; i < 5; i++) {
        const angle = (i * 72 - 90) * (Math.PI / 180)
        points.push(`${radarCenter + r * Math.cos(angle)},${radarCenter + r * Math.sin(angle)}`)
      }
      return points.join(' ')
    }

    const radarPointsStr = computed(() =>
      radarData.value.map((v, i) => getPointPosition(i, v)).map(p => `${p.x},${p.y}`).join(' ')
    )

    function getLabelStyle(index) {
      const angle = (index * 72 - 90) * (Math.PI / 180)
      const r = 170
      const xPct = ((radarCenter + r * Math.cos(angle)) / 380) * 100
      const yPct = ((radarCenter + r * Math.sin(angle)) / 380) * 100
      return { left: xPct + '%', top: yPct + '%' }
    }

    const positionOptions = [
      { value: 'frontend', label: '前端开发' },
      { value: 'backend', label: '后端开发' },
      { value: 'fullstack', label: '全栈开发' }
    ]
    const selectedPosition = ref('frontend')
    const positionCodeMap = {
      frontend: 'FRONTEND',
      backend: 'JAVA_BACKEND',
      fullstack: 'FRONTEND'
    }

    const topStrengths = computed(() =>
      [...skillDomains.value]
        .filter((item) => item.averageScore != null)
        .sort((a, b) => Number(b.averageScore) - Number(a.averageScore))
        .slice(0, 3)
        .map((item) => ({
          name: item.domainName || item.domainCode,
          score: Math.round(Number(item.averageScore)),
          code: item.domainCode
        }))
    )
    const topWeaknesses = computed(() =>
      [...skillDomains.value]
        .filter((item) => item.averageScore != null)
        .sort((a, b) => Number(a.averageScore) - Number(b.averageScore))
        .slice(0, 3)
        .map((item) => ({
          name: item.domainName || item.domainCode,
          score: Math.round(Number(item.averageScore)),
          code: item.domainCode
        }))
    )

    const loadGrowthData = async () => {
      loading.value = true
      loadError.value = ''
      const positionCode = positionCodeMap[selectedPosition.value]
      try {
        const [profile, statistics, skillOverview] = await Promise.all([
          getProfile(),
          getProfileStatistics(positionCode),
          getProfileSkillOverview(positionCode)
        ])

        profileNickname.value = profile?.nickname || props.user.name || '面试者'
        stats.totalInterviews = Number(statistics?.totalSessions) || 0
        stats.totalHours = Math.round(((Number(statistics?.totalMinutes) || 0) / 60) * 10) / 10
        stats.avgScore = statistics?.averageScore == null ? '--' : Math.round(Number(statistics.averageScore))
        trendPoints.value = Array.isArray(statistics?.scoreTrend) ? statistics.scoreTrend : []
        skillDomains.value = Array.isArray(skillOverview?.domains) ? skillOverview.domains : []
      } catch (error) {
        console.error('[GrowthCenterPage] 加载成长中心数据失败', error)
        loadError.value = error?.message || '成长数据加载失败'
      } finally {
        loading.value = false
      }
    }

    function goToInterview(autoFocus, autoPosition) {
      emit('goToInterview', {
        auto_focus: autoFocus || undefined,
        auto_position: autoPosition || selectedPosition.value
      })
    }

    watch(selectedPosition, () => {
      loadGrowthData()
    })

    onMounted(() => {
      loadGrowthData()
    })

    return {
      loading,
      loadError,
      displayName,
      stats,
      radarLabels,
      radarData,
      radarLevels,
      getGridPoints,
      getPointPosition,
      getLabelStyle,
      radarPointsStr,
      selectedTrendDimension,
      trendDimensionOptions,
      trendChartPoints,
      trendLinePath,
      trendAreaPath,
      trendXLabels,
      selectedPosition,
      positionOptions,
      topStrengths,
      topWeaknesses,
      goToInterview
    }
  }
}
</script>

<style scoped>
.chart-block {
  display: flex;
  gap: 32px;
  padding: 24px;
  margin-bottom: 32px;
  min-height: 380px;
}

.chart-block-left {
  flex: 0 0 420px;
}

.chart-block-right {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.block-title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0 0 16px;
}

.block-title i {
  color: var(--primary-color);
}

.header-error {
  margin-top: 8px;
  font-size: 13px;
  color: #f87171;
}

.radar-wrap {
  position: relative;
  width: 380px;
  height: 380px;
}

.radar-svg {
  width: 100%;
  height: 100%;
}

.radar-grid {
  fill: none;
  stroke: rgba(59, 89, 152, 0.15);
  stroke-width: 1.5;
}

.radar-area {
  stroke: var(--primary-color);
  stroke-width: 2;
  transition: all 0.4s ease;
}

.radar-point {
  fill: var(--primary-color);
  stroke: white;
  stroke-width: 2;
}

.radar-labels {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.radar-label {
  position: absolute;
  font-size: 12px;
  font-weight: 600;
  transform: translate(-50%, -50%);
  padding: 4px 10px;
  background: rgba(255, 255, 255, 0.9);
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  color: var(--text-primary);
}

.trend-dimension-select {
  margin-bottom: 16px;
  max-width: 200px;
}

.trend-chart-wrap {
  flex: 1;
  min-height: 200px;
}

.trend-svg {
  width: 100%;
  height: 180px;
  display: block;
}

.trend-area {
  transition: all 0.3s ease;
}

.trend-line {
  transition: all 0.3s ease;
}

.trend-x-labels {
  display: flex;
  justify-content: space-between;
  padding: 8px 0 0;
  font-size: 12px;
  color: var(--text-secondary);
}

.section-header-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  flex-wrap: wrap;
  gap: 12px;
}

.position-select-wrap {
  display: flex;
  align-items: center;
  gap: 10px;
}

.position-select-label {
  font-size: 14px;
  color: var(--text-secondary);
}

.rank-container {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 24px;
}

.rank-card {
  padding: 24px;
}

.rank-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 20px;
}

.rank-header i {
  font-size: 18px;
}

.rank-card.red .rank-header i {
  color: #ef4444;
}

.rank-card.black .rank-header i {
  color: #f59e0b;
}

.rank-header h4 {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
}

.rank-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.rank-item {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.rank-position {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
  background: linear-gradient(135deg, var(--primary-color), var(--primary-light));
  color: white;
  flex-shrink: 0;
}

.rank-item.weak .rank-position {
  background: linear-gradient(135deg, #f59e0b, #d97706);
}

.rank-name {
  flex: 1;
  min-width: 80px;
  font-size: 14px;
  color: var(--text-primary);
  font-weight: 500;
}

.rank-bar {
  width: 80px;
  height: 8px;
  background: rgba(59, 89, 152, 0.1);
  border-radius: 4px;
  overflow: hidden;
  flex-shrink: 0;
}

.rank-fill {
  height: 100%;
  background: linear-gradient(90deg, var(--primary-color), var(--primary-light));
  border-radius: 4px;
  transition: width 0.5s ease;
}

.rank-fill.weak {
  background: linear-gradient(90deg, #f59e0b, #d97706);
}

.rank-score {
  font-size: 14px;
  font-weight: 600;
  color: var(--primary-color);
  min-width: 32px;
  text-align: right;
}

.rank-item.weak .rank-score {
  color: #f59e0b;
}

.rank-action-btn {
  padding: 6px 14px;
  font-size: 12px;
  border-radius: var(--radius-sm);
  border: 1px solid rgba(59, 89, 152, 0.3);
  background: rgba(59, 89, 152, 0.1);
  color: var(--primary-color);
  cursor: pointer;
  font-family: inherit;
  transition: all 0.2s ease;
}

.rank-action-btn:hover {
  background: rgba(59, 89, 152, 0.2);
  border-color: var(--primary-color);
}
</style>
