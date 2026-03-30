import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const projectRoot = path.resolve(__dirname, '..')

function readProjectFile(relativePath) {
  return fs.readFileSync(path.join(projectRoot, relativePath), 'utf8')
}

test('user sidebar should not expose analysis entry', () => {
  const sidebarSource = readProjectFile('src/components/Sidebar.vue')

  assert.equal(
    sidebarSource.includes("@click=\"$emit('navigate', 'analysis')\""),
    false,
    '用户端侧边栏不应再暴露 analysis 导航入口'
  )
})

test('user app shell should not import or render AnalysisPage', () => {
  const appSource = readProjectFile('src/App.vue')

  assert.equal(
    appSource.includes("import AnalysisPage from './components/AnalysisPage.vue'"),
    false,
    'App.vue 不应再导入用户端 AnalysisPage'
  )

  assert.equal(
    appSource.includes("<AnalysisPage v-else-if=\"currentPage === 'analysis'\" />"),
    false,
    'App.vue 不应再渲染用户端 AnalysisPage'
  )

  assert.equal(
    appSource.includes("['growth', 'interview', 'history', 'analysis', 'settings']"),
    false,
    '用户端快捷键页签列表不应再包含 analysis'
  )
})

test('user analysis component file should be removed while admin analysis stays', () => {
  const userAnalysisPath = path.join(projectRoot, 'src/components/AnalysisPage.vue')
  const adminLayoutSource = readProjectFile('src/components/AdminLayout.vue')

  assert.equal(
    fs.existsSync(userAnalysisPath),
    false,
    '用户端 AnalysisPage.vue 应被删除'
  )

  assert.equal(
    adminLayoutSource.includes("<DataAnalysis v-else-if=\"currentPage === 'analysis'\" />"),
    true,
    '管理端 analysis 页面必须保留'
  )
})
