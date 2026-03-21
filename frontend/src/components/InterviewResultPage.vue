<template>
  <div class="result-page">
    <div class="result-container">
      <header class="result-header glass-card">
        <div class="header-left">
          <h1>面试结束</h1>
          <p class="subtitle">{{ jobName }} · 工作年限 {{ experienceLabel }} · 用时 {{ duration }}</p>
        </div>
        <div class="header-right">
          <button class="btn btn-secondary glass-btn" @click="goBack">
            <i class="fas fa-arrow-left"></i>
            返回首页
          </button>
          <button class="btn btn-primary glass-btn" @click="restartInterview">
            <i class="fas fa-redo"></i>
            再来一次
          </button>
        </div>
      </header>

      <div class="result-main">
        <div class="left-section">
          <div class="radar-card glass-card">
            <h3 class="card-title">
              <i class="fas fa-chart-radar"></i>
              面试能力雷达图
              <span class="score-in-title">{{ hasOverallScore ? `${animatedScore} 分` : '待生成' }}</span>
            </h3>

            <div v-if="isProfessionalMode && hasRadarData" class="radar-container">
              <div class="radar-chart">
                <div class="radar-polygon" :style="radarStyle"></div>
                <div class="radar-labels">
                  <span
                    v-for="(label, index) in radarLabels"
                    :key="index"
                    class="label-item"
                    :style="{ color: getRadarColor(index) }"
                  >
                    {{ label }}
                  </span>
                </div>
                <div class="radar-points">
                  <div
                    v-for="(value, index) in radarValues"
                    :key="index"
                    class="radar-point"
                    :style="getPointStyle(index, value)"
                  ></div>
                </div>
              </div>

              <div class="radar-legend">
                <div v-for="(label, index) in radarLabels" :key="index" class="legend-item">
                  <div class="legend-dot" :style="{ background: getRadarColor(index) }"></div>
                  <span class="legend-label">{{ label }}</span>
                  <span class="legend-value">{{ radarValues[index] }}%</span>
                </div>
              </div>
            </div>

            <div v-else class="radar-empty-state">
              <i class="fas fa-chart-radar"></i>
              <p>{{ radarEmptyTitle }}</p>
              <span>{{ radarEmptyDescription }}</span>
            </div>
          </div>

          <div class="summary-card glass-card">
            <h3 class="card-title">
              <i class="fas fa-comment-dots"></i>
              面试官总结评语
            </h3>

            <div class="summary-content">
              <div class="summary-avatar">
                <div class="avatar-icon">
                  <i class="fas fa-robot"></i>
                </div>
              </div>
              <div class="summary-text">
                <p>{{ summaryComment }}</p>
              </div>
            </div>

            <div class="summary-tags">
              <span class="tag info">正式报告</span>
              <span class="tag warning">结果为准</span>
            </div>
          </div>
        </div>

        <div class="right-section">
          <div class="question-list-card glass-card">
            <h3 class="card-title">
              <i class="fas fa-list-alt"></i>
              问题列表
              <span class="question-count">共 {{ totalQuestions }} 题</span>
            </h3>

            <div class="question-list">
              <div
                v-for="(item, index) in answers"
                :key="index"
                class="question-item"
                @click="goToQuestionDetail(index)"
              >
                <div class="question-item-header">
                  <span class="question-num">Q{{ getQuestionNumber(item, index) }}</span>
                  <span class="question-text">{{ getQuestionText(item) }}</span>
                  <span v-if="hasScore(item)" class="item-score" :class="getScoreClass(item.score)">{{ Number(item.score) }} 分</span>
                  <span v-else class="item-status" :class="getStatusClass(item.status)">{{ getStatusText(item.status) }}</span>
                </div>
                <p class="question-comment">{{ getAnswerComment(item) }}</p>
              </div>
            </div>
          </div>

          <div class="blindspot-card glass-card">
            <h3 class="card-title">
              <i class="fas fa-bullseye"></i>
              本场知识域与薄弱点
            </h3>

            <div class="domain-list">
              <div v-if="domainWeakSpots.length === 0" class="domain-empty">
                正式报告暂未提供知识域薄弱点分析。
              </div>
              <div v-for="domain in domainWeakSpots" :key="domain.name" class="domain-item">
                <div class="domain-header">
                  <h4>{{ domain.name }}</h4>
                  <span v-if="domain.delta != null" class="score-delta" :class="domain.delta >= 0 ? 'positive' : 'negative'">
                    {{ domain.delta >= 0 ? '+' : '' }}{{ domain.delta }} 分
                  </span>
                </div>
                <p class="domain-weak">{{ domain.weakPoints }}</p>
              </div>
            </div>
          </div>

          <div class="recommend-card glass-card">
            <h3 class="card-title">
              <i class="fas fa-lightbulb"></i>
              智能推荐
            </h3>

            <div class="recommend-list">
              <div v-for="r in recommendResources" :key="r.id" class="recommend-item">
                <i :class="r.icon" class="recommend-icon"></i>
                <div class="recommend-content">
                  <h4>{{ r.title }}</h4>
                  <p>{{ r.desc }}</p>
                  <a :href="r.link" target="_blank" rel="noopener" class="recommend-link">
                    查看 <i class="fas fa-external-link-alt"></i>
                  </a>
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
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { getLearningRecommendations } from '../api/resume'

const RADAR_DIMENSIONS = [
  { key: 'fundamentals', label: '基础原理掌握' },
  { key: 'engineering_practice', label: '工程实践与项目落地' },
  { key: 'scenario_tradeoff', label: '场景分析与方案取舍' },
  { key: 'debugging', label: '问题定位与排查思路' },
  { key: 'communication', label: '沟通表达与结构化呈现' }
]

const FALLBACK_RECOMMENDATIONS = [
  { id: 'fallback-1', title: 'MDN Web 文档', desc: '前端权威参考，建议常查。', icon: 'fas fa-book', link: 'https://developer.mozilla.org/zh-CN/' },
  { id: 'fallback-2', title: 'Vue 官方文档', desc: 'Vue 3 组合式 API 与最佳实践。', icon: 'fas fa-code', link: 'https://cn.vuejs.org/' },
  { id: 'fallback-3', title: '前端面试题精选', desc: '按知识域分类整理，适合复盘补强。', icon: 'fas fa-list-ul', link: 'https://www.google.com/search?q=%E5%89%8D%E7%AB%AF+%E9%9D%A2%E8%AF%95%E9%A2%98+%E7%B2%BE%E9%80%89' }
]

const RESOURCE_ICON_MAP = {
  practice: 'fas fa-dumbbell',
  article: 'fas fa-book',
  course: 'fas fa-graduation-cap',
  project: 'fas fa-laptop-code'
}

function buildInitialRadarValues(report) {
  const radarScores = Array.isArray(report?.comprehensiveRadarScores) ? report.comprehensiveRadarScores : []
  const scoreMap = new Map()
  radarScores.forEach((item) => {
    if (item?.dimensionKey) {
      scoreMap.set(item.dimensionKey, Number(item?.score))
    }
  })

  return RADAR_DIMENSIONS.map((dimension) => {
    const score = scoreMap.get(dimension.key)
    if (!Number.isFinite(score)) return 0
    return Math.max(0, Math.min(100, Math.round(score)))
  })
}

function mapRecommendationItem(item, section, index) {
  const title = item?.title || '学习建议'
  const reason = item?.reason || '建议结合本场面试薄弱点进行复盘。'
  const duration = Number(item?.estimatedMinutes)
  const suffix = Number.isFinite(duration) && duration > 0 ? ` · 约${duration}分钟` : ''
  const sectionTag = section?.sectionTitle ? `【${section.sectionTitle}】` : ''

  return {
    id: item?.itemId || `${section?.sectionKey || 'section'}-${index + 1}`,
    title: `${sectionTag}${title}`,
    desc: `${reason}${suffix}`,
    icon: RESOURCE_ICON_MAP[item?.resourceType] || 'fas fa-lightbulb',
    link: item?.link || '#'
  }
}

export default {
  name: 'InterviewResultPage',
  props: {
    resultData: {
      type: Object,
      default: () => ({
        score: null,
        correctCount: null,
        totalQuestions: 0,
        duration: '--',
        jobName: '模拟面试',
        experienceLabel: '',
        answers: []
      })
    }
  },
  emits: ['goBack', 'restart', 'showQuestionDetail'],
  setup(props, { emit }) {
    const animatedScore = ref(0)
    const recommendationResources = ref([])
    const recommendationStatus = ref('idle')

    const score = computed(() => {
      const numeric = Number(props.resultData?.score)
      return Number.isFinite(numeric) ? Math.round(numeric) : null
    })
    const hasOverallScore = computed(() => score.value != null)
    const totalQuestions = computed(() => {
      const numeric = Number(props.resultData?.totalQuestions)
      if (Number.isFinite(numeric) && numeric > 0) return Math.round(numeric)
      return Array.isArray(props.resultData?.answers) ? props.resultData.answers.length : 0
    })
    const duration = computed(() => props.resultData.duration || '--')
    const jobName = computed(() => props.resultData.jobName || '模拟面试')
    const experienceLabel = computed(() => props.resultData.experienceLabel || '未知')
    const answers = computed(() => props.resultData.answers || [])
    const sessionId = computed(() => props.resultData.sessionId || props.resultData?.report?.sessionId || null)

    const radarLabels = RADAR_DIMENSIONS.map((item) => item.label)
    const radarValues = reactive(buildInitialRadarValues(props.resultData?.report))
    const syncRadarValues = (report) => {
      const nextValues = buildInitialRadarValues(report)
      nextValues.forEach((value, index) => {
        radarValues[index] = value
      })
    }
    const interviewMode = computed(() =>
      props.resultData?.report?.mode || props.resultData?.interviewMode || props.resultData?.mode || 'practice'
    )
    const isProfessionalMode = computed(() => String(interviewMode.value).toLowerCase() === 'professional')
    const hasRadarData = computed(() =>
      isProfessionalMode.value && radarValues.some((value) => Number(value) > 0)
    )

    const loadRecommendations = async () => {
      if (!sessionId.value) {
        recommendationResources.value = []
        recommendationStatus.value = 'fallback'
        return
      }
      recommendationStatus.value = 'loading'
      try {
        const data = await getLearningRecommendations(sessionId.value)
        if (data?.recommendationStatus !== 'ready' || !Array.isArray(data.sections)) {
          recommendationResources.value = []
          recommendationStatus.value = data?.recommendationStatus || 'fallback'
          return
        }

        const list = []
        data.sections.forEach((section) => {
          const items = Array.isArray(section?.items) ? section.items : []
          items.forEach((item, idx) => {
            list.push(mapRecommendationItem(item, section, idx))
          })
        })

        recommendationResources.value = list
        recommendationStatus.value = list.length ? 'ready' : 'fallback'
      } catch (err) {
        recommendationResources.value = []
        recommendationStatus.value = 'fallback'
        console.warn('[InterviewResultPage] failed to load learning recommendations', err)
      }
    }

    onMounted(() => {
      const target = score.value ?? 0
      const dur = 1500
      const start = Date.now()
      const step = () => {
        const elapsed = Date.now() - start
        const progress = Math.min(elapsed / dur, 1)
        const easeOut = 1 - Math.pow(1 - progress, 3)
        animatedScore.value = Math.round(target * easeOut)
        if (progress < 1) requestAnimationFrame(step)
      }
      requestAnimationFrame(step)
      loadRecommendations()
    })

    watch(sessionId, () => {
      loadRecommendations()
    })

    watch(() => props.resultData?.report, (report) => {
      syncRadarValues(report)
    }, { deep: true })

    const radarStyle = computed(() => {
      const points = radarValues.map((v, i) => {
        const angle = (i * 72 - 90) * Math.PI / 180
        const r = v * 0.4
        return `${50 + r * Math.cos(angle)}% ${50 + r * Math.sin(angle)}%`
      })
      return { clipPath: `polygon(${points.join(', ')})` }
    })

    const getPointStyle = (index, value) => {
      const angle = (index * 72 - 90) * Math.PI / 180
      const r = value * 0.4
      return {
        left: `${50 + r * Math.cos(angle)}%`,
        top: `${50 + r * Math.sin(angle)}%`
      }
    }

    const getRadarColor = (index) => {
      const colors = ['#667eea', '#f093fb', '#4facfe', '#43e97b', '#fa709a']
      return colors[index]
    }

    const summaryComment = computed(() => {
      const reportSummary = props.resultData?.report?.summary
      if (reportSummary) return reportSummary
      return '正式报告摘要暂未生成，请以后端报告内容为准。'
    })

    const radarEmptyTitle = computed(() => (
      isProfessionalMode.value ? '本场专业模式雷达暂未生成' : '练习模式不计算面试能力雷达'
    ))
    const radarEmptyDescription = computed(() => (
      isProfessionalMode.value
        ? '报告已生成，但当前缺少足够的综合能力评分数据，请稍后刷新或重新生成报告。'
        : '练习模式只产出题目与知识域层面的复盘，不计算 5 维面试能力评分。'
    ))

    const domainWeakSpots = computed(() => {
      const reportScores = props.resultData?.report?.skillDomainScores
      if (Array.isArray(reportScores) && reportScores.length) {
        return reportScores.slice(0, RADAR_DIMENSIONS.length).map((item) => {
          const scoreNum = Number(item?.score)
          const hasNumericScore = Number.isFinite(scoreNum)
          return {
            name: item?.domainName || item?.domainCode || '通用能力',
            weakPoints: String(item?.commentary || '').trim() || '正式报告未提供该知识域点评。',
            delta: hasNumericScore ? Math.round(scoreNum - 70) : null
          }
        })
      }
      return []
    })

    const recommendResources = computed(() => {
      if (recommendationResources.value.length) {
        return recommendationResources.value
      }
      return FALLBACK_RECOMMENDATIONS
    })

    const normalizeAnswerStatus = (status) => {
      const normalized = String(status || '').trim().toLowerCase()
      if (['answered', 'skipped', 'pending'].includes(normalized)) {
        return normalized
      }
      return 'pending'
    }

    const hasScore = (item) => {
      if (!item || item.score == null) return false
      return Number.isFinite(Number(item.score))
    }

    const getQuestionText = (item) => {
      return item?.questionStem || item?.question || '未命名题目'
    }

    const getQuestionNumber = (item, index) => {
      const numeric = Number(item?.questionNo)
      if (Number.isInteger(numeric) && numeric > 0) return numeric
      return index + 1
    }

    const getStatusClass = (status) => {
      return normalizeAnswerStatus(status)
    }

    const getStatusText = (status) => {
      const normalized = normalizeAnswerStatus(status)
      if (normalized === 'answered') return '已完成'
      if (normalized === 'skipped') return '已跳过'
      return '待同步'
    }

    const getScoreClass = (s) => {
      const score = Number(s)
      if (!Number.isFinite(score)) return 'medium'
      if (score >= 80) return 'high'
      if (score >= 60) return 'medium'
      return 'low'
    }

    const getAnswerComment = (item) => {
      const commentary = String(item?.commentary || '').trim()
      if (commentary) return commentary

      const status = normalizeAnswerStatus(item?.status)
      const score = Number(item?.score)
      const hasNumericScore = Number.isFinite(score)
      if (!hasNumericScore) {
        if (status === 'answered') return '本题复盘信息暂时缺失，请稍后重试或查看单题详情。'
        if (status === 'skipped') return '本题已跳过，建议优先补强该知识点。'
        return '本题尚未作答或结果待同步。'
      }

      return '本题正式评分已生成，但当前缺少单题点评文案，请查看正式报告或稍后重试。'
    }

    const goToQuestionDetail = (index) => {
      const answer = answers.value[index] || null
      emit('showQuestionDetail', {
        index,
        questionId: answer?.questionId ?? null,
        sessionId: sessionId.value ?? null
      })
    }

    const goBack = () => emit('goBack')
    const restartInterview = () => emit('restart')

    return {
      animatedScore,
      hasOverallScore,
      score,
      totalQuestions,
      duration,
      jobName,
      experienceLabel,
      answers,
      isProfessionalMode,
      hasRadarData,
      radarLabels,
      radarValues,
      radarStyle,
      radarEmptyTitle,
      radarEmptyDescription,
      summaryComment,
      domainWeakSpots,
      recommendResources,
      recommendationStatus,
      hasScore,
      getQuestionText,
      getQuestionNumber,
      getStatusClass,
      getStatusText,
      getPointStyle,
      getRadarColor,
      getScoreClass,
      getAnswerComment,
      goToQuestionDetail,
      goBack,
      restartInterview
    }
  }
}
</script>

<style scoped>
.result-page {
  min-height: 100vh;
  position: relative;
  padding: 20px;
}

.result-container {
  max-width: 1400px;
  margin: 0 auto;
  position: relative;
  z-index: 1;
}

.result-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px 24px;
  margin-bottom: 20px;
}

.header-left h1 {
  font-size: 24px;
  font-weight: 700;
  color: var(--text-primary);
  margin-bottom: 4px;
}

.header-left .subtitle {
  font-size: 14px;
  color: var(--text-secondary);
}

.header-right {
  display: flex;
  gap: 12px;
}

.result-main {
  display: flex;
  gap: 20px;
}

.left-section {
  width: 380px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.right-section {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.card-title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 20px;
}

.card-title i {
  color: var(--primary-color);
}

.score-in-title {
  margin-left: auto;
  font-size: 18px;
  font-weight: 700;
  color: var(--primary-color);
}

.question-count {
  margin-left: auto;
  font-size: 13px;
  font-weight: 400;
  color: var(--text-secondary);
}

.radar-card {
  padding: 24px;
}

.radar-container {
  display: flex;
  align-items: center;
  gap: 24px;
  flex-wrap: wrap;
}

.radar-chart {
  position: relative;
  width: 200px;
  height: 200px;
}

.radar-polygon {
  position: absolute;
  inset: 10%;
  background: rgba(59, 89, 152, 0.1);
  border: 2px solid var(--primary-color);
  transition: clip-path 1s ease-out;
}

.radar-labels {
  position: absolute;
  inset: 0;
}

.label-item {
  position: absolute;
  font-size: 11px;
  color: var(--text-secondary);
  white-space: nowrap;
  transform: translate(-50%, -50%);
}

.label-item:nth-child(1) { top: 0; left: 50%; }
.label-item:nth-child(2) { top: 19%; right: 0; left: auto; transform: translate(0, -50%); }
.label-item:nth-child(3) { bottom: 0; right: 0; left: auto; transform: translate(0, 0); }
.label-item:nth-child(4) { bottom: 0; left: 0; transform: translate(0, 0); }
.label-item:nth-child(5) { top: 19%; left: 0; transform: translate(0, -50%); }

.radar-points {
  position: absolute;
  inset: 0;
}

.radar-point {
  position: absolute;
  width: 10px;
  height: 10px;
  background: var(--primary-color);
  border-radius: 50%;
  transform: translate(-50%, -50%);
  box-shadow: 0 0 10px var(--primary-color);
}

.radar-legend {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.radar-empty-state {
  min-height: 260px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  text-align: center;
  gap: 10px;
  color: var(--text-secondary);
}

.radar-empty-state i {
  font-size: 28px;
  color: var(--primary-color);
}

.radar-empty-state p {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
}

.radar-empty-state span {
  max-width: 300px;
  font-size: 13px;
  line-height: 1.7;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
}

.legend-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.legend-label {
  flex: 1;
  color: var(--text-secondary);
}

.legend-value {
  font-weight: 600;
  color: var(--text-primary);
}

.summary-card {
  padding: 24px;
}

.summary-content {
  display: flex;
  gap: 16px;
  margin-bottom: 16px;
}

.summary-avatar {
  flex-shrink: 0;
}

.avatar-icon {
  width: 48px;
  height: 48px;
  background: linear-gradient(135deg, var(--primary-color), var(--primary-light));
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 20px;
}

.summary-text p {
  font-size: 14px;
  line-height: 1.8;
  color: var(--text-primary);
}

.summary-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.tag {
  padding: 4px 12px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 500;
}

.tag.success { background: rgba(16, 185, 129, 0.15); color: #10b981; }
.tag.info { background: rgba(59, 89, 152, 0.15); color: var(--primary-color); }
.tag.warning { background: rgba(245, 158, 11, 0.15); color: #f59e0b; }

.question-list-card {
  padding: 24px;
}

.question-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-height: 420px;
  overflow-y: auto;
}

.question-item {
  padding: 16px;
  background: var(--glass-bg);
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: all var(--transition-normal);
}

.question-item:hover {
  border-color: var(--primary-color);
}

.question-item-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}

.question-num {
  padding: 4px 10px;
  background: rgba(59, 89, 152, 0.15);
  border-radius: 4px;
  font-size: 12px;
  font-weight: 500;
  color: var(--primary-color);
  white-space: nowrap;
}

.question-text {
  flex: 1;
  font-size: 14px;
  font-weight: 500;
  color: var(--text-primary);
  min-width: 0;
}

.item-score {
  padding: 4px 12px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 600;
  flex-shrink: 0;
}

.item-score.high { background: rgba(16, 185, 129, 0.15); color: #10b981; }
.item-score.medium { background: rgba(245, 158, 11, 0.15); color: #f59e0b; }
.item-score.low { background: rgba(239, 68, 68, 0.15); color: #ef4444; }

.item-status {
  padding: 4px 12px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 600;
  flex-shrink: 0;
}

.item-status.answered { background: rgba(59, 130, 246, 0.15); color: #3b82f6; }
.item-status.skipped { background: rgba(245, 158, 11, 0.15); color: #f59e0b; }
.item-status.pending { background: rgba(148, 163, 184, 0.18); color: #64748b; }

.question-comment {
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.6;
  margin: 0;
  padding-left: 0;
}

.blindspot-card {
  padding: 24px;
}

.domain-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.domain-item {
  padding: 16px;
  background: var(--glass-bg);
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-md);
}

.domain-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.domain-header h4 {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
}

.score-delta {
  font-size: 14px;
  font-weight: 600;
}

.score-delta.positive { color: #10b981; }
.score-delta.negative { color: #ef4444; }

.domain-empty {
  padding: 14px 16px;
  border-radius: 16px;
  background: rgba(148, 163, 184, 0.12);
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.6;
}

.domain-weak {
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.5;
  margin: 0;
}

.recommend-card {
  padding: 24px;
}

.recommend-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.recommend-item {
  display: flex;
  gap: 16px;
  padding: 14px;
  background: var(--glass-bg);
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-md);
}

.recommend-icon {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  color: var(--primary-color);
  flex-shrink: 0;
}

.recommend-content h4 {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0 0 4px 0;
}

.recommend-content p {
  font-size: 13px;
  color: var(--text-secondary);
  margin: 0 0 8px 0;
}

.recommend-link {
  font-size: 13px;
  color: var(--primary-color);
  text-decoration: none;
}

.recommend-link:hover {
  text-decoration: underline;
}
</style>
