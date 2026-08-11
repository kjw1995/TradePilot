package com.kjw.tradepilot.marketdata.application.port.in;

import com.kjw.tradepilot.marketdata.domain.Market;
import com.kjw.tradepilot.marketdata.domain.MarketTick;
import reactor.core.publisher.Mono;

public interface MarketDataQuery {
    Mono<MarketTick> getLatest(Market market, String symbol);
}
