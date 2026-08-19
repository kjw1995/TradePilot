package com.kjw.tradepilot.alert.application.port.out;

import com.kjw.tradepilot.alert.domain.PriceAlert;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface PriceAlertEventPublisherPort {
    Mono<Void> publish(PriceAlert alert);

    Flux<PriceAlert> stream(String accountId);
}
