<template>
  <div class="rag-management-page">
    <div class="page-header glass-card">
      <div class="header-info">
        <h3>
          <i class="fas fa-database"></i>
          RAG语料管理
        </h3>
        <p>文件上传、语料解析、向量化与检索命中率分析</p>
      </div>
      <div class="header-stats">
        <span class="stat-item">
          <i class="fas fa-file-alt"></i>
          {{ stats.totalFiles }} 文件
        </span>
        <span class="stat-item">
          <i class="fas fa-puzzle-piece"></i>
          {{ stats.totalChunks }} 切片
        </span>
        <span class="stat-item">
          <i class="fas fa-vector-square"></i>
          {{ stats.totalVectors }} 向量
        </span>
      </div>
    </div>

    <div class="main-grid">
      <div class="upload-section glass-card">
        <div class="section-header">
          <h4>
            <i class="fas fa-cloud-upload-alt"></i>
            文件上传
          </h4>
        </div>

        <div 
          class="upload-area"
          :class="{ dragging: isDragging }"
          @dragover.prevent="isDragging = true"
          @dragleave.prevent="isDragging = false"
          @drop.prevent="handleDrop"
          @click="triggerFileInput"
        >
          <input 
            type="file" 
            ref="fileInput" 
            multiple 
            accept=".pdf,.docx,.doc,.txt,.md"
            @change="handleFileSelect"
            hidden
          >
          <div class="upload-icon">
            <i class="fas fa-cloud-upload-alt"></i>
          </div>
          <div class="upload-text">
            <p class="main-text">拖拽文件到此处或点击上传</p>
            <p class="sub-text">支持 PDF、DOCX、DOC、TXT、MD 格式</p>
          </div>
          <div class="upload-formats">
            <span class="format-badge pdf"><i class="fas fa-file-pdf"></i> PDF</span>
            <span class="format-badge docx"><i class="fas fa-file-word"></i> DOCX</span>
            <span class="format-badge txt"><i class="fas fa-file-alt"></i> TXT</span>
            <span class="format-badge md"><i class="fab fa-markdown"></i> MD</span>
          </div>
        </div>

        <div class="upload-queue" v-if="uploadQueue.length > 0">
          <div class="queue-header">
            <span class="queue-title">上传队列</span>
            <button class="clear-queue-btn" @click="clearQueue">清空</button>
          </div>
          <div class="queue-list">
            <div v-for="file in uploadQueue" :key="file.id" class="queue-item">
              <div class="file-icon" :class="getFileType(file.name)">
                <i :class="getFileIcon(file.name)"></i>
              </div>
              <div class="file-info">
                <span class="file-name">{{ file.name }}</span>
                <span class="file-size">{{ formatSize(file.size) }}</span>
              </div>
              <div class="file-progress">
                <div class="progress-bar">
                  <div class="progress-fill" :style="{ width: file.progress + '%' }" :class="file.status"></div>
                </div>
                <span class="progress-text" :class="file.status">{{ file.progress }}%</span>
              </div>
              <div class="file-status" :class="file.status">
                <i :class="getStatusIcon(file.status)"></i>
              </div>
              <button class="remove-btn" @click="removeFromQueue(file.id)" v-if="file.status !== 'uploading'">
                <i class="fas fa-times"></i>
              </button>
            </div>
          </div>
        </div>
      </div>

      <div class="parsing-section glass-card">
        <div class="section-header">
          <h4>
            <i class="fas fa-cogs"></i>
            语料解析状态
          </h4>
          <div class="parsing-stats">
            <span class="parsing-stat">
              <i class="fas fa-check-circle"></i>
              {{ parsingStats.completed }} 已完成
            </span>
            <span class="parsing-stat processing">
              <i class="fas fa-spinner fa-spin"></i>
              {{ parsingStats.processing }} 处理中
            </span>
            <span class="parsing-stat pending">
              <i class="fas fa-clock"></i>
              {{ parsingStats.pending }} 等待中
            </span>
          </div>
        </div>

        <div class="parsing-list">
          <div v-for="doc in parsingDocs" :key="doc.id" class="parsing-item">
            <div class="parsing-main">
              <div class="doc-icon" :class="doc.type">
                <i :class="getDocTypeIcon(doc.type)"></i>
              </div>
              <div class="doc-info">
                <div class="doc-header">
                  <span class="doc-name">{{ doc.name }}</span>
                  <span class="doc-status" :class="doc.status">
                    <i :class="getParsingStatusIcon(doc.status)"></i>
                    {{ getParsingStatusText(doc.status) }}
                  </span>
                </div>
                <div class="doc-progress" v-if="doc.status === 'parsing'">
                  <div class="progress-bar">
                    <div class="progress-fill" :style="{ width: doc.progress + '%' }"></div>
                  </div>
                  <span class="progress-text">{{ doc.progress }}% - {{ doc.currentStep }}</span>
                </div>
                <div class="doc-meta" v-else>
                  <span class="meta-item"><i class="fas fa-puzzle-piece"></i> {{ doc.chunks }} 切片</span>
                  <span class="meta-item"><i class="fas fa-clock"></i> {{ doc.parseTime }}s</span>
                  <span class="meta-item"><i class="fas fa-text-width"></i> {{ doc.charCount }} 字符</span>
                </div>
              </div>
            </div>
            <div class="parsing-actions">
              <button class="action-btn view" @click="viewParsed(doc)" v-if="doc.status === 'completed'">
                <i class="fas fa-eye"></i>
              </button>
              <button class="action-btn retry" @click="retryParsing(doc)" v-if="doc.status === 'failed'">
                <i class="fas fa-redo"></i>
              </button>
              <button class="action-btn delete" @click="deleteDoc(doc)">
                <i class="fas fa-trash"></i>
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="vectorization-section glass-card">
      <div class="section-header">
        <h4>
          <i class="fas fa-vector-square"></i>
          向量化进度
        </h4>
        <div class="vector-actions">
          <button class="vector-btn" @click="startVectorization" :disabled="pendingVectorization === 0">
            <i class="fas fa-play"></i>
            开始向量化
          </button>
        </div>
      </div>

      <div class="vector-overview">
        <div class="vector-stat-card">
          <div class="vector-stat-icon completed">
            <i class="fas fa-check"></i>
          </div>
          <div class="vector-stat-content">
            <span class="vector-stat-value">{{ vectorStats.completed }}</span>
            <span class="vector-stat-label">已完成</span>
          </div>
        </div>
        <div class="vector-stat-card">
          <div class="vector-stat-icon processing">
            <i class="fas fa-spinner fa-spin"></i>
          </div>
          <div class="vector-stat-content">
            <span class="vector-stat-value">{{ vectorStats.processing }}</span>
            <span class="vector-stat-label">处理中</span>
          </div>
        </div>
        <div class="vector-stat-card">
          <div class="vector-stat-icon pending">
            <i class="fas fa-clock"></i>
          </div>
          <div class="vector-stat-content">
            <span class="vector-stat-value">{{ pendingVectorization }}</span>
            <span class="vector-stat-label">待处理</span>
          </div>
        </div>
        <div class="vector-stat-card">
          <div class="vector-stat-icon total">
            <i class="fas fa-database"></i>
          </div>
          <div class="vector-stat-content">
            <span class="vector-stat-value">{{ vectorStats.totalVectors }}</span>
            <span class="vector-stat-label">总向量数</span>
          </div>
        </div>
      </div>

      <div class="vector-progress-section">
        <div class="progress-header">
          <span class="progress-title">当前处理进度</span>
          <span class="progress-detail" v-if="currentVectorTask">
            {{ currentVectorTask.name }} - {{ currentVectorTask.processed }}/{{ currentVectorTask.total }} 切片
          </span>
        </div>
        <div class="main-progress-bar" v-if="currentVectorTask">
          <div class="progress-fill" :style="{ width: (currentVectorTask.processed / currentVectorTask.total * 100) + '%' }"></div>
        </div>
        <div class="no-task" v-else>
          <i class="fas fa-check-circle"></i>
          暂无进行中的向量化任务
        </div>
      </div>

      <div class="vector-list">
        <div class="vector-list-header">
          <span class="col name">文档名称</span>
          <span class="col chunks">切片数</span>
          <span class="col model">嵌入模型</span>
          <span class="col status">状态</span>
          <span class="col actions">操作</span>
        </div>
        <div class="vector-list-body">
          <div v-for="item in vectorItems" :key="item.id" class="vector-item">
            <div class="col name">
              <div class="item-icon" :class="item.type">
                <i :class="getDocTypeIcon(item.type)"></i>
              </div>
              <span class="item-name">{{ item.name }}</span>
            </div>
            <div class="col chunks">{{ item.chunks }}</div>
            <div class="col model">
              <span class="model-badge">{{ item.embedModel }}</span>
            </div>
            <div class="col status">
              <span class="status-badge" :class="item.status">
                <i :class="getVectorStatusIcon(item.status)"></i>
                {{ getVectorStatusText(item.status) }}
              </span>
            </div>
            <div class="col actions">
              <button class="vec-action-btn" @click="viewVectors(item)" v-if="item.status === 'completed'">
                <i class="fas fa-eye"></i>
              </button>
              <button class="vec-action-btn" @click="reVectorize(item)" v-if="item.status === 'completed'">
                <i class="fas fa-sync"></i>
              </button>
              <button class="vec-action-btn delete" @click="deleteVector(item)">
                <i class="fas fa-trash"></i>
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="hitrate-section glass-card">
      <div class="section-header">
        <h4>
          <i class="fas fa-bullseye"></i>
          检索命中率分析
        </h4>
        <div class="hitrate-actions">
          <select v-model="hitratePeriod" class="period-select">
            <option value="day">今日</option>
            <option value="week">本周</option>
            <option value="month">本月</option>
          </select>
          <button class="refresh-btn" @click="refreshHitrate">
            <i class="fas fa-sync-alt"></i>
          </button>
        </div>
      </div>

      <div class="hitrate-overview">
        <div class="hitrate-card">
          <div class="hitrate-value" :class="getHitrateClass(hitrateStats.avgHitrate)">
            {{ hitrateStats.avgHitrate }}%
          </div>
          <div class="hitrate-label">平均命中率</div>
          <div class="hitrate-trend" :class="hitrateStats.trend > 0 ? 'up' : 'down'">
            <i :class="hitrateStats.trend > 0 ? 'fas fa-arrow-up' : 'fas fa-arrow-down'"></i>
            {{ Math.abs(hitrateStats.trend) }}%
          </div>
        </div>
        <div class="hitrate-card">
          <div class="hitrate-value">{{ hitrateStats.totalQueries }}</div>
          <div class="hitrate-label">总查询次数</div>
        </div>
        <div class="hitrate-card">
          <div class="hitrate-value">{{ hitrateStats.avgLatency }}ms</div>
          <div class="hitrate-label">平均延迟</div>
        </div>
        <div class="hitrate-card">
          <div class="hitrate-value">{{ hitrateStats.topKAccuracy }}%</div>
          <div class="hitrate-label">Top-K准确率</div>
        </div>
      </div>

      <div class="hitrate-charts">
        <div class="chart-card">
          <div class="chart-title">
            <i class="fas fa-chart-line"></i>
            命中率趋势
          </div>
          <div class="chart-container">
            <svg viewBox="0 0 400 150" class="hitrate-chart">
              <defs>
                <linearGradient id="hitrateGradient" x1="0%" y1="0%" x2="0%" y2="100%">
                  <stop offset="0%" stop-color="rgba(16, 185, 129, 0.3)" />
                  <stop offset="100%" stop-color="rgba(16, 185, 129, 0.02)" />
                </linearGradient>
              </defs>
              <path :d="hitrateAreaPath" fill="url(#hitrateGradient)" />
              <path :d="hitrateLinePath" fill="none" stroke="#10b981" stroke-width="2" />
              <circle v-for="(point, index) in hitratePoints" :key="index" 
                :cx="point.x" :cy="point.y" r="3" fill="#10b981" />
            </svg>
          </div>
        </div>
        <div class="chart-card">
          <div class="chart-title">
            <i class="fas fa-chart-bar"></i>
            查询分布
          </div>
          <div class="bar-chart">
            <div v-for="(bar, index) in queryDistribution" :key="index" class="bar-item">
              <div class="bar-wrapper">
                <div class="bar" :style="{ height: bar.height + '%', background: bar.color }"></div>
              </div>
              <span class="bar-label">{{ bar.label }}</span>
              <span class="bar-value">{{ bar.value }}</span>
            </div>
          </div>
        </div>
      </div>

      <div class="hitrate-details">
        <div class="detail-section">
          <div class="detail-title">
            <i class="fas fa-trophy"></i>
            高频查询 TOP5
          </div>
          <div class="top-queries">
            <div v-for="(query, index) in topQueries" :key="index" class="query-item">
              <span class="query-rank" :class="{ top: index < 3 }">{{ index + 1 }}</span>
              <span class="query-text">{{ query.text }}</span>
              <span class="query-count">{{ query.count }}次</span>
              <span class="query-rate" :class="getHitrateClass(query.hitrate)">{{ query.hitrate }}%</span>
            </div>
          </div>
        </div>
        <div class="detail-section">
          <div class="detail-title">
            <i class="fas fa-exclamation-triangle"></i>
            低命中查询
          </div>
          <div class="low-hitrate-queries">
            <div v-for="(query, index) in lowHitrateQueries" :key="index" class="query-item warning">
              <span class="query-text">{{ query.text }}</span>
              <span class="query-rate low">{{ query.hitrate }}%</span>
              <button class="improve-btn" @click="improveQuery(query)">
                <i class="fas fa-magic"></i>
                优化
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { ref, computed } from 'vue'

export default {
  name: 'RagManagement',
  setup() {
    const fileInput = ref(null)
    const isDragging = ref(false)
    const hitratePeriod = ref('week')

    const stats = ref({
      totalFiles: 156,
      totalChunks: 2847,
      totalVectors: 2847
    })

    const uploadQueue = ref([
      { id: 1, name: '面试技巧指南.pdf', size: 2456000, progress: 100, status: 'completed' },
      { id: 2, name: '技术面试题库.docx', size: 1230000, progress: 67, status: 'uploading' },
      { id: 3, name: '产品经理面试.md', size: 89000, progress: 0, status: 'pending' }
    ])

    const parsingStats = ref({
      completed: 142,
      processing: 3,
      pending: 11
    })

    const parsingDocs = ref([
      { id: 1, name: '前端开发面试指南.pdf', type: 'pdf', status: 'completed', chunks: 45, parseTime: 12.5, charCount: 45000 },
      { id: 2, name: 'Java后端技术栈.docx', type: 'docx', status: 'parsing', progress: 65, currentStep: '提取文本内容...' },
      { id: 3, name: '算法面试宝典.pdf', type: 'pdf', status: 'completed', chunks: 78, parseTime: 18.2, charCount: 78000 },
      { id: 4, name: '产品经理面试题.pdf', type: 'pdf', status: 'failed', chunks: 0, parseTime: 0, charCount: 0 },
      { id: 5, name: '系统设计面试.md', type: 'md', status: 'pending', chunks: 0, parseTime: 0, charCount: 0 }
    ])

    const vectorStats = ref({
      completed: 138,
      processing: 2,
      totalVectors: 2847
    })

    const currentVectorTask = ref({
      name: 'Java后端技术栈.docx',
      processed: 23,
      total: 45
    })

    const vectorItems = ref([
      { id: 1, name: '前端开发面试指南.pdf', type: 'pdf', chunks: 45, embedModel: 'text-embedding-3', status: 'completed' },
      { id: 2, name: 'Java后端技术栈.docx', type: 'docx', chunks: 45, embedModel: 'text-embedding-3', status: 'processing' },
      { id: 3, name: '算法面试宝典.pdf', type: 'pdf', chunks: 78, embedModel: 'text-embedding-3', status: 'completed' },
      { id: 4, name: '系统设计面试.md', type: 'md', chunks: 32, embedModel: 'text-embedding-3', status: 'pending' }
    ])

    const hitrateStats = ref({
      avgHitrate: 87.5,
      trend: 2.3,
      totalQueries: 15680,
      avgLatency: 45,
      topKAccuracy: 92.3
    })

    const hitrateData = ref([82, 85, 78, 88, 91, 86, 89, 92, 87, 90, 88, 91])

    const hitratePoints = computed(() => {
      return hitrateData.value.map((val, index) => ({
        x: 20 + (index * 30),
        y: 130 - (val / 100) * 110
      }))
    })

    const hitrateLinePath = computed(() => {
      return hitratePoints.value.map((p, i) => `${i === 0 ? 'M' : 'L'} ${p.x} ${p.y}`).join(' ')
    })

    const hitrateAreaPath = computed(() => {
      const linePath = hitrateLinePath.value
      return `${linePath} L 350 130 L 20 130 Z`
    })

    const queryDistribution = ref([
      { label: '技术', value: 45, height: 90, color: '#3b5998' },
      { label: '行为', value: 28, height: 56, color: '#10b981' },
      { label: '项目', value: 18, height: 36, color: '#f59e0b' },
      { label: '情景', value: 9, height: 18, color: '#8b5cf6' }
    ])

    const topQueries = ref([
      { text: 'Vue3响应式原理', count: 1256, hitrate: 95 },
      { text: 'React Hooks使用', count: 987, hitrate: 92 },
      { text: '微服务架构设计', count: 845, hitrate: 88 },
      { text: '数据库优化', count: 723, hitrate: 91 },
      { text: '分布式系统', count: 654, hitrate: 86 }
    ])

    const lowHitrateQueries = ref([
      { text: '最新前端框架对比', hitrate: 45 },
      { text: 'AI面试官特点', hitrate: 52 },
      { text: '面试评分标准', hitrate: 58 }
    ])

    const pendingVectorization = computed(() => {
      return vectorItems.value.filter(v => v.status === 'pending').length
    })

    const triggerFileInput = () => {
      fileInput.value.click()
    }

    const handleDrop = (e) => {
      isDragging.value = false
      const files = e.dataTransfer.files
      addFilesToQueue(files)
    }

    const handleFileSelect = (e) => {
      const files = e.target.files
      addFilesToQueue(files)
    }

    const addFilesToQueue = (files) => {
      for (let file of files) {
        uploadQueue.value.push({
          id: Date.now() + Math.random(),
          name: file.name,
          size: file.size,
          progress: 0,
          status: 'pending'
        })
      }
    }

    const clearQueue = () => {
      uploadQueue.value = []
    }

    const removeFromQueue = (id) => {
      const index = uploadQueue.value.findIndex(f => f.id === id)
      if (index > -1) {
        uploadQueue.value.splice(index, 1)
      }
    }

    const formatSize = (bytes) => {
      if (bytes < 1024) return bytes + ' B'
      if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
      return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
    }

    const getFileType = (filename) => {
      const ext = filename.split('.').pop().toLowerCase()
      return ext
    }

    const getFileIcon = (filename) => {
      const ext = filename.split('.').pop().toLowerCase()
      const icons = {
        pdf: 'fas fa-file-pdf',
        docx: 'fas fa-file-word',
        doc: 'fas fa-file-word',
        txt: 'fas fa-file-alt',
        md: 'fab fa-markdown'
      }
      return icons[ext] || 'fas fa-file'
    }

    const getDocTypeIcon = (type) => {
      const icons = {
        pdf: 'fas fa-file-pdf',
        docx: 'fas fa-file-word',
        md: 'fab fa-markdown',
        txt: 'fas fa-file-alt'
      }
      return icons[type] || 'fas fa-file'
    }

    const getStatusIcon = (status) => {
      const icons = {
        pending: 'fas fa-clock',
        uploading: 'fas fa-spinner fa-spin',
        completed: 'fas fa-check',
        failed: 'fas fa-times'
      }
      return icons[status] || 'fas fa-question'
    }

    const getParsingStatusIcon = (status) => {
      const icons = {
        pending: 'fas fa-clock',
        parsing: 'fas fa-spinner fa-spin',
        completed: 'fas fa-check',
        failed: 'fas fa-times'
      }
      return icons[status] || 'fas fa-question'
    }

    const getParsingStatusText = (status) => {
      const texts = {
        pending: '等待解析',
        parsing: '解析中',
        completed: '解析完成',
        failed: '解析失败'
      }
      return texts[status] || status
    }

    const getVectorStatusIcon = (status) => {
      const icons = {
        pending: 'fas fa-clock',
        processing: 'fas fa-spinner fa-spin',
        completed: 'fas fa-check',
        failed: 'fas fa-times'
      }
      return icons[status] || 'fas fa-question'
    }

    const getVectorStatusText = (status) => {
      const texts = {
        pending: '待处理',
        processing: '处理中',
        completed: '已完成',
        failed: '失败'
      }
      return texts[status] || status
    }

    const getHitrateClass = (rate) => {
      if (rate >= 85) return 'high'
      if (rate >= 70) return 'medium'
      return 'low'
    }

    const viewParsed = (doc) => {
      console.log('View parsed:', doc)
    }

    const retryParsing = (doc) => {
      console.log('Retry parsing:', doc)
    }

    const deleteDoc = (doc) => {
      console.log('Delete doc:', doc)
    }

    const startVectorization = () => {
      console.log('Starting vectorization')
    }

    const viewVectors = (item) => {
      console.log('View vectors:', item)
    }

    const reVectorize = (item) => {
      console.log('Re-vectorize:', item)
    }

    const deleteVector = (item) => {
      console.log('Delete vector:', item)
    }

    const refreshHitrate = () => {
      console.log('Refreshing hitrate')
    }

    const improveQuery = (query) => {
      console.log('Improve query:', query)
    }

    return {
      fileInput,
      isDragging,
      hitratePeriod,
      stats,
      uploadQueue,
      parsingStats,
      parsingDocs,
      vectorStats,
      currentVectorTask,
      vectorItems,
      hitrateStats,
      hitrateData,
      hitratePoints,
      hitrateLinePath,
      hitrateAreaPath,
      queryDistribution,
      topQueries,
      lowHitrateQueries,
      pendingVectorization,
      triggerFileInput,
      handleDrop,
      handleFileSelect,
      clearQueue,
      removeFromQueue,
      formatSize,
      getFileType,
      getFileIcon,
      getDocTypeIcon,
      getStatusIcon,
      getParsingStatusIcon,
      getParsingStatusText,
      getVectorStatusIcon,
      getVectorStatusText,
      getHitrateClass,
      viewParsed,
      retryParsing,
      deleteDoc,
      startVectorization,
      viewVectors,
      reVectorize,
      deleteVector,
      refreshHitrate,
      improveQuery
    }
  }
}
</script>

<style scoped>
.rag-management-page {
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

.header-stats {
  display: flex;
  gap: 20px;
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  background: var(--glass-bg);
  border-radius: var(--radius-md);
  font-size: 13px;
  color: var(--text-secondary);
}

.stat-item i {
  color: var(--primary-color);
}

.main-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 24px;
}

.upload-section,
.parsing-section {
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

.upload-area {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 20px;
  border: 2px dashed var(--glass-border);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: all 0.3s ease;
}

.upload-area:hover,
.upload-area.dragging {
  border-color: var(--primary-color);
  background: rgba(59, 89, 152, 0.05);
}

.upload-icon {
  font-size: 48px;
  color: var(--primary-color);
  margin-bottom: 16px;
}

.upload-text {
  text-align: center;
  margin-bottom: 16px;
}

.main-text {
  font-size: 15px;
  font-weight: 500;
  color: var(--text-primary);
  margin: 0 0 4px;
}

.sub-text {
  font-size: 12px;
  color: var(--text-secondary);
  margin: 0;
}

.upload-formats {
  display: flex;
  gap: 8px;
}

.format-badge {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border-radius: 12px;
  font-size: 11px;
  font-weight: 500;
}

.format-badge.pdf { background: rgba(239, 68, 68, 0.15); color: #ef4444; }
.format-badge.docx { background: rgba(59, 130, 246, 0.15); color: #3b82f6; }
.format-badge.txt { background: rgba(107, 114, 128, 0.15); color: #6b7280; }
.format-badge.md { background: rgba(16, 185, 129, 0.15); color: #10b981; }

.upload-queue {
  margin-top: 20px;
}

.queue-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.queue-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-primary);
}

.clear-queue-btn {
  padding: 4px 10px;
  background: transparent;
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-sm);
  font-size: 11px;
  color: var(--text-secondary);
  cursor: pointer;
}

.queue-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 200px;
  overflow-y: auto;
}

.queue-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  background: var(--glass-bg);
  border-radius: var(--radius-md);
}

.file-icon {
  width: 36px;
  height: 36px;
  border-radius: var(--radius-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
}

.file-icon.pdf { background: rgba(239, 68, 68, 0.15); color: #ef4444; }
.file-icon.docx, .file-icon.doc { background: rgba(59, 130, 246, 0.15); color: #3b82f6; }
.file-icon.txt { background: rgba(107, 114, 128, 0.15); color: #6b7280; }
.file-icon.md { background: rgba(16, 185, 129, 0.15); color: #10b981; }

.file-info {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.file-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-primary);
}

.file-size {
  font-size: 11px;
  color: var(--text-secondary);
}

.file-progress {
  width: 120px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.progress-bar {
  height: 4px;
  background: rgba(59, 89, 152, 0.1);
  border-radius: 2px;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  border-radius: 2px;
  transition: width 0.3s ease;
  background: linear-gradient(90deg, var(--primary-color), var(--primary-light));
}

.progress-fill.completed { background: linear-gradient(90deg, #10b981, #34d399); }
.progress-fill.failed { background: linear-gradient(90deg, #ef4444, #f87171); }

.progress-text {
  font-size: 11px;
  color: var(--text-secondary);
  text-align: right;
}

.progress-text.completed { color: #10b981; }
.progress-text.failed { color: #ef4444; }

.file-status {
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
}

.file-status.completed { color: #10b981; }
.file-status.uploading { color: var(--primary-color); }
.file-status.pending { color: var(--text-light); }
.file-status.failed { color: #ef4444; }

.remove-btn {
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: transparent;
  border: none;
  color: var(--text-light);
  cursor: pointer;
}

.remove-btn:hover {
  color: #ef4444;
}

.parsing-stats {
  display: flex;
  gap: 16px;
}

.parsing-stat {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #10b981;
}

.parsing-stat.processing { color: var(--primary-color); }
.parsing-stat.pending { color: var(--text-secondary); }

.parsing-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-height: 350px;
  overflow-y: auto;
}

.parsing-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px;
  background: var(--glass-bg);
  border-radius: var(--radius-md);
}

.parsing-main {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: 1;
}

.doc-icon {
  width: 40px;
  height: 40px;
  border-radius: var(--radius-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
}

.doc-icon.pdf { background: rgba(239, 68, 68, 0.15); color: #ef4444; }
.doc-icon.docx { background: rgba(59, 130, 246, 0.15); color: #3b82f6; }
.doc-icon.md { background: rgba(16, 185, 129, 0.15); color: #10b981; }
.doc-icon.txt { background: rgba(107, 114, 128, 0.15); color: #6b7280; }

.doc-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.doc-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.doc-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-primary);
}

.doc-status {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  font-weight: 500;
}

.doc-status.completed { color: #10b981; }
.doc-status.parsing { color: var(--primary-color); }
.doc-status.pending { color: var(--text-secondary); }
.doc-status.failed { color: #ef4444; }

.doc-progress {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.doc-progress .progress-bar {
  width: 200px;
}

.progress-text {
  font-size: 11px;
  color: var(--text-secondary);
}

.doc-meta {
  display: flex;
  gap: 12px;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: var(--text-secondary);
}

.meta-item i {
  font-size: 10px;
}

.parsing-actions {
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
}

.action-btn.view {
  background: rgba(59, 89, 152, 0.15);
  color: var(--primary-color);
}

.action-btn.retry {
  background: rgba(245, 158, 11, 0.15);
  color: #f59e0b;
}

.action-btn.delete {
  background: rgba(239, 68, 68, 0.15);
  color: #ef4444;
}

.vectorization-section {
  padding: 24px;
}

.vector-actions {
  display: flex;
  gap: 12px;
}

.vector-btn {
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

.vector-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.vector-overview {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 24px;
}

.vector-stat-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px;
  background: var(--glass-bg);
  border-radius: var(--radius-md);
}

.vector-stat-icon {
  width: 40px;
  height: 40px;
  border-radius: var(--radius-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 16px;
}

.vector-stat-icon.completed { background: linear-gradient(135deg, #10b981, #34d399); }
.vector-stat-icon.processing { background: linear-gradient(135deg, #3b5998, #5a7ab8); }
.vector-stat-icon.pending { background: linear-gradient(135deg, #f59e0b, #fbbf24); }
.vector-stat-icon.total { background: linear-gradient(135deg, #8b5cf6, #a78bfa); }

.vector-stat-content {
  display: flex;
  flex-direction: column;
}

.vector-stat-value {
  font-size: 20px;
  font-weight: 700;
  color: var(--text-primary);
}

.vector-stat-label {
  font-size: 12px;
  color: var(--text-secondary);
}

.vector-progress-section {
  padding: 16px;
  background: var(--glass-bg);
  border-radius: var(--radius-md);
  margin-bottom: 20px;
}

.progress-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.progress-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-primary);
}

.progress-detail {
  font-size: 12px;
  color: var(--text-secondary);
}

.main-progress-bar {
  height: 8px;
  background: rgba(59, 89, 152, 0.1);
  border-radius: 4px;
  overflow: hidden;
}

.main-progress-bar .progress-fill {
  height: 100%;
  background: linear-gradient(90deg, var(--primary-color), var(--primary-light));
}

.no-task {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #10b981;
}

.vector-list {
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-md);
  overflow: hidden;
}

.vector-list-header {
  display: flex;
  padding: 12px 16px;
  background: var(--glass-bg);
  font-size: 12px;
  font-weight: 500;
  color: var(--text-secondary);
}

.vector-list-body {
  display: flex;
  flex-direction: column;
}

.vector-item {
  display: flex;
  align-items: center;
  padding: 14px 16px;
  border-bottom: 1px solid var(--glass-border);
}

.vector-item:last-child {
  border-bottom: none;
}

.col {
  display: flex;
  align-items: center;
}

.col.name { flex: 1; gap: 10px; }
.col.chunks { width: 80px; }
.col.model { width: 140px; }
.col.status { width: 100px; }
.col.actions { width: 100px; justify-content: flex-end; gap: 6px; }

.item-icon {
  width: 32px;
  height: 32px;
  border-radius: var(--radius-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
}

.item-icon.pdf { background: rgba(239, 68, 68, 0.15); color: #ef4444; }
.item-icon.docx { background: rgba(59, 130, 246, 0.15); color: #3b82f6; }
.item-icon.md { background: rgba(16, 185, 129, 0.15); color: #10b981; }

.item-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-primary);
}

.model-badge {
  padding: 4px 10px;
  background: rgba(59, 89, 152, 0.15);
  border-radius: 12px;
  font-size: 11px;
  color: var(--primary-color);
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

.status-badge.completed {
  background: rgba(16, 185, 129, 0.15);
  color: #10b981;
}

.status-badge.processing {
  background: rgba(59, 89, 152, 0.15);
  color: var(--primary-color);
}

.status-badge.pending {
  background: rgba(245, 158, 11, 0.15);
  color: #f59e0b;
}

.vec-action-btn {
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
}

.vec-action-btn:hover {
  background: rgba(59, 89, 152, 0.2);
  color: var(--primary-color);
}

.vec-action-btn.delete:hover {
  background: rgba(239, 68, 68, 0.2);
  color: #ef4444;
}

.hitrate-section {
  padding: 24px;
}

.hitrate-actions {
  display: flex;
  gap: 10px;
}

.period-select {
  padding: 6px 12px;
  background: var(--glass-bg);
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-sm);
  font-size: 12px;
  color: var(--text-primary);
  font-family: inherit;
}

.refresh-btn {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--glass-bg);
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-sm);
  color: var(--text-secondary);
  cursor: pointer;
}

.refresh-btn:hover {
  color: var(--primary-color);
}

.hitrate-overview {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 24px;
}

.hitrate-card {
  padding: 20px;
  background: var(--glass-bg);
  border-radius: var(--radius-md);
  text-align: center;
}

.hitrate-value {
  font-size: 28px;
  font-weight: 700;
  color: var(--text-primary);
}

.hitrate-value.high { color: #10b981; }
.hitrate-value.medium { color: #f59e0b; }
.hitrate-value.low { color: #ef4444; }

.hitrate-label {
  font-size: 12px;
  color: var(--text-secondary);
  margin-top: 4px;
}

.hitrate-trend {
  font-size: 12px;
  margin-top: 8px;
}

.hitrate-trend.up { color: #10b981; }
.hitrate-trend.down { color: #ef4444; }

.hitrate-charts {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
  margin-bottom: 24px;
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
  height: 150px;
}

.hitrate-chart {
  width: 100%;
  height: 100%;
}

.bar-chart {
  display: flex;
  align-items: flex-end;
  gap: 16px;
  height: 150px;
  padding-top: 20px;
}

.bar-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}

.bar-wrapper {
  width: 100%;
  height: 100px;
  display: flex;
  align-items: flex-end;
}

.bar {
  width: 100%;
  border-radius: var(--radius-sm) var(--radius-sm) 0 0;
}

.bar-label {
  font-size: 11px;
  color: var(--text-secondary);
}

.bar-value {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-primary);
}

.hitrate-details {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
}

.detail-section {
  padding: 20px;
  background: var(--glass-bg);
  border-radius: var(--radius-md);
}

.detail-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 500;
  color: var(--text-primary);
  margin-bottom: 16px;
}

.detail-title i {
  color: var(--primary-color);
}

.top-queries,
.low-hitrate-queries {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.query-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  background: rgba(255, 255, 255, 0.5);
  border-radius: var(--radius-sm);
}

.query-item.warning {
  background: rgba(239, 68, 68, 0.05);
}

.query-rank {
  width: 24px;
  height: 24px;
  background: rgba(59, 89, 152, 0.1);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 600;
  color: var(--text-secondary);
}

.query-rank.top {
  background: linear-gradient(135deg, var(--primary-color), var(--primary-light));
  color: white;
}

.query-text {
  flex: 1;
  font-size: 13px;
  color: var(--text-primary);
}

.query-count {
  font-size: 12px;
  color: var(--text-secondary);
}

.query-rate {
  font-size: 12px;
  font-weight: 600;
}

.query-rate.high { color: #10b981; }
.query-rate.medium { color: #f59e0b; }
.query-rate.low { color: #ef4444; }

.improve-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  background: rgba(59, 89, 152, 0.15);
  border: none;
  border-radius: var(--radius-sm);
  font-size: 11px;
  color: var(--primary-color);
  cursor: pointer;
}
</style>
