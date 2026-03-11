/**
 * TtsPlayerService - 题目 TTS 播放与音量分析服务
 *
 * 功能：
 * 1. 轮询后端音频状态接口（/audio），拿到音频文件地址后播放
 * 2. 使用 Web Audio API 的 AnalyserNode 实时输出音量百分比
 * 3. 支持自动播报 / 手动播放 / 静音模式，以及 skip/interrupt 控制
 */
export class TtsPlayerService {
  constructor() {
    this.mode = 'auto' // auto | manual | mute
    this.audio = null
    this.audioContext = null
    this.analyser = null
    this.source = null
    this.animationFrameId = null
    this.isPlaying = false
    this.pendingAudioUrl = ''

    /** 音量回调: (level: number, speaking: boolean) => void */
    this.onVolume = null
    /** 播放状态回调: (playing: boolean) => void */
    this.onPlayingChange = null
  }

  setMode(mode) {
    this.mode = mode
    if (mode === 'mute') {
      this.interrupt()
    }
  }

  /**
   * 根据后端状态接口轮询并触发播放。
   * @param {string} audioStatusUrl 例如 /api/v1/interviews/1/questions/42/audio
   */
  async handleDoneEvent(audioStatusUrl) {
    if (!audioStatusUrl || this.mode === 'mute') return null
    const readyUrl = await this._waitForAudio(audioStatusUrl)
    this.pendingAudioUrl = readyUrl || ''
    if (this.mode === 'auto' && readyUrl) {
      await this.play(readyUrl)
    }
    return readyUrl
  }

  async play(url = '') {
    const targetUrl = url || this.pendingAudioUrl
    if (!targetUrl || this.mode === 'mute') return
    await this._ensureAudioGraph(targetUrl)
    await this.audio.play()
    this.isPlaying = true
    this.onPlayingChange?.(true)
    this._startLevelLoop()
  }

  skip() {
    if (!this.audio) return
    this.audio.currentTime = this.audio.duration || this.audio.currentTime
    this._stopPlaybackState()
  }

  interrupt() {
    if (!this.audio) return
    this.audio.pause()
    this.audio.currentTime = 0
    this._stopPlaybackState()
  }

  destroy() {
    this.interrupt()
    if (this.audio) {
      this.audio.src = ''
    }
    if (this.audioContext) {
      this.audioContext.close().catch(() => {})
      this.audioContext = null
    }
    this.analyser = null
    this.source = null
  }

  async _waitForAudio(audioStatusUrl) {
    const maxRetry = 8
    for (let i = 0; i < maxRetry; i++) {
      const resp = await fetch(audioStatusUrl, {
        headers: { Authorization: `Bearer ${localStorage.getItem('aiInterviewToken') || localStorage.getItem('token') || ''}` },
      })
      if (!resp.ok) {
        await this._sleep(300)
        continue
      }
      const json = await resp.json()
      const ready = json?.data?.ready === true
      const audioUrl = json?.data?.audioUrl || ''
      if (ready && audioUrl) return audioUrl
      await this._sleep(300 + i * 150)
    }
    return null
  }

  async _ensureAudioGraph(url) {
    if (!this.audio) {
      this.audio = new Audio()
      this.audio.preload = 'auto'
      this.audio.addEventListener('ended', () => this._stopPlaybackState())
      this.audio.addEventListener('pause', () => {
        if (this.audio.currentTime === 0 || this.audio.currentTime >= this.audio.duration) {
          this._stopPlaybackState()
        }
      })
    }
    if (this.audio.src !== location.origin + url && this.audio.src !== url) {
      this.audio.src = url
    }

    if (!this.audioContext) {
      this.audioContext = new (window.AudioContext || window.webkitAudioContext)()
    }
    if (!this.source) {
      this.source = this.audioContext.createMediaElementSource(this.audio)
      this.analyser = this.audioContext.createAnalyser()
      this.analyser.fftSize = 256
      this.source.connect(this.analyser)
      this.analyser.connect(this.audioContext.destination)
    }
    if (this.audioContext.state === 'suspended') {
      await this.audioContext.resume()
    }
  }

  _startLevelLoop() {
    if (!this.analyser) return
    const bufferLength = this.analyser.frequencyBinCount
    const dataArray = new Uint8Array(bufferLength)

    const tick = () => {
      if (!this.isPlaying || !this.analyser) return
      this.analyser.getByteFrequencyData(dataArray)
      const avg = dataArray.reduce((s, v) => s + v, 0) / bufferLength
      const level = Math.min(100, Math.round((avg / 255) * 130))
      this.onVolume?.(level, this.isPlaying)
      this.animationFrameId = requestAnimationFrame(tick)
    }
    this.animationFrameId = requestAnimationFrame(tick)
  }

  _stopPlaybackState() {
    this.isPlaying = false
    if (this.animationFrameId) {
      cancelAnimationFrame(this.animationFrameId)
      this.animationFrameId = null
    }
    this.onVolume?.(0, false)
    this.onPlayingChange?.(false)
  }

  _sleep(ms) {
    return new Promise((resolve) => setTimeout(resolve, ms))
  }
}

export const ttsPlayerService = new TtsPlayerService()

