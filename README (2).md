# Tripinly client agent package

Instructions, knowledge and skills for the agent that continues the Tripinly Kotlin Multiplatform app (Android + iOS).

```
CLAUDE.md                          Agent instructions: role, rules, workflow, build order, skill index
docs/knowledge/
  01-product-brief.md              What Tripinly is (shared with the backend agent)
  02-client-rules.md               Product rules the app must reflect
  03-api-contract.md               Copy of the backend API spec (OpenAPI wins once the backend runs)
  04-realtime-and-push.md          Socket.IO events, push, deep links
  05-maps-places-routing.md        Google Maps for drawing, places/search/routes from the backend
  06-current-state.md              Template the agent fills in by auditing your repo first
  07-architecture.md               Layers, preferred libraries, networking rules
  08-decision-log.md               Decisions so far
  09-open-questions.md             What still needs your answer
.claude/skills/
  repo-audit/  api-integration/  auth-and-onboarding/  map-features/  photos/
  realtime-and-push/  localization/  premium-and-paywall/  release-checklist/
```

## Setup

Copy `CLAUDE.md`, `docs/` and `.claude/` into the root of the KMP repository and commit them. Start the agent with: "Run the repo-audit skill and report back."

## Working with two agents

- The backend agent owns the API. The client agent never changes it; it writes **contract requests** into `docs/contract-requests.md`.
- You carry contract requests to the backend agent, and carry contract changes from the backend report back to the client agent (it updates `03-api-contract.md`).
- Once the backend is deployed, point the client agent at `/v1/openapi.json`; that becomes the source of truth.
