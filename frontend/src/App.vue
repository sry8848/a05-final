<template>
  <div class="background-decoration">
    <div class="blob blob-1"></div>
    <div class="blob blob-2"></div>
    <div class="blob blob-3"></div>
  </div>

  <LoginPage 
    v-if="!isLoggedIn && !showRegister && !showAdminLogin" 
    @loginSuccess="handleLoginSuccess"
    @goToRegister="goToRegister"
    @goToAdminLogin="goToAdminLogin"
  />

  <AdminLoginPage 
    v-else-if="!isLoggedIn && showAdminLogin"
    @loginSuccess="handleAdminLoginSuccess"
    @goToUserLogin="goToUserLogin"
  />

  <RegisterPage 
    v-else-if="!isLoggedIn && showRegister"
    @registerSuccess="handleRegisterSuccess"
    @goToLogin="goToLogin"
  />

  <template v-else>
    <AdminLayout 
      v-if="isAdmin"
      :user="user"
      :isDarkMode="isDarkMode"
      @logout="handleLogout"
      @toggleDarkMode="toggleDarkMode"
    />
    <template v-else>
      <QuestionDetailPage
        v-if="showQuestionDetail"
        :detail="selectedQuestionDetail"
        :loading="questionDetailLoading"
        @back="handleCloseQuestionDetail"
        @collect="handleCollectQuestion"
        @navigateQuestion="handleNavigateQuestionDetail"
      />
      <InterviewReportGeneratingPage
        v-else-if="showReportGeneratingPage"
        :status="reportGeneratingStatus"
        :jobName="reportGeneratingJobName"
        @goHistory="handleGeneratingGoHistory"
        @refreshStatus="handleGeneratingRefreshStatus"
        @restart="handleGeneratingRestart"
      />
      <InterviewResultPage 
        v-else-if="showResultPage"
        :resultData="interviewResult"
        @goBack="handleResultGoBack"
        @restart="handleResultRestart"
        @showQuestionDetail="handleShowQuestionDetailFromResult"
      />
      <RadarChartPage 
        v-else-if="showRadarPage"
        @goBack="showRadarPage = false"
      />
      <ScoreTrendPage 
        v-else-if="showScoreTrendPage"
        @goBack="showScoreTrendPage = false"
      />
      <div v-else class="main-container" :class="{ 'interview-fullscreen': isInterviewRunning }">
        <Sidebar 
          v-show="!isInterviewRunning"
          :currentPage="currentPage" 
          :user="user"
          :historyHasUnread="historyHasUnread"
          @navigate="navigateTo"
          @logout="handleLogout"
        />
        
        <main class="content-area">
          <GrowthCenterPage 
            v-if="currentPage === 'growth'" 
            :user="user"
            @navigate="navigateTo"
            @goToInterview="handleGoToInterview"
          />
          <InterviewPage 
            v-else-if="currentPage === 'interview'"
            ref="interviewPage"
            :fullscreen="isInterviewRunning"
            @interviewStart="isInterviewRunning = true"
            @interviewEnd="handleInterviewEnd"
          />
          <HistoryPage
            v-else-if="currentPage === 'history'"
            @goToQuestionBank="openQuestionBank"
            @showInterviewDetail="handleShowInterviewDetail"
          />
          <QuestionBankPage
            v-else-if="currentPage === 'questionBank'"
            @showDetail="handleShowQuestionDetailFromBank"
            @redo="handleRedoQuestionFromBank"
          />
          <ResumesPage v-else-if="currentPage === 'resumes'" />
          <AnalysisPage v-else-if="currentPage === 'analysis'" />
          <SettingsPage 
            v-else-if="currentPage === 'settings'"
            :isDarkMode="isDarkMode"
            :user="user"
            @toggleDarkMode="toggleDarkMode"
            @saveSettings="saveSettings"
          />
        </main>
      </div>
    </template>
  </template>

  <div v-if="notification.show" class="notification" :class="`notification-${notification.type}`">
    <i :class="notificationIcon"></i>
    <span>{{ notification.message }}</span>
  </div>
</template>

<script>
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import Sidebar from './components/Sidebar.vue'
import InterviewPage from './components/InterviewPage.vue'
import HistoryPage from './components/HistoryPage.vue'
import AnalysisPage from './components/AnalysisPage.vue'
import SettingsPage from './components/SettingsPage.vue'
import LoginPage from './components/LoginPage.vue'
import RegisterPage from './components/RegisterPage.vue'
import InterviewResultPage from './components/InterviewResultPage.vue'
import QuestionBankPage from './components/QuestionBankPage.vue'
import GrowthCenterPage from './components/GrowthCenterPage.vue'
import RadarChartPage from './components/RadarChartPage.vue'
import ScoreTrendPage from './components/ScoreTrendPage.vue'
import QuestionDetailPage from './components/QuestionDetailPage.vue'
import InterviewReportGeneratingPage from './components/InterviewReportGeneratingPage.vue'
import AdminLoginPage from './components/AdminLoginPage.vue'
import AdminLayout from './components/AdminLayout.vue'
import ResumesPage from './components/ResumesPage.vue'
import {
  getCurrentAdmin,
  getCurrentUser,
  logoutAdmin,
  logoutUser
} from './api/auth'
import { createQuestionBankItem, getInterviewQuestionDetail, getInterviewReport } from './api/resume'
import {
  applyReadyReportToInterviewRecord,
  normalizeStoredInterviewRecord,
  normalizeDisplayReportStatus,
  upsertReadyInterviewRecord
} from './utils/growthHistoryState'
import {
  resolveLogoutViewState,
  resolveUserLoginViewState
} from './utils/authViewState'
import { normalizeHighlightedAnnotations } from './utils/questionDetailAnnotationRender'
import {
  ADMIN_TOKEN_KEY,
  USER_TOKEN_KEY,
  clearPersistedAuthSession,
  persistAdminSession,
  persistUserSession,
  restoreAuthSession
} from './utils/authSession'
import { mergeInterviewResultWithReport as mergeResultWithReport } from './utils/interviewResultState'
import { withQuestionRedoState } from './utils/questionRedoState'

export default {
  name: 'App',
  components: {
    Sidebar,
    InterviewPage,
    HistoryPage,
    ResumesPage,
    AnalysisPage,
    SettingsPage,
    LoginPage,
    RegisterPage,
    InterviewResultPage,
    QuestionBankPage,
    GrowthCenterPage,
    RadarChartPage,
    ScoreTrendPage,
    QuestionDetailPage,
    InterviewReportGeneratingPage,
    AdminLoginPage,
    AdminLayout
  },
  setup() {
    const isLoggedIn = ref(false)
    const showRegister = ref(false)
    const showAdminLogin = ref(false)
    const isAdmin = ref(false)
    const currentPage = ref('growth')
    const isDarkMode = ref(false)
    const isInterviewRunning = ref(false)
    const interviewPage = ref(null)
    const showResultPage = ref(false)
    const showReportGeneratingPage = ref(false)
    const reportGeneratingStatus = ref('generating')
    const reportGeneratingJobName = ref('本场面试')
    const pendingGeneratingResult = ref(null)
    const pendingGeneratingSessionId = ref(null)
    const resultEntrySource = ref('live')
    const historyHasUnread = ref(false)
    const interviewResult = ref(null)
    const showRadarPage = ref(false)
    const showScoreTrendPage = ref(false)
    const showQuestionDetail = ref(false)
    const questionDetailLoading = ref(false)
    const selectedQuestionDetail = ref(null)
    const questionDetailContext = ref(null)
    const questionDetailRequestSeq = ref(0)
    const INTERVIEW_RECORDS_KEY = 'interviewRecords'
    const INTERVIEW_RECORDS_UPDATED_EVENT = 'interview-records-updated'
    const HISTORY_UNREAD_DOT_KEY = 'historyUnreadDot'
    const REPORT_POLL_INTERVAL_MS = 3000
    const pollingSessionLocks = new Set()
    let reportPollingTimer = null
    
    const user = reactive({
      name: '面试者',
      email: '',
      avatarUrl: '',
      level: 'Lv.1 初级工程师',
      totalInterviews: 12,
      avgScore: 85,
      totalHours: 36,
      points: 0
    })

    const notification = reactive({
      show: false,
      message: '',
      type: 'info'
    })

    const notificationIcon = computed(() => {
      const icons = {
        success: 'fas fa-check-circle',
        error: 'fas fa-times-circle',
        info: 'fas fa-info-circle'
      }
      return icons[notification.type] || icons.info
    })

    const emitInterviewRecordsUpdated = () => {
      if (typeof window === 'undefined') return
      window.dispatchEvent(new CustomEvent(INTERVIEW_RECORDS_UPDATED_EVENT))
    }

    const normalizeStoredRecord = (record) => normalizeStoredInterviewRecord(record)

    const readInterviewRecords = () => {
      try {
        const raw = localStorage.getItem(INTERVIEW_RECORDS_KEY)
        const parsed = raw ? JSON.parse(raw) : []
        if (!Array.isArray(parsed)) return []
        const normalized = parsed.map(normalizeStoredRecord)
        if (JSON.stringify(normalized) !== JSON.stringify(parsed)) {
          localStorage.setItem(INTERVIEW_RECORDS_KEY, JSON.stringify(normalized))
        }
        return normalized
      } catch (error) {
        return []
      }
    }

    const writeInterviewRecords = (records) => {
      localStorage.setItem(INTERVIEW_RECORDS_KEY, JSON.stringify(records))
      emitInterviewRecordsUpdated()
    }

    const normalizeReportStatus = (value) => normalizeDisplayReportStatus(value)

    const setHistoryUnreadFlag = (value) => {
      const normalized = !!value
      historyHasUnread.value = normalized
      localStorage.setItem(HISTORY_UNREAD_DOT_KEY, normalized ? '1' : '0')
    }

    const clearHistoryUnreadFlag = () => {
      setHistoryUnreadFlag(false)
    }

    const normalizeQuestionNo = (value) => {
      const numeric = Number(value)
      if (!Number.isInteger(numeric) || numeric <= 0) return null
      return numeric
    }

    const normalizeQuestionStatus = (value) => {
      const normalized = String(value || '').trim().toLowerCase()
      if (['answered', 'skipped', 'pending'].includes(normalized)) {
        return normalized
      }
      return 'pending'
    }

    const normalizeNullableScore = (value) => {
      if (value == null || value === '') return null
      const numeric = Number(value)
      if (!Number.isFinite(numeric)) return null
      return numeric
    }

    const deriveLocalAnswerStatus = (item) => {
      if (hasOwn(item, 'status')) {
        return normalizeQuestionStatus(item.status)
      }
      const answerText = String(item?.answer ?? item?.userAnswer ?? '')
      if (!answerText) return 'pending'
      if (answerText === '[跳过]' || answerText === '[skip]') return 'skipped'
      return 'answered'
    }

    const applyReadyReportToRecord = (record, report) =>
      applyReadyReportToInterviewRecord(record, report)

    const applyFailedReportToRecord = (record) => {
      const updated = { ...record }
      updated.reportStatus = 'failed'
      updated.reportFailedAt = new Date().toISOString()
      updated.report = null
      updated.syncStatus = 'report_failed'
      return updated
    }

    const finalizeGeneratingResult = (report) => {
      const records = upsertReadyInterviewRecord(
        readInterviewRecords(),
        pendingGeneratingResult.value,
        report
      )
      writeInterviewRecords(records.slice(0, 50))
      interviewResult.value = mergeResultWithReport(pendingGeneratingResult.value || {}, report || {})
      showReportGeneratingPage.value = false
      reportGeneratingStatus.value = 'generating'
      pendingGeneratingResult.value = null
      pendingGeneratingSessionId.value = null
      showResultPage.value = true
      showNotification('面试报告已生成', 'success')
    }

    const pollGeneratingReports = async () => {
      if (!isLoggedIn.value || isAdmin.value) return

      const records = readInterviewRecords()
      if (!records.length) return

      let changed = false
      let shouldSetHistoryUnread = false
      let reportForGeneratingPage = null

      for (let index = 0; index < records.length; index += 1) {
        const record = records[index]
        const status = normalizeReportStatus(record?.reportStatus)
        if (status !== 'generating') continue

        const sessionId = normalizeSessionId(record?.sessionId)
        if (!sessionId) continue

        if (pollingSessionLocks.has(sessionId)) continue
        pollingSessionLocks.add(sessionId)
        try {
          const report = await getInterviewReport(sessionId)
          if (report?.reportStatus === 'ready') {
            records[index] = applyReadyReportToRecord(record, report)
            changed = true
            const isCurrentGeneratingSession =
              showReportGeneratingPage.value
              && normalizeSessionId(pendingGeneratingSessionId.value) === sessionId
            if (currentPage.value !== 'history' && !isCurrentGeneratingSession) {
              shouldSetHistoryUnread = true
            }
            if (showReportGeneratingPage.value && normalizeSessionId(pendingGeneratingSessionId.value) === sessionId) {
              reportForGeneratingPage = report
            }
          } else if (report?.reportStatus === 'failed') {
            records[index] = applyFailedReportToRecord(record)
            changed = true
            if (currentPage.value !== 'history') {
              shouldSetHistoryUnread = true
            }
            if (showReportGeneratingPage.value && normalizeSessionId(pendingGeneratingSessionId.value) === sessionId) {
              reportGeneratingStatus.value = 'failed'
            }
          }
        } catch (error) {
          console.warn('[App] poll report failed', { sessionId, error })
        } finally {
          pollingSessionLocks.delete(sessionId)
        }
      }

      if (changed) {
        writeInterviewRecords(records)
      }
      if (shouldSetHistoryUnread && currentPage.value !== 'history') {
        setHistoryUnreadFlag(true)
      }
      if (reportForGeneratingPage && showReportGeneratingPage.value) {
        finalizeGeneratingResult(reportForGeneratingPage)
      }
    }

    const startReportPolling = () => {
      if (reportPollingTimer) return
      reportPollingTimer = setInterval(() => {
        pollGeneratingReports().catch((error) => {
          console.warn('[App] report polling failed', error)
        })
      }, REPORT_POLL_INTERVAL_MS)
    }

    const stopReportPolling = () => {
      if (!reportPollingTimer) return
      clearInterval(reportPollingTimer)
      reportPollingTimer = null
    }

    const mapModeLabel = (mode) => {
      if (mode === 'practice') return '练习模式'
      if (mode === 'professional') return '专业模式'
      return mode || '练习模式'
    }

    const mapJobType = (jobName = '') => {
      if (jobName.includes('前端')) return 'frontend'
      if (jobName.includes('后端')) return 'backend'
      if (jobName.includes('全栈')) return 'fullstack'
      if (jobName.includes('算法')) return 'algorithm'
      return 'frontend'
    }

    const inferDomainName = (questionText = '', keywords = [], jobName = '') => {
      const content = `${questionText} ${keywords.join(' ')} ${jobName}`.toLowerCase()
      if (/(vue|react|javascript|css|html|浏览器|前端)/.test(content)) return '前端基础'
      if (/(mysql|redis|数据库|缓存|java|并发|jvm|后端)/.test(content)) return '后端基础'
      if (/(算法|复杂度|链表|树|排序)/.test(content)) return '算法与数据结构'
      if (/(项目|场景|设计|架构)/.test(content)) return '工程实践'
      return jobName.includes('前端') ? '前端综合能力' : '通用技术能力'
    }

    const inferQuestionType = (questionText = '', index = 0) => {
      if (/(如何|怎么|设计|实现)/.test(questionText)) return '场景题'
      if (/(为什么|原理|解释|区别|什么是)/.test(questionText)) return '原理题'
      if (/(项目|经历|负责)/.test(questionText)) return '项目题'
      return index === 0 ? '开场题' : '综合题'
    }

    const buildAnswerOutline = (questionText = '', keywords = []) => {
      const focusKeyword = keywords[0] || '核心原理'
      return [
        `先用一句话说明这题的核心概念，并点明与「${focusKeyword}」的关系。`,
        '再分 2 到 3 点展开关键原理、流程或边界条件。',
        '结合一个真实项目场景说明自己是如何落地的。',
        '最后补充常见误区、优化思路或取舍判断。'
      ]
    }

    const buildRewrittenAnswer = (questionText = '', keywords = [], domainName = '') => {
      const keyPhrase = keywords.length ? keywords.join('、') : '核心原理、实现细节和适用边界'
      return `如果我重新回答这题，我会先明确题目考察的是${domainName}，再围绕${keyPhrase}分点展开。随后我会结合真实项目说明这些知识点在业务中的使用方式、收益和边界，最后补充常见误区与优化思路，让回答既有原理也有实践。`
    }

    const hasFormalQuestionEvaluation = (value) => {
      if (!value || typeof value !== 'object') return false
      if (normalizeNullableScore(value.score) != null) return true
      const commentary = String(value.commentary ?? value.analysis ?? '').trim()
      if (commentary) return true
      return ['strengthPoints', 'weakPoints', 'evaluatedDomains', 'highlightedSegments', 'highlightedAnnotations'].some((key) => (
        Array.isArray(value[key]) && value[key].length > 0
      ))
    }

    const normalizeStringArray = (value) => {
      if (!Array.isArray(value)) return []
      return value
        .map((item) => String(item ?? '').trim())
        .filter(Boolean)
    }

    const normalizeSessionId = (value) => {
      if (value == null) return null
      const normalized = String(value).trim()
      return normalized ? normalized : null
    }

    const normalizeQuestionId = (value) => {
      if (value == null) return null
      const normalized = String(value).trim()
      return normalized ? normalized : null
    }

    const hasOwn = (obj, key) => Object.prototype.hasOwnProperty.call(obj || {}, key)

    const normalizeEvaluationStatus = (value) => {
      if (value == null) return 'pending'
      const normalized = String(value).trim().toLowerCase()
      if (['pending', 'generating', 'ready', 'failed'].includes(normalized)) {
        return normalized
      }
      return 'pending'
    }

    const mapBackendHighlightedSegments = (segments = []) => {
      if (!Array.isArray(segments)) return []
      return segments
        .map((item) => {
          if (!item || typeof item !== 'object') return null
          const segment = String(item.segment ?? '').trim()
          if (!segment) return null
          const labelRaw = String(item.label ?? '').trim().toLowerCase()
          const label = labelRaw === 'weakness' ? 'weakness' : 'strength'
          const comment = String(item.comment ?? '').trim()
          return { segment, label, comment }
        })
        .filter(Boolean)
    }

    const mapBackendHighlightedAnnotations = (annotations = []) => normalizeHighlightedAnnotations(annotations)

    const mergeQuestionDetailFromBackend = (fallbackDetail, backendDetail) => {
      const merged = { ...(fallbackDetail || {}) }
      const source = (backendDetail && typeof backendDetail === 'object') ? backendDetail : null
      if (!source) {
        return withQuestionRedoState(merged, { requested: Boolean(merged.redoRequested) })
      }

      // 核心字段以后端为准（包括 null），避免本地同名字段回写覆盖。
      if (hasOwn(source, 'questionId')) merged.questionId = normalizeQuestionId(source.questionId)
      if (hasOwn(source, 'questionNo')) merged.questionNumber = normalizeQuestionNo(source.questionNo)
      if (hasOwn(source, 'questionStem')) merged.questionStem = source.questionStem
      if (hasOwn(source, 'domainName')) merged.domainName = source.domainName
      if (hasOwn(source, 'questionType')) merged.questionType = source.questionType
      if (hasOwn(source, 'userAnswer')) merged.userAnswer = source.userAnswer
      if (hasOwn(source, 'answerStatus')) {
        merged.answerStatus = source.answerStatus == null ? null : normalizeQuestionStatus(source.answerStatus)
      }
      if (hasOwn(source, 'evaluationStatus')) {
        merged.evaluationStatus = normalizeEvaluationStatus(source.evaluationStatus)
      }
      if (hasOwn(source, 'score')) merged.score = source.score
      if (hasOwn(source, 'commentary')) merged.commentary = source.commentary
      if (hasOwn(source, 'rewrittenAnswer')) merged.rewrittenAnswer = source.rewrittenAnswer

      // 非核心字段：仅在后端给出时更新；缺失时保留本地派生值。
      if (hasOwn(source, 'strengthPoints')) merged.strengthPoints = source.strengthPoints
      if (hasOwn(source, 'weakPoints')) merged.weakPoints = source.weakPoints
      if (hasOwn(source, 'evaluatedDomains')) merged.evaluatedDomains = source.evaluatedDomains
      if (hasOwn(source, 'idealAnswerOutline')) merged.idealAnswerOutline = source.idealAnswerOutline
      if (hasOwn(source, 'highlightedSegments')) {
        merged.highlightedSegments = mapBackendHighlightedSegments(source.highlightedSegments)
      }
      if (hasOwn(source, 'highlightedAnnotations')) {
        merged.highlightedAnnotations = mapBackendHighlightedAnnotations(source.highlightedAnnotations)
      }
      if (hasOwn(source, 'backfillFromLocalAllowed')) {
        merged.backfillFromLocalAllowed = source.backfillFromLocalAllowed
      }

      return withQuestionRedoState(merged, { requested: Boolean(merged.redoRequested) })
    }

    const hydrateQuestionDetailFromBackend = async (baseDetail) => {
      if (!baseDetail) return
      if (!baseDetail.sessionId || !baseDetail.questionId) return

      const requestSeq = ++questionDetailRequestSeq.value
      questionDetailLoading.value = true
      try {
        const backendDetail = await getInterviewQuestionDetail(baseDetail.sessionId, baseDetail.questionId)
        if (requestSeq !== questionDetailRequestSeq.value) return
        selectedQuestionDetail.value = mergeQuestionDetailFromBackend(baseDetail, backendDetail)
      } catch (error) {
        if (requestSeq !== questionDetailRequestSeq.value) return
        console.warn('加载后端单题详情失败，回退本地详情：', error)
      } finally {
        if (requestSeq === questionDetailRequestSeq.value) {
          questionDetailLoading.value = false
        }
      }
    }

    const openQuestionDetail = async (detail, context) => {
      selectedQuestionDetail.value = detail
      questionDetailContext.value = context
      showQuestionDetail.value = true
      await hydrateQuestionDetailFromBackend(detail)
    }

    const isQuestionCollected = (recordId, questionId) => {
      try {
        const bank = JSON.parse(localStorage.getItem('questionBank') || '[]')
        return bank.some((item) => {
          const itemQuestionKey = item.localQuestionKey || item.questionId || item.id
          return item.recordId === recordId && itemQuestionKey === questionId
        })
      } catch (error) {
        return false
      }
    }

    const buildQuestionDetailViewModel = ({ source, record, result, questionIndex, sessionId = null, questionId = null }) => {
      const questionList = record?.answers || result?.answers || []
      const answer = questionList[questionIndex]
      if (!answer) return null

      const recordId = record?.id || `result-${questionIndex}`
      const jobName = record?.job || result?.jobName || '模拟面试'
      const modeLabel = mapModeLabel(record?.mode || result?.interviewMode)
      const questionStem = answer.questionStem || answer.question || ''
      const answerText = answer.userAnswer ?? answer.answer ?? ''
      const answerScore = hasOwn(answer, 'score') ? normalizeNullableScore(answer.score) : null
      const domainName = inferDomainName(questionStem, answer.keywords || [], jobName)
      const resolvedSessionId = normalizeSessionId(sessionId)
        || normalizeSessionId(result?.sessionId)
        || normalizeSessionId(result?.report?.sessionId)
        || normalizeSessionId(record?.sessionId)
      const explicitQuestionId = normalizeQuestionId(questionId) || normalizeQuestionId(answer.questionId)
      const localQuestionKey = explicitQuestionId || `${recordId}-${questionIndex}`
      const isLocalFallback = !resolvedSessionId || !explicitQuestionId
      const answerStatus = deriveLocalAnswerStatus(answer)
      const hasFormalEvaluation = hasFormalQuestionEvaluation(answer)

      return withQuestionRedoState({
        source,
        recordId,
        sessionId: resolvedSessionId,
        questionId: explicitQuestionId,
        localQuestionKey,
        isLocalFallback,
        questionIndex,
        questionNumber: normalizeQuestionNo(answer.questionNo) || questionIndex + 1,
        totalQuestions: questionList.length,
        questionStem,
        domainName,
        questionType: answer.questionType || inferQuestionType(questionStem, questionIndex),
        answerStatus,
        evaluationStatus: hasFormalEvaluation ? 'ready' : 'pending',
        userAnswer: answerText,
        highlightedAnnotations: hasFormalEvaluation ? mapBackendHighlightedAnnotations(answer.highlightedAnnotations) : [],
        highlightedSegments: hasFormalEvaluation ? mapBackendHighlightedSegments(answer.highlightedSegments) : [],
        score: hasFormalEvaluation ? answerScore : null,
        commentary: String(answer.commentary ?? '').trim() || null,
        strengthPoints: normalizeStringArray(answer.strengthPoints),
        weakPoints: normalizeStringArray(answer.weakPoints),
        evaluatedDomains: Array.isArray(answer.evaluatedDomains) ? answer.evaluatedDomains : [],
        idealAnswerOutline: buildAnswerOutline(questionStem, answer.keywords || []),
        rewrittenAnswer: hasOwn(answer, 'rewrittenAnswer')
          ? answer.rewrittenAnswer
          : buildRewrittenAnswer(questionStem, answer.keywords || [], domainName),
        isCollected: isQuestionCollected(recordId, localQuestionKey),
        keywords: answer.keywords || [],
        jobName,
        modeLabel,
        interviewDate: record?.date || new Date().toLocaleString('zh-CN'),
        hasPrev: questionIndex > 0,
        hasNext: questionIndex < questionList.length - 1,
        backfillFromLocalAllowed: true
      })
    }

    const handleLoginSuccess = (userData) => {
      const nextViewState = resolveUserLoginViewState({
        isLoggedIn: isLoggedIn.value,
        isAdmin: isAdmin.value,
        showAdminLogin: showAdminLogin.value,
        showRegister: showRegister.value
      })
      if (!userData?.token) {
        showNotification('登录返回缺少令牌，请重试', 'error')
        return
      }
      persistUserSession(localStorage, userData)
      getCurrentUser(userData.token)
        .then((currentUser) => {
          persistUserSession(localStorage, {
            token: userData.token,
            nickname: currentUser?.nickname || userData.nickname || '',
            email: currentUser?.email || ''
          })
          user.name = currentUser?.nickname || userData.nickname || user.name
          user.email = currentUser?.email || ''
          isLoggedIn.value = nextViewState.isLoggedIn
          isAdmin.value = nextViewState.isAdmin
          showAdminLogin.value = nextViewState.showAdminLogin
          showRegister.value = nextViewState.showRegister
          showNotification('登录成功，欢迎回来！', 'success')
        })
        .catch((error) => {
          clearPersistedAuthSession(localStorage)
          user.name = '面试者'
          user.email = ''
          showNotification(error?.message || '获取当前用户信息失败，请重试', 'error')
        })
    }

    const handleRegisterSuccess = (userData) => {
      const nextViewState = resolveLogoutViewState({
        isLoggedIn: isLoggedIn.value,
        isAdmin: isAdmin.value,
        showAdminLogin: showAdminLogin.value,
        currentPage: currentPage.value
      })
      user.name = userData.username
      user.email = userData.email || ''
      isLoggedIn.value = nextViewState.isLoggedIn
      isAdmin.value = nextViewState.isAdmin
      showAdminLogin.value = nextViewState.showAdminLogin
      currentPage.value = nextViewState.currentPage
      showRegister.value = false
      showNotification('注册成功，请登录后继续', 'success')
    }

    const handleLogout = async () => {
      const nextViewState = resolveLogoutViewState({
        isLoggedIn: isLoggedIn.value,
        isAdmin: isAdmin.value,
        showAdminLogin: showAdminLogin.value,
        currentPage: currentPage.value
      })
      const adminToken = localStorage.getItem(ADMIN_TOKEN_KEY)
      const userToken = localStorage.getItem(USER_TOKEN_KEY)
      try {
        if (isAdmin.value && adminToken) {
          await logoutAdmin(adminToken)
        } else if (!isAdmin.value && userToken) {
          await logoutUser(userToken)
        }
      } catch (error) {
        console.warn('[App] logout request failed', error)
      }
      clearPersistedAuthSession(localStorage)
      isLoggedIn.value = nextViewState.isLoggedIn
      isAdmin.value = nextViewState.isAdmin
      showAdminLogin.value = nextViewState.showAdminLogin
      showRegister.value = false
      currentPage.value = nextViewState.currentPage
      user.name = '面试者'
      user.email = ''
      isInterviewRunning.value = false
      showReportGeneratingPage.value = false
      pendingGeneratingResult.value = null
      pendingGeneratingSessionId.value = null
      reportGeneratingStatus.value = 'generating'
      resultEntrySource.value = 'live'
      clearHistoryUnreadFlag()
      showNotification('已退出登录', 'info')
    }

    const handleInterviewEnd = (resultData) => {
      isInterviewRunning.value = false
      if (resultData) {
        const reportStatus = normalizeReportStatus(resultData.reportStatus)
        if (reportStatus === 'generating' && normalizeSessionId(resultData.sessionId)) {
          showResultPage.value = false
          interviewResult.value = null
          pendingGeneratingResult.value = { ...resultData }
          pendingGeneratingSessionId.value = normalizeSessionId(resultData.sessionId)
          reportGeneratingStatus.value = 'generating'
          reportGeneratingJobName.value = resultData.jobName || '本场面试'
          resultEntrySource.value = 'live'
          showReportGeneratingPage.value = true
          return
        }

        showReportGeneratingPage.value = false
        pendingGeneratingResult.value = null
        pendingGeneratingSessionId.value = null
        interviewResult.value = resultData
        resultEntrySource.value = 'live'
        showResultPage.value = true
      } else {
        currentPage.value = 'growth'
      }
    }

    const goToRegister = () => {
      showRegister.value = true
    }

    const goToLogin = () => {
      showRegister.value = false
    }

    const goToAdminLogin = () => {
      showAdminLogin.value = true
    }

    const goToUserLogin = () => {
      showAdminLogin.value = false
    }

    const handleAdminLoginSuccess = (userData) => {
      if (userData?.token) {
        persistAdminSession(localStorage, userData)
      }
      isLoggedIn.value = true
      isAdmin.value = true
      showAdminLogin.value = false
      showRegister.value = false
      user.name = userData.displayName || userData.username || '管理员'
      showNotification('管理端登录成功！', 'success')
    }

    const navigateTo = (page) => {
      if (isInterviewRunning.value) {
        if (!confirm('面试正在进行中，确定要离开吗？')) {
          return
        }
        isInterviewRunning.value = false
      }
      currentPage.value = page
      if (page === 'history') {
        clearHistoryUnreadFlag()
      }
    }

    const openQuestionBank = () => {
      currentPage.value = 'questionBank'
    }

    const selectJob = (jobType) => {
      currentPage.value = 'interview'
      setTimeout(() => {
        if (interviewPage.value) {
          interviewPage.value.setJobType(jobType)
        }
      }, 100)
    }

    const handleGoToInterview = (params) => {
      currentPage.value = 'interview'
      const hasAutoParams = params && (params.auto_focus || params.auto_position || params.auto_target_role || params.auto_mode)

      setTimeout(() => {
        if (!interviewPage.value) return

        if (hasAutoParams) {
          if (interviewPage.value.setJobType) {
            interviewPage.value.setJobType(params.auto_target_role || params.auto_position || 'frontend')
          }
          if (interviewPage.value.setFocusTopic && params.auto_focus) {
            interviewPage.value.setFocusTopic(params.auto_focus)
          }
          if (interviewPage.value.setInterviewMode && params.auto_mode) {
            interviewPage.value.setInterviewMode(params.auto_mode)
          }
          return
        }

        if (interviewPage.value.setJobType) {
          interviewPage.value.setJobType('frontend')
        }
      }, 100)
    }

    const readQuestionBank = () => {
      try {
        const bank = JSON.parse(localStorage.getItem('questionBank') || '[]')
        return Array.isArray(bank) ? bank : []
      } catch (error) {
        return []
      }
    }

    const buildQuestionDetailFromBankItem = (item, index = 0, list = []) => {
      if (!item) return null
      const resolvedSessionId = normalizeSessionId(item.sessionId)
      const explicitQuestionId = normalizeQuestionId(item.questionId)
      const localQuestionKey = normalizeQuestionId(item.localQuestionKey) || explicitQuestionId || normalizeQuestionId(item.id) || `question-bank-${index}`
      const isLocalFallback = !resolvedSessionId || !explicitQuestionId
      const bankScore = normalizeNullableScore(item.score)
      const hasFormalEvaluation = hasFormalQuestionEvaluation(item)

      const detail = {
        source: 'questionBank',
        recordId: item.recordId || 'question-bank',
        sessionId: resolvedSessionId,
        questionId: explicitQuestionId,
        localQuestionKey,
        isLocalFallback,
        questionIndex: index,
        questionNumber: index + 1,
        totalQuestions: list.length || 1,
        questionStem: item.questionStem || item.question || '未命名题目',
        domainName: item.domainName || inferDomainName(item.questionStem || item.question, item.keywords || [], item.jobName || item.job || ''),
        questionType: item.questionType || inferQuestionType(item.questionStem || item.question, index),
        answerStatus: !(item.userAnswer || item.answer) ? 'skipped' : 'answered',
        evaluationStatus: hasFormalEvaluation ? 'ready' : 'pending',
        userAnswer: item.userAnswer || item.answer || '',
        highlightedAnnotations: hasFormalEvaluation ? mapBackendHighlightedAnnotations(item.highlightedAnnotations) : [],
        highlightedSegments: hasFormalEvaluation ? mapBackendHighlightedSegments(item.highlightedSegments) : [],
        score: hasFormalEvaluation ? bankScore : null,
        commentary: String(item.analysis ?? item.commentary ?? '').trim() || null,
        strengthPoints: normalizeStringArray(item.strengthPoints),
        weakPoints: normalizeStringArray(item.weakPoints),
        evaluatedDomains: Array.isArray(item.evaluatedDomains) ? item.evaluatedDomains : [],
        idealAnswerOutline: item.idealAnswerOutline || buildAnswerOutline(item.questionStem || item.question || '', item.keywords || []),
        rewrittenAnswer: item.rewrittenAnswer || item.standardAnswer || buildRewrittenAnswer(item.questionStem || item.question || '', item.keywords || [], item.domainName || '通用技术能力'),
        isCollected: true,
        keywords: item.keywords || [],
        jobName: item.jobName || item.job || '模拟面试',
        modeLabel: item.modeLabel || '练习模式',
        interviewDate: item.createdAt || item.date || new Date().toLocaleString('zh-CN'),
        hasPrev: index > 0,
        hasNext: index < list.length - 1,
        backLabel: '返回问答库',
        backfillFromLocalAllowed: true
      }

      return withQuestionRedoState(detail)
    }

    const handleShowQuestionDetailFromBank = (bankItem) => {
      const bank = readQuestionBank()
      const index = bank.findIndex((item) => item.id === bankItem.id)
      const context = {
        source: 'questionBank',
        itemId: bankItem.id,
        questionIndex: index >= 0 ? index : 0
      }
      const detail = buildQuestionDetailFromBankItem(bankItem, index >= 0 ? index : 0, bank)
      openQuestionDetail(detail, context)
    }

    const handleRedoQuestionFromBank = (bankItem) => {
      const bank = readQuestionBank()
      const index = bank.findIndex((item) => item.id === bankItem.id)
      const context = {
        source: 'questionBank',
        itemId: bankItem.id,
        questionIndex: index >= 0 ? index : 0
      }
      const baseDetail = buildQuestionDetailFromBankItem(bankItem, index >= 0 ? index : 0, bank)
      const detail = withQuestionRedoState(baseDetail, { requested: true })
      if (!detail.canRedo) {
        showNotification(detail.redoDisabledReason, 'info')
      }
      openQuestionDetail(detail, context)
    }

    const toggleDarkMode = () => {
      isDarkMode.value = !isDarkMode.value
      document.documentElement.setAttribute(
        'data-theme',
        isDarkMode.value ? 'dark' : 'light'
      )
      saveUserSettings()
    }

    const showNotification = (message, type = 'info') => {
      notification.message = message
      notification.type = type
      notification.show = true
      setTimeout(() => {
        notification.show = false
      }, 3000)
    }

    const saveUserSettings = () => {
      const settings = {
        isDarkMode: isDarkMode.value,
        user: { ...user },
        isLoggedIn: isLoggedIn.value
      }
      localStorage.setItem('aiInterviewSettings', JSON.stringify(settings))
      showNotification('设置已保存', 'success')
    }

    const saveSettings = (updatedUser) => {
      if (updatedUser && typeof updatedUser === 'object') {
        Object.assign(user, updatedUser)
      }
      saveUserSettings()
    }

    const handleResultGoBack = () => {
      showResultPage.value = false
      interviewResult.value = null
      currentPage.value = resultEntrySource.value === 'history' ? 'history' : 'growth'
    }

    const handleResultRestart = () => {
      showResultPage.value = false
      interviewResult.value = null
      currentPage.value = 'interview'
    }

    const handleGeneratingGoHistory = () => {
      showReportGeneratingPage.value = false
      currentPage.value = 'history'
      clearHistoryUnreadFlag()
    }

    const handleGeneratingRefreshStatus = async () => {
      const sessionId = normalizeSessionId(pendingGeneratingSessionId.value)
      if (!sessionId) return
      try {
        const report = await getInterviewReport(sessionId)
        if (report?.reportStatus === 'ready') {
          const records = upsertReadyInterviewRecord(readInterviewRecords(), pendingGeneratingResult.value, report)
          writeInterviewRecords(records.slice(0, 50))
          finalizeGeneratingResult(report)
          return
        }
        reportGeneratingStatus.value = report?.reportStatus === 'failed' ? 'failed' : 'generating'
      } catch (error) {
        showNotification(error?.message || '重新拉取报告失败，请稍后重试', 'error')
      }
    }

    const handleGeneratingRestart = () => {
      showReportGeneratingPage.value = false
      pendingGeneratingResult.value = null
      pendingGeneratingSessionId.value = null
      resultEntrySource.value = 'live'
      currentPage.value = 'interview'
    }

    const handleShowQuestionDetailFromResult = ({ index = 0, questionId = null, sessionId = null } = {}) => {
      const detail = buildQuestionDetailViewModel({
        source: 'result',
        result: interviewResult.value,
        questionIndex: index,
        questionId,
        sessionId
      })
      const context = {
        source: 'result',
        questionIndex: index
      }
      openQuestionDetail(detail, context)
    }

    const handleShowInterviewDetail = async (recordOrItem) => {
      if (!recordOrItem || typeof recordOrItem !== 'object') return

      const sessionId = normalizeSessionId(recordOrItem.sessionId || recordOrItem.id)
      if (!sessionId) return

      const records = readInterviewRecords()
      let record = records.find((item) => normalizeSessionId(item.sessionId) === sessionId)

      if (!record) {
        record = {
          id: sessionId,
          sessionId,
          job: recordOrItem.job || recordOrItem.title || '模拟面试',
          jobName: recordOrItem.job || recordOrItem.title || '模拟面试',
          date: recordOrItem.date || new Date().toLocaleString('zh-CN'),
          score: Number.isFinite(Number(recordOrItem.score)) ? Number(recordOrItem.score) : null,
          duration: recordOrItem.duration || '--',
          questions: Number(recordOrItem.questions) || 0,
          correct: Number.isFinite(Number(recordOrItem.correct)) ? Number(recordOrItem.correct) : null,
          answers: [],
          mode: recordOrItem.mode || 'practice',
          reportStatus: recordOrItem.reportStatus || 'generating'
        }
        records.unshift(record)
        writeInterviewRecords(records.slice(0, 50))
      }

      const displayName = record.jobName || record.job || recordOrItem.job || '本场面试'
      const status = normalizeReportStatus(recordOrItem.reportStatus || record.reportStatus)
      if (status !== 'ready') {
        showResultPage.value = false
        interviewResult.value = null
        pendingGeneratingResult.value = {
          ...record,
          jobName: displayName
        }
        pendingGeneratingSessionId.value = sessionId
        reportGeneratingStatus.value = status
        reportGeneratingJobName.value = displayName
        resultEntrySource.value = 'history'
        showReportGeneratingPage.value = true
        return
      }

      try {
        const report = await getInterviewReport(sessionId)
        if (report?.reportStatus !== 'ready') {
          pendingGeneratingResult.value = {
            ...record,
            jobName: displayName
          }
          pendingGeneratingSessionId.value = sessionId
          reportGeneratingStatus.value = report?.reportStatus === 'failed' ? 'failed' : 'generating'
          reportGeneratingJobName.value = displayName
          resultEntrySource.value = 'history'
          showReportGeneratingPage.value = true
          return
        }

        const updatedRecords = upsertReadyInterviewRecord(readInterviewRecords(), {
          ...record,
          jobName: displayName,
          interviewMode: record.mode || recordOrItem.mode || 'practice'
        }, report)
        writeInterviewRecords(updatedRecords.slice(0, 50))
        interviewResult.value = mergeResultWithReport({
          ...record,
          jobName: displayName,
          interviewMode: record.mode || recordOrItem.mode || 'practice'
        }, report)
        resultEntrySource.value = 'history'
        showReportGeneratingPage.value = false
        showResultPage.value = true
      } catch (error) {
        showNotification(error?.message || '加载面试报告失败，请稍后重试', 'error')
      }
    }

    const handleCloseQuestionDetail = () => {
      showQuestionDetail.value = false
      questionDetailLoading.value = false
      questionDetailRequestSeq.value += 1
    }

    const handleNavigateQuestionDetail = (delta) => {
      if (!questionDetailContext.value) return

      const nextIndex = questionDetailContext.value.questionIndex + delta
      if (nextIndex < 0) return

      if (questionDetailContext.value.source === 'result') {
        if (!interviewResult.value?.answers?.[nextIndex]) return
        const context = {
          ...questionDetailContext.value,
          questionIndex: nextIndex
        }
        const detail = buildQuestionDetailViewModel({
          source: 'result',
          result: interviewResult.value,
          questionIndex: nextIndex
        })
        openQuestionDetail(detail, context)
        return
      }

      if (questionDetailContext.value.source === 'questionBank') {
        const bank = readQuestionBank()
        const nextItem = bank[nextIndex]
        if (!nextItem) return

        const context = {
          ...questionDetailContext.value,
          itemId: nextItem.id,
          questionIndex: nextIndex
        }
        const detail = buildQuestionDetailFromBankItem(nextItem, nextIndex, bank)
        openQuestionDetail(detail, context)
        return
      }

      const records = JSON.parse(localStorage.getItem('interviewRecords') || '[]')
      const record = records.find((item) => item.id === questionDetailContext.value.recordId)
      if (!record?.answers?.[nextIndex]) return

      const context = {
        ...questionDetailContext.value,
        questionIndex: nextIndex
      }
      const detail = buildQuestionDetailViewModel({
        source: 'record',
        record,
        questionIndex: nextIndex
      })
      openQuestionDetail(detail, context)
    }

    const handleCollectQuestion = async (detail) => {
      if (!detail) return
      if (!detail.sessionId || !detail.questionId) {
        showNotification('当前题目缺少后端上下文，暂不支持收藏', 'info')
        return
      }

      const storageKey = 'questionBank'
      const savedItems = JSON.parse(localStorage.getItem(storageKey) || '[]')
      try {
        const collected = await createQuestionBankItem({
          sessionId: detail.sessionId,
          questionId: detail.questionId,
          tag: detail.domainName || null
        })

        const localItem = {
          id: collected?.id || `${detail.sessionId}-${detail.questionId}`,
          recordId: detail.recordId || detail.sessionId,
          sessionId: collected?.sessionId || detail.sessionId,
          questionId: collected?.questionId || detail.questionId,
          localQuestionKey: detail.localQuestionKey || detail.questionId,
          questionStem: collected?.questionStem || detail.questionStem,
          question: collected?.questionStem || detail.questionStem,
          tag: collected?.tag || detail.domainName,
          createdAt: collected?.createdAt || detail.interviewDate,
          answer: collected?.answerSummary || '',
          userAnswer: collected?.answerSummary || '',
          score: collected?.score ?? null,
          keywords: detail.keywords || [],
          jobName: detail.jobName,
          job: detail.jobName,
          jobType: mapJobType(detail.jobName),
          modeLabel: detail.modeLabel,
          date: detail.interviewDate,
          timestamp: new Date(collected?.createdAt || detail.interviewDate || Date.now()).getTime() || Date.now(),
          rewrittenAnswer: detail.rewrittenAnswer,
          standardAnswer: detail.rewrittenAnswer,
          analysis: detail.commentary,
          domainName: collected?.domainName || detail.domainName,
          questionType: collected?.questionType || detail.questionType,
          isLocalFallback: false
        }

        const index = savedItems.findIndex((item) => String(item.id) === String(localItem.id))
        if (index >= 0) {
          savedItems[index] = localItem
        } else {
          savedItems.unshift(localItem)
        }
        localStorage.setItem(storageKey, JSON.stringify(savedItems))
        selectedQuestionDetail.value = {
          ...detail,
          isCollected: true
        }
        showNotification('已收藏到成长问答库', 'success')
      } catch (error) {
        showNotification(error?.message || '收藏失败，请稍后重试', 'error')
      }
    }

    const loadUserSettings = () => {
      const saved = localStorage.getItem('aiInterviewSettings')
      if (saved) {
        try {
          const settings = JSON.parse(saved)
          isDarkMode.value = settings.isDarkMode || false
          if (settings.user) {
            Object.assign(user, settings.user)
          }
          
          if (isDarkMode.value) {
            document.documentElement.setAttribute('data-theme', 'dark')
          }
        } catch (e) {
          console.warn('加载设置失败:', e)
        }
      }
    }

    const restoreStoredAuthSession = async () => {
      try {
        const restored = await restoreAuthSession({
          storage: localStorage,
          getCurrentAdmin,
          getCurrentUser
        })
        isLoggedIn.value = restored.isLoggedIn
        isAdmin.value = restored.isAdmin
        if (restored.userName) {
          user.name = restored.userName
        }
        user.email = restored.userEmail || ''
      } catch (error) {
        console.warn('[App] auth session restore failed', error)
        clearPersistedAuthSession(localStorage)
        isLoggedIn.value = false
        isAdmin.value = false
        user.name = '面试者'
        user.email = ''
      }
    }

    const loadHistoryUnreadFlag = () => {
      historyHasUnread.value = localStorage.getItem(HISTORY_UNREAD_DOT_KEY) === '1'
    }

    const handleKeyboardShortcuts = (event) => {
      if (!isLoggedIn.value) return
      
      if (event.ctrlKey || event.metaKey) {
        const pages = ['growth', 'interview', 'history', 'analysis', 'settings']
        const key = parseInt(event.key)
        if (key >= 1 && key <= 5) {
          event.preventDefault()
          navigateTo(pages[key - 1])
        }
      }
    }

    onMounted(() => {
      loadUserSettings()
      restoreStoredAuthSession().catch((error) => {
        console.warn('[App] restoreStoredAuthSession failed', error)
      })
      loadHistoryUnreadFlag()
      startReportPolling()
      pollGeneratingReports().catch((error) => {
        console.warn('[App] initial report polling failed', error)
      })
      document.addEventListener('keydown', handleKeyboardShortcuts)
    })

    onUnmounted(() => {
      stopReportPolling()
      document.removeEventListener('keydown', handleKeyboardShortcuts)
    })

    return {
      isLoggedIn,
      showRegister,
      showAdminLogin,
      isAdmin,
      currentPage,
      isDarkMode,
      isInterviewRunning,
      user,
      notification,
      notificationIcon,
      interviewPage,
      showResultPage,
      showReportGeneratingPage,
      reportGeneratingStatus,
      reportGeneratingJobName,
      historyHasUnread,
      interviewResult,
      showRadarPage,
      showScoreTrendPage,
      showQuestionDetail,
      questionDetailLoading,
      selectedQuestionDetail,
      handleLoginSuccess,
      handleRegisterSuccess,
      handleLogout,
      handleInterviewEnd,
      goToRegister,
      goToLogin,
      goToAdminLogin,
      goToUserLogin,
      handleAdminLoginSuccess,
      navigateTo,
      openQuestionBank,
      selectJob,
      handleGoToInterview,
      handleShowQuestionDetailFromBank,
      handleRedoQuestionFromBank,
      toggleDarkMode,
      saveSettings,
      handleResultGoBack,
      handleResultRestart,
      handleGeneratingGoHistory,
      handleGeneratingRefreshStatus,
      handleGeneratingRestart,
      handleShowInterviewDetail,
      handleShowQuestionDetailFromResult,
      handleCloseQuestionDetail,
      handleNavigateQuestionDetail,
      handleCollectQuestion
    }
  }
}
</script>

<style>
.notification {
  position: fixed;
  top: 20px;
  right: 20px;
  padding: 14px 20px;
  background: var(--glass-bg);
  backdrop-filter: blur(20px);
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  gap: 10px;
  z-index: 2000;
  animation: slideInRight 0.3s ease;
  box-shadow: var(--shadow-medium);
}

@keyframes slideInRight {
  from {
    opacity: 0;
    transform: translateX(100px);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
}

.notification-success i { color: var(--success-color); }
.notification-error i { color: var(--danger-color); }
.notification-info i { color: var(--info-color); }

.main-container.interview-fullscreen .content-area {
  margin-left: 0;
}
</style>
