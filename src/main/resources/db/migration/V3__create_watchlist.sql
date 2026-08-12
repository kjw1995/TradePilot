CREATE TABLE watchlist_items
(
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    account_id    VARCHAR(50)  NOT NULL,
    symbol        VARCHAR(20)  NOT NULL,
    market        VARCHAR(20)  NOT NULL,
    name          VARCHAR(100) NOT NULL,
    display_order INT          NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_watchlist_items_account
        FOREIGN KEY (account_id) REFERENCES portfolio_accounts (account_id),
    CONSTRAINT uk_watchlist_items_account_market_symbol
        UNIQUE (account_id, market, symbol),
    INDEX idx_watchlist_items_account_order (account_id, display_order)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

INSERT INTO watchlist_items (
    account_id, symbol, market, name, display_order, created_at
) VALUES
    ('local-account', '005930', 'KRX', '삼성전자', 0, UTC_TIMESTAMP(6)),
    ('local-account', '000660', 'KRX', 'SK하이닉스', 1, UTC_TIMESTAMP(6));
