package com.kjw.tradepilot.instrument.application.port.in;

import com.kjw.tradepilot.instrument.domain.SecurityInstrument;
import com.kjw.tradepilot.marketdata.domain.Market;
import reactor.core.publisher.Flux;

public interface InstrumentSearchQuery {
    Flux<SecurityInstrument> search(Market market, String query, int limit);
}
