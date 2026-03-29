import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const styleSource = readFileSync(
  new URL('../css/style.css', import.meta.url),
  'utf8'
)
const indexHtmlSource = readFileSync(
  new URL('../index.html', import.meta.url),
  'utf8'
)
const resumesPageSource = readFileSync(
  new URL('../src/components/ResumesPage.vue', import.meta.url),
  'utf8'
)
const adminLayoutSource = readFileSync(
  new URL('../src/components/AdminLayout.vue', import.meta.url),
  'utf8'
)

test('runtime stylesheet should define dedicated modal surface tokens and shared panel styles', () => {
  assert.match(indexHtmlSource, /href="\/css\/style\.css"/)
  assert.match(styleSource, /--modal-surface-bg:/)
  assert.match(styleSource, /--modal-surface-border:/)
  assert.match(styleSource, /--modal-surface-shadow:/)
  assert.match(styleSource, /--modal-input-bg:/)
  assert.match(styleSource, /\.modal-panel\s*\{/)
})

test('resume modals should use modal panel styling instead of glass-card', () => {
  assert.match(resumesPageSource, /class="modal-content modal-panel upload-modal"/)
  assert.match(resumesPageSource, /class="modal-content modal-panel detail-modal"/)
  assert.doesNotMatch(resumesPageSource, /class="modal-content glass-card upload-modal"/)
  assert.doesNotMatch(resumesPageSource, /class="modal-content glass-card detail-modal"/)
  assert.match(resumesPageSource, /background:\s*var\(--modal-input-bg\);/)
})

test('admin profile modal should use the shared bright modal panel in all themes', () => {
  assert.match(adminLayoutSource, /class="profile-modal modal-panel"/)
  assert.doesNotMatch(adminLayoutSource, /class="profile-modal glass-card"/)
  assert.match(adminLayoutSource, /background:\s*var\(--modal-input-bg\);/)
  assert.doesNotMatch(adminLayoutSource, /:root\.dark \.profile-modal/)
  assert.doesNotMatch(adminLayoutSource, /\.dark \.profile-modal/)
})
