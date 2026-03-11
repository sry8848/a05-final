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
        @back="handleCloseQuestionDetail"
        @collect="handleCollectQuestion"
        @navigateQuestion="handleNavigateQuestionDetail"
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
      <InterviewDetailPage 
        v-else-if="showInterviewDetail"
        :recordId="selectedRecordId"
        @goBack="showInterviewDetail = false"
        @retry="handleRetryInterview"
        @showQuestionDetail="handleShowQuestionDetailFromRecord"
      />
      <div v-else class="main-container" :class="{ 'interview-fullscreen': isInterviewRunning }">
        <Sidebar 
          v-show="!isInterviewRunning"
          :currentPage="currentPage" 
          :user="user"
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
          <HistoryPage v-else-if="currentPage === 'history'" @goToQuestionBank="openQuestionBank" @showInterviewDetail="handleShowInterviewDetail" />
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
import InterviewDetailPage from './components/InterviewDetailPage.vue'
import QuestionDetailPage from './components/QuestionDetailPage.vue'
import AdminLoginPage from './components/AdminLoginPage.vue'
import AdminLayout from './components/AdminLayout.vue'
import ResumesPage from './components/ResumesPage.vue'

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
    InterviewDetailPage,
    QuestionDetailPage,
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
    const interviewResult = ref(null)
    const showRadarPage = ref(false)
    const showScoreTrendPage = ref(false)
    const showInterviewDetail = ref(false)
    const showQuestionDetail = ref(false)
    const selectedRecordId = ref(null)
    const selectedQuestionDetail = ref(null)
    const questionDetailContext = ref(null)
    
    const user = reactive({
      name: '面试者',
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

    const inferTargetDepth = (score = 0, questionIndex = 0) => {
      if (score >= 85) return 'L4'
      if (score >= 70) return 'L3'
      if (questionIndex >= 3) return 'L3'
      return 'L2'
    }

    const buildHighlightedSegments = (answerText = '', keywords = [], score = 0) => {
      if (!answerText || answerText === '[跳过]') return []

      const normalized = answerText.trim()
      const lowerText = normalized.toLowerCase()
      const matches = keywords
        .map((keyword) => {
          const start = lowerText.indexOf(String(keyword).toLowerCase())
          return start >= 0 ? { keyword, start, end: start + keyword.length } : null
        })
        .filter(Boolean)
        .sort((a, b) => a.start - b.start)
        .filter((match, index, array) => {
          if (index === 0) return true
          return match.start >= array[index - 1].end
        })

      if (matches.length === 0) {
        return [
          {
            text: normalized,
            type: score >= 60 ? 'normal' : 'weakness',
            note: score >= 60 ? '' : '可以补充更多关键概念和业务细节。'
          }
        ]
      }

      const segments = []
      let cursor = 0

      matches.forEach((match) => {
        if (match.start > cursor) {
          segments.push({
            text: normalized.slice(cursor, match.start),
            type: 'normal'
          })
        }

        segments.push({
          text: normalized.slice(match.start, match.end),
          type: 'strength',
          note: `命中了关键词「${match.keyword}」`
        })

        cursor = match.end
      })

      if (cursor < normalized.length) {
        segments.push({
          text: normalized.slice(cursor),
          type: score < 60 ? 'weakness' : 'normal',
          note: score < 60 ? '这一段还可以补充推导过程或案例。' : ''
        })
      }

      return segments
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

    const buildCommentary = (answer, domainName) => {
      if (answer.score >= 85) return `你对${domainName}的核心知识掌握较好，回答结构完整，已经具备较强复盘价值。`
      if (answer.score >= 70) return `你已经覆盖了${domainName}的主要内容，但还可以继续补充原理深度和项目细节。`
      if (answer.score >= 60) return `回答方向基本正确，但在${domainName}上的表达还不够充分，建议加强结构化输出。`
      return `当前回答没有充分体现${domainName}的关键考点，建议优先补强核心概念、流程和应用场景。`
    }

    const buildStrengthPoints = (answer, keywords = []) => {
      const points = []
      if (answer.score >= 80) points.push('回答整体结构清晰，具备较好的复盘基础。')
      if (answer.answer && answer.answer !== '[跳过]' && answer.answer.length >= 40) {
        points.push('回答信息量较足，没有停留在一句话式作答。')
      }
      if (keywords.length > 0) {
        points.push(`命中了关键词：${keywords.slice(0, 2).join('、')}。`)
      }
      return points.slice(0, 3)
    }

    const buildWeakPoints = (answer, keywords = []) => {
      const points = []
      if (!answer.answer || answer.answer === '[跳过]') {
        points.push('本题未作答，建议优先补齐基础答题框架。')
      }
      if (answer.score < 80) {
        const missingKeywords = keywords.filter((keyword) => !String(answer.answer || '').toLowerCase().includes(String(keyword).toLowerCase()))
        if (missingKeywords.length > 0) {
          points.push(`可继续补充：${missingKeywords.slice(0, 2).join('、')}。`)
        }
      }
      if (answer.score < 60) {
        points.push('建议加强答题结构，先定义概念，再讲原理，最后结合场景。')
      }
      return points.slice(0, 3)
    }

    const isQuestionCollected = (recordId, questionId) => {
      try {
        const bank = JSON.parse(localStorage.getItem('questionBank') || '[]')
        return bank.some((item) => item.recordId === recordId && item.questionId === questionId)
      } catch (error) {
        return false
      }
    }

    const buildQuestionDetailViewModel = ({ source, record, result, questionIndex }) => {
      const questionList = record?.answers || result?.answers || []
      const answer = questionList[questionIndex]
      if (!answer) return null

      const recordId = record?.id || `result-${questionIndex}`
      const jobName = record?.job || result?.jobName || '模拟面试'
      const modeLabel = mapModeLabel(record?.mode || result?.interviewMode)
      const domainName = inferDomainName(answer.question, answer.keywords || [], jobName)
      const weakPoints = buildWeakPoints(answer, answer.keywords || [])

      return {
        source,
        recordId,
        sessionId: record?.id || result?.sessionId || recordId,
        questionId: answer.questionId || `${recordId}-${questionIndex}`,
        questionIndex,
        questionNumber: questionIndex + 1,
        totalQuestions: questionList.length,
        questionStem: answer.question,
        domainName,
        questionType: inferQuestionType(answer.question, questionIndex),
        targetDepth: inferTargetDepth(answer.score, questionIndex),
        answerStatus: !answer.answer || answer.answer === '[跳过]' ? 'skipped' : 'answered',
        userAnswer: answer.answer,
        highlightedSegments: buildHighlightedSegments(answer.answer, answer.keywords || [], answer.score),
        score: answer.score || 0,
        commentary: buildCommentary(answer, domainName),
        strengthPoints: buildStrengthPoints(answer, answer.keywords || []),
        weakPoints,
        evaluatedDomains: [
          {
            domainName,
            score: answer.score || 0,
            note: weakPoints[0] || '本题主要考察基础理解和表达完整度。'
          }
        ],
        idealAnswerOutline: buildAnswerOutline(answer.question, answer.keywords || []),
        rewrittenAnswer: buildRewrittenAnswer(answer.question, answer.keywords || [], domainName),
        isCollected: isQuestionCollected(recordId, answer.questionId || `${recordId}-${questionIndex}`),
        keywords: answer.keywords || [],
        jobName,
        modeLabel,
        interviewDate: record?.date || new Date().toLocaleString('zh-CN'),
        hasPrev: questionIndex > 0,
        hasNext: questionIndex < questionList.length - 1
      }
    }

    const handleLoginSuccess = (userData) => {
      if (userData && userData.nickname) {
        user.name = userData.nickname
      }
      isLoggedIn.value = true
      showRegister.value = false
      showNotification('登录成功，欢迎回来！', 'success')
    }

    const handleRegisterSuccess = (userData) => {
      user.name = userData.username
      isLoggedIn.value = true
      showRegister.value = false
      showNotification('注册成功，欢迎加入！', 'success')
    }

    const handleLogout = () => {
      isLoggedIn.value = false
      currentPage.value = 'growth'
      isInterviewRunning.value = false
      showNotification('已退出登录', 'info')
    }

    const handleInterviewEnd = (resultData) => {
      isInterviewRunning.value = false
      if (resultData) {
        interviewResult.value = resultData
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
      isLoggedIn.value = true
      isAdmin.value = true
      showAdminLogin.value = false
      user.name = userData.username || '管理员'
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

      const detail = {
        source: 'questionBank',
        recordId: item.recordId || item.sessionId || 'question-bank',
        sessionId: item.sessionId || item.recordId || 'question-bank',
        questionId: item.questionId || item.id,
        questionIndex: index,
        questionNumber: index + 1,
        totalQuestions: list.length || 1,
        questionStem: item.questionStem || item.question || '未命名题目',
        domainName: item.domainName || inferDomainName(item.questionStem || item.question, item.keywords || [], item.jobName || item.job || ''),
        questionType: item.questionType || inferQuestionType(item.questionStem || item.question, index),
        targetDepth: item.targetDepth || 'L2',
        answerStatus: !(item.userAnswer || item.answer) ? 'skipped' : 'answered',
        userAnswer: item.userAnswer || item.answer || '',
        highlightedSegments: buildHighlightedSegments(item.userAnswer || item.answer || '', item.keywords || [], item.score || 0),
        score: item.score || 0,
        commentary: item.analysis || buildCommentary({ score: item.score || 0, answer: item.userAnswer || item.answer || '' }, item.domainName || '通用技术能力'),
        strengthPoints: buildStrengthPoints({ score: item.score || 0, answer: item.userAnswer || item.answer || '' }, item.keywords || []),
        weakPoints: buildWeakPoints({ score: item.score || 0, answer: item.userAnswer || item.answer || '' }, item.keywords || []),
        evaluatedDomains: [
          {
            domainName: item.domainName || inferDomainName(item.questionStem || item.question, item.keywords || [], item.jobName || item.job || ''),
            score: item.score || 0,
            note: item.analysis || '建议围绕核心概念、原理和实际场景继续补强。'
          }
        ],
        idealAnswerOutline: item.idealAnswerOutline || buildAnswerOutline(item.questionStem || item.question || '', item.keywords || []),
        rewrittenAnswer: item.rewrittenAnswer || item.standardAnswer || buildRewrittenAnswer(item.questionStem || item.question || '', item.keywords || [], item.domainName || '通用技术能力'),
        isCollected: true,
        keywords: item.keywords || [],
        jobName: item.jobName || item.job || '模拟面试',
        modeLabel: item.modeLabel || '练习模式',
        interviewDate: item.createdAt || item.date || new Date().toLocaleString('zh-CN'),
        hasPrev: index > 0,
        hasNext: index < list.length - 1,
        backLabel: '返回问答库'
      }

      return detail
    }

    const handleShowQuestionDetailFromBank = (bankItem) => {
      const bank = readQuestionBank()
      const index = bank.findIndex((item) => item.id === bankItem.id)
      selectedQuestionDetail.value = buildQuestionDetailFromBankItem(bankItem, index >= 0 ? index : 0, bank)
      questionDetailContext.value = {
        source: 'questionBank',
        itemId: bankItem.id,
        questionIndex: index >= 0 ? index : 0
      }
      showQuestionDetail.value = true
    }

    const handleRedoQuestionFromBank = (bankItem) => {
      currentPage.value = 'interview'
      setTimeout(() => {
        if (!interviewPage.value?.startSingleQuestionInterview) return
        interviewPage.value.startSingleQuestionInterview({
          id: bankItem.questionId || bankItem.id,
          question: bankItem.questionStem || bankItem.question,
          keywords: bankItem.keywords || [],
          jobType: bankItem.jobType || mapJobType(bankItem.jobName || bankItem.job || ''),
          jobName: bankItem.jobName || bankItem.job || '前端开发工程师',
          domainName: bankItem.domainName || inferDomainName(bankItem.questionStem || bankItem.question, bankItem.keywords || [], bankItem.jobName || bankItem.job || ''),
          sourceItemId: bankItem.id
        })
      }, 100)
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

    const saveSettings = () => {
      saveUserSettings()
    }

    const handleResultGoBack = () => {
      showResultPage.value = false
      interviewResult.value = null
      currentPage.value = 'growth'
    }

    const handleResultRestart = () => {
      showResultPage.value = false
      interviewResult.value = null
      currentPage.value = 'interview'
    }

    const handleShowQuestionDetailFromResult = ({ index = 0 } = {}) => {
      selectedQuestionDetail.value = buildQuestionDetailViewModel({
        source: 'result',
        result: interviewResult.value,
        questionIndex: index
      })
      questionDetailContext.value = {
        source: 'result',
        questionIndex: index
      }
      showQuestionDetail.value = true
    }

    const handleShowInterviewDetail = (recordId) => {
      selectedRecordId.value = recordId
      showInterviewDetail.value = true
    }

    const handleShowQuestionDetailFromRecord = ({ recordId, questionIndex = 0 }) => {
      const records = JSON.parse(localStorage.getItem('interviewRecords') || '[]')
      const record = records.find((item) => item.id === recordId)

      selectedQuestionDetail.value = buildQuestionDetailViewModel({
        source: 'record',
        record,
        questionIndex
      })
      questionDetailContext.value = {
        source: 'record',
        recordId,
        questionIndex
      }
      showQuestionDetail.value = true
    }

    const handleCloseQuestionDetail = () => {
      showQuestionDetail.value = false
    }

    const handleNavigateQuestionDetail = (delta) => {
      if (!questionDetailContext.value) return

      const nextIndex = questionDetailContext.value.questionIndex + delta
      if (nextIndex < 0) return

      if (questionDetailContext.value.source === 'result') {
        if (!interviewResult.value?.answers?.[nextIndex]) return
        questionDetailContext.value = {
          ...questionDetailContext.value,
          questionIndex: nextIndex
        }
        selectedQuestionDetail.value = buildQuestionDetailViewModel({
          source: 'result',
          result: interviewResult.value,
          questionIndex: nextIndex
        })
        return
      }

      if (questionDetailContext.value.source === 'questionBank') {
        const bank = readQuestionBank()
        const nextItem = bank[nextIndex]
        if (!nextItem) return

        questionDetailContext.value = {
          ...questionDetailContext.value,
          itemId: nextItem.id,
          questionIndex: nextIndex
        }
        selectedQuestionDetail.value = buildQuestionDetailFromBankItem(nextItem, nextIndex, bank)
        return
      }

      const records = JSON.parse(localStorage.getItem('interviewRecords') || '[]')
      const record = records.find((item) => item.id === questionDetailContext.value.recordId)
      if (!record?.answers?.[nextIndex]) return

      questionDetailContext.value = {
        ...questionDetailContext.value,
        questionIndex: nextIndex
      }
      selectedQuestionDetail.value = buildQuestionDetailViewModel({
        source: 'record',
        record,
        questionIndex: nextIndex
      })
    }

    const handleCollectQuestion = (detail) => {
      if (!detail) return

      const storageKey = 'questionBank'
      const savedItems = JSON.parse(localStorage.getItem(storageKey) || '[]')
      const exists = savedItems.some((item) => item.recordId === detail.recordId && item.questionId === detail.questionId)

      if (exists) {
        selectedQuestionDetail.value = {
          ...detail,
          isCollected: true
        }
        showNotification('该题已经在成长问答库中了', 'info')
        return
      }

      savedItems.unshift({
        id: `${detail.recordId}-${detail.questionId}`,
        recordId: detail.recordId,
        sessionId: detail.sessionId,
        questionId: detail.questionId,
        questionStem: detail.questionStem,
        question: detail.questionStem,
        tag: detail.domainName,
        createdAt: detail.interviewDate,
        answer: detail.userAnswer || '',
        userAnswer: detail.userAnswer || '',
        score: detail.score,
        keywords: detail.keywords || [],
        jobName: detail.jobName,
        job: detail.jobName,
        jobType: mapJobType(detail.jobName),
        modeLabel: detail.modeLabel,
        date: detail.interviewDate,
        timestamp: new Date(detail.interviewDate).getTime() || Date.now(),
        rewrittenAnswer: detail.rewrittenAnswer,
        standardAnswer: detail.rewrittenAnswer,
        analysis: detail.commentary,
        domainName: detail.domainName,
        questionType: detail.questionType,
        targetDepth: detail.targetDepth
      })

      localStorage.setItem(storageKey, JSON.stringify(savedItems))
      selectedQuestionDetail.value = {
        ...detail,
        isCollected: true
      }
      showNotification('已收藏到成长问答库', 'success')
    }

    const handleRetryInterview = (record) => {
      showInterviewDetail.value = false
      currentPage.value = 'interview'
    }

    const loadUserSettings = () => {
      const saved = localStorage.getItem('aiInterviewSettings')
      if (saved) {
        try {
          const settings = JSON.parse(saved)
          isDarkMode.value = settings.isDarkMode || false
          isLoggedIn.value = settings.isLoggedIn || false
          Object.assign(user, settings.user)
          
          if (isDarkMode.value) {
            document.documentElement.setAttribute('data-theme', 'dark')
          }
        } catch (e) {
          console.warn('加载设置失败:', e)
        }
      }
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
      document.addEventListener('keydown', handleKeyboardShortcuts)
    })

    onUnmounted(() => {
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
      interviewResult,
      showRadarPage,
      showScoreTrendPage,
      showInterviewDetail,
      showQuestionDetail,
      selectedRecordId,
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
      handleShowInterviewDetail,
      handleShowQuestionDetailFromRecord,
      handleShowQuestionDetailFromResult,
      handleCloseQuestionDetail,
      handleNavigateQuestionDetail,
      handleCollectQuestion,
      handleRetryInterview
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
