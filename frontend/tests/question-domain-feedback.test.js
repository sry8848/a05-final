import test from 'node:test'
import assert from 'node:assert/strict'

import { buildEvaluatedDomainFeedback } from '../src/utils/questionDomainFeedback.js'

test('buildEvaluatedDomainFeedback should drop numeric scores and keep commentary only', () => {
  const items = buildEvaluatedDomainFeedback([
    {
      domainCode: 'browser',
      domainName: '浏览器原理',
      score: 91,
      commentary: '主线表达稳定'
    }
  ])

  assert.deepEqual(items, [
    {
      domainCode: 'browser',
      domainName: '浏览器原理',
      commentary: '主线表达稳定'
    }
  ])
})

test('buildEvaluatedDomainFeedback should fall back to available note text', () => {
  const items = buildEvaluatedDomainFeedback([
    {
      domainCode: 'network',
      domainName: '',
      note: '先补齐三次握手与四次挥手的边界区别'
    }
  ])

  assert.deepEqual(items, [
    {
      domainCode: 'network',
      domainName: 'network',
      commentary: '先补齐三次握手与四次挥手的边界区别'
    }
  ])
})
