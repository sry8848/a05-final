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
              <span class="score-in-title">{{ animatedScore }} 分</span>
            </h3>

            <div class="radar-container">
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
              <span class="tag success">表现优秀</span>
              <span class="tag info">基础扎实</span>
              <span class="tag warning">可继续提升</span>
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
              <div v-for="domain in domainWeakSpots" :key="domain.name" class="domain-item">
                <div class="domain-header">
                  <h4>{{ domain.name }}</h4>
                  <span class="score-delta" :class="domain.delta >= 0 ? 'positive' : 'negative'">
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

const RADAR_LABELS = ['专业底层功底', '工程实战经验', '沟通与表达能力', '逻辑分析与解决问题', '场景与架构思维']
const DEFAULT_RADAR_VALUES = [75, 68, 82, 70, 65]

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
  if (!report || !Array.isArray(report.skillDomainScores) || report.skillDomainScores.length === 0) {
    return [...DEFAULT_RADAR_VALUES]
  }
  const values = report.skillDomainScores
    .slice(0, RADAR_LABELS.length)
    .map((item) => {
      const n = Number(item?.score)
      if (!Number.isFinite(n)) return 70
      return Math.max(0, Math.min(100, Math.round(n)))
    })

  while (values.length < RADAR_LABELS.length) {
    values.push(70)
  }
  return values
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
        score: 85,
        correctCount: 8,
        totalQuestions: 10,
        duration: '15:30',
        jobName: '前端开发工程师',
        experienceLabel: '1-3年',
        answers: []
      })
    }
  },
  emits: ['goBack', 'restart', 'showQuestionDetail'],
  setup(props, { emit }) {
    const animatedScore = ref(0)
    const recommendationResources = ref([])
    const recommendationStatus = ref('idle')

    const score = computed(() => props.resultData.score || 85)
    const totalQuestions = computed(() => props.resultData.totalQuestions || 10)
    const duration = computed(() => props.resultData.duration || '15:30')
    const jobName = computed(() => props.resultData.jobName || '前端开发工程师')
    const experienceLabel = computed(() => props.resultData.experienceLabel || '1-3年')
    const answers = computed(() => props.resultData.answers || [])
    const sessionId = computed(() => props.resultData.sessionId || props.resultData?.report?.sessionId || null)

    const radarLabels = RADAR_LABELS
    const radarValues = reactive(buildInitialRadarValues(props.resultData?.report))

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
      const target = score.value
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

      if (score.value >= 85) {
        return '非常棒！你在本次面试中表现出色，技术基础扎实，思路清晰。继续保持这个节奏。'
      }
      if (score.value >= 70) {
        return '整体表现不错，基础掌握较好。建议继续针对薄弱点做重点补强。'
      }
      if (score.value >= 60) {
        return '基本合格，但仍有提升空间。建议系统复盘并做针对性训练。'
      }
      return '本次面试表现还有提升空间。建议先补基础，再做高频题强化。'
    })

    const domainWeakSpots = computed(() => {
      const reportScores = props.resultData?.report?.skillDomainScores
      if (Array.isArray(reportScores) && reportScores.length) {
        return reportScores.slice(0, RADAR_LABELS.length).map((item) => {
          const scoreNum = Number(item?.score)
          const scoreValue = Number.isFinite(scoreNum) ? scoreNum : 70
          const delta = Math.round(scoreValue - 70)
          return {
            name: item?.domainName || item?.domainCode || '通用能力',
            weakPoints: item?.commentary || (scoreValue < 70
              ? '建议围绕核心概念、常见追问和场景题做补强训练。'
              : '本场表现良好，建议持续保持。'),
            delta
          }
        })
      }

      const weakDesc = [
        '加强技术细节理解，确保回答准确完整。',
        '深入学习底层原理，强化工程化落地能力。',
        '扩展关联知识面，建立完整知识网络。',
        '练习结构化表达，先结论再展开。',
        '多做场景题与项目题，强化问题拆解能力。'
      ]

      return RADAR_LABELS.map((name, i) => {
        const v = radarValues[i]
        const delta = v - 70
        return {
          name,
          weakPoints: v < 70 ? weakDesc[i] : '本场表现良好，可继续保持',
          delta
        }
      })
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
      if (normalized === 'answered') return '待评估'
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
        if (status === 'answered') return '本题已回答，评分待生成或待评估。'
        if (status === 'skipped') return '本题已跳过，建议优先补强该知识点。'
        return '本题尚未作答或结果待同步。'
      }

      if (score >= 90) return '回答非常全面，覆盖了关键点，表达结构清晰。'
      if (score >= 75) return '回答较好，主要内容完整，细节还可以再展开。'
      if (score >= 60) return '回答基本正确，但深度和结构还可加强。'
      if (score > 0) return '回答有部分正确内容，建议围绕核心概念重新复盘。'
      return status === 'skipped' ? '本题已跳过，建议优先补强该知识点。' : '本题尚未作答或结果待同步。'
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
      score,
      totalQuestions,
      duration,
      jobName,
      experienceLabel,
      answers,
      radarLabels,
      radarValues,
      radarStyle,
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
