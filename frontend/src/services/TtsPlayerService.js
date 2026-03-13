/**
 * TtsPlayerService - 题目片段 TTS 队列播放服务
 *
 * 功能：
 * 1. 消费 SSE tts_ready 事件，按 segmentIndex 顺序排队播放
 * 2. 通过 fetch + Authorization 拉取二进制并转 Blob URL
 * 3. 保留 auto/manual/mute 三档，skip=停止当前并清空待播
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

    this.currentGenerationId = ''
    this.nextExpectedIndex = 0
    this.pendingByIndex = new Map()
    this.queue = []
    this.isDraining = false
    this.manualDrainRequested = false
    this.currentPlaybackFinish = null

    /** 音量回调: (level: number, speaking: boolean) => void */
    this.onVolume = null
    /** 播放状态回调: (playing: boolean) => void */
    this.onPlayingChange = null
  }

  setMode(mode) {
    this.mode = mode
    if (mode === 'mute') {
      this.skip()
      return
    }
    if (mode === 'auto' && this.queue.length > 0) {
      this._drainQueue()
    }
  }

  beginGeneration(generationId) {
    if (!generationId) return
    if (this.currentGenerationId === generationId) return
    this.skip()
    this.currentGenerationId = generationId
    this.nextExpectedIndex = 0
  }

  async handleTtsReadyEvent(payload) {
    if (!payload || this.mode === 'mute') return

    const generationId = payload?.generationId || ''
    if (generationId) {
      if (!this.currentGenerationId) {
        this.currentGenerationId = generationId
      } else if (generationId !== this.currentGenerationId) {
        // 忽略非当前 generation 的延迟事件，防止跨题音频串播。
        return
      }
    }

    const segmentIndex = Number(payload?.segmentIndex)
    const audioUrl = payload?.audioUrl || ''
    if (!Number.isInteger(segmentIndex) || segmentIndex < 0 || !audioUrl) return

    if (segmentIndex < this.nextExpectedIndex) return
    if (this.pendingByIndex.has(segmentIndex)) return
    if (this.queue.some((item) => item.segmentIndex === segmentIndex)) return

    const expectedGeneration = this.currentGenerationId
    const blobUrl = await this._fetchAudioBlobUrl(audioUrl)
    if (!blobUrl) return

    if (expectedGeneration && this.currentGenerationId !== expectedGeneration) {
      URL.revokeObjectURL(blobUrl)
      return
    }

    this.pendingByIndex.set(segmentIndex, { segmentIndex, blobUrl })
    this._enqueueReadySegments()

    if (this.mode === 'auto' || (this.mode === 'manual' && this.manualDrainRequested)) {
      await this._drainQueue()
    }
  }

  async play() {
    if (this.mode === 'mute') return
    this.manualDrainRequested = true
    await this._drainQueue()
  }

  skip() {
    if (this.audio) {
      this.audio.pause()
      this.audio.currentTime = 0
    }
    if (this.currentPlaybackFinish) {
      this.currentPlaybackFinish()
      this.currentPlaybackFinish = null
    }
    this._stopPlaybackState()
    this._clearQueue()
    this.manualDrainRequested = false
  }

  interrupt() {
    this.skip()
  }

  destroy() {
    this.skip()
    if (this.audio) {
      this.audio.src = ''
    }
    if (this.audioContext) {
      this.audioContext.close().catch(() => {})
      this.audioContext = null
    }
    this.analyser = null
    this.source = null
    this.currentGenerationId = ''
    this.nextExpectedIndex = 0
  }

  _enqueueReadySegments() {
    while (this.pendingByIndex.has(this.nextExpectedIndex)) {
      const segment = this.pendingByIndex.get(this.nextExpectedIndex)
      this.pendingByIndex.delete(this.nextExpectedIndex)
      this.queue.push(segment)
      this.nextExpectedIndex += 1
    }
  }

  async _drainQueue() {
    if (this.isDraining) return
    if (this.mode === 'manual' && !this.manualDrainRequested) return
    if (!this.queue.length) return

    this.isDraining = true
    try {
      while (this.queue.length > 0 && this.mode !== 'mute') {
        if (this.mode === 'manual' && !this.manualDrainRequested) break
        const next = this.queue.shift()
        await this._playBlobUrl(next.blobUrl)
      }
    } finally {
      this.isDraining = false
      if (this.mode === 'manual') {
        this.manualDrainRequested = false
      }
    }
  }

  async _playBlobUrl(blobUrl) {
    try {
      await this._ensureAudioGraph(blobUrl)
    } catch (_) {
      this._revokeBlobUrl(blobUrl)
      return
    }

    await new Promise((resolve) => {
      let finished = false

      const finish = () => {
        if (finished) return
        finished = true
        this.currentPlaybackFinish = null
        this.audio.removeEventListener('ended', finish)
        this.audio.removeEventListener('error', finish)
        this._revokeBlobUrl(blobUrl)
        this._stopPlaybackState()
        resolve()
      }

      this.currentPlaybackFinish = finish
      this.audio.addEventListener('ended', finish)
      this.audio.addEventListener('error', finish)

      this.audio.play().then(() => {
        this.isPlaying = true
        this.onPlayingChange?.(true)
        this._startLevelLoop()
      }).catch(() => finish())
    })
  }

  async _fetchAudioBlobUrl(audioUrl) {
    const token = localStorage.getItem('aiInterviewToken') || localStorage.getItem('token') || ''
    const headers = token ? { Authorization: `Bearer ${token}` } : {}
    const resp = await fetch(audioUrl, { headers })
    if (!resp.ok) return null
    const blob = await resp.blob()
    if (!blob || blob.size <= 0) return null
    return URL.createObjectURL(blob)
  }

  async _ensureAudioGraph(url) {
    if (!this.audio) {
      this.audio = new Audio()
      this.audio.preload = 'auto'
    }
    if (this.audio.src !== url) {
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
      const avg = dataArray.reduce((sum, value) => sum + value, 0) / bufferLength
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

  _clearQueue() {
    for (const queued of this.queue) {
      this._revokeBlobUrl(queued.blobUrl)
    }
    this.queue = []

    for (const pending of this.pendingByIndex.values()) {
      this._revokeBlobUrl(pending.blobUrl)
    }
    this.pendingByIndex.clear()
  }

  _revokeBlobUrl(url) {
    if (!url) return
    try {
      URL.revokeObjectURL(url)
    } catch (_) {}
  }
}

export const ttsPlayerService = new TtsPlayerService()
