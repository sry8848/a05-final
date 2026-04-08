# 用户端数据分析页删除设计

## 背景

当前前端同时存在两套“数据分析”概念：

- 用户端数据分析页
  - `frontend/src/components/AnalysisPage.vue`
  - `frontend/src/components/Sidebar.vue`
  - `frontend/src/App.vue`
- 管理端数据分析页
  - `frontend/src/components/DataAnalysis.vue`
  - `frontend/src/components/AdminLayout.vue`

本次需求明确为：删除用户端数据分析页，保留管理端数据分析页。

## 目标

让普通用户界面不再出现“数据分析”页面、菜单和可达状态；同时不影响管理端 `analysis` 页面。

## 范围

本次只处理用户端：

- 删除侧边栏“数据分析”入口
- 删除 `App.vue` 中用户端 `analysis` 页面分支
- 删除用户端分析页组件文件
- 同步收紧与用户端页面状态相关的测试

本次不处理：

- 管理端 `DataAnalysis.vue`
- 管理端 `AdminLayout.vue` 的 `analysis` 菜单和页面渲染
- 任何后端接口

## 设计决策

### 1. 采用彻底删除，而不是隐藏入口

原因：

- 隐藏入口但保留页面会留下死逻辑
- 历史状态恢复、键盘快捷键或代码调用仍可能进入该页
- 需求是“删掉”，不是“临时下线”

因此本次采用物理删除：

- 删组件
- 删菜单
- 删页面状态分支
- 删用户端可达路径

### 2. 管理端 `analysis` 不动

仓库里管理端 `analysis` 由 `AdminLayout.vue` 和 `DataAnalysis.vue` 承担，属于另一套界面状态。本次需求已经明确保留，因此不得误删。

## 受影响文件

- `frontend/src/App.vue`
- `frontend/src/components/Sidebar.vue`
- `frontend/src/components/AnalysisPage.vue`
- `frontend/tests/auth-view-state.test.js`

## 行为变化

删除后，用户端应满足以下行为：

- 侧边栏不再显示“数据分析”
- 用户端主内容区不再渲染 `AnalysisPage`
- 用户端快捷键页签列表不再包含 `analysis`
- 登出等视图状态逻辑不再依赖用户端 `analysis` 作为合法落点

## 风险与处理

### 风险 1：只删 UI，不删状态分支

如果只删菜单，`currentPage` 仍可能被赋值为 `analysis`，页面会进入空白分支或不可预期状态。

处理：

- 同步删除 `App.vue` 中 `analysis` 分支
- 删除快捷键页列表中的 `analysis`

### 风险 2：误伤管理端分析页

项目里同名概念容易误删管理端入口。

处理：

- 本次只修改用户端 `Sidebar.vue`、`App.vue`、`AnalysisPage.vue`
- 明确不修改 `AdminLayout.vue` 和 `DataAnalysis.vue`

## 验收标准

- 用户端侧边栏不存在“数据分析”
- 用户端 `App.vue` 不再导入或渲染 `AnalysisPage`
- `frontend/src/components/AnalysisPage.vue` 被删除
- 相关测试通过
- 前端构建通过
