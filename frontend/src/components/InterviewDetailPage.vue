<template>
  <section id="page-interview-detail" class="page-section active">
    <header class="detail-header glass-card">
      <button class="back-btn" @click="goBack">
        <i class="fas fa-arrow-left"></i>
        返回历史记录
      </button>
      <div class="header-content">
        <h2 class="page-title">
          <i class="fas fa-file-alt"></i>
          面试记录详情
        </h2>
        <p class="page-subtitle">{{ interviewRecord?.company || '模拟面试' }} · {{ interviewRecord?.job }}</p>
      </div>
    </header>

    <div class="detail-content">
      <div class="overview-section glass-card">
        <div class="overview-header">
          <h3>
            <i class="fas fa-chart-pie"></i>
            面试概览
          </h3>
          <span class="interview-date">{{ interviewRecord?.date }}</span>
        </div>
        
        <div class="overview-grid">
          <div class="overview-item main-score">
            <div class="score-circle" :class="getScoreClass(interviewRecord?.score)">
              <svg viewBox="0 0 100 100">
                <circle cx="50" cy="50" r="45" class="score-bg" />
                <circle 
                  cx="50" cy="50" r="45" 
                  class="score-fill" 
                  :style="{ strokeDashoffset: getScoreOffset(interviewRecord?.score) }"
                />
              </svg>
              <div class="score-content">
                <span class="score-number">{{ interviewRecord?.score || 0 }}</span>
                <span class="score-label">综合得分</span>
              </div>
            </div>
          </div>
          
          <div class="overview-stats">
            <div class="stat-row">
              <div class="stat-item">
                <i class="fas fa-briefcase"></i>
                <div class="stat-info">
                  <span class="stat-value">{{ interviewRecord?.job }}</span>
                  <span class="stat-label">面试岗位</span>
                </div>
              </div>
              <div class="stat-item">
                <i class="fas fa-layer-group"></i>
                <div class="stat-info">
                  <span class="stat-value">{{ interviewRecord?.round || '一面' }}</span>
                  <span class="stat-label">面试轮次</span>
                </div>
              </div>
            </div>
            <div class="stat-row">
              <div class="stat-item">
                <i class="fas fa-clock"></i>
                <div class="stat-info">
                  <span class="stat-value">{{ interviewRecord?.duration || '00:00' }}</span>
                  <span class="stat-label">面试时长</span>
                </div>
              </div>
              <div class="stat-item">
                <i class="fas fa-question-circle"></i>
                <div class="stat-info">
                  <span class="stat-value">{{ interviewRecord?.questions || 0 }}题</span>
                  <span class="stat-label">题目数量</span>
                </div>
              </div>
            </div>
            <div class="stat-row">
              <div class="stat-item">
                <i class="fas fa-check-circle"></i>
                <div class="stat-info">
                  <span class="stat-value correct">{{ interviewRecord?.correct || 0 }}题</span>
                  <span class="stat-label">回答正确</span>
                </div>
              </div>
              <div class="stat-item">
                <i class="fas fa-trophy"></i>
                <div class="stat-info">
                  <span class="stat-value">击败{{ interviewRecord?.beatPercent || 0 }}%</span>
                  <span class="stat-label">超越用户</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="analysis-section glass-card">
        <h3>
          <i class="fas fa-brain"></i>
          能力分析
        </h3>
        
        <div class="analysis-content">
          <div class="radar-chart-container">
            <svg viewBox="0 0 300 300" class="radar-svg">
              <defs>
                <linearGradient id="detailRadarGradient" x1="0%" y1="0%" x2="100%" y2="100%">
                  <stop offset="0%" stop-color="rgba(59, 89, 152, 0.6)" />
                  <stop offset="100%" stop-color="rgba(102, 126, 234, 0.4)" />
                </linearGradient>
              </defs>
              
              <g class="grid">
                <polygon v-for="i in 5" :key="'grid'+i" :points="getGridPoints(i * 20)" class="grid-polygon" />
              </g>
              
              <g class="axes">
                <line v-for="(dim, index) in dimensions" :key="'axis'+index" 
                  x1="150" y1="150" 
                  :x2="getAxisEnd(index).x" 
                  :y2="getAxisEnd(index).y" 
                  class="axis-line"
                />
              </g>
              
              <polygon :points="getDataPoints()" class="data-polygon" />
              
              <g class="labels">
                <text 
                  v-for="(dim, index) in dimensions" 
                  :key="'label'+index"
                  :x="getLabelPosition(index).x"
                  :y="getLabelPosition(index).y"
                  class="dimension-label"
                >
                  {{ dim.name }}
                </text>
              </g>
              
              <g class="data-points">
                <circle 
                  v-for="(dim, index) in dimensions" 
                  :key="'point'+index"
                  :cx="getDataPoint(index).x"
                  :cy="getDataPoint(index).y"
                  r="5"
                  class="data-point"
                />
              </g>
            </svg>
          </div>
          
          <div class="dimension-list">
            <div 
              v-for="(dim, index) in dimensions" 
              :key="index"
              class="dimension-item"
            >
              <div class="dimension-header">
                <span class="dimension-name">{{ dim.name }}</span>
                <span class="dimension-score" :class="getScoreClass(dim.value)">{{ dim.value }}%</span>
              </div>
              <div class="dimension-bar">
                <div class="bar-fill" :style="{ width: dim.value + '%' }" :class="getScoreClass(dim.value)"></div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="questions-section glass-card">
        <div class="section-header">
          <h3>
            <i class="fas fa-list-check"></i>
            答题详情
          </h3>
          <div class="filter-tabs">
            <button 
              v-for="tab in questionTabs" 
              :key="tab.value"
              class="tab-btn"
              :class="{ active: activeTab === tab.value }"
              @click="activeTab = tab.value"
            >
              {{ tab.label }}
              <span class="tab-count">{{ getTabCount(tab.value) }}</span>
            </button>
          </div>
        </div>

        <div class="questions-list">
          <div 
            v-for="answer in filteredAnswers" 
            :key="answer.questionId || answer.originalIndex"
            class="question-item glass-card"
            @click="showQuestionDetail(answer)"
          >
            <div class="question-header">
              <span class="question-number">Q{{ answer.originalIndex + 1 }}</span>
              <span class="question-score" :class="getScoreClass(answer.score)">
                {{ answer.score }}分
              </span>
            </div>
            
            <div class="question-content">
              <p class="question-text">{{ answer.question }}</p>
              
              <div class="answer-section">
                <div class="answer-item">
                  <span class="answer-label">
                    <i class="fas fa-user"></i>
                    我的回答
                  </span>
                  <p class="answer-text" :class="{ empty: !answer.answer || answer.answer === '[跳过]' }">
                    {{ answer.answer || '未作答' }}
                  </p>
                </div>
              </div>
              
              <div class="keywords-section" v-if="answer.keywords && answer.keywords.length">
                <span class="keywords-label">关键词覆盖：</span>
                <div class="keywords-list">
                  <span 
                    v-for="keyword in answer.keywords" 
                    :key="keyword"
                    class="keyword-tag"
                    :class="{ covered: isKeywordCovered(answer.answer, keyword) }"
                  >
                    {{ keyword }}
                  </span>
                </div>
              </div>

              <div class="question-actions">
                <button class="detail-link-btn" @click.stop="showQuestionDetail(answer)">
                  查看单题复盘
                  <i class="fas fa-arrow-right"></i>
                </button>
              </div>
            </div>
          </div>

          <div v-if="filteredAnswers.length === 0" class="empty-state">
            <i class="fas fa-inbox"></i>
            <p>暂无符合条件的题目</p>
          </div>
        </div>
      </div>

      <div class="feedback-section glass-card">
        <h3>
          <i class="fas fa-comment-dots"></i>
          面试评价
        </h3>
        
        <div class="feedback-content">
          <div class="feedback-item" v-if="interviewRecord?.score >= 80">
            <div class="feedback-icon excellent">
              <i class="fas fa-star"></i>
            </div>
            <div class="feedback-text">
              <h4>表现优秀</h4>
              <p>你对{{ interviewRecord?.job }}相关知识掌握得很好，继续保持！建议挑战更高难度的面试。</p>
            </div>
          </div>
          
          <div class="feedback-item" v-else-if="interviewRecord?.score >= 60">
            <div class="feedback-icon good">
              <i class="fas fa-thumbs-up"></i>
            </div>
            <div class="feedback-text">
              <h4>表现良好</h4>
              <p>基础知识扎实，建议针对薄弱环节进行针对性练习，提升综合能力。</p>
            </div>
          </div>
          
          <div class="feedback-item" v-else>
            <div class="feedback-icon need-improve">
              <i class="fas fa-book-reader"></i>
            </div>
            <div class="feedback-text">
              <h4>需要加强</h4>
              <p>建议从基础开始系统学习，多做练习，积累经验后再进行面试。</p>
            </div>
          </div>
          
          <div class="suggestions">
            <h4>
              <i class="fas fa-lightbulb"></i>
              改进建议
            </h4>
            <ul>
              <li v-for="(suggestion, index) in suggestions" :key="index">
                {{ suggestion }}
              </li>
            </ul>
          </div>
        </div>
      </div>
    </div>

    <div class="detail-actions glass-card">
      <button class="action-btn retry" @click="retryInterview">
        <i class="fas fa-redo"></i>
        再次面试
      </button>
      <button class="action-btn export" @click="exportReport">
        <i class="fas fa-download"></i>
        导出报告
      </button>
      <button class="action-btn share" @click="shareReport">
        <i class="fas fa-share-alt"></i>
        分享成绩
      </button>
    </div>
  </section>
</template>

<script>
import { ref, computed, onMounted } from 'vue'

export default {
  name: 'InterviewDetailPage',
  props: {
    recordId: {
      type: [String, Number],
      default: null
    }
  },
  emits: ['goBack', 'retry', 'showQuestionDetail'],
  setup(props, { emit }) {
    const interviewRecord = ref(null)
    const activeTab = ref('all')

    const questionTabs = [
      { value: 'all', label: '全部' },
      { value: 'correct', label: '正确' },
      { value: 'wrong', label: '错误' },
      { value: 'skipped', label: '跳过' }
    ]

    const dimensions = ref([
      { name: '基础知识', value: 75 },
      { name: '项目经验', value: 68 },
      { name: '算法能力', value: 82 },
      { name: '系统设计', value: 60 },
      { name: '沟通表达', value: 70 },
      { name: '问题分析', value: 78 }
    ])

    const suggestions = ref([
      '加强对系统设计相关知识的学习',
      '多参与实际项目，积累实战经验',
      '练习算法题目，提升编程能力',
      '注意回答的逻辑性和条理性'
    ])

    const filteredAnswers = computed(() => {
      if (!interviewRecord.value?.answers) return []
      
      const answers = interviewRecord.value.answers.map((answer, index) => ({
        ...answer,
        originalIndex: index
      }))
      
      switch (activeTab.value) {
        case 'correct':
          return answers.filter(a => a.score >= 60)
        case 'wrong':
          return answers.filter(a => a.score > 0 && a.score < 60)
        case 'skipped':
          return answers.filter(a => a.score === 0 || a.answer === '[跳过]')
        default:
          return answers
      }
    })

    const loadInterviewRecord = () => {
      const records = JSON.parse(localStorage.getItem('interviewRecords') || '[]')
      
      if (props.recordId) {
        interviewRecord.value = records.find(r => r.id === props.recordId)
      }
      
      if (!interviewRecord.value && records.length > 0) {
        interviewRecord.value = records[0]
      }
      
      if (interviewRecord.value?.answers) {
        updateDimensionsFromAnswers()
      }
    }

    const updateDimensionsFromAnswers = () => {
      const answers = interviewRecord.value.answers
      if (!answers || answers.length === 0) return
      
      const avgScore = answers.reduce((sum, a) => sum + a.score, 0) / answers.length
      
      dimensions.value = [
        { name: '基础知识', value: Math.min(100, Math.round(avgScore * (0.9 + Math.random() * 0.2))) },
        { name: '项目经验', value: Math.min(100, Math.round(avgScore * (0.8 + Math.random() * 0.2))) },
        { name: '算法能力', value: Math.min(100, Math.round(avgScore * (0.85 + Math.random() * 0.2))) },
        { name: '系统设计', value: Math.min(100, Math.round(avgScore * (0.75 + Math.random() * 0.2))) },
        { name: '沟通表达', value: Math.min(100, Math.round(avgScore * (0.88 + Math.random() * 0.15))) },
        { name: '问题分析', value: Math.min(100, Math.round(avgScore * (0.92 + Math.random() * 0.15))) }
      ]
    }

    const getTabCount = (tab) => {
      if (!interviewRecord.value?.answers) return 0
      
      const answers = interviewRecord.value.answers
      
      switch (tab) {
        case 'correct':
          return answers.filter(a => a.score >= 60).length
        case 'wrong':
          return answers.filter(a => a.score > 0 && a.score < 60).length
        case 'skipped':
          return answers.filter(a => a.score === 0 || a.answer === '[跳过]').length
        default:
          return answers.length
      }
    }

    const getScoreClass = (score) => {
      if (score >= 80) return 'high'
      if (score >= 60) return 'medium'
      return 'low'
    }

    const getScoreOffset = (score) => {
      const circumference = 2 * Math.PI * 45
      return circumference - (score / 100) * circumference
    }

    const isKeywordCovered = (answer, keyword) => {
      if (!answer || answer === '[跳过]') return false
      return answer.toLowerCase().includes(keyword.toLowerCase())
    }

    const getGridPoints = (radius) => {
      const points = []
      const angleStep = (2 * Math.PI) / dimensions.value.length
      
      for (let i = 0; i < dimensions.value.length; i++) {
        const angle = angleStep * i - Math.PI / 2
        const x = 150 + radius * Math.cos(angle)
        const y = 150 + radius * Math.sin(angle)
        points.push(`${x},${y}`)
      }
      
      return points.join(' ')
    }

    const getAxisEnd = (index) => {
      const angle = (2 * Math.PI / dimensions.value.length) * index - Math.PI / 2
      return {
        x: 150 + 100 * Math.cos(angle),
        y: 150 + 100 * Math.sin(angle)
      }
    }

    const getLabelPosition = (index) => {
      const angle = (2 * Math.PI / dimensions.value.length) * index - Math.PI / 2
      return {
        x: 150 + 130 * Math.cos(angle),
        y: 150 + 130 * Math.sin(angle) + 5
      }
    }

    const getDataPoints = () => {
      const points = []
      const angleStep = (2 * Math.PI) / dimensions.value.length
      
      for (let i = 0; i < dimensions.value.length; i++) {
        const angle = angleStep * i - Math.PI / 2
        const radius = (dimensions.value[i].value / 100) * 100
        const x = 150 + radius * Math.cos(angle)
        const y = 150 + radius * Math.sin(angle)
        points.push(`${x},${y}`)
      }
      
      return points.join(' ')
    }

    const getDataPoint = (index) => {
      const angle = (2 * Math.PI / dimensions.value.length) * index - Math.PI / 2
      const radius = (dimensions.value[index].value / 100) * 100
      return {
        x: 150 + radius * Math.cos(angle),
        y: 150 + radius * Math.sin(angle)
      }
    }

    const goBack = () => {
      emit('goBack')
    }

    const retryInterview = () => {
      emit('retry', interviewRecord.value)
    }

    const showQuestionDetail = (answer) => {
      emit('showQuestionDetail', {
        recordId: props.recordId || interviewRecord.value?.id,
        questionIndex: answer.originalIndex
      })
    }

    const exportReport = () => {
      const report = {
        title: '面试报告',
        date: interviewRecord.value?.date,
        job: interviewRecord.value?.job,
        score: interviewRecord.value?.score,
        answers: interviewRecord.value?.answers
      }
      
      const blob = new Blob([JSON.stringify(report, null, 2)], { type: 'application/json' })
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `面试报告_${interviewRecord.value?.date?.replace(/[:\s]/g, '-')}.json`
      a.click()
      URL.revokeObjectURL(url)
    }

    const shareReport = () => {
      if (navigator.share) {
        navigator.share({
          title: '我的面试成绩',
          text: `我在AI面试中获得了${interviewRecord.value?.score}分！`,
          url: window.location.href
        })
      } else {
        alert('分享功能暂不支持，请截图分享')
      }
    }

    onMounted(() => {
      loadInterviewRecord()
    })

    return {
      interviewRecord,
      activeTab,
      questionTabs,
      dimensions,
      suggestions,
      filteredAnswers,
      getTabCount,
      getScoreClass,
      getScoreOffset,
      isKeywordCovered,
      getGridPoints,
      getAxisEnd,
      getLabelPosition,
      getDataPoints,
      getDataPoint,
      goBack,
      retryInterview,
      showQuestionDetail,
      exportReport,
      shareReport
    }
  }
}
</script>

<style scoped>
.detail-header {
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
  background: var(--glass-bg);
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-md);
  font-size: 14px;
  color: var(--text-primary);
  cursor: pointer;
  transition: all 0.3s ease;
  font-family: inherit;
}

.back-btn:hover {
  border-color: var(--primary-color);
  color: var(--primary-color);
}

.header-content {
  flex: 1;
}

.page-title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 22px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0 0 4px;
}

.page-title i {
  color: var(--primary-color);
}

.page-subtitle {
  font-size: 14px;
  color: var(--text-secondary);
  margin: 0;
}

.detail-content {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.overview-section,
.analysis-section,
.questions-section,
.feedback-section {
  padding: 24px;
}

.overview-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.overview-header h3 {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
}

.overview-header h3 i {
  color: var(--primary-color);
}

.interview-date {
  font-size: 13px;
  color: var(--text-secondary);
}

.overview-grid {
  display: flex;
  gap: 40px;
  align-items: center;
}

.overview-item.main-score {
  flex-shrink: 0;
}

.score-circle {
  position: relative;
  width: 160px;
  height: 160px;
}

.score-circle svg {
  transform: rotate(-90deg);
  width: 100%;
  height: 100%;
}

.score-bg {
  fill: none;
  stroke: rgba(59, 89, 152, 0.15);
  stroke-width: 8;
}

.score-fill {
  fill: none;
  stroke-width: 8;
  stroke-linecap: round;
  stroke-dasharray: 283;
  transition: stroke-dashoffset 1s ease;
}

.score-circle.high .score-fill {
  stroke: #10b981;
}

.score-circle.medium .score-fill {
  stroke: #f59e0b;
}

.score-circle.low .score-fill {
  stroke: #ef4444;
}

.score-content {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.score-number {
  font-size: 42px;
  font-weight: 700;
  color: var(--text-primary);
  line-height: 1;
}

.score-label {
  font-size: 13px;
  color: var(--text-secondary);
  margin-top: 4px;
}

.overview-stats {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.stat-row {
  display: flex;
  gap: 24px;
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: 1;
}

.stat-item > i {
  width: 36px;
  height: 36px;
  border-radius: var(--radius-sm);
  background: rgba(59, 89, 152, 0.1);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--primary-color);
  font-size: 14px;
}

.stat-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.stat-value {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}

.stat-value.correct {
  color: #10b981;
}

.stat-label {
  font-size: 12px;
  color: var(--text-secondary);
}

.analysis-section h3 {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0 0 24px;
}

.analysis-section h3 i {
  color: var(--primary-color);
}

.analysis-content {
  display: flex;
  gap: 40px;
  align-items: center;
}

.radar-chart-container {
  flex-shrink: 0;
  width: 300px;
  height: 300px;
}

.radar-svg {
  width: 100%;
  height: 100%;
}

.grid-polygon {
  fill: none;
  stroke: rgba(59, 89, 152, 0.15);
  stroke-width: 1;
}

.axis-line {
  stroke: rgba(59, 89, 152, 0.2);
  stroke-width: 1;
}

.data-polygon {
  fill: url(#detailRadarGradient);
  stroke: var(--primary-color);
  stroke-width: 2;
}

.dimension-label {
  font-size: 12px;
  fill: var(--text-secondary);
  text-anchor: middle;
}

.data-point {
  fill: var(--primary-color);
}

.dimension-list {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.dimension-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.dimension-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.dimension-name {
  font-size: 14px;
  color: var(--text-primary);
}

.dimension-score {
  font-size: 14px;
  font-weight: 600;
}

.dimension-score.high {
  color: #10b981;
}

.dimension-score.medium {
  color: #f59e0b;
}

.dimension-score.low {
  color: #ef4444;
}

.dimension-bar {
  height: 8px;
  background: rgba(59, 89, 152, 0.1);
  border-radius: 4px;
  overflow: hidden;
}

.bar-fill {
  height: 100%;
  border-radius: 4px;
  transition: width 0.5s ease;
}

.bar-fill.high {
  background: linear-gradient(90deg, #10b981, #34d399);
}

.bar-fill.medium {
  background: linear-gradient(90deg, #f59e0b, #fbbf24);
}

.bar-fill.low {
  background: linear-gradient(90deg, #ef4444, #f87171);
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.section-header h3 {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
}

.section-header h3 i {
  color: var(--primary-color);
}

.filter-tabs {
  display: flex;
  gap: 8px;
}

.tab-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  background: var(--glass-bg);
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-md);
  font-size: 13px;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all 0.3s ease;
  font-family: inherit;
}

.tab-btn:hover {
  border-color: var(--primary-color);
  color: var(--primary-color);
}

.tab-btn.active {
  background: linear-gradient(135deg, var(--primary-color), var(--primary-light));
  border-color: transparent;
  color: white;
}

.tab-count {
  padding: 2px 6px;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 10px;
  font-size: 11px;
}

.tab-btn:not(.active) .tab-count {
  background: rgba(59, 89, 152, 0.15);
}

.questions-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.question-item {
  padding: 20px;
  cursor: pointer;
  transition: transform 0.2s ease, border-color 0.2s ease;
}

.question-item:hover {
  transform: translateY(-2px);
  border-color: rgba(102, 126, 234, 0.45);
}

.question-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.question-number {
  font-size: 14px;
  font-weight: 600;
  color: var(--primary-color);
}

.question-score {
  padding: 4px 12px;
  border-radius: var(--radius-sm);
  font-size: 13px;
  font-weight: 600;
}

.question-score.high {
  background: rgba(16, 185, 129, 0.15);
  color: #10b981;
}

.question-score.medium {
  background: rgba(245, 158, 11, 0.15);
  color: #f59e0b;
}

.question-score.low {
  background: rgba(239, 68, 68, 0.15);
  color: #ef4444;
}

.question-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.question-text {
  font-size: 15px;
  font-weight: 500;
  color: var(--text-primary);
  line-height: 1.6;
  margin: 0;
}

.answer-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.answer-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.answer-label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--text-secondary);
}

.answer-label i {
  font-size: 11px;
}

.answer-text {
  font-size: 14px;
  color: var(--text-secondary);
  line-height: 1.6;
  margin: 0;
  padding: 12px 16px;
  background: rgba(26, 26, 46, 0.3);
  border-radius: var(--radius-md);
}

.answer-text.empty {
  color: var(--text-light);
  font-style: italic;
}

.keywords-section {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.keywords-label {
  font-size: 12px;
  color: var(--text-secondary);
}

.keywords-list {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.question-actions {
  display: flex;
  justify-content: flex-end;
}

.detail-link-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border: 1px solid rgba(102, 126, 234, 0.25);
  border-radius: var(--radius-sm);
  background: rgba(102, 126, 234, 0.08);
  color: var(--primary-color);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.detail-link-btn:hover {
  background: rgba(102, 126, 234, 0.14);
}

.keyword-tag {
  padding: 4px 10px;
  background: rgba(59, 89, 152, 0.1);
  border-radius: 4px;
  font-size: 12px;
  color: var(--text-secondary);
}

.keyword-tag.covered {
  background: rgba(16, 185, 129, 0.15);
  color: #10b981;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px;
  color: var(--text-secondary);
}

.empty-state i {
  font-size: 40px;
  margin-bottom: 12px;
  opacity: 0.5;
}

.empty-state p {
  font-size: 14px;
  margin: 0;
}

.feedback-section h3 {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0 0 20px;
}

.feedback-section h3 i {
  color: var(--primary-color);
}

.feedback-content {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.feedback-item {
  display: flex;
  gap: 16px;
  padding: 20px;
  background: rgba(26, 26, 46, 0.3);
  border-radius: var(--radius-md);
}

.feedback-icon {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  flex-shrink: 0;
}

.feedback-icon.excellent {
  background: linear-gradient(135deg, #10b981, #059669);
  color: white;
}

.feedback-icon.good {
  background: linear-gradient(135deg, #f59e0b, #d97706);
  color: white;
}

.feedback-icon.need-improve {
  background: linear-gradient(135deg, #667eea, #764ba2);
  color: white;
}

.feedback-text h4 {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0 0 8px;
}

.feedback-text p {
  font-size: 14px;
  color: var(--text-secondary);
  line-height: 1.6;
  margin: 0;
}

.suggestions {
  padding: 20px;
  background: rgba(245, 158, 11, 0.05);
  border: 1px solid rgba(245, 158, 11, 0.15);
  border-radius: var(--radius-md);
}

.suggestions h4 {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: #f59e0b;
  margin: 0 0 12px;
}

.suggestions ul {
  margin: 0;
  padding-left: 20px;
}

.suggestions li {
  font-size: 14px;
  color: var(--text-secondary);
  line-height: 1.8;
}

.detail-actions {
  display: flex;
  justify-content: center;
  gap: 16px;
  padding: 20px 24px;
  margin-top: 24px;
}

.action-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 24px;
  border-radius: var(--radius-md);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s ease;
  font-family: inherit;
  border: none;
}

.action-btn.retry {
  background: linear-gradient(135deg, var(--primary-color), var(--primary-light));
  color: white;
}

.action-btn.retry:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(59, 89, 152, 0.3);
}

.action-btn.export {
  background: var(--glass-bg);
  border: 1px solid var(--glass-border);
  color: var(--text-primary);
}

.action-btn.export:hover {
  border-color: var(--primary-color);
  color: var(--primary-color);
}

.action-btn.share {
  background: rgba(16, 185, 129, 0.1);
  border: 1px solid rgba(16, 185, 129, 0.3);
  color: #10b981;
}

.action-btn.share:hover {
  background: rgba(16, 185, 129, 0.2);
}
</style>
