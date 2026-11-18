package dev.interviewcopilot.bff;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientResponseException;

/** Client errors from the backend (400, 404, ...) reach the browser as the backend wrote them; server errors become 502. */
@RestControllerAdvice
class UpstreamErrors {

    @ExceptionHandler(RestClientResponseException.class)
    ResponseEntity<byte[]> upstream(RestClientResponseException e) {
        if (e.getStatusCode().is5xxServerError()) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
        return ResponseEntity.status(e.getStatusCode()).contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(e.getResponseBodyAsByteArray());
    }
}
