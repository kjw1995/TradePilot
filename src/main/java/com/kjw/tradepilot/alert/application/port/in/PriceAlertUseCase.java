package com.kjw.tradepilot.alert.application.port.in;

import com.kjw.tradepilot.alert.domain.PriceAlert;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface PriceAlertUseCase {
    Mono<PriceAlert> create(CreatePriceAlertCommand command);

    Flux<PriceAlert> getAlerts(String accountId);

    Mono<Void> delete(String accountId, UUID alertId);

    Mono<PriceAlert> reactivate(String accountId, UUID alertId);

    Flux<PriceAlert> stream(String accountId);
}
