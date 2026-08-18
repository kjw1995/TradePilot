package com.kjw.tradepilot.order.adapter.out.messaging;

import com.kjw.tradepilot.order.application.port.out.OrderEventPublisherPort;
import com.kjw.tradepilot.order.domain.TradeOrder;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

@Component
class ReactorOrderEventPublisher implements OrderEventPublisherPort {
    private final Sinks.Many<TradeOrder> sink = Sinks.many().multicast().directBestEffort();

    @Override
    public Mono<Void> publish(TradeOrder order) {
        return Mono.fromRunnable(() -> {
            Sinks.EmitResult result = sink.tryEmitNext(order);
            if (result.isFailure() && result != Sinks.EmitResult.FAIL_ZERO_SUBSCRIBER) {
                throw new IllegalStateException("Unable to publish order event: " + result);
            }
        });
    }

    @Override
    public Flux<TradeOrder> stream(String accountId) {
        return sink.asFlux().filter(order -> order.accountId().equals(accountId));
    }
}
