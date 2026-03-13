<template>
  <section id="page-question-bank" class="page-section active">
    <header class="bank-header glass-card">
      <div class="header-left">
        <h1 class="page-title">
          <i class="fas fa-book"></i>
          成长问答库
        </h1>
        <p class="header-desc">管理收藏题目，进入单题复盘或只针对这一题发起练习。</p>
      </div>
      <div class="header-stats">
        <div class="stat-item">
          <span class="stat-value">{{ totalQuestions }}</span>
          <span class="stat-label">收藏题目</span>
        </div>
        <div class="stat-item">
          <span class="stat-value">{{ weakQuestions }}</span>
          <span class="stat-label">待加强</span>
        </div>
        <div class="stat-item">
          <span class="stat-value">{{ averageScore }}</span>
          <span class="stat-label">平均分</span>
        </div>
      </div>
    </header>

    <section class="filter-section glass-card">
      <div class="filter-row">
        <div class="filter-group">
          <label>时间范围</label>
          <CustomSelect v-model="filters.timeRange" :options="timeRangeOptions" @change="applyFilters" />
        </div>
        <div class="filter-group">
          <label>知识域</label>
          <CustomSelect v-model="filters.domainTag" :options="domainOptions" @change="applyFilters" />
        </div>
        <div class="filter-group">
          <label>分数区间</label>
          <CustomSelect v-model="filters.scoreRange" :options="scoreRangeOptions" @change="applyFilters" />
        </div>
        <div class="filter-group">
          <label>排序方式</label>
          <CustomSelect v-model="filters.sortBy" :options="sortOptions" @change="applyFilters" />
        </div>
      </div>

      <div class="filter-tags">
        <span v-if="filters.timeRange !== 'all'" class="filter-tag" @click="filters.timeRange = 'all'; applyFilters()">
          {{ getTimeRangeLabel(filters.timeRange) }}
          <i class="fas fa-times"></i>
        </span>
        <span v-if="filters.domainTag !== 'all'" class="filter-tag" @click="filters.domainTag = 'all'; applyFilters()">
          {{ getDomainLabel(filters.domainTag) }}
          <i class="fas fa-times"></i>
        </span>
        <span v-if="filters.scoreRange !== 'all'" class="filter-tag" @click="filters.scoreRange = 'all'; applyFilters()">
          {{ getScoreLabel(filters.scoreRange) }}
          <i class="fas fa-times"></i>
        </span>
        <button v-if="hasActiveFilters" class="clear-btn" @click="clearFilters">
          <i class="fas fa-eraser"></i>
          清除筛选
        </button>
      </div>
    </section>

    <div class="question-grid">
      <div v-if="isLoading" class="empty-state glass-card">
        <i class="fas fa-spinner fa-spin"></i>
        <p>正在加载问答库...</p>
      </div>

      <article
        v-for="item in filteredQuestions"
        :key="item.id"
        class="question-card glass-card"
      >
        <div class="card-top">
          <div class="card-tags">
            <span class="domain-tag">{{ item.domainName }}</span>
            <span v-if="item.tag" class="sub-tag">{{ item.tag }}</span>
          </div>
          <div class="card-score" :class="getScoreClass(item.score)">
            {{ item.score == null ? '--' : `${item.score}分` }}
          </div>
        </div>

        <div class="question-text">{{ item.questionStem }}</div>

        <div class="meta-row">
          <span class="info-item"><i class="fas fa-briefcase"></i>{{ item.jobName }}</span>
          <span class="info-item"><i class="fas fa-layer-group"></i>{{ item.questionType }}</span>
          <span class="info-item"><i class="fas fa-calendar-alt"></i>{{ compactDate(item.createdAt) }}</span>
        </div>

        <div v-if="item.userAnswer" class="answer-preview">
          {{ truncateText(item.userAnswer, 72) }}
        </div>

        <div class="card-actions">
          <button class="action-btn" @click="showDetail(item)">
            <i class="fas fa-eye"></i>
            查看详情
          </button>
          <button class="action-btn redo" @click="redoQuestion(item)">
            <i class="fas fa-play"></i>
            重做
          </button>
          <button class="action-btn delete" @click="deleteQuestion(item.id)">
            <i class="fas fa-trash-alt"></i>
            删除
          </button>
        </div>
      </article>

      <div v-if="!isLoading && filteredQuestions.length === 0" class="empty-state glass-card">
        <i class="fas fa-book-open"></i>
        <p>{{ loadError || (totalQuestions === 0 ? '你还没有收藏题目，去单题复盘页积累第一道题吧。' : '暂无符合条件的题目。') }}</p>
        <button v-if="hasActiveFilters" class="btn btn-secondary glass-btn" @click="clearFilters">
          清除筛选条件
        </button>
      </div>
    </div>
  </section>
</template>

<script>
import { computed, onMounted, reactive, ref } from 'vue'
import { deleteQuestionBankItem, getQuestionBank } from '../api/resume'
import CustomSelect from './CustomSelect.vue'

export default {
  name: 'QuestionBankPage',
  components: {
    CustomSelect
  },
  emits: ['showDetail', 'redo'],
  setup(props, { emit }) {
    const STORAGE_KEY = 'questionBank'
    const allQuestions = ref([])
    const isLoading = ref(false)
    const loadError = ref('')

    const filters = reactive({
      timeRange: 'all',
      domainTag: 'all',
      scoreRange: 'all',
      sortBy: 'newest'
    })

    const timeRangeOptions = [
      { value: 'all', label: '全部时间' },
      { value: 'week', label: '最近 7 天' },
      { value: 'month', label: '最近 30 天' },
      { value: 'quarter', label: '最近 90 天' }
    ]

    const scoreRangeOptions = [
      { value: 'all', label: '全部分数' },
      { value: 'low', label: '60 分以下' },
      { value: 'medium', label: '60 - 79 分' },
      { value: 'high', label: '80 分及以上' }
    ]

    const sortOptions = [
      { value: 'newest', label: '最新收藏' },
      { value: 'oldest', label: '最早收藏' },
      { value: 'scoreAsc', label: '分数从低到高' },
      { value: 'scoreDesc', label: '分数从高到低' }
    ]

    const totalQuestions = computed(() => allQuestions.value.length)
    const weakQuestions = computed(() =>
      allQuestions.value.filter((item) => item.score != null && item.score < 60).length
    )
    const averageScore = computed(() => {
      const scored = allQuestions.value.filter((item) => item.score != null)
      if (!scored.length) return '--'
      const total = scored.reduce((sum, item) => sum + Number(item.score || 0), 0)
      return Math.round(total / scored.length)
    })

    const domainOptions = computed(() => {
      const names = [...new Set(allQuestions.value.map((item) => item.domainName).filter(Boolean))]
      return [
        { value: 'all', label: '全部知识域' },
        ...names.map((name) => ({ value: name, label: name }))
      ]
    })

    const hasActiveFilters = computed(() => {
      return filters.timeRange !== 'all' || filters.domainTag !== 'all' || filters.scoreRange !== 'all'
    })

    const filteredQuestions = computed(() => {
      let result = [...allQuestions.value]

      if (filters.timeRange !== 'all') {
        const rangeDaysMap = {
          week: 7,
          month: 30,
          quarter: 90
        }
        const cutoff = Date.now() - rangeDaysMap[filters.timeRange] * 24 * 60 * 60 * 1000
        result = result.filter((item) => item.timestamp >= cutoff)
      }

      if (filters.domainTag !== 'all') {
        result = result.filter((item) => item.domainName === filters.domainTag)
      }

      if (filters.scoreRange !== 'all') {
        const scoreRangeMap = {
          low: [0, 59],
          medium: [60, 79],
          high: [80, 100]
        }
        const [min, max] = scoreRangeMap[filters.scoreRange]
        result = result.filter((item) => item.score != null && item.score >= min && item.score <= max)
      }

      const sorterMap = {
        newest: (a, b) => b.timestamp - a.timestamp,
        oldest: (a, b) => a.timestamp - b.timestamp,
        scoreAsc: (a, b) => a.score - b.score,
        scoreDesc: (a, b) => b.score - a.score
      }
      result.sort(sorterMap[filters.sortBy])

      return result
    })

    const inferDomainName = (item = {}) => {
      const rawText = `${item.domainName || ''} ${item.tag || ''} ${item.questionStem || item.question || ''}`.toLowerCase()
      if (/(vue|react|javascript|css|html|浏览器|前端)/.test(rawText)) return '前端基础'
      if (/(mysql|redis|数据库|缓存|java|并发|jvm|后端)/.test(rawText)) return '后端基础'
      if (/(算法|复杂度|链表|树|排序)/.test(rawText)) return '算法与数据结构'
      if (/(项目|系统设计|架构|工程)/.test(rawText)) return '工程实践'
      return item.domainName || item.tag || '通用技术能力'
    }

    const normalizeCreatedAt = (value, fallbackTimestamp = Date.now()) => {
      if (value) return value
      return new Date(fallbackTimestamp).toLocaleString('zh-CN', { hour12: false })
    }

    const normalizeQuestion = (item = {}, index = 0) => {
      const timestamp = Number(item.timestamp) || new Date(item.createdAt || item.date || Date.now()).getTime() || Date.now() + index
      const domainName = inferDomainName(item)
      const score = item.score == null || item.score === '' ? null : Number(item.score)

      return {
        id: item.id || `${item.recordId || item.sessionId || 'bank'}-${item.questionId || index}`,
        questionStem: item.questionStem || item.question || '未命名题目',
        domainName,
        score: Number.isFinite(score) ? score : null,
        sessionId: item.sessionId || item.recordId || '',
        questionId: item.questionId || null,
        tag: item.tag || domainName,
        createdAt: normalizeCreatedAt(item.createdAt || item.date, timestamp),
        timestamp,
        jobName: item.jobName || item.job || '模拟面试',
        jobType: item.jobType || 'frontend',
        questionType: item.questionType || '综合题',
        targetDepth: item.targetDepth || 'L2',
        modeLabel: item.modeLabel || '练习模式',
        userAnswer: item.userAnswer || item.answerSummary || item.answer || '',
        rewrittenAnswer: item.rewrittenAnswer || item.standardAnswer || '',
        analysis: item.analysis || ''
      }
    }

    const syncLocalCache = (items) => {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(items))
    }

    const buildScoreFilter = () => {
      if (filters.scoreRange === 'low') return { minScore: 0, maxScore: 59 }
      if (filters.scoreRange === 'medium') return { minScore: 60, maxScore: 79 }
      if (filters.scoreRange === 'high') return { minScore: 80, maxScore: 100 }
      return { minScore: null, maxScore: null }
    }

    const toSortParams = () => {
      if (filters.sortBy === 'oldest') return { sortBy: 'createdAt', sortOrder: 'asc' }
      if (filters.sortBy === 'scoreAsc') return { sortBy: 'score', sortOrder: 'asc' }
      if (filters.sortBy === 'scoreDesc') return { sortBy: 'score', sortOrder: 'desc' }
      return { sortBy: 'createdAt', sortOrder: 'desc' }
    }

    const loadQuestions = async () => {
      isLoading.value = true
      loadError.value = ''
      try {
        const scoreFilter = buildScoreFilter()
        const sortParams = toSortParams()
        const pageData = await getQuestionBank({
          page: 1,
          pageSize: 200,
          minScore: scoreFilter.minScore,
          maxScore: scoreFilter.maxScore,
          sortBy: sortParams.sortBy,
          sortOrder: sortParams.sortOrder
        })
        const items = Array.isArray(pageData?.items) ? pageData.items : []
        const mapped = items.map((item, index) => normalizeQuestion(item, index))
        allQuestions.value = mapped
        syncLocalCache(mapped)
      } catch (error) {
        console.error('[QuestionBankPage] 加载问答库失败', error)
        allQuestions.value = []
        loadError.value = error?.message || '加载问答库失败，请稍后重试'
      } finally {
        isLoading.value = false
      }
    }

    const applyFilters = () => {
      loadQuestions()
    }

    const clearFilters = () => {
      filters.timeRange = 'all'
      filters.domainTag = 'all'
      filters.scoreRange = 'all'
      filters.sortBy = 'newest'
      loadQuestions()
    }

    const getTimeRangeLabel = (value) => {
      const labelMap = {
        week: '最近 7 天',
        month: '最近 30 天',
        quarter: '最近 90 天'
      }
      return labelMap[value] || '全部时间'
    }

    const getDomainLabel = (value) => {
      const option = domainOptions.value.find((item) => item.value === value)
      return option ? option.label : value
    }

    const getScoreLabel = (value) => {
      const labelMap = {
        low: '60 分以下',
        medium: '60 - 79 分',
        high: '80 分及以上'
      }
      return labelMap[value] || '全部分数'
    }

    const getScoreClass = (score) => {
      if (score == null) return 'none'
      if (score >= 80) return 'high'
      if (score >= 60) return 'medium'
      return 'low'
    }

    const truncateText = (text, maxLength) => {
      if (!text) return ''
      if (text.length <= maxLength) return text
      return `${text.slice(0, maxLength)}...`
    }

    const compactDate = (value) => {
      if (!value) return ''
      const date = new Date(value)
      if (Number.isNaN(date.getTime())) return value
      return date.toLocaleDateString('zh-CN')
    }

    const redoQuestion = (item) => {
      emit('redo', item)
    }

    const deleteQuestion = async (id) => {
      if (!confirm('确定从成长问答库中删除这道题目吗？')) return

      try {
        await deleteQuestionBankItem(id)
        await loadQuestions()
      } catch (error) {
        alert(error?.message || '删除失败，请稍后重试')
      }
    }

    const showDetail = (item) => {
      emit('showDetail', item)
    }

    onMounted(() => {
      loadQuestions()
    })

    return {
      filters,
      totalQuestions,
      weakQuestions,
      averageScore,
      timeRangeOptions,
      domainOptions,
      scoreRangeOptions,
      sortOptions,
      filteredQuestions,
      isLoading,
      loadError,
      hasActiveFilters,
      applyFilters,
      clearFilters,
      getTimeRangeLabel,
      getDomainLabel,
      getScoreLabel,
      getScoreClass,
      compactDate,
      truncateText,
      showDetail,
      redoQuestion,
      deleteQuestion,
    }
  }
}
</script>

<style scoped>
.bank-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20px;
  padding: 18px 20px;
  margin-bottom: 16px;
}

.header-left {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.page-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 22px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
}

.page-title i {
  color: var(--primary-color);
}

.header-desc {
  font-size: 13px;
  color: var(--text-secondary);
  margin: 0;
}

.header-stats {
  display: flex;
  gap: 14px;
}

.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  min-width: 64px;
  padding: 8px 12px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.04);
}

.stat-value {
  font-size: 24px;
  font-weight: 700;
  color: var(--primary-color);
}

.stat-label {
  font-size: 12px;
  color: var(--text-secondary);
}

.filter-section {
  padding: 16px 18px;
  margin-bottom: 16px;
  position: relative;
  z-index: 2;
}

.filter-row {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
  margin-bottom: 14px;
}

.filter-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.filter-group label {
  font-size: 12px;
  color: var(--text-secondary);
  font-weight: 500;
}

.filter-tags {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.filter-tag {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  background: rgba(59, 89, 152, 0.15);
  border-radius: 16px;
  font-size: 13px;
  color: var(--primary-color);
  cursor: pointer;
}

.filter-tag:hover {
  background: rgba(59, 89, 152, 0.25);
}

.clear-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  background: transparent;
  border: 1px solid var(--glass-border);
  border-radius: 16px;
  font-size: 13px;
  color: var(--text-secondary);
  cursor: pointer;
  font-family: inherit;
}

.clear-btn:hover {
  border-color: var(--primary-color);
  color: var(--primary-color);
}

.question-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.question-card {
  padding: 14px 16px;
  transition: all var(--transition-normal);
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.question-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-medium);
}

.card-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.card-tags {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.domain-tag,
.sub-tag {
  display: inline-flex;
  align-items: center;
  padding: 5px 12px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 500;
}

.domain-tag {
  background: rgba(99, 102, 241, 0.14);
  color: #6366f1;
}

.sub-tag {
  background: rgba(59, 89, 152, 0.1);
  color: var(--text-secondary);
}

.date-tag,
.info-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--text-secondary);
}

.card-score {
  padding: 5px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
}

.card-score.high {
  background: rgba(16, 185, 129, 0.15);
  color: #10b981;
}

.card-score.medium {
  background: rgba(245, 158, 11, 0.15);
  color: #f59e0b;
}

.card-score.low {
  background: rgba(239, 68, 68, 0.15);
  color: #ef4444;
}

.card-score.none {
  background: rgba(148, 163, 184, 0.2);
  color: #94a3b8;
}

.question-text {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-primary);
  line-height: 1.6;
  min-height: 44px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.meta-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 14px;
}

.answer-preview {
  font-size: 12px;
  color: var(--text-secondary);
  line-height: 1.6;
  padding: 10px 12px;
  background: rgba(59, 89, 152, 0.05);
  border-radius: 10px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.card-actions {
  display: flex;
  gap: 8px;
  margin-top: auto;
  padding-top: 8px;
}

.action-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  justify-content: center;
  flex: 1;
  padding: 8px 10px;
  background: var(--glass-bg);
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-md);
  font-size: 12px;
  color: var(--text-primary);
  cursor: pointer;
  transition: all var(--transition-normal);
  font-family: inherit;
}

.action-btn:hover {
  border-color: var(--primary-color);
  color: var(--primary-color);
}

.action-btn.redo:hover {
  background: rgba(16, 185, 129, 0.1);
  border-color: #10b981;
  color: #10b981;
}

.action-btn.delete:hover {
  background: rgba(239, 68, 68, 0.1);
  border-color: #ef4444;
  color: #ef4444;
}

.empty-state {
  grid-column: 1 / -1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 60px 20px;
  color: var(--text-secondary);
  text-align: center;
}

.empty-state i {
  font-size: 48px;
  opacity: 0.5;
}

.empty-state p {
  margin: 0;
  font-size: 15px;
}

@media (max-width: 1200px) {
  .filter-row {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .question-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 900px) {
  .bank-header {
    flex-direction: column;
    align-items: stretch;
  }

  .header-stats {
    justify-content: space-between;
  }
}

@media (max-width: 768px) {
  .filter-row {
    grid-template-columns: 1fr;
  }

  .card-actions {
    flex-direction: column;
  }
}
</style>
