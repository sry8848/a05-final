<template>
  <div class="rag-management-page">
    <section class="page-header glass-card">
      <div>
        <h3>
          <i class="fas fa-database"></i>
          RAG语料管理
        </h3>
        <p>上传 JSONL 题卡文件，服务端会先全量校验，全部通过后再统一入库。</p>
      </div>
    </section>

    <section class="upload-section glass-card">
      <div class="section-header">
        <h4>
          <i class="fas fa-cloud-upload-alt"></i>
          JSONL 文件上传
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
          ref="fileInput"
          type="file"
          multiple
          accept=".jsonl"
          hidden
          @change="handleFileSelect"
        >
        <div class="upload-icon">
          <i class="fas fa-file-upload"></i>
        </div>
        <p class="main-text">拖拽 JSONL 文件到此处或点击上传</p>
        <p class="sub-text">仅支持 JSONL 题卡导入格式，服务端会先全量校验再统一入库</p>
        <div class="upload-formats">
          <span class="format-badge jsonl">
            <i class="fas fa-file-code"></i>
            JSONL
          </span>
        </div>
      </div>

      <div v-if="uploadQueue.length > 0" class="upload-queue">
        <div class="queue-header">
          <span class="queue-title">上传队列</span>
          <button class="clear-queue-btn" @click="clearQueue">清空</button>
        </div>

        <div class="queue-list">
          <div v-for="item in uploadQueue" :key="item.id" class="queue-item" :class="item.status">
            <div class="file-icon">
              <i class="fas fa-file-code"></i>
            </div>

            <div class="file-body">
              <div class="file-row">
                <div class="file-meta">
                  <span class="file-name">{{ item.name }}</span>
                  <span class="file-size">{{ formatSize(item.size) }}</span>
                </div>
                <div class="file-actions">
                  <button
                    v-if="item.status === 'failed' && hasDetail(item)"
                    class="toggle-btn"
                    @click.stop="toggleExpanded(item.id)"
                  >
                    {{ item.expanded ? '收起详情' : '查看详情' }}
                  </button>
                  <button
                    v-if="item.status !== 'uploading'"
                    class="remove-btn"
                    @click.stop="removeFromQueue(item.id)"
                  >
                    <i class="fas fa-times"></i>
                  </button>
                </div>
              </div>

              <div class="progress-row">
                <div class="progress-bar">
                  <div class="progress-fill" :class="item.status" :style="{ width: `${item.progress}%` }"></div>
                </div>
                <span class="progress-text" :class="item.status">{{ item.progress }}%</span>
              </div>

              <div class="summary-row">
                <span class="status-chip" :class="item.status">
                  <i :class="statusIcon(item.status)"></i>
                  {{ statusText(item.status) }}
                </span>
                <span class="summary-text">{{ item.summary }}</span>
              </div>

              <div v-if="item.result" class="result-meta">
                <span>总行数：{{ item.result.totalLines }}</span>
                <span>合法行数：{{ item.result.validLines }}</span>
                <span v-if="item.status === 'success'">入库条数：{{ item.result.ingestedCount }}</span>
              </div>

              <div v-if="item.expanded && hasDetail(item)" class="error-detail">
                <div
                  v-for="(error, index) in item.result.errors"
                  :key="`${item.id}-error-${index}`"
                  class="error-item"
                >
                  <div class="error-head">
                    <span class="error-scope">{{ error.scope === 'line' ? `第 ${error.lineNo} 行` : '文件级错误' }}</span>
                    <span v-if="error.field" class="error-field">{{ error.field }}</span>
                  </div>
                  <div class="error-reason">{{ error.reason }}</div>
                  <div v-if="error.expected" class="error-expected">期望：{{ error.expected }}</div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { importKnowledgeJsonl } from '../api/ragAdmin.js'
import {
  createUploadQueueItem,
  markFailure,
  markSuccess,
  markUploading
} from './ragManagementImportState.js'

const fileInput = ref(null)
const isDragging = ref(false)
const uploadQueue = ref([])

function triggerFileInput() {
  fileInput.value?.click()
}

function handleDrop(event) {
  isDragging.value = false
  processFiles(event.dataTransfer?.files)
}

function handleFileSelect(event) {
  processFiles(event.target.files)
  event.target.value = ''
}

function processFiles(fileList) {
  const files = Array.from(fileList || [])
  files.forEach((file) => {
    const queueItem = createUploadQueueItem(file)
    uploadQueue.value.unshift(queueItem)

    if (!file.name.toLowerCase().endsWith('.jsonl')) {
      patchQueueItem(queueItem.id, (current) => markFailure(current, {
        status: 400,
        message: '文件类型不支持',
        result: {
          fileName: file.name,
          totalLines: 0,
          validLines: 0,
          ingestedCount: 0,
          errors: [
            {
              scope: 'file',
              reason: '文件类型不支持',
              expected: '.jsonl'
            }
          ]
        }
      }))
      return
    }

    void uploadSingleFile(file, queueItem.id)
  })
}

async function uploadSingleFile(file, itemId) {
  patchQueueItem(itemId, (current) => markUploading(current))
  try {
    const result = await importKnowledgeJsonl(file)
    patchQueueItem(itemId, (current) => markSuccess(current, result))
  } catch (error) {
    patchQueueItem(itemId, (current) => markFailure(current, error))
  }
}

function patchQueueItem(itemId, updater) {
  uploadQueue.value = uploadQueue.value.map((item) => {
    if (item.id !== itemId) {
      return item
    }
    return updater(item)
  })
}

function toggleExpanded(itemId) {
  patchQueueItem(itemId, (current) => ({
    ...current,
    expanded: !current.expanded
  }))
}

function clearQueue() {
  uploadQueue.value = []
}

function removeFromQueue(itemId) {
  uploadQueue.value = uploadQueue.value.filter((item) => item.id !== itemId)
}

function hasDetail(item) {
  return Boolean(item?.result?.errors?.length)
}

function formatSize(bytes) {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

function statusText(status) {
  const mapping = {
    pending: '等待中',
    uploading: '处理中',
    success: '已完成',
    failed: '失败'
  }
  return mapping[status] || status
}

function statusIcon(status) {
  const mapping = {
    pending: 'fas fa-clock',
    uploading: 'fas fa-spinner fa-spin',
    success: 'fas fa-check',
    failed: 'fas fa-triangle-exclamation'
  }
  return mapping[status] || 'fas fa-circle-question'
}
</script>

<style scoped>
.rag-management-page {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.page-header,
.upload-section {
  padding: 24px;
}

.page-header h3,
.section-header h4 {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 0;
  color: var(--text-primary);
}

.page-header p {
  margin: 8px 0 0;
  color: var(--text-secondary);
  font-size: 13px;
}

.section-header {
  margin-bottom: 20px;
}

.upload-area {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 44px 20px;
  border: 2px dashed var(--glass-border);
  border-radius: var(--radius-md);
  background: rgba(59, 89, 152, 0.03);
  cursor: pointer;
  transition: all 0.2s ease;
}

.upload-area.dragging,
.upload-area:hover {
  border-color: var(--primary-color);
  background: rgba(59, 89, 152, 0.08);
}

.upload-icon {
  font-size: 42px;
  color: var(--primary-color);
}

.main-text {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}

.sub-text {
  margin: 0;
  font-size: 12px;
  color: var(--text-secondary);
}

.upload-formats {
  display: flex;
  gap: 8px;
}

.format-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
}

.format-badge.jsonl {
  color: #2563eb;
  background: rgba(37, 99, 235, 0.12);
}

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
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}

.clear-queue-btn,
.toggle-btn,
.remove-btn {
  border: none;
  border-radius: var(--radius-sm);
  cursor: pointer;
  font-family: inherit;
}

.clear-queue-btn {
  padding: 6px 12px;
  background: transparent;
  border: 1px solid var(--glass-border);
  color: var(--text-secondary);
}

.queue-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.queue-item {
  display: flex;
  gap: 14px;
  padding: 16px;
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-md);
  background: var(--glass-bg);
}

.queue-item.failed {
  border-color: rgba(239, 68, 68, 0.35);
}

.queue-item.success {
  border-color: rgba(16, 185, 129, 0.3);
}

.file-icon {
  width: 42px;
  height: 42px;
  border-radius: var(--radius-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  color: #2563eb;
  background: rgba(37, 99, 235, 0.12);
  flex-shrink: 0;
}

.file-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.file-row,
.progress-row,
.summary-row,
.result-meta,
.error-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.file-meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.file-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}

.file-size {
  font-size: 12px;
  color: var(--text-secondary);
}

.file-actions {
  display: flex;
  gap: 8px;
}

.toggle-btn,
.remove-btn {
  padding: 6px 10px;
  background: rgba(59, 89, 152, 0.08);
  color: var(--text-secondary);
}

.remove-btn {
  width: 32px;
  height: 32px;
  padding: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.progress-bar {
  flex: 1;
  height: 6px;
  background: rgba(59, 89, 152, 0.08);
  border-radius: 999px;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  transition: width 0.2s ease;
  background: linear-gradient(90deg, #94a3b8, #cbd5e1);
}

.progress-fill.uploading {
  background: linear-gradient(90deg, #2563eb, #60a5fa);
}

.progress-fill.success {
  background: linear-gradient(90deg, #10b981, #34d399);
}

.progress-fill.failed {
  background: linear-gradient(90deg, #ef4444, #f87171);
}

.progress-text {
  min-width: 44px;
  text-align: right;
  font-size: 12px;
  color: var(--text-secondary);
}

.progress-text.success {
  color: #10b981;
}

.progress-text.failed {
  color: #ef4444;
}

.status-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
}

.status-chip.pending {
  background: rgba(148, 163, 184, 0.14);
  color: #64748b;
}

.status-chip.uploading {
  background: rgba(37, 99, 235, 0.12);
  color: #2563eb;
}

.status-chip.success {
  background: rgba(16, 185, 129, 0.12);
  color: #10b981;
}

.status-chip.failed {
  background: rgba(239, 68, 68, 0.12);
  color: #ef4444;
}

.summary-text,
.result-meta {
  font-size: 13px;
  color: var(--text-secondary);
}

.result-meta {
  flex-wrap: wrap;
}

.error-detail {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 12px;
  border-radius: var(--radius-sm);
  background: rgba(239, 68, 68, 0.05);
}

.error-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.error-scope,
.error-field {
  font-size: 12px;
  font-weight: 600;
  color: #ef4444;
}

.error-reason,
.error-expected {
  font-size: 12px;
  color: var(--text-secondary);
}

@media (max-width: 768px) {
  .file-row,
  .progress-row,
  .summary-row {
    flex-direction: column;
    align-items: flex-start;
  }

  .file-actions {
    width: 100%;
    justify-content: flex-end;
  }

  .progress-text {
    text-align: left;
  }
}
</style>
