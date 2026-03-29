function normalizeStatus(value) {
  const normalized = String(value || '').trim().toLowerCase()
  if (['ready', 'generating', 'failed', 'cancelled'].includes(normalized)) {
    return normalized
  }
  return 'ready'
}

function normalizeId(value) {
  if (value == null || value === '') return null
  const numeric = Number(value)
  return Number.isFinite(numeric) ? numeric : null
}

function normalizeRole(value) {
  return String(value || '').trim().toLowerCase() === 'assistant' ? 'assistant' : 'user'
}

function normalizeContent(value) {
  return typeof value === 'string' ? value : String(value || '')
}

export function normalizeQuestionConsultMessage(value) {
  if (!value || typeof value !== 'object') return null
  const id = normalizeId(value.id)
  if (id == null) return null

  return {
    id,
    role: normalizeRole(value.role),
    status: normalizeStatus(value.status),
    content: normalizeContent(value.content),
    replyToMessageId: normalizeId(value.replyToMessageId),
    createdAt: value.createdAt || null
  }
}

export function normalizeQuestionConsultMessages(value) {
  if (!Array.isArray(value)) return []
  return value.map(normalizeQuestionConsultMessage).filter(Boolean)
}

export function buildOptimisticQuestionConsultMessages(content, ids, createdAt = new Date().toISOString()) {
  const userMessageId = normalizeId(ids?.userMessageId)
  const assistantMessageId = normalizeId(ids?.assistantMessageId)
  if (userMessageId == null || assistantMessageId == null) {
    return []
  }

  return [
    {
      id: userMessageId,
      role: 'user',
      status: 'ready',
      content: String(content || '').trim(),
      replyToMessageId: null,
      createdAt
    },
    {
      id: assistantMessageId,
      role: 'assistant',
      status: 'generating',
      content: '',
      replyToMessageId: userMessageId,
      createdAt
    }
  ]
}

export function appendQuestionConsultDelta(messages, assistantMessageId, text) {
  const targetId = normalizeId(assistantMessageId)
  if (!Array.isArray(messages) || targetId == null || !text) return Array.isArray(messages) ? messages : []
  return messages.map((message) => {
    if (message?.id !== targetId) return message
    return {
      ...message,
      content: `${normalizeContent(message.content)}${String(text)}`
    }
  })
}

export function replaceQuestionConsultMessage(messages, assistantMessageId, patch = {}) {
  const targetId = normalizeId(assistantMessageId)
  if (!Array.isArray(messages) || targetId == null) return Array.isArray(messages) ? messages : []
  return messages.map((message) => {
    if (message?.id !== targetId) return message
    return {
      ...message,
      ...(patch.content == null ? {} : { content: normalizeContent(patch.content) }),
      ...(patch.status == null ? {} : { status: normalizeStatus(patch.status) }),
      ...(patch.createdAt == null ? {} : { createdAt: patch.createdAt })
    }
  })
}
