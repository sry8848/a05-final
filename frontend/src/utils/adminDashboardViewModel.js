const PROVIDER_LABELS = {
  openai: 'OpenAI',
  mock: 'Mock'
}

function toSafeNumber(value) {
  return Number.isFinite(Number(value)) ? Number(value) : 0
}

function formatCount(value) {
  return toSafeNumber(value).toLocaleString('zh-CN')
}

function sumSeries(series) {
  return series.reduce((total, item) => total + toSafeNumber(item), 0)
}

function buildWeeklyStats(series) {
  if (!Array.isArray(series) || series.length === 0) {
    return { total: '0', avg: '0' }
  }

  const total = sumSeries(series)
  return {
    total: formatCount(total),
    avg: formatCount(Math.round(total / series.length))
  }
}

function formatRefreshTime(value) {
  if (!value) {
    return '未刷新'
  }

  if (typeof value === 'string' && /^\d{4}-\d{2}-\d{2} \d{2}:\d{2}$/.test(value)) {
    return value
  }

  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    const match = String(value).match(/^(\d{4}-\d{2}-\d{2})T(\d{2}:\d{2})/)
    return match ? `${match[1]} ${match[2]}` : '未刷新'
  }

  const formatter = new Intl.DateTimeFormat('zh-CN', {
    timeZone: 'Asia/Shanghai',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false
  })

  return formatter.format(date).replace(/\//g, '-').replace(',', '')
}

function hasSystemPingData(systemPing) {
  return Boolean(systemPing && typeof systemPing.serverTime === 'string' && systemPing.serverTime.trim())
}

function formatPromptTime(value) {
  if (!value) {
    return '未使用'
  }

  const match = String(value).match(/^\d{4}-(\d{2}-\d{2})T(\d{2}:\d{2})/)
  if (match) {
    return `${match[1]} ${match[2]}`
  }

  return formatRefreshTime(value)
}

function resolveProviderLabel(provider) {
  if (!provider || !String(provider).trim()) {
    return 'Unknown'
  }

  return PROVIDER_LABELS[String(provider).toLowerCase()] || provider
}

function resolveModelBadge(items) {
  if (!items.length) {
    return { text: '最近15分钟暂无调用', tone: 'neutral' }
  }

  if (items.some((item) => item.status === 'error')) {
    return { text: '存在异常', tone: 'error' }
  }

  if (items.some((item) => item.status === 'warning')) {
    return { text: '存在警告', tone: 'warning' }
  }

  return { text: '全部正常', tone: 'healthy' }
}

function buildOverviewStats(overview) {
  return [
    {
      key: 'totalUsers',
      label: '总用户数',
      value: formatCount(overview.totalUsers),
      meta: {
        tone: 'up',
        value: `+${formatCount(overview.newUsersToday)}`,
        label: '今日新增'
      },
      iconClass: 'users'
    },
    {
      key: 'totalInterviews',
      label: '面试总场次',
      value: formatCount(overview.totalInterviews),
      meta: {
        tone: 'up',
        value: `+${formatCount(overview.interviewsToday)}`,
        label: '今日场次'
      },
      iconClass: 'interviews'
    },
    {
      key: 'activeInterviews',
      label: '活跃面试数',
      value: formatCount(overview.activeInterviews),
      meta: {
        tone: 'live',
        value: '进行中',
        label: '实时状态'
      },
      iconClass: 'active',
      highlight: true
    },
    {
      key: 'totalTokensToday',
      label: '今日Token消耗',
      value: formatCount(overview.totalTokensToday),
      meta: {
        tone: 'neutral',
        value: formatCount(overview.totalTokensToday),
        label: '今日累计'
      },
      iconClass: 'tokens'
    }
  ]
}

function buildModelPanel(models) {
  const items = (Array.isArray(models) ? models : []).map((item) => {
    const successRate = Math.max(0, Math.min(1, Number(item.successRate ?? 0)))
    const errorRate = (1 - successRate) * 100
    return {
      key: `${item.modelProvider || 'unknown'}:${item.modelName || 'unknown'}`,
      providerLabel: resolveProviderLabel(item.modelProvider),
      modelName: item.modelName || 'unknown',
      requestCountText: `${formatCount(item.requestCount)} 次`,
      latencyText: `${toSafeNumber(item.avgLatencyMs)}ms`,
      p95LatencyText: item.p95LatencyMs == null ? 'P95 -' : `P95 ${toSafeNumber(item.p95LatencyMs)}ms`,
      errorRateText: `${errorRate.toFixed(2)}%`,
      status: item.status || 'warning'
    }
  })

  return {
    badge: resolveModelBadge(items),
    items
  }
}

function buildPromptSummaryPanel(prompts) {
  const items = (Array.isArray(prompts) ? prompts : []).map((item) => {
    const configuredVersion = item.configuredVersion || '-'
    const templateVersion = item.templateVersion || '-'
    return {
      key: item.promptCode || `prompt-${configuredVersion}`,
      title: item.promptCode || 'unknown_prompt',
      subtitle: `配置 v${configuredVersion} / 模板 v${templateVersion}`,
      versionText: configuredVersion,
      timeText: formatPromptTime(item.lastUsedAt),
      usageText: `24小时调用 ${formatCount(item.callsLast24Hours)} 次`,
      status: configuredVersion === templateVersion ? 'healthy' : 'warning',
      sourcePath: item.sourcePath || ''
    }
  })

  return {
    items
  }
}

function normalizeSeries(series) {
  return Array.isArray(series) ? series.map((item) => toSafeNumber(item)) : []
}

function buildSystemSummaryPanel({
  currentAdminName,
  lastRefreshedAt,
  modelCount,
  promptCount,
  systemPing,
  systemPingState
}) {
  const hasPing = hasSystemPingData(systemPing)
  const isLoading = Boolean(systemPingState?.loading)
  const hasError = Boolean(systemPingState?.error)

  let statusLine = '系统连通信息暂不可用'
  let statusTone = 'neutral'

  if (isLoading && !hasPing && !hasError) {
    statusLine = '服务状态 检测中...'
  } else if (hasError && hasPing) {
    statusLine = '系统连通信息暂不可用，摘要数据继续沿用最近结果'
    statusTone = 'warning'
  } else if (hasError) {
    statusLine = '系统连通信息暂不可用'
  } else if (hasPing) {
    statusLine = `服务状态 连通正常 · 服务端时间 ${formatRefreshTime(systemPing.serverTime)} · 时区 Asia/Shanghai`
    statusTone = 'healthy'
  }

  return {
    statusLine,
    statusTone,
    items: [
      { label: '当前管理员', value: currentAdminName || '管理员' },
      { label: '最近刷新', value: formatRefreshTime(lastRefreshedAt) },
      { label: '当前模型数', value: formatCount(modelCount) },
      { label: '当前 Prompt 数', value: formatCount(promptCount) }
    ]
  }
}

export function buildAdminDashboardViewModel({
  overview = {},
  trends = {},
  models = [],
  prompts = [],
  systemPing = null,
  systemPingState = {},
  currentAdminName = '管理员',
  lastRefreshedAt = null
} = {}) {
  const dateLabels = Array.isArray(trends.dates) ? trends.dates : []
  const userSeries = normalizeSeries(trends.newUsers)
  const interviewSeries = normalizeSeries(trends.interviews)
  const tokenSeries = normalizeSeries(trends.totalTokens)
  const modelPanel = buildModelPanel(models)
  const promptSummaryPanel = buildPromptSummaryPanel(prompts)

  return {
    overviewStats: buildOverviewStats(overview),
    dateLabels,
    userSeries,
    interviewSeries,
    tokenSeries,
    weeklyUserStats: buildWeeklyStats(userSeries),
    weeklyInterviewStats: buildWeeklyStats(interviewSeries),
    weeklyTokenStats: buildWeeklyStats(tokenSeries),
    modelPanel,
    systemSummaryPanel: buildSystemSummaryPanel({
      currentAdminName,
      lastRefreshedAt,
      modelCount: modelPanel.items.length,
      promptCount: promptSummaryPanel.items.length,
      systemPing,
      systemPingState
    }),
    promptSummaryPanel
  }
}
