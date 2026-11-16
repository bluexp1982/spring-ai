-- ============================================================
-- 后台管理员用户 — 开发测试样本数据
-- 执行前请确保 sys_user.sql 中的表已创建
-- 注意：DataInitializer.java 会自动创建 admin 用户，
--       所以此处不再重复插入 admin，仅补充其他用户
-- ============================================================

-- -----------------------------------------------------------
-- 密码说明（BCrypt 加密）：
--   admin      / admin123  ← DataInitializer 自动创建
--   zhangsan   / 123456
--   lisi       / 123456
--   wangwu     / 123456
--   zhaoliu    / abc@123
--   xiaomei    / 123456
-- -----------------------------------------------------------
INSERT INTO sys_user (username, password, display_name, is_active, created_at, updated_at) VALUES

-- 1. 普通管理员
('zhangsan', '$2a$10$zNVl3mVwbuG43LfLo3qfVumoFwbEvHovnrV9t4YfgFgZL9Pb7Hv8C',
 '张三',  TRUE,  '2026-01-15 09:00:00+08', '2026-01-15 09:00:00+08'),

-- 2. 财务管理员
('lisi', '$2a$10$qRzyuVK24aGqmeEj2PL9Te0Q2hpLAjhQUVtDrGK/Kx8.MZRHtOjvm',
 '李四',  TRUE,  '2026-02-01 10:00:00+08', '2026-02-01 10:00:00+08'),

-- 3. 运营管理员
('wangwu', '$2a$10$UryCl3JDt4Vs7AkRhGrUVuw64rrtkZ.BDIuVpati1kimBtO1IG8VK',
 '王五',  TRUE,  '2026-03-10 14:00:00+08', '2026-03-10 14:00:00+08'),

-- 4. 超级管理员（备用账号）
('zhaoliu', '$2a$10$P7PhWRJeomdkD6qlgwE2xO1/u3830CIG1j28Ru5XDu/eqw9W5Hc6S',
 '赵六',  TRUE,  '2026-04-20 08:30:00+08', '2026-04-20 08:30:00+08'),

-- 5. 已禁用账号（模拟离职员工）
('xiaomei', '$2a$10$uRYhVz83akKBtKG0p02L..hITKlaOTfkDsubaUEIAD38QtogVEwl6',
 '小美',  FALSE, '2026-05-01 09:00:00+08', '2026-06-30 18:00:00+08');