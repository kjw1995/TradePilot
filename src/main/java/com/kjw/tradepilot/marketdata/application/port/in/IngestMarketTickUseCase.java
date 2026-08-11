package com.kjw.tradepilot.marketdata.application.port.in;

import com.kjw.tradepilot.marketdata.domain.MarketTick;
import reactor.core.publisher.Mono;

public interface IngestMarketTickUseCase {
    Mono<MarketTick> ingest(MarketTick tick);
}
