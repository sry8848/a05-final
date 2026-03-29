import { buildApiUrl, resolveBackendUrl } from '../api/base.js'

/**
 * AsrService — 后端代理 ASR 封装
 *
 * 流程：
 * 1. 通过后端 /api/v1/asr/token 获取代理 WebSocket 地址和采样配置
 * 2. 浏览器连接后端 /api/v1/asr/stream
 * 3. 后端再代理连接阿里云 Paraformer WebSocket
 * 4. 前端只接收 ready/interim/segment_final/final/error 五类事件
 */

const ASR_TOKEN_URL = buildApiUrl('/asr/token')
const DEFAULT_AUDIO_CONFIG = {
  format: 'pcm',
  sampleRate: 16000,
  channels: 1,
  encoding: 'pcm16le',
}

export class AsrService {
  constructor() {
    this._ws = null
    this._stream = null
    this._audioContext = null
    this._scriptProcessor = null
    this._token = null
    this._state = 'idle'
    this._stopTimeout = null
    this._readyResolve = null
    this._readyReject = null
    this._committedText = ''

    this.onInterim = null
    this.onFinal = null
    this.onError = null
    this.onStateChange = null
  }

  async init() {
    return
  }

  async isAvailable() {
    const token = await this._fetchToken()
    return token?.enabled === true
  }

  async start(questionType = 'PRINCIPLE', context = {}) {
    if (this._state !== 'idle' && this._state !== 'stopped') {
      console.warn('[AsrService] start() 忽略，当前状态:', this._state)
      return
    }

    this._committedText = ''
    this._setState('connecting')

    try {
      const token = await this._fetchToken()
      if (!token?.enabled) {
        throw new Error('ASR 未启用，前端应降级为文字输入')
      }

      const audioConfig = this._resolveAudioConfig(token)
      console.info('[AsrService] 启动代理 ASR', {
        wsUrl: token.wsUrl,
        protocolVersion: token.protocolVersion || 'v1',
        audioConfig,
        questionType,
        context,
      })

      this._stream = await navigator.mediaDevices.getUserMedia({
        audio: {
          sampleRate: audioConfig.sampleRate,
          channelCount: audioConfig.channels,
          echoCancellation: true,
          noiseSuppression: true,
        },
      })

      await this._connectWebSocket(resolveBackendUrl(token.wsUrl))
      await this._sendStartAndWaitReady(questionType, token.protocolVersion || 'v1', audioConfig, context)
      this._startAudioCapture(audioConfig)

      this._setState('running')
    } catch (err) {
      this._setState('stopped')
      this._cleanupReadyPromise()
      this._handleError(err)
      this._stopAudioCapture()
      this._closeWebSocket()
    }
  }

  async stop() {
    if (this._state !== 'running') {
      console.warn('[AsrService] stop() 忽略，当前状态:', this._state)
      return
    }

    this._setState('stopping')
    this._stopAudioCapture()

    if (this._ws && this._ws.readyState === WebSocket.OPEN) {
      this._ws.send(JSON.stringify({ type: 'stop' }))
    }

    this._stopTimeout = setTimeout(() => {
      this._handleError(new Error('等待 ASR 最终结果超时'))
      this._closeWebSocket()
      this._setState('stopped')
    }, 3000)
  }

  destroy() {
    clearTimeout(this._stopTimeout)
    this._stopAudioCapture()
    this._closeWebSocket()
    this._committedText = ''
    this._setState('stopped')
  }

  _setState(state) {
    this._state = state
    this.onStateChange?.(state)
  }

  _handleError(err) {
    console.error('[AsrService] 错误:', err)
    this.onError?.(err)
  }

  async _fetchToken() {
    try {
      const resp = await fetch(ASR_TOKEN_URL, {
        headers: { Authorization: `Bearer ${this._getJwtFromStorage()}` },
      })
      if (!resp.ok) throw new Error(`获取 ASR token 失败: ${resp.status}`)
      const data = await resp.json()
      this._token = data.data
      console.info('[AsrService] 获取新的代理 ticket', {
        wsUrl: this._token?.wsUrl,
        ticket: this._token?.ticket,
        expiresAt: this._token?.expiresAt,
      })
      return this._token
    } catch (e) {
      console.error('[AsrService] 获取 token 失败', e)
      return null
    }
  }

  _getJwtFromStorage() {
    return localStorage.getItem('aiInterviewToken') || localStorage.getItem('token') || ''
  }

  _resolveAudioConfig(token) {
    return {
      ...DEFAULT_AUDIO_CONFIG,
      ...(token?.audio || {}),
    }
  }

  _connectWebSocket(wsUrl) {
    return new Promise((resolve, reject) => {
      const ws = new WebSocket(wsUrl)
      ws.binaryType = 'arraybuffer'
      this._ws = ws

      const timeout = setTimeout(() => {
        reject(new Error('WebSocket 连接超时（5s）'))
        ws.close()
      }, 5000)

      ws.onopen = () => {
        clearTimeout(timeout)
        console.info('[AsrService] 代理 WebSocket 已连接')
        resolve()
      }

      ws.onerror = (event) => {
        clearTimeout(timeout)
        console.error('[AsrService] WebSocket onerror', event)
        reject(new Error('WebSocket 连接失败'))
      }

      ws.onmessage = (event) => {
        this._handleWsMessage(event)
      }

      ws.onclose = () => {
        console.info('[AsrService] 代理 WebSocket 已关闭, state=', this._state)
        if (this._state === 'running' || this._state === 'stopping' || this._state === 'connecting') {
          this._handleError(new Error('WebSocket 意外断开'))
          this._setState('stopped')
        }
      }
    })
  }

  _sendStartAndWaitReady(questionType, protocolVersion, audioConfig, context = {}) {
    if (!this._ws || this._ws.readyState !== WebSocket.OPEN) {
      return Promise.reject(new Error('WebSocket 尚未连接'))
    }

    return new Promise((resolve, reject) => {
      const timeout = setTimeout(() => {
        this._cleanupReadyPromise()
        reject(new Error('等待代理 ASR ready 超时'))
      }, 5000)

      this._readyResolve = () => {
        clearTimeout(timeout)
        this._cleanupReadyPromise()
        resolve()
      }
      this._readyReject = (err) => {
        clearTimeout(timeout)
        this._cleanupReadyPromise()
        reject(err)
      }

      this._ws.send(
        JSON.stringify({
          type: 'start',
          questionType,
          protocolVersion,
          audio: audioConfig,
          context,
        })
      )
    })
  }

  _cleanupReadyPromise() {
    this._readyResolve = null
    this._readyReject = null
  }

  _startAudioCapture(audioConfig) {
    const sampleRate = audioConfig.sampleRate || 16000
    this._audioContext = new (window.AudioContext || window.webkitAudioContext)({ sampleRate })

    const source = this._audioContext.createMediaStreamSource(this._stream)
    this._scriptProcessor = this._audioContext.createScriptProcessor(4096, 1, 1)
    this._scriptProcessor.onaudioprocess = (e) => {
      if (this._state !== 'running' && this._state !== 'stopping') return
      if (!this._ws || this._ws.readyState !== WebSocket.OPEN) return

      const float32 = e.inputBuffer.getChannelData(0)
      const pcm16 = this._float32ToPcm16(float32)
      this._ws.send(pcm16.buffer.slice(0))
    }

    source.connect(this._scriptProcessor)
    this._scriptProcessor.connect(this._audioContext.destination)
  }

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

  _closeWebSocket() {
    if (this._ws) {
      this._ws.onclose = null
      this._ws.onerror = null
      this._ws.onmessage = null
      this._ws.close()
      this._ws = null
    }
  }

  _handleWsMessage(event) {
    let msg
    try {
      msg = JSON.parse(event.data)
    } catch {
      return
    }

    switch (msg.type) {
      case 'ready':
        console.info('[AsrService] 收到 ready 事件', msg)
        this._readyResolve?.()
        break

      case 'interim':
        console.debug('[AsrService] 收到 interim 事件', msg)
        this.onInterim?.(this._mergeRecognizedText(msg.text || ''))
        break

      case 'segment_final':
        console.info('[AsrService] 收到 segment_final 事件', msg)
        this._appendCommittedText(msg.text || '')
        this.onInterim?.(this._committedText)
        break

      case 'final':
        console.info('[AsrService] 收到 final 事件', msg)
        clearTimeout(this._stopTimeout)
        this._closeWebSocket()
        this._setState('stopped')
        this._committedText = ''
        this.onFinal?.(
          msg.text || '',
          msg.pauseStats || null,
          msg.asrSegments || [],
          {
            rawText: msg.rawText || '',
            changeList: msg.changeList || [],
            correctionApplied: msg.correctionApplied === true,
          }
        )
        break

      case 'error': {
        console.error('[AsrService] 收到 error 事件', msg)
        const error = new Error(msg.message || '未知 ASR 错误')
        this._readyReject?.(error)
        clearTimeout(this._stopTimeout)
        this._closeWebSocket()
        this._setState('stopped')
        this._committedText = ''
        this._handleError(error)
        break
      }

      default:
        console.warn('[AsrService] 收到未知事件', msg)
    }
  }

  _float32ToPcm16(float32Array) {
    const int16 = new Int16Array(float32Array.length)
    for (let i = 0; i < float32Array.length; i++) {
      const clamped = Math.max(-1, Math.min(1, float32Array[i]))
      int16[i] = clamped < 0 ? clamped * 0x8000 : clamped * 0x7fff
    }
    return int16
  }

  _appendCommittedText(text) {
    const normalized = String(text || '').trim()
    if (!normalized) {
      return
    }
    this._committedText = this._committedText
      ? `${this._committedText}${normalized}`
      : normalized
  }

  _mergeRecognizedText(interimText) {
    const normalizedInterim = String(interimText || '').trim()
    if (!normalizedInterim) {
      return this._committedText
    }
    return this._committedText
      ? `${this._committedText}${normalizedInterim}`
      : normalizedInterim
  }
}

export const asrService = new AsrService()
