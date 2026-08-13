package com.kjw.tradepilot.instrument.adapter.in.web;

import com.kjw.tradepilot.instrument.domain.SecurityInstrument;
import com.kjw.tradepilot.marketdata.domain.Market;

record InstrumentSearchResponse(
        Market market,
        String symbol,
        String name,
        String exchange,
        String currency
) {
    static InstrumentSearchResponse from(SecurityInstrument instrument) {
        return new InstrumentSearchResponse(
                instrument.market(),
                instrument.symbol(),
                instrument.name(),
                instrument.exchange(),
                instrument.currency()
        );
    }
}
