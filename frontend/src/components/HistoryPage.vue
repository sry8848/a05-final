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
        @click="viewHistoryDetail(item.id)"
      >
        <div class="history-info">
          <span class="history-title">{{ item.job }}</span>
          <div class="history-meta">
            <span><i class="fas fa-calendar"></i> {{ item.date }}</span>
            <span><i class="fas fa-clock"></i> 用时 {{ item.duration }}</span>
            <span><i class="fas fa-check-circle"></i> {{ item.correct }}/{{ item.questions }} 正确</span>
          </div>
        </div>
        <div class="history-score">
          <span class="score-badge">{{ item.score }}分</span>
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
import { ref, onMounted } from 'vue'

export default {
  name: 'HistoryPage',
  emits: ['goToQuestionBank', 'showInterviewDetail'],
  setup(props, { emit }) {
    const historyList = ref([])

    const loadHistory = () => {
      const records = JSON.parse(localStorage.getItem('interviewRecords') || '[]')
      if (records.length > 0) {
        historyList.value = records
      } else {
        historyList.value = generateMockHistory()
      }
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
          correct: 8
        },
        {
          id: 2,
          job: '前端开发工程师',
          date: '2024-01-14 10:00',
          score: 78,
          duration: '12:45',
          questions: 10,
          correct: 7
        },
        {
          id: 3,
          job: '全栈开发工程师',
          date: '2024-01-13 16:20',
          score: 92,
          duration: '18:00',
          questions: 15,
          correct: 14
        },
        {
          id: 4,
          job: '后端开发工程师',
          date: '2024-01-12 09:15',
          score: 70,
          duration: '20:30',
          questions: 12,
          correct: 8
        },
        {
          id: 5,
          job: '前端开发工程师',
          date: '2024-01-10 11:00',
          score: 88,
          duration: '14:20',
          questions: 10,
          correct: 9
        }
      ]
    }

    const viewHistoryDetail = (id) => {
      emit('showInterviewDetail', id)
    }

    const goToQuestionBank = () => {
      emit('goToQuestionBank')
    }

    onMounted(() => {
      loadHistory()
    })

    return {
      historyList,
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
