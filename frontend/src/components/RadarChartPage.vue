<template>
  <section id="page-radar-chart" class="page-section active">
    <header class="chart-page-header glass-card">
      <button class="back-btn" @click="goBack">
        <i class="fas fa-arrow-left"></i>
        返回成长中心
      </button>
      <h2 class="page-title">
        <i class="fas fa-chart-radar"></i>
        面试能力雷达图
      </h2>
      <div class="dimension-selector">
        <CustomSelect 
          v-model="selectedDimension" 
          :options="dimensionOptions"
        />
      </div>
    </header>

    <div class="radar-content glass-card">
      <div class="radar-main">
        <div class="radar-chart-large">
          <svg viewBox="0 0 600 600" class="radar-svg">
            <defs>
              <linearGradient id="radarGradientLarge" x1="0%" y1="0%" x2="100%" y2="100%">
                <stop offset="0%" stop-color="rgba(59, 89, 152, 0.7)" />
                <stop offset="100%" stop-color="rgba(102, 126, 234, 0.5)" />
              </linearGradient>
              <filter id="glowLarge">
                <feGaussianBlur stdDeviation="3" result="coloredBlur"/>
                <feMerge>
                  <feMergeNode in="coloredBlur"/>
                  <feMergeNode in="SourceGraphic"/>
                </feMerge>
              </filter>
            </defs>
            
            <polygon 
              v-for="(level, index) in radarLevels" 
              :key="index"
              :points="getGridPoints(level)"
              class="radar-grid"
            />
            
            <polygon 
              :points="currentRadarPoints" 
              class="radar-area"
              fill="url(#radarGradientLarge)"
            />
            
            <circle 
              v-for="(value, index) in radarData" 
              :key="index"
              :cx="getPointPosition(index, value).x"
              :cy="getPointPosition(index, value).y"
              :r="10"
              class="radar-point"
            />
          </svg>
          
          <div class="radar-labels-large">
            <span 
              v-for="(label, index) in radarLabels" 
              :key="index"
              class="radar-label-large"
              :style="{ 
                left: getLabelPosition(index).x + '%', 
                top: getLabelPosition(index).y + '%',
                borderColor: getRadarColor(index)
              }"
            >
              <span class="label-name">{{ label }}</span>
              <span class="label-score" :style="{ color: getRadarColor(index) }">{{ radarData[index] }}%</span>
            </span>
          </div>
        </div>
      </div>

      <div class="radar-sidebar">
        <div class="legend-section">
          <h4>能力维度分析</h4>
          <div class="legend-list">
            <div v-for="(label, index) in radarLabels" :key="index" class="legend-item-large">
              <div class="legend-header">
                <div class="legend-color" :style="{ background: getRadarColor(index) }"></div>
                <span class="legend-name">{{ label }}</span>
                <span class="legend-score" :style="{ color: getRadarColor(index) }">{{ radarData[index] }}%</span>
              </div>
              <div class="legend-bar">
                <div class="legend-fill" :style="{ width: radarData[index] + '%', background: getRadarColor(index) }"></div>
              </div>
              <p class="legend-desc">{{ getDimensionDesc(index) }}</p>
            </div>
          </div>
        </div>

        <div class="analysis-section">
          <h4>综合分析</h4>
          <div class="analysis-content">
            <p>您的技术能力整体处于<strong>{{ getOverallLevel() }}</strong>水平。</p>
            <p>最强项：<span class="highlight">{{ radarLabels[maxIndex] }}</span>（{{ radarData[maxIndex] }}%）</p>
            <p>待提升：<span class="weak">{{ radarLabels[minIndex] }}</span>（{{ radarData[minIndex] }}%）</p>
          </div>
          <div class="suggestion">
            <i class="fas fa-lightbulb"></i>
            <span>建议重点加强{{ radarLabels[minIndex] }}方面的练习</span>
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
  name: 'RadarChartPage',
  components: {
    CustomSelect
  },
  emits: ['goBack'],
  setup(props, { emit }) {
    const selectedDimension = ref('all')

    const dimensionOptions = [
      { value: 'all', label: '全部维度' },
      { value: 'tech', label: '技术正确性' },
      { value: 'depth', label: '技术深度' },
      { value: 'breadth', label: '知识广度' },
      { value: 'logic', label: '逻辑清晰度' },
      { value: 'problem', label: '问题解决' }
    ]

    const radarLabels = ['技术正确性', '技术深度', '知识广度', '逻辑清晰度', '问题解决']
    const radarData = reactive([85, 72, 78, 80, 68])
    const radarLevels = [20, 40, 60, 80, 100]

    const maxIndex = computed(() => radarData.indexOf(Math.max(...radarData)))
    const minIndex = computed(() => radarData.indexOf(Math.min(...radarData)))

    const getPointPosition = (index, value) => {
      const angle = (index * 72 - 90) * Math.PI / 180
      const maxRadius = 220
      const minRadius = 40
      const radius = minRadius + (value / 100) * (maxRadius - minRadius)
      const centerX = 300
      const centerY = 300
      const x = centerX + radius * Math.cos(angle)
      const y = centerY + radius * Math.sin(angle)
      return { x, y }
    }

    const getGridPoints = (level) => {
      const maxRadius = 220
      const minRadius = 40
      const radius = minRadius + (level / 100) * (maxRadius - minRadius)
      const centerX = 300
      const centerY = 300
      const points = []
      for (let i = 0; i < 5; i++) {
        const angle = (i * 72 - 90) * Math.PI / 180
        const x = centerX + radius * Math.cos(angle)
        const y = centerY + radius * Math.sin(angle)
        points.push(`${x},${y}`)
      }
      return points.join(' ')
    }

    const getLabelPosition = (index) => {
      const angle = (index * 72 - 90) * Math.PI / 180
      const labelRadius = 270
      const centerX = 300
      const centerY = 300
      const x = ((centerX + labelRadius * Math.cos(angle)) / 600) * 100
      const y = ((centerY + labelRadius * Math.sin(angle)) / 600) * 100
      return { x, y }
    }

    const getRadarPoints = () => {
      return radarData.map((value, index) => {
        const pos = getPointPosition(index, value)
        return `${pos.x},${pos.y}`
      }).join(' ')
    }

    const currentRadarPoints = computed(() => getRadarPoints())

    const getRadarColor = (index) => {
      const colors = ['#667eea', '#f093fb', '#4facfe', '#43e97b', '#fa709a']
      return colors[index]
    }

    const getDimensionDesc = (index) => {
      const descs = [
        '代码实现的准确性和规范性',
        '对技术原理的深入理解程度',
        '技术栈的广度和跨领域知识',
        '表达思路的条理性和逻辑性',
        '分析和解决实际问题的能力'
      ]
      return descs[index]
    }

    const getOverallLevel = () => {
      const avg = radarData.reduce((a, b) => a + b, 0) / radarData.length
      if (avg >= 80) return '优秀'
      if (avg >= 60) return '良好'
      return '待提升'
    }

    const goBack = () => {
      emit('goBack')
    }

    return {
      selectedDimension,
      dimensionOptions,
      radarLabels,
      radarData,
      radarLevels,
      maxIndex,
      minIndex,
      getPointPosition,
      getGridPoints,
      getLabelPosition,
      currentRadarPoints,
      getRadarColor,
      getDimensionDesc,
      getOverallLevel,
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
  color: var(--primary-color);
}

.radar-content {
  display: flex;
  gap: 32px;
  padding: 32px;
}

.radar-main {
  flex: 1;
  display: flex;
  justify-content: center;
  align-items: center;
}

.radar-chart-large {
  width: 600px;
  height: 600px;
  position: relative;
}

.radar-svg {
  width: 100%;
  height: 100%;
}

.radar-grid {
  fill: none;
  stroke: rgba(59, 89, 152, 0.12);
  stroke-width: 1.5;
}

.radar-area {
  stroke: var(--primary-color);
  stroke-width: 3;
  transition: all 0.5s ease;
  filter: url(#glowLarge);
}

.radar-point {
  fill: var(--primary-color);
  stroke: white;
  stroke-width: 3;
  transition: all 0.3s ease;
}

.radar-labels-large {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.radar-label-large {
  position: absolute;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 12px 20px;
  background: rgba(255, 255, 255, 0.95);
  border-radius: 12px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
  border-left: 4px solid;
  transform: translate(-50%, -50%);
  pointer-events: auto;
}

.label-name {
  font-size: 14px;
  font-weight: 600;
  color: #1a1a2e;
}

.label-score {
  font-size: 20px;
  font-weight: 700;
}

.radar-sidebar {
  width: 320px;
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.legend-section h4,
.analysis-section h4 {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0 0 16px;
}

.legend-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.legend-item-large {
  padding: 16px;
  background: rgba(255, 255, 255, 0.03);
  border-radius: 10px;
}

.legend-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}

.legend-color {
  width: 12px;
  height: 12px;
  border-radius: 3px;
}

.legend-name {
  flex: 1;
  font-size: 14px;
  font-weight: 500;
  color: var(--text-primary);
}

.legend-score {
  font-size: 18px;
  font-weight: 700;
}

.legend-bar {
  height: 6px;
  background: rgba(59, 89, 152, 0.1);
  border-radius: 3px;
  overflow: hidden;
  margin-bottom: 8px;
}

.legend-fill {
  height: 100%;
  border-radius: 3px;
  transition: width 0.5s ease;
}

.legend-desc {
  font-size: 12px;
  color: var(--text-secondary);
  margin: 0;
}

.analysis-content {
  padding: 16px;
  background: rgba(255, 255, 255, 0.03);
  border-radius: 10px;
  margin-bottom: 16px;
}

.analysis-content p {
  font-size: 14px;
  color: var(--text-secondary);
  margin: 0 0 8px;
}

.analysis-content p:last-child {
  margin-bottom: 0;
}

.analysis-content strong {
  color: var(--text-primary);
}

.analysis-content .highlight {
  color: #10b981;
  font-weight: 600;
}

.analysis-content .weak {
  color: #ef4444;
  font-weight: 600;
}

.suggestion {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  background: rgba(245, 158, 11, 0.1);
  border-radius: 8px;
  font-size: 13px;
  color: #f59e0b;
}

.suggestion i {
  font-size: 16px;
}
</style>
