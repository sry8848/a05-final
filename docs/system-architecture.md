# AI 模拟面试系统架构图

## 系统整体架构

```mermaid
graph TB
    subgraph "前端层 (Frontend)"
        A[Vue 3 + Vite]
        A1[页面组件<br/>Login/Interview/Report等]
        A2[服务层<br/>ASR/TTS/QuestionStream]
        A3[工具层<br/>状态管理/验证]
        A --> A1
        A --> A2
        A --> A3
    end

    subgraph "网关与接入层"
        B[Nginx / Vite Proxy]
    end

    subgraph "后端服务层 (Spring Boot)"
        C1[认证授权模块<br/>Auth]
        C2[面试核心引擎<br/>Interview]
        C3[简历管理模块<br/>Resume]
        C4[用户画像模块<br/>Profile]
        C5[职位与题库<br/>Position/QuestionBank]
        C6[AI服务集成<br/>AI]
        C7[语音服务<br/>Speech]
        C8[RAG检索增强<br/>RAG]
        C9[管理后台<br/>Admin]
    end

    subgraph "数据层"
        D1[(MySQL<br/>业务数据)]
        D2[(Redis<br/>缓存/SSE)]
        D3[(RabbitMQ<br/>消息队列)]
        D4[(Qdrant<br/>向量数据库)]
    end

    subgraph "外部服务层"
        E1[阿里云百炼<br/>OpenAI兼容API]
        E2[阿里云ASR<br/>语音识别]
        E3[阿里云TTS<br/>语音合成]
        E4[SMTP邮件服务]
    end

    A -->|HTTP/WebSocket| B
    B -->|/api/v1| C1
    B -->|/api/v1| C2
    B -->|/api/v1| C3
    B -->|/api/v1| C4
    B -->|/api/v1| C5
    B -->|SSE流式| C6
    B -->|WebSocket| C7

    C1 -->|读写用户| D1
    C1 -->|验证码缓存| D2
    C1 -->|发送邮件| E4

    C2 -->|面试会话| D1
    C2 -->|SSE缓存| D2
    C2 -->|异步任务| D3
    C2 -->|调用AI| C6

    C3 -->|简历数据| D1
    C3 -->|PDF/DOCX解析| C3

    C4 -->|用户统计| D1

    C5 -->|题库/职位| D1

    C6 -->|AI调用日志| D1
    C6 -->|调用大模型| E1

    C7 -->|语音中转| E2
    C7 -->|语音合成| E3

    C8 -->|向量检索| D4
    C8 -->|知识入库| D4
    C8 -->|Rerank| E1

    C9 -->|管理数据| D1
```

## 技术栈详解

### 前端技术栈
- **框架**: Vue 3 (Composition API)
- **构建工具**: Vite 5
- **主要特性**:
  - 组件化开发
  - Fetch API 调用
  - WebSocket/ASR 实时语音
  - SSE 流式响应

### 后端技术栈
- **核心框架**: Spring Boot 3.2.5
- **Java版本**: Java 21
- **ORM**: MyBatis-Plus 3.5.5
- **安全框架**: Spring Security + JWT
- **AI集成**: Spring AI 1.0.0
- **流式支持**: WebFlux (SSE)

### 基础设施
- **数据库**: MySQL 8.0 (业务数据)
- **缓存**: Redis (会话、SSE缓存)
- **消息队列**: RabbitMQ (异步任务)
- **向量数据库**: Qdrant (RAG知识库)

### AI与语音服务
- **大模型**: 阿里云百炼 (OpenAI兼容接口)
- **语音识别**: 阿里云ASR (Paraformer)
- **语音合成**: 阿里云TTS (CosyVoice)
- **重排序**: 阿里云Rerank (gte-rerank-v2)

## 核心模块架构

### 1. 认证授权模块 (Auth)
```mermaid
graph LR
    A[用户请求] --> B[JWT认证过滤器]
    B --> C{Token验证}
    C -->|有效| D[SecurityContext]
    C -->|无效| E[401未授权]
    D --> F[访问受保护资源]
    
    G[邮箱验证码] --> H[Redis存储]
    I[注册/登录] --> J[密码加密]
    J --> K[User表]
```

### 2. 面试引擎模块 (Interview)
```mermaid
graph TB
    A[创建面试会话] --> B[状态账本初始化]
    B --> C[首题生成]
    C --> D[SSE流式输出]
    D --> E[用户答题]
    E --> F[答案评估]
    F --> G[决策引擎]
    G --> H{下一步决策}
    H -->|继续出题| I[下一题生成]
    H -->|结束面试| J[生成报告]
    I --> D
    J --> K[面试完成]
    
    subgraph "决策引擎"
        G1[Planner规划器]
        G2[策略目录]
        G3[配额管理]
        G4[状态账本Reducer]
    end
```

### 3. AI服务集成模块
```mermaid
graph LR
    A[业务服务] --> B[Prompt模板服务]
    B --> C[渲染Prompt]
    C --> D{Mock模式?}
    D -->|是| E[MockAiClient]
    D -->|否| F[OpenAiClient]
    F --> G[阿里云百炼API]
    E --> H[AiCallResult]
    G --> H
    H --> I[AI调用日志]
    I --> J[MySQL]
```

### 4. RAG检索增强模块
```mermaid
graph TB
    A[知识入库] --> B[文本分块]
    B --> C[向量化]
    C --> D[Qdrant存储]
    
    E[用户问题] --> F[向量化]
    F --> G[向量检索]
    G --> D
    D --> H[Top-K候选]
    H --> I[Rerank重排序]
    I --> J[精选上下文]
    J --> K[注入Prompt]
```

### 5. 语音服务模块
```mermaid
graph LR
    A[前端] -->|WebSocket| B[AsrProxy]
    B -->|流式音频| C[阿里云ASR]
    C -->|识别文本| D[文本聚合]
    D --> E[停顿检测]
    E -->|自动提交| F[面试答案]
    
    G[问题文本] --> H[TTS服务]
    H -->|合成语音| I[阿里云TTS]
    I -->|音频流| J[前端播放]
```

## 数据流转架构

### 完整面试流程
```mermaid
sequenceDiagram
    participant U as 用户
    participant F as 前端
    participant B as 后端
    participant AI as AI服务
    participant DB as MySQL
    participant R as Redis
    
    U->>F: 登录/注册
    F->>B: POST /auth/login
    B->>DB: 验证用户
    B->>R: 存储验证码(注册时)
    B-->>F: 返回JWT Token
    
    U->>F: 创建面试
    F->>B: POST /interview
    B->>DB: 创建InterviewSession
    B->>B: 初始化StateLedger
    B->>AI: 生成首题Prompt
    AI-->>B: 返回题目
    B->>R: SSE缓存
    B-->>F: SSE流式推送
    
    U->>F: 回答问题(文本/语音)
    F->>B: POST /interview/{id}/submit
    B->>DB: 保存Attempt
    B->>AI: 评估答案
    AI-->>B: 评估结果+决策
    B->>B: 更新StateLedger
    B->>DB: 更新Session
    
    alt 继续面试
        B->>AI: 生成下一题
        AI-->>B: 新题目
        B-->>F: SSE推送
    else 结束面试
        B->>AI: 生成报告
        AI-->>B: 面试报告
        B->>DB: 保存Report
        B-->>F: 返回报告
    end
```

## 部署架构

```mermaid
graph TB
    subgraph "开发环境"
        A1[前端:5173]
        B1[后端:8080]
        C1[(MySQL:3306)]
        D1[(Redis:6379)]
        E1[(RabbitMQ:5672)]
        F1[(Qdrant:6333/6334)]
    end
    
    subgraph "Docker Compose"
        C1
        D1
        E1
        F1
    end
    
    A1 -->|Vite Proxy| B1
    B1 --> C1
    B1 --> D1
    B1 --> E1
    B1 --> F1
```

## 核心设计模式

1. **分层架构**: Controller → Service → Mapper/Repository
2. **状态机模式**: 面试会话状态管理
3. **策略模式**: AI决策策略可插拔
4. **事件驱动**: 异步任务通过RabbitMQ
5. **流式处理**: SSE/WebSocket实时交互
6. **RAG模式**: 检索增强生成

---

*注: 此架构图可在支持Mermaid的Markdown编辑器中查看渲染效果*
