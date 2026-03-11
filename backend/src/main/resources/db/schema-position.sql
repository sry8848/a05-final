-- 岗位知识域表：存储各岗位下的知识域定义（静态配置数据，由运营维护）
-- 执行前请确保已执行 01-create-database.sql

CREATE TABLE IF NOT EXISTS position_skill_domains (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    position_code   VARCHAR(64)  NOT NULL COMMENT '岗位编码，对应 target_role 枚举，如 JAVA_BACKEND',
    position_name   VARCHAR(128) NOT NULL COMMENT '岗位中文名，如 Java 后端开发',
    version         INT          NOT NULL DEFAULT 1 COMMENT '知识域定义版本，升级知识域树时递增，便于兼容历史报告',
    domain_code     VARCHAR(64)  NOT NULL COMMENT '知识域编码，如 java_core',
    domain_name     VARCHAR(128) NOT NULL COMMENT '知识域中文名，如 Java 核心基础',
    description     VARCHAR(255) NULL     COMMENT '知识域说明',
    sort_order      INT          NOT NULL DEFAULT 0 COMMENT '排序序号，正序展示',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_psd_position_code (position_code),
    UNIQUE KEY uk_psd_position_domain (position_code, domain_code, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='岗位知识域配置表';

-- ============================================================
-- 种子数据：Java 后端工程师（JAVA_BACKEND）知识域
-- ============================================================
INSERT INTO position_skill_domains (position_code, position_name, version, domain_code, domain_name, description, sort_order) VALUES
('JAVA_BACKEND', 'Java 后端开发', 1, 'java_core',      'Java 核心基础',   'Java 语法、泛型、集合框架、IO', 1),
('JAVA_BACKEND', 'Java 后端开发', 1, 'concurrency',    '并发编程',        'JMM、线程模型、锁机制、线程池', 2),
('JAVA_BACKEND', 'Java 后端开发', 1, 'jvm',            'JVM 原理',        '内存结构、GC 算法、类加载', 3),
('JAVA_BACKEND', 'Java 后端开发', 1, 'spring',         'Spring 框架',     'IoC、AOP、Spring Boot、事务', 4),
('JAVA_BACKEND', 'Java 后端开发', 1, 'mysql',          'MySQL 数据库',    '索引原理、事务、锁、SQL 优化', 5),
('JAVA_BACKEND', 'Java 后端开发', 1, 'redis',          'Redis 缓存',      '数据结构、持久化、缓存策略、集群', 6),
('JAVA_BACKEND', 'Java 后端开发', 1, 'system_design',  '系统设计',        '高可用、高并发、分布式、微服务', 7),
('JAVA_BACKEND', 'Java 后端开发', 1, 'mq',             '消息队列',        'Kafka/RabbitMQ 原理、消息可靠性', 8);

-- ============================================================
-- 种子数据：前端工程师（FRONTEND）知识域
-- ============================================================
INSERT INTO position_skill_domains (position_code, position_name, version, domain_code, domain_name, description, sort_order) VALUES
('FRONTEND', '前端开发', 1, 'js_core',        'JavaScript 核心',  '原型链、闭包、异步模型、ES6+', 1),
('FRONTEND', '前端开发', 1, 'browser',        '浏览器原理',        '渲染流程、Event Loop、缓存', 2),
('FRONTEND', '前端开发', 1, 'vue_react',      'Vue/React 框架',   '组件化、响应式原理、虚拟 DOM', 3),
('FRONTEND', '前端开发', 1, 'css_layout',     'CSS 与布局',        'Flex、Grid、响应式设计', 4),
('FRONTEND', '前端开发', 1, 'performance',    '前端性能优化',      '加载优化、渲染优化、首屏优化', 5),
('FRONTEND', '前端开发', 1, 'network',        '网络基础',          'HTTP、HTTPS、WebSocket', 6);
