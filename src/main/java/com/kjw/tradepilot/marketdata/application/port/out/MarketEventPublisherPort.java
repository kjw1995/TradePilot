package com.kjw.tradepilot.marketdata.application.port.out;

import com.kjw.tradepilot.marketdata.domain.MarketTick;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface MarketEventPublisherPort {
    Mono<Void> publish(MarketTick tick);

    Flux<MarketTick> stream();
}
