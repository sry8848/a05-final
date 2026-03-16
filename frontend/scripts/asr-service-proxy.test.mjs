import assert from 'node:assert/strict'
import { AsrService } from '../src/services/AsrService.js'

globalThis.localStorage = {
  getItem() {
    return 'jwt-token'
  },
}

const service = new AsrService()

let fetchCount = 0
globalThis.fetch = async () => {
  fetchCount += 1
  return {
    ok: true,
    async json() {
      return {
        data: {
          enabled: true,
          wsUrl: `ws://localhost:8080/api/v1/asr/stream?ticket=ticket-${fetchCount}`,
          ticket: `ticket-${fetchCount}`,
          expiresAt: Math.floor(Date.now() / 1000) + 300,
          audio: { format: 'pcm', sampleRate: 16000, channels: 1, encoding: 'pcm16le' },
          protocolVersion: 'v1',
        },
      }
    },
  }
}

const token1 = await service._fetchToken()
const token2 = await service._fetchToken()

assert.equal(token1.ticket, 'ticket-1', 'First token fetch should return the first ticket')
assert.equal(token2.ticket, 'ticket-2', 'ASR token fetch should not reuse cached one-time tickets')
assert.equal(fetchCount, 2, 'ASR token fetch should request a fresh proxy ticket every time')

assert.deepEqual(
  service._resolveAudioConfig({
    audio: { format: 'pcm', sampleRate: 8000, channels: 1, encoding: 'pcm16le' },
  }),
  { format: 'pcm', sampleRate: 8000, channels: 1, encoding: 'pcm16le' },
  'AsrService should use audio config returned by backend token'
)

let finalPayload = null
let interimPayload = null
service.onFinal = (text, pauseStats, segments, metadata) => {
  finalPayload = { text, pauseStats, segments, metadata }
}
service.onInterim = (text) => {
  interimPayload = text
}

service._handleWsMessage({
  data: JSON.stringify({
    type: 'segment_final',
    text: '你的。',
    beginTime: 100,
    endTime: 800,
  }),
})

assert.equal(
  interimPayload,
  '你的。',
  'AsrService should surface segment_final text as live recognition progress'
)

service._handleWsMessage({
  data: JSON.stringify({
    type: 'interim',
    text: '还有你刚才提到三元微服务购物社区。',
  }),
})

assert.equal(
  interimPayload,
  '你的。还有你刚才提到三元微服务购物社区。',
  'AsrService should merge committed segment_final text with subsequent interim text'
)

service._state = 'stopping'
service._closeWebSocket = () => {}
service._setState = (state) => {
  service._state = state
}

service._handleWsMessage({
  data: JSON.stringify({
    type: 'final',
    text: '你好',
    rawText: '你号',
    changeList: [{ from: '你号', to: '你好', reason: 'homophone' }],
    correctionApplied: true,
    pauseStats: { wpm: 120, longPauseCount: 1, longestPauseMs: 1800 },
    asrSegments: [{ text: '你好', beginTime: 0, endTime: 500 }],
  }),
})

assert.deepEqual(
  finalPayload,
  {
    text: '你好',
    pauseStats: { wpm: 120, longPauseCount: 1, longestPauseMs: 1800 },
    segments: [{ text: '你好', beginTime: 0, endTime: 500 }],
    metadata: {
      rawText: '你号',
      changeList: [{ from: '你号', to: '你好', reason: 'homophone' }],
      correctionApplied: true,
    },
  },
  'AsrService should surface backend final event as onFinal callback'
)

console.log('asr-service-proxy test passed')
