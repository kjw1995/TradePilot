CREATE TABLE security_instruments
(
    market     VARCHAR(20)  NOT NULL,
    symbol     VARCHAR(20)  NOT NULL,
    name       VARCHAR(100) NOT NULL,
    exchange   VARCHAR(20)  NOT NULL,
    currency   CHAR(3)      NOT NULL,
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    updated_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (market, symbol),
    INDEX idx_security_instruments_market_name (market, name),
    INDEX idx_security_instruments_active (market, active)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

INSERT INTO security_instruments (
    market, symbol, name, exchange, currency, active, updated_at
) VALUES
    ('KRX', '005930', '삼성전자', 'KOSPI', 'KRW', TRUE, UTC_TIMESTAMP(6)),
    ('KRX', '000660', 'SK하이닉스', 'KOSPI', 'KRW', TRUE, UTC_TIMESTAMP(6)),
    ('KRX', '035420', 'NAVER', 'KOSPI', 'KRW', TRUE, UTC_TIMESTAMP(6)),
    ('KRX', '035720', '카카오', 'KOSPI', 'KRW', TRUE, UTC_TIMESTAMP(6)),
    ('KRX', '005380', '현대차', 'KOSPI', 'KRW', TRUE, UTC_TIMESTAMP(6)),
    ('KRX', '000270', '기아', 'KOSPI', 'KRW', TRUE, UTC_TIMESTAMP(6)),
    ('KRX', '068270', '셀트리온', 'KOSPI', 'KRW', TRUE, UTC_TIMESTAMP(6)),
    ('KRX', '373220', 'LG에너지솔루션', 'KOSPI', 'KRW', TRUE, UTC_TIMESTAMP(6)),
    ('KRX', '207940', '삼성바이오로직스', 'KOSPI', 'KRW', TRUE, UTC_TIMESTAMP(6)),
    ('KRX', '005490', 'POSCO홀딩스', 'KOSPI', 'KRW', TRUE, UTC_TIMESTAMP(6)),
    ('KRX', '105560', 'KB금융', 'KOSPI', 'KRW', TRUE, UTC_TIMESTAMP(6)),
    ('KRX', '055550', '신한지주', 'KOSPI', 'KRW', TRUE, UTC_TIMESTAMP(6)),
    ('KRX', '012450', '한화에어로스페이스', 'KOSPI', 'KRW', TRUE, UTC_TIMESTAMP(6)),
    ('KRX', '034020', '두산에너빌리티', 'KOSPI', 'KRW', TRUE, UTC_TIMESTAMP(6)),
    ('KRX', '051910', 'LG화학', 'KOSPI', 'KRW', TRUE, UTC_TIMESTAMP(6)),
    ('KRX', '006400', '삼성SDI', 'KOSPI', 'KRW', TRUE, UTC_TIMESTAMP(6)),
    ('KRX', '247540', '에코프로비엠', 'KOSDAQ', 'KRW', TRUE, UTC_TIMESTAMP(6)),
    ('KRX', '086520', '에코프로', 'KOSDAQ', 'KRW', TRUE, UTC_TIMESTAMP(6));
