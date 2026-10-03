---
name: api-integration
description: Use for any Tripinly client network work — Ktor client setup, DTOs, repositories, auth header and token refresh, error codes, pagination, fakes while the backend isn't ready, and contract changes made together with the backend.
---

# API integration

Contract: the backend's `/v1/openapi.json` (staging: `https://api-staging-4ade.up.railway.app/v1/openapi.json`) and its copy in `docs/knowledge/03-api-contract.md`. Architecture: `docs/knowledge/07-architecture.md`. Networking lives in `core:network`; each feature's remote calls live in its `data/remote` package.

## Adding a call

1. Find the endpoint in the OpenAPI document. **Not there?** Change the backend first (see "Contract changes" below). Don't invent endpoints or fields on the client.
2. API models are **generated from `openapi.json`** into `core:network` (regenerate after every backend contract change; never hand-edit generated files). They stay in the data layer.
3. Remote data source function (suspend) using the shared `HttpClient`.
4. Repository method mapping DTO → domain model and errors → the shared result type.
5. Fake implementation returning realistic data (same shapes), used when the build flag `USE_FAKE_API` is on, and reused as the test fake.
6. State holder uses the repository only.
7. `commonTest` with Ktor `MockEngine` serving JSON copied from a real staging response (`src/commonTest/resources/`): success, a mapped error code, network failure.

## Foundation (build once)

- Shared `HttpClient` factory: JSON (`ignoreUnknownKeys`), bearer auth from `SecureStore`, `Accept-Language`, base URL per environment, logging in debug with the `Authorization` header redacted.
- **Refresh:** on 401 + `TOKEN_EXPIRED`, a single-flight `POST /auth/refresh`; store both new tokens; retry once. Refresh failure → emit a global "signed out" event → navigate to sign-in and clear local data.
- Global handling: `ONBOARDING_INCOMPLETE` → onboarding, `CONSENT_REQUIRED` → consent screen, `ACCOUNT_SUSPENDED` → message + sign-out, `RATE_LIMITED` → "try again shortly".
- Paging helper for `{ items, nextCursor }`.
- `Idempotency-Key` header on POSTs that create content, once the backend implements it (in the spec, not built yet; add it to the backend first).

## Error codes to handle specifically

`USERNAME_TAKEN`, `USERNAME_INVALID`, `AGE_REQUIREMENT_NOT_MET`, `TRIP_NOT_COPYABLE`, `USER_BLOCKED`, `INVITE_EXPIRED`, `PHOTO_LIMIT_REACHED`, `UPLOAD_TOO_LARGE`, `UNSUPPORTED_MEDIA_TYPE`, `PREMIUM_REQUIRED`, `REAUTH_REQUIRED`, `ROUTING_UNAVAILABLE`, `BBOX_TOO_LARGE`, `NOT_FOUND` (show "not available"), `VALIDATION_FAILED` (map `details` to form fields). Anything else: show the server's `message`.

## Contract changes

The same agent owns both repos, so there are no contract requests to hand over. When the client needs something the API doesn't have (or has in an awkward shape):

1. Change the backend in `shahin68/tripinly-backend` on its own feature branch: endpoint, DTOs, tests, its `04-api-spec.md` and `openapi.json` (backend `add-endpoint` skill). Keep `/v1` backward compatible unless the user approves a breaking change.
2. After it merges and deploys to staging, regenerate the client API models and update `03-api-contract.md`.
3. List every contract change in the stage report, with the backend PR link.

Until the backend change lands, build against a fake behind the repository interface.
