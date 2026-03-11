# 登录 / 注册页实现计划（玻璃拟态 + 蓝白主色）

## 1. 需求确认

### 1.1 登录页 `/login`

| 项目 | 说明 |
|------|------|
| 登录方式 | **Tab 切换**：① 邮箱 + 密码 ② 邮箱 + 验证码（MVP）；手机验证码登录后续迭代（预留扩展） |
| 字段 | `email`、`password`（密码登录）、`code`（验证码登录）、`loginType`（password \| email-code） |
| 交互 | 验证码 Tab：显示「发送验证码」按钮，60s 倒计时；提交中禁用按钮；失败显示 message |
| 跳转 | 登录成功 → 首页或仪表盘；底部「去注册」→ `/register` |
| 接口 | `POST /api/v1/auth/login/password`、`POST /api/v1/auth/login/email-code`、`POST /api/v1/auth/email-code/send` |

### 1.2 注册页 `/register`

| 项目 | 说明 |
|------|------|
| 字段 | `email`、`code`、`nickname`、`password`、`confirmPassword`（前端校验一致） |
| 交互 | 包含「发送验证码」按钮（`scene=register`，60s 倒计时）；提交中禁用；失败显示 message；成功 → 跳转登录页并可选提示「注册成功，请登录」 |
| 跳转 | 底部「返回登录」→ `/login` |
| 接口 | `POST /api/v1/auth/email-code/send`（`scene=register`）、`POST /api/v1/auth/register` |

### 1.3 视觉规范（玻璃拟态 + 蓝白）

- **主色**：蓝色系（如 `#2563eb`、`#3b82f6`）为按钮、链接、焦点。
- **背景**：浅蓝白渐变或柔和蓝白底，便于玻璃效果可见。
- **玻璃拟态**：卡片/表单容器使用 `background: rgba(255,255,255,0.2)` + `backdrop-filter: blur(12px)`，边框 `1px solid rgba(255,255,255,0.3)`，圆角 `12px~16px`，轻微阴影。
- **输入框**：与卡片统一风格，半透明白底 + 毛玻璃或白底轻阴影，蓝框 focus。
- **一致性**：登录页与注册页共用同一套 CSS 变量与组件风格。

---

## 2. 实现计划

### 2.1 依赖

- `vue-router`：路由，`/login`、`/register`。
- `pinia`：全局状态，存储 `token`、`user`，提供 `login`、`logout`、`fetchMe`。
- `axios`：请求封装，baseURL 指向 `/api/v1`，响应里取 `data`/统一处理 `code !== 0`。

### 2.2 目录与文件

```
frontend/src/
├── main.ts                 # 挂载 app，use(router), use(pinia)，引入全局样式
├── App.vue                 # <router-view />
├── style.css               # 全局：CSS 变量（蓝白+玻璃）、基础重置
├── router/
│   └── index.ts            # routes: Login, Register，后续可加 history
├── stores/
│   └── auth.ts             # token、user、login、logout、fetchMe
├── api/
│   └── auth.ts             # register、loginPassword、loginEmailCode、sendEmailCode
├── views/
│   └── auth/
│       ├── Login.vue       # Tab + 表单 + 调用 API + 玻璃卡片
│       └── Register.vue    # 表单 + 调用 API + 玻璃卡片
└── types/
    └── auth.ts             # 请求/响应类型（可选）
```

### 2.3 步骤顺序

1. **安装依赖**：`vue-router`、`pinia`、`axios`。
2. **全局样式**：在 `style.css` 中定义蓝白主色与玻璃拟态变量，以及表单、按钮的玻璃风格。
3. **路由**：配置 `/login`、`/register`，默认可重定向到 `/login`。
4. **API 层**：axios 实例 baseURL `/api/v1`，实现 auth 相关接口；响应拦截器统一处理错误与 `data`。
5. **Auth Store**：保存 token（localStorage）、user；提供 `loginByPassword`、`loginByEmailCode`、`logout`、`fetchMe`。
6. **登录页**：Tab（密码 / 验证码）、邮箱/密码或邮箱/验证码表单、发送验证码（60s 冷却）、提交调用 store 与 API，成功后跳转；玻璃卡片布局。
7. **注册页**：邮箱、昵称、密码、确认密码、提交调用 register API，成功后跳转登录；玻璃卡片布局。
8. **入口**：`main.ts` 使用 `createPinia()`、`router`，`App.vue` 仅保留 `<router-view />`。

### 2.4 与后端联调说明

- 若后端 auth 接口尚未就绪，可在 `api/auth.ts` 内对部分接口做 mock（返回固定 token 与用户信息），便于先完成前端 UI 与流程。
- 联调时统一使用 `api-design.md` 中的请求体与响应格式（`code`、`message`、`data`）。

---

## 3. 验收要点

- [ ] 登录页：Tab 切换正常，密码登录与邮箱验证码登录均可提交并得到反馈。
- [ ] 注册页：校验确认密码一致，提交后跳转登录页。
- [ ] 整站风格：玻璃拟态卡片 + 蓝白主色，登录/注册页风格统一。
- [ ] 请求：与 `api-design.md` 一致；未联调时 mock 可工作。
