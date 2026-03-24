export function formatTrendTooltipDate(value) {
  const text = String(value ?? '').trim()
  if (!text) return ''

  const fullDateMatch = text.match(/^(\d{4}-\d{2}-\d{2})/)
  if (fullDateMatch) {
    return fullDateMatch[1]
  }

  return text
}

export function formatTrendAxisDate(value) {
  const fullDate = formatTrendTooltipDate(value)
  if (!fullDate) return ''

  const compactDateMatch = fullDate.match(/^\d{4}-(\d{2}-\d{2})$/)
  if (compactDateMatch) {
    return compactDateMatch[1]
  }

  return fullDate
}

export function buildSparseDateAxisLabels(values, maxVisibleLabels = 5) {
  const labels = Array.isArray(values) ? values.map((item) => formatTrendAxisDate(item)) : []
  if (!labels.length) return []

  const numericMax = Math.max(2, Math.floor(Number(maxVisibleLabels) || 0))
  if (labels.length <= numericMax) {
    return labels
  }

  const visibleIndexes = new Set()
  for (let slot = 0; slot < numericMax; slot += 1) {
    const index = Math.round((slot * (labels.length - 1)) / (numericMax - 1))
    visibleIndexes.add(index)
  }

  return labels.map((label, index) => (visibleIndexes.has(index) ? label : ''))
}
