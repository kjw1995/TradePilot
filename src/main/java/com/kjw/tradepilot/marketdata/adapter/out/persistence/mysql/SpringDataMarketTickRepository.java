package com.kjw.tradepilot.marketdata.adapter.out.persistence.mysql;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

interface SpringDataMarketTickRepository extends ReactiveCrudRepository<MarketTickEntity, Long> {
    @Query("""
            SELECT id, symbol, market, price, volume, traded_at, received_at, source
            FROM market_ticks
            WHERE market = :market AND symbol = :symbol
            ORDER BY traded_at DESC, id DESC
            LIMIT 1
            """)
    Mono<MarketTickEntity> findLatest(String market, String symbol);
}
