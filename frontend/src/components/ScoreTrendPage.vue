<template>
  <section id="page-score-trend" class="page-section active">
    <header class="chart-page-header glass-card">
      <button class="back-btn" @click="goBack">
        <i class="fas fa-arrow-left"></i>
        返回成长中心
      </button>
      <h2 class="page-title">
        <i class="fas fa-chart-area"></i>
        综合得分趋势
      </h2>
      <div class="time-selector">
        <CustomSelect 
          v-model="selectedTimeRange" 
          :options="timeRangeOptions"
        />
      </div>
    </header>

    <div class="trend-content">
      <div class="stats-row">
        <div class="stat-card-large glass-card">
          <div class="stat-icon high">
            <i class="fas fa-arrow-up"></i>
          </div>
          <div class="stat-info">
            <span class="stat-label">最高分</span>
            <span class="stat-value">{{ maxScore }}</span>
          </div>
        </div>
        <div class="stat-card-large glass-card">
          <div class="stat-icon low">
            <i class="fas fa-arrow-down"></i>
          </div>
          <div class="stat-info">
            <span class="stat-label">最低分</span>
            <span class="stat-value">{{ minScore }}</span>
          </div>
        </div>
        <div class="stat-card-large glass-card">
          <div class="stat-icon avg">
            <i class="fas fa-chart-line"></i>
          </div>
          <div class="stat-info">
            <span class="stat-label">平均分</span>
            <span class="stat-value">{{ avgScore }}</span>
          </div>
        </div>
        <div class="stat-card-large glass-card">
          <div class="stat-icon trend">
            <i class="fas fa-trending-up"></i>
          </div>
          <div class="stat-info">
            <span class="stat-label">进步幅度</span>
            <span class="stat-value">+{{ progressRate }}%</span>
          </div>
        </div>
      </div>

      <div class="chart-card glass-card">
        <div class="chart-header">
          <h3>得分走势</h3>
          <div class="chart-legend">
            <span class="legend-item">
              <span class="legend-dot green"></span>
              得分
            </span>
            <span class="legend-item">
              <span class="legend-line dashed"></span>
              平均线
            </span>
          </div>
        </div>
        <div class="chart-wrapper">
          <div class="y-axis">
            <span>100</span>
            <span>80</span>
            <span>60</span>
            <span>40</span>
            <span>20</span>
            <span>0</span>
          </div>
          <div class="chart-area">
            <svg viewBox="0 0 800 300" preserveAspectRatio="xMidYMid meet">
              <defs>
                <linearGradient id="areaGradientLarge" x1="0%" y1="0%" x2="0%" y2="100%">
                  <stop offset="0%" stop-color="rgba(16, 185, 129, 0.5)" />
                  <stop offset="50%" stop-color="rgba(16, 185, 129, 0.2)" />
                  <stop offset="100%" stop-color="rgba(16, 185, 129, 0.02)" />
                </linearGradient>
                <linearGradient id="lineGradientLarge" x1="0%" y1="0%" x2="100%" y2="0%">
                  <stop offset="0%" stop-color="#10b981" />
                  <stop offset="50%" stop-color="#34d399" />
                  <stop offset="100%" stop-color="#10b981" />
                </linearGradient>
                <filter id="glowLarge">
                  <feGaussianBlur stdDeviation="3" result="coloredBlur"/>
                  <feMerge>
                    <feMergeNode in="coloredBlur"/>
                    <feMergeNode in="SourceGraphic"/>
                  </feMerge>
                </filter>
              </defs>
              
              <g class="grid-lines">
                <line x1="0" y1="60" x2="800" y2="60" class="grid-line" />
                <line x1="0" y1="120" x2="800" y2="120" class="grid-line" />
                <line x1="0" y1="180" x2="800" y2="180" class="grid-line" />
                <line x1="0" y1="240" x2="800" y2="240" class="grid-line" />
              </g>
              
              <line x1="0" :y1="avgLineY" x2="800" :y2="avgLineY" class="avg-line" />
              
              <path 
                :d="areaPath"
                fill="url(#areaGradientLarge)"
                class="trend-area"
              />
              
              <path 
                :d="linePath"
                fill="none"
                stroke="url(#lineGradientLarge)"
                stroke-width="4"
                stroke-linecap="round"
                stroke-linejoin="round"
                filter="url(#glowLarge)"
                class="trend-line"
              />
              
              <g class="data-points">
                <circle 
                  v-for="(point, index) in chartData" 
                  :key="index"
                  :cx="point.x"
                  :cy="point.y"
                  r="8"
                  class="trend-point-outer"
                />
                <circle 
                  v-for="(point, index) in chartData" 
                  :key="'inner'+index"
                  :cx="point.x"
                  :cy="point.y"
                  r="4"
                  class="trend-point-inner"
                />
              </g>
              
              <g class="value-labels">
                <text 
                  v-for="(point, index) in chartData" 
                  :key="'label'+index"
                  :x="point.x"
                  :y="point.y - 15"
                  class="value-label"
                >
                  {{ point.value }}
                </text>
              </g>
            </svg>
          </div>
        </div>
        <div class="x-axis">
          <span v-for="(label, index) in xLabels" :key="index">{{ label }}</span>
        </div>
      </div>

      <div class="insights-card glass-card">
        <h3><i class="fas fa-lightbulb"></i> 成长洞察</h3>
        <div class="insights-list">
          <div class="insight-item">
            <i class="fas fa-check-circle"></i>
            <span>您的得分整体呈上升趋势，进步明显</span>
          </div>
          <div class="insight-item">
            <i class="fas fa-star"></i>
            <span>最佳表现出现在{{ bestDay }}，得分{{ maxScore }}分</span>
          </div>
          <div class="insight-item">
            <i class="fas fa-chart-line"></i>
            <span>相比初期，您的得分提升了{{ progressRate }}%</span>
          </div>
          <div class="insight-item suggestion">
            <i class="fas fa-arrow-right"></i>
            <span>建议继续保持练习频率，稳步提升</span>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>

<script>
import { ref, reactive, computed } from 'vue'
import CustomSelect from './CustomSelect.vue'

export default {
  name: 'ScoreTrendPage',
  components: {
    CustomSelect
  },
  emits: ['goBack'],
  setup(props, { emit }) {
    const selectedTimeRange = ref('week')

    const timeRangeOptions = [
      { value: 'week', label: '最近一周' },
      { value: 'month', label: '最近一月' },
      { value: 'quarter', label: '最近三月' },
      { value: 'year', label: '最近一年' }
    ]

    const scoreData = reactive([70, 80, 76, 90, 85, 92, 88])
    const xLabels = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']

    const maxScore = computed(() => Math.max(...scoreData))
    const minScore = computed(() => Math.min(...scoreData))
    const avgScore = computed(() => Math.round(scoreData.reduce((a, b) => a + b, 0) / scoreData.length))
    const progressRate = computed(() => {
      const first = scoreData[0]
      const last = scoreData[scoreData.length - 1]
      return Math.round(((last - first) / first) * 100)
    })
    const bestDay = computed(() => xLabels[scoreData.indexOf(maxScore.value)])

    const chartData = computed(() => {
      const step = 800 / (scoreData.length - 1)
      return scoreData.map((value, index) => ({
        x: index * step,
        y: 300 - (value / 100) * 300,
        value
      }))
    })

    const linePath = computed(() => {
      return chartData.value.map((point, index) => {
        return `${index === 0 ? 'M' : 'L'} ${point.x} ${point.y}`
      }).join(' ')
    })

    const areaPath = computed(() => {
      const line = linePath.value
      const lastPoint = chartData.value[chartData.value.length - 1]
      const firstPoint = chartData.value[0]
      return `${line} L ${lastPoint.x} 300 L ${firstPoint.x} 300 Z`
    })

    const avgLineY = computed(() => 300 - (avgScore.value / 100) * 300)

    const goBack = () => {
      emit('goBack')
    }

    return {
      selectedTimeRange,
      timeRangeOptions,
      scoreData,
      xLabels,
      maxScore,
      minScore,
      avgScore,
      progressRate,
      bestDay,
      chartData,
      linePath,
      areaPath,
      avgLineY,
      goBack
    }
  }
}
</script>

<style scoped>
.chart-page-header {
  display: flex;
  align-items: center;
  gap: 24px;
  padding: 20px 24px;
  margin-bottom: 24px;
}

.back-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 20px;
  background: rgba(59, 89, 152, 0.1);
  border: 1px solid rgba(59, 89, 152, 0.3);
  border-radius: var(--radius-md);
  color: var(--primary-color);
  font-size: 14px;
  cursor: pointer;
  transition: all 0.3s ease;
  font-family: inherit;
}

.back-btn:hover {
  background: rgba(59, 89, 152, 0.2);
}

.page-title {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 20px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
}

.page-title i {
  color: #10b981;
}

.trend-content {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.stats-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
}

.stat-card-large {
  padding: 24px;
  display: flex;
  align-items: center;
  gap: 20px;
}

.stat-icon {
  width: 56px;
  height: 56px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
}

.stat-icon.high {
  background: rgba(16, 185, 129, 0.15);
  color: #10b981;
}

.stat-icon.low {
  background: rgba(239, 68, 68, 0.15);
  color: #ef4444;
}

.stat-icon.avg {
  background: rgba(59, 130, 246, 0.15);
  color: #3b82f6;
}

.stat-icon.trend {
  background: rgba(245, 158, 11, 0.15);
  color: #f59e0b;
}

.stat-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.stat-label {
  font-size: 13px;
  color: var(--text-secondary);
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: var(--text-primary);
}

.chart-card {
  padding: 24px;
}

.chart-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.chart-header h3 {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
}

.chart-legend {
  display: flex;
  gap: 20px;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--text-secondary);
}

.legend-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
}

.legend-dot.green {
  background: #10b981;
}

.legend-line {
  width: 20px;
  height: 2px;
  background: repeating-linear-gradient(90deg, #f59e0b 0, #f59e0b 4px, transparent 4px, transparent 8px);
}

.chart-wrapper {
  display: flex;
  gap: 12px;
  height: 300px;
}

.y-axis {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  font-size: 12px;
  color: var(--text-secondary);
  padding: 10px 0;
}

.chart-area {
  flex: 1;
}

.chart-area svg {
  width: 100%;
  height: 100%;
}

.grid-line {
  stroke: rgba(59, 89, 152, 0.08);
  stroke-width: 1;
  stroke-dasharray: 4 2;
}

.avg-line {
  stroke: #f59e0b;
  stroke-width: 2;
  stroke-dasharray: 8 4;
  opacity: 0.6;
}

.trend-area {
  transition: all 0.3s ease;
}

.trend-line {
  transition: all 0.3s ease;
}

.trend-point-outer {
  fill: rgba(16, 185, 129, 0.2);
  stroke: #10b981;
  stroke-width: 3;
  transition: all 0.3s ease;
}

.trend-point-inner {
  fill: white;
  transition: all 0.3s ease;
}

.value-label {
  font-size: 12px;
  font-weight: 600;
  fill: #10b981;
  text-anchor: middle;
}

.x-axis {
  display: flex;
  justify-content: space-between;
  padding-left: 40px;
  margin-top: 12px;
  font-size: 12px;
  color: var(--text-secondary);
}

.insights-card {
  padding: 24px;
}

.insights-card h3 {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0 0 20px;
}

.insights-card h3 i {
  color: #f59e0b;
}

.insights-list {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
}

.insight-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  background: rgba(255, 255, 255, 0.03);
  border-radius: 10px;
  font-size: 14px;
  color: var(--text-secondary);
}

.insight-item i {
  font-size: 18px;
  color: #10b981;
}

.insight-item.suggestion {
  background: rgba(245, 158, 11, 0.1);
  color: #f59e0b;
}

.insight-item.suggestion i {
  color: #f59e0b;
}
</style>
