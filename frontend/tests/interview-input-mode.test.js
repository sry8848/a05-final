import test from 'node:test'
import assert from 'node:assert/strict'

import {
  buildVoiceFailureMessage,
  canStartProfessionalInterview,
  normalizeInputMode,
  shouldRenderTextInput,
  shouldShowInputModeToggle
} from '../src/utils/interviewInputMode.js'

test('normalizeInputMode should force professional mode to stay on voice', () => {
  assert.equal(normalizeInputMode('professional', 'text'), 'voice')
  assert.equal(normalizeInputMode('professional', 'voice'), 'voice')
})

test('normalizeInputMode should allow practice mode to switch between text and voice', () => {
  assert.equal(normalizeInputMode('practice', 'text'), 'text')
  assert.equal(normalizeInputMode('practice', 'voice'), 'voice')
  assert.equal(normalizeInputMode('practice', 'unknown'), 'text')
})

test('professional mode should hide text UI and require microphone plus ASR to start', () => {
  assert.equal(shouldShowInputModeToggle('professional'), false)
  assert.equal(shouldRenderTextInput('professional', 'text'), false)
  assert.equal(canStartProfessionalInterview({ microphoneReady: true, asrAvailable: true }), true)
  assert.equal(canStartProfessionalInterview({ microphoneReady: true, asrAvailable: false }), false)
  assert.equal(canStartProfessionalInterview({ microphoneReady: false, asrAvailable: true }), false)
})

test('practice mode should keep text UI available and expose manual fallback wording', () => {
  assert.equal(shouldShowInputModeToggle('practice'), true)
  assert.equal(shouldRenderTextInput('practice', 'text'), true)
  assert.match(buildVoiceFailureMessage('practice'), /切换到文字输入/)
})

test('professional mode should expose strict voice-only failure wording', () => {
  assert.match(buildVoiceFailureMessage('professional'), /仅支持语音/)
  assert.doesNotMatch(buildVoiceFailureMessage('professional'), /切换到文字输入/)
})
