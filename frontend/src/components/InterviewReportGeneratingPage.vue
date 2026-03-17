<template>
  <section class="report-generating-page">
    <div class="report-generating-card glass-card">
      <div class="status-icon" :class="statusClass">
        <i :class="statusIcon"></i>
      </div>
      <h2 class="title">{{ titleText }}</h2>
      <p class="subtitle">
        {{ subtitleText }}
      </p>
      <div class="actions">
        <button class="btn btn-primary glass-btn" @click="$emit('goHistory')">
          <i class="fas fa-history"></i>
          返回面试记录
        </button>
        <button
          v-if="normalizedStatus === 'failed'"
          class="btn btn-secondary glass-btn"
          @click="$emit('refreshStatus')"
        >
          <i class="fas fa-rotate"></i>
          重新拉取报告
        </button>
        <button
          v-if="normalizedStatus === 'failed'"
          class="btn btn-secondary glass-btn"
          @click="$emit('restart')"
        >
          <i class="fas fa-redo"></i>
          重新面试
        </button>
      </div>
    </div>
  </section>
</template>

<script>
import { computed } from 'vue'

export default {
  name: 'InterviewReportGeneratingPage',
  props: {
    status: {
      type: String,
      default: 'generating'
    },
    jobName: {
      type: String,
      default: '本场面试'
    }
  },
  emits: ['goHistory', 'refreshStatus', 'restart'],
  setup(props) {
    const normalizedStatus = computed(() => {
      return props.status === 'failed' ? 'failed' : 'generating'
    })

    const statusClass = computed(() => normalizedStatus.value)

    const statusIcon = computed(() => {
      return normalizedStatus.value === 'failed'
        ? 'fas fa-circle-exclamation'
        : 'fas fa-spinner fa-spin'
    })

    const titleText = computed(() => {
      return normalizedStatus.value === 'failed' ? '报告生成失败' : '报告正在生成中'
    })

    const subtitleText = computed(() => {
      if (normalizedStatus.value === 'failed') {
        return `${props.jobName} 的报告生成失败。你可以重新拉取状态，或直接重新发起一场面试。`
      }
      return `${props.jobName} 的报告正在生成，你可以先返回面试记录页。若停留在此页，生成完成后将自动跳转。`
    })

    return {
      statusClass,
      statusIcon,
      titleText,
      subtitleText
    }
  }
}
</script>

<style scoped>
.report-generating-page {
  min-height: calc(100vh - 80px);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}

.report-generating-card {
  width: min(680px, 100%);
  padding: 36px;
  text-align: center;
}

.status-icon {
  width: 72px;
  height: 72px;
  margin: 0 auto 18px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
}

.status-icon.generating {
  color: var(--primary-color);
  background: rgba(102, 126, 234, 0.15);
}

.status-icon.failed {
  color: var(--danger-color);
  background: rgba(239, 68, 68, 0.15);
}

.title {
  margin-bottom: 12px;
  color: var(--text-primary);
}

.subtitle {
  margin: 0 auto 24px;
  color: var(--text-secondary);
  max-width: 560px;
  line-height: 1.7;
}

.actions {
  display: flex;
  justify-content: center;
  flex-wrap: wrap;
  gap: 12px;
}
</style>
