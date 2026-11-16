-- ============================================================
-- 后台管理员用户表
-- 用于 JWT 认证登录
-- ============================================================
CREATE TABLE IF NOT EXISTS sys_user (
    id              BIGSERIAL       PRIMARY KEY,
    username        VARCHAR(50)     NOT NULL UNIQUE,            -- 登录用户名
    password        VARCHAR(255)    NOT NULL,                   -- BCrypt 加密后的密码
    display_name    VARCHAR(100),                               -- 显示名称
    is_active       BOOLEAN         NOT NULL DEFAULT TRUE,      -- 是否启用
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  sys_user              IS '后台管理员用户表';
COMMENT ON COLUMN sys_user.username     IS '登录用户名，唯一';
COMMENT ON COLUMN sys_user.password     IS 'BCrypt 加密后的密码';
COMMENT ON COLUMN sys_user.display_name IS '显示名称';
COMMENT ON COLUMN sys_user.is_active    IS '是否启用，禁用后无法登录';

CREATE INDEX idx_sys_user_username ON sys_user(username);

-- 自动更新时间触发器
CREATE TRIGGER trg_sys_user_updated_at
    BEFORE UPDATE ON sys_user
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();