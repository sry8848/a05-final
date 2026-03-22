<template>
  <section id="page-interview" class="page-section active" :class="{ 'fullscreen-mode': fullscreen }">
    <div v-if="!isRunning && !isLoading && !isDeviceTesting" id="interview-config" class="interview-config-enhanced">
      <div class="config-header glass-card">
        <h2 class="config-main-title">
          <i class="fas fa-clipboard-check"></i>
          面试准备
        </h2>
        <p class="config-subtitle">完善以下信息，获得更精准的面试体验</p>
      </div>

      <div class="config-single glass-card">
        <div class="form-group">
          <label>简历选择</label>
          <CustomSelect
            v-model="config.resumeId"
            :options="resumeOptions"
            placeholder="请选择简历"
          />
          <p v-if="!resumeOptions.length" class="field-hint">请先前往「简历管理」添加简历</p>
        </div>

        <div class="form-group">
          <label>岗位选择</label>
          <CustomSelect
            v-model="config.jobType"
            :options="positionOptions"
            placeholder="请选择岗位"
          />
        </div>

        <div class="form-group">
          <label>工作年限</label>
          <div class="experience-selector">
            <button
              v-for="exp in experienceLevels"
              :key="exp.value"
              class="exp-btn"
              :class="{ active: config.experience === exp.value }"
              @click="config.experience = exp.value"
            >
              {{ exp.label }}
            </button>
          </div>
        </div>

        <div class="form-group">
          <label>招聘要求 JD</label>
          <textarea
            v-model="config.jobDescription"
            class="glass-textarea jd-input"
            placeholder="粘贴招聘要求（JD），AI 将根据 JD 生成针对性问题..."
            rows="5"
          ></textarea>
          <div class="jd-hint">
            <i class="fas fa-lightbulb"></i>
            <span>粘贴 JD 后，系统将智能分析关键词并生成相关问题</span>
          </div>
        </div>

        <div class="form-group">
          <label>面试模式</label>
          <div class="mode-cards">
            <div
              class="mode-card practice"
              :class="{ active: config.interviewMode === 'practice' }"
              @click="config.interviewMode = 'practice'"
            >
              <div class="mode-icon">
                <i class="fas fa-graduation-cap"></i>
              </div>
              <div class="mode-info">
                <span class="mode-name">练习模式</span>
                <span class="mode-desc">同时支持文本和语音输入，不设倒计时，只计入技术得分，适合碎片化练习</span>
              </div>
            </div>
            <div
              class="mode-card professional"
              :class="{ active: config.interviewMode === 'professional' }"
              @click="config.interviewMode = 'professional'"
            >
              <div class="mode-icon">
                <i class="fas fa-award"></i>
              </div>
              <div class="mode-info">
                <span class="mode-name">专业模式</span>
                <span class="mode-desc">强制语音，有思考时间限制，同时计入技术得分和面试能力得分，专为拟真化设计</span>
              </div>
            </div>
          </div>
        </div>

        <div class="form-group" v-if="config.interviewMode === 'practice'">
          <label>侧重知识点（可选）</label>
          <div class="knowledge-points">
            <div class="points-input">
              <input
                type="text"
                v-model="newKnowledgePoint"
                class="glass-input"
                placeholder="输入知识点后按回车添加"
                @keydown.enter.prevent="addKnowledgePoint"
              />
            </div>
            <div class="points-tags">
              <span
                v-for="(point, index) in config.knowledgePoints"
                :key="index"
                class="point-tag"
              >
                {{ point }}
                <i class="fas fa-times" @click="removeKnowledgePoint(index)"></i>
              </span>
            </div>
          </div>
        </div>

        <div class="form-group">
          <label>压迫感强度</label>
          <div class="pressure-selector">
            <button
              v-for="p in pressureLevels"
              :key="p.value"
              class="pressure-btn"
              :class="[{ active: config.pressure === p.value }, 'pressure-' + p.value]"
              @click="config.pressure = p.value"
            >
              {{ p.label }}
            </button>
          </div>
        </div>

        <div class="form-group">
          <label>AI 音色</label>
          <div class="voice-selector">
            <button
              v-for="voice in voiceOptions"
              :key="voice.value"
              class="voice-btn"
              :class="[{ active: config.voiceType === voice.value }, 'voice-' + voice.value]"
              @click="config.voiceType = voice.value"
            >
              <i :class="voice.icon"></i>
              <span>{{ voice.label }}</span>
            </button>
          </div>
        </div>
      </div>

      <div class="config-actions glass-card">
        <button class="btn btn-primary btn-large start-btn" @click="startInterview">
          <i class="fas fa-arrow-right"></i>
          下一步
        </button>
      </div>
    </div>

    <div v-if="isLoading" class="interview-loading-page">
      <div class="loading-container">
        <div class="loading-animation">
          <div class="loading-circle">
            <div class="circle-inner"></div>
            <div class="circle-glow"></div>
          </div>
          <div class="loading-particles">
            <span v-for="i in 12" :key="i" class="particle" :style="{ '--delay': i * 0.1 + 's' }"></span>
          </div>
        </div>

        <div class="loading-content">
          <h2 class="loading-title">{{ loadingTitle }}</h2>
          <p class="loading-subtitle">{{ loadingSubtitle }}</p>
          
          <div class="loading-progress">
            <div class="progress-bar">
              <div class="progress-fill" :style="{ width: loadingProgress + '%' }"></div>
            </div>
            <span class="progress-text">{{ displayLoadingProgress }}%</span>
          </div>

          <div class="loading-steps">
            <div 
              v-for="(step, index) in loadingSteps" 
              :key="index"
              class="step-item"
              :class="{ active: index <= currentStep, completed: index < currentStep }"
            >
              <div class="step-icon">
                <i v-if="index < currentStep" class="fas fa-check"></i>
                <i v-else-if="index === currentStep" class="fas fa-spinner fa-spin"></i>
                <i v-else class="fas fa-circle"></i>
              </div>
              <span class="step-text">{{ step }}</span>
            </div>
          </div>
        </div>

        <div class="loading-info glass-card">
          <div class="info-row">
            <div class="info-item">
              <i class="fas fa-building"></i>
              <span>{{ config.companyName || '模拟面试' }}</span>
            </div>
            <div class="info-item">
              <i class="fas fa-user-tie"></i>
              <span>{{ currentJob?.label }}</span>
            </div>
            <div class="info-item">
              <i class="fas fa-layer-group"></i>
              <span>{{ currentRound?.label }}</span>
            </div>
          </div>
          <div class="info-row">
            <div class="info-item">
              <i class="fas fa-graduation-cap"></i>
              <span>{{ config.interviewMode === 'practice' ? '练习模式' : '专业模式' }}</span>
            </div>
            <div class="info-item">
              <i class="fas fa-clock"></i>
              <span>{{ config.totalQuestions }} 道题目</span>
            </div>
            <div class="info-item">
              <i class="fas fa-microphone"></i>
              <span>{{ config.interviewMode === 'professional' ? '语音面试' : '文字面试' }}</span>
            </div>
          </div>
        </div>

        <div class="loading-tips">
          <div class="tip-icon">
            <i class="fas fa-lightbulb"></i>
          </div>
          <p class="tip-text">{{ currentTip }}</p>
        </div>
      </div>
    </div>

    <div v-if="isDeviceTesting" class="device-testing-page">
      <div class="testing-container">
        <div class="testing-header">
          <button class="btn btn-secondary testing-back-btn" @click="backToConfig">
            <i class="fas fa-arrow-left"></i>
            返回配置
          </button>
          <h2 class="testing-title">
            <i class="fas fa-cog"></i>
            设备检测
          </h2>
          <p class="testing-subtitle">请确保您的设备正常工作，以获得最佳面试体验</p>
        </div>

        <div class="testing-grid">
          <div class="test-section glass-card">
            <div class="test-header">
              <div class="test-icon camera">
                <i class="fas fa-video"></i>
              </div>
              <div class="test-info">
                <h3>摄像头检测</h3>
                <span class="test-status" :class="{ ready: deviceTest.cameraReady }">
                  <i :class="cameraStatusIcon"></i>
                  {{ cameraStatusText }}
                </span>
              </div>
            </div>
            <div class="camera-preview">
              <video ref="testVideoElement" autoplay playsinline muted></video>
              <div v-if="deviceTest.cameraStatus === 'testing'" class="preview-overlay">
                <i class="fas fa-spinner fa-spin"></i>
                <span>正在启动摄像头...</span>
              </div>
            </div>
            <div class="test-actions">
              <button class="test-btn" @click="testCamera" :disabled="deviceTest.cameraStatus === 'testing'">
                <i class="fas fa-redo"></i>
                重新检测
              </button>
            </div>
          </div>

          <div class="test-section glass-card">
            <div class="test-header">
              <div class="test-icon microphone">
                <i class="fas fa-microphone"></i>
              </div>
              <div class="test-info">
                <h3>麦克风检测</h3>
                <span class="test-status" :class="{ ready: deviceTest.microphoneReady }">
                  <i :class="microphoneStatusIcon"></i>
                  {{ microphoneStatusText }}
                </span>
              </div>
            </div>
            <div class="microphone-test">
              <div class="waveform-container">
                <div class="waveform-bars">
                  <div 
                    v-for="i in 20" 
                    :key="i" 
                    class="waveform-bar"
                    :style="{ 
                      height: getWaveformHeight(i) + '%',
                      animationDelay: (i * 0.05) + 's'
                    }"
                  ></div>
                </div>
                <div class="microphone-level-bar">
                  <div class="level-fill" :style="{ width: deviceTest.microphoneLevel + '%' }"></div>
                </div>
              </div>
              <p class="test-hint">请对着麦克风说话，观察波形变化</p>
            </div>
            <div class="test-actions">
              <button class="test-btn" @click="retryMicrophoneTest" :disabled="isMicTesting">
                <i class="fas fa-redo"></i>
                重新检测
              </button>
            </div>
          </div>

          <div class="test-section glass-card">
            <div class="test-header">
              <div class="test-icon speaker">
                <i class="fas fa-volume-up"></i>
              </div>
              <div class="test-info">
                <h3>扬声器检测</h3>
                <span class="test-status" :class="{ ready: deviceTest.speakerReady }">
                  <i :class="deviceTest.speakerReady ? 'fas fa-check-circle' : 'fas fa-circle'"></i>
                  {{ deviceTest.speakerReady ? '正常' : '待测试' }}
                </span>
              </div>
            </div>
            <div class="speaker-test">
              <div class="speaker-visual">
                <div class="speaker-icon-large" :class="{ playing: deviceTest.isPlayingAudio }">
                  <i class="fas fa-volume-up"></i>
                  <div class="sound-waves">
                    <span></span>
                    <span></span>
                    <span></span>
                  </div>
                </div>
              </div>
              <p class="test-hint">点击下方按钮播放测试音频</p>
            </div>
            <div class="test-actions">
              <button class="test-btn" @click="playTestAudio" :disabled="deviceTest.isPlayingAudio">
                <i :class="deviceTest.isPlayingAudio ? 'fas fa-stop' : 'fas fa-play'"></i>
                {{ deviceTest.isPlayingAudio ? '播放中...' : '播放测试音频' }}
              </button>
              <button 
                class="test-btn success" 
                @click="confirmSpeaker" 
                v-if="!deviceTest.speakerReady && !deviceTest.isPlayingAudio"
              >
                <i class="fas fa-check"></i>
                听到了
              </button>
            </div>
          </div>
        </div>

        <div class="testing-summary glass-card">
          <div class="summary-row">
            <div class="summary-item" :class="{ ready: deviceTest.cameraReady }">
              <i class="fas fa-video"></i>
              <span>摄像头</span>
              <i :class="deviceTest.cameraReady ? 'fas fa-check-circle' : 'fas fa-times-circle'" class="status-icon"></i>
            </div>
            <div class="summary-item" :class="{ ready: deviceTest.microphoneReady }">
              <i class="fas fa-microphone"></i>
              <span>麦克风</span>
              <i :class="deviceTest.microphoneReady ? 'fas fa-check-circle' : 'fas fa-times-circle'" class="status-icon"></i>
            </div>
            <div class="summary-item" :class="{ ready: deviceTest.speakerReady }">
              <i class="fas fa-volume-up"></i>
              <span>扬声器</span>
              <i :class="deviceTest.speakerReady ? 'fas fa-check-circle' : 'fas fa-times-circle'" class="status-icon"></i>
            </div>
            <div class="summary-item network-item" :class="networkStatusClass">
              <i class="fas fa-network-wired"></i>
              <span>网络</span>
              <span class="network-value">{{ networkLatencyText }}</span>
              <i :class="networkStatusIcon" class="status-icon"></i>
            </div>
          </div>
          <p class="network-hint" :class="networkStatusClass">{{ networkStatusHint }}</p>
          <p v-if="professionalVoiceBlockedReason" class="voice-requirement-hint">{{ professionalVoiceBlockedReason }}</p>
          <div class="summary-actions">
            <button 
              class="btn btn-primary start-interview-btn" 
              @click="startInterviewAfterTest"
              :disabled="!allDevicesReady"
            >
              <i class="fas fa-rocket"></i>
              开始面试
            </button>
          </div>
        </div>
      </div>
    </div>
    
    <div v-if="isRunning" class="interview-workspace">
      <header class="interview-topbar glass-card">
        <div class="topbar-left">
          <div class="interview-badge">
            <i class="fas fa-briefcase"></i>
            <span>{{ jobDisplayName }}</span>
          </div>
        </div>
        
        <div class="topbar-center">
          <div class="timer-display">
            <i class="fas fa-stopwatch"></i>
            <span class="timer-value">{{ formattedTime }}</span>
          </div>
        </div>
        
        <div class="topbar-right">
          <div class="interview-status" :class="interviewStatus">
            <span class="status-dot"></span>
            <span>{{ statusText }}</span>
          </div>
        </div>
      </header>

      <div class="interview-main">
        <aside class="sidebar-left glass-card" :class="{ collapsed: leftSidebarCollapsed }">
          <button class="sidebar-toggle left" @click="leftSidebarCollapsed = !leftSidebarCollapsed">
            <i :class="leftSidebarCollapsed ? 'fas fa-chevron-right' : 'fas fa-chevron-left'"></i>
          </button>
          
          <div class="sidebar-content" v-show="!leftSidebarCollapsed">
          <div class="actions-section">
            <h4 class="section-title">
              <i class="fas fa-tools"></i>
              操作
            </h4>
            <div class="action-buttons">
              <button class="action-btn end" @click="endInterview">
                <i class="fas fa-stop-circle"></i>
                <span>结束面试</span>
              </button>
            </div>
          </div>
          
          <div v-if="showInputModeToggle" class="input-mode-section">
            <h4 class="section-title">
              <i class="fas fa-keyboard"></i>
              输入方式
            </h4>
            <div class="input-mode-toggle">
              <button 
                class="mode-btn" 
                :class="{ active: inputMode === 'text' }"
                @click="setInputMode('text')"
              >
                <i class="fas fa-keyboard"></i>
                <span>文字</span>
              </button>
              <button 
                class="mode-btn" 
                :class="{ active: inputMode === 'voice' }"
                @click="setInputMode('voice')"
              >
                <i class="fas fa-microphone"></i>
                <span>语音</span>
              </button>
            </div>
          </div>
          </div>
        </aside>

        <main class="chat-area glass-card">
          <div class="chat-messages" ref="chatMessages">
            <div 
              v-for="(msg, index) in messages" 
              :key="index"
              class="message"
              :class="msg.type"
            >
              <div class="message-avatar">
                <i :class="msg.type === 'ai' ? 'fas fa-robot' : 'fas fa-user'"></i>
              </div>
              <div class="message-content">
                <p>{{ msg.content }}</p>
              </div>
            </div>
            <div v-if="isListening" class="message user draft">
              <div class="message-avatar">
                <i class="fas fa-user"></i>
              </div>
              <div class="message-content">
                <p>{{ recognizedText || '正在识别...' }}</p>
                <span class="draft-hint">正在输入...</span>
              </div>
            </div>
          </div>
          
          <div class="chat-input-area">
            <div class="input-row" v-if="showTextInput">
              <div class="input-wrapper">
                <textarea 
                  v-model="answerInput" 
                  class="glass-textarea" 
                  placeholder="请输入你的回答... (Ctrl+Enter 提交)" 
                  rows="3"
                  @keydown.enter.ctrl="submitAnswer"
                ></textarea>
              </div>
              <button 
                class="btn btn-primary submit-btn"
                @click="submitAnswer" 
                :disabled="!answerInput.trim() || isSubmittingAnswer || isWaitingNextQuestion || isFinishing"
              >
                <i class="fas fa-paper-plane"></i>
                提交
              </button>
            </div>
            <div class="voice-input-wrapper" v-else>
              <button 
                class="voice-btn" 
                :class="{ active: isListening, connecting: asrState === 'connecting' }"
                @click="toggleVoiceInput"
                :disabled="isAiSpeaking || asrState === 'connecting' || asrState === 'stopping' || isSubmittingAnswer || isWaitingNextQuestion || isFinishing"
              >
                <i :class="asrState === 'connecting' ? 'fas fa-spinner fa-spin' : 'fas fa-microphone'"></i>
                <span>
                  {{ isAiSpeaking ? '面试官播报中...' : asrState === 'connecting' ? '连接中...' : asrState === 'stopping' ? '处理中...' : isListening ? '说完了' : '点击说话' }}
                </span>
              </button>
              <div v-if="inputMode === 'voice' && (isListening || recognizedText)" class="asr-fallback-hint">
                <i class="fas fa-wave-square"></i>
                <span>系统将于回答结束后自动优化转写结果</span>
              </div>
              <div v-if="voiceFailureMessage" class="voice-error-panel">
                <p class="voice-error-text">{{ voiceFailureMessage }}</p>
                <div class="voice-error-actions">
                  <button class="btn btn-primary" @click="retryVoiceRecognition" :disabled="isListening || isSubmittingAnswer || isWaitingNextQuestion || isFinishing">
                    <i class="fas fa-redo"></i>
                    重试语音
                  </button>
                  <button class="btn btn-secondary" @click="endInterview" :disabled="isFinishing">
                    <i class="fas fa-stop-circle"></i>
                    结束面试
                  </button>
                </div>
              </div>
              <div v-if="lastPauseStats && !isListening" class="pause-stats-hint">
                <i class="fas fa-wave-square"></i>
                <span>语速 {{ lastPauseStats.wpm }} 字/分 · 停顿 {{ lastPauseStats.longPauseCount }} 次</span>
              </div>
            </div>
          </div>
        </main>

        <aside class="sidebar-right glass-card" :class="{ collapsed: rightSidebarCollapsed }">
          <button class="sidebar-toggle right" @click="rightSidebarCollapsed = !rightSidebarCollapsed">
            <i :class="rightSidebarCollapsed ? 'fas fa-chevron-left' : 'fas fa-chevron-right'"></i>
          </button>
          
          <div class="sidebar-content" v-show="!rightSidebarCollapsed">
            <div class="ai-interviewer-section">
            <div class="interviewer-header">
              <h4>AI面试官</h4>
              <label class="toggle-switch">
                <input type="checkbox" v-model="showInterviewer">
                <span class="toggle-slider"></span>
              </label>
            </div>
            <transition name="fade">
              <div v-if="showInterviewer">
                <AiInterviewerAvatar
                  :speaking="isAiSpeaking"
                  :volume-level="aiVoiceLevel"
                  :role-name="jobDisplayName"
                />
              </div>
            </transition>
          </div>
          
          <div class="camera-section">
            <div class="camera-header">
              <h4>我的摄像头</h4>
              <label class="toggle-switch">
                <input type="checkbox" v-model="cameraEnabled" @change="toggleCamera">
                <span class="toggle-slider"></span>
              </label>
            </div>
            <transition name="fade">
              <div v-if="cameraEnabled" class="camera-container">
                <div class="camera-view">
                  <video ref="videoElement" autoplay playsinline muted></video>
                  <div class="camera-overlay">
                    <div class="face-frame"></div>
                  </div>
                </div>
                <div class="camera-controls">
                  <button class="camera-btn" @click="toggleRecording" :class="{ recording: isRecording }">
                    <i :class="isRecording ? 'fas fa-stop' : 'fas fa-circle'"></i>
                    <span>{{ isRecording ? '停止录像' : '开始录像' }}</span>
                  </button>
                </div>
                <div class="analysis-panel">
                  <div class="analysis-item">
                    <i class="fas fa-smile"></i>
                    <span>表情: {{ expressionStatus }}</span>
                  </div>
                  <div class="analysis-item">
                    <i class="fas fa-eye"></i>
                    <span>视线: {{ gazeStatus }}</span>
                  </div>
                </div>
              </div>
              <div v-else class="camera-placeholder">
                <i class="fas fa-video-slash"></i>
                <span>摄像头已关闭</span>
              </div>
            </transition>
          </div>
          </div>
        </aside>
      </div>
    </div>

  </section>
</template>

<script>
import { ref, reactive, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { getJobDisplayName } from '../utils/interview'
import {
  DECISION_TEXT_MAP,
  normalizeInterviewDecision
} from '../utils/interviewDecision'
import {
  normalizeAuthoritativeQuestion,
  resolveAsrQuestionType,
  resolveNextStreamQuestion
} from '../utils/interviewCurrentQuestion'
import {
  buildProfessionalInterviewStartBlockedReason,
  buildVoiceFailureMessage,
  canStartProfessionalInterview,
  normalizeInputMode,
  shouldRenderTextInput,
  shouldShowInputModeToggle
} from '../utils/interviewInputMode'
import { formatLoadingProgress } from '../utils/interviewLoadingProgress'
import { buildPendingInterviewResult } from '../utils/interviewResultState'
import { buildInterviewCreatePayload } from '../utils/interviewSessionPayload'
import {
  getResumes,
  createInterviewSession,
  getInterviewSessionDetail,
  submitInterviewAttempt,
  finishInterviewSession,
  getSystemPing
} from '../api/resume'
import CustomSelect from './CustomSelect.vue'
import AiInterviewerAvatar from './AiInterviewerAvatar.vue'
import TtsPlayerControl from './TtsPlayerControl.vue'
import { asrService } from '../services/AsrService'
import { ttsPlayerService } from '../services/TtsPlayerService'
import { QuestionStreamClient } from '../services/QuestionStreamClient'
import deviceTestToneUrl from '../assets/audio/device-test-tone.wav'

export default {
  name: 'InterviewPage',
  components: { CustomSelect, AiInterviewerAvatar, TtsPlayerControl },
  props: {
    fullscreen: {
      type: Boolean,
      default: false
    }
  },
  emits: ['interviewStart', 'interviewEnd'],
  setup(props, { emit }) {
    const config = reactive({
      jobType: 'frontend',
      totalQuestions: 10,
      mode: 'chat',
      resumeId: 'default',
      companyName: '',
      experience: 'intern',
      salaryMin: '',
      salaryMax: '',
      interviewRound: 'first',
      jobDescription: '',
      interviewMode: 'practice',
      pressure: 'low',
      voiceType: 'random',
      selectedStages: ['technical', 'project'],
      knowledgePoints: []
    })

    const resumeList = ref([])
    const newKnowledgePoint = ref('')
    const showAllJobs = ref(false)
    const isRunning = ref(false)
    const isLoading = ref(false)
    const isDeviceTesting = ref(false)
    const loadingProgress = ref(0)
    const currentStep = ref(0)
    const currentTipIndex = ref(0)
    let loadingTimer = null
    let tipTimer = null

    const deviceTest = reactive({
      cameraReady: false,
      cameraStatus: 'idle', // idle | testing | passed | failed
      microphoneReady: false,
      microphoneStatus: 'idle', // idle | testing | passed | failed
      speakerReady: false,
      microphoneLevel: 0,
      isPlayingAudio: false
    })
    // ==================== 网络检测配置 ====================
    const PING_TIMEOUT_MS = 1000                    // 单次 Ping 请求超时时间（毫秒）
    const NETWORK_POLL_INTERVAL_MS = 5000           // 网络状态轮询间隔（毫秒）
    const NETWORK_STABLE_WINDOW_SIZE = 3            // 网络状态稳定窗口大小，需连续 N 次采样结果一致才判定为稳定
    const NETWORK_PASS_MAX_MS = 200                 // 网络状态"通过"的最大延迟阈值（毫秒），低于此值判定为优秀
    const NETWORK_WARNING_MAX_MS = 500              // 网络状态"警告"的最大延迟阈值（毫秒），200-500ms 为警告，超过 500ms 为失败

    // ==================== 麦克风检测配置 ====================
    const MIC_FRAME_INTERVAL_MS = 50                // 麦克风音频帧采样间隔（毫秒）
    const MIC_REQUIRED_CONSECUTIVE_ACTIVE_FRAMES = 4 // 麦克风激活所需连续有效帧数，连续 N 帧超过阈值才判定为检测通过
    const MIC_BASELINE_FRAMES = 20                  // 麦克风基线采样帧数，用于计算环境噪音基准值
    const MIC_DYNAMIC_THRESHOLD_FACTOR = 1.8        // 麦克风动态阈值因子，阈值 = 基线噪音 × 此因子
    const MIC_MIN_RMS_THRESHOLD = 0.006             // 麦克风最小 RMS 阈值，防止基线噪音过低导致阈值过小
    const MIC_DETECTION_TIMEOUT_MS = 8000           // 麦克风检测超时时间（毫秒），超时后自动结束检测
    const DEBUG_MIC_TEST = true                     // 麦克风检测调试模式开关，开启后控制台输出详细日志
    const micFailReason = ref('')
    const isMicTesting = ref(false)
    const NETWORK_UNSTABLE_HINT = '网络检测失败或结果不稳定，可继续但建议检查网络'
    const networkTest = reactive({
      status: 'testing', // testing | pass | warning | fail
      latencyMs: null,
      hint: '正在检测网络连通性...'
    })
    let networkTestRunId = 0
    let networkPollTimerId = null
    let networkPingInFlight = false
    const networkStableSamples = []
    const testVideoElement = ref(null)
    const cameraTestStream = ref(null)
    const audioContext = ref(null)
    const analyser = ref(null)
    const microphoneStream = ref(null)
    let microphoneSourceNode = null
    let micDetectTimerId = null
    let micDetectTimeoutId = null
    let micTestRunId = 0
    let animationFrameId = null
    const currentQuestion = ref(0)
    const totalQuestions = ref(10)
    const elapsedTime = ref(0)
    const timerInterval = ref(null)
    const messages = ref([])
    const answerInput = ref('')
    const chatMessages = ref(null)
    const questions = ref([])
    const answers = ref([])
    const inputMode = ref(normalizeInputMode(config.interviewMode, 'text'))
    const isListening = ref(false)
    const recognizedText = ref('')
    const lastRawAsrText = ref('')
    const lastAsrCorrectionChanges = ref([])
    const voiceFailureMessage = ref('')
    const showInterviewer = ref(true)
    const cameraEnabled = ref(false)
    const isRecording = ref(false)
    const isAiSpeaking = ref(false)
    const aiVoiceLevel = ref(0)
    const ttsMode = ref('auto') // auto | manual | mute
    // ASR 相关状态
    const asrAvailable = ref(false)
    const asrCapabilityChecked = ref(false)
    const asrState = ref('idle')      // idle | connecting | running | stopping | stopped
    const lastPauseStats = ref(null)  // 最近一次语音作答的停顿统计
    const lastAsrSegments = ref([])   // 最近一次语音作答的 ASR 片段
    const videoElement = ref(null)
    const leftSidebarCollapsed = ref(false)
    const rightSidebarCollapsed = ref(false)
    let mediaStream = null
    let streamAbortController = null
    const backendSessionId = ref(null)
    const isSubmittingAnswer = ref(false)
    const isWaitingNextQuestion = ref(false)
    const isFinishing = ref(false)
    const streamConnectionState = ref('idle') // idle | connecting | streaming | reconnecting | completed | error

    const metrics = reactive({
      speed: '适中',
      speedPercent: 60,
      fluency: '良好',
      fluencyPercent: 75,
      latency: 120,
      latencyPercent: 12
    })

    const jobOptions = [
      { 
        value: 'frontend', 
        label: '前端开发工程师', 
        desc: 'HTML/CSS/JS/Vue/React',
        icon: 'fas fa-code',
        gradient: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)'
      },
      { 
        value: 'backend', 
        label: '后端开发工程师', 
        desc: 'Java/Python/Go/数据库',
        icon: 'fas fa-server',
        gradient: 'linear-gradient(135deg, #f093fb 0%, #f5576c 100%)'
      },
      { 
        value: 'fullstack', 
        label: '全栈开发工程师', 
        desc: '前后端技术全掌握',
        icon: 'fas fa-layer-group',
        gradient: 'linear-gradient(135deg, #4facfe 0%, #00f2fe 100%)'
      },
      { 
        value: 'algorithm', 
        label: '算法工程师', 
        desc: '数据结构与算法',
        icon: 'fas fa-brain',
        gradient: 'linear-gradient(135deg, #43e97b 0%, #38f9d7 100%)'
      },
      { 
        value: 'product', 
        label: '产品经理', 
        desc: '产品设计与管理',
        icon: 'fas fa-lightbulb',
        gradient: 'linear-gradient(135deg, #fa709a 0%, #fee140 100%)'
      }
    ]

    const experienceLevels = [
      { value: 'intern', label: '实习' },
      { value: 'fresh', label: '应届' },
      { value: 'junior', label: '1-3年' },
      { value: 'senior', label: '3-5年' },
      { value: 'expert', label: '5年+' }
    ]

    const interviewRounds = [
      { value: 'first', label: '一面', icon: 'fas fa-user' },
      { value: 'technical', label: '技术面', icon: 'fas fa-code' },
      { value: 'hr', label: 'HR面', icon: 'fas fa-handshake' },
      { value: 'leader', label: '主管面', icon: 'fas fa-user-tie' }
    ]

    const pressureLevels = [
      { value: 'low', label: '轻松' },
      { value: 'medium', label: '适中' },
      { value: 'high', label: '紧张' },
      { value: 'extreme', label: '高压' }
    ]

    const voiceOptions = [
      { value: 'random', label: '随机', icon: 'fas fa-random' },
      { value: 'male', label: '男声', icon: 'fas fa-male' },
      { value: 'female', label: '女声', icon: 'fas fa-female' }
    ]

    const optionalStages = [
      { value: 'selfIntro', label: '自我介绍', icon: 'fas fa-user-circle' },
      { value: 'technical', label: '技术问答', icon: 'fas fa-laptop-code' },
      { value: 'project', label: '项目经历', icon: 'fas fa-project-diagram' },
      { value: 'algorithm', label: '算法题', icon: 'fas fa-code-branch' },
      { value: 'behavior', label: '行为面试', icon: 'fas fa-comments' },
      { value: 'qa', label: '反向提问', icon: 'fas fa-question-circle' }
    ]

    const loadingSteps = [
      '分析岗位要求',
      '生成面试题目',
      '初始化AI面试官',
      '准备面试环境',
      '加载完成'
    ]

    const loadingTips = [
      '面试时保持自信，语速适中，表达清晰',
      '回答问题前可以先思考几秒钟，组织好语言',
      '遇到不会的问题，可以坦诚说明并展示学习态度',
      '适当举例说明能让回答更有说服力',
      '面试结束前可以准备几个有深度的问题询问面试官',
      '注意倾听面试官的问题，确保回答切题',
      '展示你的思考过程比直接给出答案更重要'
    ]

    const loadingTitle = computed(() => {
      const titles = [
        '正在准备面试环境...',
        'AI面试官正在就位...',
        '面试题目生成中...',
        '即将开始面试...'
      ]
      return titles[Math.min(Math.floor(loadingProgress.value / 25), titles.length - 1)]
    })

    const displayLoadingProgress = computed(() => {
      return formatLoadingProgress(loadingProgress.value)
    })

    const loadingSubtitle = computed(() => {
      if (loadingProgress.value < 25) return '正在分析岗位要求，为您定制面试内容'
      if (loadingProgress.value < 50) return '根据您的经验水平生成匹配的面试题目'
      if (loadingProgress.value < 75) return 'AI面试官正在准备面试场景'
      return '一切准备就绪，面试即将开始'
    })

    const currentTip = computed(() => {
      return loadingTips[currentTipIndex.value]
    })

    const currentJob = computed(() => {
      return jobOptions.find(job => job.value === config.jobType) || jobOptions[0]
    })

    const currentRound = computed(() => {
      return interviewRounds.find(round => round.value === config.interviewRound) || interviewRounds[0]
    })

    const otherJobs = computed(() => {
      return jobOptions.filter(job => job.value !== config.jobType)
    })

    const resumeOptions = computed(() =>
      resumeList.value.map(r => ({ value: r.id, label: r.name || `简历 ${r.id}` }))
    )
    const positionOptions = computed(() =>
      jobOptions.map(job => ({ value: job.value, label: job.label }))
    )

    const jobDisplayName = computed(() => getJobDisplayName(config.jobType))

    const formattedTime = computed(() => {
      const minutes = Math.floor(elapsedTime.value / 60)
      const seconds = elapsedTime.value % 60
      return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
    })

    const currentQuestionData = computed(() => {
      return questions.value[currentQuestion.value]
    })

    const interviewStatus = computed(() => {
      if (isWaitingNextQuestion.value && (streamConnectionState.value === 'connecting' || streamConnectionState.value === 'reconnecting')) {
        return 'reconnecting'
      }
      if (isAiSpeaking.value) return 'speaking'
      if (isListening.value) return 'listening'
      return 'waiting'
    })

    const statusText = computed(() => {
      const statusMap = {
        reconnecting: '网络恢复中',
        speaking: 'AI回答中',
        listening: '正在倾听',
        waiting: '等待回答'
      }
      return statusMap[interviewStatus.value]
    })

    const showInputModeToggle = computed(() => shouldShowInputModeToggle(config.interviewMode))
    const showTextInput = computed(() => shouldRenderTextInput(config.interviewMode, inputMode.value))
    const professionalVoiceBlockedReason = computed(() => {
      if (config.interviewMode !== 'professional') {
        return ''
      }
      if (!asrCapabilityChecked.value) {
        return '正在检测语音识别能力...'
      }
      return buildProfessionalInterviewStartBlockedReason({
        microphoneReady: deviceTest.microphoneReady,
        asrAvailable: asrAvailable.value
      })
    })

    const expressionStatus = computed(() => {
      const expressions = ['自然', '紧张', '放松', '专注']
      return expressions[Math.floor(Math.random() * expressions.length)]
    })

    const gazeStatus = computed(() => {
      const gazes = ['正常', '偏左', '偏右', '正视']
      return gazes[Math.floor(Math.random() * gazes.length)]
    })

    const PREPARE_CONFIG_KEY = 'aiInterviewPrepareConfig'

    const loadPrepareConfig = () => {
      try {
        const raw = localStorage.getItem(PREPARE_CONFIG_KEY)
        if (!raw) return
        const data = JSON.parse(raw)
        if (data.jobType) config.jobType = data.jobType
        if (data.experience) config.experience = data.experience
        if (data.jobDescription != null) config.jobDescription = data.jobDescription
        if (data.interviewMode) config.interviewMode = data.interviewMode
        if (Array.isArray(data.knowledgePoints)) config.knowledgePoints = data.knowledgePoints
        if (data.resumeId != null && resumeList.value.some(r => r.id === data.resumeId)) {
          config.resumeId = data.resumeId
        }
        if (data.pressure) config.pressure = data.pressure
        if (data.voiceType) config.voiceType = data.voiceType
      } catch (_) {}
    }

    const savePrepareConfig = () => {
      try {
        localStorage.setItem(PREPARE_CONFIG_KEY, JSON.stringify({
          resumeId: config.resumeId,
          jobType: config.jobType,
          experience: config.experience,
          jobDescription: config.jobDescription,
          interviewMode: config.interviewMode,
          knowledgePoints: config.knowledgePoints,
          pressure: config.pressure,
          voiceType: config.voiceType
        }))
      } catch (_) {}
    }

    const loadResumes = async () => {
      try {
        const list = await getResumes()
        resumeList.value = list || []
      } catch (_) {
        resumeList.value = []
      }
    }

    onMounted(async () => {
      await loadResumes()
      loadPrepareConfig()
      ttsPlayerService.onVolume = (level, speaking) => {
        aiVoiceLevel.value = level
        isAiSpeaking.value = speaking || isAiSpeaking.value
        if (!speaking && ttsMode.value !== 'mute') {
          isAiSpeaking.value = false
        }
      }
      ttsPlayerService.onPlayingChange = (playing) => {
        isAiSpeaking.value = playing
        if (!playing) {
          aiVoiceLevel.value = 0
        }
      }
      // 初始化 ASR：拉取停顿阈值配置，检测 ASR 是否可用
      asrService.init().then(async () => {
        asrAvailable.value = await asrService.isAvailable()
        asrCapabilityChecked.value = true
      }).catch(() => {
        asrAvailable.value = false
        asrCapabilityChecked.value = true
      })
      // 绑定 ASR 回调
      asrService.onInterim = (text) => {
        recognizedText.value = text
      }
      asrService.onFinal = (text, pauseStats, segments, metadata = {}) => {
        recognizedText.value = text
        lastRawAsrText.value = metadata.rawText || text
        lastAsrCorrectionChanges.value = metadata.changeList || []
        lastPauseStats.value = pauseStats
        lastAsrSegments.value = segments
        isListening.value = false
        voiceFailureMessage.value = ''
        // 最终帧到达后自动提交
        if (text.trim()) {
          nextTick(() => submitAnswer())
        }
      }
      asrService.onError = (err) => {
        console.error('[InterviewPage] ASR 错误:', err)
        isListening.value = false
        asrState.value = 'stopped'
        if (inputMode.value === 'voice' || config.interviewMode === 'professional') {
          const message = buildVoiceFailureMessage(config.interviewMode)
          voiceFailureMessage.value = message
          alert(message)
        }
      }
      asrService.onStateChange = (state) => {
        asrState.value = state
      }
    })

    const uploadResume = () => {
      const input = document.createElement('input')
      input.type = 'file'
      input.accept = '.pdf,.doc,.docx'
      input.onchange = (e) => {
        const file = e.target.files[0]
        if (file) {
          config.resumeId = 'uploaded'
          console.log('简历已上传:', file.name)
        }
      }
      input.click()
    }

    const addKnowledgePoint = () => {
      if (newKnowledgePoint.value.trim() && !config.knowledgePoints.includes(newKnowledgePoint.value.trim())) {
        config.knowledgePoints.push(newKnowledgePoint.value.trim())
        newKnowledgePoint.value = ''
      }
    }

    const removeKnowledgePoint = (index) => {
      config.knowledgePoints.splice(index, 1)
    }

    const toggleJobSelector = () => {
      showAllJobs.value = !showAllJobs.value
    }

    const selectJob = (jobType) => {
      config.jobType = jobType
      showAllJobs.value = false
    }

    const setJobType = (jobType) => {
      config.jobType = jobType
    }

    const setFocusTopic = (focusTopic) => {
      if (!focusTopic) {
        config.knowledgePoints = []
        return
      }
      config.knowledgePoints = [focusTopic]
    }

    const setInterviewMode = (mode) => {
      config.interviewMode = mode === 'professional' ? 'professional' : 'practice'
    }

    const startInterview = () => {
      config.totalQuestions = 10
      savePrepareConfig()
      voiceFailureMessage.value = ''

      if (config.interviewMode === 'professional') {
        config.mode = 'voice'
        inputMode.value = normalizeInputMode(config.interviewMode, 'voice')
        isDeviceTesting.value = true
        startDeviceTest()
      } else {
        config.mode = 'chat'
        inputMode.value = normalizeInputMode(config.interviewMode, 'text')
        startLoadingPhase()
      }
    }

    const startLoadingPhase = () => {
      isLoading.value = true
      loadingProgress.value = 0
      currentStep.value = 0
      currentTipIndex.value = 0
      startLoadingAnimation()
      startTipRotation()
      startInterviewDirectly().catch((err) => {
        console.error('[InterviewPage] failed to start interview', err)
        failLoading()
        alert(err?.message || '启动面试失败，请重试。')
      })
    }

    const startLoadingAnimation = () => {
      if (loadingTimer) clearInterval(loadingTimer)
      loadingTimer = setInterval(() => {
        loadingProgress.value = Math.min(loadingProgress.value + 1.2, 92)

        const newStep = Math.floor(loadingProgress.value / (100 / loadingSteps.length))
        if (newStep !== currentStep.value && newStep < loadingSteps.length) {
          currentStep.value = newStep
        }
      }, 80)
    }

    const startTipRotation = () => {
      if (tipTimer) clearInterval(tipTimer)
      tipTimer = setInterval(() => {
        currentTipIndex.value = (currentTipIndex.value + 1) % loadingTips.length
      }, 3000)
    }

    const finishLoading = () => {
      loadingProgress.value = 100
      currentStep.value = loadingSteps.length - 1
      if (loadingTimer) {
        clearInterval(loadingTimer)
        loadingTimer = null
      }
      if (tipTimer) {
        clearInterval(tipTimer)
        tipTimer = null
      }
      isLoading.value = false
    }

    const failLoading = () => {
      if (loadingTimer) {
        clearInterval(loadingTimer)
        loadingTimer = null
      }
      if (tipTimer) {
        clearInterval(tipTimer)
        tipTimer = null
      }
      isLoading.value = false
    }

    const startInterviewDirectly = async () => {
      totalQuestions.value = config.totalQuestions
      currentQuestion.value = 0
      answers.value = []
      messages.value = []
      elapsedTime.value = 0
      answerInput.value = ''
      recognizedText.value = ''
      voiceFailureMessage.value = ''
      backendSessionId.value = null
      isSubmittingAnswer.value = false
      isWaitingNextQuestion.value = false
      streamConnectionState.value = 'idle'
      isFinishing.value = false

      if (streamAbortController) {
        streamAbortController.abort()
        streamAbortController = null
      }

      const firstQuestion = await createAndWaitFirstQuestion()
      questions.value = [firstQuestion]

      isRunning.value = true
      if (isLoading.value) {
        finishLoading()
      }
      startTimer()
      sendWelcomeMessage()
      emit('interviewStart')
    }

    const allDevicesReady = computed(() => canStartProfessionalInterview({
      microphoneReady: deviceTest.microphoneReady,
      asrAvailable: asrCapabilityChecked.value && asrAvailable.value
    }))

    const cameraStatusText = computed(() => {
      if (deviceTest.cameraStatus === 'failed') return '未通过'
      if (deviceTest.cameraReady) return '正常'
      if (deviceTest.cameraStatus === 'idle') return '待检测'
      return '检测中'
    })

    const cameraStatusIcon = computed(() => {
      if (deviceTest.cameraStatus === 'failed') return 'fas fa-times-circle'
      if (deviceTest.cameraReady) return 'fas fa-check-circle'
      if (deviceTest.cameraStatus === 'idle') return 'fas fa-circle'
      return 'fas fa-spinner fa-spin'
    })

    const microphoneStatusText = computed(() => {
      if (deviceTest.microphoneStatus === 'failed') {
        const textMap = {
          permission_denied: '请允许浏览器使用麦克风',
          no_input: '未检测到输入：请对麦克风说话后重试',
          device_unavailable: '设备不可用/被占用：请检查系统输入设备',
          timeout: '超时：未检测到明显语音输入，请重试',
          resume_failed: '麦克风启动失败，请重试',
          unknown: '麦克风启动失败，请重试'
        }
        return textMap[micFailReason.value] || textMap.unknown
      }
      if (deviceTest.microphoneReady) return '正常'
      if (deviceTest.microphoneStatus === 'idle') return '待检测'
      return '检测中'
    })

    const microphoneStatusIcon = computed(() => {
      if (deviceTest.microphoneStatus === 'failed') return 'fas fa-times-circle'
      if (deviceTest.microphoneReady) return 'fas fa-check-circle'
      if (deviceTest.microphoneStatus === 'idle') return 'fas fa-circle'
      return 'fas fa-spinner fa-spin'
    })

    const logMicDebug = (event, payload = {}) => {
      if (!DEBUG_MIC_TEST) return
      console.log('[MicTest]', event, payload)
    }

    const classifyMicError = (err) => {
      const name = String(err?.name || '')
      if (name === 'NotAllowedError' || name === 'SecurityError' || name === 'PermissionDeniedError') {
        return 'permission_denied'
      }
      if (name === 'NotFoundError' || name === 'NotReadableError' || name === 'OverconstrainedError' || name === 'AbortError' || name === 'TrackStartError') {
        return 'device_unavailable'
      }
      return 'unknown'
    }

    const networkStatusClass = computed(() => {
      const map = {
        testing: 'network-testing',
        pass: 'network-pass',
        warning: 'network-warning',
        fail: 'network-fail'
      }
      return map[networkTest.status] || 'network-warning'
    })

    const networkStatusIcon = computed(() => {
      const map = {
        testing: 'fas fa-spinner fa-spin',
        pass: 'fas fa-check-circle',
        warning: 'fas fa-exclamation-circle',
        fail: 'fas fa-times-circle'
      }
      return map[networkTest.status] || 'fas fa-exclamation-circle'
    })

    const networkLatencyText = computed(() => {
      if (Number.isFinite(networkTest.latencyMs) && networkTest.latencyMs >= 0) {
        return `${networkTest.latencyMs}ms`
      }
      if (networkTest.status === 'testing') return '检测中'
      return '--'
    })

    const networkStatusHint = computed(() => networkTest.hint || '')

    const cleanupCameraResources = () => {
      if (cameraTestStream.value) {
        cameraTestStream.value.getTracks().forEach(track => track.stop())
        cameraTestStream.value = null
      }
      if (testVideoElement.value) {
        testVideoElement.value.srcObject = null
      }
    }

    const cleanupMicrophoneResources = () => {
      if (micDetectTimerId) {
        clearInterval(micDetectTimerId)
        micDetectTimerId = null
      }
      if (micDetectTimeoutId) {
        clearTimeout(micDetectTimeoutId)
        micDetectTimeoutId = null
      }
      if (animationFrameId) {
        cancelAnimationFrame(animationFrameId)
        animationFrameId = null
      }
      if (microphoneSourceNode) {
        try {
          microphoneSourceNode.disconnect()
        } catch (_) {}
        microphoneSourceNode = null
      }
      if (analyser.value) {
        try {
          analyser.value.disconnect()
        } catch (_) {}
        analyser.value = null
      }
      if (microphoneStream.value) {
        microphoneStream.value.getTracks().forEach(track => track.stop())
        microphoneStream.value = null
      }
      if (audioContext.value) {
        audioContext.value.close().catch(() => {})
        audioContext.value = null
      }
      isMicTesting.value = false
      deviceTest.microphoneLevel = 0
    }

    const stopNetworkPolling = () => {
      if (networkPollTimerId) {
        clearInterval(networkPollTimerId)
        networkPollTimerId = null
      }
      networkPingInFlight = false
      networkTestRunId += 1
    }

    const cleanupDeviceTestResources = () => {
      stopNetworkPolling()
      micTestRunId += 1
      cleanupMicrophoneResources()
      cleanupCameraResources()
      ttsPlayerService.skip()
      deviceTest.isPlayingAudio = false
    }

    const startNetworkPolling = () => {
      stopNetworkPolling()
      void testNetworkLatency()
      networkPollTimerId = setInterval(() => {
        void testNetworkLatency()
      }, NETWORK_POLL_INTERVAL_MS)
    }

    const startDeviceTest = async () => {
      cleanupDeviceTestResources()
      deviceTest.cameraReady = false
      deviceTest.cameraStatus = 'testing'
      deviceTest.microphoneReady = false
      deviceTest.microphoneStatus = 'testing'
      micFailReason.value = ''
      isMicTesting.value = true
      deviceTest.speakerReady = false
      deviceTest.isPlayingAudio = false
      deviceTest.microphoneLevel = 0
      networkTest.status = 'testing'
      networkTest.latencyMs = null
      networkTest.hint = '正在检测网络连通性...'
      networkStableSamples.length = 0
      startNetworkPolling()
      await Promise.allSettled([
        testCamera(),
        testMicrophone({ userInitiated: false })
      ])
    }

    const classifyNetworkStatus = (latencyMs) => {
      if (latencyMs < NETWORK_PASS_MAX_MS) return 'pass'
      if (latencyMs <= NETWORK_WARNING_MAX_MS) return 'warning'
      return 'fail'
    }

    const computeMedian = (samples) => {
      const sorted = [...samples].sort((a, b) => a - b)
      const mid = Math.floor(sorted.length / 2)
      if (sorted.length % 2 === 0) {
        return Math.round((sorted[mid - 1] + sorted[mid]) / 2)
      }
      return sorted[mid]
    }

    const runPingSample = async (timeoutMs = PING_TIMEOUT_MS) => {
      const controller = new AbortController()
      const timeoutId = setTimeout(() => controller.abort(), timeoutMs)
      const startAt = performance.now()
      try {
        const response = await getSystemPing(controller.signal)
        if (!response.ok) {
          throw new Error(`ping failed with status ${response.status}`)
        }
        return Math.max(0, Math.round(performance.now() - startAt))
      } finally {
        clearTimeout(timeoutId)
      }
    }

    const testNetworkLatency = async () => {
      if (networkPingInFlight) return
      networkPingInFlight = true
      const runId = ++networkTestRunId
      const hasStableLatency = Number.isFinite(networkTest.latencyMs) && networkTest.latencyMs >= 0
      const shouldDeferUiUpdates = isMicTesting.value && hasStableLatency
      if (!hasStableLatency) {
        networkTest.status = 'testing'
        networkTest.hint = '正在检测网络连通性...'
      }

      try {
        // Warm-up request: amortize first-connection overhead, excluded from stats.
        await runPingSample(PING_TIMEOUT_MS).catch(() => null)

        const settled = await Promise.allSettled([
          runPingSample(PING_TIMEOUT_MS),
          runPingSample(PING_TIMEOUT_MS),
          runPingSample(PING_TIMEOUT_MS)
        ])
        if (runId !== networkTestRunId) return

        const successSamples = settled
          .filter((item) => item.status === 'fulfilled')
          .map((item) => item.value)
          .filter((value) => Number.isFinite(value) && value >= 0)

        if (successSamples.length >= 2) {
          const currentLatencyMs = computeMedian(successSamples)
          networkStableSamples.push(currentLatencyMs)
          if (networkStableSamples.length > NETWORK_STABLE_WINDOW_SIZE) {
            networkStableSamples.shift()
          }
          const stableLatencyMs = computeMedian(networkStableSamples)
          if (shouldDeferUiUpdates) {
            return
          }
          const status = classifyNetworkStatus(stableLatencyMs)
          networkTest.latencyMs = stableLatencyMs
          networkTest.status = status
          networkTest.hint = status === 'pass'
            ? `网络延迟 ${stableLatencyMs}ms，连接稳定`
            : NETWORK_UNSTABLE_HINT
          return
        }

        if (successSamples.length === 1) {
          networkStableSamples.push(successSamples[0])
          if (networkStableSamples.length > NETWORK_STABLE_WINDOW_SIZE) {
            networkStableSamples.shift()
          }
          if (shouldDeferUiUpdates) {
            return
          }
          const stableLatencyMs = computeMedian(networkStableSamples)
          networkTest.latencyMs = stableLatencyMs
          networkTest.status = 'warning'
          networkTest.hint = NETWORK_UNSTABLE_HINT
          return
        }

        if (Number.isFinite(networkTest.latencyMs) && networkTest.latencyMs >= 0) {
          networkTest.status = 'warning'
          networkTest.hint = `网络刷新失败，当前展示最近稳定延迟 ${networkTest.latencyMs}ms`
          return
        }
        networkTest.latencyMs = null
        networkTest.status = 'warning'
        networkTest.hint = NETWORK_UNSTABLE_HINT
      } catch (err) {
        if (runId !== networkTestRunId) return
        console.warn('[InterviewPage] 网络检测失败，降级为 warning', err)
        if (Number.isFinite(networkTest.latencyMs) && networkTest.latencyMs >= 0) {
          networkTest.status = 'warning'
          networkTest.hint = `网络刷新失败，当前展示最近稳定延迟 ${networkTest.latencyMs}ms`
          return
        }
        networkTest.latencyMs = null
        networkTest.status = 'warning'
        networkTest.hint = NETWORK_UNSTABLE_HINT
      } finally {
        networkPingInFlight = false
      }
    }

    const testCamera = async () => {
      cleanupCameraResources()
      deviceTest.cameraReady = false
      deviceTest.cameraStatus = 'testing'
      try {
        const stream = await navigator.mediaDevices.getUserMedia({ video: true })
        cameraTestStream.value = stream
        const hasLiveTrack = stream.getVideoTracks().some(track => track.readyState === 'live')
        if (!hasLiveTrack) {
          throw new Error('摄像头轨道未处于 live 状态')
        }

        if (testVideoElement.value) {
          testVideoElement.value.srcObject = stream
          await new Promise((resolve, reject) => {
            const videoEl = testVideoElement.value
            if (!videoEl) {
              resolve()
              return
            }
            if (videoEl.readyState >= 1) {
              resolve()
              return
            }
            const timeoutId = setTimeout(() => {
              reject(new Error('摄像头元数据加载超时'))
            }, 2000)
            const onLoaded = () => {
              clearTimeout(timeoutId)
              resolve()
            }
            videoEl.addEventListener('loadedmetadata', onLoaded, { once: true })
          })
        }

        deviceTest.cameraReady = true
        deviceTest.cameraStatus = 'passed'
      } catch (err) {
        console.error('摄像头检测失败:', err)
        deviceTest.cameraReady = false
        deviceTest.cameraStatus = 'failed'
      }
    }

    const failMicrophoneTest = (runId, reason, details = {}) => {
      if (runId !== micTestRunId) return
      deviceTest.microphoneReady = false
      deviceTest.microphoneStatus = 'failed'
      micFailReason.value = reason || 'unknown'
      logMicDebug('result', { runId, result: 'failed', failReason: micFailReason.value, ...details })
      cleanupMicrophoneResources()
    }

    const passMicrophoneTest = (runId, details = {}) => {
      if (runId !== micTestRunId) return
      deviceTest.microphoneReady = true
      deviceTest.microphoneStatus = 'passed'
      micFailReason.value = ''
      logMicDebug('result', { runId, result: 'passed', ...details })
      cleanupMicrophoneResources()
    }

    const ensureMicAudioContextRunning = async (runId) => {
      if (!audioContext.value) {
        audioContext.value = new (window.AudioContext || window.webkitAudioContext)()
      }
      try {
        await audioContext.value.resume()
      } catch (err) {
        logMicDebug('resume-error', { runId, name: err?.name || '', message: err?.message || '' })
      }
      return audioContext.value?.state === 'running'
    }

    const testMicrophone = async ({ userInitiated = false } = {}) => {
      const runId = ++micTestRunId
      cleanupMicrophoneResources()
      deviceTest.microphoneReady = false
      deviceTest.microphoneStatus = 'testing'
      deviceTest.microphoneLevel = 0
      micFailReason.value = ''
      isMicTesting.value = true
      logMicDebug('start', { runId, userInitiated })

      try {
        if (userInitiated) {
          const resumedEarly = await ensureMicAudioContextRunning(runId)
          if (!resumedEarly) {
            failMicrophoneTest(runId, 'resume_failed')
            return
          }
        }

        microphoneStream.value = await navigator.mediaDevices.getUserMedia({
          audio: {
            echoCancellation: true,
            noiseSuppression: true,
            autoGainControl: true
          }
        })
        if (runId !== micTestRunId) {
          microphoneStream.value.getTracks().forEach(track => track.stop())
          microphoneStream.value = null
          return
        }

        const resumed = await ensureMicAudioContextRunning(runId)
        if (!resumed) {
          failMicrophoneTest(runId, 'resume_failed')
          return
        }

        analyser.value = audioContext.value.createAnalyser()
        analyser.value.fftSize = 1024
        analyser.value.smoothingTimeConstant = 0.2
        microphoneSourceNode = audioContext.value.createMediaStreamSource(microphoneStream.value)
        microphoneSourceNode.connect(analyser.value)
        updateMicrophoneLevel()

        const timeData = new Uint8Array(analyser.value.fftSize)
        let baselineAccumulator = 0
        let baselineCount = 0
        let consecutiveActiveFrames = 0
        let windowMaxRms = 0
        let sawActiveInput = false

        micDetectTimerId = setInterval(() => {
          if (runId !== micTestRunId || deviceTest.microphoneStatus !== 'testing' || !analyser.value) return

          analyser.value.getByteTimeDomainData(timeData)
          let sumSquares = 0
          for (let i = 0; i < timeData.length; i++) {
            const normalized = (timeData[i] - 128) / 128
            sumSquares += normalized * normalized
          }
          const rms = Math.sqrt(sumSquares / timeData.length)
          windowMaxRms = Math.max(windowMaxRms, rms)

          if (baselineCount < MIC_BASELINE_FRAMES) {
            baselineAccumulator += rms
            baselineCount += 1
            logMicDebug('sample', {
              runId,
              rms: Number(rms.toFixed(5)),
              windowMaxRms: Number(windowMaxRms.toFixed(5)),
              activeStreak: consecutiveActiveFrames
            })
            return
          }

          const baseline = baselineCount > 0 ? baselineAccumulator / baselineCount : 0
          const dynamicThreshold = Math.max(
            MIC_MIN_RMS_THRESHOLD,
            Math.min(0.03, baseline * MIC_DYNAMIC_THRESHOLD_FACTOR)
          )

          if (rms >= dynamicThreshold) {
            sawActiveInput = true
            consecutiveActiveFrames += 1
          } else {
            consecutiveActiveFrames = 0
          }

          logMicDebug('sample', {
            runId,
            rms: Number(rms.toFixed(5)),
            windowMaxRms: Number(windowMaxRms.toFixed(5)),
            activeStreak: consecutiveActiveFrames
          })

          if (consecutiveActiveFrames >= MIC_REQUIRED_CONSECUTIVE_ACTIVE_FRAMES) {
            passMicrophoneTest(runId, {
              windowMaxRms: Number(windowMaxRms.toFixed(5)),
              activeStreak: consecutiveActiveFrames
            })
          }
        }, MIC_FRAME_INTERVAL_MS)

        micDetectTimeoutId = setTimeout(() => {
          if (runId !== micTestRunId || deviceTest.microphoneStatus !== 'testing') return
          failMicrophoneTest(runId, sawActiveInput ? 'timeout' : 'no_input', {
            windowMaxRms: Number(windowMaxRms.toFixed(5)),
            activeStreak: consecutiveActiveFrames
          })
        }, MIC_DETECTION_TIMEOUT_MS)
      } catch (err) {
        logMicDebug('error', {
          runId,
          name: err?.name || '',
          message: err?.message || ''
        })
        failMicrophoneTest(runId, classifyMicError(err), {
          errName: err?.name || '',
          errMessage: err?.message || ''
        })
      }
    }

    const retryMicrophoneTest = () => {
      void testMicrophone({ userInitiated: true })
    }

    const updateMicrophoneLevel = () => {
      if (!analyser.value) return

      const timeData = new Uint8Array(analyser.value.fftSize)
      analyser.value.getByteTimeDomainData(timeData)

      let sumSquares = 0
      for (let i = 0; i < timeData.length; i++) {
        const normalized = (timeData[i] - 128) / 128
        sumSquares += normalized * normalized
      }
      const rms = Math.sqrt(sumSquares / timeData.length)
      deviceTest.microphoneLevel = Math.max(0, Math.min(100, Math.round((rms / 0.08) * 100)))

      animationFrameId = requestAnimationFrame(updateMicrophoneLevel)
    }

    const getWaveformHeight = (index) => {
      const baseHeight = 20
      const maxVariation = 60
      const centerIndex = 10
      const distance = Math.abs(index - centerIndex)
      const centerWeight = 1 - (distance / centerIndex) * 0.5
      const randomFactor = Math.sin(Date.now() / 100 + index) * 0.5 + 0.5
      const levelFactor = deviceTest.microphoneLevel / 100
      
      return baseHeight + maxVariation * centerWeight * randomFactor * levelFactor
    }

    const playTestAudio = async () => {
      await ttsPlayerService.unlockByUserGesture()
      deviceTest.isPlayingAudio = true
      try {
        await ttsPlayerService.playLocalTestAudio(deviceTestToneUrl)
      } catch (err) {
        console.warn('[InterviewPage] 播放测试音频失败', err)
      } finally {
        deviceTest.isPlayingAudio = false
      }
    }

    const confirmSpeaker = () => {
      deviceTest.speakerReady = true
    }

    const backToConfig = () => {
      stopDeviceTest()
      isDeviceTesting.value = false
      deviceTest.cameraStatus = 'idle'
      deviceTest.microphoneStatus = 'idle'
      voiceFailureMessage.value = ''
    }

    const startInterviewAfterTest = async () => {
      if (!allDevicesReady.value) {
        alert(professionalVoiceBlockedReason.value || '专业模式仅支持语音输入，请完成语音能力检测后重试。')
        return
      }
      await ttsPlayerService.unlockByUserGesture()
      stopDeviceTest()
      isDeviceTesting.value = false
      startLoadingPhase()
    }

    const stopDeviceTest = () => {
      cleanupDeviceTestResources()
    }

    const ROLE_MAP = {
      frontend: 'FRONTEND',
      backend: 'JAVA_BACKEND',
      fullstack: 'FRONTEND',
      algorithm: 'DATA_ENGINEER',
      product: 'QA'
    }

    const EXPERIENCE_MAP = {
      intern: 'INTERN',
      fresh: 'FRESH_GRAD',
      junior: 'JUNIOR',
      senior: 'MIDDLE',
      expert: 'SENIOR'
    }

    const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

    const isAbortLikeError = (err) => {
      if (!err) return false
      if (err.name === 'AbortError') return true
      const message = String(err.message || '').toLowerCase()
      return message.includes('aborted')
    }

    const buildCreateInterviewPayloadForCurrentState = () => buildInterviewCreatePayload({
      config,
      roleMap: ROLE_MAP,
      experienceMap: EXPERIENCE_MAP
    })

    const fetchAuthoritativeCurrentQuestion = async (fallbackStem = '') => {
      if (!backendSessionId.value) {
        throw new Error('缺少会话上下文，无法获取当前题')
      }
      const detail = await getInterviewSessionDetail(backendSessionId.value)
      return resolveNextStreamQuestion({
        sessionDetail: detail,
        fallbackStem
      })
    }

    const ensureCurrentQuestionForAsr = async () => {
      const currentQ = questions.value[currentQuestion.value]
      try {
        resolveAsrQuestionType(currentQ)
        return currentQ
      } catch (error) {
        const authoritativeQuestion = await fetchAuthoritativeCurrentQuestion()
        if (currentQuestion.value >= 0 && currentQuestion.value < questions.value.length) {
          questions.value.splice(currentQuestion.value, 1, authoritativeQuestion)
        } else {
          questions.value.push(authoritativeQuestion)
          currentQuestion.value = questions.value.length - 1
        }
        return authoritativeQuestion
      }
    }

    const createAndWaitFirstQuestion = async () => {
      const createResp = await createInterviewSession(buildCreateInterviewPayloadForCurrentState())
      const sessionId = createResp?.sessionId
      if (!sessionId) {
        throw new Error('创建面试会话失败：缺少 sessionId')
      }
      backendSessionId.value = sessionId

      const detail = await pollSessionUntilReady(sessionId)
      return normalizeAuthoritativeQuestion(detail?.currentQuestion)
    }

    const pollSessionUntilReady = async (sessionId, timeoutMs = 90000, intervalMs = 1500) => {
      const startAt = Date.now()
      while (Date.now() - startAt < timeoutMs) {
        const detail = await getInterviewSessionDetail(sessionId)
        const status = detail?.status

        if (status === 'in_progress' && detail?.currentQuestion?.questionId) {
          return detail
        }
        if (status === 'aborted') {
          throw new Error('面试会话已中止')
        }

        loadingProgress.value = Math.min(95, loadingProgress.value + 1)
        await sleep(intervalMs)
      }
      throw new Error('面试准备超时，请重试')
    }

    const buildAttemptId = () => {
      if (globalThis.crypto?.randomUUID) {
        return globalThis.crypto.randomUUID()
      }
      return Date.now() + '-' + Math.random().toString(36).slice(2, 10)
    }

    const streamNextQuestion = async (streamAttemptId) => {
      if (!backendSessionId.value) {
        throw new Error('缺少会话上下文，无法流式生成下一题')
      }

      if (streamAbortController) {
        streamAbortController.abort()
      }
      streamAbortController = new AbortController()

      isWaitingNextQuestion.value = true
      isAiSpeaking.value = true
      streamConnectionState.value = 'connecting'

      messages.value.push({
        type: 'ai',
        content: ''
      })
      const streamMessageIndex = messages.value.length - 1

      let streamedStem = ''
      let donePayload = null

      try {
        const streamClient = new QuestionStreamClient({ maxReconnectDurationMs: 60000 })
        await streamClient.consume(
          backendSessionId.value,
          streamAttemptId,
          {
            onStateChange: (state) => {
              streamConnectionState.value = state
            },
            onReconnect: ({ attempt, delayMs, error }) => {
              console.warn('[InterviewPage] SSE重连中', { attempt, delayMs, error: error?.message || null })
            },
            onStart: (payload) => {
              ttsPlayerService.beginGeneration(payload?.generationId || streamAttemptId)
            },
            onDelta: async (payload) => {
              const delta = payload?.text || ''
              streamedStem += delta
              messages.value[streamMessageIndex].content = streamedStem || '...'
              await scrollToBottom()
            },
            onTtsReady: (payload) => {
              // 不阻塞主 SSE 消费链路，避免 done 事件被 TTS 播放耗时卡住
              ttsPlayerService.handleTtsReadyEvent(payload).catch((err) => {
                console.warn('[InterviewPage] 处理 tts_ready 事件失败，降级为文本模式', err)
              })
            },
            onDone: (payload) => {
              donePayload = payload || null
            },
            onError: (payload) => {
              throw new Error(payload?.message || '流式生成下一题失败')
            }
          },
          streamAbortController.signal
        )

        const finalStem = streamedStem.trim()
        if (!finalStem) {
          throw new Error('未收到下一题文本，请重试')
        }

        messages.value[streamMessageIndex].content = finalStem
        let nextQuestion
        try {
          nextQuestion = resolveNextStreamQuestion({
            donePayload,
            fallbackStem: finalStem
          })
        } catch (firstError) {
          console.warn('[InterviewPage] done 事件缺少权威题目快照，尝试回拉当前题', {
            error: firstError?.message || firstError,
            donePayload
          })
          nextQuestion = await fetchAuthoritativeCurrentQuestion(finalStem)
        }

        const currentQuestionId = Number(questions.value[currentQuestion.value]?.questionId)
        if (Number.isInteger(currentQuestionId) && currentQuestionId > 0 && nextQuestion.questionId === currentQuestionId) {
          throw new Error('后端未返回新的当前题，请刷新页面后重试')
        }

        questions.value.push(nextQuestion)
        currentQuestion.value = questions.value.length - 1
      } finally {
        if (streamAbortController) {
          streamAbortController = null
        }
        isWaitingNextQuestion.value = false
        isAiSpeaking.value = false
        streamConnectionState.value = 'idle'
      }
    }

    const startTimer = () => {
      const startTime = Date.now()
      timerInterval.value = setInterval(() => {
        elapsedTime.value = Math.floor((Date.now() - startTime) / 1000)
        updateMetrics()
      }, 1000)
    }

    const stopTimer = () => {
      if (timerInterval.value) {
        clearInterval(timerInterval.value)
        timerInterval.value = null
      }
    }

    const updateMetrics = () => {
      metrics.speedPercent = 50 + Math.random() * 30
      metrics.fluencyPercent = 60 + Math.random() * 25
      if (Number.isFinite(networkTest.latencyMs) && networkTest.latencyMs >= 0) {
        metrics.latency = networkTest.latencyMs
      }
      metrics.latencyPercent = Math.min(metrics.latency / 5, 100)
    }

    const sendWelcomeMessage = () => {
      isAiSpeaking.value = true
      const firstQuestion = questions.value[0]
      if (firstQuestion) {
        messages.value.push({
          type: 'ai',
          content: firstQuestion.question
        })
      }
      setTimeout(() => {
        isAiSpeaking.value = false
      }, 600)
      scrollToBottom()
    }

    const toggleVoiceInput = async () => {
      if (isAiSpeaking.value) {
        console.info('[InterviewPage] 已阻止语音输入：当前仍在播报题目')
        return
      }

      inputMode.value = normalizeInputMode(config.interviewMode, 'voice')

      if (!isListening.value) {
        // ── 开始录音 ──
        recognizedText.value = ''
        lastPauseStats.value = null
        lastAsrSegments.value = []
        lastRawAsrText.value = ''
        lastAsrCorrectionChanges.value = []
        voiceFailureMessage.value = ''

        if (!asrAvailable.value) {
          const message = buildVoiceFailureMessage(config.interviewMode)
          voiceFailureMessage.value = message
          alert(message)
          return
        }

        let currentQ
        try {
          currentQ = await ensureCurrentQuestionForAsr()
        } catch (error) {
          const message = error?.message || '当前题目元数据缺失，无法启动语音识别。'
          voiceFailureMessage.value = message
          alert(message)
          return
        }
        const questionType = resolveAsrQuestionType(currentQ)
        try {
          isListening.value = true
          await asrService.start(questionType, {
            roleHint: config.jobType || '',
            questionText: currentQ?.question || currentQ?.stem || '',
            jobDescription: config.jobDescription || '',
          })
        } catch (error) {
          console.error('[InterviewPage] 启动语音识别失败', error)
          isListening.value = false
          asrState.value = 'stopped'
          const message = buildVoiceFailureMessage(config.interviewMode)
          voiceFailureMessage.value = message
          alert(message)
        }
      } else {
        // ── 停止录音 ──
        isListening.value = false
        try {
          await asrService.stop()
        } catch (error) {
          console.warn('[InterviewPage] 停止语音识别失败', error)
        }
      }
    }

    const setInputMode = (mode) => {
      const nextMode = normalizeInputMode(config.interviewMode, mode)
      if (config.interviewMode === 'professional' && nextMode !== mode) {
        inputMode.value = nextMode
        alert('专业模式仅支持语音输入。')
        return
      }
      if (mode === 'text' && isListening.value) {
        isListening.value = false
        asrService.stop().catch(() => {})
        answerInput.value = recognizedText.value
      }
      inputMode.value = nextMode
      voiceFailureMessage.value = ''
    }

    const retryVoiceRecognition = async () => {
      voiceFailureMessage.value = ''
      await toggleVoiceInput()
    }

    const toggleCamera = async () => {
      if (cameraEnabled.value) {
        try {
          mediaStream = await navigator.mediaDevices.getUserMedia({ video: true })
          if (videoElement.value) {
            videoElement.value.srcObject = mediaStream
          }
        } catch (err) {
          console.error('无法访问摄像头:', err)
          cameraEnabled.value = false
        }
      } else {
        if (mediaStream) {
          mediaStream.getTracks().forEach(track => track.stop())
          mediaStream = null
        }
      }
    }

    const toggleRecording = () => {
      isRecording.value = !isRecording.value
    }

    const submitAnswer = async () => {
      const normalizedMode = normalizeInputMode(config.interviewMode, inputMode.value)
      if (inputMode.value !== normalizedMode) {
        inputMode.value = normalizedMode
      }
      if (config.interviewMode === 'professional' && normalizedMode !== 'voice') {
        alert('专业模式仅支持语音输入，请使用语音作答。')
        return
      }

      const text = normalizedMode === 'voice' ? recognizedText.value : answerInput.value
      const answer = text.trim()
      if (!answer || isSubmittingAnswer.value || isWaitingNextQuestion.value || isFinishing.value) {
        console.info('[InterviewPage] submit blocked', {
          hasAnswer: Boolean(answer),
          isSubmittingAnswer: isSubmittingAnswer.value,
          isWaitingNextQuestion: isWaitingNextQuestion.value,
          isFinishing: isFinishing.value,
          streamConnectionState: streamConnectionState.value,
          currentQuestionIndex: currentQuestion.value
        })
        return
      }

      // 用户主动提交时，立即打断当前题目播报，避免“旧题语音残留到下一题”。
      ttsPlayerService.skip()
      await submitAnswerText(answer)
    }

    const submitAnswerText = async (answer) => {
      const question = questions.value[currentQuestion.value]
      if (!question) return
      const questionId = Number(question.questionId)
      if (!Number.isInteger(questionId) || questionId <= 0) {
        alert('当前题目尚未完全就绪，请稍后重试。')
        return
      }

      const currentPauseStats = inputMode.value === 'voice' ? lastPauseStats.value : null
      const currentAsrSegments = inputMode.value === 'voice' ? lastAsrSegments.value : []
      const currentRawAsrText = inputMode.value === 'voice' ? lastRawAsrText.value : null
      const currentAsrCorrectionChanges = inputMode.value === 'voice' ? lastAsrCorrectionChanges.value : []

      messages.value.push({
        type: 'user',
        content: answer
      })
      answerInput.value = ''
      recognizedText.value = ''
      lastPauseStats.value = null
      lastAsrSegments.value = []
      lastRawAsrText.value = ''
      lastAsrCorrectionChanges.value = []
      voiceFailureMessage.value = ''

      isSubmittingAnswer.value = true
      try {
        if (!backendSessionId.value) {
          throw new Error('面试会话尚未初始化')
        }

        const attemptId = buildAttemptId()
        const resp = await submitInterviewAttempt(backendSessionId.value, {
          questionId,
          attemptId,
          answerText: answer,
          rawAsrText: currentRawAsrText,
          asrCorrectionChanges: currentAsrCorrectionChanges,
          isFinal: true,
          pauseStats: currentPauseStats,
          asrSegments: currentAsrSegments,
          audioUrl: null
        })

        const decision = normalizeInterviewDecision(resp)
        answers.value.push({
          questionId,
          questionNo: question.questionNo || null,
          questionType: question.questionType || null,
          question: question.question,
          answer,
          status: 'answered',
          keywords: question.keywords || []
        })

        const decisionText = DECISION_TEXT_MAP[decision]
        if (decisionText) {
          messages.value.push({
            type: 'ai',
            content: decisionText
          })
        }

        if (decision === 'wrapup' || !resp?.streamAttemptId) {
          await finishInterview({ manual: false })
        } else {
          await streamNextQuestion(resp.streamAttemptId)
        }
      } catch (err) {
        if (isAbortLikeError(err)) {
          console.info('[InterviewPage] submit flow aborted by user action')
        } else {
          console.error('[InterviewPage] failed to submit answer', err)
          alert(err?.message || '提交回答失败，请重试。')
        }
      } finally {
        isSubmittingAnswer.value = false
        await scrollToBottom()
      }
    }

    const handleManualPlayTts = async () => {
      try {
        await ttsPlayerService.play()
      } catch (err) {
        console.warn('[InterviewPage] 手动播放 TTS 失败', err)
      }
    }

    const handleSkipTts = () => {
      ttsPlayerService.skip()
    }

    const handleInterruptTts = () => {
      ttsPlayerService.interrupt()
    }

    const endInterview = async () => {
      if (isFinishing.value) return
      if (confirm('确认结束本场面试吗？')) {
        ttsPlayerService.skip()
        await finishInterview({ manual: true })
      }
    }

    const finishInterview = async ({ manual = false } = {}) => {
      if (isFinishing.value) return
      isFinishing.value = true
      ttsPlayerService.skip()

      try {
        stopTimer()

        if (mediaStream) {
          mediaStream.getTracks().forEach(track => track.stop())
        }

        if (streamAbortController) {
          streamAbortController.abort()
          streamAbortController = null
        }

        const resultData = buildPendingInterviewResult({
          answers: answers.value,
          duration: formattedTime.value
        })

        if (backendSessionId.value) {
          try {
            if (manual) {
              await finishInterviewSession(backendSessionId.value)
            }
          } catch (err) {
            console.warn('[InterviewPage] failed to finish interview session, fallback to generating status', err)
          }
          resultData.reportStatus = 'generating'
          resultData.reportStartedAt = new Date().toISOString()
        } else {
          resultData.reportStatus = 'failed'
          resultData.reportFailedAt = new Date().toISOString()
        }

        resultData.answers = answers.value
        resultData.jobName = jobDisplayName.value
        resultData.companyName = config.companyName
        resultData.interviewRound = currentRound.value.label
        resultData.interviewMode = config.interviewMode
        resultData.sessionId = backendSessionId.value ?? null
        const expLabel = experienceLevels.find(e => e.value === config.experience)
        resultData.experienceLabel = expLabel ? expLabel.label : '未知'

        const savedRecord = saveInterviewRecord(resultData)
        if (savedRecord?.id != null) {
          resultData.recordId = savedRecord.id
        }
        isRunning.value = false
        emit('interviewEnd', resultData)
      } finally {
        isFinishing.value = false
      }
    }

    const saveInterviewRecord = (resultData) => {
      const hasSessionId = resultData?.sessionId != null && String(resultData.sessionId).trim() !== ''
      const nowIso = new Date().toISOString()
      const reportStatus = ['ready', 'failed', 'generating'].includes(resultData?.reportStatus)
        ? resultData.reportStatus
        : (hasSessionId ? 'generating' : 'failed')
      const record = {
        id: Date.now(),
        job: jobDisplayName.value,
        company: config.companyName,
        round: currentRound.value.label,
        date: new Date().toLocaleString('zh-CN'),
        score: resultData.score ?? null,
        duration: resultData.duration,
        questions: resultData.totalQuestions,
        correct: resultData.correctCount ?? null,
        beatPercent: resultData.beatPercent ?? null,
        feedback: resultData.feedback ?? null,
        answers: Array.isArray(resultData.answers) ? resultData.answers : answers.value,
        mode: config.interviewMode,
        sessionId: hasSessionId ? String(resultData.sessionId).trim() : null,
        reportStatus,
        reportStartedAt: reportStatus === 'generating' ? (resultData.reportStartedAt || nowIso) : (resultData.reportStartedAt || null),
        reportReadyAt: reportStatus === 'ready' ? (resultData.reportReadyAt || nowIso) : null,
        reportFailedAt: reportStatus === 'failed' ? (resultData.reportFailedAt || nowIso) : null,
        report: reportStatus === 'ready' ? (resultData.report || null) : null,
        syncStatus: hasSessionId ? (reportStatus === 'ready' ? 'synced' : 'report_generating') : 'failed_missing_session',
        fallbackReason: hasSessionId ? null : 'missing_session_id'
      }
      
      let records = JSON.parse(localStorage.getItem('interviewRecords') || '[]')
      records.unshift(record)
      records = records.slice(0, 50)
      localStorage.setItem('interviewRecords', JSON.stringify(records))
      if (typeof window !== 'undefined') {
        window.dispatchEvent(new CustomEvent('interview-records-updated'))
      }
      return record
    }

    const resetInterview = () => {
      isRunning.value = false
      currentQuestion.value = 0
      messages.value = []
      answers.value = []
      elapsedTime.value = 0
      streamConnectionState.value = 'idle'
    }

    const scrollToBottom = async () => {
      await nextTick()
      if (chatMessages.value) {
        chatMessages.value.scrollTop = chatMessages.value.scrollHeight
      }
    }

    watch(() => config.interviewMode, (mode) => {
      inputMode.value = normalizeInputMode(mode, inputMode.value)
    }, { immediate: true })

    watch(inputMode, (mode) => {
      const normalized = normalizeInputMode(config.interviewMode, mode)
      if (mode !== normalized) {
        inputMode.value = normalized
      }
    })

    watch(recognizedText, () => {
      if (isListening.value) scrollToBottom()
    })

    watch(ttsMode, (mode) => {
      ttsPlayerService.setMode(mode)
    })

    onUnmounted(() => {
      if (streamAbortController) {
        streamAbortController.abort()
        streamAbortController = null
      }
      asrService.destroy()
      ttsPlayerService.destroy()
      stopTimer()
      stopDeviceTest()
      if (loadingTimer) {
        clearInterval(loadingTimer)
      }
      if (tipTimer) {
        clearInterval(tipTimer)
      }
      if (mediaStream) {
        mediaStream.getTracks().forEach(track => track.stop())
      }
    })

    return {
      config,
      newKnowledgePoint,
      resumeList,
      resumeOptions,
      positionOptions,
      showAllJobs,
      jobOptions,
      currentJob,
      currentRound,
      otherJobs,
      experienceLevels,
      interviewRounds,
      pressureLevels,
      voiceOptions,
      optionalStages,
      isRunning,
      isLoading,
      isDeviceTesting,
      loadingProgress,
      displayLoadingProgress,
      currentStep,
      loadingSteps,
      loadingTips,
      loadingTitle,
      loadingSubtitle,
      currentTip,
      deviceTest,
      cameraStatusText,
      cameraStatusIcon,
      microphoneStatusText,
      microphoneStatusIcon,
      isMicTesting,
      networkStatusClass,
      networkStatusIcon,
      networkLatencyText,
      networkStatusHint,
      testVideoElement,
      allDevicesReady,
      currentQuestion,
      totalQuestions,
      elapsedTime,
      formattedTime,
      jobDisplayName,
      messages,
      answerInput,
      chatMessages,
      inputMode,
      showInputModeToggle,
      showTextInput,
      setInputMode,
      isListening,
      recognizedText,
      voiceFailureMessage,
      showInterviewer,
      cameraEnabled,
      isRecording,
      isAiSpeaking,
      aiVoiceLevel,
      ttsMode,
      videoElement,
      metrics,
      currentQuestionData,
      interviewStatus,
      statusText,
      expressionStatus,
      gazeStatus,
      fullscreen: props.fullscreen,
      leftSidebarCollapsed,
      rightSidebarCollapsed,
      isSubmittingAnswer,
      isWaitingNextQuestion,
      isFinishing,
      uploadResume,
      addKnowledgePoint,
      removeKnowledgePoint,
      toggleJobSelector,
      selectJob,
      setJobType,
      setFocusTopic,
      setInterviewMode,
      startInterview,
      professionalVoiceBlockedReason,
      toggleVoiceInput,
      retryVoiceRecognition,
      toggleCamera,
      toggleRecording,
      submitAnswer,
      endInterview,
      handleManualPlayTts,
      handleSkipTts,
      handleInterruptTts,
      testCamera,
      testMicrophone,
      retryMicrophoneTest,
      playTestAudio,
      confirmSpeaker,
      backToConfig,
      startInterviewAfterTest,
      getWaveformHeight,
      asrAvailable,
      asrState,
      lastPauseStats,
      lastAsrSegments
    }
  }
}
</script>

<style scoped>
.interview-config-enhanced {
  display: flex;
  flex-direction: column;
  gap: 24px;
  padding: 0 8px;
}

.config-header {
  text-align: center;
  padding: 32px 24px;
}

.config-main-title {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  font-size: 28px;
  font-weight: 700;
  color: var(--text-primary);
  margin: 0 0 8px;
}

.config-main-title i {
  color: var(--primary-color);
}

.config-subtitle {
  font-size: 14px;
  color: var(--text-secondary);
  margin: 0;
}

.config-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 24px;
}

.config-single {
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.config-single .form-group {
  margin: 0;
}

.config-single .experience-selector {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.config-single .mode-cards {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
}

.field-hint {
  margin-top: 8px;
  font-size: 13px;
  color: var(--text-muted);
}

.config-section {
  padding: 24px;
}

.config-section .section-title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0 0 20px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--glass-border);
}

.config-section .section-title i {
  color: var(--primary-color);
}

.form-row {
  display: flex;
  gap: 20px;
  margin-bottom: 20px;
}

.form-group {
  margin-bottom: 20px;
}

.form-group.half {
  flex: 1;
  margin-bottom: 0;
}

.form-group label {
  display: block;
  font-size: 13px;
  font-weight: 500;
  color: var(--text-secondary);
  margin-bottom: 10px;
}

.form-group label .highlight {
  color: var(--primary-color);
  font-weight: 600;
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
  transition: all 0.3s ease;
}

.glass-input:focus {
  outline: none;
  border-color: var(--primary-color);
  box-shadow: 0 0 0 3px rgba(59, 89, 152, 0.15);
}

.glass-input::placeholder {
  color: var(--text-light);
}

.glass-input.small {
  width: 80px;
  text-align: center;
}

.resume-selector {
  display: flex;
  gap: 12px;
}

.resume-card {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 16px;
  background: var(--glass-bg);
  border: 2px solid var(--glass-border);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: all 0.3s ease;
}

.resume-card:hover {
  border-color: rgba(59, 89, 152, 0.4);
  background: rgba(59, 89, 152, 0.05);
}

.resume-card.active {
  border-color: var(--primary-color);
  background: rgba(59, 89, 152, 0.1);
}

.resume-card i {
  font-size: 24px;
  color: var(--primary-color);
}

.resume-card span {
  font-size: 13px;
  color: var(--text-primary);
}

.resume-card.upload {
  border-style: dashed;
}

.experience-selector {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.exp-btn {
  padding: 8px 16px;
  background: var(--glass-bg);
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-sm);
  font-size: 13px;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all 0.3s ease;
  font-family: inherit;
}

.exp-btn:hover {
  border-color: var(--primary-color);
  color: var(--primary-color);
}

.exp-btn.active {
  background: linear-gradient(135deg, var(--primary-color), var(--primary-light));
  border-color: transparent;
  color: white;
}

.salary-range {
  display: flex;
  align-items: center;
  gap: 8px;
}

.range-separator {
  color: var(--text-secondary);
}

.salary-unit {
  font-size: 14px;
  color: var(--text-secondary);
}

.job-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}

.job-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px;
  background: var(--glass-bg);
  border: 2px solid var(--glass-border);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: all 0.3s ease;
}

.job-card:hover {
  border-color: rgba(59, 89, 152, 0.4);
  transform: translateY(-2px);
}

.job-card.active {
  border-color: var(--primary-color);
  background: rgba(59, 89, 152, 0.1);
  box-shadow: 0 4px 12px rgba(59, 89, 152, 0.15);
}

.job-icon {
  width: 40px;
  height: 40px;
  border-radius: var(--radius-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  color: white;
  flex-shrink: 0;
}

.job-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.job-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
}

.job-desc {
  font-size: 11px;
  color: var(--text-secondary);
}

.round-selector {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.round-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 18px;
  background: var(--glass-bg);
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-md);
  font-size: 13px;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all 0.3s ease;
  font-family: inherit;
}

.round-btn:hover {
  border-color: var(--primary-color);
  color: var(--primary-color);
}

.round-btn.active {
  background: linear-gradient(135deg, var(--primary-color), var(--primary-light));
  border-color: transparent;
  color: white;
}

.round-btn i {
  font-size: 14px;
}

.jd-input {
  min-height: 120px;
  line-height: 1.6;
}

.jd-hint {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 10px;
  padding: 10px 14px;
  background: rgba(245, 158, 11, 0.1);
  border-radius: var(--radius-sm);
  font-size: 12px;
  color: #f59e0b;
}

.jd-hint i {
  font-size: 14px;
}

.mode-cards {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.mode-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px;
  background: var(--glass-bg);
  border: 2px solid var(--glass-border);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: all 0.3s ease;
}

.mode-card:hover {
  border-color: rgba(59, 89, 152, 0.4);
}

.mode-card.active {
  border-color: var(--primary-color);
  background: rgba(59, 89, 152, 0.1);
}

.mode-card.practice .mode-icon {
  background: linear-gradient(135deg, #10b981, #059669);
}

.mode-card.professional .mode-icon {
  background: linear-gradient(135deg, #f59e0b, #d97706);
}

.mode-icon {
  width: 44px;
  height: 44px;
  border-radius: var(--radius-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  color: white;
  flex-shrink: 0;
}

.mode-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.mode-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}

.mode-desc {
  font-size: 11px;
  color: var(--text-secondary);
}

.pressure-selector {
  display: flex;
  gap: 8px;
}

.pressure-btn {
  flex: 1;
  padding: 10px;
  background: var(--glass-bg);
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-sm);
  font-size: 13px;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all 0.3s ease;
  font-family: inherit;
}

.pressure-btn:hover {
  border-color: var(--primary-color);
  color: var(--primary-color);
}

.pressure-btn.active {
  background: linear-gradient(135deg, var(--primary-color), var(--primary-light));
  border-color: transparent;
  color: white;
}

.pressure-btn.pressure-low { border-left: 3px solid #10b981; }
.pressure-btn.pressure-low:hover { border-color: #10b981; color: #059669; }
.pressure-btn.pressure-low.active { background: linear-gradient(135deg, #10b981, #059669); }

.pressure-btn.pressure-medium { border-left: 3px solid #3b82f6; }
.pressure-btn.pressure-medium:hover { border-color: #3b82f6; color: #2563eb; }
.pressure-btn.pressure-medium.active { background: linear-gradient(135deg, #3b82f6, #2563eb); }

.pressure-btn.pressure-high { border-left: 3px solid #f59e0b; }
.pressure-btn.pressure-high:hover { border-color: #f59e0b; color: #d97706; }
.pressure-btn.pressure-high.active { background: linear-gradient(135deg, #f59e0b, #d97706); }

.pressure-btn.pressure-extreme { border-left: 3px solid #ef4444; }
.pressure-btn.pressure-extreme:hover { border-color: #ef4444; color: #dc2626; }
.pressure-btn.pressure-extreme.active { background: linear-gradient(135deg, #ef4444, #dc2626); }

.voice-selector {
  display: flex;
  gap: 8px;
}

.voice-btn {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 10px;
  background: var(--glass-bg);
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-sm);
  font-size: 13px;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all 0.3s ease;
  font-family: inherit;
}

.voice-btn:hover {
  border-color: var(--primary-color);
  color: var(--primary-color);
}

.voice-btn.active {
  background: linear-gradient(135deg, var(--primary-color), var(--primary-light));
  border-color: transparent;
  color: white;
}

.voice-btn.voice-random { border-left: 3px solid #8b5cf6; }
.voice-btn.voice-random:hover { border-color: #8b5cf6; color: #7c3aed; }
.voice-btn.voice-random.active { background: linear-gradient(135deg, #8b5cf6, #7c3aed); }

.voice-btn.voice-male { border-left: 3px solid #0ea5e9; }
.voice-btn.voice-male:hover { border-color: #0ea5e9; color: #0284c7; }
.voice-btn.voice-male.active { background: linear-gradient(135deg, #0ea5e9, #0284c7); }

.voice-btn.voice-female { border-left: 3px solid #ec4899; }
.voice-btn.voice-female:hover { border-color: #ec4899; color: #db2777; }
.voice-btn.voice-female.active { background: linear-gradient(135deg, #ec4899, #db2777); }

.stage-selector {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.stage-checkbox {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 16px;
  background: var(--glass-bg);
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: all 0.3s ease;
}

.stage-checkbox:hover {
  border-color: rgba(59, 89, 152, 0.4);
}

.stage-checkbox.active {
  border-color: var(--primary-color);
  background: rgba(59, 89, 152, 0.1);
}

.checkbox-custom {
  width: 18px;
  height: 18px;
  border-radius: 4px;
  background: var(--glass-border);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.3s ease;
}

.checkbox-custom i {
  font-size: 10px;
  color: white;
  opacity: 0;
  transition: all 0.3s ease;
}

.stage-checkbox.active .checkbox-custom {
  background: linear-gradient(135deg, var(--primary-color), var(--primary-light));
}

.stage-checkbox.active .checkbox-custom i {
  opacity: 1;
}

.stage-label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--text-primary);
}

.stage-label i {
  font-size: 14px;
  color: var(--primary-color);
}

.knowledge-points {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.points-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.point-tag {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  background: rgba(59, 89, 152, 0.1);
  border-radius: 16px;
  font-size: 13px;
  color: var(--primary-color);
}

.point-tag i {
  font-size: 10px;
  cursor: pointer;
  opacity: 0.6;
  transition: opacity 0.3s ease;
}

.point-tag i:hover {
  opacity: 1;
}

.config-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 24px;
  position: sticky;
  bottom: 0;
}

.config-summary {
  display: flex;
  gap: 24px;
}

.summary-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--text-secondary);
}

.summary-item i {
  color: var(--primary-color);
}

.start-btn {
  padding: 14px 40px;
  font-size: 16px;
}

.glass-slider {
  width: 100%;
  height: 6px;
  background: var(--glass-border);
  border-radius: 3px;
  appearance: none;
  cursor: pointer;
}

.glass-slider::-webkit-slider-thumb {
  appearance: none;
  width: 18px;
  height: 18px;
  background: linear-gradient(135deg, var(--primary-color), var(--primary-light));
  border-radius: 50%;
  cursor: pointer;
  box-shadow: 0 2px 8px rgba(59, 89, 152, 0.3);
}

.job-selector {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.job-option-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px;
  background: var(--glass-bg);
  border: 2px solid var(--glass-border);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: all var(--transition-normal);
  position: relative;
}

.job-option-card.selected-job {
  border-color: var(--primary-color);
  background: rgba(59, 89, 152, 0.1);
}

.job-option-card.selected-job:hover {
  background: rgba(59, 89, 152, 0.15);
}

.job-options-list .job-option-card:hover {
  background: rgba(59, 89, 152, 0.1);
  border-color: rgba(59, 89, 152, 0.3);
  transform: translateX(4px);
}

.job-options-list .job-option-card.active {
  background: rgba(59, 89, 152, 0.15);
  border-color: var(--primary-color);
  box-shadow: 0 4px 12px rgba(59, 89, 152, 0.2);
}

.job-option-icon {
  width: 48px;
  height: 48px;
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  color: white;
  flex-shrink: 0;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.job-option-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
  flex: 1;
}

.job-option-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}

.job-option-desc {
  font-size: 12px;
  color: var(--text-secondary);
}

.job-option-arrow {
  font-size: 16px;
  color: var(--primary-color);
  transition: transform var(--transition-normal);
}

.job-selector.expanded .job-option-arrow {
  transform: rotate(180deg);
}

.job-option-check {
  position: absolute;
  right: 16px;
  top: 50%;
  transform: translateY(-50%);
  font-size: 20px;
  color: var(--primary-color);
  animation: checkIn 0.3s ease;
}

@keyframes checkIn {
  from {
    opacity: 0;
    transform: translateY(-50%) scale(0.5);
  }
  to {
    opacity: 1;
    transform: translateY(-50%) scale(1);
  }
}

.expand-enter-active,
.expand-leave-active {
  transition: all 0.3s ease;
  overflow: hidden;
}

.expand-enter-from,
.expand-leave-to {
  opacity: 0;
  max-height: 0;
  transform: translateY(-10px);
}

.expand-enter-to,
.expand-leave-from {
  opacity: 1;
  max-height: 500px;
  transform: translateY(0);
}

.job-options-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--glass-border);
  margin-top: 12px;
}

.interview-workspace {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 40px);
  gap: 16px;
}

.interview-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 24px;
  flex-shrink: 0;
}

.topbar-left {
  display: flex;
  align-items: center;
  gap: 24px;
}

.interview-badge {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  background: linear-gradient(135deg, var(--primary-color), var(--primary-light));
  border-radius: var(--radius-md);
  color: white;
  font-weight: 500;
}

.question-progress {
  display: flex;
  align-items: baseline;
  gap: 4px;
  font-size: 18px;
}

.question-progress .current {
  font-size: 24px;
  font-weight: 700;
  color: var(--primary-color);
}

.question-progress .separator {
  color: var(--text-light);
}

.question-progress .total {
  color: var(--text-secondary);
}

.topbar-center {
  display: flex;
  align-items: center;
}

.timer-display {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 24px;
  background: rgba(59, 89, 152, 0.1);
  border-radius: var(--radius-lg);
  font-size: 28px;
  font-weight: 700;
  color: var(--primary-color);
}

.timer-display i {
  font-size: 20px;
  opacity: 0.7;
}

.topbar-right {
  display: flex;
  align-items: center;
}

.interview-status {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  border-radius: var(--radius-md);
  font-size: 14px;
  font-weight: 500;
}

.interview-status.waiting {
  background: rgba(16, 185, 129, 0.1);
  color: #10b981;
}

.interview-status.speaking {
  background: rgba(59, 89, 152, 0.1);
  color: var(--primary-color);
}

.interview-status.listening {
  background: rgba(245, 158, 11, 0.1);
  color: #f59e0b;
}

.interview-status.reconnecting {
  background: rgba(249, 115, 22, 0.12);
  color: #f97316;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: currentColor;
  animation: pulse 1.5s infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

.interview-main {
  display: flex;
  gap: 16px;
  flex: 1;
  min-height: 0;
}

.sidebar-left,
.sidebar-right {
  width: 280px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 20px;
  padding: 20px;
  overflow-y: auto;
  position: relative;
  transition: width 0.3s ease, padding 0.3s ease;
}

.sidebar-left.collapsed,
.sidebar-right.collapsed {
  width: 40px;
  padding: 20px 8px;
  overflow: hidden;
}

.sidebar-toggle {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: var(--glass-bg);
  border: 1px solid var(--glass-border);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  font-size: 12px;
  color: var(--text-secondary);
  transition: all var(--transition-normal);
  z-index: 10;
}

.sidebar-toggle:hover {
  background: rgba(59, 89, 152, 0.1);
  border-color: var(--primary-color);
  color: var(--primary-color);
}

.sidebar-toggle.left {
  right: 8px;
}

.sidebar-toggle.right {
  left: 8px;
}

.sidebar-content {
  opacity: 1;
  transition: opacity 0.2s ease;
}

.sidebar-left.collapsed .sidebar-content,
.sidebar-right.collapsed .sidebar-content {
  opacity: 0;
  pointer-events: none;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 16px;
}

.section-title i {
  color: var(--primary-color);
}

.metrics-grid {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.metric-item {
  display: flex;
  align-items: center;
  gap: 12px;
}

.metric-icon {
  width: 36px;
  height: 36px;
  border-radius: var(--radius-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  color: white;
}

.metric-icon.speed {
  background: linear-gradient(135deg, #667eea, #764ba2);
}

.metric-icon.fluency {
  background: linear-gradient(135deg, #10b981, #059669);
}

.metric-icon.latency {
  background: linear-gradient(135deg, #f59e0b, #d97706);
}

.metric-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
  flex: 1;
}

.metric-value {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}

.metric-label {
  font-size: 11px;
  color: var(--text-secondary);
}

.metric-bar {
  width: 60px;
  height: 6px;
  background: var(--glass-border);
  border-radius: 3px;
  overflow: hidden;
}

.metric-fill {
  height: 100%;
  background: linear-gradient(90deg, var(--primary-color), var(--primary-light));
  border-radius: 3px;
  transition: width 0.5s ease;
}

.latency-bar .metric-fill {
  background: linear-gradient(90deg, #10b981, #059669);
}

.action-buttons {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.action-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 12px;
  background: var(--glass-bg);
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-md);
  font-size: 14px;
  font-weight: 500;
  color: var(--text-primary);
  cursor: pointer;
  transition: all var(--transition-normal);
  font-family: inherit;
  position: relative;
}

.action-btn:hover {
  background: rgba(59, 89, 152, 0.1);
  border-color: var(--primary-color);
}

.action-btn.end:hover {
  background: rgba(239, 68, 68, 0.1);
  border-color: #ef4444;
  color: #ef4444;
}

.action-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.input-mode-toggle {
  display: flex;
  gap: 8px;
}

.mode-btn {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 12px;
  background: var(--glass-bg);
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-md);
  font-size: 12px;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all var(--transition-normal);
  font-family: inherit;
}

.mode-btn:hover {
  background: rgba(59, 89, 152, 0.1);
}

.mode-btn.active {
  background: rgba(59, 89, 152, 0.2);
  border-color: var(--primary-color);
  color: var(--primary-color);
}

.mode-btn i {
  font-size: 18px;
}

.chat-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  padding: 0;
}

.chat-header {
  padding: 20px;
  border-bottom: 1px solid var(--glass-border);
}

.current-question {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.question-badge {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  background: rgba(59, 89, 152, 0.1);
  border-radius: var(--radius-sm);
  font-size: 13px;
  font-weight: 500;
  color: var(--primary-color);
  width: fit-content;
}

.question-text {
  font-size: 16px;
  font-weight: 500;
  color: var(--text-primary);
  line-height: 1.6;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.message {
  display: flex;
  gap: 12px;
  width: 100%;
  animation: messageIn 0.3s ease;
}

@keyframes messageIn {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.message.ai {
  flex-direction: row;
}

.message.user {
  flex-direction: row-reverse;
  justify-content: flex-end;
}

.message-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.message.ai .message-avatar {
  background: linear-gradient(135deg, var(--primary-color), var(--primary-light));
  color: white;
}

.message.user .message-avatar {
  background: linear-gradient(135deg, #10b981, #059669);
  color: white;
}

.message-content {
  max-width: 70%;
  padding: 14px 18px;
  border-radius: var(--radius-lg);
  font-size: 14px;
  line-height: 1.6;
}

.message.ai .message-content {
  background: rgba(59, 89, 152, 0.1);
  border: 1px solid rgba(59, 89, 152, 0.2);
}

.message.user .message-content {
  width: 70%;
  max-width: 70%;
  min-height: 44px;
  box-sizing: border-box;
  background: rgba(16, 185, 129, 0.1);
  border: 1px solid rgba(16, 185, 129, 0.2);
}

.message.user.draft .message-content {
  border-style: dashed;
  border-color: rgba(16, 185, 129, 0.4);
}

.message.user.draft .message-content p {
  color: var(--text-secondary);
  font-style: italic;
}

.draft-hint {
  display: block;
  margin-top: 6px;
  font-size: 12px;
  color: var(--text-light);
}

.voice-recognition {
  padding: 16px 20px;
  background: rgba(245, 158, 11, 0.05);
  border-top: 1px solid var(--glass-border);
}

.voice-indicator {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 10px;
}

.voice-wave {
  display: flex;
  align-items: center;
  gap: 3px;
  height: 20px;
}

.voice-wave span {
  width: 3px;
  background: #f59e0b;
  border-radius: 2px;
  animation: wave 0.5s ease-in-out infinite;
}

.voice-wave span:nth-child(1) { animation-delay: 0s; height: 8px; }
.voice-wave span:nth-child(2) { animation-delay: 0.1s; height: 16px; }
.voice-wave span:nth-child(3) { animation-delay: 0.2s; height: 12px; }
.voice-wave span:nth-child(4) { animation-delay: 0.3s; height: 20px; }

@keyframes wave {
  0%, 100% { transform: scaleY(0.5); }
  50% { transform: scaleY(1); }
}

.voice-text {
  font-size: 13px;
  color: #f59e0b;
  font-weight: 500;
}

.recognized-text {
  font-size: 14px;
  color: var(--text-primary);
  padding: 10px;
  background: var(--glass-bg);
  border-radius: var(--radius-sm);
}

.chat-input-area {
  padding: 20px;
  border-top: 1px solid var(--glass-border);
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.input-row {
  display: flex;
  align-items: flex-end;
  gap: 12px;
}

.input-row .input-wrapper {
  flex: 1;
  min-width: 0;
}

.input-row .submit-btn {
  flex-shrink: 0;
  align-self: flex-end;
}

.input-wrapper {
  width: 100%;
}

.glass-textarea {
  width: 100%;
  padding: 14px 16px;
  background: var(--glass-bg);
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-md);
  font-size: 14px;
  color: var(--text-primary);
  resize: none;
  font-family: inherit;
  transition: all var(--transition-normal);
}

.glass-textarea:focus {
  outline: none;
  border-color: var(--primary-color);
  box-shadow: 0 0 0 3px rgba(59, 89, 152, 0.2);
}

.glass-textarea::placeholder {
  color: var(--text-light);
}

.voice-input-wrapper {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.voice-error-panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 12px 14px;
  border: 1px solid rgba(239, 68, 68, 0.25);
  border-radius: var(--radius-md);
  background: rgba(239, 68, 68, 0.08);
}

.voice-error-text {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
  color: #dc2626;
}

.voice-error-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.voice-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 16px;
  background: var(--glass-bg);
  border: 2px dashed var(--glass-border);
  border-radius: var(--radius-md);
  font-size: 14px;
  font-weight: 500;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all var(--transition-normal);
  font-family: inherit;
}

.voice-btn:hover {
  background: rgba(59, 89, 152, 0.1);
  border-color: var(--primary-color);
  color: var(--primary-color);
}

.voice-btn.active {
  background: rgba(239, 68, 68, 0.1);
  border: 2px solid #ef4444;
  color: #ef4444;
}

.interviewer-header,
.camera-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.interviewer-header h4,
.camera-header h4 {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}

.toggle-switch {
  position: relative;
  display: inline-block;
  width: 44px;
  height: 24px;
}

.toggle-switch input {
  opacity: 0;
  width: 0;
  height: 0;
}

.toggle-slider {
  position: absolute;
  cursor: pointer;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: var(--glass-border);
  border-radius: 24px;
  transition: 0.3s;
}

.toggle-slider::before {
  position: absolute;
  content: "";
  height: 18px;
  width: 18px;
  left: 3px;
  bottom: 3px;
  background-color: white;
  border-radius: 50%;
  transition: 0.3s;
}

.toggle-switch input:checked + .toggle-slider {
  background: linear-gradient(135deg, var(--primary-color), var(--primary-light));
}

.toggle-switch input:checked + .toggle-slider::before {
  transform: translateX(20px);
}

.interviewer-avatar {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
}

.avatar-container {
  position: relative;
  width: 100px;
  height: 100px;
}

.avatar-glow {
  position: absolute;
  inset: -10px;
  background: radial-gradient(circle, rgba(59, 89, 152, 0.3) 0%, transparent 70%);
  border-radius: 50%;
  animation: glow 2s ease-in-out infinite;
}

@keyframes glow {
  0%, 100% { opacity: 0.5; transform: scale(1); }
  50% { opacity: 1; transform: scale(1.1); }
}

.avatar-image {
  position: absolute;
  inset: 0;
  background: linear-gradient(135deg, var(--primary-color), var(--primary-light));
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 40px;
  color: white;
  box-shadow: 0 8px 24px rgba(59, 89, 152, 0.3);
}

.avatar-status {
  position: absolute;
  bottom: 5px;
  right: 5px;
  width: 20px;
  height: 20px;
  background: #10b981;
  border-radius: 50%;
  border: 3px solid white;
}

.avatar-status.speaking {
  background: #f59e0b;
}

.avatar-status .pulse {
  position: absolute;
  inset: -3px;
  border-radius: 50%;
  background: currentColor;
  opacity: 0.3;
  animation: pulse-ring 1.5s infinite;
}

@keyframes pulse-ring {
  0% { transform: scale(1); opacity: 0.3; }
  100% { transform: scale(1.5); opacity: 0; }
}

.interviewer-info {
  text-align: center;
}

.interviewer-name {
  display: block;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}

.interviewer-role {
  font-size: 12px;
  color: var(--text-secondary);
}

.camera-container {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.camera-view {
  position: relative;
  width: 100%;
  aspect-ratio: 4/3;
  background: #1a1a2e;
  border-radius: var(--radius-md);
  overflow: hidden;
}

.camera-view video {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.camera-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  pointer-events: none;
}

.face-frame {
  width: 60%;
  height: 70%;
  border: 2px dashed rgba(255, 255, 255, 0.3);
  border-radius: 50%;
}

.camera-controls {
  display: flex;
  justify-content: center;
}

.camera-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 20px;
  background: var(--glass-bg);
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-md);
  font-size: 13px;
  color: var(--text-primary);
  cursor: pointer;
  transition: all var(--transition-normal);
  font-family: inherit;
}

.camera-btn:hover {
  background: rgba(59, 89, 152, 0.1);
  border-color: var(--primary-color);
}

.camera-btn.recording {
  background: rgba(239, 68, 68, 0.1);
  border-color: #ef4444;
  color: #ef4444;
}

.camera-btn.recording i {
  animation: blink 1s infinite;
}

@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.3; }
}

.analysis-panel {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px;
  background: var(--glass-bg);
  border-radius: var(--radius-sm);
}

.analysis-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--text-secondary);
}

.analysis-item i {
  color: var(--primary-color);
}

.camera-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 40px 20px;
  background: var(--glass-bg);
  border-radius: var(--radius-md);
  color: var(--text-light);
}

.camera-placeholder i {
  font-size: 32px;
}

.camera-placeholder span {
  font-size: 13px;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

.expand-enter-active,
.expand-leave-active {
  transition: all 0.3s ease;
  overflow: hidden;
}

.expand-enter-from,
.expand-leave-to {
  opacity: 0;
  max-height: 0;
  transform: translateY(-10px);
}

.expand-enter-to,
.expand-leave-from {
  opacity: 1;
  max-height: 500px;
  transform: translateY(0);
}

.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}


.interview-loading-page {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, rgba(26, 26, 46, 0.95) 0%, rgba(22, 33, 62, 0.95) 100%);
  z-index: 200;
  animation: fadeIn 0.3s ease;
}

@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

.loading-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 40px;
  max-width: 600px;
  width: 90%;
}

.loading-animation {
  position: relative;
  width: 180px;
  height: 180px;
}

.loading-circle {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 120px;
  height: 120px;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--primary-color), var(--primary-light));
  animation: pulse-circle 2s ease-in-out infinite;
}

.circle-inner {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 80px;
  height: 80px;
  border-radius: 50%;
  background: linear-gradient(135deg, rgba(26, 26, 46, 0.9), rgba(22, 33, 62, 0.9));
  display: flex;
  align-items: center;
  justify-content: center;
}

.circle-inner::before {
  content: '\f007';
  font-family: 'Font Awesome 5 Free';
  font-weight: 900;
  font-size: 32px;
  color: var(--primary-color);
  animation: icon-pulse 1.5s ease-in-out infinite;
}

.circle-glow {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 140px;
  height: 140px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(59, 89, 152, 0.4) 0%, transparent 70%);
  animation: glow-pulse 2s ease-in-out infinite;
}

@keyframes pulse-circle {
  0%, 100% { transform: translate(-50%, -50%) scale(1); }
  50% { transform: translate(-50%, -50%) scale(1.05); }
}

@keyframes icon-pulse {
  0%, 100% { opacity: 0.7; transform: scale(1); }
  50% { opacity: 1; transform: scale(1.1); }
}

@keyframes glow-pulse {
  0%, 100% { opacity: 0.5; transform: translate(-50%, -50%) scale(1); }
  50% { opacity: 1; transform: translate(-50%, -50%) scale(1.2); }
}

.loading-particles {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 180px;
  height: 180px;
}

.particle {
  position: absolute;
  width: 8px;
  height: 8px;
  background: var(--primary-color);
  border-radius: 50%;
  animation: particle-float 3s ease-in-out infinite;
  animation-delay: var(--delay);
}

.particle:nth-child(1) { top: 0; left: 50%; transform: translateX(-50%); }
.particle:nth-child(2) { top: 13%; left: 87%; }
.particle:nth-child(3) { top: 50%; left: 100%; transform: translateY(-50%); }
.particle:nth-child(4) { top: 87%; left: 87%; }
.particle:nth-child(5) { top: 100%; left: 50%; transform: translateX(-50%); }
.particle:nth-child(6) { top: 87%; left: 13%; }
.particle:nth-child(7) { top: 50%; left: 0; transform: translateY(-50%); }
.particle:nth-child(8) { top: 13%; left: 13%; }
.particle:nth-child(9) { top: 25%; left: 75%; }
.particle:nth-child(10) { top: 75%; left: 75%; }
.particle:nth-child(11) { top: 75%; left: 25%; }
.particle:nth-child(12) { top: 25%; left: 25%; }

@keyframes particle-float {
  0%, 100% { opacity: 0.3; transform: scale(1); }
  50% { opacity: 1; transform: scale(1.5); }
}

.loading-content {
  text-align: center;
}

.loading-title {
  font-size: 24px;
  font-weight: 700;
  color: var(--text-primary);
  margin: 0 0 8px;
  animation: title-fade 0.5s ease;
}

@keyframes title-fade {
  from { opacity: 0; transform: translateY(-10px); }
  to { opacity: 1; transform: translateY(0); }
}

.loading-subtitle {
  font-size: 14px;
  color: var(--text-secondary);
  margin: 0 0 24px;
}

.loading-progress {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 32px;
}

.progress-bar {
  flex: 1;
  height: 8px;
  background: rgba(59, 89, 152, 0.2);
  border-radius: 4px;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, var(--primary-color), var(--primary-light));
  border-radius: 4px;
  transition: width 0.1s ease;
  position: relative;
}

.progress-fill::after {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.3), transparent);
  animation: shimmer 1.5s infinite;
}

@keyframes shimmer {
  0% { transform: translateX(-100%); }
  100% { transform: translateX(100%); }
}

.progress-text {
  font-size: 14px;
  font-weight: 600;
  color: var(--primary-color);
  min-width: 45px;
}

.loading-steps {
  display: flex;
  justify-content: center;
  gap: 24px;
}

.step-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  opacity: 0.4;
  transition: all 0.3s ease;
}

.step-item.active {
  opacity: 1;
}

.step-item.completed {
  opacity: 0.7;
}

.step-icon {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: rgba(59, 89, 152, 0.2);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  color: var(--text-secondary);
  transition: all 0.3s ease;
}

.step-item.active .step-icon {
  background: linear-gradient(135deg, var(--primary-color), var(--primary-light));
  color: white;
  box-shadow: 0 4px 12px rgba(59, 89, 152, 0.3);
}

.step-item.completed .step-icon {
  background: linear-gradient(135deg, #10b981, #059669);
  color: white;
}

.step-text {
  font-size: 11px;
  color: var(--text-secondary);
  white-space: nowrap;
}

.step-item.active .step-text {
  color: var(--text-primary);
  font-weight: 500;
}

.loading-info {
  padding: 20px 24px;
  width: 100%;
}

.info-row {
  display: flex;
  justify-content: center;
  gap: 32px;
  margin-bottom: 12px;
}

.info-row:last-child {
  margin-bottom: 0;
}

.info-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--text-secondary);
}

.info-item i {
  color: var(--primary-color);
  font-size: 14px;
}

.loading-tips {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px 24px;
  background: rgba(245, 158, 11, 0.1);
  border: 1px solid rgba(245, 158, 11, 0.2);
  border-radius: var(--radius-lg);
  animation: tip-fade 0.5s ease;
}

@keyframes tip-fade {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

.tip-icon {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: linear-gradient(135deg, #f59e0b, #d97706);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.tip-icon i {
  font-size: 18px;
  color: white;
}

.tip-text {
  font-size: 14px;
  color: #f59e0b;
  line-height: 1.5;
  margin: 0;
}

.device-testing-page {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, rgba(26, 26, 46, 0.98) 0%, rgba(22, 33, 62, 0.98) 100%);
  z-index: 200;
  animation: fadeIn 0.3s ease;
  overflow-y: auto;
  padding: 40px 20px;
}

.testing-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 32px;
  max-width: 1200px;
  width: 100%;
}

.testing-header {
  text-align: center;
  width: 100%;
  position: relative;
}

.testing-back-btn {
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
}

.testing-title {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  font-size: 28px;
  font-weight: 700;
  color: var(--text-primary);
  margin: 0 0 8px;
}

.testing-title i {
  color: var(--primary-color);
  animation: rotate 4s linear infinite;
}

@keyframes rotate {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.testing-subtitle {
  font-size: 14px;
  color: var(--text-secondary);
  margin: 0;
}

.testing-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 24px;
  width: 100%;
}

.test-section {
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.test-header {
  display: flex;
  align-items: center;
  gap: 16px;
}

.test-icon {
  width: 48px;
  height: 48px;
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  color: white;
}

.test-icon.camera {
  background: linear-gradient(135deg, #667eea, #764ba2);
}

.test-icon.microphone {
  background: linear-gradient(135deg, #10b981, #059669);
}

.test-icon.speaker {
  background: linear-gradient(135deg, #f59e0b, #d97706);
}

.test-info {
  flex: 1;
}

.test-info h3 {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0 0 4px;
}

.test-status {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--text-secondary);
}

.test-status.ready {
  color: #10b981;
}

.test-status i {
  font-size: 12px;
}

.camera-preview {
  position: relative;
  width: 100%;
  aspect-ratio: 4/3;
  background: #1a1a2e;
  border-radius: var(--radius-md);
  overflow: hidden;
}

.camera-preview video {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.preview-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  background: rgba(26, 26, 46, 0.9);
}

.preview-overlay i {
  font-size: 32px;
  color: var(--primary-color);
}

.preview-overlay span {
  font-size: 13px;
  color: var(--text-secondary);
}

.microphone-test {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.waveform-container {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 20px;
  background: rgba(26, 26, 46, 0.5);
  border-radius: var(--radius-md);
}

.waveform-bars {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  height: 80px;
}

.waveform-bar {
  width: 8px;
  background: linear-gradient(180deg, #10b981, #059669);
  border-radius: 4px;
  transition: height 0.1s ease;
}

.microphone-level-bar {
  height: 8px;
  background: rgba(59, 89, 152, 0.2);
  border-radius: 4px;
  overflow: hidden;
}

.level-fill {
  height: 100%;
  background: linear-gradient(90deg, #10b981, #34d399);
  border-radius: 4px;
  transition: width 0.1s ease;
}

.test-hint {
  font-size: 12px;
  color: var(--text-secondary);
  text-align: center;
  margin: 0;
}

.speaker-test {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  padding: 20px;
}

.speaker-visual {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100px;
}

.speaker-icon-large {
  position: relative;
  width: 80px;
  height: 80px;
  border-radius: 50%;
  background: linear-gradient(135deg, #f59e0b, #d97706);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32px;
  color: white;
}

.speaker-icon-large.playing {
  animation: pulse-scale 0.5s ease infinite;
}

@keyframes pulse-scale {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.1); }
}

.sound-waves {
  position: absolute;
  inset: -20px;
  pointer-events: none;
}

.sound-waves span {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  border-radius: 50%;
  border: 2px solid rgba(245, 158, 11, 0.5);
  animation: sound-wave 1.5s ease-out infinite;
}

.sound-waves span:nth-child(1) { animation-delay: 0s; }
.sound-waves span:nth-child(2) { animation-delay: 0.3s; }
.sound-waves span:nth-child(3) { animation-delay: 0.6s; }

@keyframes sound-wave {
  0% {
    width: 80px;
    height: 80px;
    opacity: 1;
  }
  100% {
    width: 140px;
    height: 140px;
    opacity: 0;
  }
}

.speaker-icon-large:not(.playing) .sound-waves span {
  animation: none;
  opacity: 0;
}

.test-actions {
  display: flex;
  gap: 12px;
  justify-content: center;
}

.test-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 20px;
  background: var(--glass-bg);
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-md);
  font-size: 13px;
  color: var(--text-primary);
  cursor: pointer;
  transition: all 0.3s ease;
  font-family: inherit;
}

.test-btn:hover:not(:disabled) {
  border-color: var(--primary-color);
  color: var(--primary-color);
}

.test-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.test-btn.success {
  background: linear-gradient(135deg, #10b981, #059669);
  border-color: transparent;
  color: white;
}

.test-btn.success:hover {
  background: linear-gradient(135deg, #059669, #047857);
}

.testing-summary {
  padding: 24px;
  width: 100%;
}

.summary-row {
  display: flex;
  justify-content: center;
  flex-wrap: wrap;
  gap: 48px;
  row-gap: 12px;
  margin-bottom: 24px;
}

.summary-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 24px;
  background: rgba(26, 26, 46, 0.5);
  border-radius: var(--radius-md);
  font-size: 14px;
  color: rgba(255, 255, 255, 0.85);
}

.summary-item.network-item {
  min-width: 220px;
}

.network-value {
  min-width: 68px;
  text-align: right;
  font-weight: 600;
}

.summary-item.ready {
  background: rgba(16, 185, 129, 0.1);
  color: #10b981;
}

.summary-item.network-testing {
  background: rgba(96, 165, 250, 0.12);
  color: #93c5fd;
}

.summary-item.network-pass {
  background: rgba(16, 185, 129, 0.12);
  color: #10b981;
}

.summary-item.network-warning {
  background: rgba(245, 158, 11, 0.14);
  color: #f59e0b;
}

.summary-item.network-fail {
  background: rgba(239, 68, 68, 0.14);
  color: #ef4444;
}

.summary-item i:first-child {
  font-size: 18px;
}

.status-icon {
  font-size: 16px;
}

.summary-item.ready .status-icon {
  color: #10b981;
}

.summary-item:not(.ready) .status-icon {
  color: #ef4444;
}

.summary-item.network-testing .status-icon {
  color: #93c5fd;
}

.summary-item.network-pass .status-icon {
  color: #10b981;
}

.summary-item.network-warning .status-icon {
  color: #f59e0b;
}

.summary-item.network-fail .status-icon {
  color: #ef4444;
}

.network-hint {
  margin: 0 0 20px;
  text-align: center;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.75);
}

.network-hint.network-pass {
  color: #34d399;
}

.network-hint.network-warning {
  color: #fbbf24;
}

.network-hint.network-fail {
  color: #f87171;
}

.voice-requirement-hint {
  margin: 12px 0 0;
  text-align: center;
  font-size: 13px;
  color: #dc2626;
}

.summary-actions {
  display: flex;
  gap: 16px;
  justify-content: center;
}

.start-interview-btn {
  padding: 14px 40px;
  font-size: 16px;
}

.start-interview-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.fullscreen-mode {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  width: 100vw;
  height: 100vh;
  z-index: 100;
}

.fullscreen-mode .interview-workspace {
  height: 100vh;
  padding: 20px;
  box-sizing: border-box;
}

.fullscreen-mode .interview-topbar {
  border-radius: var(--radius-lg);
  margin-bottom: 16px;
}

.fullscreen-mode .sidebar-left,
.fullscreen-mode .sidebar-right {
  height: calc(100vh - 100px);
  overflow-y: auto;
}

.fullscreen-mode .chat-area {
  height: calc(100vh - 100px);
}
</style>
