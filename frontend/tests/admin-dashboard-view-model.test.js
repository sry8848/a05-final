import test from 'node:test'
import assert from 'node:assert/strict'

async function loadAdminDashboardViewModelModule() {
  try {
    return await import('../src/utils/adminDashboardViewModel.js')
  } catch {
    return {}
  }
}

test('buildAdminDashboardViewModel should map overview and token metrics as counts, not currency', async () => {
  const { buildAdminDashboardViewModel } = await loadAdminDashboardViewModelModule()

  assert.equal(
    typeof buildAdminDashboardViewModel,
    'function',
    'buildAdminDashboardViewModel must be implemented'
  )

  const viewModel = buildAdminDashboardViewModel({
    overview: {
      totalUsers: 2847,
      newUsersToday: 156,
      totalInterviews: 15632,
      interviewsToday: 423,
      activeInterviews: 23,
      totalTokensToday: 23456
    },
    trends: {
      dates: ['03-16', '03-17', '03-18', '03-19', '03-20', '03-21', '03-22'],
      newUsers: [120, 145, 132, 178, 156, 189, 156],
      interviews: [320, 380, 350, 420, 390, 450, 423],
      totalTokens: [1800, 2100, 1950, 2450, 2200, 2600, 2345]
    }
  })

  assert.deepEqual(viewModel.dateLabels, ['03-16', '03-17', '03-18', '03-19', '03-20', '03-21', '03-22'])
  assert.equal(viewModel.overviewStats[2].label, '活跃面试数')
  assert.equal(viewModel.overviewStats[2].value, '23')
  assert.equal(viewModel.overviewStats[3].label, '今日Token消耗')
  assert.equal(viewModel.overviewStats[3].value, '23,456')
  assert.equal(viewModel.overviewStats[3].meta.label, '今日累计')
  assert.equal(viewModel.overviewStats[3].meta.value, '23,456')
  assert.equal(viewModel.weeklyTokenStats.total, '15,445')
  assert.equal(viewModel.weeklyTokenStats.avg, '2,206')
  assert.equal(viewModel.overviewStats[3].value.includes('$'), false)
})

test('buildAdminDashboardViewModel should derive model panel badge and error rate text', async () => {
  const { buildAdminDashboardViewModel } = await loadAdminDashboardViewModelModule()

  const viewModel = buildAdminDashboardViewModel({
    models: [
      {
        modelProvider: 'openai',
        modelName: 'gpt-4.1-mini',
        requestCount: 120,
        successCount: 117,
        errorCount: 3,
        successRate: 0.975,
        avgLatencyMs: 1340,
        p95LatencyMs: 1880,
        status: 'warning'
      },
      {
        modelProvider: 'mock',
        modelName: 'unit-test',
        requestCount: 12,
        successCount: 12,
        errorCount: 0,
        successRate: 1,
        avgLatencyMs: 32,
        p95LatencyMs: 40,
        status: 'healthy'
      }
    ]
  })

  assert.equal(viewModel.modelPanel.badge.text, '存在警告')
  assert.equal(viewModel.modelPanel.items[0].providerLabel, 'OpenAI')
  assert.equal(viewModel.modelPanel.items[0].errorRateText, '2.50%')
  assert.equal(viewModel.modelPanel.items[0].latencyText, '1340ms')
  assert.equal(viewModel.modelPanel.items[0].p95LatencyText, 'P95 1880ms')
  assert.equal(viewModel.modelPanel.items[1].providerLabel, 'Mock')
})

test('buildAdminDashboardViewModel should show no-traffic model badge when models are empty', async () => {
  const { buildAdminDashboardViewModel } = await loadAdminDashboardViewModelModule()

  const viewModel = buildAdminDashboardViewModel({
    models: []
  })

  assert.equal(viewModel.modelPanel.badge.text, '最近15分钟暂无调用')
  assert.equal(viewModel.modelPanel.items.length, 0)
})

test('buildAdminDashboardViewModel should map prompt summaries and warning state correctly', async () => {
  const { buildAdminDashboardViewModel } = await loadAdminDashboardViewModelModule()

  const viewModel = buildAdminDashboardViewModel({
    prompts: [
      {
        promptCode: 'system_prompt',
        configuredVersion: '3.1.2',
        templateVersion: '3.1.2',
        sourcePath: '/prompts/system/v3.1.2.md',
        callsLast24Hours: 98,
        lastUsedAt: '2026-03-22T08:15:00+08:00'
      },
      {
        promptCode: 'evaluation_prompt',
        configuredVersion: '2.0.0',
        templateVersion: '2.1.0',
        sourcePath: '/prompts/eval/v2.1.0.md',
        callsLast24Hours: 0,
        lastUsedAt: null
      }
    ],
    models: [
      {
        modelProvider: 'openai',
        modelName: 'gpt-4.1-mini',
        requestCount: 8,
        successCount: 8,
        errorCount: 0,
        successRate: 1,
        avgLatencyMs: 600,
        p95LatencyMs: 900,
        status: 'healthy'
      }
    ],
    currentAdminName: '超级管理员',
    lastRefreshedAt: '2026-03-22T10:30:00+08:00'
  })

  assert.equal(viewModel.promptSummaryPanel.items[0].subtitle, '配置 v3.1.2 / 模板 v3.1.2')
  assert.equal(viewModel.promptSummaryPanel.items[0].usageText, '24小时调用 98 次')
  assert.equal(viewModel.promptSummaryPanel.items[1].status, 'warning')
  assert.equal(viewModel.promptSummaryPanel.items[1].timeText, '未使用')
  assert.equal(viewModel.promptSummaryPanel.items[1].usageText, '24小时调用 0 次')
  assert.equal(viewModel.systemSummaryPanel.items[0].label, '当前管理员')
  assert.equal(viewModel.systemSummaryPanel.items[0].value, '超级管理员')
  assert.equal(viewModel.systemSummaryPanel.items[1].label, '最近刷新')
  assert.equal(viewModel.systemSummaryPanel.items[1].value, '2026-03-22 10:30')
  assert.equal(viewModel.systemSummaryPanel.items[2].value, '1')
  assert.equal(viewModel.systemSummaryPanel.items[3].value, '2')
})

test('buildAdminDashboardViewModel should render healthy system ping summary line', async () => {
  const { buildAdminDashboardViewModel } = await loadAdminDashboardViewModelModule()

  const viewModel = buildAdminDashboardViewModel({
    systemPing: {
      serverTime: '2026-03-22T02:30:00Z'
    },
    systemPingState: {
      loading: false,
      error: '',
      empty: false
    }
  })

  assert.equal(viewModel.systemSummaryPanel.statusTone, 'healthy')
  assert.equal(
    viewModel.systemSummaryPanel.statusLine,
    '服务状态 连通正常 · 服务端时间 2026-03-22 10:30 · 时区 Asia/Shanghai'
  )
})

test('buildAdminDashboardViewModel should render degraded system ping copy for stale and empty states', async () => {
  const { buildAdminDashboardViewModel } = await loadAdminDashboardViewModelModule()

  const staleViewModel = buildAdminDashboardViewModel({
    systemPing: {
      serverTime: '2026-03-22T02:30:00Z'
    },
    systemPingState: {
      loading: false,
      error: 'ping failed',
      empty: false
    }
  })

  assert.equal(staleViewModel.systemSummaryPanel.statusTone, 'warning')
  assert.equal(
    staleViewModel.systemSummaryPanel.statusLine,
    '系统连通信息暂不可用，摘要数据继续沿用最近结果'
  )

  const emptyViewModel = buildAdminDashboardViewModel({
    systemPing: null,
    systemPingState: {
      loading: false,
      error: 'ping failed',
      empty: true
    }
  })

  assert.equal(emptyViewModel.systemSummaryPanel.statusTone, 'neutral')
  assert.equal(emptyViewModel.systemSummaryPanel.statusLine, '系统连通信息暂不可用')
})
