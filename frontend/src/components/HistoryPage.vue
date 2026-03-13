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
        :class="{ disabled: !isRecordClickable(item) }"
        @click="viewHistoryDetail(item)"
      >
        <div class="history-info">
          <span class="history-title">{{ item.job }}</span>
          <div class="history-meta">
            <span><i class="fas fa-calendar"></i> {{ item.date }}</span>
            <span><i class="fas fa-clock"></i> 用时 {{ item.duration }}</span>
            <span v-if="normalizeReportStatus(item.reportStatus) === 'ready'">
              <i class="fas fa-check-circle"></i>
              {{ item.correct }}/{{ item.questions }} 正确
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
          <span
            class="score-badge"
            :class="`status-${normalizeReportStatus(item.reportStatus)}`"
          >
            {{ getScoreBadgeText(item) }}
          </span>
          <i
            v-if="isRecordClickable(item)"
            class="fas fa-chevron-right"
            style="color: var(--text-light);"
          ></i>
          <i
            v-else-if="normalizeReportStatus(item.reportStatus) === 'generating'"
            class="fas fa-clock"
            style="color: var(--text-light);"
          ></i>
          <i
            v-else
            class="fas fa-ban"
            style="color: var(--text-light);"
          ></i>
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

export default {
  name: 'HistoryPage',
  emits: ['goToQuestionBank', 'showInterviewDetail'],
  setup(props, { emit }) {
    const historyList = ref([])
    const INTERVIEW_RECORDS_UPDATED_EVENT = 'interview-records-updated'

    const normalizeReportStatus = (status) => {
      const normalized = String(status || '').trim().toLowerCase()
      if (normalized === 'generating' || normalized === 'failed' || normalized === 'ready') {
        return normalized
      }
      return 'ready'
    }

    const normalizeRecord = (record) => ({
      ...record,
      reportStatus: normalizeReportStatus(record?.reportStatus)
    })

    const loadHistory = () => {
      try {
        const records = JSON.parse(localStorage.getItem('interviewRecords') || '[]')
        if (Array.isArray(records) && records.length > 0) {
          historyList.value = records.map(normalizeRecord)
          return
        }
      } catch (error) {
        console.warn('[HistoryPage] 加载历史记录失败，使用默认数据', error)
      }
      historyList.value = generateMockHistory()
    }

    const generateMockHistory = () => {
      return [
        {
          id: 1,
          job: '前端开发工程师',
          date: '2024-01-15 14:30',
          score: 85,
          duration: '15:30',
          questions: 10,
          correct: 8,
          reportStatus: 'ready'
        },
        {
          id: 2,
          job: '前端开发工程师',
          date: '2024-01-14 10:00',
          score: 78,
          duration: '12:45',
          questions: 10,
          correct: 7,
          reportStatus: 'ready'
        },
        {
          id: 3,
          job: '全栈开发工程师',
          date: '2024-01-13 16:20',
          score: 92,
          duration: '18:00',
          questions: 15,
          correct: 14,
          reportStatus: 'ready'
        },
        {
          id: 4,
          job: '后端开发工程师',
          date: '2024-01-12 09:15',
          score: 70,
          duration: '20:30',
          questions: 12,
          correct: 8,
          reportStatus: 'ready'
        },
        {
          id: 5,
          job: '前端开发工程师',
          date: '2024-01-10 11:00',
          score: 88,
          duration: '14:20',
          questions: 10,
          correct: 9,
          reportStatus: 'ready'
        }
      ]
    }

    const isRecordClickable = (item) => {
      return normalizeReportStatus(item?.reportStatus) === 'ready'
    }

    const getScoreBadgeText = (item) => {
      const status = normalizeReportStatus(item?.reportStatus)
      if (status === 'generating') return '生成中'
      if (status === 'failed') return '失败'
      const score = Number(item?.score)
      return Number.isFinite(score) ? `${Math.round(score)}分` : '--'
    }

    const viewHistoryDetail = (item) => {
      if (!isRecordClickable(item)) return
      emit('showInterviewDetail', item.id)
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
      normalizeReportStatus,
      isRecordClickable,
      getScoreBadgeText,
      viewHistoryDetail,
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

.history-item.disabled {
  opacity: 0.8;
  cursor: not-allowed;
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
