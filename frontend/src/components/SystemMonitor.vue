<template>
  <div class="system-monitor-page">
    <div class="page-header glass-card">
      <div class="header-info">
        <h3>
          <i class="fas fa-server"></i>
          系统监控
        </h3>
        <p>实时监控系统运行状态与任务队列</p>
      </div>
      <div class="header-actions">
        <span class="update-time">
          <i class="fas fa-sync-alt"></i>
          更新于 {{ lastUpdateTime }}
        </span>
      </div>
    </div>

    <div class="monitor-grid">
      <div class="interview-rooms-section glass-card">
        <div class="section-header">
          <h4>
            <i class="fas fa-video"></i>
            高并发面试房间监控
          </h4>
          <div class="live-indicator">
            <span class="pulse"></span>
            实时
          </div>
        </div>
        
        <div class="rooms-overview">
          <div class="overview-stats">
            <div class="overview-stat">
              <div class="stat-icon rooms">
                <i class="fas fa-door-open"></i>
              </div>
              <div class="stat-content">
                <span class="stat-value">{{ roomsStats.activeRooms }}</span>
                <span class="stat-label">活跃房间</span>
              </div>
            </div>
            <div class="overview-stat">
              <div class="stat-icon websocket">
                <i class="fas fa-plug"></i>
              </div>
              <div class="stat-content">
                <span class="stat-value">{{ roomsStats.websocketConnections }}</span>
                <span class="stat-label">WebSocket连接</span>
              </div>
            </div>
            <div class="overview-stat">
              <div class="stat-icon audio">
                <i class="fas fa-microphone"></i>
              </div>
              <div class="stat-content">
                <span class="stat-value">{{ roomsStats.audioStreams }}</span>
                <span class="stat-label">音频流</span>
              </div>
            </div>
            <div class="overview-stat">
              <div class="stat-icon bandwidth">
                <i class="fas fa-network-wired"></i>
              </div>
              <div class="stat-content">
                <span class="stat-value">{{ roomsStats.bandwidth }} Mbps</span>
                <span class="stat-label">总带宽</span>
              </div>
            </div>
          </div>
        </div>

        <div class="websocket-section">
          <div class="subsection-title">
            <i class="fas fa-link"></i>
            WebSocket连接状态
          </div>
          <div class="connection-chart">
            <div class="chart-y-axis">
              <span>{{ maxConnections }}</span>
              <span>{{ Math.floor(maxConnections * 0.75) }}</span>
              <span>{{ Math.floor(maxConnections * 0.5) }}</span>
              <span>{{ Math.floor(maxConnections * 0.25) }}</span>
              <span>0</span>
            </div>
            <div class="chart-area">
              <svg viewBox="0 0 400 120" class="line-chart">
                <defs>
                  <linearGradient id="wsGradient" x1="0%" y1="0%" x2="0%" y2="100%">
                    <stop offset="0%" stop-color="rgba(16, 185, 129, 0.3)" />
                    <stop offset="100%" stop-color="rgba(16, 185, 129, 0.02)" />
                  </linearGradient>
                </defs>
                <path :d="wsAreaPath" fill="url(#wsGradient)" />
                <path :d="wsLinePath" fill="none" stroke="#10b981" stroke-width="2" />
                <circle v-for="(point, index) in wsPoints" :key="index" 
                  :cx="point.x" :cy="point.y" r="3" fill="#10b981" />
              </svg>
              <div class="chart-x-axis">
                <span v-for="(label, index) in timeLabels" :key="index">{{ label }}</span>
              </div>
            </div>
          </div>
          <div class="connection-stats">
            <div class="conn-stat">
              <span class="conn-label">峰值连接</span>
              <span class="conn-value">{{ roomsStats.peakConnections }}</span>
            </div>
            <div class="conn-stat">
              <span class="conn-label">平均连接时长</span>
              <span class="conn-value">{{ roomsStats.avgConnectionTime }}min</span>
            </div>
            <div class="conn-stat">
              <span class="conn-label">重连率</span>
              <span class="conn-value" :class="getReconnectClass(roomsStats.reconnectRate)">{{ roomsStats.reconnectRate }}%</span>
            </div>
          </div>
        </div>

        <div class="audio-quality-section">
          <div class="subsection-title">
            <i class="fas fa-headphones"></i>
            音频流质量监控
          </div>
          <div class="quality-grid">
            <div v-for="stream in audioStreams" :key="stream.id" class="quality-card">
              <div class="quality-header">
                <span class="stream-id">{{ stream.id }}</span>
                <span class="stream-status" :class="stream.status">{{ getStreamStatusText(stream.status) }}</span>
              </div>
              <div class="quality-metrics">
                <div class="quality-metric">
                  <span class="metric-label">延迟</span>
                  <span class="metric-value" :class="getLatencyClass(stream.latency)">{{ stream.latency }}ms</span>
                </div>
                <div class="quality-metric">
                  <span class="metric-label">丢包率</span>
                  <span class="metric-value" :class="getPacketLossClass(stream.packetLoss)">{{ stream.packetLoss }}%</span>
                </div>
                <div class="quality-metric">
                  <span class="metric-label">码率</span>
                  <span class="metric-value">{{ stream.bitrate }}kbps</span>
                </div>
                <div class="quality-metric">
                  <span class="metric-label">抖动</span>
                  <span class="metric-value" :class="getJitterClass(stream.jitter)">{{ stream.jitter }}ms</span>
                </div>
              </div>
              <div class="quality-bar">
                <div class="quality-score" :style="{ width: stream.qualityScore + '%' }" :class="getQualityClass(stream.qualityScore)"></div>
              </div>
              <div class="quality-score-text">质量评分: {{ stream.qualityScore }}分</div>
            </div>
          </div>
        </div>
      </div>

      <div class="task-queue-section glass-card">
        <div class="section-header">
          <h4>
            <i class="fas fa-tasks"></i>
            异步任务队列状态
          </h4>
        </div>

        <div class="queue-overview">
          <div class="queue-stat-card">
            <div class="queue-stat-icon pending">
              <i class="fas fa-clock"></i>
            </div>
            <div class="queue-stat-content">
              <span class="queue-stat-value">{{ queueStats.pendingTasks }}</span>
              <span class="queue-stat-label">排队任务</span>
            </div>
          </div>
          <div class="queue-stat-card">
            <div class="queue-stat-icon processing">
              <i class="fas fa-spinner fa-spin"></i>
            </div>
            <div class="queue-stat-content">
              <span class="queue-stat-value">{{ queueStats.processingTasks }}</span>
              <span class="queue-stat-label">处理中</span>
            </div>
          </div>
          <div class="queue-stat-card">
            <div class="queue-stat-icon completed">
              <i class="fas fa-check"></i>
            </div>
            <div class="queue-stat-content">
              <span class="queue-stat-value">{{ queueStats.completedTasks }}</span>
              <span class="queue-stat-label">已完成</span>
            </div>
          </div>
        </div>

        <div class="processing-speed-section">
          <div class="subsection-title">
            <i class="fas fa-tachometer-alt"></i>
            处理速度
          </div>
          <div class="speed-stats">
            <div class="speed-item">
              <div class="speed-gauge">
                <svg viewBox="0 0 100 60" class="gauge-chart">
                  <path d="M 10 55 A 40 40 0 0 1 90 55" fill="none" stroke="rgba(59, 89, 152, 0.2)" stroke-width="8" stroke-linecap="round" />
                  <path :d="getGaugePath(queueStats.throughput, 200)" fill="none" stroke="#10b981" stroke-width="8" stroke-linecap="round" />
                </svg>
                <div class="gauge-value">{{ queueStats.throughput }}</div>
              </div>
              <div class="speed-label">任务/分钟</div>
            </div>
            <div class="speed-item">
              <div class="speed-gauge">
                <svg viewBox="0 0 100 60" class="gauge-chart">
                  <path d="M 10 55 A 40 40 0 0 1 90 55" fill="none" stroke="rgba(59, 89, 152, 0.2)" stroke-width="8" stroke-linecap="round" />
                  <path :d="getGaugePath(queueStats.avgProcessTime, 5000)" fill="none" stroke="#f59e0b" stroke-width="8" stroke-linecap="round" />
                </svg>
                <div class="gauge-value">{{ queueStats.avgProcessTime }}ms</div>
              </div>
              <div class="speed-label">平均处理时间</div>
            </div>
            <div class="speed-item">
              <div class="speed-gauge">
                <svg viewBox="0 0 100 60" class="gauge-chart">
                  <path d="M 10 55 A 40 40 0 0 1 90 55" fill="none" stroke="rgba(59, 89, 152, 0.2)" stroke-width="8" stroke-linecap="round" />
                  <path :d="getGaugePath(queueStats.successRate, 100)" fill="none" stroke="#3b5998" stroke-width="8" stroke-linecap="round" />
                </svg>
                <div class="gauge-value">{{ queueStats.successRate }}%</div>
              </div>
              <div class="speed-label">成功率</div>
            </div>
          </div>
        </div>

        <div class="task-types-section">
          <div class="subsection-title">
            <i class="fas fa-layer-group"></i>
            任务类型分布
          </div>
          <div class="task-type-list">
            <div v-for="task in taskTypes" :key="task.name" class="task-type-item">
              <div class="task-type-info">
                <span class="task-type-name">{{ task.name }}</span>
                <span class="task-type-count">{{ task.count }} 个任务</span>
              </div>
              <div class="task-type-bar">
                <div class="task-type-fill" :style="{ width: task.percentage + '%', background: task.color }"></div>
              </div>
              <span class="task-type-percentage">{{ task.percentage }}%</span>
            </div>
          </div>
        </div>

        <div class="workers-section">
          <div class="subsection-title">
            <i class="fas fa-cogs"></i>
            Worker状态
          </div>
          <div class="workers-grid">
            <div v-for="worker in workers" :key="worker.id" class="worker-card" :class="worker.status">
              <div class="worker-header">
                <span class="worker-id">{{ worker.id }}</span>
                <span class="worker-status" :class="worker.status">{{ getWorkerStatusText(worker.status) }}</span>
              </div>
              <div class="worker-metrics">
                <div class="worker-metric">
                  <span class="wm-label">当前任务</span>
                  <span class="wm-value">{{ worker.currentTask || '空闲' }}</span>
                </div>
                <div class="worker-metric">
                  <span class="wm-label">已完成</span>
                  <span class="wm-value">{{ worker.completedCount }}</span>
                </div>
                <div class="worker-metric">
                  <span class="wm-label">CPU</span>
                  <span class="wm-value" :class="getUsageClass(worker.cpu)">{{ worker.cpu }}%</span>
                </div>
                <div class="worker-metric">
                  <span class="wm-label">内存</span>
                  <span class="wm-value" :class="getUsageClass(worker.memory)">{{ worker.memory }}%</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="dead-letter-section glass-card">
      <div class="section-header">
        <h4>
          <i class="fas fa-exclamation-triangle"></i>
          死信队列管理
        </h4>
        <div class="dlq-stats">
          <span class="dlq-count">
            <i class="fas fa-envelope"></i>
            {{ deadLetterQueue.total }} 条消息
          </span>
          <button class="retry-all-btn" @click="retryAll" :disabled="deadLetterQueue.total === 0">
            <i class="fas fa-redo"></i>
            全部重试
          </button>
          <button class="clear-all-btn" @click="clearAll" :disabled="deadLetterQueue.total === 0">
            <i class="fas fa-trash"></i>
            清空队列
          </button>
        </div>
      </div>

      <div class="dlq-filters">
        <select v-model="dlqFilter" class="glass-select">
          <option value="all">全部类型</option>
          <option value="interview">面试任务</option>
          <option value="ai">AI处理</option>
          <option value="notification">通知</option>
          <option value="report">报告生成</option>
        </select>
        <select v-model="dlqErrorFilter" class="glass-select">
          <option value="all">全部错误</option>
          <option value="timeout">超时</option>
          <option value="rate_limit">限流</option>
          <option value="error">处理错误</option>
        </select>
      </div>

      <div class="dlq-list">
        <div v-for="item in filteredDLQItems" :key="item.id" class="dlq-item">
          <div class="dlq-item-header">
            <div class="dlq-item-info">
              <span class="dlq-item-type" :class="item.type">{{ getTypeText(item.type) }}</span>
              <span class="dlq-item-id">{{ item.id }}</span>
            </div>
            <span class="dlq-item-time">{{ item.failedAt }}</span>
          </div>
          <div class="dlq-item-content">
            <div class="dlq-error-info">
              <i class="fas fa-times-circle"></i>
              <span class="error-type">{{ item.errorType }}</span>
              <span class="error-message">{{ item.errorMessage }}</span>
            </div>
            <div class="dlq-retry-info">
              <span class="retry-count">重试次数: {{ item.retryCount }}/{{ item.maxRetry }}</span>
            </div>
          </div>
          <div class="dlq-item-actions">
            <button class="action-btn retry" @click="retryItem(item.id)">
              <i class="fas fa-redo"></i>
              重试
            </button>
            <button class="action-btn view" @click="viewDetail(item)">
              <i class="fas fa-eye"></i>
              详情
            </button>
            <button class="action-btn delete" @click="deleteItem(item.id)">
              <i class="fas fa-trash"></i>
              删除
            </button>
          </div>
        </div>
      </div>

      <div class="dlq-pagination" v-if="deadLetterQueue.total > 0">
        <button class="page-btn" :disabled="dlqPage === 1" @click="dlqPage--">
          <i class="fas fa-chevron-left"></i>
        </button>
        <span class="page-info">第 {{ dlqPage }} / {{ totalPages }} 页</span>
        <button class="page-btn" :disabled="dlqPage >= totalPages" @click="dlqPage++">
          <i class="fas fa-chevron-right"></i>
        </button>
      </div>
    </div>
  </div>
</template>

<script>
import { ref, computed } from 'vue'

export default {
  name: 'SystemMonitor',
  setup() {
    const lastUpdateTime = ref('刚刚')
    const dlqFilter = ref('all')
    const dlqErrorFilter = ref('all')
    const dlqPage = ref(1)

    const roomsStats = ref({
      activeRooms: 47,
      websocketConnections: 156,
      audioStreams: 89,
      bandwidth: 245.8,
      peakConnections: 203,
      avgConnectionTime: 28,
      reconnectRate: 2.3
    })

    const maxConnections = 250
    const wsConnectionData = ref([120, 145, 132, 156, 148, 167, 155, 178, 162, 156, 145, 156])
    const timeLabels = ['5min', '', '', '', '', '现在']

    const wsPoints = computed(() => {
      const maxVal = maxConnections
      return wsConnectionData.value.map((val, index) => ({
        x: 20 + (index * 30),
        y: 110 - (val / maxVal) * 100
      }))
    })

    const wsLinePath = computed(() => {
      return wsPoints.value.map((p, i) => `${i === 0 ? 'M' : 'L'} ${p.x} ${p.y}`).join(' ')
    })

    const wsAreaPath = computed(() => {
      const linePath = wsLinePath.value
      return `${linePath} L 350 110 L 20 110 Z`
    })

    const audioStreams = ref([
      { id: 'STREAM-001', status: 'good', latency: 45, packetLoss: 0.1, bitrate: 128, jitter: 5, qualityScore: 95 },
      { id: 'STREAM-002', status: 'good', latency: 52, packetLoss: 0.2, bitrate: 128, jitter: 8, qualityScore: 92 },
      { id: 'STREAM-003', status: 'warning', latency: 120, packetLoss: 1.5, bitrate: 96, jitter: 25, qualityScore: 78 },
      { id: 'STREAM-004', status: 'good', latency: 38, packetLoss: 0.05, bitrate: 128, jitter: 3, qualityScore: 98 }
    ])

    const queueStats = ref({
      pendingTasks: 234,
      processingTasks: 12,
      completedTasks: 8945,
      throughput: 156,
      avgProcessTime: 1250,
      successRate: 98.5
    })

    const taskTypes = ref([
      { name: 'AI面试对话', count: 89, percentage: 38, color: '#3b5998' },
      { name: '报告生成', count: 67, percentage: 29, color: '#10b981' },
      { name: '向量检索', count: 45, percentage: 19, color: '#f59e0b' },
      { name: '通知推送', count: 33, percentage: 14, color: '#8b5cf6' }
    ])

    const workers = ref([
      { id: 'Worker-01', status: 'busy', currentTask: 'AI对话处理', completedCount: 1234, cpu: 72, memory: 65 },
      { id: 'Worker-02', status: 'busy', currentTask: '报告生成', completedCount: 987, cpu: 58, memory: 52 },
      { id: 'Worker-03', status: 'idle', currentTask: null, completedCount: 1567, cpu: 5, memory: 28 },
      { id: 'Worker-04', status: 'busy', currentTask: '向量检索', completedCount: 756, cpu: 45, memory: 48 }
    ])

    const deadLetterQueue = ref({
      total: 23,
      items: [
        { id: 'DLQ-001', type: 'ai', errorType: 'timeout', errorMessage: 'OpenAI API响应超时', retryCount: 3, maxRetry: 3, failedAt: '14:32:15' },
        { id: 'DLQ-002', type: 'interview', errorType: 'rate_limit', errorMessage: '请求频率超过限制', retryCount: 2, maxRetry: 3, failedAt: '14:28:45' },
        { id: 'DLQ-003', type: 'notification', errorType: 'error', errorMessage: '推送服务连接失败', retryCount: 3, maxRetry: 3, failedAt: '14:25:12' },
        { id: 'DLQ-004', type: 'report', errorType: 'timeout', errorMessage: 'PDF生成超时', retryCount: 1, maxRetry: 3, failedAt: '14:20:33' },
        { id: 'DLQ-005', type: 'ai', errorType: 'error', errorMessage: '模型返回格式错误', retryCount: 2, maxRetry: 3, failedAt: '14:15:28' }
      ]
    })

    const filteredDLQItems = computed(() => {
      let items = deadLetterQueue.value.items
      if (dlqFilter.value !== 'all') {
        items = items.filter(i => i.type === dlqFilter.value)
      }
      if (dlqErrorFilter.value !== 'all') {
        items = items.filter(i => i.errorType === dlqErrorFilter.value)
      }
      return items
    })

    const totalPages = computed(() => Math.ceil(deadLetterQueue.value.total / 10))

    const getGaugePath = (value, max) => {
      const percentage = Math.min(value / max, 1)
      const angle = percentage * 180
      const rad = (angle - 180) * Math.PI / 180
      const x = 50 + 40 * Math.cos(rad)
      const y = 55 - 40 * Math.sin(rad)
      return `M 10 55 A 40 40 0 0 1 ${x} ${y}`
    }

    const getStreamStatusText = (status) => {
      const map = { good: '良好', warning: '警告', error: '异常' }
      return map[status] || status
    }

    const getWorkerStatusText = (status) => {
      const map = { busy: '忙碌', idle: '空闲', error: '异常' }
      return map[status] || status
    }

    const getTypeText = (type) => {
      const map = { interview: '面试任务', ai: 'AI处理', notification: '通知', report: '报告生成' }
      return map[type] || type
    }

    const getLatencyClass = (latency) => {
      if (latency < 50) return 'good'
      if (latency < 100) return 'normal'
      return 'warning'
    }

    const getPacketLossClass = (loss) => {
      if (loss < 0.5) return 'good'
      if (loss < 1) return 'normal'
      return 'warning'
    }

    const getJitterClass = (jitter) => {
      if (jitter < 10) return 'good'
      if (jitter < 20) return 'normal'
      return 'warning'
    }

    const getQualityClass = (score) => {
      if (score >= 90) return 'excellent'
      if (score >= 70) return 'good'
      if (score >= 50) return 'normal'
      return 'poor'
    }

    const getReconnectClass = (rate) => {
      if (rate < 3) return 'good'
      if (rate < 5) return 'normal'
      return 'warning'
    }

    const getUsageClass = (value) => {
      if (value >= 80) return 'danger'
      if (value >= 60) return 'warning'
      return 'normal'
    }

    const retryAll = () => {
      console.log('Retrying all DLQ items')
    }

    const clearAll = () => {
      console.log('Clearing all DLQ items')
    }

    const retryItem = (id) => {
      console.log('Retrying item:', id)
    }

    const viewDetail = (item) => {
      console.log('Viewing detail:', item)
    }

    const deleteItem = (id) => {
      console.log('Deleting item:', id)
    }

    return {
      lastUpdateTime,
      dlqFilter,
      dlqErrorFilter,
      dlqPage,
      roomsStats,
      maxConnections,
      timeLabels,
      wsPoints,
      wsLinePath,
      wsAreaPath,
      audioStreams,
      queueStats,
      taskTypes,
      workers,
      deadLetterQueue,
      filteredDLQItems,
      totalPages,
      getGaugePath,
      getStreamStatusText,
      getWorkerStatusText,
      getTypeText,
      getLatencyClass,
      getPacketLossClass,
      getJitterClass,
      getQualityClass,
      getReconnectClass,
      getUsageClass,
      retryAll,
      clearAll,
      retryItem,
      viewDetail,
      deleteItem
    }
  }
}
</script>

<style scoped>
.system-monitor-page {
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
  margin: 0 0 4px 0;
}

.header-info h3 i {
  color: var(--primary-color);
}

.header-info p {
  font-size: 13px;
  color: var(--text-secondary);
  margin: 0;
}

.update-time {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--text-light);
}

.monitor-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 24px;
}

.interview-rooms-section,
.task-queue-section {
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

.live-indicator {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #10b981;
  font-weight: 500;
}

.live-indicator .pulse {
  width: 8px;
  height: 8px;
  background: #10b981;
  border-radius: 50%;
  animation: pulse 1.5s infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.5; transform: scale(1.2); }
}

.rooms-overview {
  margin-bottom: 24px;
}

.overview-stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

.overview-stat {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  background: var(--glass-bg);
  border-radius: var(--radius-md);
}

.stat-icon {
  width: 40px;
  height: 40px;
  border-radius: var(--radius-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 16px;
}

.stat-icon.rooms { background: linear-gradient(135deg, #3b5998, #5a7ab8); }
.stat-icon.websocket { background: linear-gradient(135deg, #10b981, #34d399); }
.stat-icon.audio { background: linear-gradient(135deg, #f59e0b, #fbbf24); }
.stat-icon.bandwidth { background: linear-gradient(135deg, #8b5cf6, #a78bfa); }

.stat-content {
  display: flex;
  flex-direction: column;
}

.stat-value {
  font-size: 20px;
  font-weight: 700;
  color: var(--text-primary);
}

.stat-label {
  font-size: 12px;
  color: var(--text-secondary);
}

.websocket-section {
  margin-bottom: 24px;
  padding: 20px;
  background: var(--glass-bg);
  border-radius: var(--radius-md);
}

.subsection-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 500;
  color: var(--text-primary);
  margin-bottom: 16px;
}

.subsection-title i {
  color: var(--primary-color);
}

.connection-chart {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}

.chart-y-axis {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  font-size: 10px;
  color: var(--text-light);
  padding: 5px 0;
}

.chart-area {
  flex: 1;
}

.line-chart {
  width: 100%;
  height: 120px;
}

.chart-x-axis {
  display: flex;
  justify-content: space-between;
  margin-top: 8px;
  font-size: 10px;
  color: var(--text-light);
}

.connection-stats {
  display: flex;
  gap: 24px;
  padding-top: 16px;
  border-top: 1px solid var(--glass-border);
}

.conn-stat {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.conn-label {
  font-size: 11px;
  color: var(--text-secondary);
}

.conn-value {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}

.conn-value.good { color: #10b981; }
.conn-value.normal { color: #f59e0b; }
.conn-value.warning { color: #ef4444; }

.audio-quality-section {
  padding: 20px;
  background: var(--glass-bg);
  border-radius: var(--radius-md);
}

.quality-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}

.quality-card {
  padding: 14px;
  background: rgba(255, 255, 255, 0.5);
  border-radius: var(--radius-sm);
  border: 1px solid var(--glass-border);
}

.quality-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.stream-id {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-primary);
}

.stream-status {
  font-size: 10px;
  padding: 2px 8px;
  border-radius: 10px;
  font-weight: 500;
}

.stream-status.good { background: rgba(16, 185, 129, 0.15); color: #10b981; }
.stream-status.warning { background: rgba(245, 158, 11, 0.15); color: #f59e0b; }
.stream-status.error { background: rgba(239, 68, 68, 0.15); color: #ef4444; }

.quality-metrics {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px;
  margin-bottom: 12px;
}

.quality-metric {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.quality-metric .metric-label {
  font-size: 10px;
  color: var(--text-secondary);
}

.quality-metric .metric-value {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-primary);
}

.quality-metric .metric-value.good { color: #10b981; }
.quality-metric .metric-value.normal { color: #f59e0b; }
.quality-metric .metric-value.warning { color: #ef4444; }

.quality-bar {
  height: 4px;
  background: rgba(59, 89, 152, 0.1);
  border-radius: 2px;
  overflow: hidden;
  margin-bottom: 6px;
}

.quality-score {
  height: 100%;
  border-radius: 2px;
  transition: width 0.5s ease;
}

.quality-score.excellent { background: linear-gradient(90deg, #10b981, #34d399); }
.quality-score.good { background: linear-gradient(90deg, #34d399, #6ee7b7); }
.quality-score.normal { background: linear-gradient(90deg, #f59e0b, #fbbf24); }
.quality-score.poor { background: linear-gradient(90deg, #ef4444, #f87171); }

.quality-score-text {
  font-size: 10px;
  color: var(--text-secondary);
  text-align: center;
}

.queue-overview {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  margin-bottom: 24px;
}

.queue-stat-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px;
  background: var(--glass-bg);
  border-radius: var(--radius-md);
}

.queue-stat-icon {
  width: 44px;
  height: 44px;
  border-radius: var(--radius-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 18px;
}

.queue-stat-icon.pending { background: linear-gradient(135deg, #f59e0b, #fbbf24); }
.queue-stat-icon.processing { background: linear-gradient(135deg, #3b5998, #5a7ab8); }
.queue-stat-icon.completed { background: linear-gradient(135deg, #10b981, #34d399); }

.queue-stat-content {
  display: flex;
  flex-direction: column;
}

.queue-stat-value {
  font-size: 24px;
  font-weight: 700;
  color: var(--text-primary);
}

.queue-stat-label {
  font-size: 12px;
  color: var(--text-secondary);
}

.processing-speed-section {
  margin-bottom: 24px;
  padding: 20px;
  background: var(--glass-bg);
  border-radius: var(--radius-md);
}

.speed-stats {
  display: flex;
  justify-content: space-around;
}

.speed-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}

.speed-gauge {
  position: relative;
  width: 100px;
}

.gauge-chart {
  width: 100%;
  height: 60px;
}

.gauge-value {
  position: absolute;
  bottom: 0;
  left: 50%;
  transform: translateX(-50%);
  font-size: 14px;
  font-weight: 700;
  color: var(--text-primary);
}

.speed-label {
  font-size: 12px;
  color: var(--text-secondary);
}

.task-types-section {
  margin-bottom: 24px;
  padding: 20px;
  background: var(--glass-bg);
  border-radius: var(--radius-md);
}

.task-type-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.task-type-item {
  display: flex;
  align-items: center;
  gap: 12px;
}

.task-type-info {
  flex: 0 0 140px;
  display: flex;
  flex-direction: column;
}

.task-type-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-primary);
}

.task-type-count {
  font-size: 11px;
  color: var(--text-secondary);
}

.task-type-bar {
  flex: 1;
  height: 8px;
  background: rgba(59, 89, 152, 0.1);
  border-radius: 4px;
  overflow: hidden;
}

.task-type-fill {
  height: 100%;
  border-radius: 4px;
  transition: width 0.5s ease;
}

.task-type-percentage {
  width: 40px;
  font-size: 12px;
  font-weight: 600;
  color: var(--text-primary);
  text-align: right;
}

.workers-section {
  padding: 20px;
  background: var(--glass-bg);
  border-radius: var(--radius-md);
}

.workers-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}

.worker-card {
  padding: 14px;
  background: rgba(255, 255, 255, 0.5);
  border-radius: var(--radius-sm);
  border: 1px solid var(--glass-border);
}

.worker-card.busy { border-left: 3px solid #3b5998; }
.worker-card.idle { border-left: 3px solid #10b981; }
.worker-card.error { border-left: 3px solid #ef4444; }

.worker-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.worker-id {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-primary);
}

.worker-status {
  font-size: 10px;
  padding: 2px 8px;
  border-radius: 10px;
  font-weight: 500;
}

.worker-status.busy { background: rgba(59, 89, 152, 0.15); color: var(--primary-color); }
.worker-status.idle { background: rgba(16, 185, 129, 0.15); color: #10b981; }
.worker-status.error { background: rgba(239, 68, 68, 0.15); color: #ef4444; }

.worker-metrics {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 6px;
}

.worker-metric {
  display: flex;
  flex-direction: column;
}

.wm-label {
  font-size: 10px;
  color: var(--text-secondary);
}

.wm-value {
  font-size: 11px;
  font-weight: 500;
  color: var(--text-primary);
}

.wm-value.normal { color: #10b981; }
.wm-value.warning { color: #f59e0b; }
.wm-value.danger { color: #ef4444; }

.dead-letter-section {
  padding: 24px;
}

.dlq-stats {
  display: flex;
  align-items: center;
  gap: 12px;
}

.dlq-count {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--text-secondary);
  padding: 6px 12px;
  background: var(--glass-bg);
  border-radius: var(--radius-sm);
}

.retry-all-btn,
.clear-all-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border: none;
  border-radius: var(--radius-sm);
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
}

.retry-all-btn {
  background: rgba(16, 185, 129, 0.15);
  color: #10b981;
}

.retry-all-btn:hover:not(:disabled) {
  background: rgba(16, 185, 129, 0.25);
}

.clear-all-btn {
  background: rgba(239, 68, 68, 0.15);
  color: #ef4444;
}

.clear-all-btn:hover:not(:disabled) {
  background: rgba(239, 68, 68, 0.25);
}

.retry-all-btn:disabled,
.clear-all-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.dlq-filters {
  display: flex;
  gap: 12px;
  margin: 16px 0;
}

.glass-select {
  padding: 8px 12px;
  background: var(--glass-bg);
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-sm);
  font-size: 13px;
  color: var(--text-primary);
  font-family: inherit;
}

.dlq-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-height: 400px;
  overflow-y: auto;
}

.dlq-item {
  padding: 16px;
  background: var(--glass-bg);
  border-radius: var(--radius-md);
  border-left: 3px solid #ef4444;
}

.dlq-item-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.dlq-item-info {
  display: flex;
  align-items: center;
  gap: 10px;
}

.dlq-item-type {
  font-size: 10px;
  padding: 2px 8px;
  border-radius: 10px;
  font-weight: 500;
}

.dlq-item-type.interview { background: rgba(59, 89, 152, 0.15); color: var(--primary-color); }
.dlq-item-type.ai { background: rgba(16, 185, 129, 0.15); color: #10b981; }
.dlq-item-type.notification { background: rgba(245, 158, 11, 0.15); color: #f59e0b; }
.dlq-item-type.report { background: rgba(139, 92, 246, 0.15); color: #8b5cf6; }

.dlq-item-id {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-primary);
}

.dlq-item-time {
  font-size: 11px;
  color: var(--text-light);
}

.dlq-item-content {
  margin-bottom: 12px;
}

.dlq-error-info {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.dlq-error-info i {
  color: #ef4444;
  font-size: 12px;
}

.error-type {
  font-size: 11px;
  padding: 2px 6px;
  background: rgba(239, 68, 68, 0.15);
  color: #ef4444;
  border-radius: 4px;
}

.error-message {
  font-size: 12px;
  color: var(--text-secondary);
}

.dlq-retry-info {
  font-size: 11px;
  color: var(--text-light);
}

.dlq-item-actions {
  display: flex;
  gap: 8px;
}

.action-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  border: none;
  border-radius: var(--radius-sm);
  font-size: 11px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
}

.action-btn.retry {
  background: rgba(16, 185, 129, 0.15);
  color: #10b981;
}

.action-btn.retry:hover {
  background: rgba(16, 185, 129, 0.25);
}

.action-btn.view {
  background: rgba(59, 89, 152, 0.15);
  color: var(--primary-color);
}

.action-btn.view:hover {
  background: rgba(59, 89, 152, 0.25);
}

.action-btn.delete {
  background: rgba(239, 68, 68, 0.15);
  color: #ef4444;
}

.action-btn.delete:hover {
  background: rgba(239, 68, 68, 0.25);
}

.dlq-pagination {
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
  transition: all 0.2s ease;
}

.page-btn:hover:not(:disabled) {
  background: var(--primary-color);
  color: white;
  border-color: var(--primary-color);
}

.page-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.page-info {
  font-size: 13px;
  color: var(--text-secondary);
}
</style>
