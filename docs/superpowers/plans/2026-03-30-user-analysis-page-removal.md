# 用户端数据分析页删除 Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 删除用户端数据分析页及其入口，同时保证管理端数据分析页保持可用。

**Architecture:** 改动限定在用户端壳层入口和页面装配层，不引入新路由系统，也不改管理端页面结构。通过测试先锁定用户视图状态，再删除用户端页面组件和导航入口，最后跑构建回归。

**Tech Stack:** Vue 3, Vite, node:test.

---

## Chunk 1: 测试先行锁定删除范围

### Task 1: 收紧用户端视图状态测试

**Files:**
- Modify: `frontend/tests/auth-view-state.test.js`
- Test: `frontend/tests/auth-view-state.test.js`

- [ ] **Step 1: 写失败测试**

把当前登出测试中的 `currentPage: 'analysis'` 改成一个仍然合法但非分析页的用户端页面值，确保测试不再把用户端分析页视为可接受状态样本。

- [ ] **Step 2: 运行定向测试，确认先失败或至少覆盖到删除意图**

Run: `cd frontend && node --test tests/auth-view-state.test.js`

Expected:
- 当前测试覆盖的视图状态不再依赖 `analysis`

## Chunk 2: 删除用户端入口与页面实现

### Task 2: 删除用户端菜单和页面分支

**Files:**
- Modify: `frontend/src/components/Sidebar.vue`
- Modify: `frontend/src/App.vue`
- Delete: `frontend/src/components/AnalysisPage.vue`

- [ ] **Step 1: 删除 `Sidebar.vue` 中用户端“数据分析”菜单**
- [ ] **Step 2: 删除 `App.vue` 中 `AnalysisPage` 的 import、注册和渲染分支**
- [ ] **Step 3: 删除用户端快捷键页签数组中的 `analysis`**
- [ ] **Step 4: 删除 `frontend/src/components/AnalysisPage.vue` 文件**

## Chunk 3: 验证

### Task 3: 运行测试和构建回归

**Files:**
- Test: `frontend/tests/auth-view-state.test.js`

- [ ] **Step 1: 运行定向测试**

Run: `cd frontend && node --test tests/auth-view-state.test.js`

Expected:
- PASS

- [ ] **Step 2: 运行前端构建**

Run: `cd frontend && npm run build`

Expected:
- Build success

- [ ] **Step 3: 人工核查**

确认：

- 用户端侧边栏没有“数据分析”
- 管理端 `analysis` 相关代码未被改动
