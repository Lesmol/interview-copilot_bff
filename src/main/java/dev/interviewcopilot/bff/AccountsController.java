package dev.interviewcopilot.bff;

import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/** Sign-up and password reset: no signed-in user yet, so no {@code X-User-Id}. Errors are relayed as the backend sent them. */
@RestController
@RequestMapping("/api/v1/accounts")
class AccountsController {

    record SignUpRequest(@NotBlank @Email @Size(max = 254) String email,
                         @NotBlank @Size(max = 100) String name,
                         @NotBlank @Size(min = 10, max = 128) String password) {
    }

    record PasswordResetRequest(@NotBlank @Email @Size(max = 254) String email) {
    }

    record PasswordResetConfirmation(@NotBlank @Size(max = 200) String token,
                                     @NotBlank @Size(min = 10, max = 128) String newPassword) {
    }

    private final RestClient internalGateway;

    AccountsController(RestClient internalGatewayClient) {
        this.internalGateway = internalGatewayClient;
    }

    @PostMapping("/sign-up")
    @ApiResponse(responseCode = "201", description = "Account created")
    ResponseEntity<byte[]> signUp(@Valid @RequestBody SignUpRequest request) {
        return relay("/api/v1/accounts/sign-up", request);
    }

    @PostMapping("/password-reset/request")
    @ApiResponse(responseCode = "202", description = "Accepted; the response never reveals whether the email has an account")
    ResponseEntity<byte[]> requestReset(@Valid @RequestBody PasswordResetRequest request) {
        return relay("/api/v1/accounts/password-reset/request", request);
    }

    @PostMapping("/password-reset/confirm")
    @ApiResponse(responseCode = "204", description = "Password changed")
    ResponseEntity<byte[]> confirmReset(@Valid @RequestBody PasswordResetConfirmation request) {
        return relay("/api/v1/accounts/password-reset/confirm", request);
    }

    private ResponseEntity<byte[]> relay(String path, Object body) {
        try {
            var response = internalGateway.post().uri(path).contentType(MediaType.APPLICATION_JSON)
                    .body(body).retrieve().toBodilessEntity();
            return ResponseEntity.status(response.getStatusCode()).build();
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().is5xxServerError()) {
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
            }
            return ResponseEntity.status(e.getStatusCode()).contentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .body(e.getResponseBodyAsByteArray());
        }
    }
}
