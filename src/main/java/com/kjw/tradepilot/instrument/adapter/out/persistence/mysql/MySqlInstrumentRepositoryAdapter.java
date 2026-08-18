package com.kjw.tradepilot.instrument.adapter.out.persistence.mysql;

import com.kjw.tradepilot.instrument.application.port.out.InstrumentRepositoryPort;
import com.kjw.tradepilot.instrument.domain.SecurityInstrument;
import com.kjw.tradepilot.marketdata.domain.Market;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Repository
class MySqlInstrumentRepositoryAdapter implements InstrumentRepositoryPort {
    private final DatabaseClient databaseClient;

    MySqlInstrumentRepositoryAdapter(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public Mono<SecurityInstrument> find(Market market, String symbol) {
        return databaseClient.sql("""
                        SELECT market, symbol, name, exchange, currency, updated_at
                        FROM security_instruments
                        WHERE market = :market AND symbol = :symbol AND active = TRUE
                        """)
                .bind("market", market.name())
                .bind("symbol", symbol)
                .map((row, metadata) -> mapInstrument(row))
                .one();
    }

    @Override
    public Flux<SecurityInstrument> search(Market market, String query, int limit) {
        return databaseClient.sql("""
                        SELECT market, symbol, name, exchange, currency, updated_at
                        FROM security_instruments
                        WHERE market = :market
                          AND active = TRUE
                          AND (symbol LIKE CONCAT(:query, '%') OR name LIKE CONCAT('%', :query, '%'))
                        ORDER BY CASE
                                     WHEN symbol = :query THEN 0
                                     WHEN UPPER(name) = :query THEN 1
                                     WHEN symbol LIKE CONCAT(:query, '%') THEN 2
                                     WHEN UPPER(name) LIKE CONCAT(:query, '%') THEN 3
                                     ELSE 4
                                 END,
                                 name ASC
                        LIMIT :limit
                        """)
                .bind("market", market.name())
                .bind("query", query)
                .bind("limit", limit)
                .map((row, metadata) -> mapInstrument(row))
                .all();
    }

    private SecurityInstrument mapInstrument(io.r2dbc.spi.Row row) {
        return new SecurityInstrument(
                Market.valueOf(row.get("market", String.class)),
                row.get("symbol", String.class),
                row.get("name", String.class),
                row.get("exchange", String.class),
                row.get("currency", String.class),
                row.get("updated_at", LocalDateTime.class).toInstant(ZoneOffset.UTC)
        );
    }
}
