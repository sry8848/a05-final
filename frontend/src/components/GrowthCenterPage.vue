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
        <p class="block-desc">仅统计最近 8 场专业模式面试，维度与面试报告保持一致。</p>
        <div v-if="hasProfessionalRadarData" class="radar-wrap">
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
        <div v-else class="radar-empty">
          <i class="fas fa-chart-radar"></i>
          <p>暂无可计算的专业模式样本</p>
          <span>至少完成 1 场专业模式面试后，这里才会显示能力雷达图。</span>
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
            <div v-if="!topStrengths.length" class="rank-empty">最近 8 场内暂无满足规则的红榜知识域。</div>
            <div v-for="(item, idx) in topStrengths" :key="'s-' + idx" class="rank-item">
              <span class="rank-position">{{ idx + 1 }}</span>
              <div class="rank-main">
                <div class="rank-title-row">
                  <span class="rank-name">{{ item.name }}</span>
                  <span class="rank-score">{{ item.score }}</span>
                </div>
                <div class="rank-meta">
                  <span class="rank-delta positive">{{ formatDelta(item.delta) }}</span>
                  <span class="rank-samples">{{ item.appearanceCount }} 次</span>
                </div>
                <div class="rank-bar">
                  <div class="rank-fill" :style="{ width: item.score + '%' }"></div>
                </div>
                <div v-if="item.weaknessPoints.length" class="rank-points">
                  <span v-for="point in item.weaknessPoints" :key="point" class="rank-point-tag">{{ point }}</span>
                </div>
              </div>
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
            <div v-if="!topWeaknesses.length" class="rank-empty">最近 8 场内暂无满足规则的黑榜知识域。</div>
            <div v-for="(item, idx) in topWeaknesses" :key="'w-' + idx" class="rank-item weak">
              <span class="rank-position">{{ idx + 1 }}</span>
              <div class="rank-main">
                <div class="rank-title-row">
                  <span class="rank-name">{{ item.name }}</span>
                  <span class="rank-score">{{ item.score }}</span>
                </div>
                <div class="rank-meta">
                  <span class="rank-delta negative">{{ formatDelta(item.delta) }}</span>
                  <span class="rank-samples">{{ item.appearanceCount }} 次</span>
                </div>
                <div class="rank-bar">
                  <div class="rank-fill weak" :style="{ width: item.score + '%' }"></div>
                </div>
                <div v-if="item.weaknessPoints.length" class="rank-points">
                  <span v-for="point in item.weaknessPoints" :key="point" class="rank-point-tag">{{ point }}</span>
                </div>
              </div>
              <button type="button" class="rank-action-btn" @click="goToInterview(item.code, selectedPosition)">
                去练习
              </button>
            </div>
          </div>
        </div>
      </div>

      <div class="domain-trend-card glass-card">
        <div class="domain-trend-header">
          <div>
            <h4>知识域近 8 场得分变化</h4>
            <p>只可查看当前红黑榜中展示的知识域，折线点位为该知识域在对应场次中的原始得分。</p>
          </div>
          <div v-if="selectedRankTrendItem" class="domain-trend-summary">
            <span>{{ selectedRankTrendItem.name }}</span>
            <strong>{{ selectedRankTrendItem.score }}</strong>
          </div>
        </div>

        <div v-if="rankedDomainOptions.length" class="domain-chip-list">
          <button
            v-for="item in rankedDomainOptions"
            :key="item.code"
            type="button"
            class="domain-chip"
            :class="{ active: selectedRankTrendDomain === item.code }"
            @click="selectedRankTrendDomain = item.code"
          >
            {{ item.name }}
          </button>
        </div>

        <div v-if="domainTrendSeries.length" class="domain-trend-chart-wrap">
          <svg viewBox="0 0 720 220" preserveAspectRatio="xMidYMid meet" class="domain-trend-svg">
            <defs>
              <linearGradient id="domainTrendGrad" x1="0%" y1="0%" x2="0%" y2="100%">
                <stop offset="0%" stop-color="rgba(59, 89, 152, 0.35)" />
                <stop offset="100%" stop-color="rgba(59, 89, 152, 0.04)" />
              </linearGradient>
            </defs>
            <path :d="domainTrendAreaPath" fill="url(#domainTrendGrad)" class="trend-area" />
            <path :d="domainTrendLinePath" fill="none" stroke="#3b5998" stroke-width="3" stroke-linecap="round" stroke-linejoin="round" class="trend-line" />
            <circle
              v-for="(p, i) in domainTrendChartPoints"
              :key="'dc-' + i"
              :cx="p.x"
              :cy="p.y"
              r="5"
              fill="#3b5998"
              stroke="white"
              stroke-width="2"
            />
          </svg>
          <div class="trend-x-labels">
            <span v-for="(lb, i) in domainTrendXLabels" :key="'dx-' + i">{{ lb }}</span>
          </div>
        </div>

        <div v-else class="domain-trend-empty">
          当前选中知识域在最近 8 场内暂无可绘制的得分轨迹。
        </div>
      </div>
    </div>
  </section>
</template>

<script>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { getProfile, getProfileSkillOverview, getProfileStatistics } from '../api/resume'
import CustomSelect from './CustomSelect.vue'
import { getGrowthRequestPositionCodes } from '../utils/growthHistoryState'

const RADAR_DIMENSIONS = [
  { key: 'fundamentals', label: '基础原理掌握' },
  { key: 'engineering_practice', label: '工程实践与项目落地' },
  { key: 'scenario_tradeoff', label: '场景分析与方案取舍' },
  { key: 'debugging', label: '问题定位与排查思路' },
  { key: 'communication', label: '沟通表达与结构化呈现' }
]

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
    const professionalRadarScores = ref([])
    const professionalRadarSampleCount = ref(0)
    const skillOverviewTopStrengths = ref([])
    const skillOverviewTopWeaknesses = ref([])
    const selectedRankTrendDomain = ref('')

    const radarScoreMap = computed(() => {
      const map = new Map()
      professionalRadarScores.value.forEach((item) => {
        if (item?.dimensionKey) {
          map.set(item.dimensionKey, item)
        }
      })
      return map
    })
    const radarLabels = computed(() => RADAR_DIMENSIONS.map((item) => item.label))
    const radarData = computed(() => RADAR_DIMENSIONS.map((item) => {
      const score = Number(radarScoreMap.value.get(item.key)?.score || 0)
      return Math.max(0, Math.min(100, Math.round(score)))
    }))
    const hasProfessionalRadarData = computed(() =>
      professionalRadarSampleCount.value > 0 && professionalRadarScores.value.length > 0
    )
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

    const normalizeRankItem = (item) => ({
      name: item?.domainName || item?.domainCode || '未知知识域',
      score: Math.round(Number(item?.score || 0)),
      code: item?.domainCode,
      delta: Number(item?.scoreDelta || 0),
      appearanceCount: Number(item?.appearanceCount || 0),
      weaknessPoints: Array.isArray(item?.weaknessPoints) ? item.weaknessPoints.slice(0, 3) : [],
      recentScores: Array.isArray(item?.recentScores) ? item.recentScores : []
    })

    const topStrengths = computed(() =>
      (Array.isArray(skillOverviewTopStrengths.value) ? skillOverviewTopStrengths.value : []).map(normalizeRankItem)
    )
    const topWeaknesses = computed(() =>
      (Array.isArray(skillOverviewTopWeaknesses.value) ? skillOverviewTopWeaknesses.value : []).map(normalizeRankItem)
    )

    const rankedDomainOptions = computed(() => {
      const map = new Map()
      ;[...topStrengths.value, ...topWeaknesses.value].forEach((item) => {
        if (item?.code && !map.has(item.code)) {
          map.set(item.code, item)
        }
      })
      return [...map.values()]
    })

    const selectedRankTrendItem = computed(() =>
      rankedDomainOptions.value.find((item) => item.code === selectedRankTrendDomain.value) || rankedDomainOptions.value[0] || null
    )

    const domainTrendSeries = computed(() => selectedRankTrendItem.value?.recentScores || [])
    const domainTrendXLabels = computed(() =>
      domainTrendSeries.value.map((item) => String(item?.date || '').slice(5))
    )
    const domainTrendChartPoints = computed(() => {
      const data = domainTrendSeries.value.map((item) => Number(item?.score || 0))
      if (!data.length) return []
      const w = 720
      const h = 200
      const padding = 26
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
    const domainTrendLinePath = computed(() => {
      const pts = domainTrendChartPoints.value
      if (!pts.length) return ''
      return pts.map((p, i) => (i === 0 ? `M ${p.x} ${p.y}` : `L ${p.x} ${p.y}`)).join(' ')
    })
    const domainTrendAreaPath = computed(() => {
      const pts = domainTrendChartPoints.value
      if (!pts.length) return ''
      const h = 200
      const padding = 26
      const line = domainTrendLinePath.value
      return `${line} L ${pts[pts.length - 1].x} ${h - padding} L ${pts[0].x} ${h - padding} Z`
    })

    const loadGrowthData = async () => {
      loading.value = true
      loadError.value = ''
      const requestPositionCodes = getGrowthRequestPositionCodes(selectedPosition.value, positionCodeMap)
      try {
        const [profile, statistics, skillOverview] = await Promise.all([
          getProfile(),
          getProfileStatistics(requestPositionCodes.statisticsPositionCode),
          getProfileSkillOverview(requestPositionCodes.skillOverviewPositionCode)
        ])

        profileNickname.value = profile?.nickname || props.user.name || '面试者'
        stats.totalInterviews = Number(statistics?.totalSessions) || 0
        stats.totalHours = Math.round(((Number(statistics?.totalMinutes) || 0) / 60) * 10) / 10
        stats.avgScore = statistics?.averageScore == null ? '--' : Math.round(Number(statistics.averageScore))
        trendPoints.value = Array.isArray(statistics?.scoreTrend) ? statistics.scoreTrend : []
        professionalRadarScores.value = Array.isArray(statistics?.professionalRadarScores) ? statistics.professionalRadarScores : []
        professionalRadarSampleCount.value = Number(statistics?.professionalSampleCount || 0)
        skillDomains.value = Array.isArray(skillOverview?.domains) ? skillOverview.domains : []
        skillOverviewTopStrengths.value = Array.isArray(skillOverview?.topStrengths) ? skillOverview.topStrengths : []
        skillOverviewTopWeaknesses.value = Array.isArray(skillOverview?.topWeaknesses) ? skillOverview.topWeaknesses : []
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

    watch(rankedDomainOptions, (list) => {
      if (!list.length) {
        selectedRankTrendDomain.value = ''
        return
      }
      if (!list.some((item) => item.code === selectedRankTrendDomain.value)) {
        selectedRankTrendDomain.value = list[0].code
      }
    }, { immediate: true })

    onMounted(() => {
      loadGrowthData()
    })

    const formatDelta = (delta) => {
      const value = Math.round(Number(delta || 0))
      return `${value >= 0 ? '+' : ''}${value}`
    }

    return {
      loading,
      loadError,
      displayName,
      stats,
      hasProfessionalRadarData,
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
      rankedDomainOptions,
      selectedRankTrendDomain,
      selectedRankTrendItem,
      domainTrendSeries,
      domainTrendChartPoints,
      domainTrendLinePath,
      domainTrendAreaPath,
      domainTrendXLabels,
      formatDelta,
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

.block-desc {
  margin: -4px 0 16px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--text-secondary);
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

.radar-empty {
  min-height: 320px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  gap: 10px;
  color: var(--text-secondary);
  text-align: center;
}

.radar-empty i {
  font-size: 28px;
  color: var(--primary-color);
}

.radar-empty p {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
}

.radar-empty span {
  max-width: 260px;
  font-size: 13px;
  line-height: 1.6;
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

.rank-empty {
  padding: 14px 16px;
  border: 1px dashed rgba(148, 163, 184, 0.35);
  border-radius: var(--radius-md);
  color: var(--text-secondary);
  font-size: 13px;
}

.rank-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
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

.rank-main {
  flex: 1;
  min-width: 0;
}

.rank-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 6px;
}

.rank-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
  font-size: 12px;
}

.rank-delta {
  font-weight: 700;
}

.rank-delta.positive {
  color: #10b981;
}

.rank-delta.negative {
  color: #ef4444;
}

.rank-samples {
  color: var(--text-secondary);
}

.rank-bar {
  width: 100%;
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

.rank-points {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

.rank-point-tag {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(59, 89, 152, 0.08);
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 1.4;
}

.rank-item.weak .rank-point-tag {
  background: rgba(245, 158, 11, 0.12);
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

.domain-trend-card {
  margin-top: 24px;
  padding: 24px;
}

.domain-trend-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 16px;
}

.domain-trend-header h4 {
  margin: 0 0 6px;
  font-size: 16px;
  color: var(--text-primary);
}

.domain-trend-header p {
  margin: 0;
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.6;
}

.domain-trend-summary {
  min-width: 120px;
  padding: 10px 14px;
  border-radius: 12px;
  background: rgba(59, 89, 152, 0.08);
  color: var(--text-primary);
  text-align: right;
}

.domain-trend-summary span {
  display: block;
  font-size: 12px;
  color: var(--text-secondary);
}

.domain-trend-summary strong {
  font-size: 22px;
  color: var(--primary-color);
}

.domain-chip-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 18px;
}

.domain-chip {
  border: 1px solid rgba(59, 89, 152, 0.22);
  background: rgba(59, 89, 152, 0.08);
  color: var(--text-secondary);
  border-radius: 999px;
  padding: 8px 14px;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.domain-chip.active,
.domain-chip:hover {
  color: white;
  background: linear-gradient(135deg, var(--primary-color), var(--primary-light));
  border-color: transparent;
}

.domain-trend-chart-wrap {
  width: 100%;
}

.domain-trend-svg {
  width: 100%;
  height: 220px;
  display: block;
}

.domain-trend-empty {
  padding: 16px 0 4px;
  color: var(--text-secondary);
  font-size: 13px;
}
</style>
