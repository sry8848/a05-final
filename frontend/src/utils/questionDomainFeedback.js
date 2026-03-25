export function buildEvaluatedDomainFeedback(value) {
  if (!Array.isArray(value)) return []
  return value
    .map((item) => {
      if (!item || typeof item !== 'object') return null
      const domainCode = String(item.domainCode || '').trim()
      const domainName = String(item.domainName || '').trim() || domainCode
      const commentary = String(item.commentary || item.note || '').trim()
      if (!domainName && !commentary) return null
      return {
        domainCode,
        domainName,
        commentary
      }
    })
    .filter(Boolean)
}
