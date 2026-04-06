<template>
  <!-- 面试报告生成页面，用于在面试结束后展示报告生成状态 -->
  <section class="report-generating-page">
    <!-- 中心卡片容器 -->
    <div class="report-generating-card glass-card">
      <!-- 状态图标区域 -->
      <div class="status-icon" :class="statusClass">
        <i :class="statusIcon"></i>
      </div>
      <!-- 标题文字 -->
      <h2 class="title">{{ titleText }}</h2>
      <!-- 副标题说明 -->
      <p class="subtitle">
        {{ subtitleText }}
      </p>
      <!-- 操作按钮区域 -->
      <div class="actions">
        <!-- 返回面试记录按钮 -->
        <button class="btn btn-primary glass-btn" @click="$emit('goHistory')">
          <i class="fas fa-history"></i>
          返回面试记录
        </button>
        <!-- 重新拉取报告按钮（仅在失败时显示） -->
        <button
          v-if="normalizedStatus === 'failed'"
          class="btn btn-secondary glass-btn"
          @click="$emit('refreshStatus')"
        >
          <i class="fas fa-rotate"></i>
          重新拉取报告
        </button>
        <!-- 重新面试按钮（仅在失败时显示） -->
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

/**
 * 面试报告生成页面组件
 * 
 * 本组件负责在面试结束后向用户展示报告生成的状态，包括：
 * - 生成中状态（带有旋转加载动画）
 * - 生成失败状态（提供重试和重新面试选项）
 * 
 * 该组件是一个纯展示型组件，所有交互逻辑通过事件传递给父组件处理
 * 
 * @component
 * @example
 * <InterviewReportGeneratingPage
 *   status="generating"
 *   jobName="后端开发工程师"
 *   @goHistory="goToHistory"
 *   @refreshStatus="refreshReport"
 *   @restart="restartInterview"
 * />
 */
export default {
  name: 'InterviewReportGeneratingPage',
  props: {
    /**
     * 报告生成状态
     * @type {string}
     * @default 'generating'
     * @example 'generating' | 'failed' | 'ready'
     */
    status: {
      type: String,
      default: 'generating'
    },
    /**
     * 岗位名称，用于在提示文案中显示
     * @type {string}
     * @default '本场面试'
     */
    jobName: {
      type: String,
      default: '本场面试'
    }
  },
  /**
   * 组件事件列表
   * @event goHistory - 用户点击返回面试记录按钮时触发
   * @event refreshStatus - 用户点击重新拉取报告按钮时触发
   * @event restart - 用户点击重新面试按钮时触发
   */
  emits: ['goHistory', 'refreshStatus', 'restart'],
  setup(props) {
    /**
     * 规范化后的状态，只识别 'generating' 和 'failed' 两种状态
     * 其他状态统一按 'generating' 处理
     * @returns {string} 'generating' 或 'failed'
     */
    const normalizedStatus = computed(() => {
      return props.status === 'failed' ? 'failed' : 'generating'
    })

    /**
     * 状态图标容器的 CSS 类名
     * @returns {string} 与状态对应的 CSS 类名
     */
    const statusClass = computed(() => normalizedStatus.value)

    /**
     * 状态图标 FontAwesome 类名
     * @returns {string} 失败时显示感叹号，生成中显示旋转加载图标
     */
    const statusIcon = computed(() => {
      return normalizedStatus.value === 'failed'
        ? 'fas fa-circle-exclamation'
        : 'fas fa-spinner fa-spin'
    })

    /**
     * 主标题文字
     * @returns {string} 失败时显示"报告生成失败"，生成中显示"报告正在生成中"
     */
    const titleText = computed(() => {
      return normalizedStatus.value === 'failed' ? '报告生成失败' : '报告正在生成中'
    })

    /**
     * 副标题说明文字
     * 根据状态和岗位名称生成对应的提示文案
     * @returns {string} 详细的状态说明文字
     */
    const subtitleText = computed(() => {
      if (normalizedStatus.value === 'failed') {
        // 失败时提示用户可以选择重新拉取或重新面试
        return `${props.jobName} 的报告生成失败。你可以重新拉取状态，或直接重新发起一场面试。`
      }
      // 生成中时提示用户可以离开页面，生成完成后会自动跳转
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
/**
 * 页面容器样式
 * 全屏高度，垂直居中对齐内容
 */
.report-generating-page {
  min-height: calc(100vh - 80px);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}

/**
 * 中心卡片容器样式
 * 玻璃拟态效果，最大宽度680px
 */
.report-generating-card {
  width: min(680px, 100%);
  padding: 36px;
  text-align: center;
}

/**
 * 状态图标样式
 * 50%圆角，根据状态显示不同背景色
 */
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

/**
 * 生成中状态图标样式
 * 蓝色背景，体现正在处理中
 */
.status-icon.generating {
  color: var(--primary-color);
  background: rgba(102, 126, 234, 0.15);
}

/**
 * 失败状态图标样式
 * 红色背景，强调异常状态
 */
.status-icon.failed {
  color: var(--danger-color);
  background: rgba(239, 68, 68, 0.15);
}

/**
 * 主标题样式
 */
.title {
  margin-bottom: 12px;
  color: var(--text-primary);
}

/**
 * 副标题样式
 * 最大宽度限制，增强可读性
 */
.subtitle {
  margin: 0 auto 24px;
  color: var(--text-secondary);
  max-width: 560px;
  line-height: 1.7;
}

/**
 * 按钮容器样式
 * 水平居中，支持换行
 */
.actions {
  display: flex;
  justify-content: center;
  flex-wrap: wrap;
  gap: 12px;
}
</style>
