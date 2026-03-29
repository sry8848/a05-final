function normalizeInterviewMode(interviewMode) {
  return interviewMode === 'professional' ? 'professional' : 'practice'
}

export function normalizeInputMode(interviewMode, requestedMode) {
  if (normalizeInterviewMode(interviewMode) === 'professional') {
    return 'voice'
  }
  return requestedMode === 'voice' ? 'voice' : 'text'
}

export function shouldShowInputModeToggle(interviewMode) {
  return normalizeInterviewMode(interviewMode) !== 'professional'
}

export function shouldRenderTextInput(interviewMode, inputMode) {
  return shouldShowInputModeToggle(interviewMode) && normalizeInputMode(interviewMode, inputMode) === 'text'
}

export function canStartProfessionalInterview({ microphoneReady, asrAvailable }) {
  return Boolean(microphoneReady) && Boolean(asrAvailable)
}

export function buildProfessionalInterviewStartBlockedReason({ microphoneReady, asrAvailable }) {
  if (!microphoneReady) {
    return '麦克风检测未通过，请修复后重新检测。'
  }
  if (!asrAvailable) {
    return '当前浏览器语音识别不可用，专业模式仅支持语音，请返回配置页或更换浏览器后重试。'
  }
  return ''
}

export function buildVoiceFailureMessage(interviewMode) {
  if (normalizeInterviewMode(interviewMode) === 'professional') {
    return '专业模式仅支持语音输入，请重试麦克风/语音识别或结束面试。'
  }
  return '当前语音识别不可用，请重试或切换到文字输入。'
}
