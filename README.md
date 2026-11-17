# interview-copilot_bff

Backend-for-frontend: screen-shaped routes for the web app. Spring Boot 4.1 / Java 25.

**No authentication of its own.** It is reachable only from the edge gateway (network rule) and trusts `X-User-Id`, rejecting requests without it (400) as a safety check. Outbound calls to the internal gateway carry an OAuth2 client-credentials service token (cached until near expiry) plus `X-User-Id`.

The contract is published as `openapi/bff.json` (springdoc). A test fails when the committed file drifts from the code; regenerate with:

```
./mvnw test -Dopenapi.update=true
```

Configuration is environment variables only; see `.env.example`. Licence: Apache-2.0.

## API versioning

Every HTTP API is versioned in the path: `/api/v1/...`. The version in the path is the contract version, separate from the artifact version. Each OpenAPI document carries the same contract version in `info.version` (currently `1.0.0`).

- Compatible changes (new endpoints, new optional fields) keep `/api/v1` and bump the minor or patch of `info.version`.
- A breaking change adds `/api/v2` next to `/api/v1`; both are served until consumers have moved, and the old one is removed in a later release.
- The gateways route only `/api/v1/**`, so a version nobody has deployed yet is a clean 404 at the edge, not a surprise further in.
- Actuator health endpoints are operational and unversioned.
