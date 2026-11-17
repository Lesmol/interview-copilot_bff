# Decisions

- **Spring Security as a library only** (`spring-security-oauth2-client`, no Boot security starter), so there is no inbound filter chain at all; the client registration is built from properties. The authorized-client service caches the service token until near expiry.
- **Screen-shaped `whoami`:** the BFF returns only `{ userId }`; the backend's `clientId` is not exposed to the browser.
- `X-User-Id` is hidden from the published OpenAPI spec: the edge gateway sets it, the frontend never sends it.
