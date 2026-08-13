package com.kjw.tradepilot;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureWebTestClient
class TradepilotApplicationTests {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void contextLoads() {
    }

    @Test
    void servesRealtimeDashboardFromRoot() {
        webTestClient.get()
                .uri("/")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith("text/html")
                .expectBody(String.class)
                .value(body -> {
                    assertThat(body).contains("TradePilot · Live Market");
                    assertThat(body).contains("data-testid=\"price-chart\"");
                    assertThat(body).contains("data-testid=\"portfolio-comparison\"");
                    assertThat(body).contains("id=\"watchlist-form\"");
                    assertThat(body).contains("id=\"quote-list\"");
                    assertThat(body).contains("id=\"instrument-search\"");
                    assertThat(body).contains("id=\"instrument-results\"");
                });
    }

}
