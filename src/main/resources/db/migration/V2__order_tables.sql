-- G3-F07 Tạo đơn hàng — Order Service owned tables (Order DB).
-- Other services own their own schemas (BO config, Commercial credit, Payment
-- transaction); Order consumes those over HTTP and does NOT create them here.

CREATE TABLE `order` (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    agent_id           BIGINT       NOT NULL,
    currency           VARCHAR(10)  NOT NULL,
    status             VARCHAR(32)  NOT NULL DEFAULT 'AWAITING_PAYMENT',
    subtotal           BIGINT       NOT NULL,
    order_discount     BIGINT       NOT NULL DEFAULT 0,
    grand_total        BIGINT       NOT NULL,
    failure_reason     TEXT         NULL,
    expired_at         DATETIME     NULL,
    timeout_emitted_at DATETIME     NULL,
    created_at         DATETIME     NOT NULL,
    updated_at         DATETIME     NOT NULL,
    PRIMARY KEY (id),
    KEY idx_order_agent_status (agent_id, status),
    KEY idx_order_status_created (status, created_at)
);

CREATE TABLE order_detail (
    id                    BIGINT NOT NULL AUTO_INCREMENT,
    order_id              BIGINT NOT NULL,
    product_id            BIGINT NOT NULL,
    quantity              INT    NOT NULL,
    unit_price_original   BIGINT NOT NULL,
    unit_price_discounted BIGINT NOT NULL,
    line_subtotal         BIGINT NOT NULL,
    line_discount         BIGINT NOT NULL DEFAULT 0,
    line_total            BIGINT NOT NULL,
    created_at            DATETIME NOT NULL,
    PRIMARY KEY (id),
    KEY idx_order_detail_order (order_id)
);

CREATE TABLE order_discounts (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    order_id        BIGINT        NOT NULL,
    policy_id       BIGINT        NOT NULL,
    policy_name     VARCHAR(200)  NOT NULL,
    scope           VARCHAR(16)   NOT NULL,
    target_item_id  BIGINT        NULL,
    discount_type   VARCHAR(16)   NOT NULL,
    discount_value  DECIMAL(15,4) NOT NULL,
    discount_amount BIGINT        NOT NULL,
    created_at      DATETIME      NOT NULL,
    PRIMARY KEY (id),
    KEY idx_order_discounts_order (order_id)
);

CREATE TABLE order_payment (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    order_id        BIGINT      NOT NULL,
    payment_trans_id BIGINT     NOT NULL,
    channel_id      BIGINT      NOT NULL,
    status          VARCHAR(16) NOT NULL DEFAULT 'CREATED',
    attempt_number  INT         NOT NULL DEFAULT 1,
    created_at      DATETIME    NOT NULL,
    updated_at      DATETIME    NOT NULL,
    PRIMARY KEY (id),
    KEY idx_order_payment_order (order_id, created_at),
    KEY idx_order_payment_trans (payment_trans_id)
);

CREATE TABLE order_outbox (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    event_id      VARCHAR(36) NOT NULL,
    event_type    VARCHAR(50) NOT NULL,
    aggregate_id  BIGINT      NOT NULL,
    payload       TEXT        NOT NULL,
    status        VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    attempt_count INT         NOT NULL DEFAULT 0,
    max_attempts  INT         NOT NULL DEFAULT 5,
    next_retry_at DATETIME    NOT NULL,
    last_error    TEXT        NULL,
    created_at    DATETIME    NOT NULL,
    updated_at    DATETIME    NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_outbox_event (event_id),
    KEY idx_order_outbox_poll (status, next_retry_at)
);
