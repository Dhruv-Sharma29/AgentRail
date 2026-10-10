# Phase 5: visual workflow workspace

Implemented acceptance criteria, in implementation order:

1. React/TypeScript/Vite frontend, Tailwind and React Flow. Session login/logout with same-origin development proxy, CSRF refresh after login, no browser credential/token persistence, safe text rendering and explicit loading/error/empty states.
2. Owner-scoped paginated workflow list, create/update/archive and immutable version history. Visual nodes and edges map to strict backend definitions; optional bounded layout positions survive save/reload. Stale save conflicts retain the unsaved draft. Saving never starts execution.
3. Accessible canvas and form controls for trigger, agent, action and approval settings. Support template has receive/classify/search/review/record. The editor reflects current linear execution restrictions and validates obvious invalid graphs; Java remains authoritative. Existing unsupported graphs remain inspectable.
4. Explicit saved-version run start with issue input and one retained idempotency key per unchanged submission; status polling, ordered step attempts/proposals/decisions, audit events, result and cancellation. No automatic start or approval. Agent runs use the server-configured provider and may spend tokens.
5. Owner approval inbox, exact frozen payload/digest/deadline review, explicit approve/reject; stale decisions refresh the request without authorizing changed payloads. Pagination and session expiry cannot leak the previous user's data.
6. Unit tests, production build and Playwright checks of a real deterministic backend: login, editing/reloading, start/pause, approve/reject, cancellation, conflict, literal untrusted content, logout and protected routes. Review, documentation and local startup.

Deployment: frontend dev server at 127.0.0.1:5173 proxies /api and /actuator to 127.0.0.1:8080. Backend authorization/CSRF configuration stays intact. A production build requires a same-origin reverse proxy; production deployment is Phase 6. Editor coordinates are workflow metadata, not execution permissions. No provider keys enter the browser bundle.


Verified: 263 backend tests, 9 frontend tests and 7 real Chromium scenarios; strict production build and formatting checks passed. The uncertain-start browser test deliberately loses a committed response, then verifies one run after explicit retry. Editing is disabled while saving so late responses cannot overwrite additional changes. Desktop/mobile screenshots and the real development history were inspected. See [verification.md](verification.md) for evidence and limitations.

Framework setup follows the official [React Flow guide](https://reactflow.dev/learn), [Tailwind Vite integration](https://tailwindcss.com/docs/installation/using-vite) and [Vite guide](https://vite.dev/guide/). Dependencies are pinned in the lockfile.
