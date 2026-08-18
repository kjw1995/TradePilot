CREATE TABLE trade_orders
(
    order_id          CHAR(36)        NOT NULL,
    account_id        VARCHAR(50)     NOT NULL,
    market            VARCHAR(20)     NOT NULL,
    symbol            VARCHAR(20)     NOT NULL,
    name              VARCHAR(100)    NOT NULL,
    side              VARCHAR(10)     NOT NULL,
    order_type        VARCHAR(10)     NOT NULL,
    quantity          BIGINT          NOT NULL,
    limit_price       DECIMAL(19, 4)  NULL,
    status            VARCHAR(20)     NOT NULL,
    filled_quantity   BIGINT          NOT NULL DEFAULT 0,
    average_fill_price DECIMAL(19, 4) NULL,
    idempotency_key   VARCHAR(100)    NOT NULL,
    created_at        DATETIME(6)     NOT NULL,
    updated_at        DATETIME(6)     NOT NULL,
    PRIMARY KEY (order_id),
    CONSTRAINT fk_trade_orders_account
        FOREIGN KEY (account_id) REFERENCES portfolio_accounts (account_id),
    CONSTRAINT uk_trade_orders_account_idempotency
        UNIQUE (account_id, idempotency_key),
    INDEX idx_trade_orders_account_created (account_id, created_at DESC),
    INDEX idx_trade_orders_matching (status, market, symbol)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE trade_executions
(
    execution_id CHAR(36)       NOT NULL,
    order_id     CHAR(36)       NOT NULL,
    account_id   VARCHAR(50)    NOT NULL,
    market       VARCHAR(20)    NOT NULL,
    symbol       VARCHAR(20)    NOT NULL,
    side         VARCHAR(10)    NOT NULL,
    quantity     BIGINT         NOT NULL,
    price        DECIMAL(19, 4) NOT NULL,
    executed_at  DATETIME(6)    NOT NULL,
    PRIMARY KEY (execution_id),
    CONSTRAINT fk_trade_executions_order
        FOREIGN KEY (order_id) REFERENCES trade_orders (order_id),
    INDEX idx_trade_executions_account_time (account_id, executed_at DESC),
    INDEX idx_trade_executions_order (order_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
