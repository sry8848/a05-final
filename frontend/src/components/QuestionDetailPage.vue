<template>
  <!-- 题目详情/问答复盘页面，用于展示单道面试题的详细复盘 -->
  <section id="page-question-detail" class="page-section active">
    <!-- 页面头部：返回按钮、页面标题、收藏按钮 -->
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

      <!-- 收藏按钮 -->
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

    <!-- 加载状态 -->
    <div v-if="loading" class="state-card glass-card">
      <i class="fas fa-spinner fa-spin"></i>
      <p>正在加载单题复盘内容...</p>
    </div>

    <!-- 无数据状态 -->
    <div v-else-if="!hasDetail" class="state-card glass-card">
      <i class="fas fa-inbox"></i>
      <p>当前题目不存在，或暂时无法加载详情。</p>
    </div>

    <!-- 详情主内容区域 -->
    <div v-else class="detail-layout">
      <!-- 左侧主内容列 -->
      <div class="left-column">
        <!-- 题目信息卡片：知识域、题型、题目原文 -->
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

        <!-- 我的回答卡片：标注过的回答文本和注解 -->
        <section class="glass-card content-card">
          <div class="section-header">
            <h3 class="section-title">
              <i class="fas fa-user"></i>
              我的回答
            </h3>
            <span class="section-tip">仅对关键亮点与待补强片段做文字标注</span>
          </div>

          <!-- 跳过状态提示 -->
          <div v-if="detail.answerStatus === 'skipped'" class="empty-answer">
            本题当时被跳过，建议先按黄金骨架补全一版答案，再继续向 AI 追问。
          </div>
          <!-- 标注过的回答文本 -->
          <p v-else class="annotated-answer">
            <template v-if="renderSlices.length">
              <span
                v-for="(segment, index) in renderSlices"
                :key="`${segment.start ?? 'legacy'}-${segment.end ?? index}-${segment.text}`"
                :class="segment.type === 'plain' ? 'answer-segment-plain' : ['answer-segment', segment.type]"
              >
                {{ segment.text }}
              </span>
            </template>
            <template v-else>{{ detail.userAnswer || '未作答' }}</template>
          </p>

          <!-- 注解说明列表 -->
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

        <!-- 评分与点评卡片 -->
        <section class="glass-card content-card">
          <div class="score-overview">
            <!-- 评估就绪状态 -->
            <template v-if="evaluationStatus === 'ready'">
              <!-- 单题得分徽章 -->
              <div v-if="hasNumericScore" class="score-badge" :class="scoreClass">
                <span class="score-value">{{ detail.score }}</span>
                <span class="score-label">单题得分</span>
              </div>
              <!-- 评语文本 -->
              <div class="score-copy">
                <h3 class="section-title">
                  <i class="fas fa-chart-line"></i>
                  评分与点评
                </h3>
                <p class="commentary-text">{{ displayCommentary }}</p>
              </div>
            </template>
            <!-- 评估非就绪状态：生成中/失败/待生成 -->
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

          <!-- 知识域点评列表 -->
          <div v-if="evaluationStatus === 'ready' && detailDomainFeedback.length" class="domain-score-list">
            <p class="domain-feedback-title">知识域点评</p>
            <div v-for="item in detailDomainFeedback" :key="`${item.domainCode}-${item.domainName}`" class="domain-score-item">
              <div class="domain-score-header">
                <span>{{ item.domainName }}</span>
              </div>
              <p>{{ item.commentary || '暂无点评' }}</p>
            </div>
          </div>

          <!-- 亮点与薄弱点网格 -->
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

        <!-- 重新作答卡片 -->
        <section class="glass-card content-card">
          <div class="section-header">
            <h3 class="section-title">
              <i class="fas fa-pen-to-square"></i>
              重新作答
            </h3>
            <span class="section-tip">独立单题评估，不影响原会话与整场配额</span>
          </div>

          <!-- 重答功能不可用状态 -->
          <div v-if="!redoContext.canRedo" class="redo-disabled">
            <i class="fas fa-ban"></i>
            <span>{{ redoContext.redoDisabledReason }}</span>
          </div>

          <!-- 重答功能可用 -->
          <template v-else>
            <div class="redo-actions">
              <button class="redo-action-btn primary" @click="beginRedo">
                <i class="fas fa-rotate-right"></i>
                {{ showRedoComposer ? '继续编辑重答' : '开始重新作答' }}
              </button>
              <button
                class="redo-action-btn"
                :disabled="redoLoading || redoSubmitting"
                @click="syncLatestRedoAttempt()"
              >
                <i :class="redoLoading ? 'fas fa-spinner fa-spin' : 'fas fa-arrows-rotate'"></i>
                刷新最新结果
              </button>
            </div>

            <!-- 重答编辑器 -->
            <div v-if="showRedoComposer" class="redo-composer">
              <textarea
                v-model="redoAnswer"
                class="redo-input"
                rows="6"
                placeholder="只针对这一题重新作答。这里不会触发下一题规划，也不会污染原会话。"
              ></textarea>
              <div class="redo-composer-actions">
                <button
                  class="redo-submit-btn"
                  :disabled="!redoAnswer.trim() || redoSubmitting"
                  @click="submitRedo"
                >
                  <i :class="redoSubmitting ? 'fas fa-spinner fa-spin' : 'fas fa-paper-plane'"></i>
                  {{ redoSubmitting ? '提交中' : '提交重答' }}
                </button>
              </div>
            </div>

            <!-- 重答错误提示 -->
            <p v-if="redoError" class="redo-error">{{ redoError }}</p>

            <!-- 重答加载状态：拉取中 -->
            <div v-if="redoLoading && !redoLatest" class="redo-status-card generating">
              <i class="fas fa-spinner fa-spin"></i>
              <span>正在拉取最新重答结果...</span>
            </div>

            <!-- 重答状态：尚无记录 -->
            <div v-else-if="!redoLatest" class="redo-status-card idle">
              <i class="fas fa-file-pen"></i>
              <span>尚无单题重答记录。点击上方按钮开始重答。</span>
            </div>

            <!-- 重答结果展示 -->
            <template v-else>
              <!-- 重答评估状态 -->
              <div class="redo-status-card" :class="redoLatest.evaluationStatus">
                <template v-if="redoLatest.evaluationStatus === 'ready'">
                  <i class="fas fa-circle-check"></i>
                  <span>最新一次重答评估已生成{{ latestRedoAtText ? ` · ${latestRedoAtText}` : '' }}</span>
                </template>
                <template v-else-if="redoLatest.evaluationStatus === 'failed'">
                  <i class="fas fa-circle-exclamation"></i>
                  <span>最新一次重答评估失败，请调整答案后重新提交。</span>
                </template>
                <template v-else>
                  <i class="fas fa-spinner fa-spin"></i>
                  <span>最新一次重答评估生成中，请稍后刷新查看。</span>
                </template>
              </div>

              <!-- 重答内容 -->
              <div class="redo-answer-block">
                <h4 class="mini-title">最新重答内容</h4>
                <p class="redo-answer-text">{{ redoLatest.answerText || '暂无重答内容' }}</p>
              </div>

              <!-- 重答结果详情：就绪时展示 -->
              <div v-if="redoLatest.evaluationStatus === 'ready'" class="redo-result-layout">
                <div class="redo-score-panel">
                  <!-- 重答得分 -->
                  <div v-if="redoHasNumericScore" class="score-badge" :class="redoScoreClass">
                    <span class="score-value">{{ redoLatest.score }}</span>
                    <span class="score-label">重答得分</span>
                  </div>
                  <!-- 重答点评 -->
                  <div class="redo-commentary">
                    <h4 class="mini-title">重答点评</h4>
                    <p>{{ redoLatest.commentary || '评语待生成' }}</p>
                  </div>
                </div>

                <!-- 重答知识域点评 -->
                <div v-if="redoDomainFeedback.length" class="domain-score-list">
                  <p class="domain-feedback-title">知识域点评</p>
                  <div v-for="item in redoDomainFeedback" :key="`${item.domainCode}-${item.domainName}`" class="domain-score-item">
                    <div class="domain-score-header">
                      <span>{{ item.domainName || item.domainCode }}</span>
                    </div>
                    <p>{{ item.commentary || '暂无点评' }}</p>
                  </div>
                </div>

                <!-- 重答亮点与薄弱点 -->
                <div class="point-grid">
                  <div class="point-card success">
                    <h4>重答亮点</h4>
                    <ul>
                      <li v-for="point in redoLatest.strengthPoints" :key="point">{{ point }}</li>
                    </ul>
                  </div>
                  <div class="point-card danger">
                    <h4>重答薄弱点</h4>
                    <ul>
                      <li v-for="point in redoLatest.weakPoints" :key="point">{{ point }}</li>
                    </ul>
                  </div>
                </div>

                <!-- 重答理想骨架 -->
                <div v-if="redoLatest.idealAnswerOutline.length" class="redo-answer-block">
                  <h4 class="mini-title">重答理想骨架</h4>
                  <ol class="outline-list">
                    <li v-for="(item, index) in redoLatest.idealAnswerOutline" :key="`${item}-${index}`">
                      {{ item }}
                    </li>
                  </ol>
                </div>

                <!-- 重答参考答案 -->
                <div v-if="redoLatest.rewrittenAnswer" class="redo-answer-block">
                  <h4 class="mini-title">重答参考答案</h4>
                  <p class="rewritten-answer">{{ redoLatest.rewrittenAnswer }}</p>
                </div>
              </div>
            </template>
          </template>
        </section>

        <!-- 黄金答题骨架卡片 -->
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

        <!-- 参考满分重构卡片 -->
        <section class="glass-card content-card">
          <h3 class="section-title">
            <i class="fas fa-star"></i>
            参考满分重构
          </h3>
          <p class="rewritten-answer">{{ detail.rewrittenAnswer }}</p>
        </section>
      </div>

      <!-- 右侧边栏 -->
      <aside class="right-column">
        <!-- AI追问卡片 -->
        <section class="glass-card sidebar-card">
          <div class="section-header">
            <h3 class="section-title">
              <i class="fas fa-robot"></i>
              AI 追问
            </h3>
            <span class="section-tip">围绕当前题继续追问，回复由后端实时生成</span>
          </div>

          <!-- 快捷问题按钮 -->
          <div class="quick-question-list">
            <button
              v-for="item in quickQuestions"
              :key="item"
              class="quick-question-btn"
              :disabled="isConsultSending || !consultContext.canConsult"
              @click="useQuickQuestion(item)"
            >
              {{ item }}
            </button>
          </div>

          <!-- 追问消息列表 -->
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
                <p>{{ resolveConsultMessageContent(message) }}</p>
                <span>{{ formatConsultCreatedAt(message.createdAt) }}</span>
              </div>
            </div>
          </div>
          <!-- 追问空状态 -->
          <div v-else class="consult-empty">
            可以继续追问「为什么失分」「怎么重答」或「能否继续追问我一轮」。
          </div>

          <!-- 追问错误提示 -->
          <p v-if="consultError" class="consult-error">{{ consultError }}</p>

          <!-- 追问输入区域 -->
          <div class="consult-input-area">
            <textarea
              v-model="consultInput"
              class="consult-input"
              rows="4"
              placeholder="继续围绕本题提问..."
            ></textarea>
            <button
              class="send-btn"
              :disabled="!consultInput.trim() || isConsultSending || !consultContext.canConsult"
              @click="sendConsult()"
            >
              <i :class="isConsultSending ? 'fas fa-spinner fa-spin' : 'fas fa-paper-plane'"></i>
              {{ isConsultSending ? '发送中' : '发送' }}
            </button>
          </div>
        </section>

        <!-- 复盘摘要卡片 -->
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

        <!-- 上下题导航卡片 -->
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
import { computed, onUnmounted, ref, watch } from 'vue'
import {
  createQuestionConsultMessage,
  createQuestionRedoAttempt,
  getLatestQuestionRedoAttempt,
  getQuestionConsultMessages,
  streamQuestionConsultMessage
} from '../api/resume'
import {
  normalizeQuestionRedoAttempt,
  shouldPollQuestionRedoAttempt,
  withQuestionRedoState
} from '../utils/questionRedoState'
import { buildQuestionDetailRenderSlices } from '../utils/questionDetailAnnotationRender'
import { buildEvaluatedDomainFeedback } from '../utils/questionDomainFeedback'
import {
  appendQuestionConsultDelta as appendConsultDelta,
  buildOptimisticQuestionConsultMessages as buildConsultOptimisticMessages,
  normalizeQuestionConsultMessages as normalizeConsultMessages,
  replaceQuestionConsultMessage as replaceConsultMessage
} from '../utils/questionConsultState'

/**
 * 重答轮询间隔（毫秒）
 * 用于轮询重答评估生成状态
 */
const REDO_POLL_INTERVAL_MS = 2000

/**
 * 题目详情页面组件
 * 
 * 本组件负责展示单道面试题的详细复盘内容，包括：
 * - 题目基本信息（知识域、题型、题目原文）
 * - 用户原始回答及标注过的亮点/薄弱点
 * - 评分与综合点评
 * - 知识域细粒度点评
 * - 黄金答题骨架
 * - 参考满分重构答案
 * - 重新作答功能（独立评估）
 * - AI 实时追问对话
 * - 上下题快速导航
 * - 题目收藏功能
 * 
 * @component
 */
export default {
  name: 'QuestionDetailPage',
  props: {
    /**
     * 题目详情数据对象
     * 包含题目、回答、评分、点评等完整信息
     * @type {Object}
     */
    detail: {
      type: Object,
      default: null
    },
    /**
     * 加载状态标志
     * @type {boolean}
     */
    loading: {
      type: Boolean,
      default: false
    }
  },
  emits: ['back', 'collect', 'navigateQuestion'],
  setup(props, { emit }) {
    // ==================== 响应式状态 ====================
    
    /** AI 追问消息列表 */
    const consultMessages = ref([])
    /** AI 追问输入框内容 */
    const consultInput = ref('')
    /** AI 追问错误信息 */
    const consultError = ref('')
    /** AI 追问发送中标志 */
    const isConsultSending = ref(false)

    /** 重新作答的答案文本 */
    const redoAnswer = ref('')
    /** 重新作答错误信息 */
    const redoError = ref('')
    /** 重新作答提交中标志 */
    const redoSubmitting = ref(false)
    /** 重新作答加载中标志 */
    const redoLoading = ref(false)
    /** 最新一次重答尝试数据 */
    const redoLatest = ref(null)
    /** 是否显示重答编辑器 */
    const showRedoComposer = ref(false)
    /** 重答轮询定时器引用 */
    let redoPollTimer = null
    /** 追问流式请求中断控制器 */
    let consultStreamAbortController = null

    // ==================== 计算属性 ====================

    /**
     * 是否有题目详情数据
     * @returns {boolean}
     */
    const hasDetail = computed(() => Boolean(props.detail))

    /**
     * 重答上下文状态
     * 包含是否可重答、禁用原因等信息
     * @returns {Object}
     */
    const redoContext = computed(() => withQuestionRedoState(
      props.detail,
      { requested: Boolean(props.detail?.redoRequested) }
    ))

    /**
     * 评估状态（归一化）
     * @returns {string} 'pending' | 'generating' | 'ready' | 'failed'
     */
    const evaluationStatus = computed(() => {
      const raw = props.detail?.evaluationStatus
      if (!raw) return 'ready'
      const normalized = String(raw).toLowerCase()
      if (['pending', 'generating', 'ready', 'failed'].includes(normalized)) {
        return normalized
      }
      return 'pending'
    })

    /**
     * 是否有数值化得分
     * @returns {boolean}
     */
    const hasNumericScore = computed(() => {
      const score = props.detail?.score
      if (score == null) return false
      return Number.isFinite(Number(score))
    })

    /**
     * 得分徽章样式类名
     * 根据分数返回高/中/低三档
     * @returns {string}
     */
    const scoreClass = computed(() => {
      const score = Number(props.detail?.score)
      if (!Number.isFinite(score)) return 'medium'
      if (score >= 80) return 'high'
      if (score >= 60) return 'medium'
      return 'low'
    })

    /**
     * 点评文本（含默认值
     * @returns {string}
     */
    const displayCommentary = computed(() => {
      const commentary = String(props.detail?.commentary || '').trim()
      return commentary || '评语待生成'
    })

    /**
     * 知识域点评列表
     * @returns {Array}
     */
    const detailDomainFeedback = computed(() => buildEvaluatedDomainFeedback(props.detail?.evaluatedDomains))

    /**
     * 摘要中的得分文本
     * @returns {string}
     */
    const summaryScoreText = computed(() => {
      if (evaluationStatus.value !== 'ready' || !hasNumericScore.value) return '--'
      return `${Number(props.detail.score)} 分`
    })

    /**
     * 重答是否有数值化得分
     * @returns {boolean}
     */
    const redoHasNumericScore = computed(() => {
      const score = redoLatest.value?.score
      return score != null && Number.isFinite(Number(score))
    })

    /**
     * 重答得分徽章样式类名
     * @returns {string}
     */
    const redoScoreClass = computed(() => {
      const score = Number(redoLatest.value?.score)
      if (!Number.isFinite(score)) return 'medium'
      if (score >= 80) return 'high'
      if (score >= 60) return 'medium'
      return 'low'
    })

    /**
     * 重答知识域点评列表
     * @returns {Array}
     */
    const redoDomainFeedback = computed(() => buildEvaluatedDomainFeedback(redoLatest.value?.evaluatedDomains))

    /**
     * 最新重答时间文本
     * @returns {string}
     */
    const latestRedoAtText = computed(() => {
      const raw = redoLatest.value?.createdAt
      if (!raw) return ''
      const date = new Date(raw)
      if (Number.isNaN(date.getTime())) return String(raw)
      return date.toLocaleString('zh-CN', { hour12: false })
    })

    /**
     * 回答标注片段
     * 将原始回答文本按标注切分为高亮片段
     * @returns {Array}
     */
    const renderSlices = computed(() => buildQuestionDetailRenderSlices({
      answerText: props.detail?.userAnswer || '',
      highlightedAnnotations: props.detail?.highlightedAnnotations || [],
      highlightedSegments: props.detail?.highlightedSegments || []
    }))

    /**
     * 注解说明列表
     * 从标注片段中提取非普通片段的注解
     * @returns {Array}
     */
    const annotationNotes = computed(() => renderSlices.value.filter((item) => item.type !== 'plain' && item.note))

    /**
     * 快捷问题列表
     * 基于当前题目生成的快捷追问
     * @returns {string[]}
     */
    const quickQuestions = computed(() => [
      '为什么这里会失分？',
      '如果重答，这题应该怎么组织结构？',
      `能基于${props.detail?.jobName || '当前岗位'}再追问我一轮吗？`
    ])

    /**
     * 返回按钮标签
     * @returns {string}
     */
    const backLabel = computed(() => props.detail?.backLabel || '返回报告')

    /**
     * AI 追问上下文
     * 包含会话ID、题目ID、是否可追问等信息
     * @returns {Object}
     */
    const consultContext = computed(() => {
      const sessionId = props.detail?.sessionId == null ? '' : String(props.detail.sessionId).trim()
      const questionId = props.detail?.questionId == null ? '' : String(props.detail.questionId).trim()
      return {
        sessionId,
        questionId,
        canConsult: Boolean(sessionId && questionId)
      }
    })

    // ==================== 工具方法 ====================

    /**
     * 停止重答轮询
     */
    const stopRedoPolling = () => {
      if (!redoPollTimer) return
      clearInterval(redoPollTimer)
      redoPollTimer = null
    }

    /**
     * 停止追问流式请求
     */
    const stopConsultStreaming = () => {
      if (!consultStreamAbortController) return
      consultStreamAbortController.abort()
      consultStreamAbortController = null
    }

    /**
     * 加载 AI 追问历史消息
     */
    const loadQuestionConsultMessages = async () => {
      if (!consultContext.value.canConsult) {
        consultMessages.value = []
        return
      }
      const messages = await getQuestionConsultMessages(
        consultContext.value.sessionId,
        consultContext.value.questionId
      )
      consultMessages.value = normalizeConsultMessages(messages)
    }

    /**
     * 加载最新重答尝试
     * @param {Object} options - 加载选项
     * @param {boolean} options.silent - 是否静默加载（不显示loading）
     */
    const loadLatestRedoAttempt = async ({ silent = false } = {}) => {
      if (!redoContext.value.canRedo) {
        redoLatest.value = null
        redoLoading.value = false
        stopRedoPolling()
        return
      }

      if (!silent) {
        redoLoading.value = true
      }
      try {
        const latest = await getLatestQuestionRedoAttempt(
          redoContext.value.sessionId,
          redoContext.value.questionId
        )
        redoLatest.value = normalizeQuestionRedoAttempt(latest)
        if (!shouldPollQuestionRedoAttempt(redoLatest.value)) {
          stopRedoPolling()
        }
      } catch (error) {
        if (!silent) {
          redoError.value = error?.message || '加载最新重答失败，请稍后重试。'
        }
        stopRedoPolling()
      } finally {
        if (!silent) {
          redoLoading.value = false
        }
      }
    }

    /**
     * 启动重答轮询
     * 定期检查重答评估是否生成完成
     */
    const startRedoPolling = () => {
      stopRedoPolling()
      if (!redoContext.value.canRedo || !shouldPollQuestionRedoAttempt(redoLatest.value)) {
        return
      }
      redoPollTimer = window.setInterval(async () => {
        try {
          await loadLatestRedoAttempt({ silent: true })
          if (!shouldPollQuestionRedoAttempt(redoLatest.value)) {
            stopRedoPolling()
          }
        } catch (_) {
          stopRedoPolling()
        }
      }, REDO_POLL_INTERVAL_MS)
    }

    /**
     * 同步最新重答尝试
     * 手动刷新并重启动轮询
     */
    const syncLatestRedoAttempt = async () => {
      redoError.value = ''
      await loadLatestRedoAttempt()
      startRedoPolling()
    }

    // ==================== 事件处理方法 ====================

    /**
     * 发送 AI 追问
     * @param {string} presetQuestion - 预置问题（可选，不传则使用输入框内容）
     */
    const sendConsult = async (presetQuestion = '') => {
      const content = (presetQuestion || consultInput.value).trim()
      if (!content || isConsultSending.value || !consultContext.value.canConsult) return

      consultError.value = ''
      isConsultSending.value = true
      stopConsultStreaming()

      let created = null
      try {
        // 创建追问消息
        created = await createQuestionConsultMessage(
          consultContext.value.sessionId,
          consultContext.value.questionId,
          { content }
        )
        // 乐观更新：立即添加用户消息和占位的助理消息
        consultMessages.value = [
          ...consultMessages.value,
          ...buildConsultOptimisticMessages(content, created)
        ]
        consultInput.value = ''

        // 流式接收助理回复
        const controller = new AbortController()
        consultStreamAbortController = controller
        await streamQuestionConsultMessage(
          consultContext.value.sessionId,
          consultContext.value.questionId,
          created.assistantMessageId,
          {
            // 增量更新：接收文本片段
            onDelta(payload) {
              consultMessages.value = appendConsultDelta(
                consultMessages.value,
                created.assistantMessageId,
                payload?.text || ''
              )
            },
            // 完成：替换完整内容
            onDone(payload) {
              consultMessages.value = replaceConsultMessage(
                consultMessages.value,
                payload?.assistantMessageId || created.assistantMessageId,
                {
                  content: payload?.content || '',
                  status: payload?.status || 'ready'
                }
              )
            },
            // 错误：标记为失败
            onError(payload) {
              consultMessages.value = replaceConsultMessage(
                consultMessages.value,
                payload?.assistantMessageId || created.assistantMessageId,
                {
                  status: payload?.status || 'failed'
                }
              )
              consultError.value = payload?.message || 'AI 追问暂时失败，请稍后重试。'
            }
          },
          controller.signal
        )
      } catch (error) {
        // 非中断错误处理
        if (error?.name !== 'AbortError') {
          consultError.value = error?.message || 'AI 追问暂时失败，请稍后重试。'
          if (created?.assistantMessageId) {
            consultMessages.value = replaceConsultMessage(
              consultMessages.value,
              created.assistantMessageId,
              { status: 'failed' }
            )
          }
        }
      } finally {
        // 清理
        if (!consultStreamAbortController || consultStreamAbortController.signal.aborted) {
          consultStreamAbortController = null
        }
        if (consultStreamAbortController && !consultStreamAbortController.signal.aborted) {
          consultStreamAbortController = null
        }
        isConsultSending.value = false
      }
    }

    /**
     * 使用快捷问题
     * @param {string} question - 快捷问题文本
     */
    const useQuickQuestion = (question) => {
      sendConsult(question)
    }

    /**
     * 开始重新作答
     * 打开编辑器，如有历史重答则预填充
     */
    const beginRedo = () => {
      if (!redoContext.value.canRedo) return
      showRedoComposer.value = true
      if (!redoAnswer.value.trim() && redoLatest.value?.answerText) {
        redoAnswer.value = redoLatest.value.answerText
      }
    }

    /**
     * 提交重新作答
     */
    const submitRedo = async () => {
      if (!redoContext.value.canRedo || !redoAnswer.value.trim() || redoSubmitting.value) return

      redoError.value = ''
      redoSubmitting.value = true
      stopRedoPolling()
      try {
        const created = await createQuestionRedoAttempt(
          redoContext.value.sessionId,
          redoContext.value.questionId,
          { answerText: redoAnswer.value.trim() }
        )
        redoLatest.value = normalizeQuestionRedoAttempt(created)
        showRedoComposer.value = true
        startRedoPolling()
      } catch (error) {
        redoError.value = error?.message || '提交重答失败，请稍后重试。'
      } finally {
        redoSubmitting.value = false
      }
    }

    /**
     * 返回上一页
     */
    const handleBack = () => {
      emit('back')
    }

    /**
     * 收藏题目
     */
    const handleCollect = () => {
      emit('collect', props.detail)
    }

    /**
     * 导航到上一题或下一题
     * @param {number} delta - 偏移量，-1为上一题，1为下一题
     */
    const navigateQuestion = (delta) => {
      emit('navigateQuestion', delta)
    }

    /**
     * 格式化追问消息创建时间
     * @param {string} value - 时间值
     * @returns {string}
     */
    const formatConsultCreatedAt = (value) => {
      if (!value) return ''
      const date = new Date(value)
      if (Number.isNaN(date.getTime())) return String(value)
      return date.toLocaleTimeString('zh-CN', {
        hour: '2-digit',
        minute: '2-digit'
      })
    }

    /**
     * 解析追问消息内容
     * 根据状态返回不同的显示文本
     * @param {Object} message - 消息对象
     * @returns {string}
     */
    const resolveConsultMessageContent = (message) => {
      const content = String(message?.content || '').trim()
      if (content) return content
      if (message?.role === 'assistant' && message?.status === 'generating') {
        return '正在生成回复...'
      }
      if (message?.role === 'assistant' && message?.status === 'cancelled') {
        return '该条追问已取消，请重新发起。'
      }
      if (message?.role === 'assistant' && message?.status === 'failed') {
        return '该条追问生成失败，请重新发起。'
      }
      return '暂无内容'
    }

    // ==================== 监听器 ====================

    /**
     * 监听题目详情变化
     * 切换题目时重置状态并加载新数据
     */
    watch(
      consultContext,
      async () => {
        // 重置追问状态
        consultInput.value = ''
        consultError.value = ''
        isConsultSending.value = false
        stopConsultStreaming()
        if (consultContext.value.canConsult) {
          try {
            await loadQuestionConsultMessages()
          } catch (error) {
            consultMessages.value = []
            consultError.value = error?.message || '加载 AI 追问历史失败，请稍后重试。'
          }
        } else {
          consultMessages.value = []
        }

        // 重置重答状态
        stopRedoPolling()
        redoAnswer.value = ''
        redoError.value = ''
        redoSubmitting.value = false
        redoLoading.value = false
        redoLatest.value = null
        showRedoComposer.value = Boolean(redoContext.value.canRedo && redoContext.value.redoRequested)
        if (redoContext.value.canRedo) {
          syncLatestRedoAttempt().catch(() => {})
        }
      },
      { immediate: true }
    )

    // ==================== 生命周期钩子 ====================

    /**
     * 组件卸载时清理资源
     */
    onUnmounted(() => {
      stopRedoPolling()
      stopConsultStreaming()
    })

    return {
      consultMessages,
      consultInput,
      consultError,
      isConsultSending,
      hasDetail,
      redoContext,
      redoAnswer,
      redoError,
      redoSubmitting,
      redoLoading,
      redoLatest,
      showRedoComposer,
      evaluationStatus,
      hasNumericScore,
      scoreClass,
      displayCommentary,
      detailDomainFeedback,
      summaryScoreText,
      redoHasNumericScore,
      redoScoreClass,
      redoDomainFeedback,
      latestRedoAtText,
      renderSlices,
      annotationNotes,
      quickQuestions,
      consultContext,
      formatConsultCreatedAt,
      resolveConsultMessageContent,
      backLabel,
      handleBack,
      handleCollect,
      navigateQuestion,
      sendConsult,
      useQuickQuestion,
      beginRedo,
      submitRedo,
      syncLatestRedoAttempt
    }
  }
}
</script>

<style scoped>
/**
 * 页面头部样式
 */
.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20px;
  padding: 20px 24px;
  margin-bottom: 24px;
}

/**
 * 头部左侧区域
 */
.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

/**
 * 通用按钮样式
 */
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

/**
 * 返回按钮样式
 */
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

/**
 * 页面标题样式
 */
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

/**
 * 收藏按钮样式
 */
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

/**
 * 状态卡片样式（加载/无数据）
 */
.state-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  min-height: 260px;
  color: var(--text-secondary);
}

/**
 * 详情布局：左右两栏
 */
.detail-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.6fr) minmax(320px, 0.9fr);
  gap: 24px;
}

/**
 * 左右两栏通用样式
 */
.left-column,
.right-column {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/**
 * 内容卡片和侧边栏卡片通用样式
 */
.content-card,
.sidebar-card {
  padding: 24px;
}

/**
 * 元信息行样式
 */
.meta-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 20px;
}

/**
 * 元标签样式
 */
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

/**
 * 区块头部样式
 */
.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

/**
 * 区块标题样式
 */
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

/**
 * 区块提示文字样式
 */
.section-tip {
  color: var(--text-secondary);
  font-size: 12px;
}

/**
 * 通用文本样式
 */
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

/**
 * 标注回答样式
 */
.annotated-answer {
  display: block;
  margin: 0;
  padding: 16px 18px;
  border-radius: var(--radius-md);
  border: 1px solid rgba(148, 163, 184, 0.28);
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 8px 24px rgba(148, 163, 184, 0.12);
  color: #1f2937;
  line-height: 1.9;
  white-space: pre-wrap;
  max-height: 320px;
  overflow-y: auto;
}

/**
 * 回答片段通用样式
 */
.answer-segment {
  color: inherit;
}

.answer-segment-plain {
  color: inherit;
}

.answer-segment.strength {
  color: #0f9f6e;
  font-weight: 600;
}

.answer-segment.weakness {
  color: #d14343;
  font-weight: 600;
}

/**
 * 空回答样式
 */
.empty-answer {
  padding: 16px 18px;
  border: 1px dashed rgba(239, 68, 68, 0.35);
  border-radius: var(--radius-md);
  color: var(--text-secondary);
  line-height: 1.7;
}

/**
 * 注解说明列表样式
 */
.annotation-notes {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 14px;
}

/**
 * 单个注解样式
 */
.annotation-note {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 12px 14px;
  border: 1px solid transparent;
  border-radius: 12px;
  font-size: 13px;
  line-height: 1.7;
}

.annotation-note i {
  margin-top: 2px;
}

.annotation-note.strength {
  background: #f5fbf8;
  border-color: #d7eee3;
  color: #24443a;
}

.annotation-note.strength i {
  color: #0f9f6e;
}

.annotation-note.weakness {
  background: #fff7f7;
  border-color: #f2d6d6;
  color: #5b2d2d;
}

.annotation-note.weakness i {
  color: #d14343;
}

/**
 * 评分概览区域样式
 */
.score-overview {
  display: flex;
  gap: 18px;
  align-items: center;
  margin-bottom: 18px;
}

/**
 * 得分徽章样式
 */
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

/**
 * 得分数值样式
 */
.score-value {
  font-size: 32px;
  font-weight: 700;
  color: var(--text-primary);
}

/**
 * 得分标签样式
 */
.score-label {
  font-size: 12px;
  color: var(--text-secondary);
}

/**
 * 评语区域样式
 */
.score-copy {
  flex: 1;
}

/**
 * 评估状态样式
 */
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

/**
 * 重答禁用/状态卡片通用样式
 */
.redo-disabled,
.redo-status-card,
.redo-answer-block {
  padding: 14px 16px;
  border-radius: var(--radius-md);
  border: 1px solid transparent;
}

/**
 * 重答禁用样式
 */
.redo-disabled {
  display: flex;
  align-items: center;
  gap: 10px;
  background: rgba(239, 68, 68, 0.08);
  border-color: rgba(239, 68, 68, 0.22);
  color: var(--text-secondary);
}

/**
 * 重答操作按钮区域
 */
.redo-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}

/**
 * 重答操作按钮样式
 */
.redo-action-btn,
.redo-submit-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 10px 16px;
  border-radius: var(--radius-md);
  border: 1px solid var(--glass-border);
  background: var(--glass-bg);
  color: var(--text-primary);
  cursor: pointer;
  font-family: inherit;
  transition: all 0.2s ease;
}

.redo-action-btn.primary,
.redo-submit-btn {
  background: linear-gradient(135deg, var(--primary-color), var(--primary-light));
  border-color: transparent;
  color: white;
}

.redo-action-btn:disabled,
.redo-submit-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

/**
 * 重答编辑器样式
 */
.redo-composer {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 14px;
}

/**
 * 重答输入框样式
 */
.redo-input {
  width: 100%;
  padding: 14px 16px;
  border-radius: var(--radius-md);
  border: 1px solid var(--glass-border);
  background: rgba(15, 23, 42, 0.28);
  color: var(--text-primary);
  resize: vertical;
  font-family: inherit;
}

/**
 * 重答编辑器操作区域
 */
.redo-composer-actions {
  display: flex;
  justify-content: flex-end;
}

/**
 * 重答错误样式
 */
.redo-error {
  margin: 0 0 14px;
  color: #f87171;
  font-size: 13px;
}

/**
 * 重答状态卡片样式
 */
.redo-status-card {
  display: flex;
  align-items: center;
  gap: 10px;
  color: var(--text-primary);
  margin-bottom: 14px;
}

.redo-status-card.idle,
.redo-status-card.pending {
  background: rgba(148, 163, 184, 0.12);
  border-color: rgba(148, 163, 184, 0.28);
}

.redo-status-card.generating {
  background: rgba(59, 130, 246, 0.12);
  border-color: rgba(59, 130, 246, 0.28);
}

.redo-status-card.ready {
  background: rgba(16, 185, 129, 0.12);
  border-color: rgba(16, 185, 129, 0.24);
}

.redo-status-card.failed {
  background: rgba(239, 68, 68, 0.12);
  border-color: rgba(239, 68, 68, 0.28);
}

/**
 * 小标题样式
 */
.mini-title {
  margin: 0 0 10px;
  font-size: 14px;
  color: var(--text-primary);
}

/**
 * 重答内容区域样式
 */
.redo-answer-block {
  background: rgba(15, 23, 42, 0.24);
  margin-bottom: 14px;
}

/**
 * 重答文本样式
 */
.redo-answer-text,
.redo-commentary p {
  margin: 0;
  color: var(--text-primary);
  line-height: 1.8;
  white-space: pre-wrap;
}

/**
 * 重答结果布局样式
 */
.redo-result-layout {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

/**
 * 重答评分面板样式
 */
.redo-score-panel {
  display: flex;
  align-items: center;
  gap: 18px;
}

/**
 * 重答点评样式
 */
.redo-commentary {
  flex: 1;
}

/**
 * 知识域点评列表样式
 */
.domain-score-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 18px;
}

/**
 * 知识域点评标题样式
 */
.domain-feedback-title {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
  color: var(--text-secondary);
}

/**
 * 单个知识域点评项样式
 */
.domain-score-item {
  padding: 14px 16px;
  border-radius: var(--radius-md);
  background: rgba(15, 23, 42, 0.24);
}

/**
 * 知识域点评头部样式
 */
.domain-score-header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
  color: var(--text-primary);
}

/**
 * 知识域点评文本样式
 */
.domain-score-item p {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--text-secondary);
}

/**
 * 亮点/薄弱点网格样式
 */
.point-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

/**
 * 点卡片样式
 */
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

/**
 * 快捷问题列表样式
 */
.quick-question-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin: 14px 0 16px;
}

/**
 * 快捷问题按钮样式
 */
.quick-question-btn {
  padding: 8px 12px;
  background: rgba(102, 126, 234, 0.08);
  color: var(--primary-color);
  font-size: 12px;
}

.quick-question-btn:hover {
  background: rgba(102, 126, 234, 0.14);
}

.quick-question-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

/**
 * 追问消息列表样式
 */
.consult-messages {
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-height: 360px;
  overflow-y: auto;
  margin-bottom: 14px;
}

/**
 * 单个追问消息样式
 */
.consult-item {
  display: flex;
  gap: 10px;
}

.consult-item.user {
  flex-direction: row-reverse;
}

/**
 * 追问头像样式
 */
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

/**
 * 追问气泡样式
 */
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

/**
 * 追问错误样式
 */
.consult-error {
  color: #f87171;
  font-size: 13px;
  margin-bottom: 12px;
}

/**
 * 追问输入区域样式
 */
.consult-input-area {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

/**
 * 追问输入框样式
 */
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

/**
 * 发送按钮样式
 */
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

/**
 * 摘要网格样式
 */
.summary-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

/**
 * 摘要项样式
 */
.summary-item {
  padding: 14px;
  border-radius: var(--radius-md);
  background: rgba(15, 23, 42, 0.24);
  display: flex;
  flex-direction: column;
  gap: 6px;
}

/**
 * 摘要标签样式
 */
.summary-label {
  font-size: 12px;
  color: var(--text-secondary);
}

.summary-item strong {
  color: var(--text-primary);
}

/**
 * 导航操作区域样式
 */
.navigation-actions {
  display: flex;
  gap: 12px;
}

/**
 * 导航按钮样式
 */
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

/**
 * 响应式：中等屏幕以下改为单列布局
 */
@media (max-width: 1080px) {
  .detail-layout {
    grid-template-columns: 1fr;
  }

  .summary-grid,
  .point-grid {
    grid-template-columns: 1fr;
  }

  .redo-score-panel {
    flex-direction: column;
    align-items: flex-start;
  }
}

/**
 * 响应式：小屏幕以下调整头部和操作区域
 */
@media (max-width: 768px) {
  .detail-header {
    flex-direction: column;
    align-items: stretch;
  }

  .header-left {
    flex-direction: column;
    align-items: flex-start;
  }

  .redo-actions,
  .navigation-actions {
    flex-direction: column;
  }
}
</style>
