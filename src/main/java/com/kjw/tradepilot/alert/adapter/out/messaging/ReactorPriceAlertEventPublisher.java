package com.kjw.tradepilot.alert.adapter.out.messaging;

import com.kjw.tradepilot.alert.application.port.out.PriceAlertEventPublisherPort;
import com.kjw.tradepilot.alert.domain.PriceAlert;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

@Component
class ReactorPriceAlertEventPublisher implements PriceAlertEventPublisherPort {
    private final Sinks.Many<PriceAlert> sink = Sinks.many().multicast().directBestEffort();

    @Override
    public Mono<Void> publish(PriceAlert alert) {
        return Mono.fromRunnable(() -> {
            Sinks.EmitResult result = sink.tryEmitNext(alert);
            if (result.isFailure() && result != Sinks.EmitResult.FAIL_ZERO_SUBSCRIBER) {
                throw new IllegalStateException("Unable to publish price alert event: " + result);
            }
        });
    }

    @Override
    public Flux<PriceAlert> stream(String accountId) {
        return sink.asFlux().filter(alert -> alert.accountId().equals(accountId));
    }
}
