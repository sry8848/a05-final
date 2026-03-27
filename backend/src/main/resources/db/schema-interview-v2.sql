-- 面试核心表：interview_preferences / interview_sessions / session_skill_states
--             interview_questions / ai_invocation_logs
-- 执行前请确保已执行 01-create-database.sql、schema-auth.sql、schema-resume.sql、schema-position.sql
-- 注意：若已执行旧版 schema-interview.sql，interview_sessions 字段结构已变更，
--       请先 DROP TABLE interview_sessions，再执行本脚本。

-- ============================================================
-- 1. 面试偏好表：记录用户最近一次准备页的填写偏好
-- ============================================================
CREATE TABLE IF NOT EXISTS interview_preferences (
    id                          BIGINT       AUTO_INCREMENT PRIMARY KEY,
    user_id                     BIGINT       NOT NULL         COMMENT '用户 ID',
    target_role                 VARCHAR(64)  NOT NULL         COMMENT '最近岗位枚举，如 JAVA_BACKEND',
    experience_level            VARCHAR(32)  NOT NULL         COMMENT '最近工作年限枚举，如 SENIOR',
    mode                        VARCHAR(32)  NOT NULL         COMMENT '最近面试模式：practice / professional',
    focus_topics                VARCHAR(512) NULL             COMMENT '最近侧重点（自由文本）',
    think_time_limit_seconds    INT          NULL             COMMENT '最近思考时间限制（秒）',
    answer_time_limit_seconds   INT          NULL             COMMENT '最近回答时间限制（秒）',
    updated_at                  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_interview_preferences_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='面试准备偏好表（每用户保留最近一条）';

-- ============================================================
-- 2. 面试会话表（完整版，替代旧版简化表）
-- ============================================================
DROP TABLE IF EXISTS interview_sessions;

CREATE TABLE interview_sessions (
    id                      BIGINT       AUTO_INCREMENT PRIMARY KEY,
    user_id                 BIGINT       NOT NULL         COMMENT '用户 ID',
    resume_id               BIGINT       NULL             COMMENT '关联简历 ID',
    title                   VARCHAR(255) NOT NULL DEFAULT '' COMMENT '本场标题，如 Java 后端开发模拟面试',
    target_role             VARCHAR(64)  NOT NULL         COMMENT '目标岗位枚举，如 JAVA_BACKEND',
    experience_level        VARCHAR(32)  NOT NULL         COMMENT '工作年限枚举，如 SENIOR',
    mode                    VARCHAR(32)  NOT NULL         COMMENT '面试模式：practice / professional',
    job_description         LONGTEXT     NULL             COMMENT 'JD 文本',
    focus_topics            VARCHAR(512) NULL             COMMENT '用户指定侧重点',
    current_question_no     INT          NOT NULL DEFAULT 0 COMMENT '当前题号（从 1 开始）',
    context_window_size     INT          NOT NULL DEFAULT 5 COMMENT '最近 x 题读取完整 Q/A 的窗口大小',
    think_time_limit_seconds    INT      NULL             COMMENT '专业模式思考时间限制（秒）',
    answer_time_limit_seconds   INT      NULL             COMMENT '专业模式回答时间限制（秒）',
    first_question_json     JSON         NULL             COMMENT '第一题快照，status=in_progress 时由 GET /interviews/{id} 内嵌返回',
    syllabus_json           JSON         NULL             COMMENT 'Planner 生成的主考纲（含规划推理、知识域与项目/实习条目）',
    state_ledger_json       JSON         NULL             COMMENT '状态账本（唯一过程状态表达，记录知识域覆盖、题型进度等）',
    status                  VARCHAR(32)  NOT NULL DEFAULT 'planning' COMMENT '会话状态：created/planning/in_progress/report_generating/completed/aborted',
    model_provider          VARCHAR(64)  NULL             COMMENT '模型供应商',
    model_name              VARCHAR(128) NULL             COMMENT '模型名称',
    started_at              DATETIME     NULL             COMMENT '正式开始时间（首题就绪时更新）',
    finished_at             DATETIME     NULL             COMMENT '面试结束时间',
    created_at              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_is_user_id (user_id),
    INDEX idx_is_status (status),
    INDEX idx_is_user_status (user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='面试会话表';

-- ============================================================
-- 3. 知识域考察状态表：每场面试 × 每个知识域 = 一条记录
-- ============================================================
CREATE TABLE IF NOT EXISTS session_skill_states (
    id              BIGINT      AUTO_INCREMENT PRIMARY KEY,
    session_id      BIGINT      NOT NULL         COMMENT '所属面试会话 ID',
    domain_code     VARCHAR(64) NOT NULL         COMMENT '关联 position_skill_domains.domain_code',
    status          VARCHAR(32) NOT NULL DEFAULT 'uncovered' COMMENT '考察状态：uncovered/in_progress/covered/circuit_broken',
    tested_count    INT         NOT NULL DEFAULT 0 COMMENT '被考察题数',
    saturated       TINYINT     NOT NULL DEFAULT 0 COMMENT '是否已问透（1=是，0=否）',
    evidence_refs   JSON        NULL             COMMENT '相关题目 ID 列表，如 [5001, 5003]',
    ai_notes        TEXT        NULL             COMMENT 'AI 对该知识域的定性备注',
    created_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_sss_session_domain (session_id, domain_code),
    INDEX idx_sss_session_id (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='面试会话知识域考察状态表';

-- ============================================================
-- 4. 面试题目表：存储每一道动态生成的题目
-- ============================================================
CREATE TABLE IF NOT EXISTS interview_questions (
    id                      BIGINT       AUTO_INCREMENT PRIMARY KEY,
    session_id              BIGINT       NOT NULL         COMMENT '所属面试会话 ID',
    question_no             INT          NOT NULL         COMMENT '题号（在本场面试中唯一，从 1 开始）',
    question_type           VARCHAR(64)  NOT NULL         COMMENT '题目类型：INTRO/PROJECT_DEEP_DIVE/SCENARIO/PRINCIPLE/BEHAVIORAL',
    domain_code             VARCHAR(64)  NULL             COMMENT '主知识域 code',
    secondary_domain_codes  JSON         NULL             COMMENT '副知识域 code 列表',
    stem                    LONGTEXT     NOT NULL         COMMENT '题目正文',
    target_skill            VARCHAR(128) NULL             COMMENT '核心考察点，如 缓存击穿',
    expected_points         JSON         NULL             COMMENT '理想回答要点列表',
    status                  VARCHAR(32)  NOT NULL DEFAULT 'pending' COMMENT '题目状态：pending/asked/answered/skipped',
    hint_text               LONGTEXT     NULL             COMMENT '面试官提示文本（用户请求提示后写入）',
    generation_context_json JSON         NULL             COMMENT '生成题目时的决策上下文快照',
    created_at              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_iq_session_id (session_id),
    UNIQUE KEY uk_iq_session_no (session_id, question_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='面试题目表';

-- ============================================================
-- 5. 回答尝试表：记录候选人对每道题的回答及评估结果快照
-- ============================================================
CREATE TABLE IF NOT EXISTS interview_attempts (
    id                  BIGINT       AUTO_INCREMENT PRIMARY KEY,
    session_id          BIGINT       NOT NULL         COMMENT '所属面试会话 ID',
    question_id         BIGINT       NOT NULL         COMMENT '被回答的题目 ID（关联 interview_questions.id）',
    attempt_id          VARCHAR(64)  NOT NULL         COMMENT '客户端生成的全局唯一幂等键（UUID）',
    answer_text         LONGTEXT     NULL             COMMENT '候选人回答文本（文字模式）或语音转写结果',
    is_final            TINYINT      NOT NULL DEFAULT 1 COMMENT '是否为最终版回答（1=是，0=否；文字模式默认 1）',
    evaluation_json     JSON         NULL             COMMENT '评估决策结果快照（signal/depthReached/nextStrategy 等）',
    detail_evaluation_status VARCHAR(32) NOT NULL DEFAULT 'pending' COMMENT '单题详细评估状态：pending/generating/ready/failed',
    detail_evaluation_json JSON      NULL             COMMENT '单题详细评估结构化结果',
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_ia_attempt_id (attempt_id),
    INDEX idx_ia_session_id (session_id),
    INDEX idx_ia_question_id (question_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='候选人回答尝试表（幂等键为 attempt_id）';

-- ============================================================
-- 6. 单题重答表：围绕原题快照提交独立重答，不污染整场面试会话
-- ============================================================
CREATE TABLE IF NOT EXISTS question_redo_attempts (
    id                  BIGINT       AUTO_INCREMENT PRIMARY KEY,
    user_id             BIGINT       NOT NULL         COMMENT '用户 ID',
    source_session_id   BIGINT       NOT NULL         COMMENT '原会话 ID',
    source_question_id  BIGINT       NOT NULL         COMMENT '原题目 ID',
    source_snapshot_json JSON        NULL             COMMENT '冻结原题快照',
    answer_text         LONGTEXT     NULL             COMMENT '重答内容',
    evaluation_status   VARCHAR(32)  NOT NULL DEFAULT 'pending' COMMENT '单题重答评估状态：pending/generating/ready/failed',
    evaluation_json     JSON         NULL             COMMENT '单题重答评估结构化结果',
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_qra_user_source (user_id, source_session_id, source_question_id),
    INDEX idx_qra_source_question (source_question_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='单题重答记录表';

-- ============================================================
-- 7. 面试报告表：面试结束后异步生成，每场会话一条记录
-- ============================================================
CREATE TABLE IF NOT EXISTS interview_reports (
    id                          BIGINT         AUTO_INCREMENT PRIMARY KEY,
    session_id                  BIGINT         NOT NULL         COMMENT '对应面试会话 ID（全局唯一）',
    overall_score               DECIMAL(5,1)   NULL             COMMENT '综合得分 0~100，保留 1 位小数',
    skill_domain_scores         JSON           NULL             COMMENT '逐知识域评分明细（domainCode/domainName/score/commentary）',
    comprehensive_radar_scores  JSON           NULL             COMMENT '专业模式综合能力雷达数据（练习模式可为 null）',
    summary                     LONGTEXT       NULL             COMMENT '总结评语',
    strengths                   JSON           NULL             COMMENT '优势列表（JSON 数组）',
    weaknesses                  JSON           NULL             COMMENT '薄弱点列表（JSON 数组）',
    improvement_suggestions     JSON           NULL             COMMENT '提升建议列表（JSON 数组）',
    recommended_topics          JSON           NULL             COMMENT '推荐练习知识点列表（可为 null）',
    interactive_transcript_json JSON           NULL             COMMENT '互动式逐字稿（反馈页展示用）',
    created_at                  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_ir_session_id (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='面试报告表（每场会话一条）';

-- ============================================================
-- 8. AI 调用审计日志表：记录每次大模型调用的元数据
-- ============================================================
CREATE TABLE IF NOT EXISTS ai_invocation_logs (
    id                      BIGINT       AUTO_INCREMENT PRIMARY KEY,
    session_id              BIGINT       NULL             COMMENT '面试会话 ID',
    question_id             BIGINT       NULL             COMMENT '题目 ID',
    user_id                 BIGINT       NULL             COMMENT '用户 ID',
    prompt_code             VARCHAR(64)  NOT NULL         COMMENT 'Prompt 标识，如 planner / question_generation_stream',
    prompt_version          VARCHAR(32)  NOT NULL DEFAULT 'v1' COMMENT 'Prompt 版本',
    model_provider          VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '模型供应商，如 openai / mock',
    model_name              VARCHAR(128) NOT NULL DEFAULT '' COMMENT '模型名称，如 gpt-4o-mini',
    temperature             DECIMAL(4,2) NULL             COMMENT '温度参数',
    request_tokens          INT          NULL             COMMENT '输入 Token 数',
    response_tokens         INT          NULL             COMMENT '输出 Token 数',
    latency_ms              INT          NULL             COMMENT '端到端延迟（毫秒）',
    retrieval_context_json  JSON         NULL             COMMENT 'RAG 检索结果摘要',
    request_payload_json    JSON         NULL             COMMENT '请求快照（脱敏后）',
    response_payload_json   JSON         NULL             COMMENT '响应快照',
    success                 TINYINT      NOT NULL DEFAULT 1 COMMENT '是否成功（1=成功，0=失败）',
    error_message           VARCHAR(512) NULL             COMMENT '失败时的错误信息',
    created_at              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_ail_session_id (session_id),
    INDEX idx_ail_prompt_code (prompt_code),
    INDEX idx_ail_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 调用审计日志表';
