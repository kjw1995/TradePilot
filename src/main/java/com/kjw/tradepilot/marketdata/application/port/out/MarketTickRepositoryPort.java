package com.kjw.tradepilot.marketdata.application.port.out;

import com.kjw.tradepilot.marketdata.domain.Market;
import com.kjw.tradepilot.marketdata.domain.MarketTick;
import reactor.core.publisher.Mono;

public interface MarketTickRepositoryPort {
    Mono<MarketTick> save(MarketTick tick);

    Mono<MarketTick> findLatest(Market market, String symbol);
}
