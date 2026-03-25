export function formatTrendTooltipDate(value) {
  const text = String(value ?? '').trim()
  if (!text) return ''

  const fullDateMatch = text.match(/^(\d{4}-\d{2}-\d{2})/)
  if (fullDateMatch) {
    return fullDateMatch[1]
  }

  return text
}

export function takeRecentTrendPoints(values, limit = 8) {
  if (!Array.isArray(values) || values.length === 0) return []

  const numericLimit = Math.max(1, Math.floor(Number(limit) || 0))
  if (values.length <= numericLimit) {
    return values
  }

  return values.slice(-numericLimit)
}
