-- 岗位知识域表：存储各岗位下的知识域定义（静态配置数据，由运营维护）
-- 执行前请确保已执行 01-create-database.sql

CREATE TABLE IF NOT EXISTS position_skill_domains (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    position_code   VARCHAR(64)  NOT NULL COMMENT '岗位编码，对应系统岗位枚举，如 JAVA_BACKEND',
    position_name   VARCHAR(128) NOT NULL COMMENT '岗位中文名，如 Java 后端开发',
    version         INT          NOT NULL DEFAULT 1 COMMENT '知识域定义版本，当前阶段固定使用 1',
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
('JAVA_BACKEND', 'Java 后端开发', 1, 'java_core',      'Java 基础',       '集合框架（HashMap/ConcurrentHashMap）、面向对象、反射、泛型、异常、Java 8+ 特性', 1),
('JAVA_BACKEND', 'Java 后端开发', 1, 'concurrency',    '并发编程',        '线程池、锁机制（synchronized/ReentrantLock）、AQS、volatile、ThreadLocal、并发容器、虚拟线程', 2),
('JAVA_BACKEND', 'Java 后端开发', 1, 'jvm',            'Java 虚拟机',     'JMM、垃圾回收（GC 算法/收集器）、类加载、对象内存布局、OOM 与性能排障', 3),
('JAVA_BACKEND', 'Java 后端开发', 1, 'mysql',          '关系型数据库',    '索引原理、事务隔离级别、MVCC、锁机制、慢 SQL 优化、分库分表基础', 4),
('JAVA_BACKEND', 'Java 后端开发', 1, 'redis',          '分布式缓存',      '数据结构、RDB/AOF、主从/哨兵/集群、击穿/穿透/雪崩、缓存一致性', 5),
('JAVA_BACKEND', 'Java 后端开发', 1, 'spring',         'Spring 生态',     'IoC、AOP、Bean 生命周期、自动装配、事务失效场景、Spring Boot 基础', 6),
('JAVA_BACKEND', 'Java 后端开发', 1, 'mq',             '消息队列',        'RabbitMQ/Kafka 选型、消息可靠性、幂等消费、顺序消息、延迟消息', 7),
('JAVA_BACKEND', 'Java 后端开发', 1, 'microservice',   '微服务组件',      '注册中心、配置中心、网关路由、Feign/RPC、熔断、限流、服务治理', 8),
('JAVA_BACKEND', 'Java 后端开发', 1, 'distributed',    '分布式综合',      '分布式锁、分布式事务、CAP/BASE、最终一致性、Seata', 9),
('JAVA_BACKEND', 'Java 后端开发', 1, 'cs_basics',      '计算机基础',      'TCP/IP、HTTP/HTTPS、进程与线程、I/O 模型（BIO/NIO/Epoll）、常见算法思路', 10);

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
