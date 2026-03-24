import test from 'node:test'
import assert from 'node:assert/strict'

import {
  buildSparseDateAxisLabels,
  formatTrendAxisDate,
  formatTrendTooltipDate
} from '../src/utils/growthTrendAxis.js'

test('formatTrendAxisDate should convert full date into compact month-day label', () => {
  assert.equal(formatTrendAxisDate('2026-03-14'), '03-14')
  assert.equal(formatTrendAxisDate('2026-03-14T08:30:00'), '03-14')
  assert.equal(formatTrendAxisDate('03-14'), '03-14')
})

test('formatTrendTooltipDate should keep full date for hover details', () => {
  assert.equal(formatTrendTooltipDate('2026-03-14T08:30:00'), '2026-03-14')
  assert.equal(formatTrendTooltipDate('2026-03-14'), '2026-03-14')
})

test('buildSparseDateAxisLabels should keep axis readable when there are many trend points', () => {
  const labels = buildSparseDateAxisLabels([
    '2026-03-14',
    '2026-03-15',
    '2026-03-16',
    '2026-03-17',
    '2026-03-18',
    '2026-03-19',
    '2026-03-20',
    '2026-03-21'
  ], 4)

  assert.equal(labels.length, 8)
  assert.equal(labels[0], '03-14')
  assert.equal(labels.at(-1), '03-21')
  assert.equal(labels.filter(Boolean).length, 4)
})

test('buildSparseDateAxisLabels should keep all labels when points are already few', () => {
  const labels = buildSparseDateAxisLabels(['2026-03-14', '2026-03-15', '2026-03-16'], 4)

  assert.deepEqual(labels, ['03-14', '03-15', '03-16'])
})
