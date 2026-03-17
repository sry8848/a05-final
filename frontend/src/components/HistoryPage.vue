<template>
  <section id="page-history" class="page-section active">
    <div class="page-header">
      <h3 class="section-title">
        <i class="fas fa-history"></i>
        面试记录
      </h3>
      <button class="btn btn-primary glass-btn question-bank-btn" @click="goToQuestionBank">
        <i class="fas fa-book"></i>
        成长问答库
      </button>
    </div>
    
    <div class="history-list">
      <div
        v-for="item in historyList"
        :key="item.id"
        class="history-item"
        @click="viewHistoryDetail(item)"
      >
        <div class="history-info">
          <span class="history-title">{{ item.job }}</span>
          <div class="history-meta">
            <span><i class="fas fa-calendar"></i> {{ item.date }}</span>
            <span><i class="fas fa-clock"></i> 用时 {{ item.duration }}</span>
            <span v-if="normalizeReportStatus(item.reportStatus) === 'ready'">
              <i class="fas fa-check-circle"></i>
              {{ Number.isFinite(Number(item.correct)) ? item.correct : '--' }}/{{ item.questions || '--' }} 正确
            </span>
            <span v-else-if="normalizeReportStatus(item.reportStatus) === 'generating'" class="status-meta generating">
              <i class="fas fa-spinner fa-spin"></i>
              报告生成中
            </span>
            <span v-else class="status-meta failed">
              <i class="fas fa-circle-exclamation"></i>
              生成失败
            </span>
          </div>
        </div>
        <div class="history-score">
          <button
            class="delete-btn"
            title="删除面试记录"
            :disabled="deletingSessionId === item.sessionId"
            @click.stop="deleteHistoryItem(item)"
          >
            <i :class="deletingSessionId === item.sessionId ? 'fas fa-spinner fa-spin' : 'fas fa-trash-alt'"></i>
          </button>
          <span
            class="score-badge"
            :class="`status-${normalizeReportStatus(item.reportStatus)}`"
          >
            {{ getScoreBadgeText(item) }}
          </span>
          <i class="fas fa-chevron-right" style="color: var(--text-light);"></i>
        </div>
      </div>
      
      <div v-if="historyList.length === 0" class="empty-state">
        <i class="fas fa-folder-open"></i>
        <p>暂无面试记录</p>
      </div>
    </div>
  </section>
</template>

<script>
import { ref, onMounted, onUnmounted } from 'vue'
import { deleteInterviewSession, getInterviewHistory } from '../api/resume'
import {
  mapBackendHistoryStatus,
  normalizeDisplayReportStatus,
  normalizeStoredInterviewRecord,
  shouldUseBackendHistory
} from '../utils/growthHistoryState'

export default {
  name: 'HistoryPage',
  emits: ['goToQuestionBank', 'showInterviewDetail'],
  setup(props, { emit }) {
    const historyList = ref([])
    const deletingSessionId = ref(null)
    const INTERVIEW_RECORDS_UPDATED_EVENT = 'interview-records-updated'
    const STATUS_READY = 'ready'
    const STATUS_GENERATING = 'generating'
    const STATUS_FAILED = 'failed'

    const normalizeReportStatus = (status) => normalizeDisplayReportStatus(status)

    const normalizeRecord = (record, options = {}) => normalizeStoredInterviewRecord(record, options)

    const mapRoleLabel = (targetRole) => {
      const map = {
        FRONTEND: '前端开发工程师',
        JAVA_BACKEND: '后端开发工程师',
        GO_BACKEND: '后端开发工程师',
        DATA_ENGINEER: '数据工程师',
        QA: '测试工程师',
        DEVOPS: '运维工程师'
      }
      return map[targetRole] || targetRole || '模拟面试'
    }

    const toDateText = (value) => {
      const date = new Date(value)
      if (Number.isNaN(date.getTime())) return value || ''
      return date.toLocaleString('zh-CN', { hour12: false })
    }

    const mapBackendItem = (item) => {
      const score = Number(item?.overallScore)
      const sessionId = item?.sessionId
      return normalizeRecord({
        id: sessionId,
        sessionId,
        job: mapRoleLabel(item?.targetRole),
        targetRole: item?.targetRole,
        date: toDateText(item?.createdAt),
        duration: '--',
        score: Number.isFinite(score) ? Math.round(score) : null,
        questions: Number(item?.questionCount) || 0,
        correct: null,
        reportStatus: mapBackendHistoryStatus(item?.status)
      }, {
        allowReadyWithoutReportDowngrade: false
      })
    }

    const loadLocalHistory = () => {
      try {
        const records = JSON.parse(localStorage.getItem('interviewRecords') || '[]')
        if (Array.isArray(records) && records.length > 0) {
          const normalized = records.map(normalizeRecord)
          if (JSON.stringify(normalized) !== JSON.stringify(records)) {
            localStorage.setItem('interviewRecords', JSON.stringify(normalized))
          }
          return normalized
        }
      } catch (error) {
        console.warn('[HistoryPage] 加载本地历史记录失败', error)
      }
      return []
    }

    const loadHistory = async () => {
      try {
        const page = await getInterviewHistory({
          page: 1,
          pageSize: 50,
          sortBy: 'createdAt',
          sortOrder: 'desc'
        })
        if (shouldUseBackendHistory(page)) {
          historyList.value = page.items.map(mapBackendItem)
          return
        }
      } catch (error) {
        console.warn('[HistoryPage] 加载后端历史记录失败，切换本地兜底', error)
      }
      historyList.value = loadLocalHistory()
    }

    const getScoreBadgeText = (item) => {
      const status = normalizeReportStatus(item?.reportStatus)
      if (status === STATUS_GENERATING) return '生成中'
      if (status === STATUS_FAILED) return '失败'
      const score = Number(item?.score)
      return Number.isFinite(score) ? `${Math.round(score)}分` : '--'
    }

    const viewHistoryDetail = (item) => {
      emit('showInterviewDetail', item)
    }

    const removeLocalRecord = (sessionId) => {
      try {
        const records = JSON.parse(localStorage.getItem('interviewRecords') || '[]')
        const filtered = Array.isArray(records)
          ? records.filter((item) => String(item?.sessionId || item?.id || '') !== String(sessionId))
          : []
        localStorage.setItem('interviewRecords', JSON.stringify(filtered))
        window.dispatchEvent(new CustomEvent(INTERVIEW_RECORDS_UPDATED_EVENT))
      } catch (error) {
        console.warn('[HistoryPage] 删除本地历史记录失败', error)
      }
    }

    const deleteHistoryItem = async (item) => {
      const sessionId = item?.sessionId
      if (!sessionId) return
      if (!confirm(`确认删除「${item.job || '本场面试'}」的记录吗？删除后不可恢复。`)) return
      deletingSessionId.value = sessionId
      try {
        await deleteInterviewSession(sessionId)
        removeLocalRecord(sessionId)
        await loadHistory()
      } catch (error) {
        alert(error?.message || '删除失败，请稍后重试')
      } finally {
        deletingSessionId.value = null
      }
    }

    const goToQuestionBank = () => {
      emit('goToQuestionBank')
    }

    const handleRecordsUpdated = () => {
      loadHistory()
    }

    onMounted(() => {
      loadHistory()
      window.addEventListener(INTERVIEW_RECORDS_UPDATED_EVENT, handleRecordsUpdated)
    })

    onUnmounted(() => {
      window.removeEventListener(INTERVIEW_RECORDS_UPDATED_EVENT, handleRecordsUpdated)
    })

    return {
      historyList,
      deletingSessionId,
      normalizeReportStatus,
      getScoreBadgeText,
      viewHistoryDetail,
      deleteHistoryItem,
      goToQuestionBank
    }
  }
}
</script>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.question-bank-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 20px;
  font-size: 14px;
}

.status-meta.generating {
  color: #f59e0b;
}

.status-meta.failed {
  color: var(--danger-color);
}

.score-badge.status-generating {
  background: rgba(245, 158, 11, 0.16);
  color: #d97706;
}

.score-badge.status-failed {
  background: rgba(239, 68, 68, 0.14);
  color: var(--danger-color);
}

.score-badge.status-ready {
  background: rgba(16, 185, 129, 0.14);
  color: #059669;
}

.history-score {
  display: flex;
  align-items: center;
  gap: 12px;
}

.delete-btn {
  width: 34px;
  height: 34px;
  border: none;
  border-radius: 50%;
  color: var(--danger-color);
  background: rgba(239, 68, 68, 0.12);
  cursor: pointer;
  transition: transform 0.2s ease, background 0.2s ease;
}

.delete-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  background: rgba(239, 68, 68, 0.18);
}

.delete-btn:disabled {
  cursor: not-allowed;
  opacity: 0.7;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  color: var(--text-secondary);
}

.empty-state i {
  font-size: 48px;
  margin-bottom: 16px;
  opacity: 0.5;
}

.empty-state p {
  font-size: 16px;
}
</style>
