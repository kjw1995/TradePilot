package com.kjw.tradepilot.order.application.port.out;

import com.kjw.tradepilot.order.domain.TradeOrder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface OrderEventPublisherPort {
    Mono<Void> publish(TradeOrder order);

    Flux<TradeOrder> stream(String accountId);
}
