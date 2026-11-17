package dev.interviewcopilot.bff;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import io.swagger.v3.oas.annotations.Parameter;

@RestController
@RequestMapping("/api/v1")
class SpeechTokenController {

    /** Only a short-lived token plus the provider name, never an API key. */
    record SpeechTokenResponse(String provider, String token, int expiresInSeconds) {
    }

    private final RestClient internalGateway;

    SpeechTokenController(RestClient internalGatewayClient) {
        this.internalGateway = internalGatewayClient;
    }

    @GetMapping(value = "/stt-token", produces = "application/json")
    SpeechTokenResponse token(@RequestParam(defaultValue = "deepgram") String provider,
                              @Parameter(hidden = true) @RequestHeader("X-User-Id") String userId) {
        return internalGateway.get()
                .uri(uri -> uri.path("/api/v1/stt-token").queryParam("provider", provider).build())
                .header("X-User-Id", userId)
                .retrieve().body(SpeechTokenResponse.class);
    }
}
