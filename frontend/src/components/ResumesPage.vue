<template>
  <section id="page-resumes" class="page-section active">
    <header class="page-header glass-card">
      <div class="header-left">
        <h1 class="page-title">
          <i class="fas fa-file-alt"></i>
          简历管理
        </h1>
        <p class="page-desc">管理多份简历，用于面试上下文。支持 PDF、DOCX、MD 上传与识别文本编辑。</p>
      </div>
      <div class="header-actions">
        <button type="button" class="btn btn-primary glass-btn" @click="showUploadModal = true">
          <i class="fas fa-cloud-upload-alt"></i>
          上传简历
        </button>
      </div>
    </header>

    <div class="resume-list-wrap">
      <div v-if="loading" class="loading-wrap">
        <i class="fas fa-spinner fa-spin"></i>
        <span>加载中...</span>
      </div>
      <div v-else-if="!list.length" class="empty-wrap">
        <i class="fas fa-folder-open"></i>
        <p>暂无简历</p>
        <p class="empty-hint">点击「上传简历」添加第一份简历</p>
      </div>
      <div v-else class="resume-list">
        <div
          v-for="item in list"
          :key="item.id"
          class="resume-card glass-card"
        >
          <div class="card-main">
            <div class="card-icon">
              <i class="fas fa-file-pdf" v-if="resumeFileKind(item) === 'pdf'"></i>
              <i class="fas fa-file-word" v-else-if="resumeFileKind(item) === 'word'"></i>
              <i class="fas fa-file-code" v-else-if="resumeFileKind(item) === 'markdown'"></i>
              <i class="fas fa-file-alt" v-else></i>
            </div>
            <div class="card-info">
              <span class="card-name">{{ item.name || '未命名简历' }}</span>
              <span class="card-time">{{ formatTime(item.createdAt) }}</span>
              <div class="card-tags">
                <span class="tag status" :class="item.parseStatus">{{ parseStatusText(item.parseStatus) }}</span>
              </div>
            </div>
          </div>
          <div class="card-actions">
            <button type="button" class="action-btn" @click="openDetail(item)">
              详情
            </button>
            <button type="button" class="action-btn danger" @click="confirmDelete(item)">
              删除
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- 上传弹窗 -->
    <div v-if="showUploadModal" class="modal-overlay" @click.self="closeUploadModal">
      <div class="modal-content glass-card upload-modal">
        <div class="modal-header">
          <h3><i class="fas fa-cloud-upload-alt"></i> 上传简历</h3>
          <button type="button" class="close-btn" @click="closeUploadModal" :disabled="uploading">
            <i class="fas fa-times"></i>
          </button>
        </div>
        <div class="modal-body">
          <div
            class="upload-zone"
            :class="{ dragover: isDragover, uploading: uploading }"
            @dragover.prevent="isDragover = true"
            @dragleave.prevent="isDragover = false"
            @drop.prevent="onDrop"
            @click="triggerFileInput"
          >
            <input
              ref="fileInput"
              type="file"
              accept=".pdf,.docx,.md"
              class="file-input"
              @change="onFileSelect"
            />
            <template v-if="!uploading">
              <i class="fas fa-cloud-upload-alt"></i>
              <p>将 PDF、DOCX 或 MD 文件拖到此处，或点击选择</p>
            </template>
            <template v-else>
              <i class="fas fa-spinner fa-spin"></i>
              <p>{{ uploadStatusText }}</p>
            </template>
          </div>
        </div>
      </div>
    </div>

    <!-- 详情/编辑弹窗 -->
    <div v-if="showDetailModal" class="modal-overlay" @click.self="closeDetailModal">
      <div class="modal-content glass-card detail-modal">
        <div class="modal-header">
          <h3><i class="fas fa-edit"></i> 简历详情 · 识别文本</h3>
          <button type="button" class="close-btn" @click="closeDetailModal" :disabled="saving">
            <i class="fas fa-times"></i>
          </button>
        </div>
        <div class="modal-body">
          <div v-if="detailLoading" class="detail-loading">
            <i class="fas fa-spinner fa-spin"></i>
            <span>加载中...</span>
          </div>
          <template v-else>
            <div class="form-group">
              <label>简历名称</label>
              <input
                v-model="detailForm.name"
                type="text"
                class="glass-input"
                placeholder="请输入名称"
              />
            </div>
            <div class="form-group">
              <label>解析状态</label>
              <span class="status-badge" :class="detailForm.parseStatus">{{ parseStatusText(detailForm.parseStatus) }}</span>
            </div>
            <div v-if="detailForm.parseStatus === 'parsing'" class="parsing-hint">
              <i class="fas fa-spinner fa-spin"></i>
              正在解析，请稍候…
            </div>
            <div v-else class="form-group">
              <label>识别文本（可编辑后保存，将作为面试上下文使用）</label>
              <textarea
                v-model="detailForm.parsedText"
                class="glass-textarea"
                placeholder="解析完成后将显示识别文本"
                rows="12"
              ></textarea>
            </div>
          </template>
        </div>
        <div class="modal-footer">
          <button type="button" class="btn btn-secondary glass-btn" @click="closeDetailModal">
            取消
          </button>
          <button
            type="button"
            class="btn btn-primary glass-btn"
            :disabled="saving || detailForm.parseStatus === 'parsing'"
            @click="saveDetail"
          >
            <i v-if="saving" class="fas fa-spinner fa-spin"></i>
            <i v-else class="fas fa-save"></i>
            {{ saving ? '保存中...' : '保存' }}
          </button>
        </div>
      </div>
    </div>
  </section>
</template>

<script>
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import * as resumeApi from '@/api/resume'
import { buildUploadedResumeItem, detectResumeFileKind, patchResumeListItem } from '@/utils/resumeState'

const PARSE_STATUS_MAP = {
  parsing: '解析中',
  parsed: '解析完成',
  failed: '解析失败'
}

export default {
  name: 'ResumesPage',
  setup() {
    const list = ref([])
    const loading = ref(false)
    const showUploadModal = ref(false)
    const showDetailModal = ref(false)
    const isDragover = ref(false)
    const uploading = ref(false)
    const uploadStatusText = ref('')
    const fileInput = ref(null)
    const detailLoading = ref(false)
    const saving = ref(false)
    const detailForm = reactive({
      id: null,
      name: '',
      parseStatus: '',
      parsedText: ''
    })
    const listPollTimers = new Map()

    function parseStatusText(status) {
      return PARSE_STATUS_MAP[status] || status || '未知'
    }

    function formatTime(createdAt) {
      if (!createdAt) return ''
      const d = new Date(createdAt)
      const now = new Date()
      const diff = now - d
      if (diff < 60000) return '刚刚'
      if (diff < 3600000) return Math.floor(diff / 60000) + ' 分钟前'
      if (diff < 86400000) return d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
      return d.toLocaleDateString('zh-CN')
    }

    function resumeFileKind(item) {
      return detectResumeFileKind(item?.name)
    }

    async function loadList() {
      loading.value = true
      try {
        const data = await resumeApi.getResumes()
        list.value = Array.isArray(data) ? data : []
        restartParsingPolls()
      } catch (e) {
        clearAllListPolls()
        list.value = []
        console.warn('简历列表加载失败:', e.message)
      } finally {
        loading.value = false
      }
    }

    function triggerFileInput() {
      if (uploading.value) return
      fileInput.value?.click()
    }

    function onDrop(e) {
      isDragover.value = false
      if (uploading.value) return
      const file = e.dataTransfer?.files?.[0]
      if (file) doUpload(file)
    }

    function onFileSelect(e) {
      const file = e.target?.files?.[0]
      if (file) doUpload(file)
      e.target.value = ''
    }

    async function doUpload(file) {
      const name = (file.name || '').toLowerCase()
      if (!name.endsWith('.pdf') && !name.endsWith('.docx') && !name.endsWith('.md')) {
        alert('仅支持 PDF、DOCX、MD 格式')
        return
      }
      uploading.value = true
      uploadStatusText.value = '上传中...'
      try {
        const data = await resumeApi.uploadResume(file)
        const resumeId = data?.resumeId ?? data?.id
        uploadStatusText.value = data?.parseStatus === 'parsed' ? '已完成' : '解析中...'
        const uploadedItem = buildUploadedResumeItem({
          resumeId,
          fileName: file.name,
          parseStatus: data?.parseStatus || 'parsing'
        })
        list.value = [uploadedItem, ...list.value]
        closeUploadModal()
        openDetail(uploadedItem)
      } catch (e) {
        alert(e.message || '上传失败')
      } finally {
        uploading.value = false
      }
    }

    function stopListPoll(resumeId) {
      const timer = listPollTimers.get(resumeId)
      if (timer) {
        clearTimeout(timer)
        listPollTimers.delete(resumeId)
      }
    }

    function clearAllListPolls() {
      for (const timer of listPollTimers.values()) {
        clearTimeout(timer)
      }
      listPollTimers.clear()
    }

    function syncListItem(resumeId, patch) {
      list.value = patchResumeListItem(list.value, resumeId, patch)
    }

    async function refreshDetail(resumeId = detailForm.id) {
      if (!resumeId) return
      try {
        const data = await resumeApi.getResume(resumeId)
        if (detailForm.id !== resumeId) return
        detailForm.name = data?.name ?? detailForm.name
        detailForm.parseStatus = data?.parseStatus ?? detailForm.parseStatus
        detailForm.parsedText = data?.parsedText ?? data?.parsed_text ?? ''
        syncListItem(resumeId, {
          name: detailForm.name,
          parseStatus: detailForm.parseStatus
        })
        if (detailForm.parseStatus === 'parsing') {
          scheduleListPoll(resumeId)
        }
      } catch (e) {
        if (detailForm.id === resumeId) {
          detailForm.parsedText = ''
        }
      } finally {
        if (detailForm.id === resumeId) {
          detailLoading.value = false
        }
      }
    }

    function scheduleListPoll(resumeId, delayMs = 2500) {
      if (!resumeId) return
      stopListPoll(resumeId)
      const timer = setTimeout(async () => {
        listPollTimers.delete(resumeId)
        try {
          const data = await resumeApi.getParseStatus(resumeId)
          const nextStatus = data?.parseStatus
          if (nextStatus) {
            syncListItem(resumeId, { parseStatus: nextStatus })
            if (detailForm.id === resumeId) {
              detailForm.parseStatus = nextStatus
              if (data?.parsedTextPreview) {
                detailForm.parsedText = data.parsedTextPreview
              }
            }
          }
          if (nextStatus === 'parsing') {
            scheduleListPoll(resumeId)
            return
          }
          if (nextStatus === 'parsed' && detailForm.id === resumeId) {
            await refreshDetail(resumeId)
          }
        } catch (_) {
          scheduleListPoll(resumeId)
        }
      }, delayMs)
      listPollTimers.set(resumeId, timer)
    }

    function restartParsingPolls() {
      clearAllListPolls()
      list.value
        .filter(item => item?.parseStatus === 'parsing')
        .forEach(item => scheduleListPoll(item.id))
    }

    function closeUploadModal() {
      if (!uploading.value) {
        showUploadModal.value = false
        isDragover.value = false
      }
    }

    function openDetail(item) {
      showDetailModal.value = true
      detailForm.id = item.id
      detailForm.name = item.name || ''
      detailForm.parseStatus = item.parseStatus || ''
      detailForm.parsedText = ''
      detailLoading.value = true
      refreshDetail(item.id)
    }

    async function saveDetail() {
      if (!detailForm.id) return
      saving.value = true
      try {
        await resumeApi.updateResume(detailForm.id, {
          name: detailForm.name,
          parsedText: detailForm.parsedText
        })
        syncListItem(detailForm.id, { name: detailForm.name })
        closeDetailModal()
      } catch (e) {
        alert(e.message || '保存失败')
      } finally {
        saving.value = false
      }
    }

    function closeDetailModal() {
      showDetailModal.value = false
      detailLoading.value = false
      detailForm.id = null
      detailForm.name = ''
      detailForm.parseStatus = ''
      detailForm.parsedText = ''
    }

    function confirmDelete(item) {
      if (!confirm('确定删除这份简历吗？')) return
      resumeApi.deleteResume(item.id).then(() => {
        stopListPoll(item.id)
        list.value = list.value.filter(r => r.id !== item.id)
        if (showDetailModal.value && detailForm.id === item.id) closeDetailModal()
      }).catch(e => alert(e.message || '删除失败'))
    }

    onMounted(() => { loadList() })
    onBeforeUnmount(() => { clearAllListPolls() })

    return {
      list,
      loading,
      showUploadModal,
      showDetailModal,
      isDragover,
      uploading,
      uploadStatusText,
      fileInput,
      detailLoading,
      saving,
      detailForm,
      parseStatusText,
      resumeFileKind,
      formatTime,
      triggerFileInput,
      onDrop,
      onFileSelect,
      closeUploadModal,
      openDetail,
      closeDetailModal,
      saveDetail,
      confirmDelete
    }
  }
}
</script>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding: 24px 28px;
  margin-bottom: 24px;
}

.header-left {
  flex: 1;
}

.page-title {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 22px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0 0 8px;
}

.page-title i {
  color: var(--primary-color);
}

.page-desc {
  font-size: 14px;
  color: var(--text-secondary);
  margin: 0;
}

.resume-list-wrap {
  min-height: 200px;
}

.loading-wrap,
.empty-wrap {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 24px;
  color: var(--text-secondary);
}

.loading-wrap i,
.empty-wrap i {
  font-size: 48px;
  margin-bottom: 16px;
  opacity: 0.6;
}

.empty-wrap p {
  margin: 0;
}

.empty-hint {
  font-size: 13px;
  color: var(--text-light);
  margin-top: 8px;
}

.resume-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.resume-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 24px;
  flex-wrap: wrap;
  gap: 16px;
}

.card-main {
  display: flex;
  align-items: center;
  gap: 16px;
}

.card-icon {
  width: 48px;
  height: 48px;
  border-radius: var(--radius-md);
  background: linear-gradient(135deg, var(--primary-color), var(--primary-light));
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
}

.card-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.card-name {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
}

.card-time {
  font-size: 13px;
  color: var(--text-secondary);
}

.card-tags {
  display: flex;
  gap: 8px;
  margin-top: 4px;
}

.tag {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 4px;
}

.tag.status {
  background: rgba(255,255,255,0.08);
  color: var(--text-secondary);
}

.tag.status.parsed {
  color: #10b981;
}

.tag.status.parsing {
  color: #f59e0b;
}

.tag.status.failed {
  color: var(--danger-color);
}

.card-actions {
  display: flex;
  gap: 10px;
}

.action-btn {
  padding: 8px 16px;
  font-size: 13px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--glass-border);
  background: var(--glass-bg);
  color: var(--text-secondary);
  cursor: pointer;
  font-family: inherit;
  transition: all 0.2s ease;
}

.action-btn:hover {
  border-color: var(--primary-color);
  color: var(--primary-color);
}

.action-btn.danger:hover {
  border-color: var(--danger-color);
  color: var(--danger-color);
}

.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  padding: 24px;
}

.modal-content {
  width: 100%;
  max-width: 560px;
  max-height: 90vh;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  border-radius: var(--radius-lg);
}

.modal-content.detail-modal {
  max-width: 640px;
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px 24px;
  border-bottom: 1px solid var(--glass-border);
}

.modal-header h3 {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
}

.modal-header h3 i {
  color: var(--primary-color);
}

.close-btn {
  width: 36px;
  height: 36px;
  border: none;
  background: transparent;
  border-radius: var(--radius-sm);
  color: var(--text-secondary);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: inherit;
}

.close-btn:hover:not(:disabled) {
  background: rgba(59, 89, 152, 0.1);
  color: var(--primary-color);
}

.close-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.modal-body {
  padding: 24px;
  overflow-y: auto;
  flex: 1;
}

.upload-zone {
  border: 2px dashed var(--glass-border);
  border-radius: var(--radius-md);
  padding: 48px 24px;
  text-align: center;
  cursor: pointer;
  transition: all 0.2s ease;
  background: rgba(59, 89, 152, 0.05);
}

.upload-zone:hover:not(.uploading),
.upload-zone.dragover {
  border-color: var(--primary-color);
  background: rgba(59, 89, 152, 0.1);
}

.upload-zone i {
  font-size: 40px;
  color: var(--primary-color);
  margin-bottom: 16px;
  display: block;
}

.upload-zone p {
  margin: 0;
  font-size: 14px;
  color: var(--text-secondary);
}

.upload-zone.uploading {
  cursor: not-allowed;
}

.file-input {
  position: absolute;
  width: 0;
  height: 0;
  opacity: 0;
  pointer-events: none;
}

.detail-loading,
.parsing-hint {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 24px;
  justify-content: center;
  color: var(--text-secondary);
}

.form-group {
  margin-bottom: 20px;
}

.form-group label {
  display: block;
  font-size: 14px;
  font-weight: 500;
  color: var(--text-primary);
  margin-bottom: 8px;
}

.glass-input {
  width: 100%;
  padding: 12px 16px;
  background: var(--glass-bg);
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-md);
  font-size: 14px;
  color: var(--text-primary);
  font-family: inherit;
}

.glass-input:focus {
  outline: none;
  border-color: var(--primary-color);
}

.glass-textarea {
  width: 100%;
  padding: 12px 16px;
  background: var(--glass-bg);
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-md);
  font-size: 14px;
  color: var(--text-primary);
  font-family: inherit;
  resize: vertical;
  min-height: 200px;
}

.glass-textarea:focus {
  outline: none;
  border-color: var(--primary-color);
}

.status-badge {
  display: inline-block;
  padding: 4px 12px;
  border-radius: 6px;
  font-size: 13px;
}

.status-badge.parsed { background: rgba(16, 185, 129, 0.15); color: #10b981; }
.status-badge.parsing { background: rgba(245, 158, 11, 0.15); color: #f59e0b; }
.status-badge.failed { background: rgba(239, 68, 68, 0.15); color: var(--danger-color); }

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 16px 24px;
  border-top: 1px solid var(--glass-border);
}
</style>
