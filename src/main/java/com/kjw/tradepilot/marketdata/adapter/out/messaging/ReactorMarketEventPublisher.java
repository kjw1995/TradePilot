package com.kjw.tradepilot.marketdata.adapter.out.messaging;

import com.kjw.tradepilot.marketdata.application.port.out.MarketEventPublisherPort;
import com.kjw.tradepilot.marketdata.domain.MarketTick;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

@Component
class ReactorMarketEventPublisher implements MarketEventPublisherPort {
    private final Sinks.Many<MarketTick> sink = Sinks.many().multicast().directBestEffort();

    @Override
    public Mono<Void> publish(MarketTick tick) {
        return Mono.fromRunnable(() -> {
            Sinks.EmitResult result = sink.tryEmitNext(tick);
            if (result.isFailure() && result != Sinks.EmitResult.FAIL_ZERO_SUBSCRIBER) {
                throw new IllegalStateException("Unable to publish market tick: " + result);
            }
        });
    }

    @Override
    public Flux<MarketTick> stream() {
        return sink.asFlux();
    }
}
