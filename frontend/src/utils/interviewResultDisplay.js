function normalizeInterviewQuestionStatus(value) {
  const normalized = String(value || '').trim().toLowerCase()
  if (['answered', 'skipped', 'pending'].includes(normalized)) {
    return normalized
  }
  return 'pending'
}

function hasNumericScore(value) {
  if (value == null || value === '') return false
  return Number.isFinite(Number(value))
}

export function getInterviewQuestionStatusText(status) {
  const normalized = normalizeInterviewQuestionStatus(status)
  if (normalized === 'answered') return '已完成'
  if (normalized === 'skipped') return '已跳过'
  return '未提交'
}

export function getInterviewQuestionCommentaryFallback(item) {
  const commentary = String(item?.commentary || '').trim()
  if (commentary) return commentary

  const status = normalizeInterviewQuestionStatus(item?.status)
  if (!hasNumericScore(item?.score)) {
    if (status === 'answered') return '本题复盘信息暂时缺失，请稍后重试或查看单题详情。'
    if (status === 'skipped') return '本题已跳过，建议优先补强该知识点。'
    return '本题尚未提交，手动结束后不会自动补交。'
  }

  return '本题正式评分已生成，但当前缺少单题点评文案，请查看正式报告或稍后重试。'
}

export function mapInterviewReportSkillDomains(reportScores = []) {
  if (!Array.isArray(reportScores)) return []
  return reportScores.map((item) => {
    const score = Number(item?.score)
    const hasScore = Number.isFinite(score)
    return {
      name: item?.domainName || item?.domainCode || '通用能力',
      weakPoints: String(item?.commentary || '').trim() || '正式报告未提供该知识域点评。',
      score: hasScore ? Math.round(score) : null,
      scoreText: hasScore ? `${Math.round(score)} 分` : null
    }
  })
}
