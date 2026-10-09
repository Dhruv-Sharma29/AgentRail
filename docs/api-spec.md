# Foundation API

Base URL: `http://127.0.0.1:8080`. All routes except CSRF, login, and health require a session. All mutations, including login and logout, require a CSRF token. JSON rejects unknown properties.

## Authentication

1. `GET /api/auth/csrf` returns `{token, headerName, parameterName}` and establishes a session. Preserve the session cookie.
2. `POST /api/auth/login` accepts URL-encoded `username`, `password`, and the returned CSRF parameter. Success is 204; incorrect credentials are 401.
3. Fetch a fresh CSRF token after login; authentication rotates it. Send it under the returned header name on mutations.
4. `GET /api/auth/me` returns `{username, roles}`.
5. `POST /api/auth/logout` returns 204 and invalidates the session.

Cookies are HttpOnly, SameSite=Lax, and Secure by default. The loopback-only dev profile and local Compose configuration turn Secure off for local HTTP. Production requires HTTPS and Secure cookies. No browser token storage or CORS policy is needed until the frontend exists; the planned client will use the same origin or a development proxy.

## Workflows

| Method | Route | Behavior |
| --- | --- | --- |
| POST | `/api/workflows` | Create workflow and version 1; 201 with Location and details |
| GET | `/api/workflows?page=0&size=20` | Owner's active workflows; paginated summaries |
| GET | `/api/workflows/{id}` | Owner-only details, including current definition and archive state |
| PUT | `/api/workflows/{id}` | Create next immutable version; requires expectedVersion |
| DELETE | `/api/workflows/{id}` | Idempotently archive; 204; preserves history |
| GET | `/api/workflows/{id}/versions?page=0&size=20` | Owner-only immutable definitions and metadata, oldest first |

Pages return `{items, total, page, size}`. Page must be nonnegative; size is 1–100. Unowned and missing objects both return 404. Archived workflows remain inspectable by their owner and cannot be updated; active listings exclude them. Stale updates return 409. Updates and archive operations lock only the owned workflow row inside a transaction.

Create body:

```json
{
  "name": "Support intake",
  "description": "Triage incoming issues",
  "definition": {
    "nodes": [
      {"id": "intake", "type": "TRIGGER", "label": "Receive issue"},
      {"id": "ticket", "type": "ACTION", "label": "Create ticket"}
    ],
    "edges": [{"source": "intake", "target": "ticket"}]
  }
}
```

An update uses the same body plus `"expectedVersion": 1`. Names are required, limited to 120 characters, and trimmed. Description is required and limited to 2,000 characters. Definitions support 1–50 nodes and at most 100 edges; node IDs match `[a-zA-Z0-9_-]{1,64}` and labels are required, at most 120 characters. Node types: TRIGGER, AI_STEP, CONDITION, APPROVAL, ACTION.

The foundation validates structure only: exactly one trigger, unique IDs, existing edge endpoints, no duplicate/self edges, no cycles, and every node reachable from the trigger. Saving an action or AI node does not execute it. Tool allowlists, argument schemas, condition semantics, approvals, and execution budgets are added in the runtime phases.

Validation errors are 400; conflicting changes are 409. Controller errors return `{status, message, timestamp}`. Authentication/CSRF errors are 401/403 from Spring Security. Errors do not expose stack traces. `GET /actuator/health` exposes health without internal details.
