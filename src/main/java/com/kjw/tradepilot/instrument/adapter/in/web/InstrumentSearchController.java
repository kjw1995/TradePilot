package com.kjw.tradepilot.instrument.adapter.in.web;

import com.kjw.tradepilot.instrument.application.port.in.InstrumentSearchQuery;
import com.kjw.tradepilot.marketdata.domain.Market;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/v1/instruments")
class InstrumentSearchController {
    private final InstrumentSearchQuery instrumentSearchQuery;

    InstrumentSearchController(InstrumentSearchQuery instrumentSearchQuery) {
        this.instrumentSearchQuery = instrumentSearchQuery;
    }

    @GetMapping("/search")
    Flux<InstrumentSearchResponse> search(
            @RequestParam String query,
            @RequestParam(defaultValue = "KRX") Market market,
            @RequestParam(defaultValue = "8") int limit
    ) {
        return instrumentSearchQuery.search(market, query, limit)
                .map(InstrumentSearchResponse::from);
    }
}
