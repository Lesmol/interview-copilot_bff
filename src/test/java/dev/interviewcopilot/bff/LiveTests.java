package dev.interviewcopilot.bff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
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
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class LiveTests {

    static final String SESSION = "7c9e6679-7425-40de-944b-e07fc1f90ae7";
    static final String MISSING = "00000000-0000-0000-0000-000000000404";
    static HttpServer internalGateway;
    static final AtomicReference<String> seenUser = new AtomicReference<>();
    static final AtomicReference<String> seenPatchBody = new AtomicReference<>();

    @Autowired
    MockMvc mvc;

    @MockitoBean
    OAuth2AuthorizedClientManager manager;

    @BeforeAll
    static void start() throws IOException {
        internalGateway = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        internalGateway.createContext("/api/v1/sessions/" + SESSION + "/suggestions/stream", exchange -> {
            seenUser.set(exchange.getRequestHeaders().getFirst("X-User-Id"));
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().write(": connected\n\n".getBytes(StandardCharsets.UTF_8));
            exchange.getResponseBody().flush();
            exchange.getResponseBody().write("event: suggestion\ndata: {\"text\":\"Which cuts?\"}\n\n"
                    .getBytes(StandardCharsets.UTF_8));
            exchange.close();
        });
        internalGateway.createContext("/api/v1/sessions/" + MISSING + "/suggestions", exchange -> {
            byte[] body = "{\"title\":\"Not Found\",\"status\":404}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/problem+json");
            exchange.sendResponseHeaders(404, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        internalGateway.createContext("/api/v1/suggestions/", exchange -> {
            seenPatchBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = ("{\"id\":\"11111111-1111-1111-1111-111111111111\",\"sessionId\":\"" + SESSION
                    + "\",\"type\":\"vague\",\"text\":\"Which cuts?\",\"quote\":\"cuts\",\"sourceSeqs\":[2],"
                    + "\"status\":\"asked\",\"createdAt\":\"2026-11-18T10:00:00Z\"}").getBytes(StandardCharsets.UTF_8);
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
        registry.add("bff.internal-gateway-url", () -> "http://127.0.0.1:" + internalGateway.getAddress().getPort());
    }

    private void serviceToken() {
        var registration = ClientRegistration.withRegistrationId("interview-copilot-bff")
                .clientId("c").authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .tokenUri("http://localhost/token").build();
        when(manager.authorize(any())).thenReturn(new OAuth2AuthorizedClient(registration, "bff",
                new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "service-token",
                        Instant.now(), Instant.now().plusSeconds(300))));
    }

    @Test
    void relaysTheEventStreamAndTheUserId() throws Exception {
        serviceToken();
        var started = mvc.perform(get("/api/v1/sessions/" + SESSION + "/suggestions/stream")
                        .header("X-User-Id", "u1").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted()).andReturn();
        mvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.matchesPattern("(?s).*event:suggestion\\ndata:\\{\"text\":\"Which cuts\\?\"}\\n\\n.*")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Which cuts?")));
        assertThat(seenUser.get()).isEqualTo("u1");
    }

    @Test
    void anUpstream404KeepsItsStatusAndProblemBody() throws Exception {
        serviceToken();
        mvc.perform(get("/api/v1/sessions/" + MISSING + "/suggestions").header("X-User-Id", "u1"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Not Found")));
    }

    @Test
    void relaysTheSuggestionStatusChange() throws Exception {
        serviceToken();
        mvc.perform(patch("/api/v1/suggestions/11111111-1111-1111-1111-111111111111")
                        .header("X-User-Id", "u1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"asked\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"status\":\"asked\"")));
        assertThat(seenPatchBody.get()).contains("asked");
    }
}
