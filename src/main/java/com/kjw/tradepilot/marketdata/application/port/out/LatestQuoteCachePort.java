package com.kjw.tradepilot.marketdata.application.port.out;

import com.kjw.tradepilot.marketdata.domain.Market;
import com.kjw.tradepilot.marketdata.domain.MarketTick;
import reactor.core.publisher.Mono;

public interface LatestQuoteCachePort {
    Mono<Void> put(MarketTick tick);

    Mono<MarketTick> get(Market market, String symbol);
}
