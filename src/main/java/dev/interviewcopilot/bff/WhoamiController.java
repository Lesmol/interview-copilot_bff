package dev.interviewcopilot.bff;

import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

@RestController
@RequestMapping("/api")
class WhoamiController {

    /** Screen-shaped response: the shell only needs the user id. */
    record WhoamiResponse(String userId) {
    }

    record BackendWhoami(String userId, String clientId) {
    }

    private final RestClient internalGateway;

    WhoamiController(RestClient internalGatewayClient) {
        this.internalGateway = internalGatewayClient;
    }

    @GetMapping(value = "/whoami", produces = "application/json")
    WhoamiResponse whoami(@Parameter(hidden = true) @RequestHeader("X-User-Id") String userId) {
        BackendWhoami backend = internalGateway.get().uri("/api/whoami")
                .header("X-User-Id", userId)
                .retrieve().body(BackendWhoami.class);
        return new WhoamiResponse(backend.userId());
    }
}
