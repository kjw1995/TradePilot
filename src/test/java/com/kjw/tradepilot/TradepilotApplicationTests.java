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
    void servesDashboardFromRoot() {
        webTestClient.get()
                .uri("/")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith("text/html")
                .expectBody(String.class)
                .value(body -> {
                    assertThat(body).contains("TradePilot · 대시보드");
                    assertThat(body).contains("data-page=\"dashboard\"");
                    assertThat(body).contains("href=\"/watchlist.html\"");
                    assertThat(body).contains("href=\"/portfolio.html\"");
                    assertThat(body).contains("href=\"/activity.html\"");
                });
    }

    @Test
    void servesWatchlistPage() {
        webTestClient.get().uri("/watchlist.html").exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith("text/html")
                .expectBody(String.class)
                .value(body -> {
                    assertThat(body).contains("data-page=\"watchlist\"");
                    assertThat(body).contains("id=\"watchlist-form\"");
                    assertThat(body).contains("id=\"instrument-search\"");
                    assertThat(body).contains("data-testid=\"price-chart\"");
                });
    }

    @Test
    void servesPortfolioPage() {
        webTestClient.get().uri("/portfolio.html").exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> {
                    assertThat(body).contains("data-page=\"portfolio\"");
                    assertThat(body).contains("data-testid=\"portfolio-comparison\"");
                    assertThat(body).contains("id=\"portfolio-body\"");
                });
    }

    @Test
    void servesActivityPage() {
        webTestClient.get().uri("/activity.html").exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> {
                    assertThat(body).contains("data-page=\"activity\"");
                    assertThat(body).contains("id=\"activity-body\"");
                    assertThat(body).contains("id=\"event-count\"");
                });
    }

}
