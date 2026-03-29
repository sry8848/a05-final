import test from 'node:test'
import assert from 'node:assert/strict'

import {
  formatTrendTooltipDate,
  takeRecentTrendPoints
} from '../src/utils/growthTrendAxis.js'

test('formatTrendTooltipDate should keep full date for hover details', () => {
  assert.equal(formatTrendTooltipDate('2026-03-14T08:30:00'), '2026-03-14')
  assert.equal(formatTrendTooltipDate('2026-03-14'), '2026-03-14')
})

test('takeRecentTrendPoints should keep only the latest 8 items when trend data is longer', () => {
  const points = Array.from({ length: 10 }, (_, index) => ({
    date: `2026-03-${String(index + 10).padStart(2, '0')}`,
    score: index
  }))

  const recent = takeRecentTrendPoints(points, 8)

  assert.equal(recent.length, 8)
  assert.deepEqual(
    recent.map((item) => item.date),
    [
      '2026-03-12',
      '2026-03-13',
      '2026-03-14',
      '2026-03-15',
      '2026-03-16',
      '2026-03-17',
      '2026-03-18',
      '2026-03-19'
    ]
  )
})

test('takeRecentTrendPoints should keep all items when trend data is already within 8', () => {
  const points = [
    { date: '2026-03-14', score: 81 },
    { date: '2026-03-15', score: 83 },
    { date: '2026-03-16', score: 85 }
  ]

  assert.deepEqual(takeRecentTrendPoints(points, 8), points)
})

test('takeRecentTrendPoints should return an empty list for invalid input', () => {
  assert.deepEqual(takeRecentTrendPoints(null, 8), [])
})
