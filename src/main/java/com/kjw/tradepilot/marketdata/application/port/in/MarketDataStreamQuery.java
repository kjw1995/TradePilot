package com.kjw.tradepilot.marketdata.application.port.in;

import com.kjw.tradepilot.marketdata.domain.MarketTick;
import reactor.core.publisher.Flux;

public interface MarketDataStreamQuery {
    Flux<MarketTick> stream();
}
