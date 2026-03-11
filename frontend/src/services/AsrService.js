/**
 * AsrService — 阿里云百炼 DashScope 实时 ASR 封装
 *
 * 功能：
 * 1. 通过后端 /api/v1/asr/token 获取 WebSocket 连接凭证
 * 2. 使用 MediaRecorder + Web Audio API 采集麦克风 PCM 数据
 * 3. 通过 WebSocket 实时推送音频帧到 DashScope，接收中间/最终识别结果
 * 4. 检测超过阈值的停顿，在最终文本中插入 [停顿 Xs] 标签
 * 5. 计算并上报 pauseStats（wpm / longPauseCount / longestPauseMs）
 *
 * 使用方式：
 *   const asr = new AsrService()
 *   await asr.init()
 *   asr.onInterim = (text) => { ... }     // 中间帧回调（实时字幕）
 *   asr.onFinal   = (text, pauseStats, segments) => { ... }  // 最终帧回调
 *   asr.onError   = (err)  => { ... }
 *   await asr.start(questionType)
 *   // ... 用户说话 ...
 *   await asr.stop()
 */

const ASR_TOKEN_URL = '/api/v1/asr/token'
const PAUSE_THRESHOLDS_URL = '/api/v1/config/asr-pause-thresholds'

/** 各题型默认停顿阈值（毫秒），从后端 /config/asr-pause-thresholds 拉取后覆盖 */
const DEFAULT_PAUSE_THRESHOLDS = {
  INTRO: 2000,
  PRINCIPLE: 2500,
  SCENARIO: 3000,
  PROJECT_DEEP_DIVE: 2000,
  BEHAVIORAL: 2500,
}

/** 未知题型的兜底阈值 */
const FALLBACK_THRESHOLD = 2500

/** DashScope ASR 响应事件类型 */
const DASHSCOPE_EVENT = {
  SESSION_CREATED: 'session.created',
  SPEECH_STARTED: 'input_audio_buffer.speech_started',
  SPEECH_STOPPED: 'input_audio_buffer.speech_stopped',
  TRANSCRIPTION_TEXT: 'conversation.item.input_audio_transcription.text',
  TRANSCRIPTION_COMPLETED: 'conversation.item.input_audio_transcription.completed',
  SESSION_FINISHED: 'session.finished',
  ERROR: 'error',
}

export class AsrService {
  constructor() {
    // WebSocket 实例
    this._ws = null
    // MediaRecorder 实例（采集 PCM）
    this._mediaRecorder = null
    // 麦克风 MediaStream
    this._stream = null
    // AudioContext（用于 ScriptProcessor 采集 PCM）
    this._audioContext = null
    this._scriptProcessor = null

    // 凭证缓存
    this._token = null

    // 停顿阈值配置（后端拉取后填充）
    this._pauseThresholds = { ...DEFAULT_PAUSE_THRESHOLDS }

    // 当前题型（start 时传入）
    this._currentQuestionType = 'PRINCIPLE'

    // 识别片段时间线（用于停顿计算）
    // 每条：{ text, beginTime, endTime }
    this._segments = []

    // 中间帧累计文本
    this._interimBuffer = ''

    // 最终确认文本（含停顿标签）
    this._finalText = ''

    // 状态
    this._state = 'idle' // idle | connecting | running | stopping | stopped

    // ── 回调（外部赋值） ────────────────────────────────────────
    /** 中间帧回调：(interimText: string) => void */
    this.onInterim = null
    /** 最终帧回调：(finalText: string, pauseStats: object, segments: array) => void */
    this.onFinal = null
    /** 错误回调：(error: Error) => void */
    this.onError = null
    /** 状态变化回调：(state: string) => void */
    this.onStateChange = null
  }

  // ─────────────────────────────────────────────────────────────
  // 公开 API
  // ─────────────────────────────────────────────────────────────

  /**
   * 初始化：拉取停顿阈值配置（页面加载时调用一次即可）。
   * 不需要登录状态，阈值接口为公开接口。
   */
  async init() {
    try {
      const resp = await fetch(PAUSE_THRESHOLDS_URL)
      if (resp.ok) {
        const data = await resp.json()
        if (data?.data?.thresholds) {
          this._pauseThresholds = { ...DEFAULT_PAUSE_THRESHOLDS, ...data.data.thresholds }
        }
      }
    } catch (e) {
      // 降级：使用内置默认值，不影响主流程
      console.warn('[AsrService] 拉取停顿阈值失败，使用默认值', e)
    }
  }

  /**
   * 检查 ASR 是否可用（后端已启用且凭证有效）。
   * @returns {Promise<boolean>}
   */
  async isAvailable() {
    const token = await this._fetchToken()
    return token?.enabled === true
  }

  /**
   * 开始语音识别。
   * @param {string} questionType - 当前题型（INTRO/PRINCIPLE/SCENARIO/...）
   */
  async start(questionType = 'PRINCIPLE') {
    if (this._state !== 'idle' && this._state !== 'stopped') {
      console.warn('[AsrService] start() 忽略，当前状态:', this._state)
      return
    }

    this._currentQuestionType = questionType
    this._segments = []
    this._interimBuffer = ''
    this._finalText = ''
    this._setState('connecting')

    try {
      // 1. 获取凭证
      const token = await this._fetchToken()
      if (!token?.enabled) {
        throw new Error('ASR 未启用，前端应降级为文字输入')
      }

      // 2. 申请麦克风权限
      this._stream = await navigator.mediaDevices.getUserMedia({
        audio: { sampleRate: 16000, channelCount: 1, echoCancellation: true, noiseSuppression: true },
      })

      // 3. 建立 WebSocket 连接
      await this._connectWebSocket(token.wsUrl)

      // 4. 配置会话（VAD 模式）
      this._sendSessionConfig()

      // 5. 启动音频采集
      this._startAudioCapture()

      this._setState('running')
    } catch (err) {
      this._setState('stopped')
      this._handleError(err)
    }
  }

  /**
   * 停止语音识别，等待最终识别结果后触发 onFinal 回调。
   */
  async stop() {
    if (this._state !== 'running') {
      console.warn('[AsrService] stop() 忽略，当前状态:', this._state)
      return
    }
    this._setState('stopping')
    this._stopAudioCapture()

    // 发送 session.finish 通知 DashScope 我们已完成输入
    if (this._ws && this._ws.readyState === WebSocket.OPEN) {
      this._ws.send(JSON.stringify({ type: 'session.finish' }))
    }

    // 若 3 秒内没有收到 completed 事件，强制关闭并触发 onFinal
    this._stopTimeout = setTimeout(() => {
      console.warn('[AsrService] 等待最终帧超时，强制关闭')
      this._finalize()
    }, 3000)
  }

  /**
   * 强制释放所有资源（页面离开时调用）。
   */
  destroy() {
    this._stopAudioCapture()
    this._closeWebSocket()
    this._setState('stopped')
  }

  // ─────────────────────────────────────────────────────────────
  // 私有方法
  // ─────────────────────────────────────────────────────────────

  _setState(state) {
    this._state = state
    this.onStateChange?.(state)
  }

  _handleError(err) {
    console.error('[AsrService] 错误:', err)
    this.onError?.(err)
  }

  /** 拉取或使用缓存的 ASR 凭证 */
  async _fetchToken() {
    const now = Math.floor(Date.now() / 1000)
    if (this._token && this._token.expiresAt > now + 60) {
      return this._token
    }
    try {
      const resp = await fetch(ASR_TOKEN_URL, {
        headers: { Authorization: `Bearer ${this._getJwtFromStorage()}` },
      })
      if (!resp.ok) throw new Error(`获取 ASR token 失败: ${resp.status}`)
      const data = await resp.json()
      this._token = data.data
      return this._token
    } catch (e) {
      console.error('[AsrService] 获取 token 失败', e)
      return null
    }
  }

  /** 从 localStorage 读取 JWT（与 auth.js 保持一致） */
  _getJwtFromStorage() {
    return localStorage.getItem('token') || ''
  }

  /** 建立 WebSocket 并等待连接成功 */
  _connectWebSocket(wsUrl) {
    return new Promise((resolve, reject) => {
      const ws = new WebSocket(wsUrl)
      this._ws = ws

      const timeout = setTimeout(() => {
        reject(new Error('WebSocket 连接超时（5s）'))
        ws.close()
      }, 5000)

      ws.onopen = () => {
        clearTimeout(timeout)
        resolve()
      }

      ws.onerror = (e) => {
        clearTimeout(timeout)
        reject(new Error('WebSocket 连接失败'))
      }

      ws.onmessage = (event) => {
        this._handleWsMessage(event)
      }

      ws.onclose = () => {
        if (this._state === 'running') {
          this._handleError(new Error('WebSocket 意外断开'))
          this._setState('stopped')
        }
      }
    })
  }

  /** 发送 VAD 模式会话配置 */
  _sendSessionConfig() {
    if (!this._ws || this._ws.readyState !== WebSocket.OPEN) return
    this._ws.send(
      JSON.stringify({
        type: 'session.update',
        session: {
          input_audio_format: 'pcm',
          input_audio_transcription: {
            model: this._token?.model || 'paraformer-realtime-v2',
            language: 'zh',
          },
          turn_detection: {
            type: 'server_vad',
            threshold: 0.3,
            silence_duration_ms: 600,
          },
        },
      })
    )
  }

  /** 启动麦克风采集，将 PCM 数据发往 WebSocket */
  _startAudioCapture() {
    const sampleRate = 16000
    this._audioContext = new (window.AudioContext || window.webkitAudioContext)({ sampleRate })

    const source = this._audioContext.createMediaStreamSource(this._stream)

    // ScriptProcessor：每 4096 帧触发一次（约 256ms @16kHz），采集 PCM16LE
    this._scriptProcessor = this._audioContext.createScriptProcessor(4096, 1, 1)
    this._scriptProcessor.onaudioprocess = (e) => {
      if (this._state !== 'running' && this._state !== 'stopping') return
      if (!this._ws || this._ws.readyState !== WebSocket.OPEN) return

      const float32 = e.inputBuffer.getChannelData(0)
      const pcm16 = this._float32ToPcm16(float32)
      const base64 = this._arrayBufferToBase64(pcm16.buffer)

      this._ws.send(
        JSON.stringify({
          type: 'input_audio_buffer.append',
          audio: base64,
        })
      )
    }

    source.connect(this._scriptProcessor)
    this._scriptProcessor.connect(this._audioContext.destination)
  }

  /** 停止音频采集，释放相关资源 */
  _stopAudioCapture() {
    try {
      if (this._scriptProcessor) {
        this._scriptProcessor.disconnect()
        this._scriptProcessor = null
      }
      if (this._audioContext) {
        this._audioContext.close()
        this._audioContext = null
      }
      if (this._stream) {
        this._stream.getTracks().forEach((t) => t.stop())
        this._stream = null
      }
    } catch (e) {
      console.warn('[AsrService] 停止音频采集时出现警告', e)
    }
  }

  /** 关闭 WebSocket */
  _closeWebSocket() {
    if (this._ws) {
      this._ws.onclose = null
      this._ws.close()
      this._ws = null
    }
  }

  /** 处理 DashScope WebSocket 消息 */
  _handleWsMessage(event) {
    let msg
    try {
      msg = JSON.parse(event.data)
    } catch {
      return
    }

    switch (msg.type) {
      case DASHSCOPE_EVENT.TRANSCRIPTION_TEXT: {
        // 中间帧：实时字幕刷新
        const text = msg.text || msg.delta?.text || ''
        if (text) {
          this._interimBuffer = text
          this.onInterim?.(text)
        }
        break
      }

      case DASHSCOPE_EVENT.TRANSCRIPTION_COMPLETED: {
        // 最终帧：收集片段，计算停顿
        const finalText = msg.text || msg.transcript || ''
        const beginTime = msg.begin_time ?? 0
        const endTime = msg.end_time ?? 0

        if (finalText) {
          this._segments.push({ text: finalText, beginTime, endTime })
        }
        break
      }

      case DASHSCOPE_EVENT.SESSION_FINISHED: {
        clearTimeout(this._stopTimeout)
        this._finalize()
        break
      }

      case DASHSCOPE_EVENT.ERROR: {
        const errMsg = msg.error?.message || msg.message || '未知 ASR 错误'
        this._handleError(new Error(`DashScope ASR 错误: ${errMsg}`))
        this._finalize()
        break
      }
    }
  }

  /**
   * 组装最终结果：停顿打标 + pauseStats 计算 + 触发 onFinal 回调。
   */
  _finalize() {
    clearTimeout(this._stopTimeout)
    this._closeWebSocket()
    this._setState('stopped')

    const threshold = this._pauseThresholds[this._currentQuestionType] ?? FALLBACK_THRESHOLD

    // 基于片段时间线检测停顿并插入标签
    const { taggedText, longPauseCount, longestPauseMs, pauseList } =
      this._buildTaggedText(this._segments, threshold)

    // 计算语速 wpm（以每分钟字数估算，中文按字符计）
    const totalChars = this._segments.reduce((sum, s) => sum + s.text.length, 0)
    const lastEndTime = this._segments.length > 0 ? this._segments[this._segments.length - 1].endTime : 0
    const durationMinutes = lastEndTime > 0 ? lastEndTime / 1000 / 60 : 1
    const wpm = durationMinutes > 0 ? Math.round(totalChars / durationMinutes) : 0

    const pauseStats = { wpm, longPauseCount, longestPauseMs }

    this._finalText = taggedText
    this.onFinal?.(taggedText, pauseStats, [...this._segments])
  }

  /**
   * 根据片段时间线构建停顿打标文本。
   *
   * @param {Array} segments - ASR 片段数组，每条 { text, beginTime, endTime }（毫秒）
   * @param {number} threshold - 停顿阈值（毫秒）
   * @returns {{ taggedText, longPauseCount, longestPauseMs, pauseList }}
   */
  _buildTaggedText(segments, threshold) {
    if (!segments || segments.length === 0) {
      return { taggedText: this._interimBuffer, longPauseCount: 0, longestPauseMs: 0, pauseList: [] }
    }

    let taggedText = ''
    let longPauseCount = 0
    let longestPauseMs = 0
    const pauseList = []

    for (let i = 0; i < segments.length; i++) {
      const seg = segments[i]
      taggedText += seg.text

      if (i < segments.length - 1) {
        const nextSeg = segments[i + 1]
        const gap = nextSeg.beginTime - seg.endTime
        if (gap >= threshold) {
          const seconds = (gap / 1000).toFixed(1)
          taggedText += ` [停顿 ${seconds}s] `
          longPauseCount++
          if (gap > longestPauseMs) longestPauseMs = gap
          pauseList.push({ position: taggedText.length, durationMs: gap })
        }
      }
    }

    return { taggedText, longPauseCount, longestPauseMs, pauseList }
  }

  // ─────────────────────────────────────────────────────────────
  // 音频格式工具
  // ─────────────────────────────────────────────────────────────

  /** Float32 [-1,1] → Int16 PCM16LE */
  _float32ToPcm16(float32Array) {
    const int16 = new Int16Array(float32Array.length)
    for (let i = 0; i < float32Array.length; i++) {
      const clamped = Math.max(-1, Math.min(1, float32Array[i]))
      int16[i] = clamped < 0 ? clamped * 0x8000 : clamped * 0x7fff
    }
    return int16
  }

  /** ArrayBuffer → Base64 字符串 */
  _arrayBufferToBase64(buffer) {
    const bytes = new Uint8Array(buffer)
    let binary = ''
    for (let i = 0; i < bytes.byteLength; i++) {
      binary += String.fromCharCode(bytes[i])
    }
    return btoa(binary)
  }
}

/** 单例导出，供全局复用 */
export const asrService = new AsrService()
