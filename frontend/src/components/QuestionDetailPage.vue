<template>
  <section id="page-question-detail" class="page-section active">
    <header class="detail-header glass-card">
      <div class="header-left">
        <button class="back-btn" @click="handleBack">
          <i class="fas fa-arrow-left"></i>
          {{ backLabel }}
        </button>
        <div>
          <h2 class="page-title">问答复盘</h2>
          <p class="page-subtitle" v-if="hasDetail">
            {{ detail.jobName }} · {{ detail.modeLabel }} · 第 {{ detail.questionNumber }} 题
          </p>
        </div>
      </div>

      <button
        class="collect-btn"
        :class="{ collected: detail?.isCollected }"
        :disabled="!hasDetail || detail?.isCollected"
        @click="handleCollect"
      >
        <i :class="detail?.isCollected ? 'fas fa-check' : 'fas fa-bookmark'"></i>
        {{ detail?.isCollected ? '已收藏到成长问答库' : '收藏到成长问答库' }}
      </button>
    </header>

    <div v-if="loading" class="state-card glass-card">
      <i class="fas fa-spinner fa-spin"></i>
      <p>正在加载单题复盘内容...</p>
    </div>

    <div v-else-if="!hasDetail" class="state-card glass-card">
      <i class="fas fa-inbox"></i>
      <p>当前题目不存在，或暂时无法加载详情。</p>
    </div>

    <div v-else class="detail-layout">
      <div class="left-column">
        <section class="glass-card content-card">
          <div class="meta-row">
            <span class="meta-tag domain">
              <i class="fas fa-layer-group"></i>
              {{ detail.domainName }}
            </span>
            <span class="meta-tag type">
              <i class="fas fa-tag"></i>
              {{ detail.questionType }}
            </span>
            <span v-if="detail.answerStatus === 'skipped'" class="meta-tag skipped">
              <i class="fas fa-forward"></i>
              已跳过
            </span>
          </div>

          <h3 class="section-title">
            <i class="fas fa-question-circle"></i>
            题目原文
          </h3>
          <p class="question-text">{{ detail.questionStem }}</p>
        </section>

        <section class="glass-card content-card">
          <div class="section-header">
            <h3 class="section-title">
              <i class="fas fa-user"></i>
              我的回答
            </h3>
            <span class="section-tip">绿色为亮点，红色为待补强片段</span>
          </div>

          <div v-if="detail.answerStatus === 'skipped'" class="empty-answer">
            本题当时被跳过，建议先按黄金骨架补全一版答案，再继续向 AI 追问。
          </div>
          <p v-else class="annotated-answer">
            <template v-if="renderSegments.length">
              <span
                v-for="(segment, index) in renderSegments"
                :key="`${segment.text}-${index}`"
                class="answer-segment"
                :class="segment.type"
              >
                {{ segment.text }}
              </span>
            </template>
            <template v-else>{{ detail.userAnswer || '未作答' }}</template>
          </p>

          <div v-if="annotationNotes.length" class="annotation-notes">
            <div
              v-for="(note, index) in annotationNotes"
              :key="`${note.text}-${index}`"
              class="annotation-note"
              :class="note.type"
            >
              <i :class="note.type === 'strength' ? 'fas fa-circle-check' : 'fas fa-circle-exclamation'"></i>
              <span>{{ note.note }}</span>
            </div>
          </div>
        </section>

        <section class="glass-card content-card">
          <div class="score-overview">
            <template v-if="evaluationStatus === 'ready'">
              <div v-if="hasNumericScore" class="score-badge" :class="scoreClass">
                <span class="score-value">{{ detail.score }}</span>
                <span class="score-label">单题得分</span>
              </div>
              <div class="score-copy">
                <h3 class="section-title">
                  <i class="fas fa-chart-line"></i>
                  评分与点评
                </h3>
                <p class="commentary-text">{{ displayCommentary }}</p>
              </div>
            </template>
            <div v-else class="evaluation-status" :class="evaluationStatus">
              <template v-if="evaluationStatus === 'generating'">
                <i class="fas fa-spinner fa-spin"></i>
                <span>单题评估生成中，请稍后刷新查看。</span>
              </template>
              <template v-else-if="evaluationStatus === 'failed'">
                <i class="fas fa-circle-exclamation"></i>
                <span>评估暂时失败，当前仅展示基础题目信息。本期不会自动重试。</span>
              </template>
              <template v-else>
                <i class="fas fa-hourglass-half"></i>
                <span>单题评估尚未就绪。</span>
              </template>
            </div>
          </div>

          <div v-if="evaluationStatus === 'ready'" class="domain-score-list">
            <div v-for="item in detail.evaluatedDomains" :key="item.domainName" class="domain-score-item">
              <div class="domain-score-header">
                <span>{{ item.domainName }}</span>
                <strong>{{ hasDomainScore(item) ? `${Number(item.score)} 分` : '待评估' }}</strong>
              </div>
              <p>{{ item.note }}</p>
            </div>
          </div>

          <div v-if="evaluationStatus === 'ready'" class="point-grid">
            <div class="point-card success">
              <h4>亮点</h4>
              <ul>
                <li v-for="point in detail.strengthPoints" :key="point">{{ point }}</li>
              </ul>
            </div>
            <div class="point-card danger">
              <h4>薄弱点</h4>
              <ul>
                <li v-for="point in detail.weakPoints" :key="point">{{ point }}</li>
              </ul>
            </div>
          </div>
        </section>

        <section class="glass-card content-card">
          <h3 class="section-title">
            <i class="fas fa-sitemap"></i>
            黄金答题骨架
          </h3>
          <ol class="outline-list">
            <li v-for="(item, index) in detail.idealAnswerOutline" :key="`${item}-${index}`">
              {{ item }}
            </li>
          </ol>
        </section>

        <section class="glass-card content-card">
          <h3 class="section-title">
            <i class="fas fa-star"></i>
            参考满分重构
          </h3>
          <p class="rewritten-answer">{{ detail.rewrittenAnswer }}</p>
        </section>
      </div>

      <aside class="right-column">
        <section class="glass-card sidebar-card">
          <div class="section-header">
            <h3 class="section-title">
              <i class="fas fa-robot"></i>
              AI 追问
            </h3>
            <span class="section-tip">本区为本地模拟追问，不代表后端真实评估</span>
          </div>

          <div class="quick-question-list">
            <button
              v-for="item in quickQuestions"
              :key="item"
              class="quick-question-btn"
              @click="useQuickQuestion(item)"
            >
              {{ item }}
            </button>
          </div>

          <div v-if="consultMessages.length" class="consult-messages">
            <div
              v-for="message in consultMessages"
              :key="message.id"
              class="consult-item"
              :class="message.role"
            >
              <div class="consult-avatar">
                <i :class="message.role === 'user' ? 'fas fa-user' : 'fas fa-robot'"></i>
              </div>
              <div class="consult-bubble">
                <p>{{ message.content }}</p>
                <span>{{ message.createdAt }}</span>
              </div>
            </div>
          </div>
          <div v-else class="consult-empty">
            可以继续追问「为什么失分」「怎么重答」或「能否继续追问我一轮」。
          </div>

          <p v-if="consultError" class="consult-error">{{ consultError }}</p>

          <div class="consult-input-area">
            <textarea
              v-model="consultInput"
              class="consult-input"
              rows="4"
              placeholder="继续围绕本题提问..."
            ></textarea>
            <button
              class="send-btn"
              :disabled="!consultInput.trim() || isConsultSending"
              @click="sendConsult()"
            >
              <i :class="isConsultSending ? 'fas fa-spinner fa-spin' : 'fas fa-paper-plane'"></i>
              {{ isConsultSending ? '发送中' : '发送' }}
            </button>
          </div>
        </section>

        <section class="glass-card sidebar-card">
          <h3 class="section-title">
            <i class="fas fa-clipboard-check"></i>
            复盘摘要
          </h3>
          <div class="summary-grid">
            <div class="summary-item">
              <span class="summary-label">当前得分</span>
              <strong>{{ summaryScoreText }}</strong>
            </div>
            <div class="summary-item">
              <span class="summary-label">薄弱点</span>
              <strong>{{ (detail.weakPoints || []).length }} 项</strong>
            </div>
            <div class="summary-item">
              <span class="summary-label">收藏状态</span>
              <strong>{{ detail.isCollected ? '已收藏' : '未收藏' }}</strong>
            </div>
          </div>
        </section>

        <section class="glass-card sidebar-card">
          <h3 class="section-title">
            <i class="fas fa-arrows-left-right"></i>
            上下题导航
          </h3>
          <div class="navigation-actions">
            <button class="nav-btn" :disabled="!detail.hasPrev" @click="navigateQuestion(-1)">
              <i class="fas fa-arrow-left"></i>
              上一题
            </button>
            <button class="nav-btn" :disabled="!detail.hasNext" @click="navigateQuestion(1)">
              下一题
              <i class="fas fa-arrow-right"></i>
            </button>
          </div>
        </section>
      </aside>
    </div>
  </section>
</template>

<script>
import { computed, ref, watch } from 'vue'

const CONSULT_STORAGE_KEY = 'questionConsultHistory'

export default {
  name: 'QuestionDetailPage',
  props: {
    detail: {
      type: Object,
      default: null
    },
    loading: {
      type: Boolean,
      default: false
    }
  },
  emits: ['back', 'collect', 'navigateQuestion'],
  setup(props, { emit }) {
    const consultMessages = ref([])
    const consultInput = ref('')
    const consultError = ref('')
    const isConsultSending = ref(false)

    const hasDetail = computed(() => Boolean(props.detail))

    const evaluationStatus = computed(() => {
      const raw = props.detail?.evaluationStatus
      if (!raw) return 'ready'
      const normalized = String(raw).toLowerCase()
      if (['pending', 'generating', 'ready', 'failed'].includes(normalized)) {
        return normalized
      }
      return 'pending'
    })

    const hasNumericScore = computed(() => {
      const score = props.detail?.score
      if (score == null) return false
      return Number.isFinite(Number(score))
    })

    const scoreClass = computed(() => {
      const score = Number(props.detail?.score)
      if (!Number.isFinite(score)) return 'medium'
      if (score >= 80) return 'high'
      if (score >= 60) return 'medium'
      return 'low'
    })

    const displayCommentary = computed(() => {
      const commentary = String(props.detail?.commentary || '').trim()
      return commentary || '评语待生成'
    })

    const summaryScoreText = computed(() => {
      if (evaluationStatus.value !== 'ready' || !hasNumericScore.value) return '--'
      return `${Number(props.detail.score)} 分`
    })

    const hasDomainScore = (item) => {
      if (!item || item.score == null) return false
      return Number.isFinite(Number(item.score))
    }

    const renderSegments = computed(() => {
      if (!props.detail?.highlightedSegments || !props.detail.highlightedSegments.length) return []
      return props.detail.highlightedSegments
        .map((item) => {
          if (!item || typeof item !== 'object') return null
          if ('segment' in item || 'label' in item || 'comment' in item) {
            const text = String(item.segment || '').trim()
            if (!text) return null
            const label = String(item.label || '').toLowerCase()
            const type = label === 'weakness' ? 'weakness' : 'strength'
            return {
              text,
              type,
              note: item.comment || ''
            }
          }

          const text = String(item.text || '').trim()
          if (!text) return null
          return {
            text,
            type: item.type === 'weakness' ? 'weakness' : 'strength',
            note: item.note || ''
          }
        })
        .filter(Boolean)
    })

    const annotationNotes = computed(() => {
      return renderSegments.value.filter((item) => item.note)
    })

    const quickQuestions = computed(() => [
      '为什么这里会失分？',
      '如果重答，这题应该怎么组织结构？',
      `能基于${props.detail?.jobName || '当前岗位'}再追问我一轮吗？`
    ])

    const backLabel = computed(() => props.detail?.backLabel || '返回报告')

    const questionStorageId = computed(() => {
      if (!props.detail) return ''
      return `${props.detail.recordId}-${props.detail.questionId}`
    })

    const readConsultHistory = () => {
      if (!questionStorageId.value) {
        consultMessages.value = []
        return
      }

      const raw = JSON.parse(localStorage.getItem(CONSULT_STORAGE_KEY) || '{}')
      consultMessages.value = raw[questionStorageId.value] || []
    }

    const saveConsultHistory = () => {
      if (!questionStorageId.value) return
      const raw = JSON.parse(localStorage.getItem(CONSULT_STORAGE_KEY) || '{}')
      raw[questionStorageId.value] = consultMessages.value
      localStorage.setItem(CONSULT_STORAGE_KEY, JSON.stringify(raw))
    }

    const buildAssistantReply = (question) => {
      const weakPoint = props.detail?.weakPoints?.[0] || '结构化表达还不够完整'
      const outline = props.detail?.idealAnswerOutline?.slice(0, 2).join(' ')
      if (question.includes('失分')) {
        return `你这题主要失分在「${weakPoint}」。建议重答时先补齐定义和关键原理，再结合一个具体场景把回答展开。`
      }
      if (question.includes('怎么组织') || question.includes('重答')) {
        return `可以按这个顺序来组织：${outline}。这样能先把核心概念讲清，再逐步展开细节。`
      }
      return `如果围绕这题继续提升，我建议你重点补强「${weakPoint}」，并把回答拆成“概念定义 -> 关键原理 -> 场景案例 -> 边界与取舍”四段来输出。`
    }

    const pushMessage = (role, content) => {
      consultMessages.value.push({
        id: `${Date.now()}-${Math.random().toString(16).slice(2, 8)}`,
        role,
        content,
        createdAt: new Date().toLocaleTimeString('zh-CN', {
          hour: '2-digit',
          minute: '2-digit'
        })
      })
      saveConsultHistory()
    }

    const sendConsult = (presetQuestion = '') => {
      const content = (presetQuestion || consultInput.value).trim()
      if (!content || isConsultSending.value) return

      consultError.value = ''
      pushMessage('user', content)
      consultInput.value = ''
      isConsultSending.value = true

      window.setTimeout(() => {
        try {
          pushMessage('assistant', buildAssistantReply(content))
        } catch (error) {
          consultError.value = 'AI 追问暂时失败，请稍后重试。'
        } finally {
          isConsultSending.value = false
        }
      }, 500)
    }

    const useQuickQuestion = (question) => {
      sendConsult(question)
    }

    const handleBack = () => {
      emit('back')
    }

    const handleCollect = () => {
      emit('collect', props.detail)
    }

    const navigateQuestion = (delta) => {
      emit('navigateQuestion', delta)
    }

    watch(
      questionStorageId,
      () => {
        consultInput.value = ''
        consultError.value = ''
        isConsultSending.value = false
        readConsultHistory()
      },
      { immediate: true }
    )

    return {
      consultMessages,
      consultInput,
      consultError,
      isConsultSending,
      hasDetail,
      evaluationStatus,
      hasNumericScore,
      scoreClass,
      displayCommentary,
      summaryScoreText,
      hasDomainScore,
      renderSegments,
      annotationNotes,
      quickQuestions,
      backLabel,
      handleBack,
      handleCollect,
      navigateQuestion,
      sendConsult,
      useQuickQuestion
    }
  }
}
</script>

<style scoped>
.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20px;
  padding: 20px 24px;
  margin-bottom: 24px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.back-btn,
.collect-btn,
.quick-question-btn,
.send-btn,
.nav-btn {
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-md);
  font-family: inherit;
  cursor: pointer;
  transition: all 0.2s ease;
}

.back-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  background: var(--glass-bg);
  color: var(--text-primary);
}

.back-btn:hover {
  border-color: var(--primary-color);
  color: var(--primary-color);
}

.page-title {
  margin: 0 0 4px;
  font-size: 24px;
  color: var(--text-primary);
}

.page-subtitle {
  margin: 0;
  color: var(--text-secondary);
  font-size: 14px;
}

.collect-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 11px 18px;
  background: rgba(102, 126, 234, 0.14);
  color: var(--primary-color);
}

.collect-btn.collected,
.collect-btn:disabled {
  background: rgba(16, 185, 129, 0.12);
  border-color: rgba(16, 185, 129, 0.35);
  color: #10b981;
  cursor: default;
}

.state-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  min-height: 260px;
  color: var(--text-secondary);
}

.detail-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.6fr) minmax(320px, 0.9fr);
  gap: 24px;
}

.left-column,
.right-column {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.content-card,
.sidebar-card {
  padding: 24px;
}

.meta-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 20px;
}

.meta-tag {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border-radius: 999px;
  font-size: 12px;
}

.meta-tag.domain {
  background: rgba(102, 126, 234, 0.12);
  color: var(--primary-color);
}

.meta-tag.type {
  background: rgba(245, 158, 11, 0.12);
  color: #f59e0b;
}

.meta-tag.depth {
  background: rgba(59, 130, 246, 0.12);
  color: #60a5fa;
}

.meta-tag.skipped {
  background: rgba(239, 68, 68, 0.12);
  color: #ef4444;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0;
  font-size: 16px;
  color: var(--text-primary);
}

.section-title i {
  color: var(--primary-color);
}

.section-tip {
  color: var(--text-secondary);
  font-size: 12px;
}

.question-text,
.commentary-text,
.rewritten-answer,
.consult-empty,
.consult-error {
  margin: 0;
  line-height: 1.8;
}

.question-text,
.rewritten-answer {
  color: var(--text-primary);
}

.annotated-answer {
  margin: 0;
  padding: 16px 18px;
  border-radius: var(--radius-md);
  background: rgba(15, 23, 42, 0.28);
  color: var(--text-primary);
  line-height: 1.9;
  white-space: pre-wrap;
}

.answer-segment.strength {
  background: rgba(16, 185, 129, 0.16);
  color: #34d399;
}

.answer-segment.weakness {
  background: rgba(239, 68, 68, 0.16);
  color: #f87171;
}

.empty-answer {
  padding: 16px 18px;
  border: 1px dashed rgba(239, 68, 68, 0.35);
  border-radius: var(--radius-md);
  color: var(--text-secondary);
  line-height: 1.7;
}

.annotation-notes {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 14px;
}

.annotation-note {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}

.annotation-note.strength {
  color: #34d399;
}

.annotation-note.weakness {
  color: #f87171;
}

.score-overview {
  display: flex;
  gap: 18px;
  align-items: center;
  margin-bottom: 18px;
}

.score-badge {
  width: 96px;
  height: 96px;
  border-radius: 50%;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  border: 2px solid var(--glass-border);
  background: rgba(15, 23, 42, 0.24);
  flex-shrink: 0;
}

.score-badge.high {
  border-color: #10b981;
}

.score-badge.medium {
  border-color: #f59e0b;
}

.score-badge.low {
  border-color: #ef4444;
}

.score-value {
  font-size: 32px;
  font-weight: 700;
  color: var(--text-primary);
}

.score-label {
  font-size: 12px;
  color: var(--text-secondary);
}

.score-copy {
  flex: 1;
}

.evaluation-status {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 16px;
  border-radius: var(--radius-md);
  border: 1px solid transparent;
  color: var(--text-primary);
}

.evaluation-status.generating {
  background: rgba(59, 130, 246, 0.12);
  border-color: rgba(59, 130, 246, 0.28);
}

.evaluation-status.failed {
  background: rgba(239, 68, 68, 0.12);
  border-color: rgba(239, 68, 68, 0.28);
}

.evaluation-status.pending {
  background: rgba(148, 163, 184, 0.12);
  border-color: rgba(148, 163, 184, 0.28);
}

.domain-score-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 18px;
}

.domain-score-item {
  padding: 14px 16px;
  border-radius: var(--radius-md);
  background: rgba(15, 23, 42, 0.24);
}

.domain-score-header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
  color: var(--text-primary);
}

.domain-score-item p {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--text-secondary);
}

.point-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.point-card {
  padding: 16px;
  border-radius: var(--radius-md);
  border: 1px solid transparent;
}

.point-card.success {
  background: rgba(16, 185, 129, 0.08);
  border-color: rgba(16, 185, 129, 0.15);
}

.point-card.danger {
  background: rgba(239, 68, 68, 0.08);
  border-color: rgba(239, 68, 68, 0.15);
}

.point-card h4 {
  margin: 0 0 12px;
  font-size: 14px;
  color: var(--text-primary);
}

.point-card ul,
.outline-list {
  margin: 0;
  padding-left: 18px;
}

.point-card li,
.outline-list li {
  line-height: 1.8;
  color: var(--text-secondary);
}

.quick-question-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin: 14px 0 16px;
}

.quick-question-btn {
  padding: 8px 12px;
  background: rgba(102, 126, 234, 0.08);
  color: var(--primary-color);
  font-size: 12px;
}

.quick-question-btn:hover {
  background: rgba(102, 126, 234, 0.14);
}

.consult-messages {
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-height: 360px;
  overflow-y: auto;
  margin-bottom: 14px;
}

.consult-item {
  display: flex;
  gap: 10px;
}

.consult-item.user {
  flex-direction: row-reverse;
}

.consult-avatar {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: rgba(102, 126, 234, 0.14);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--primary-color);
  flex-shrink: 0;
}

.consult-bubble {
  max-width: calc(100% - 44px);
  padding: 12px 14px;
  border-radius: var(--radius-md);
  background: rgba(15, 23, 42, 0.28);
}

.consult-item.user .consult-bubble {
  background: rgba(16, 185, 129, 0.12);
}

.consult-bubble p {
  margin: 0 0 6px;
  color: var(--text-primary);
  line-height: 1.7;
}

.consult-bubble span {
  font-size: 12px;
  color: var(--text-secondary);
}

.consult-error {
  color: #f87171;
  font-size: 13px;
  margin-bottom: 12px;
}

.consult-input-area {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.consult-input {
  width: 100%;
  padding: 14px 16px;
  border-radius: var(--radius-md);
  border: 1px solid var(--glass-border);
  background: rgba(15, 23, 42, 0.28);
  color: var(--text-primary);
  resize: vertical;
  font-family: inherit;
}

.send-btn {
  align-self: flex-end;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  background: linear-gradient(135deg, var(--primary-color), var(--primary-light));
  color: white;
  border-color: transparent;
}

.send-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.summary-item {
  padding: 14px;
  border-radius: var(--radius-md);
  background: rgba(15, 23, 42, 0.24);
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.summary-label {
  font-size: 12px;
  color: var(--text-secondary);
}

.summary-item strong {
  color: var(--text-primary);
}

.navigation-actions {
  display: flex;
  gap: 12px;
}

.nav-btn {
  flex: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 11px 16px;
  background: rgba(102, 126, 234, 0.08);
  color: var(--primary-color);
}

.nav-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

@media (max-width: 1080px) {
  .detail-layout {
    grid-template-columns: 1fr;
  }

  .summary-grid,
  .point-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .detail-header {
    flex-direction: column;
    align-items: stretch;
  }

  .header-left {
    flex-direction: column;
    align-items: flex-start;
  }

  .navigation-actions {
    flex-direction: column;
  }
}
</style>
