<template>
  <!-- 面试结果/报告页面，展示面试完成后的综合评估结果 -->
  <div class="result-page">
    <div class="result-container">
      <!-- 顶部标题和操作区域 -->
      <header class="result-header glass-card">
        <div class="header-left">
          <h1>面试结束</h1>
          <p class="subtitle">{{ jobName }} · 工作年限 {{ experienceLabel }} · 用时 {{ duration }}</p>
        </div>
        <div class="header-right">
          <!-- 返回首页按钮 -->
          <button class="btn btn-secondary glass-btn" @click="goBack">
            <i class="fas fa-arrow-left"></i>
            返回首页
          </button>
          <!-- 再来一次/重新面试按钮 -->
          <button class="btn btn-primary glass-btn" @click="restartInterview">
            <i class="fas fa-redo"></i>
            再来一次
          </button>
        </div>
      </header>

      <!-- 主要内容区域 -->
      <div class="result-main">
        <!-- 左侧区域：雷达图和面试官评语 -->
        <div class="left-section">
          <!-- 能力雷达图卡片 -->
          <div class="radar-card glass-card">
            <h3 class="card-title">
              <i class="fas fa-chart-radar"></i>
              面试能力雷达图
              <span class="score-in-title">{{ hasOverallScore ? `${animatedScore} 分` : '待生成' }}</span>
            </h3>

            <!-- 雷达图展示（专业模式且有数据时显示） -->
            <div v-if="isProfessionalMode && hasRadarData" class="radar-container">
              <div class="radar-chart">
                <!-- 雷达多边形区域 -->
                <div class="radar-polygon" :style="radarStyle"></div>
                <!-- 维度标签 -->
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
                <!-- 数据点位置 -->
                <div class="radar-points">
                  <div
                    v-for="(value, index) in radarValues"
                    :key="index"
                    class="radar-point"
                    :style="getPointStyle(index, value)"
                  ></div>
                </div>
              </div>

              <!-- 雷达图图例 -->
              <div class="radar-legend">
                <div v-for="(label, index) in radarLabels" :key="index" class="legend-item">
                  <div class="legend-dot" :style="{ background: getRadarColor(index) }"></div>
                  <span class="legend-label">{{ label }}</span>
                  <span class="legend-value">{{ radarValues[index] }}%</span>
                </div>
              </div>
            </div>

            <!-- 雷达图空状态（非专业模式或无数据时显示） -->
            <div v-else class="radar-empty-state">
              <i class="fas fa-chart-radar"></i>
              <p>{{ radarEmptyTitle }}</p>
              <span>{{ radarEmptyDescription }}</span>
            </div>
          </div>

          <!-- 面试官总结评语卡片 -->
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

        <!-- 右侧区域：问题列表、薄弱点、学习推荐 -->
        <div class="right-section">
          <!-- 面试问题列表卡片 -->
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

          <!-- 知识域薄弱点分析卡片 -->
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
                  <span v-if="domain.scoreText" class="domain-score">
                    {{ domain.scoreText }}
                  </span>
                </div>
                <p class="domain-weak">{{ domain.weakPoints }}</p>
              </div>
            </div>
          </div>

          <!-- 学习资源推荐卡片 -->
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
import {
  getInterviewQuestionCommentaryFallback,
  getInterviewQuestionStatusText,
  mapInterviewReportSkillDomains
} from '../utils/interviewResultDisplay'

/**
 * 雷达图5个能力维度定义
 * 对应5角雷达图的每个维度
 */
const RADAR_DIMENSIONS = [
  { key: 'fundamentals', label: '基础原理掌握' },
  { key: 'engineering_practice', label: '工程实践与项目落地' },
  { key: 'scenario_tradeoff', label: '场景分析与方案取舍' },
  { key: 'debugging', label: '问题定位与排查思路' },
  { key: 'communication', label: '沟通表达与结构化呈现' }
]

/**
 * 学习推荐备用数据
 * 当无法从后端获取推荐数据时显示的默认推荐
 */
const FALLBACK_RECOMMENDATIONS = [
  { id: 'fallback-1', title: 'MDN Web 文档', desc: '前端权威参考，建议常查。', icon: 'fas fa-book', link: 'https://developer.mozilla.org/zh-CN/' },
  { id: 'fallback-2', title: 'Vue 官方文档', desc: 'Vue 3 组合式 API 与最佳实践。', icon: 'fas fa-code', link: 'https://cn.vuejs.org/' },
  { id: 'fallback-3', title: '前端面试题精选', desc: '按知识域分类整理，适合复盘补强。', icon: 'fas fa-list-ul', link: 'https://www.google.com/search?q=%E5%89%8D%E7%AB%AF+%E9%9D%A2%E8%AF%95%E9%A2%98+%E7%B2%BE%E9%80%89' }
]

/**
 * 推荐资源图标映射
 * 将资源类型映射为对应的 FontAwesome 图标
 */
const RESOURCE_ICON_MAP = {
  practice: 'fas fa-dumbbell',
  article: 'fas fa-book',
  course: 'fas fa-graduation-cap',
  project: 'fas fa-laptop-code'
}

/**
 * 根据报告数据构建雷达图数值
 * 将后端返回的综合能力评分映射为雷达图5个维度的百分比值
 * 
 * @param {Object} report - 面试报告数据
 * @returns {number[]} 雷达图5个维度的数值数组，范围0-100
 */
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

/**
 * 构建推荐资源项
 * 将后端返回的推荐数据格式化为前端展示所需格式
 * 
 * @param {Object} item - 单个推荐项数据
 * @param {Object} section - 所属推荐分类
 * @param {number} index - 在分类中的索引
 * @returns {Object} 格式化后的推荐项
 */
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

/**
 * 面试结果页面组件
 * 
 * 本组件负责展示面试完成后的综合评估报告，包括：
 * - 面试能力5维雷达图（仅专业模式）
 * - 面试官整体总结评语
 * - 面试问题列表及得分
 * - 知识域薄弱点分析
 * - 智能学习资源推荐
 * 
 * 提供返回首页、重新面试、查看题目详情等操作
 * 
 * @component
 */
export default {
  name: 'InterviewResultPage',
  props: {
    /**
     * 面试结果数据对象
     * 包含面试报告、问题列表、得分等信息
     * @type {Object}
     */
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
    // ==================== 响应式状态 ====================
    
    /** 总分动画数值，用于分数从0到实际值的过渡动画 */
    const animatedScore = ref(0)
    
    /** 学习推荐资源列表 */
    const recommendationResources = ref([])
    
    /** 学习推荐加载状态 */
    const recommendationStatus = ref('idle')

    // ==================== 计算属性 ====================

    /**
     * 面试总分（数值化）
     * @returns {number|null} 面试总分，null表示无分数
     */
    const score = computed(() => {
      const numeric = Number(props.resultData?.score)
      return Number.isFinite(numeric) ? Math.round(numeric) : null
    })

    /**
     * 是否有总分数据
     * @returns {boolean}
     */
    const hasOverallScore = computed(() => score.value != null)

    /**
     * 总题数
     * @returns {number}
     */
    const totalQuestions = computed(() => {
      const numeric = Number(props.resultData?.totalQuestions)
      if (Number.isFinite(numeric) && numeric > 0) return Math.round(numeric)
      return Array.isArray(props.resultData?.answers) ? props.resultData.answers.length : 0
    })

    /**
     * 面试时长
     * @returns {string}
     */
    const duration = computed(() => props.resultData.duration || '--')

    /**
     * 岗位名称
     * @returns {string}
     */
    const jobName = computed(() => props.resultData.jobName || '模拟面试')

    /**
     * 工作年限标签
     * @returns {string}
     */
    const experienceLabel = computed(() => props.resultData.experienceLabel || '未知')

    /**
     * 回答列表
     * @returns {Array}
     */
    const answers = computed(() => props.resultData.answers || [])

    /**
     * 面试会话ID
     * @returns {string|null}
     */
    const sessionId = computed(() => props.resultData.sessionId || props.resultData?.report?.sessionId || null)

    /**
     * 雷达图标签数组
     * @returns {string[]}
     */
    const radarLabels = RADAR_DIMENSIONS.map((item) => item.label)

    /**
     * 雷达图数值响应式对象
     * @type {number[]}
     */
    const radarValues = reactive(buildInitialRadarValues(props.resultData?.report))

    /**
     * 同步雷达图数值
     * @param {Object} report - 新的报告数据
     */
    const syncRadarValues = (report) => {
      const nextValues = buildInitialRadarValues(report)
      nextValues.forEach((value, index) => {
        radarValues[index] = value
      })
    }

    /**
     * 面试模式
     * @returns {string} 'practice' 或 'professional'
     */
    const interviewMode = computed(() =>
      props.resultData?.report?.mode || props.resultData?.interviewMode || props.resultData?.mode || 'practice'
    )

    /**
     * 是否为专业模式
     * @returns {boolean}
     */
    const isProfessionalMode = computed(() => String(interviewMode.value).toLowerCase() === 'professional')

    /**
     * 是否有雷达图数据
     * @returns {boolean}
     */
    const hasRadarData = computed(() =>
      isProfessionalMode.value && radarValues.some((value) => Number(value) > 0)
    )

    // ==================== 方法定义 ====================

    /**
     * 加载学习推荐数据
     * 从后端获取针对本场面试的个性化学习推荐
     */
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

    // ==================== 生命周期钩子 ====================

    onMounted(() => {
      // 启动总分动画效果
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
      
      // 加载学习推荐
      loadRecommendations()
    })

    // ==================== 监听器 ====================

    /**
     * 监听会话ID变化，重新加载推荐
     */
    watch(sessionId, () => {
      loadRecommendations()
    })

    /**
     * 监听报告数据变化，同步雷达图数值
     */
    watch(() => props.resultData?.report, (report) => {
      syncRadarValues(report)
    }, { deep: true })

    // ==================== 雷达图相关计算属性 ====================

    /**
     * 雷达图多边形样式
     * 计算5个顶点的位置，用于clip-path属性
     * @returns {Object}
     */
    const radarStyle = computed(() => {
      const points = radarValues.map((v, i) => {
        const angle = (i * 72 - 90) * Math.PI / 180
        const r = v * 0.4
        return `${50 + r * Math.cos(angle)}% ${50 + r * Math.sin(angle)}%`
      })
      return { clipPath: `polygon(${points.join(', ')})` }
    })

    /**
     * 获取雷达图数据点样式
     * @param {number} index - 维度索引
     * @param {number} value - 维度值
     * @returns {Object}
     */
    const getPointStyle = (index, value) => {
      const angle = (index * 72 - 90) * Math.PI / 180
      const r = value * 0.4
      return {
        left: `${50 + r * Math.cos(angle)}%`,
        top: `${50 + r * Math.sin(angle)}%`
      }
    }

    /**
     * 获取雷达图维度颜色
     * @param {number} index - 维度索引
     * @returns {string}
     */
    const getRadarColor = (index) => {
      const colors = ['#667eea', '#f093fb', '#4facfe', '#43e97b', '#fa709a']
      return colors[index]
    }

    // ==================== 其他计算属性 ====================

    /**
     * 面试官总结评语
     * @returns {string}
     */
    const summaryComment = computed(() => {
      const reportSummary = props.resultData?.report?.summary
      if (reportSummary) return reportSummary
      return '正式报告摘要暂未生成，请以后端报告内容为准。'
    })

    /**
     * 雷达图空状态标题
     * @returns {string}
     */
    const radarEmptyTitle = computed(() => (
      isProfessionalMode.value ? '本场专业模式雷达暂未生成' : '练习模式不计算面试能力雷达'
    ))

    /**
     * 雷达图空状态描述
     * @returns {string}
     */
    const radarEmptyDescription = computed(() => (
      isProfessionalMode.value
        ? '报告已生成，但当前缺少足够的综合能力评分数据，请稍后刷新或重新生成报告。'
        : '练习模式只产出题目与知识域层面的复盘，不计算 5 维面试能力评分。'
    ))

    /**
     * 知识域薄弱点数据
     * @returns {Array}
     */
    const domainWeakSpots = computed(() => {
      const reportScores = props.resultData?.report?.skillDomainScores
      if (Array.isArray(reportScores) && reportScores.length) {
        return mapInterviewReportSkillDomains(reportScores.slice(0, RADAR_DIMENSIONS.length))
      }
      return []
    })

    /**
     * 推荐资源列表（包含备用数据）
     * @returns {Array}
     */
    const recommendResources = computed(() => {
      if (recommendationResources.value.length) {
        return recommendationResources.value
      }
      return FALLBACK_RECOMMENDATIONS
    })

    // ==================== 问题列表相关工具方法 ====================

    /**
     * 规范化回答状态
     * @param {string} status - 原始状态
     * @returns {string}
     */
    const normalizeAnswerStatus = (status) => {
      const normalized = String(status || '').trim().toLowerCase()
      if (['answered', 'skipped', 'pending'].includes(normalized)) {
        return normalized
      }
      return 'pending'
    }

    /**
     * 检查是否有分数
     * @param {Object} item - 问题项
     * @returns {boolean}
     */
    const hasScore = (item) => {
      if (!item || item.score == null) return false
      return Number.isFinite(Number(item.score))
    }

    /**
     * 获取问题文本
     * @param {Object} item - 问题项
     * @returns {string}
     */
    const getQuestionText = (item) => {
      return item?.questionStem || item?.question || '未命名题目'
    }

    /**
     * 获取问题编号
     * @param {Object} item - 问题项
     * @param {number} index - 索引
     * @returns {number}
     */
    const getQuestionNumber = (item, index) => {
      const numeric = Number(item?.questionNo)
      if (Number.isInteger(numeric) && numeric > 0) return numeric
      return index + 1
    }

    /**
     * 获取状态CSS类名
     * @param {string} status - 状态
     * @returns {string}
     */
    const getStatusClass = (status) => {
      return normalizeAnswerStatus(status)
    }

    /**
     * 获取状态显示文本
     * @param {string} status - 状态
     * @returns {string}
     */
    const getStatusText = (status) => {
      return getInterviewQuestionStatusText(status)
    }

    /**
     * 获取分数CSS类名
     * @param {number} s - 分数
     * @returns {string}
     */
    const getScoreClass = (s) => {
      const score = Number(s)
      if (!Number.isFinite(score)) return 'medium'
      if (score >= 80) return 'high'
      if (score >= 60) return 'medium'
      return 'low'
    }

    /**
     * 获取回答评语
     * @param {Object} item - 问题项
     * @returns {string}
     */
    const getAnswerComment = (item) => {
      return getInterviewQuestionCommentaryFallback(item)
    }

    // ==================== 事件处理方法 ====================

    /**
     * 跳转到题目详情
     * @param {number} index - 问题索引
     */
    const goToQuestionDetail = (index) => {
      const answer = answers.value[index] || null
      emit('showQuestionDetail', {
        index,
        questionId: answer?.questionId ?? null,
        sessionId: sessionId.value ?? null
      })
    }

    /**
     * 返回首页
     */
    const goBack = () => emit('goBack')
    
    /**
     * 重新面试
     */
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
/**
 * 页面容器样式
 */
.result-page {
  min-height: 100vh;
  position: relative;
  padding: 20px;
}

/**
 * 结果页主容器
 */
.result-container {
  max-width: 1400px;
  margin: 0 auto;
  position: relative;
  z-index: 1;
}

/**
 * 顶部标题栏
 */
.result-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px 24px;
  margin-bottom: 20px;
}

/**
 * 左侧标题区域
 */
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

/**
 * 右侧按钮区域
 */
.header-right {
  display: flex;
  gap: 12px;
}

/**
 * 主体内容区域
 */
.result-main {
  display: flex;
  gap: 20px;
}

/**
 * 左侧内容区域
 */
.left-section {
  width: 380px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/**
 * 右侧内容区域
 */
.right-section {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/**
 * 卡片标题样式
 */
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

/**
 * 标题中的分数
 */
.score-in-title {
  margin-left: auto;
  font-size: 18px;
  font-weight: 700;
  color: var(--primary-color);
}

/**
 * 标题中的题数
 */
.question-count {
  margin-left: auto;
  font-size: 13px;
  font-weight: 400;
  color: var(--text-secondary);
}

/**
 * 雷达图卡片
 */
.radar-card {
  padding: 24px;
}

/**
 * 雷达图容器
 */
.radar-container {
  display: flex;
  align-items: center;
  gap: 24px;
  flex-wrap: wrap;
}

/**
 * 雷达图图表区域
 */
.radar-chart {
  position: relative;
  width: 200px;
  height: 200px;
}

/**
 * 雷达图多边形区域
 */
.radar-polygon {
  position: absolute;
  inset: 10%;
  background: rgba(59, 89, 152, 0.1);
  border: 2px solid var(--primary-color);
  transition: clip-path 1s ease-out;
}

/**
 * 雷达图标签容器
 */
.radar-labels {
  position: absolute;
  inset: 0;
}

/**
 * 单个雷达图标签
 */
.label-item {
  position: absolute;
  font-size: 11px;
  color: var(--text-secondary);
  white-space: nowrap;
  transform: translate(-50%, -50%);
}

/* 5个标签的定位 */
.label-item:nth-child(1) { top: 0; left: 50%; }
.label-item:nth-child(2) { top: 19%; right: 0; left: auto; transform: translate(0, -50%); }
.label-item:nth-child(3) { bottom: 0; right: 0; left: auto; transform: translate(0, 0); }
.label-item:nth-child(4) { bottom: 0; left: 0; transform: translate(0, 0); }
.label-item:nth-child(5) { top: 19%; left: 0; transform: translate(0, -50%); }

/**
 * 雷达图数据点容器
 */
.radar-points {
  position: absolute;
  inset: 0;
}

/**
 * 单个雷达图数据点
 */
.radar-point {
  position: absolute;
  width: 10px;
  height: 10px;
  background: var(--primary-color);
  border-radius: 50%;
  transform: translate(-50%, -50%);
  box-shadow: 0 0 10px var(--primary-color);
}

/**
 * 雷达图图例
 */
.radar-legend {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

/**
 * 雷达图空状态
 */
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

/**
 * 单个图例项
 */
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

/**
 * 总结评语卡片
 */
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

/**
 * 问题列表卡片
 */
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

/**
 * 薄弱点卡片
 */
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

.domain-score {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}

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

/**
 * 推荐资源卡片
 */
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
