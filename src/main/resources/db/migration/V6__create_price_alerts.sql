CREATE TABLE price_alerts
(
    alert_id             CHAR(36)       NOT NULL,
    account_id           VARCHAR(50)    NOT NULL,
    market               VARCHAR(20)    NOT NULL,
    symbol               VARCHAR(20)    NOT NULL,
    name                 VARCHAR(100)   NOT NULL,
    condition_type       VARCHAR(10)    NOT NULL,
    target_price         DECIMAL(19, 4) NOT NULL,
    status               VARCHAR(20)    NOT NULL,
    last_triggered_price DECIMAL(19, 4) NULL,
    last_triggered_at    DATETIME(6)    NULL,
    created_at           DATETIME(6)    NOT NULL,
    updated_at           DATETIME(6)    NOT NULL,
    PRIMARY KEY (alert_id),
    CONSTRAINT fk_price_alerts_account
        FOREIGN KEY (account_id) REFERENCES portfolio_accounts (account_id),
    INDEX idx_price_alerts_account_created (account_id, created_at DESC),
    INDEX idx_price_alerts_evaluation (status, market, symbol)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
