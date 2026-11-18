package dev.interviewcopilot.bff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Proves the relay streams: an event the upstream sends must reach the client while the upstream is still
 * holding the connection open, not when it finally closes.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StreamingTests {

    static final String SESSION = "7c9e6679-7425-40de-944b-e07fc1f90ae7";
    static HttpServer upstream;
    static final CountDownLatch release = new CountDownLatch(1);

    @Value("${local.server.port}")
    int port;

    @MockitoBean
    OAuth2AuthorizedClientManager manager;

    @BeforeAll
    static void start() throws IOException {
        upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        upstream.createContext("/api/v1/sessions/" + SESSION + "/suggestions/stream", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().write(": connected\n\n".getBytes(StandardCharsets.UTF_8));
            exchange.getResponseBody().flush();
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            exchange.close();
        });
        upstream.start();
    }

    @AfterAll
    static void stop() {
        release.countDown();
        upstream.stop(0);
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("bff.internal-gateway-url", () -> "http://127.0.0.1:" + upstream.getAddress().getPort());
    }

    @Test
    void firstEventReachesTheClientWhileTheUpstreamIsStillOpen() throws Exception {
        var registration = ClientRegistration.withRegistrationId("interview-copilot-bff")
                .clientId("c").authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .tokenUri("http://localhost/token").build();
        when(manager.authorize(any())).thenReturn(new OAuth2AuthorizedClient(registration, "bff",
                new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "t", Instant.now(), Instant.now().plusSeconds(300))));

        HttpResponse<java.io.InputStream> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/sessions/" + SESSION
                                + "/suggestions/stream"))
                        .header("X-User-Id", "u1").header("Accept", "text/event-stream")
                        .timeout(Duration.ofSeconds(5)).build(),
                HttpResponse.BodyHandlers.ofInputStream());
        try (var reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(reader.readLine()).isEqualTo(":connected"); // upstream has not closed yet
        } finally {
            release.countDown();
        }
    }
}
