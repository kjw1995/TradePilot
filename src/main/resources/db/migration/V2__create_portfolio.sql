CREATE TABLE portfolio_accounts
(
    account_id            VARCHAR(50)     NOT NULL,
    display_name          VARCHAR(100)    NOT NULL,
    broker                VARCHAR(50)     NOT NULL,
    masked_account_number VARCHAR(50)     NOT NULL,
    cash_balance          DECIMAL(19, 4)  NOT NULL,
    currency              CHAR(3)         NOT NULL,
    synced_at             DATETIME(6)     NOT NULL,
    PRIMARY KEY (account_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE portfolio_positions
(
    id            BIGINT         NOT NULL AUTO_INCREMENT,
    account_id    VARCHAR(50)    NOT NULL,
    symbol        VARCHAR(20)    NOT NULL,
    market        VARCHAR(20)    NOT NULL,
    name          VARCHAR(100)   NOT NULL,
    quantity      BIGINT         NOT NULL,
    average_price DECIMAL(19, 4) NOT NULL,
    synced_at     DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_portfolio_positions_account
        FOREIGN KEY (account_id) REFERENCES portfolio_accounts (account_id),
    CONSTRAINT uk_portfolio_positions_account_symbol
        UNIQUE (account_id, market, symbol),
    INDEX idx_portfolio_positions_account (account_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

INSERT INTO portfolio_accounts (
    account_id, display_name, broker, masked_account_number, cash_balance, currency, synced_at
) VALUES (
    'local-account', '내 투자계좌', 'LOCAL', '1234-****', 3500000, 'KRW', UTC_TIMESTAMP(6)
);

INSERT INTO portfolio_positions (
    account_id, symbol, market, name, quantity, average_price, synced_at
) VALUES
    ('local-account', '005930', 'KRX', '삼성전자', 25, 76000, UTC_TIMESTAMP(6)),
    ('local-account', '000660', 'KRX', 'SK하이닉스', 8, 205000, UTC_TIMESTAMP(6));
