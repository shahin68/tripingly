---
name: api-integration
description: Use for any Tripinly client network work — Ktor client setup, DTOs, repositories, auth header and token refresh, error codes, pagination, fakes while the backend isn't ready, and writing contract requests for the backend agent.
---

# API integration

Contract: `docs/knowledge/03-api-contract.md` (and the backend's `/v1/openapi.json` once it runs). Architecture: `docs/knowledge/07-architecture.md`.

## Adding a call

1. Find the endpoint in the contract. **Not there?** Stop and write a contract request (below). Don't invent endpoints or fields.
2. DTOs in shared code (`@Serializable`), named after the API, fields exactly as in the contract. Nullable where the contract allows `null` or omits fields.
3. Remote data source function (suspend) using the shared `HttpClient`.
4. Repository method mapping DTO → domain model and errors → the shared result type.
5. Fake implementation returning realistic data (same shapes), used when the build flag `USE_FAKE_API` is on.
6. State holder uses the repository only.
7. `commonTest` with Ktor `MockEngine`: success, a mapped error code, network failure.

## Foundation (build once)

- Shared `HttpClient` factory: JSON (`ignoreUnknownKeys`), bearer auth from `SecureStore`, `Accept-Language`, base URL per environment, logging in debug with the `Authorization` header redacted.
- **Refresh:** on 401 + `TOKEN_EXPIRED`, a single-flight `POST /auth/refresh`; store both new tokens; retry once. Refresh failure → emit a global "signed out" event → navigate to sign-in and clear local data.
- Global handling: `ONBOARDING_INCOMPLETE` → onboarding, `CONSENT_REQUIRED` → consent screen, `ACCOUNT_SUSPENDED` → message + sign-out, `RATE_LIMITED` → "try again shortly".
- Paging helper for `{ items, nextCursor }`.
- `Idempotency-Key` header on POSTs that create content.

## Error codes to handle specifically

`USERNAME_TAKEN`, `USERNAME_INVALID`, `AGE_REQUIREMENT_NOT_MET`, `TRIP_NOT_COPYABLE`, `USER_BLOCKED`, `INVITE_EXPIRED`, `PHOTO_LIMIT_REACHED`, `UPLOAD_TOO_LARGE`, `UNSUPPORTED_MEDIA_TYPE`, `PREMIUM_REQUIRED`, `REAUTH_REQUIRED`, `ROUTING_UNAVAILABLE`, `BBOX_TOO_LARGE`, `NOT_FOUND` (show "not available"), `VALIDATION_FAILED` (map `details` to form fields). Anything else: show the server's `message`.

## Contract requests

When the client needs something the API doesn't have (or has in an awkward shape), add it to `docs/contract-requests.md` and tell the user in your report:

```markdown
### CR-003: Example — Trip list needs weather info
- Screen: Trip screen
- Need: forecast per trip day in `GET /trips/{id}`
- Proposed: `days[].weather: { icon, tempC } | null`
- Workaround until then: hide the weather row
```

The user passes these to the backend agent. When the backend confirms, update `03-api-contract.md`.
