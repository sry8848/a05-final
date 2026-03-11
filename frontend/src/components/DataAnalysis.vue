<template>
  <div class="data-analysis-page">
    <div class="page-header glass-card">
      <div class="header-info">
        <h3>
          <i class="fas fa-database"></i>
          数据分析
        </h3>
        <p>优质问答筛选、数据标注与模型微调管理</p>
      </div>
      <div class="header-actions">
        <span class="data-stats">
          <i class="fas fa-layer-group"></i>
          共 {{ totalQAPairs }} 条问答对
        </span>
      </div>
    </div>

    <div class="main-grid">
      <div class="qa-filter-section glass-card">
        <div class="section-header">
          <h4>
            <i class="fas fa-filter"></i>
            优质问答对筛选
          </h4>
          <button class="apply-filter-btn" @click="applyFilter">
            <i class="fas fa-check"></i>
            应用筛选
          </button>
        </div>

        <div class="filter-controls">
          <div class="filter-group">
            <label class="filter-label">
              <i class="fas fa-star"></i>
              评分范围
            </label>
            <div class="range-slider">
              <div class="range-values">
                <span>{{ filterSettings.scoreMin }}</span>
                <span>{{ filterSettings.scoreMax }}</span>
              </div>
              <div class="dual-slider">
                <input type="range" v-model="filterSettings.scoreMin" min="0" max="100" step="5" />
                <input type="range" v-model="filterSettings.scoreMax" min="0" max="100" step="5" />
              </div>
              <div class="slider-track">
                <div class="slider-fill" :style="{ left: filterSettings.scoreMin + '%', width: (filterSettings.scoreMax - filterSettings.scoreMin) + '%' }"></div>
              </div>
            </div>
          </div>

          <div class="filter-group">
            <label class="filter-label">
              <i class="fas fa-link"></i>
              连贯性评分
            </label>
            <div class="coherence-options">
              <button 
                v-for="level in coherenceLevels" 
                :key="level.value"
                :class="['coherence-btn', { active: filterSettings.coherence.includes(level.value) }]"
                @click="toggleCoherence(level.value)"
              >
                <i :class="level.icon"></i>
                {{ level.label }}
              </button>
            </div>
          </div>

          <div class="filter-group">
            <label class="filter-label">
              <i class="fas fa-tags"></i>
              问题类型
            </label>
            <div class="type-tags">
              <span 
                v-for="type in questionTypes" 
                :key="type.value"
                :class="['type-tag', { active: filterSettings.types.includes(type.value) }]"
                @click="toggleType(type.value)"
              >
                {{ type.label }}
              </span>
            </div>
          </div>

          <div class="filter-group">
            <label class="filter-label">
              <i class="fas fa-clock"></i>
              时间范围
            </label>
            <div class="time-range-buttons">
              <button 
                v-for="range in timeRanges" 
                :key="range.value"
                :class="['time-btn', { active: filterSettings.timeRange === range.value }]"
                @click="filterSettings.timeRange = range.value"
              >
                {{ range.label }}
              </button>
            </div>
          </div>
        </div>

        <div class="filter-summary">
          <div class="summary-item">
            <span class="summary-label">已筛选</span>
            <span class="summary-value">{{ filteredCount }} 条</span>
          </div>
          <div class="summary-item">
            <span class="summary-label">优质率</span>
            <span class="summary-value">{{ qualityRate }}%</span>
          </div>
        </div>
      </div>

      <div class="annotation-section glass-card">
        <div class="section-header">
          <h4>
            <i class="fas fa-tags"></i>
            数据标注界面
          </h4>
          <div class="annotation-actions">
            <button class="select-all-btn" @click="selectAll">
              <i class="fas fa-check-double"></i>
              全选
            </button>
            <button class="export-btn" @click="exportJSONL" :disabled="selectedItems.length === 0">
              <i class="fas fa-download"></i>
              导出JSONL ({{ selectedItems.length }})
            </button>
          </div>
        </div>

        <div class="annotation-toolbar">
          <div class="batch-actions">
            <button class="batch-btn approve" @click="batchApprove" :disabled="selectedItems.length === 0">
              <i class="fas fa-thumbs-up"></i>
              批量通过
            </button>
            <button class="batch-btn reject" @click="batchReject" :disabled="selectedItems.length === 0">
              <i class="fas fa-thumbs-down"></i>
              批量拒绝
            </button>
            <button class="batch-btn edit" @click="batchEdit" :disabled="selectedItems.length === 0">
              <i class="fas fa-edit"></i>
              批量编辑
            </button>
          </div>
          <div class="view-toggle">
            <button :class="['view-btn', { active: viewMode === 'list' }]" @click="viewMode = 'list'">
              <i class="fas fa-list"></i>
            </button>
            <button :class="['view-btn', { active: viewMode === 'card' }]" @click="viewMode = 'card'">
              <i class="fas fa-th-large"></i>
            </button>
          </div>
        </div>

        <div class="qa-list" :class="viewMode">
          <div 
            v-for="item in qaItems" 
            :key="item.id" 
            class="qa-item"
            :class="{ selected: selectedItems.includes(item.id) }"
            @click="toggleSelect(item.id)"
          >
            <div class="qa-checkbox">
              <i :class="selectedItems.includes(item.id) ? 'fas fa-check-square' : 'far fa-square'"></i>
            </div>
            <div class="qa-content">
              <div class="qa-question">
                <span class="qa-label">Q:</span>
                <span class="qa-text">{{ item.question }}</span>
              </div>
              <div class="qa-answer">
                <span class="qa-label">A:</span>
                <span class="qa-text">{{ item.answer }}</span>
              </div>
              <div class="qa-meta">
                <span class="meta-item score" :class="getScoreClass(item.score)">
                  <i class="fas fa-star"></i>
                  {{ item.score }}分
                </span>
                <span class="meta-item coherence">
                  <i class="fas fa-link"></i>
                  {{ getCoherenceText(item.coherence) }}
                </span>
                <span class="meta-item type">
                  <i class="fas fa-tag"></i>
                  {{ getTypeText(item.type) }}
                </span>
                <span class="meta-item status" :class="item.status">
                  <i :class="getStatusIcon(item.status)"></i>
                  {{ getStatusText(item.status) }}
                </span>
              </div>
            </div>
            <div class="qa-actions">
              <button class="action-btn approve" @click.stop="approveItem(item.id)">
                <i class="fas fa-check"></i>
              </button>
              <button class="action-btn edit" @click.stop="editItem(item)">
                <i class="fas fa-pen"></i>
              </button>
              <button class="action-btn reject" @click.stop="rejectItem(item.id)">
                <i class="fas fa-times"></i>
              </button>
            </div>
          </div>
        </div>

        <div class="pagination">
          <button class="page-btn" :disabled="currentPage === 1" @click="currentPage--">
            <i class="fas fa-chevron-left"></i>
          </button>
          <span class="page-info">第 {{ currentPage }} / {{ totalPages }} 页</span>
          <button class="page-btn" :disabled="currentPage >= totalPages" @click="currentPage++">
            <i class="fas fa-chevron-right"></i>
          </button>
        </div>
      </div>
    </div>

    <div class="finetune-section glass-card">
      <div class="section-header">
        <h4>
          <i class="fas fa-brain"></i>
          模型微调进度看板
        </h4>
        <button class="new-finetune-btn" @click="startNewFinetune">
          <i class="fas fa-plus"></i>
          发起新任务
        </button>
      </div>

      <div class="finetune-overview">
        <div class="overview-card">
          <div class="overview-icon training">
            <i class="fas fa-cogs"></i>
          </div>
          <div class="overview-content">
            <span class="overview-value">{{ finetuneStats.inProgress }}</span>
            <span class="overview-label">进行中</span>
          </div>
        </div>
        <div class="overview-card">
          <div class="overview-icon completed">
            <i class="fas fa-check-circle"></i>
          </div>
          <div class="overview-content">
            <span class="overview-value">{{ finetuneStats.completed }}</span>
            <span class="overview-label">已完成</span>
          </div>
        </div>
        <div class="overview-card">
          <div class="overview-icon queued">
            <i class="fas fa-clock"></i>
          </div>
          <div class="overview-content">
            <span class="overview-value">{{ finetuneStats.queued }}</span>
            <span class="overview-label">排队中</span>
          </div>
        </div>
        <div class="overview-card">
          <div class="overview-icon data">
            <i class="fas fa-database"></i>
          </div>
          <div class="overview-content">
            <span class="overview-value">{{ finetuneStats.totalSamples }}</span>
            <span class="overview-label">训练样本</span>
          </div>
        </div>
      </div>

      <div class="finetune-tasks">
        <div class="tasks-header">
          <span class="col task-name">任务名称</span>
          <span class="col task-model">基础模型</span>
          <span class="col task-data">数据集</span>
          <span class="col task-progress">进度</span>
          <span class="col task-metrics">指标</span>
          <span class="col task-status">状态</span>
          <span class="col task-actions">操作</span>
        </div>
        <div class="tasks-list">
          <div v-for="task in finetuneTasks" :key="task.id" class="task-row">
            <div class="col task-name">
              <div class="task-info">
                <span class="task-id">{{ task.id }}</span>
                <span class="task-desc">{{ task.name }}</span>
              </div>
            </div>
            <div class="col task-model">
              <span class="model-badge">{{ task.baseModel }}</span>
            </div>
            <div class="col task-data">
              <div class="data-info">
                <span class="data-count">{{ task.sampleCount }} 样本</span>
                <span class="data-source">{{ task.dataSource }}</span>
              </div>
            </div>
            <div class="col task-progress">
              <div class="progress-info">
                <div class="progress-bar">
                  <div class="progress-fill" :style="{ width: task.progress + '%' }" :class="getProgressClass(task.status)"></div>
                </div>
                <span class="progress-text">{{ task.progress }}%</span>
                <span class="progress-detail" v-if="task.status === 'training'">
                  Epoch {{ task.currentEpoch }}/{{ task.totalEpochs }}
                </span>
              </div>
            </div>
            <div class="col task-metrics">
              <div class="metrics-info" v-if="task.metrics">
                <span class="metric-item">
                  <span class="metric-label">Loss</span>
                  <span class="metric-value">{{ task.metrics.loss }}</span>
                </span>
                <span class="metric-item">
                  <span class="metric-label">Acc</span>
                  <span class="metric-value">{{ task.metrics.accuracy }}%</span>
                </span>
              </div>
              <span v-else class="no-metrics">-</span>
            </div>
            <div class="col task-status">
              <span class="status-badge" :class="task.status">
                <i :class="getStatusIconClass(task.status)"></i>
                {{ getTaskStatusText(task.status) }}
              </span>
            </div>
            <div class="col task-actions">
              <div class="action-buttons">
                <button class="task-action-btn" @click="viewTaskDetail(task)" title="查看详情">
                  <i class="fas fa-eye"></i>
                </button>
                <button class="task-action-btn" @click="downloadModel(task)" v-if="task.status === 'completed'" title="下载模型">
                  <i class="fas fa-download"></i>
                </button>
                <button class="task-action-btn stop" @click="stopTask(task)" v-if="task.status === 'training'" title="停止">
                  <i class="fas fa-stop"></i>
                </button>
                <button class="task-action-btn delete" @click="deleteTask(task)" v-if="['completed', 'failed'].includes(task.status)" title="删除">
                  <i class="fas fa-trash"></i>
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="finetune-charts">
        <div class="chart-card">
          <div class="chart-title">
            <i class="fas fa-chart-line"></i>
            训练损失曲线
          </div>
          <div class="chart-container">
            <svg viewBox="0 0 300 120" class="loss-chart">
              <defs>
                <linearGradient id="lossGradient" x1="0%" y1="0%" x2="0%" y2="100%">
                  <stop offset="0%" stop-color="rgba(239, 68, 68, 0.3)" />
                  <stop offset="100%" stop-color="rgba(239, 68, 68, 0.02)" />
                </linearGradient>
              </defs>
              <path :d="lossAreaPath" fill="url(#lossGradient)" />
              <path :d="lossLinePath" fill="none" stroke="#ef4444" stroke-width="2" />
            </svg>
          </div>
        </div>
        <div class="chart-card">
          <div class="chart-title">
            <i class="fas fa-chart-area"></i>
            准确率曲线
          </div>
          <div class="chart-container">
            <svg viewBox="0 0 300 120" class="acc-chart">
              <defs>
                <linearGradient id="accGradient" x1="0%" y1="0%" x2="0%" y2="100%">
                  <stop offset="0%" stop-color="rgba(16, 185, 129, 0.3)" />
                  <stop offset="100%" stop-color="rgba(16, 185, 129, 0.02)" />
                </linearGradient>
              </defs>
              <path :d="accAreaPath" fill="url(#accGradient)" />
              <path :d="accLinePath" fill="none" stroke="#10b981" stroke-width="2" />
            </svg>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { ref, computed } from 'vue'

export default {
  name: 'DataAnalysis',
  setup() {
    const totalQAPairs = ref(15632)
    const viewMode = ref('list')
    const currentPage = ref(1)
    const totalPages = ref(15)

    const filterSettings = ref({
      scoreMin: 60,
      scoreMax: 100,
      coherence: ['high', 'medium'],
      types: ['technical', 'behavioral'],
      timeRange: 'week'
    })

    const coherenceLevels = [
      { value: 'high', label: '高连贯', icon: 'fas fa-link' },
      { value: 'medium', label: '中等', icon: 'fas fa-minus' },
      { value: 'low', label: '低连贯', icon: 'fas fa-unlink' }
    ]

    const questionTypes = [
      { value: 'technical', label: '技术问题' },
      { value: 'behavioral', label: '行为问题' },
      { value: 'situational', label: '情景问题' },
      { value: 'project', label: '项目经验' }
    ]

    const timeRanges = [
      { value: 'today', label: '今天' },
      { value: 'week', label: '本周' },
      { value: 'month', label: '本月' },
      { value: 'all', label: '全部' }
    ]

    const selectedItems = ref([])

    const qaItems = ref([
      { id: 1, question: '请介绍一下Vue3的响应式原理？', answer: 'Vue3使用Proxy替代了Vue2的Object.defineProperty来实现响应式...', score: 95, coherence: 'high', type: 'technical', status: 'approved' },
      { id: 2, question: '你如何处理团队冲突？', answer: '我会首先了解冲突的根源，然后...', score: 88, coherence: 'high', type: 'behavioral', status: 'pending' },
      { id: 3, question: '请描述一个你解决过的技术难题', answer: '在之前的项目中，我们遇到了性能瓶颈...', score: 82, coherence: 'medium', type: 'project', status: 'approved' },
      { id: 4, question: '如果项目延期你会怎么处理？', answer: '首先分析延期原因，然后制定补救计划...', score: 76, coherence: 'medium', type: 'situational', status: 'pending' },
      { id: 5, question: '解释一下微服务架构的优缺点', answer: '微服务架构的优点包括独立部署、技术栈灵活...', score: 91, coherence: 'high', type: 'technical', status: 'approved' }
    ])

    const filteredCount = computed(() => 8945)
    const qualityRate = computed(() => 57.2)

    const finetuneStats = ref({
      inProgress: 2,
      completed: 15,
      queued: 3,
      totalSamples: 45680
    })

    const finetuneTasks = ref([
      { id: 'FT-001', name: '面试对话优化v2.1', baseModel: 'GPT-3.5', sampleCount: 12000, dataSource: '筛选数据集A', progress: 78, currentEpoch: 4, totalEpochs: 5, status: 'training', metrics: { loss: 0.23, accuracy: 89.5 } },
      { id: 'FT-002', name: '技术问答专项', baseModel: 'GPT-4', sampleCount: 8500, dataSource: '技术问题集', progress: 45, currentEpoch: 2, totalEpochs: 5, status: 'training', metrics: { loss: 0.31, accuracy: 85.2 } },
      { id: 'FT-003', name: '行为面试模型', baseModel: 'GPT-3.5', sampleCount: 6000, dataSource: '行为问题集', progress: 0, currentEpoch: 0, totalEpochs: 3, status: 'queued', metrics: null },
      { id: 'FT-004', name: '面试对话优化v2.0', baseModel: 'GPT-3.5', sampleCount: 10000, dataSource: '筛选数据集A', progress: 100, currentEpoch: 5, totalEpochs: 5, status: 'completed', metrics: { loss: 0.18, accuracy: 92.3 } },
      { id: 'FT-005', name: '情景问答模型', baseModel: 'GPT-3.5', sampleCount: 4500, dataSource: '情景问题集', progress: 35, currentEpoch: 1, totalEpochs: 3, status: 'failed', metrics: null }
    ])

    const lossData = ref([0.8, 0.65, 0.52, 0.42, 0.35, 0.28, 0.23, 0.19, 0.16, 0.14])
    const accData = ref([65, 72, 78, 82, 85, 87, 89, 90, 91, 92])

    const lossLinePath = computed(() => {
      const points = lossData.value.map((val, index) => ({
        x: 20 + (index * 28),
        y: 100 - (val / 1) * 100
      }))
      return points.map((p, i) => `${i === 0 ? 'M' : 'L'} ${p.x} ${p.y}`).join(' ')
    })

    const lossAreaPath = computed(() => {
      const linePath = lossLinePath.value
      return `${linePath} L 280 100 L 20 100 Z`
    })

    const accLinePath = computed(() => {
      const points = accData.value.map((val, index) => ({
        x: 20 + (index * 28),
        y: 110 - (val / 100) * 100
      }))
      return points.map((p, i) => `${i === 0 ? 'M' : 'L'} ${p.x} ${p.y}`).join(' ')
    })

    const accAreaPath = computed(() => {
      const linePath = accLinePath.value
      return `${linePath} L 280 110 L 20 110 Z`
    })

    const toggleCoherence = (value) => {
      const index = filterSettings.value.coherence.indexOf(value)
      if (index > -1) {
        filterSettings.value.coherence.splice(index, 1)
      } else {
        filterSettings.value.coherence.push(value)
      }
    }

    const toggleType = (value) => {
      const index = filterSettings.value.types.indexOf(value)
      if (index > -1) {
        filterSettings.value.types.splice(index, 1)
      } else {
        filterSettings.value.types.push(value)
      }
    }

    const toggleSelect = (id) => {
      const index = selectedItems.value.indexOf(id)
      if (index > -1) {
        selectedItems.value.splice(index, 1)
      } else {
        selectedItems.value.push(id)
      }
    }

    const selectAll = () => {
      if (selectedItems.value.length === qaItems.value.length) {
        selectedItems.value = []
      } else {
        selectedItems.value = qaItems.value.map(item => item.id)
      }
    }

    const applyFilter = () => {
      console.log('Applying filter:', filterSettings.value)
    }

    const exportJSONL = () => {
      console.log('Exporting JSONL:', selectedItems.value)
    }

    const batchApprove = () => {
      console.log('Batch approve:', selectedItems.value)
    }

    const batchReject = () => {
      console.log('Batch reject:', selectedItems.value)
    }

    const batchEdit = () => {
      console.log('Batch edit:', selectedItems.value)
    }

    const approveItem = (id) => {
      console.log('Approve item:', id)
    }

    const editItem = (item) => {
      console.log('Edit item:', item)
    }

    const rejectItem = (id) => {
      console.log('Reject item:', id)
    }

    const startNewFinetune = () => {
      console.log('Starting new finetune task')
    }

    const viewTaskDetail = (task) => {
      console.log('View task detail:', task)
    }

    const downloadModel = (task) => {
      console.log('Download model:', task)
    }

    const stopTask = (task) => {
      console.log('Stop task:', task)
    }

    const deleteTask = (task) => {
      console.log('Delete task:', task)
    }

    const getScoreClass = (score) => {
      if (score >= 90) return 'excellent'
      if (score >= 80) return 'good'
      if (score >= 70) return 'normal'
      return 'low'
    }

    const getCoherenceText = (level) => {
      const map = { high: '高连贯', medium: '中等', low: '低连贯' }
      return map[level] || level
    }

    const getTypeText = (type) => {
      const map = { technical: '技术', behavioral: '行为', situational: '情景', project: '项目' }
      return map[type] || type
    }

    const getStatusText = (status) => {
      const map = { approved: '已通过', pending: '待审核', rejected: '已拒绝' }
      return map[status] || status
    }

    const getStatusIcon = (status) => {
      const map = { approved: 'fas fa-check-circle', pending: 'fas fa-clock', rejected: 'fas fa-times-circle' }
      return map[status] || 'fas fa-question'
    }

    const getProgressClass = (status) => {
      if (status === 'training') return 'training'
      if (status === 'completed') return 'completed'
      if (status === 'failed') return 'failed'
      return 'queued'
    }

    const getStatusIconClass = (status) => {
      const map = { training: 'fas fa-spinner fa-spin', completed: 'fas fa-check', queued: 'fas fa-clock', failed: 'fas fa-times' }
      return map[status] || 'fas fa-question'
    }

    const getTaskStatusText = (status) => {
      const map = { training: '训练中', completed: '已完成', queued: '排队中', failed: '失败' }
      return map[status] || status
    }

    return {
      totalQAPairs,
      viewMode,
      currentPage,
      totalPages,
      filterSettings,
      coherenceLevels,
      questionTypes,
      timeRanges,
      selectedItems,
      qaItems,
      filteredCount,
      qualityRate,
      finetuneStats,
      finetuneTasks,
      lossLinePath,
      lossAreaPath,
      accLinePath,
      accAreaPath,
      toggleCoherence,
      toggleType,
      toggleSelect,
      selectAll,
      applyFilter,
      exportJSONL,
      batchApprove,
      batchReject,
      batchEdit,
      approveItem,
      editItem,
      rejectItem,
      startNewFinetune,
      viewTaskDetail,
      downloadModel,
      stopTask,
      deleteTask,
      getScoreClass,
      getCoherenceText,
      getTypeText,
      getStatusText,
      getStatusIcon,
      getProgressClass,
      getStatusIconClass,
      getTaskStatusText
    }
  }
}
</script>

<style scoped>
.data-analysis-page {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px 24px;
}

.header-info h3 {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0 0 4px;
}

.header-info h3 i {
  color: var(--primary-color);
}

.header-info p {
  font-size: 13px;
  color: var(--text-secondary);
  margin: 0;
}

.data-stats {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  background: var(--glass-bg);
  border-radius: var(--radius-md);
  font-size: 13px;
  color: var(--text-secondary);
}

.main-grid {
  display: grid;
  grid-template-columns: 320px 1fr;
  gap: 24px;
}

.qa-filter-section,
.annotation-section {
  padding: 24px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.section-header h4 {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
}

.section-header h4 i {
  color: var(--primary-color);
}

.apply-filter-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  background: linear-gradient(135deg, var(--primary-color), var(--primary-light));
  border: none;
  border-radius: var(--radius-sm);
  font-size: 12px;
  color: white;
  cursor: pointer;
  font-family: inherit;
}

.filter-controls {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.filter-group {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.filter-label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 500;
  color: var(--text-secondary);
}

.filter-label i {
  color: var(--primary-color);
}

.range-slider {
  position: relative;
}

.range-values {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: var(--text-primary);
  margin-bottom: 8px;
}

.dual-slider {
  position: relative;
  height: 20px;
}

.dual-slider input {
  position: absolute;
  width: 100%;
  height: 4px;
  background: transparent;
  pointer-events: none;
  -webkit-appearance: none;
}

.dual-slider input::-webkit-slider-thumb {
  -webkit-appearance: none;
  width: 16px;
  height: 16px;
  background: var(--primary-color);
  border-radius: 50%;
  cursor: pointer;
  pointer-events: auto;
}

.slider-track {
  position: relative;
  height: 4px;
  background: rgba(59, 89, 152, 0.1);
  border-radius: 2px;
  margin-top: -12px;
}

.slider-fill {
  position: absolute;
  height: 100%;
  background: linear-gradient(90deg, var(--primary-color), var(--primary-light));
  border-radius: 2px;
}

.coherence-options {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.coherence-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  background: var(--glass-bg);
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-sm);
  font-size: 12px;
  color: var(--text-secondary);
  cursor: pointer;
  font-family: inherit;
  transition: all 0.2s ease;
}

.coherence-btn.active {
  background: rgba(59, 89, 152, 0.15);
  border-color: var(--primary-color);
  color: var(--primary-color);
}

.type-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.type-tag {
  padding: 6px 12px;
  background: var(--glass-bg);
  border: 1px solid var(--glass-border);
  border-radius: 20px;
  font-size: 12px;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all 0.2s ease;
}

.type-tag.active {
  background: rgba(59, 89, 152, 0.15);
  border-color: var(--primary-color);
  color: var(--primary-color);
}

.time-range-buttons {
  display: flex;
  gap: 6px;
}

.time-btn {
  flex: 1;
  padding: 8px;
  background: var(--glass-bg);
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-sm);
  font-size: 12px;
  color: var(--text-secondary);
  cursor: pointer;
  font-family: inherit;
  transition: all 0.2s ease;
}

.time-btn.active {
  background: var(--primary-color);
  border-color: var(--primary-color);
  color: white;
}

.filter-summary {
  display: flex;
  gap: 20px;
  padding: 16px;
  background: var(--glass-bg);
  border-radius: var(--radius-md);
  margin-top: 20px;
}

.summary-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.summary-label {
  font-size: 11px;
  color: var(--text-secondary);
}

.summary-value {
  font-size: 18px;
  font-weight: 700;
  color: var(--text-primary);
}

.annotation-actions {
  display: flex;
  gap: 10px;
}

.select-all-btn,
.export-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border: none;
  border-radius: var(--radius-sm);
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  font-family: inherit;
}

.select-all-btn {
  background: var(--glass-bg);
  border: 1px solid var(--glass-border);
  color: var(--text-secondary);
}

.export-btn {
  background: rgba(16, 185, 129, 0.15);
  color: #10b981;
}

.export-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.annotation-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background: var(--glass-bg);
  border-radius: var(--radius-md);
  margin-bottom: 16px;
}

.batch-actions {
  display: flex;
  gap: 8px;
}

.batch-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border: none;
  border-radius: var(--radius-sm);
  font-size: 12px;
  cursor: pointer;
  font-family: inherit;
}

.batch-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.batch-btn.approve {
  background: rgba(16, 185, 129, 0.15);
  color: #10b981;
}

.batch-btn.reject {
  background: rgba(239, 68, 68, 0.15);
  color: #ef4444;
}

.batch-btn.edit {
  background: rgba(59, 89, 152, 0.15);
  color: var(--primary-color);
}

.view-toggle {
  display: flex;
  gap: 4px;
}

.view-btn {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: transparent;
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-sm);
  color: var(--text-secondary);
  cursor: pointer;
}

.view-btn.active {
  background: var(--primary-color);
  border-color: var(--primary-color);
  color: white;
}

.qa-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-height: 400px;
  overflow-y: auto;
}

.qa-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 16px;
  background: var(--glass-bg);
  border-radius: var(--radius-md);
  border: 1px solid transparent;
  cursor: pointer;
  transition: all 0.2s ease;
}

.qa-item:hover {
  border-color: var(--glass-border);
}

.qa-item.selected {
  border-color: var(--primary-color);
  background: rgba(59, 89, 152, 0.05);
}

.qa-checkbox {
  color: var(--text-light);
  font-size: 18px;
  padding-top: 2px;
}

.qa-item.selected .qa-checkbox {
  color: var(--primary-color);
}

.qa-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.qa-question,
.qa-answer {
  display: flex;
  gap: 8px;
}

.qa-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--primary-color);
  flex-shrink: 0;
}

.qa-text {
  font-size: 13px;
  color: var(--text-primary);
  line-height: 1.5;
}

.qa-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 4px;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: var(--text-secondary);
}

.meta-item.score.excellent { color: #10b981; }
.meta-item.score.good { color: #34d399; }
.meta-item.score.normal { color: #f59e0b; }
.meta-item.score.low { color: #ef4444; }

.meta-item.status.approved { color: #10b981; }
.meta-item.status.pending { color: #f59e0b; }
.meta-item.status.rejected { color: #ef4444; }

.qa-actions {
  display: flex;
  gap: 6px;
}

.action-btn {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: var(--radius-sm);
  font-size: 12px;
  cursor: pointer;
  opacity: 0;
  transition: all 0.2s ease;
}

.qa-item:hover .action-btn {
  opacity: 1;
}

.action-btn.approve {
  background: rgba(16, 185, 129, 0.15);
  color: #10b981;
}

.action-btn.edit {
  background: rgba(59, 89, 152, 0.15);
  color: var(--primary-color);
}

.action-btn.reject {
  background: rgba(239, 68, 68, 0.15);
  color: #ef4444;
}

.pagination {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 16px;
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid var(--glass-border);
}

.page-btn {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-sm);
  background: var(--glass-bg);
  color: var(--text-primary);
  cursor: pointer;
}

.page-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.page-info {
  font-size: 13px;
  color: var(--text-secondary);
}

.finetune-section {
  padding: 24px;
}

.new-finetune-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  background: linear-gradient(135deg, var(--primary-color), var(--primary-light));
  border: none;
  border-radius: var(--radius-sm);
  font-size: 13px;
  color: white;
  cursor: pointer;
  font-family: inherit;
}

.finetune-overview {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 24px;
}

.overview-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px;
  background: var(--glass-bg);
  border-radius: var(--radius-md);
}

.overview-icon {
  width: 44px;
  height: 44px;
  border-radius: var(--radius-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 18px;
}

.overview-icon.training { background: linear-gradient(135deg, #3b5998, #5a7ab8); }
.overview-icon.completed { background: linear-gradient(135deg, #10b981, #34d399); }
.overview-icon.queued { background: linear-gradient(135deg, #f59e0b, #fbbf24); }
.overview-icon.data { background: linear-gradient(135deg, #8b5cf6, #a78bfa); }

.overview-content {
  display: flex;
  flex-direction: column;
}

.overview-value {
  font-size: 24px;
  font-weight: 700;
  color: var(--text-primary);
}

.overview-label {
  font-size: 12px;
  color: var(--text-secondary);
}

.finetune-tasks {
  margin-bottom: 24px;
}

.tasks-header {
  display: flex;
  padding: 12px 16px;
  background: var(--glass-bg);
  border-radius: var(--radius-md);
  font-size: 12px;
  font-weight: 500;
  color: var(--text-secondary);
}

.tasks-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 8px;
}

.task-row {
  display: flex;
  align-items: center;
  padding: 14px 16px;
  background: var(--glass-bg);
  border-radius: var(--radius-md);
}

.col {
  display: flex;
  align-items: center;
}

.col.task-name { flex: 0 0 180px; }
.col.task-model { flex: 0 0 100px; }
.col.task-data { flex: 0 0 120px; }
.col.task-progress { flex: 1; }
.col.task-metrics { flex: 0 0 120px; }
.col.task-status { flex: 0 0 100px; }
.col.task-actions { flex: 0 0 100px; justify-content: flex-end; }

.task-info {
  display: flex;
  flex-direction: column;
}

.task-id {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-primary);
}

.task-desc {
  font-size: 11px;
  color: var(--text-secondary);
}

.model-badge {
  padding: 4px 10px;
  background: rgba(59, 89, 152, 0.15);
  border-radius: 12px;
  font-size: 11px;
  font-weight: 500;
  color: var(--primary-color);
}

.data-info {
  display: flex;
  flex-direction: column;
}

.data-count {
  font-size: 12px;
  font-weight: 500;
  color: var(--text-primary);
}

.data-source {
  font-size: 10px;
  color: var(--text-secondary);
}

.progress-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
  width: 100%;
}

.progress-bar {
  height: 6px;
  background: rgba(59, 89, 152, 0.1);
  border-radius: 3px;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  border-radius: 3px;
  transition: width 0.5s ease;
}

.progress-fill.training { background: linear-gradient(90deg, #3b5998, #5a7ab8); }
.progress-fill.completed { background: linear-gradient(90deg, #10b981, #34d399); }
.progress-fill.failed { background: linear-gradient(90deg, #ef4444, #f87171); }
.progress-fill.queued { background: linear-gradient(90deg, #f59e0b, #fbbf24); }

.progress-text {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-primary);
}

.progress-detail {
  font-size: 10px;
  color: var(--text-secondary);
}

.metrics-info {
  display: flex;
  gap: 12px;
}

.metric-item {
  display: flex;
  flex-direction: column;
}

.metric-label {
  font-size: 10px;
  color: var(--text-secondary);
}

.metric-value {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-primary);
}

.no-metrics {
  font-size: 12px;
  color: var(--text-light);
}

.status-badge {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  border-radius: 12px;
  font-size: 11px;
  font-weight: 500;
}

.status-badge.training {
  background: rgba(59, 89, 152, 0.15);
  color: var(--primary-color);
}

.status-badge.completed {
  background: rgba(16, 185, 129, 0.15);
  color: #10b981;
}

.status-badge.queued {
  background: rgba(245, 158, 11, 0.15);
  color: #f59e0b;
}

.status-badge.failed {
  background: rgba(239, 68, 68, 0.15);
  color: #ef4444;
}

.action-buttons {
  display: flex;
  gap: 6px;
}

.task-action-btn {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: var(--radius-sm);
  background: rgba(59, 89, 152, 0.1);
  color: var(--text-secondary);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.task-action-btn:hover {
  background: rgba(59, 89, 152, 0.2);
  color: var(--primary-color);
}

.task-action-btn.stop:hover {
  background: rgba(239, 68, 68, 0.2);
  color: #ef4444;
}

.task-action-btn.delete:hover {
  background: rgba(239, 68, 68, 0.2);
  color: #ef4444;
}

.finetune-charts {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
}

.chart-card {
  padding: 20px;
  background: var(--glass-bg);
  border-radius: var(--radius-md);
}

.chart-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 500;
  color: var(--text-primary);
  margin-bottom: 16px;
}

.chart-title i {
  color: var(--primary-color);
}

.chart-container {
  height: 120px;
}

.loss-chart,
.acc-chart {
  width: 100%;
  height: 100%;
}
</style>
