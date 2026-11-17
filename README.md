# interview-copilot_bff

Backend-for-frontend: screen-shaped routes for the web app. Spring Boot 4.1 / Java 25.

**No authentication of its own.** It is reachable only from the edge gateway (network rule) and trusts `X-User-Id`, rejecting requests without it (400) as a safety check. Outbound calls to the internal gateway carry an OAuth2 client-credentials service token (cached until near expiry) plus `X-User-Id`.

The contract is published as `openapi/bff.json` (springdoc). A test fails when the committed file drifts from the code; regenerate with:

```
./mvnw test -Dopenapi.update=true
```

Configuration is environment variables only; see `.env.example`. Licence: Apache-2.0.
