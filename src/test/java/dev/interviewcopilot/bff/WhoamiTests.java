package dev.interviewcopilot.bff;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
class WhoamiTests {

    static HttpServer internalGateway;
    static final AtomicReference<String> seenAuth = new AtomicReference<>();
    static final AtomicReference<String> seenUser = new AtomicReference<>();
    static final AtomicReference<String> seenQuery = new AtomicReference<>();

    @Autowired
    MockMvc mvc;

    @MockitoBean
    OAuth2AuthorizedClientManager manager;

    @BeforeAll
    static void start() throws IOException {
        internalGateway = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        internalGateway.createContext("/api/whoami", exchange -> {
            seenAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            seenUser.set(exchange.getRequestHeaders().getFirst("X-User-Id"));
            byte[] body = "{\"userId\":\"u1\",\"clientId\":\"interview-copilot-bff\"}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        internalGateway.createContext("/api/stt-token", exchange -> {
            seenAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            seenUser.set(exchange.getRequestHeaders().getFirst("X-User-Id"));
            seenQuery.set(exchange.getRequestURI().getQuery());
            byte[] body = "{\"provider\":\"deepgram\",\"token\":\"temp\",\"expiresInSeconds\":60}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        internalGateway.start();
    }

    @AfterAll
    static void stop() {
        internalGateway.stop(0);
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("INTERNAL_GATEWAY_URL", () -> "http://127.0.0.1:" + internalGateway.getAddress().getPort());
    }

    @Test
    void rejectsRequestsWithoutUserId() throws Exception {
        mvc.perform(get("/api/whoami")).andExpect(status().isBadRequest());
    }

    @Test
    void relaysWithServiceTokenAndUserId() throws Exception {
        var registration = ClientRegistration.withRegistrationId("x").clientId("x")
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS).tokenUri("http://unused").build();
        var token = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "service-token",
                Instant.now(), Instant.now().plusSeconds(60));
        when(manager.authorize(any())).thenReturn(new OAuth2AuthorizedClient(registration, "x", token));

        mvc.perform(get("/api/whoami").header("X-User-Id", "u1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("u1"));

        assertThat(seenAuth.get()).isEqualTo("Bearer service-token");
        assertThat(seenUser.get()).isEqualTo("u1");
    }

    @Test
    void relaysSpeechTokenWithProviderAndUserId() throws Exception {
        var registration = ClientRegistration.withRegistrationId("x").clientId("x")
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS).tokenUri("http://unused").build();
        var token = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "service-token",
                Instant.now(), Instant.now().plusSeconds(60));
        when(manager.authorize(any())).thenReturn(new OAuth2AuthorizedClient(registration, "x", token));

        mvc.perform(get("/api/stt-token?provider=deepgram").header("X-User-Id", "u1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("temp"))
                .andExpect(jsonPath("$.expiresInSeconds").value(60));

        assertThat(seenQuery.get()).isEqualTo("provider=deepgram");
        assertThat(seenUser.get()).isEqualTo("u1");
    }
}
