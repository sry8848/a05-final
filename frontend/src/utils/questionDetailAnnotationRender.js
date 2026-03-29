function normalizeLabel(value) {
  return String(value || '').trim().toLowerCase() === 'weakness' ? 'weakness' : 'strength'
}

export function normalizeHighlightedAnnotations(value) {
  if (!Array.isArray(value)) return []
  return value
    .map((item) => {
      if (!item || typeof item !== 'object') return null
      const start = Number(item.start)
      const end = Number(item.end)
      if (!Number.isInteger(start) || !Number.isInteger(end) || end <= start) return null
      return {
        start,
        end,
        quote: String(item.quote || '').trim(),
        label: normalizeLabel(item.label),
        comment: String(item.comment || '').trim()
      }
    })
    .filter(Boolean)
}

export function buildQuestionDetailRenderSlices({
  answerText = '',
  highlightedAnnotations = [],
  highlightedSegments = []
} = {}) {
  const normalizedAnswer = String(answerText || '')
  const normalizedAnnotations = normalizeHighlightedAnnotations(highlightedAnnotations)
    .filter((item) => item.start >= 0 && item.end <= normalizedAnswer.length)
    .sort((left, right) => left.start - right.start || left.end - right.end)

  if (normalizedAnnotations.length) {
    const slices = []
    let cursor = 0
    for (const annotation of normalizedAnnotations) {
      if (annotation.start < cursor) continue
      if (annotation.start > cursor) {
        slices.push({
          type: 'plain',
          text: normalizedAnswer.slice(cursor, annotation.start)
        })
      }
      slices.push({
        type: annotation.label,
        text: normalizedAnswer.slice(annotation.start, annotation.end),
        note: annotation.comment,
        start: annotation.start,
        end: annotation.end,
        quote: annotation.quote
      })
      cursor = annotation.end
    }
    if (cursor < normalizedAnswer.length) {
      slices.push({
        type: 'plain',
        text: normalizedAnswer.slice(cursor)
      })
    }
    return slices.filter((item) => item.text)
  }

  if (!Array.isArray(highlightedSegments) || !highlightedSegments.length) {
    return []
  }

  return highlightedSegments
    .map((item) => {
      if (!item || typeof item !== 'object') return null
      const text = String(item.segment || item.text || '').trim()
      if (!text) return null
      return {
        type: normalizeLabel(item.label || item.type),
        text,
        note: String(item.comment || item.note || '').trim()
      }
    })
    .filter(Boolean)
}
