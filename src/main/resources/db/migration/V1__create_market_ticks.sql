CREATE TABLE market_ticks
(
    id          BIGINT         NOT NULL AUTO_INCREMENT,
    symbol      VARCHAR(20)    NOT NULL,
    market      VARCHAR(20)    NOT NULL,
    price       DECIMAL(19, 4) NOT NULL,
    volume      BIGINT         NOT NULL,
    traded_at   DATETIME(6)    NOT NULL,
    received_at DATETIME(6)    NOT NULL,
    source      VARCHAR(30)    NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_market_ticks_symbol_time (market, symbol, traded_at DESC),
    INDEX idx_market_ticks_received_at (received_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
