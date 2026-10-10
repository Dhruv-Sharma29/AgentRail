# Live planner acceptance criteria

The live planner selects existing read-only tools. Classification still uses the local tool's rules; this slice does not implement model-generated classification or external tickets.

1. Deterministic mode remains the default. OpenAI or Gemini mode is an explicit server setting, frozen with the model identity on each agent run. A provider mismatch cannot redirect a saved run to another endpoint.
2. A short transaction persists a unique planning intent, frozen request, deadline and reserved usage before dispatch. The network request runs outside database transactions and locks.
3. A second transaction accepts only the matching, unresolved intent. It checks cancellation, deadlines, current owner role, complete proposal policy and budgets before local tool execution. Duplicate/late results cannot execute again.
4. Calls have bounded input, completion tokens, response size and HTTP timeout. Automatic provider/tool retries are disabled. Reported token usage is persisted; reservations remain consumed for unknown remote outcomes.
5. Crash recovery never resends an unresolved request. A persisted intent expires to a safe terminal failure; committed results/checkpoints resume normally.
6. Offline tests cover locks, races, role revocation, invalid responses, provider failures, budgets and recovery on H2 and PostgreSQL. Live smoke verification is explicit and uses synthetic issue data.

Run transitions remain PENDING → RUNNING → SUCCEEDED/FAILED/CANCELLED. An agent node stays PENDING while its planning state changes from null → REQUESTED → RESOLVED, or ABANDONED on cancellation. Failed planning resolves to DENIED with a safe code. Raw rejected responses and exception text are not stored.

Provider request delivery is at most one application dispatch per intent, with no automatic replay after uncertainty. This cannot guarantee provider billing or exactly-once remote execution. Rolling back a result transaction retains the pending intent; recovery fails it after expiration rather than spending another call.
