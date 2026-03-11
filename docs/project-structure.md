# AI 模拟面试系统项目结构建议

## 1. 目标

在当前阶段，仓库需要同时承载三类内容：

- 产品与架构文档
- 前端 Web 工程
- 后端服务工程

因此推荐采用顶层三层目录结构，并增加前端隔离区：

```text
docs/
frontend/
frontend-isolation/
backend/
```

## 2. 顶层目录说明

### 2.1 `docs`

用于存放产品、接口、数据模型和 Prompt 设计文档。

当前建议保留：

- `product-scope.md`
- `page-list.md`
- `api-design.md`
- `db-schema.md`
- `prompt-strategy.md`
- `project-structure.md`

### 2.2 `frontend`

用于存放 Vue 3 网页端项目（与后端联调、按产品文档实现功能的正式工程）。开发时可参考 `frontend-isolation/` 中的视觉与组件进行复用。

推荐初始化后的目录：

```text
frontend/
  src/
    apis/
    assets/
    components/
      device-test/
      interview/
      profile/
      question-bank/
      report/
      resume/
    layouts/
    router/
    stores/
    types/
    utils/
    views/
      auth/
      interview/
      profile/
      question-bank/
      resume/
  public/
```

模块建议：

- `views/auth`：登录页、注册页
- `views/interview`：测试页、准备页、加载页、练习页、反馈页、单题详情页、历史页
- `views/profile`：成长中心（含欢迎语）、个人设置页
- `views/question-bank`：成长问答库
- `views/resume`：简历管理
- `components/interview`：题目卡片、答题区、倒计时、评估反馈
- `utils`：Web Speech API 封装、URL 预填参数解析

### 2.3 `frontend-isolation`（前端隔离区）

用于**导入现有前端项目**，作为视觉与 UI 的参考源，不在本区直接改功能或对接后端。

- **用途**：将你希望保留视觉风格的前端代码拷贝或链接到此处；在 `frontend/` 中开发时从此处复用布局、样式、欢迎语、组件结构等。
- **约定**：功能与接口以 `docs/api-design.md`、`docs/page-list.md`、`docs/product-scope.md` 为准；本区仅只读参考，详见仓库根目录 `frontend-isolation/README.md`。

### 2.4 `backend`

用于存放 Spring Boot 后端服务。

推荐初始化后的目录：

```text
backend/
  src/
    main/
      java/
        com/
          a05/
            aiinterview/
              common/
              auth/
              system/
              resume/
              position/
              interview/
              report/
              questionbank/
              profile/
              ai/
              admin/
      resources/
        db/
        prompts/
    test/
      java/
```

模块建议：

- `auth`：注册、验证码、登录、鉴权
- `system`：Ping、环境检测扩展位
- `resume`：上传、解析、编辑、默认简历
- `position`：岗位与知识域树
- `interview`：会话、题目、提交并继续、历史记录
- `report`：报告、单题详情、AI 追问
- `questionbank`：成长问答库
- `profile`：个人资料、成长统计、雷达图
- `ai`：Prompt 模板、模型适配器、RAG 检索接口、AI 调用日志
- `admin`：后台管理扩展位

## 3. 初始化顺序

建议按以下顺序初始化工程：

1. 先初始化 `backend` 的 Spring Boot 项目，建立统一响应、鉴权、中间件和数据库连接。
2. 初始化 `frontend` 的 Vue 3 + Vite + TypeScript 工程。
3. 先在后端实现登录、岗位知识域、简历管理和面试主链路的最小接口。
4. 前端按页面优先级实现：登录/注册 -> 测试页 -> 简历管理 -> 准备页 -> 加载页 -> 练习页 -> 报告页 -> 历史页 -> 问答库 -> 成长中心。
5. 后端再补 AI 调用日志、问答库、成长统计和非 MVP 扩展接口。

## 4. 第一阶段最小启动集

如果要尽快开工，第一阶段只需要优先建好这些内容：

- `frontend/src/views/auth`
- `frontend/src/views/interview`
- `frontend/src/views/resume`
- `frontend/src/apis`
- `frontend/src/stores`
- `backend/src/main/java/com/a05/aiinterview/auth`
- `backend/src/main/java/com/a05/aiinterview/resume`
- `backend/src/main/java/com/a05/aiinterview/position`
- `backend/src/main/java/com/a05/aiinterview/interview`
- `backend/src/main/java/com/a05/aiinterview/report`
- `backend/src/main/java/com/a05/aiinterview/ai`

## 5. 扩展性要求

当前结构需要保证后续加入这些能力时不推翻主干：

- RAG 检索
- 后端 ASR / TTS
- 熔断策略
- 红黑榜 Top3
- 后台管理端
- 算法题代码编辑器
- 智能监考

因此建议：

- Prompt 模板统一放在 `backend/src/main/resources/prompts`
- AI 调用统一走 `ai` 模块，不散落到业务代码中
- 专业模式和练习模式共用会话主流程，只在配置和评分逻辑上分叉
- 前端页面按业务域拆分，避免一个大 `views/interview` 文件承载全部逻辑
