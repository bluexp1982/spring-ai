-- ============================================================
-- SaaS 订阅订单管理系统 — 数据库表设计
-- 适用数据库: PostgreSQL 15+
-- 核心表: 套餐计划 → 订阅 → 订单 → 发票
-- ============================================================

-- -----------------------------------------------------------
-- 1. 套餐计划表 (saas_plans)
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS saas_plans (
    id              BIGSERIAL       PRIMARY KEY,
    name            VARCHAR(100)    NOT NULL,                           -- 套餐名称, e.g. "基础版", "专业版"
    code            VARCHAR(50)     NOT NULL UNIQUE,                    -- 唯一标识, e.g. "basic", "pro"
    description     TEXT,                                               -- 套餐描述
    price           NUMERIC(10, 2)  NOT NULL DEFAULT 0.00,             -- 价格
    currency        VARCHAR(3)      NOT NULL DEFAULT 'CNY',            -- 币种: CNY, USD, EUR
    billing_cycle   VARCHAR(20)     NOT NULL DEFAULT 'monthly',        -- 计费周期: monthly / yearly / quarterly / one_time
    trial_days      SMALLINT       NOT NULL DEFAULT 0,                 -- 试用天数, 0 表示无试用
    features        JSONB           DEFAULT '[]',                       -- 功能特性列表, e.g. ["api", "analytics", "team"]
    max_users       INT             NOT NULL DEFAULT 1,                 -- 最大用户数
    max_storage_mb  INT             NOT NULL DEFAULT 0,                 -- 最大存储空间 (MB), 0 表示不限
    sort_order      INT             NOT NULL DEFAULT 0,                 -- 排序权重, 越大越靠前
    is_active       BOOLEAN         NOT NULL DEFAULT TRUE,              -- 是否启用
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  saas_plans              IS '套餐计划表';
COMMENT ON COLUMN saas_plans.name         IS '套餐名称';
COMMENT ON COLUMN saas_plans.code         IS '唯一标识编码';
COMMENT ON COLUMN saas_plans.price        IS '价格 (元)';
COMMENT ON COLUMN saas_plans.billing_cycle IS '计费周期: monthly/yearly/quarterly/one_time';
COMMENT ON COLUMN saas_plans.trial_days   IS '试用天数, 0 表示无试用';
COMMENT ON COLUMN saas_plans.features     IS '功能特性 JSON 数组';
COMMENT ON COLUMN saas_plans.max_users    IS '最大用户数';


-- -----------------------------------------------------------
-- 2. 用户订阅表 (saas_subscriptions)
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS saas_subscriptions (
    id              BIGSERIAL       PRIMARY KEY,
    user_id         BIGINT          NOT NULL,                           -- 用户 ID (关联业务用户表)
    plan_id         BIGINT          NOT NULL REFERENCES saas_plans(id), -- 当前套餐
    status          VARCHAR(20)     NOT NULL DEFAULT 'active',          -- 状态: active / paused / expired / cancelled / in_trial
    auto_renew      BOOLEAN         NOT NULL DEFAULT TRUE,              -- 是否自动续费
    started_at      TIMESTAMPTZ     NOT NULL,                           -- 订阅开始时间
    expired_at      TIMESTAMPTZ,                                        -- 订阅到期时间 (NULL = 永久)
    trial_end_at    TIMESTAMPTZ,                                        -- 试用结束时间 (NULL = 无试用)
    cancelled_at    TIMESTAMPTZ,                                        -- 取消订阅时间
    paused_at       TIMESTAMPTZ,                                        -- 暂停时间
    paused_until    TIMESTAMPTZ,                                        -- 暂停恢复时间
    current_period_start TIMESTAMPTZ NOT NULL,                          -- 当前计费周期开始
    current_period_end   TIMESTAMPTZ NOT NULL,                          -- 当前计费周期结束
    metadata        JSONB           DEFAULT '{}',                       -- 扩展元数据
    deleted         SMALLINT        NOT NULL DEFAULT 0,                 -- 逻辑删除标记: 0-未删除, 1-已删除
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  saas_subscriptions            IS '用户订阅表';
COMMENT ON COLUMN saas_subscriptions.user_id    IS '用户 ID (关联业务用户表)';
COMMENT ON COLUMN saas_subscriptions.plan_id    IS '当前套餐 ID';
COMMENT ON COLUMN saas_subscriptions.status     IS '订阅状态: active/paused/expired/cancelled/in_trial';
COMMENT ON COLUMN saas_subscriptions.auto_renew IS '是否自动续费';
COMMENT ON COLUMN saas_subscriptions.metadata   IS '扩展元数据, 如外部支付系统订阅 ID';

-- 索引
CREATE INDEX idx_subscriptions_user_id    ON saas_subscriptions(user_id);
CREATE INDEX idx_subscriptions_plan_id    ON saas_subscriptions(plan_id);
CREATE INDEX idx_subscriptions_status     ON saas_subscriptions(status);
CREATE INDEX idx_subscriptions_expired_at ON saas_subscriptions(expired_at);


-- -----------------------------------------------------------
-- 3. 订阅订单表 (saas_subscription_orders)
--    核心表: 记录每一次订阅相关的支付、升降级、续费等操作
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS saas_subscription_orders (
    id                  BIGSERIAL       PRIMARY KEY,
    order_no            VARCHAR(64)     NOT NULL UNIQUE,                 -- 订单编号, 业务唯一
    subscription_id     BIGINT          NOT NULL REFERENCES saas_subscriptions(id),
    user_id             BIGINT          NOT NULL,                        -- 用户 ID
    plan_id             BIGINT          NOT NULL REFERENCES saas_plans(id),
    prev_plan_id        BIGINT,                                         -- 上一个套餐 ID (用于升降级场景)
    order_type          VARCHAR(20)     NOT NULL,                        -- 订单类型: new / renewal / upgrade / downgrade / refund / cancel
    status              VARCHAR(20)     NOT NULL DEFAULT 'pending',      -- 状态: pending / paid / failed / refunded / expired
    amount              NUMERIC(10, 2)  NOT NULL DEFAULT 0.00,           -- 订单金额
    currency            VARCHAR(3)      NOT NULL DEFAULT 'CNY',
    discount_amount     NUMERIC(10, 2)  NOT NULL DEFAULT 0.00,           -- 折扣金额
    final_amount        NUMERIC(10, 2)  NOT NULL DEFAULT 0.00,           -- 实付金额
    coupon_id           BIGINT,                                         -- 使用优惠券 ID
    payment_method      VARCHAR(50),                                    -- 支付方式: alipay / wechat / stripe / paypal
    payment_channel     VARCHAR(50),                                    -- 支付渠道: alipay_app / stripe_web / etc.
    transaction_id      VARCHAR(128),                                   -- 第三方支付平台交易 ID
    paid_at             TIMESTAMPTZ,                                    -- 支付时间
    period_start        TIMESTAMPTZ,                                    -- 购买周期开始
    period_end          TIMESTAMPTZ,                                    -- 购买周期结束
    invoice_id          BIGINT,                                         -- 关联发票 ID
    failure_reason      TEXT,                                           -- 支付失败原因
    refund_reason       TEXT,                                           -- 退款原因
    refunded_at         TIMESTAMPTZ,                                    -- 退款时间
    metadata            JSONB           DEFAULT '{}',                    -- 扩展元数据
    deleted             SMALLINT        NOT NULL DEFAULT 0,               -- 逻辑删除标记: 0-未删除, 1-已删除
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  saas_subscription_orders                IS '订阅订单表 — 核心交易表';
COMMENT ON COLUMN saas_subscription_orders.order_no       IS '订单编号, 业务唯一';
COMMENT ON COLUMN saas_subscription_orders.order_type     IS '订单类型: new/renewal/upgrade/downgrade/refund/cancel';
COMMENT ON COLUMN saas_subscription_orders.status         IS '订单状态: pending/paid/failed/refunded/expired';
COMMENT ON COLUMN saas_subscription_orders.transaction_id IS '第三方支付平台交易流水号';
COMMENT ON COLUMN saas_subscription_orders.metadata       IS '扩展信息, 如税率、发票抬头等';

-- 索引
CREATE INDEX idx_orders_subscription_id ON saas_subscription_orders(subscription_id);
CREATE INDEX idx_orders_user_id         ON saas_subscription_orders(user_id);
CREATE INDEX idx_orders_status          ON saas_subscription_orders(status);
CREATE INDEX idx_orders_order_type      ON saas_subscription_orders(order_type);
CREATE INDEX idx_orders_created_at      ON saas_subscription_orders(created_at);


-- -----------------------------------------------------------
-- 4. 发票表 (saas_invoices)
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS saas_invoices (
    id                  BIGSERIAL       PRIMARY KEY,
    invoice_no          VARCHAR(64)     NOT NULL UNIQUE,                 -- 发票编号
    order_id            BIGINT          NOT NULL REFERENCES saas_subscription_orders(id),
    subscription_id     BIGINT          NOT NULL REFERENCES saas_subscriptions(id),
    user_id             BIGINT          NOT NULL,
    title               VARCHAR(200),                                   -- 发票抬头
    tax_id_no           VARCHAR(50),                                    -- 纳税人识别号
    amount              NUMERIC(10, 2)  NOT NULL DEFAULT 0.00,           -- 开票金额
    tax_amount          NUMERIC(10, 2)  NOT NULL DEFAULT 0.00,           -- 税额
    total_amount        NUMERIC(10, 2)  NOT NULL DEFAULT 0.00,           -- 含税总金额
    status              VARCHAR(20)     NOT NULL DEFAULT 'pending',      -- 状态: pending / issued / void
    issued_at           TIMESTAMPTZ,                                    -- 开票时间
    file_url            VARCHAR(500),                                   -- 电子发票文件地址
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  saas_invoices           IS '发票表';
COMMENT ON COLUMN saas_invoices.title     IS '发票抬头';
COMMENT ON COLUMN saas_invoices.tax_id_no IS '纳税人识别号';

CREATE INDEX idx_invoices_order_id      ON saas_invoices(order_id);
CREATE INDEX idx_invoices_subscription_id ON saas_invoices(subscription_id);
CREATE INDEX idx_invoices_user_id       ON saas_invoices(user_id);


-- -----------------------------------------------------------
-- 5. 优惠券/折扣码表 (saas_coupons)
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS saas_coupons (
    id              BIGSERIAL       PRIMARY KEY,
    code            VARCHAR(50)     NOT NULL UNIQUE,                    -- 优惠码, e.g. "WELCOME50"
    name            VARCHAR(100),                                       -- 优惠名称
    discount_type   VARCHAR(20)     NOT NULL,                           -- 折扣类型: percentage / fixed
    discount_value  NUMERIC(10, 2)  NOT NULL,                           -- 折扣值: 百分比(如 20) 或固定金额(如 50.00)
    min_amount      NUMERIC(10, 2)  DEFAULT 0.00,                       -- 最低消费金额, 0 表示不限
    max_discount    NUMERIC(10, 2),                                     -- 最大折扣金额 (百分比折扣时有效)
    max_uses        INT             NOT NULL DEFAULT 0,                  -- 最大使用次数, 0 表示不限
    current_uses    INT             NOT NULL DEFAULT 0,                  -- 已使用次数
    max_user_uses   INT             NOT NULL DEFAULT 1,                  -- 每用户最大使用次数
    applicable_plan_ids BIGINT[],                                       -- 适用套餐 ID 列表, NULL 表示全部
    valid_from      TIMESTAMPTZ     NOT NULL,                           -- 生效时间
    valid_until     TIMESTAMPTZ     NOT NULL,                           -- 过期时间
    is_active       BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  saas_coupons                    IS '优惠券/折扣码表';
COMMENT ON COLUMN saas_coupons.discount_type      IS '折扣类型: percentage(百分比) / fixed(固定金额)';
COMMENT ON COLUMN saas_coupons.applicable_plan_ids IS '适用套餐 ID 数组, NULL 表示全部可用';

CREATE INDEX idx_coupons_code        ON saas_coupons(code);
CREATE INDEX idx_coupons_valid_range ON saas_coupons(valid_from, valid_until);


-- -----------------------------------------------------------
-- 6. 优惠券使用记录表 (saas_coupon_usages)
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS saas_coupon_usages (
    id              BIGSERIAL       PRIMARY KEY,
    coupon_id       BIGINT          NOT NULL REFERENCES saas_coupons(id),
    order_id        BIGINT          NOT NULL REFERENCES saas_subscription_orders(id),
    user_id         BIGINT          NOT NULL,
    discount_amount NUMERIC(10, 2)  NOT NULL,                           -- 实际抵扣金额
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE saas_coupon_usages IS '优惠券使用记录表';

CREATE INDEX idx_coupon_usages_coupon_id ON saas_coupon_usages(coupon_id);
CREATE INDEX idx_coupon_usages_order_id  ON saas_coupon_usages(order_id);
CREATE INDEX idx_coupon_usages_user_id   ON saas_coupon_usages(user_id);


-- -----------------------------------------------------------
-- 7. 自动更新时间触发器
-- -----------------------------------------------------------
CREATE OR REPLACE FUNCTION update_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 为需要自动更新 updated_at 的表创建触发器
CREATE TRIGGER trg_plans_updated_at
    BEFORE UPDATE ON saas_plans
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();

CREATE TRIGGER trg_subscriptions_updated_at
    BEFORE UPDATE ON saas_subscriptions
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();

CREATE TRIGGER trg_orders_updated_at
    BEFORE UPDATE ON saas_subscription_orders
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();

CREATE TRIGGER trg_invoices_updated_at
    BEFORE UPDATE ON saas_invoices
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();