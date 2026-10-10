# Approved GitHub ticket creation

Build one opt-in connector for `Dhruv-Sharma29/test`. Java owns the repository allowlist; secrets remain server-side. A final `CREATE_TICKET` action must immediately follow an approval checkpoint. Existing saved local workflows retain their behavior.

Acceptance criteria:

- Freeze the exact repository, title, body and correlation marker in the approval digest. Revalidate the owner, role, settings, context and connector configuration before dispatch.
- Commit one durable intent per node before HTTP; never hold a run lock across HTTP. Competing workers and restart cannot dispatch the same intent again.
- Record confirmed success with an issue number and trusted GitHub URL. Classify authentication/validation rejection as failed, and timeouts, unexpected responses and lost outcomes as unknown. Never automatically retry creation.
- Cancellation before dispatch prevents creation. After dispatch it cannot undo an external effect; retain any late result and expose that distinction.
- Reconcile an unknown result only by reading an explicit issue number in the approved repository and matching the exact title/body/marker. Reconciliation must never create an issue.
- Expose connector readiness, destination settings, exact approval fields, result links and reconciliation in the Console UI. Tests use a fake HTTP server and synthetic approvals; live creation requires explicit review in the app.

Implementation slices: connector policy and bounded HTTP tests; durable execution/approval/recovery integration; web controls and browser tests; configuration, full verification and review.

GitHub's [create-issue contract](https://docs.github.com/en/rest/issues/issues#create-an-issue) requires Issues write permission for a fine-grained token and returns 201 on success. The documented request has no idempotency parameter, so this increment claims at-most-one creation dispatch per intent, not exactly-once remote delivery. Use [get issue](https://docs.github.com/en/rest/issues/issues#get-an-issue) for explicit reconciliation. Redirects are denied to preserve the fixed destination and credential boundary.
