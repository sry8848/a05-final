import { streamInterviewQuestion } from '../api/resume'

const DEFAULT_RETRY_DELAYS_MS = [500, 1000, 2000, 4000, 6000]

function isAbortError(err) {
  if (!err) return false
  return err.name === 'AbortError' || String(err.message || '').toLowerCase().includes('aborted')
}

function sleep(ms, signal) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => {
      cleanup()
      resolve()
    }, ms)

    const onAbort = () => {
      cleanup()
      reject(new DOMException('Aborted', 'AbortError'))
    }

    const cleanup = () => {
      clearTimeout(timer)
      if (signal) signal.removeEventListener('abort', onAbort)
    }

    if (signal) signal.addEventListener('abort', onAbort, { once: true })
  })
}

export class QuestionStreamClient {
  constructor(options = {}) {
    this.maxReconnectDurationMs = options.maxReconnectDurationMs ?? 60000
    this.retryDelaysMs = Array.isArray(options.retryDelaysMs) && options.retryDelaysMs.length
      ? options.retryDelaysMs
      : DEFAULT_RETRY_DELAYS_MS
    this.reset()
  }

  reset() {
    this.lastEventId = ''
    this.maxDeltaId = -1
    this.seenStart = false
    this.terminated = false
    this.retryCount = 0
    this.state = 'idle'
  }

  async consume(sessionId, attemptId, handlers = {}, signal) {
    this.reset()
    const startedAt = Date.now()

    while (!this.terminated) {
      if (signal?.aborted) {
        throw new DOMException('Aborted', 'AbortError')
      }

      this._setState(this.retryCount > 0 ? 'reconnecting' : 'connecting', handlers)
      try {
        const result = await streamInterviewQuestion(
          sessionId,
          attemptId,
          this._wrapHandlers(handlers),
          signal,
          { lastEventId: this.lastEventId }
        )

        if (result?.lastEventId) {
          this.lastEventId = String(result.lastEventId)
        }
        if (this.terminated || result?.terminalEvent) {
          this._setState('completed', handlers)
          return
        }

        await this._scheduleReconnect(handlers, signal, startedAt, null)
      } catch (err) {
        if (isAbortError(err) || signal?.aborted) {
          throw err
        }
        if (this.terminated) {
          this._setState('error', handlers)
          throw err
        }
        if (!this._isRetryable(err)) {
          this._setState('error', handlers)
          throw err
        }
        await this._scheduleReconnect(handlers, signal, startedAt, err)
      }
    }
  }

  _wrapHandlers(handlers) {
    return {
      onStart: async (payload) => {
        if (this.seenStart) return
        this.seenStart = true
        this._setState('streaming', handlers)
        if (handlers.onStart) {
          await handlers.onStart(payload)
        }
      },
      onDelta: async (payload, eventName, eventId) => {
        if (eventId) {
          this.lastEventId = String(eventId)
          const numericId = Number(eventId)
          if (Number.isInteger(numericId)) {
            if (numericId <= this.maxDeltaId) {
              return
            }
            this.maxDeltaId = numericId
          }
        }
        this._setState('streaming', handlers)
        if (handlers.onDelta) {
          await handlers.onDelta(payload, eventName, eventId)
        }
      },
      onTtsReady: async (payload, eventName, eventId) => {
        this._setState('streaming', handlers)
        if (handlers.onTtsReady) {
          Promise.resolve(handlers.onTtsReady(payload, eventName, eventId)).catch((err) => {
            console.warn('[QuestionStreamClient] onTtsReady handler failed', err)
          })
        }
      },
      onDone: async (payload) => {
        this.terminated = true
        if (handlers.onDone) {
          await handlers.onDone(payload)
        }
      },
      onError: async (payload) => {
        this.terminated = true
        if (handlers.onError) {
          await handlers.onError(payload)
        }
      },
      onMessage: handlers.onMessage
    }
  }

  async _scheduleReconnect(handlers, signal, startedAt, err) {
    const elapsed = Date.now() - startedAt
    if (elapsed >= this.maxReconnectDurationMs) {
      this._setState('error', handlers)
      throw new Error('网络恢复超时，请重试当前题目生成')
    }

    const delay = this.retryDelaysMs[Math.min(this.retryCount, this.retryDelaysMs.length - 1)]
    this.retryCount += 1
    if (handlers.onReconnect) {
      handlers.onReconnect({
        attempt: this.retryCount,
        delayMs: delay,
        lastEventId: this.lastEventId,
        error: err || null
      })
    }
    await sleep(delay, signal)
  }

  _setState(nextState, handlers) {
    if (this.state === nextState) return
    this.state = nextState
    if (handlers.onStateChange) {
      handlers.onStateChange(nextState)
    }
  }

  _isRetryable(err) {
    const message = String(err?.message || '')
    const match = message.match(/SSE connection failed:\s*(\d{3})/)
    if (match) {
      const status = Number(match[1])
      return status === 408 || status === 409 || status === 425 || status === 429 || status >= 500
    }
    return true
  }
}
