package dev.interviewcopilot.bff;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** What the live screen needs: start a session, post final lines, follow and triage suggestions. */
@RestController
@RequestMapping("/api/v1")
class LiveController {

    record CreateSessionRequest(@NotBlank @Size(max = 200) String title,
                                @Size(max = 200) String interviewee,
                                @Size(max = 2000) String angle,
                                String interviewType,
                                Set<String> suggestionTypes,
                                String frequency) {
    }

    record SessionResponse(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id, @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String title, String interviewee, String angle,
                           @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String interviewType, @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Set<String> suggestionTypes, @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String frequency,
                           @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant startedAt, Instant endedAt) {
    }

    record UtteranceRequest(@NotBlank @Size(max = 5000) String text,
                            @Size(max = 100) String speaker,
                            @PositiveOrZero long startMs,
                            @PositiveOrZero long endMs) {
    }

    record UtteranceResponse(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id, @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int seq, String speaker, @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String text, @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long startMs, @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long endMs) {
    }

    record SuggestionResponse(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id, @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID sessionId,
                              @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"follow_up", "vague", "contradiction", "aside"}) String type,
                              @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String text, String quote, @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Integer> sourceSeqs,
                              @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"open", "asked", "dismissed", "pinned"}) String status,
                              @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt) {
    }

    record SuggestionStatusRequest(@NotBlank @Schema(allowableValues = {"open", "asked", "dismissed", "pinned"}) String status) {
    }

    private final RestClient internalGateway;

    LiveController(RestClient internalGatewayClient) {
        this.internalGateway = internalGatewayClient;
    }

    @PostMapping("/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    SessionResponse createSession(@Valid @RequestBody CreateSessionRequest request,
                                  @Parameter(hidden = true) @RequestHeader("X-User-Id") String userId) {
        return internalGateway.post().uri("/api/v1/sessions").header("X-User-Id", userId)
                .contentType(MediaType.APPLICATION_JSON).body(request).retrieve().body(SessionResponse.class);
    }

    @PostMapping("/sessions/{id}/end")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void endSession(@PathVariable UUID id, @Parameter(hidden = true) @RequestHeader("X-User-Id") String userId) {
        internalGateway.post().uri("/api/v1/sessions/{id}/end", id).header("X-User-Id", userId)
                .retrieve().toBodilessEntity();
    }

    @PostMapping("/sessions/{id}/utterances")
    @ResponseStatus(HttpStatus.CREATED)
    UtteranceResponse addUtterance(@PathVariable UUID id, @Valid @RequestBody UtteranceRequest request,
                                   @Parameter(hidden = true) @RequestHeader("X-User-Id") String userId) {
        return internalGateway.post().uri("/api/v1/sessions/{id}/utterances", id).header("X-User-Id", userId)
                .contentType(MediaType.APPLICATION_JSON).body(request).retrieve().body(UtteranceResponse.class);
    }

    @GetMapping(value = "/sessions/{id}/suggestions", produces = "application/json")
    List<SuggestionResponse> suggestions(@PathVariable UUID id,
                                         @Parameter(hidden = true) @RequestHeader("X-User-Id") String userId) {
        return internalGateway.get().uri("/api/v1/sessions/{id}/suggestions", id).header("X-User-Id", userId)
                .retrieve().body(new ParameterizedTypeReference<>() { });
    }

    @PatchMapping("/suggestions/{id}")
    SuggestionResponse updateSuggestion(@PathVariable UUID id, @Valid @RequestBody SuggestionStatusRequest request,
                                        @Parameter(hidden = true) @RequestHeader("X-User-Id") String userId) {
        return internalGateway.patch().uri("/api/v1/suggestions/{id}", id).header("X-User-Id", userId)
                .contentType(MediaType.APPLICATION_JSON).body(request).retrieve().body(SuggestionResponse.class);
    }

    /**
     * Relays the backend's event stream one event at a time, flushing each as it arrives, so a suggestion
     * reaches the browser the moment the backend sends it. The upstream status is checked before streaming
     * starts, so a 404 for someone else's session is still a proper error response.
     */
    @GetMapping(value = "/sessions/{id}/suggestions/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ApiResponse(responseCode = "200", description = "Server-sent events; one `suggestion` event per new suggestion",
            content = @Content(mediaType = MediaType.TEXT_EVENT_STREAM_VALUE, schema = @Schema(implementation = SuggestionResponse.class)))
    SseEmitter stream(@PathVariable UUID id, @Parameter(hidden = true) @RequestHeader("X-User-Id") String userId) {
        InputStream upstream = internalGateway.get().uri("/api/v1/sessions/{id}/suggestions/stream", id)
                .header("X-User-Id", userId).accept(MediaType.TEXT_EVENT_STREAM)
                .exchange((request, response) -> {
                    if (response.getStatusCode().isError()) {
                        throw new RestClientResponseException("Upstream error", response.getStatusCode(),
                                response.getStatusText(), response.getHeaders(),
                                response.getBody().readAllBytes(), null);
                    }
                    return response.getBody();
                }, false);
        SseEmitter emitter = new SseEmitter(0L);
        Runnable closeUpstream = () -> {
            try {
                upstream.close();
            } catch (IOException ignored) {
                // already closed
            }
        };
        emitter.onCompletion(closeUpstream);
        emitter.onTimeout(closeUpstream);
        emitter.onError(e -> closeUpstream.run());
        Thread.ofVirtual().name("suggestion-relay").start(() -> relay(upstream, emitter));
        return emitter;
    }

    private static void relay(InputStream upstream, SseEmitter emitter) {
        try (var reader = new BufferedReader(new InputStreamReader(upstream, StandardCharsets.UTF_8))) {
            String name = null;
            String eventId = null;
            StringBuilder data = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    if (data.length() > 0) {
                        // The builder writes fields in call order, and data must come last in an event.
                        var event = SseEmitter.event();
                        if (name != null) event.name(name);
                        if (eventId != null) event.id(eventId);
                        emitter.send(event.data(data.toString(), MediaType.TEXT_PLAIN));
                    }
                    name = null;
                    eventId = null;
                    data.setLength(0);
                } else if (line.startsWith(":")) {
                    emitter.send(SseEmitter.event().comment(line.substring(1).strip()));
                } else if (line.startsWith("event:")) {
                    name = line.substring(6).strip();
                } else if (line.startsWith("id:")) {
                    eventId = line.substring(3).strip();
                } else if (line.startsWith("data:")) {
                    if (data.length() > 0) data.append('\n');
                    data.append(line.substring(5).stripLeading());
                }
            }
            emitter.complete();
        } catch (IOException | IllegalStateException e) {
            emitter.complete(); // the browser went away, or the upstream closed
        }
    }
}
