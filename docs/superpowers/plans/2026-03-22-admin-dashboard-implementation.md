# Admin Dashboard Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在尽量复用现有管理端 UI 的前提下，把管理端首页 `dashboard` 从静态演示页落成真实数据驱动的 MVP，并补齐最小后台鉴权。

**Architecture:** 后端新增独立的 `admin/dashboard` 聚合层，按区块输出概览、趋势、模型状态和 Prompt 摘要四类数据；不引入完整 RBAC，只先用“管理员邮箱白名单 + 受保护接口”封住后台入口。前端继续复用现有 `App.vue -> AdminLayout.vue -> AdminDashboard.vue` 结构，不重做导航和版式，只替换静态数据、删掉假监控块，并把数据整理逻辑抽到可测的纯 JS 模块中。

**Tech Stack:** Vue 3, Vite, node:test, Spring Boot 3, Spring Security, MyBatis-Plus, MySQL.

**Schedule:** 总工期按 `3 ~ 4` 个开发日拆分。`Chunk 1` 用 `0.5 ~ 1` 天先解后台鉴权和接口外壳阻塞；`Chunk 2` 用 `1 ~ 1.5` 天完成真实统计查询与后端接口；`Chunk 3` 用 `1` 天把现有前端页面接成真实数据页；`Chunk 4` 用 `0.5` 天做异常态、轮询、联调和回归收口。

---

## File Map

### Backend

- Create: `backend/src/main/java/com/a05/aiinterview/admin/security/AdminSecurityProperties.java`
  - 管理员邮箱白名单配置，避免把“谁能看后台”写死在代码里。
- Create: `backend/src/main/java/com/a05/aiinterview/admin/security/AdminAccessService.java`
  - 基于当前登录用户信息断言是否允许访问管理端。
- Create: `backend/src/main/java/com/a05/aiinterview/admin/dashboard/AdminDashboardController.java`
  - 暴露 `/admin/dashboard/overview|trends|models|prompts` 四个接口。
- Create: `backend/src/main/java/com/a05/aiinterview/admin/dashboard/AdminDashboardService.java`
  - 聚合用户、面试、AI 调用和 Prompt 配置数据。
- Create: `backend/src/main/java/com/a05/aiinterview/admin/dashboard/dto/AdminDashboardOverviewDto.java`
- Create: `backend/src/main/java/com/a05/aiinterview/admin/dashboard/dto/AdminDashboardTrendsDto.java`
- Create: `backend/src/main/java/com/a05/aiinterview/admin/dashboard/dto/AdminDashboardModelStatusDto.java`
- Create: `backend/src/main/java/com/a05/aiinterview/admin/dashboard/dto/AdminDashboardPromptSummaryDto.java`
  - 与前端页面区块一一对应，避免返回一个巨型万能 DTO。
- Create: `backend/src/main/java/com/a05/aiinterview/admin/dashboard/query/AdminDashboardQueryMapper.java`
  - 专门承载统计 SQL，不污染已有业务 Mapper。
- Create: `backend/src/main/java/com/a05/aiinterview/admin/dashboard/query/DailyMetricRow.java`
- Create: `backend/src/main/java/com/a05/aiinterview/admin/dashboard/query/ModelStatusRow.java`
- Create: `backend/src/main/java/com/a05/aiinterview/admin/dashboard/query/PromptUsageRow.java`
  - 统计查询投影对象。
- Create: `backend/src/main/resources/mapper/admin/AdminDashboardQueryMapper.xml`
  - 管理端统计 SQL。
- Modify: `backend/src/main/java/com/a05/aiinterview/auth/config/SecurityConfig.java`
  - 把 `/admin/dashboard/**` 从 `permitAll` 改成受保护接口。
- Modify: `backend/src/main/java/com/a05/aiinterview/common/GlobalExceptionHandler.java`
  - 增加管理端无权限的 `403` 包装返回。
- Modify: `backend/src/main/resources/application.yml`
  - 增加管理员邮箱白名单配置。

### Backend Tests

- Create: `backend/src/test/java/com/a05/aiinterview/admin/security/AdminAccessServiceTest.java`
- Create: `backend/src/test/java/com/a05/aiinterview/admin/dashboard/AdminDashboardControllerTest.java`
- Create: `backend/src/test/java/com/a05/aiinterview/admin/dashboard/AdminDashboardServiceTest.java`
- Create: `backend/src/test/java/com/a05/aiinterview/admin/dashboard/AdminDashboardQueryMapperXmlMappingTest.java`

### Frontend

- Create: `frontend/src/api/adminDashboard.js`
  - 管理端 dashboard 专用 API，避免继续把后台接口塞进 `resume.js`。
- Create: `frontend/src/utils/adminDashboardViewModel.js`
  - 把后端数据整理成当前 `AdminDashboard.vue` 现有版式可直接消费的结构。
- Create: `frontend/tests/admin-dashboard-view-model.test.js`
  - 走现有 `node --test` 路线，不新增依赖。
- Modify: `frontend/src/api/auth.js`
  - 新增 `getCurrentUser()` 和 `logout()`，供管理端登录流程复用。
- Modify: `frontend/src/components/AdminLoginPage.vue`
  - 继续用现有视觉结构，但把登录逻辑改成真实登录。
- Modify: `frontend/src/App.vue`
  - 管理端登录成功后保存 token、调用权限探测、切换到真实后台状态。
- Modify: `frontend/src/components/AdminDashboard.vue`
  - 用真实接口替换硬编码统计数据，保留现有布局与视觉样式。
- Modify: `frontend/src/components/AdminLayout.vue`
  - 去掉顶部栏里明显造假的“在线用户 128 / 系统状态正常”等静态值，避免和真实 dashboard 冲突。

### Constraints

- 不新增前端测试库，不要求用户安装新 npm 包。
- 不引入 `vue-router`，不重做管理端导航结构。
- 不做完整 RBAC，只做本期足够用的最小后台权限门。
- 不把 dashboard 改造成队列/Worker/死信队列监控中心。

## Chunk 1: 最小后台鉴权和接口外壳（0.5 ~ 1 天）

**Deliverable:** 到这个阶段结束时，后台接口路径、返回结构和最小权限门已经稳定；非管理员访问会被拒绝，管理员接口不再裸奔。

### Task 1: 加上最小后台权限门，先封住 `/admin/dashboard/**`

**Files:**
- Create: `backend/src/main/java/com/a05/aiinterview/admin/security/AdminSecurityProperties.java`
- Create: `backend/src/main/java/com/a05/aiinterview/admin/security/AdminAccessService.java`
- Modify: `backend/src/main/java/com/a05/aiinterview/auth/config/SecurityConfig.java`
- Modify: `backend/src/main/java/com/a05/aiinterview/common/GlobalExceptionHandler.java`
- Modify: `backend/src/main/resources/application.yml`
- Test: `backend/src/test/java/com/a05/aiinterview/admin/security/AdminAccessServiceTest.java`

- [ ] **Step 1: 写失败测试，先锁定“谁能进后台”的规则**

```java
@Test
void assertAdmin_shouldRejectUserOutsideAllowlist() {
    AuthService authService = mock(AuthService.class);
    AdminSecurityProperties properties = new AdminSecurityProperties();
    properties.setAllowedEmails(List.of("admin@example.com"));

    when(authService.getMe(9L)).thenReturn(new UserInfoDto("9", "user@example.com", "普通用户"));

    AdminAccessService service = new AdminAccessService(properties, authService);

    assertThatThrownBy(() -> service.assertAdmin(9L))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining("无管理后台权限");
}
```

- [ ] **Step 2: 实现 `AdminSecurityProperties` 和 `AdminAccessService`**

关键约束：
- 使用邮箱白名单，不额外建管理员表
- 白名单为空时默认拒绝后台访问
- `authService.getMe(userId)` 查不到用户时同样拒绝

```java
@ConfigurationProperties(prefix = "admin.security")
public class AdminSecurityProperties {
    private List<String> allowedEmails = List.of();
}
```

```java
public void assertAdmin(Long userId) {
    UserInfoDto user = authService.getMe(userId);
    String email = user == null ? "" : String.valueOf(user.getEmail()).trim().toLowerCase();
    if (email.isBlank() || !allowedEmails.contains(email)) {
        throw new AccessDeniedException("无管理后台权限");
    }
}
```

- [ ] **Step 3: 在安全配置和全局异常处理中接住这个权限门**

修改点：
- `SecurityConfig.java` 把 `/admin/dashboard/**` 标记为 `authenticated()`
- `GlobalExceptionHandler.java` 新增 `AccessDeniedException` 处理，返回统一结构的 `403`

```java
.requestMatchers("/admin/dashboard/**").authenticated()
```

```java
@ExceptionHandler(AccessDeniedException.class)
@ResponseStatus(HttpStatus.OK)
public ApiResponse<Void> handleAccessDenied(AccessDeniedException e) {
    return ApiResponse.fail(403, e.getMessage());
}
```

- [ ] **Step 4: 在配置文件里加管理员邮箱白名单**

```yaml
admin:
  security:
    allowed-emails: ${ADMIN_ALLOWED_EMAILS:admin@example.com}
```

- [ ] **Step 5: 运行定向测试，确认权限规则先落稳**

Run: `cd backend && mvn -Dtest=AdminAccessServiceTest test`

Expected:
- 非白名单邮箱测试 FAIL -> PASS
- 白名单邮箱测试 PASS

- [ ] **Step 6: 提交**

```bash
git add backend/src/main/java/com/a05/aiinterview/admin/security/AdminSecurityProperties.java backend/src/main/java/com/a05/aiinterview/admin/security/AdminAccessService.java backend/src/main/java/com/a05/aiinterview/auth/config/SecurityConfig.java backend/src/main/java/com/a05/aiinterview/common/GlobalExceptionHandler.java backend/src/main/resources/application.yml backend/src/test/java/com/a05/aiinterview/admin/security/AdminAccessServiceTest.java
git commit -m "feat(admin): add minimal dashboard access guard"
```

### Task 2: 先把 dashboard 四个接口外壳和 DTO 定下来

**Files:**
- Create: `backend/src/main/java/com/a05/aiinterview/admin/dashboard/AdminDashboardController.java`
- Create: `backend/src/main/java/com/a05/aiinterview/admin/dashboard/AdminDashboardService.java`
- Create: `backend/src/main/java/com/a05/aiinterview/admin/dashboard/dto/AdminDashboardOverviewDto.java`
- Create: `backend/src/main/java/com/a05/aiinterview/admin/dashboard/dto/AdminDashboardTrendsDto.java`
- Create: `backend/src/main/java/com/a05/aiinterview/admin/dashboard/dto/AdminDashboardModelStatusDto.java`
- Create: `backend/src/main/java/com/a05/aiinterview/admin/dashboard/dto/AdminDashboardPromptSummaryDto.java`
- Test: `backend/src/test/java/com/a05/aiinterview/admin/dashboard/AdminDashboardControllerTest.java`

- [ ] **Step 1: 写失败测试，锁定接口签名和包装格式**

```java
@Test
void getOverview_shouldReturnWrappedDto() {
    AdminDashboardService service = mock(AdminDashboardService.class);
    AdminDashboardController controller = new AdminDashboardController(service);

    AdminDashboardOverviewDto dto = new AdminDashboardOverviewDto();
    dto.setTotalUsers(10L);
    dto.setNewUsersToday(2L);
    when(service.getOverview(9L)).thenReturn(dto);

    ApiResponse<AdminDashboardOverviewDto> response = controller.getOverview(9L);

    assertEquals(0, response.getCode());
    assertEquals(10L, response.getData().getTotalUsers());
}
```

- [ ] **Step 2: 先实现 DTO 和 Service 外壳，返回零值/空值安全结构**

目的：
- 先固定接口协议
- 让前端后续可以并行接线
- 不在这一任务里提前塞统计 SQL

```java
public AdminDashboardOverviewDto getOverview(Long userId) {
    accessService.assertAdmin(userId);
    AdminDashboardOverviewDto dto = new AdminDashboardOverviewDto();
    dto.setTotalUsers(0L);
    dto.setNewUsersToday(0L);
    dto.setTotalInterviews(0L);
    dto.setInterviewsToday(0L);
    dto.setActiveInterviews(0L);
    dto.setTotalTokensToday(0L);
    return dto;
}
```

- [ ] **Step 3: 控制器只做三件事**

控制器职责固定为：
- 接收参数
- 调用 service
- 包一层 `ApiResponse.ok`

不把统计逻辑写进 controller。

- [ ] **Step 4: 运行定向测试，保证接口外壳稳定**

Run: `cd backend && mvn -Dtest=AdminDashboardControllerTest test`

Expected:
- 4 个接口都能返回 `code=0`
- DTO 结构与 spec 对齐

- [ ] **Step 5: 提交**

```bash
git add backend/src/main/java/com/a05/aiinterview/admin/dashboard/AdminDashboardController.java backend/src/main/java/com/a05/aiinterview/admin/dashboard/AdminDashboardService.java backend/src/main/java/com/a05/aiinterview/admin/dashboard/dto/AdminDashboardOverviewDto.java backend/src/main/java/com/a05/aiinterview/admin/dashboard/dto/AdminDashboardTrendsDto.java backend/src/main/java/com/a05/aiinterview/admin/dashboard/dto/AdminDashboardModelStatusDto.java backend/src/main/java/com/a05/aiinterview/admin/dashboard/dto/AdminDashboardPromptSummaryDto.java backend/src/test/java/com/a05/aiinterview/admin/dashboard/AdminDashboardControllerTest.java
git commit -m "feat(admin): add dashboard endpoint contracts"
```

## Chunk 2: 真实统计查询与后端接口完成（1 ~ 1.5 天）

**Deliverable:** 到这个阶段结束时，前端即使还没接完，也已经可以拿到真实 dashboard JSON；这时可以用 Postman 或 curl 验证后台数据是否可信。

### Task 3: 完成概览卡片和 7 天趋势的真实统计

**Files:**
- Create: `backend/src/main/java/com/a05/aiinterview/admin/dashboard/query/AdminDashboardQueryMapper.java`
- Create: `backend/src/main/java/com/a05/aiinterview/admin/dashboard/query/DailyMetricRow.java`
- Create: `backend/src/main/resources/mapper/admin/AdminDashboardQueryMapper.xml`
- Modify: `backend/src/main/java/com/a05/aiinterview/admin/dashboard/AdminDashboardService.java`
- Test: `backend/src/test/java/com/a05/aiinterview/admin/dashboard/AdminDashboardServiceTest.java`
- Test: `backend/src/test/java/com/a05/aiinterview/admin/dashboard/AdminDashboardQueryMapperXmlMappingTest.java`

- [ ] **Step 1: 先写失败测试，锁定两个关键点**

关键点：
- “今日”和“最近 7 天”按 `Asia/Shanghai` 自然日计算
- 趋势图空桶必须补零

```java
@Test
void getTrends_shouldBackfillMissingDatesWithZero() {
    when(mapper.selectDailyNewUsers(any(), any())).thenReturn(List.of(
            new DailyMetricRow("03-20", 3L),
            new DailyMetricRow("03-22", 5L)
    ));

    AdminDashboardTrendsDto dto = service.getTrends(9L, 7);

    assertThat(dto.getDates()).hasSize(7);
    assertThat(dto.getNewUsers()).contains(0L);
}
```

- [ ] **Step 2: 实现统计 Mapper 和 XML**

推荐方法：
- `countTotalUsers()`
- `countNewUsersToday(start, end)`
- `countTotalInterviews()`
- `countInterviewsToday(start, end)`
- `countActiveInterviews()`
- `sumTokensToday(start, end)`
- `selectDailyNewUsers(start, end)`
- `selectDailyInterviews(start, end)`
- `selectDailyTokens(start, end)`

SQL 约束：
- 所有 Token 聚合都用 `coalesce(request_tokens, 0) + coalesce(response_tokens, 0)`
- 活跃面试只统计 `planning / in_progress / report_generating`

```xml
<select id="countActiveInterviews" resultType="long">
  SELECT COUNT(1)
  FROM interview_sessions
  WHERE status IN ('planning', 'in_progress', 'report_generating')
</select>
```

- [ ] **Step 3: 在 `AdminDashboardService` 中组装概览和趋势 DTO**

要求：
- service 层统一做时区边界计算
- service 层统一做 7 天补零
- controller 不知道任何 SQL 细节

- [ ] **Step 4: 运行定向测试**

Run: `cd backend && mvn -Dtest=AdminDashboardServiceTest,AdminDashboardQueryMapperXmlMappingTest test`

Expected:
- 补零逻辑测试通过
- XML 映射测试通过
- 统计字段与 spec 口径一致

- [ ] **Step 5: 提交**

```bash
git add backend/src/main/java/com/a05/aiinterview/admin/dashboard/query/AdminDashboardQueryMapper.java backend/src/main/java/com/a05/aiinterview/admin/dashboard/query/DailyMetricRow.java backend/src/main/resources/mapper/admin/AdminDashboardQueryMapper.xml backend/src/main/java/com/a05/aiinterview/admin/dashboard/AdminDashboardService.java backend/src/test/java/com/a05/aiinterview/admin/dashboard/AdminDashboardServiceTest.java backend/src/test/java/com/a05/aiinterview/admin/dashboard/AdminDashboardQueryMapperXmlMappingTest.java
git commit -m "feat(admin): add overview and trend statistics for dashboard"
```

### Task 4: 完成模型状态和 Prompt 摘要聚合

**Files:**
- Create: `backend/src/main/java/com/a05/aiinterview/admin/dashboard/query/ModelStatusRow.java`
- Create: `backend/src/main/java/com/a05/aiinterview/admin/dashboard/query/PromptUsageRow.java`
- Modify: `backend/src/main/java/com/a05/aiinterview/admin/dashboard/query/AdminDashboardQueryMapper.java`
- Modify: `backend/src/main/resources/mapper/admin/AdminDashboardQueryMapper.xml`
- Modify: `backend/src/main/java/com/a05/aiinterview/admin/dashboard/AdminDashboardService.java`
- Test: `backend/src/test/java/com/a05/aiinterview/admin/dashboard/AdminDashboardServiceTest.java`

- [ ] **Step 1: 写失败测试，锁定状态标签和 Prompt 合并规则**

```java
@Test
void getModels_shouldMarkNoTrafficAsNoData() {
    when(mapper.selectModelStatuses(any())).thenReturn(List.of());

    List<AdminDashboardModelStatusDto> list = service.getModels(9L, 15);

    assertThat(list).isEmpty();
}
```

```java
@Test
void getPrompts_shouldUseConfiguredVersionAsSourceOfTruth() {
    when(promptProperties.asVersionMap()).thenReturn(Map.of(PromptCode.PLANNER, "v2"));
    when(promptTemplateService.loadMetadata("planner"))
            .thenReturn(new PromptTemplateMetadata("planner", "v2", "classpath:prompts/planner.md"));

    List<AdminDashboardPromptSummaryDto> list = service.getPrompts(9L);

    assertThat(list.get(0).getConfiguredVersion()).isEqualTo("v2");
}
```

- [ ] **Step 2: 实现模型状态聚合**

按 `(model_provider, model_name)` 聚合：
- `requestCount`
- `successCount`
- `errorCount`
- `successRate`
- `avgLatencyMs`

状态规则直接写在 service：
- `healthy`
- `warning`
- `error`
- `no_data`

- [ ] **Step 3: 实现 Prompt 摘要合并**

数据来源：
- `PromptProperties.asVersionMap()`
- `PromptTemplateService.loadMetadata(promptCode)`
- `selectPromptUsageLast24Hours()`

关键约束：
- “当前配置版本”永远以配置为准
- 日志只补充“最近 24 小时调用次数”和“最近调用时间”
- 配置版本和模板版本不一致时，额外打异常标签，不要静默吞掉

- [ ] **Step 4: 运行定向测试**

Run: `cd backend && mvn -Dtest=AdminDashboardServiceTest test`

Expected:
- 模型状态聚合通过
- Prompt 摘要以配置版本为准
- 无调用数据时返回空列表或 `no_data`，不伪造“全部正常”

- [ ] **Step 5: 运行后端全量测试**

Run: `cd backend && mvn -DskipTests=false test`

Expected:
- dashboard 新增测试通过
- 原有面试链路测试不回归

- [ ] **Step 6: 提交**

```bash
git add backend/src/main/java/com/a05/aiinterview/admin/dashboard/query/ModelStatusRow.java backend/src/main/java/com/a05/aiinterview/admin/dashboard/query/PromptUsageRow.java backend/src/main/java/com/a05/aiinterview/admin/dashboard/query/AdminDashboardQueryMapper.java backend/src/main/resources/mapper/admin/AdminDashboardQueryMapper.xml backend/src/main/java/com/a05/aiinterview/admin/dashboard/AdminDashboardService.java backend/src/test/java/com/a05/aiinterview/admin/dashboard/AdminDashboardServiceTest.java
git commit -m "feat(admin): add model status and prompt summary aggregation"
```

## Chunk 3: 前端接线，继续复用当前后台 UI（1 天）

**Deliverable:** 到这个阶段结束时，管理员可以用真实 token 登录后台，并在当前这套 `AdminLayout + AdminDashboard` 页面结构里看到真实数据，而不是演示数字。

### Task 5: 先把 dashboard 数据整理逻辑抽成纯 JS，可测再接 UI

**Files:**
- Create: `frontend/src/api/adminDashboard.js`
- Create: `frontend/src/utils/adminDashboardViewModel.js`
- Create: `frontend/tests/admin-dashboard-view-model.test.js`
- Modify: `frontend/src/api/auth.js`

- [ ] **Step 1: 先写失败测试，锁定前端对后端数据的消费口径**

```js
import test from 'node:test'
import assert from 'node:assert/strict'
import { buildDashboardViewModel } from '../src/utils/adminDashboardViewModel.js'

test('buildDashboardViewModel should keep token metric as token count, not currency', () => {
  const vm = buildDashboardViewModel({
    overview: { totalTokensToday: 1289432 },
    trends: { dates: [], newUsers: [], interviews: [], totalTokens: [] },
    models: [],
    prompts: []
  })

  assert.equal(vm.overviewCards[5].label, '今日Token消耗')
  assert.equal(vm.overviewCards[5].value, '1,289,432')
})
```

- [ ] **Step 2: 新建 `adminDashboard.js`，不要继续往 `resume.js` 塞后台接口**

需要的方法：
- `getAdminDashboardOverview()`
- `getAdminDashboardTrends(days = 7)`
- `getAdminDashboardModels(windowMinutes = 15)`
- `getAdminDashboardPrompts()`
- `probeAdminDashboardAccess()`，复用 overview 作为登录后权限探测

```js
export function getAdminDashboardOverview() {
  return request('/admin/dashboard/overview', { method: 'GET' })
}
```

- [ ] **Step 3: 在 `auth.js` 里补 `getCurrentUser()` 和 `logout()`**

目的是：
- 管理端登录后可复用现有 `/auth/me`
- 无权限时能清 token 并退出后台态

- [ ] **Step 4: 实现 `adminDashboardViewModel.js`**

这个模块负责：
- 把 overview/trends/models/prompts 组合成当前 `AdminDashboard.vue` 可直接消费的数据
- 保证“Token 消耗”不被格式化成货币
- 给模型状态和 Prompt 摘要生成现有 UI 所需的标签/徽标文案

- [ ] **Step 5: 跑前端纯逻辑测试**

Run: `cd frontend && node --test tests/admin-dashboard-view-model.test.js`

Expected:
- 数据映射规则通过
- 标签文案、空态、数字格式都可预测

- [ ] **Step 6: 提交**

```bash
git add frontend/src/api/adminDashboard.js frontend/src/utils/adminDashboardViewModel.js frontend/tests/admin-dashboard-view-model.test.js frontend/src/api/auth.js
git commit -m "feat(frontend): add admin dashboard api and view model helpers"
```

### Task 6: 用真实接口替换 `AdminDashboard.vue` 静态数据，并接通管理端登录

**Files:**
- Modify: `frontend/src/components/AdminDashboard.vue`
- Modify: `frontend/src/components/AdminLayout.vue`
- Modify: `frontend/src/components/AdminLoginPage.vue`
- Modify: `frontend/src/App.vue`

- [ ] **Step 1: 保留现有 dashboard 骨架，只换数据源**

明确约束：
- 不重排页面区块
- 不推翻当前卡片/图表/右栏布局
- 只删除无真实数据支撑的演示模块

优先保留：
- 顶部卡片区
- 三张趋势图
- 右侧模型状态区
- Prompt 摘要区

删除或降级：
- 假队列状态
- 假 Worker
- 假死信队列
- 假 API 多厂商健康探活

- [ ] **Step 2: 在 `AdminDashboard.vue` 接入并发加载和局部状态**

要求：
- `Promise.allSettled` 拉 overview / trends / models / prompts
- 每个面板有自己的 `loading/error/empty`
- 任何一个接口失败都不让整页崩掉

- [ ] **Step 3: 把 `AdminLayout.vue` 顶部假统计改成中性信息**

当前顶部栏里的：
- `在线用户: 128`
- `系统状态: 正常`

都属于伪数据。首版建议改为：
- 当前管理员昵称
- 最后刷新时间
- 手动刷新按钮或轻量提示

目标是“去假留真”，不是重新设计头部。

- [ ] **Step 4: 把管理端登录改成真实登录，不再只切本地状态**

实现路径：
1. `AdminLoginPage.vue` 继续保留现有视觉结构
2. 输入语义改成 `管理员邮箱 + 密码 + 前端验证码`
3. 调用 `/auth/login/password`
4. 保存 token 到 `localStorage`
5. 调 `/auth/me`
6. 再调用 `probeAdminDashboardAccess()`；若返回 `403`，清 token 并提示“无管理后台权限”

注意：
- 这里不做完整后台账号体系
- 只做“现有登录 + 后端白名单探测”

- [ ] **Step 5: 前端构建验证**

Run: `cd frontend && npm run build`

Expected:
- `AdminDashboard.vue` 编译通过
- 管理端登录成功后能进入后台
- 非白名单邮箱无法进入后台

- [ ] **Step 6: 提交**

```bash
git add frontend/src/components/AdminDashboard.vue frontend/src/components/AdminLayout.vue frontend/src/components/AdminLoginPage.vue frontend/src/App.vue
git commit -m "feat(frontend): wire real admin dashboard data into existing ui"
```

## Chunk 4: 异常态、轮询和联调收口（0.5 天）

**Deliverable:** 到这个阶段结束时，页面不仅能显示真实数据，而且面对空数据、接口失败和刷新轮询时不会表现得像“半成品”。

### Task 7: 把页面从“能显示”收口到“能稳定用”

**Files:**
- Modify: `frontend/src/components/AdminDashboard.vue`
- Modify: `frontend/src/utils/adminDashboardViewModel.js`
- Modify: `frontend/src/api/adminDashboard.js`

- [ ] **Step 1: 加入最小轮询策略**

按 spec 实现：
- overview：30 ~ 60 秒刷新
- models：30 秒刷新
- trends：首屏加载 + 手动刷新
- prompts：首屏加载 + 手动刷新

并确保：
- 页面卸载时清理 `setInterval`
- 轮询失败不无限弹窗

- [ ] **Step 2: 补齐空数据和错误态文案**

必须覆盖：
- 系统刚启动，数据全 0
- 最近 15 分钟无模型调用
- Prompt 摘要暂无使用记录
- 某个面板接口 500

禁止：
- 用硬编码假数字填空态
- 把空态写成“系统正常”

- [ ] **Step 3: 复用 `/system/ping` 做基础系统状态卡**

做法：
- dashboard 页面单独调用一次 `GET /api/v1/system/ping`
- 展示服务端时间、最近刷新时间、固定时区 `Asia/Shanghai`
- 不扩张成复杂运维健康中心

- [ ] **Step 4: 跑前端测试和构建**

Run: `cd frontend && node --test tests/admin-dashboard-view-model.test.js`

Expected: PASS

Run: `cd frontend && npm run build`

Expected: Build success

- [ ] **Step 5: 跑后端全量测试**

Run: `cd backend && mvn -DskipTests=false test`

Expected: PASS

- [ ] **Step 6: 做最小手工冒烟**

最小路径：
1. 用白名单邮箱登录管理端
2. 进入 dashboard
3. 看到真实概览卡片
4. 看到最近 7 天趋势
5. 看到模型状态和 Prompt 摘要
6. 刷新页面后仍能正常访问
7. 用非白名单邮箱尝试进入后台，被拒绝

- [ ] **Step 7: 提交**

```bash
git add frontend/src/components/AdminDashboard.vue frontend/src/utils/adminDashboardViewModel.js frontend/src/api/adminDashboard.js
git commit -m "fix(admin): harden dashboard refresh and failure states"
```

## Milestone Summary

- **Milestone A（Chunk 1 结束）**
  - 后台接口路径和最小权限门稳定
  - 非管理员无法访问 `/admin/dashboard/**`
  - 前端和后端可以围绕固定接口继续开发

- **Milestone B（Chunk 2 结束）**
  - 后端已能返回真实 dashboard JSON
  - 即使前端还没接完，也可以独立验数

- **Milestone C（Chunk 3 结束）**
  - 当前这套管理端 UI 已变成真实数据页
  - 没有重做导航和版式

- **Milestone D（Chunk 4 结束）**
  - dashboard 具备最小可发布质量
  - 空态、错误态、轮询和权限边界都经过验证

## Assumptions and Defaults

- 本期不引入完整管理员账号体系，只用“现有登录能力 + 邮箱白名单”实现最小后台权限。
- 白名单邮箱通过配置提供，默认值只用于本地开发，正式环境必须由部署配置覆盖。
- 前端不新增测试依赖，继续使用现有 `node --test` 测纯 JS 模块。
- 首版不做 Token 货币成本，只做 Token 消耗量。
- 首版不做队列、Worker、死信队列、音视频质量监控等高级后台能力。
- 首版默认复用现有 `AdminLayout.vue` 和 `AdminDashboard.vue` 的版式，不重做 UI。

## Risks to Watch

- 如果不先解决管理端真实登录和权限探测，前端 dashboard 接口即使写完也无法稳定联调。
- 当前后端没有管理员角色字段，白名单方案是本期折中解法；不要把它误当成长期 RBAC 设计。
- `AdminLayout.vue` 顶部现有静态“在线用户/系统状态”如果不处理，会和真实 dashboard 数据相互打脸。
- `AdminDashboard.vue` 现在是大组件，直接往里继续加状态很容易失控；至少要把数据整形逻辑抽走。
- 如果趋势图不做补零，图表会在空桶日期错位，用户会误以为统计有问题。
- 如果 Prompt 摘要从 `ai_invocation_logs` 反推“当前版本”，在低流量时会误报线上版本。
- 如果接口失败时整页白屏，管理员会认为后台不可用；必须坚持局部失败、局部降级。

Plan complete and saved to `docs/superpowers/plans/2026-03-22-admin-dashboard-implementation.md`. Ready to execute?
