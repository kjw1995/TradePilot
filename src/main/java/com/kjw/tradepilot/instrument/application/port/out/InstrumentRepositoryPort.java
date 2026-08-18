package com.kjw.tradepilot.instrument.application.port.out;

import com.kjw.tradepilot.instrument.domain.SecurityInstrument;
import com.kjw.tradepilot.marketdata.domain.Market;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface InstrumentRepositoryPort {
    Mono<SecurityInstrument> find(Market market, String symbol);

    Flux<SecurityInstrument> search(Market market, String query, int limit);
}
