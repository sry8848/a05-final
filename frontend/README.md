# Frontend

Vue 3 + Vite 前端工程。

## 当前形态

- 当前是单页状态切换应用，根组件为 [frontend/src/App.vue](/D:/a05-cursor/frontend/src/App.vue)
- 目前没有引入 Vue Router
- 用户端通过 `currentPage` 和若干 overlay 状态切换页面
- 管理端通过 `AdminLayout` 内部状态切换模块

## 启动方式

```bash
cd frontend
npm install
npm run dev
```

## API 基地址规则

前端统一通过 [frontend/src/api/base.js](/D:/a05-cursor/frontend/src/api/base.js) 解析 API 地址。

- 未配置 `VITE_API_BASE_URL`：默认访问 `/api/v1`，由 Vite 代理到 `http://localhost:8080`
- 配置了 `VITE_API_BASE_URL=http://localhost:8080`：自动补成 `http://localhost:8080/api/v1`
- 配置了 `VITE_API_BASE_URL=http://localhost:8080/api/v1`：直接使用

当前 `vite.config.js` 设置了：

- `envDir: '..'`
- `/api` 代理到 `http://localhost:8080`

所以根目录 `.env` 会被前端开发环境读取。

## 目录约定

```text
src/
├── api/         # HTTP API 封装
├── assets/      # 静态资源
├── components/  # 页面级与复用组件
├── services/    # ASR / TTS 等服务封装
├── utils/       # 展示模型、状态转换、存储工具
├── App.vue      # 应用根组件
└── main.js
```

## 当前主要页面入口

用户端侧栏页面：

- `growth`
- `interview`
- `history`
- `questionBank`
- `resumes`
- `analysis`
- `settings`

用户端 overlay / 临时页：

- 报告生成页
- 报告页
- 单题详情页
- 雷达图页
- 分数趋势页

管理端页面：

- `dashboard`
- `prompt`
- `model`
- `rag`
- `monitor`
- `analysis`

详细说明见 [docs/page-list.md](/D:/a05-cursor/docs/page-list.md)。

## 开发约定

- 当前协作文档以 `npm` 为准，不要混用 `pnpm`
- 业务接口、头像地址、音频地址统一通过 `base.js` 解析
- 用户认证和管理员认证是两套 token
- 面试主链路是：
  `POST /interviews/{sessionId}/attempts` -> `GET /interviews/{sessionId}/questions/stream?attemptId=...`

## 相关文档

- 根目录联调说明：[README.md](/D:/a05-cursor/README.md)
- API 文档：[docs/api-design.md](/D:/a05-cursor/docs/api-design.md)
- 页面与状态流转：[docs/page-list.md](/D:/a05-cursor/docs/page-list.md)
- 项目结构：[docs/project-structure.md](/D:/a05-cursor/docs/project-structure.md)
