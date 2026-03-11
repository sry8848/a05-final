-- 首次运行前执行：创建数据库（与 application.yml 中 spring.datasource.url 的库名一致）
-- 使用方式：mysql -u root -p < src/main/resources/db/01-create-database.sql
CREATE DATABASE IF NOT EXISTS aiinterview DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
