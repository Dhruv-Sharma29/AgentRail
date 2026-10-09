---
name: agent-security-review
description: Review AgentFlow Java and AI execution trust boundaries for authorization, prompt injection, unsafe tools, secrets, and approval bypasses.
---

# agent-security-review

Review the diff and reachable paths. Report findings with source locations, an abuse scenario, impact, and a focused fix. Distinguish current controls from planned controls.

- Trace identity through workflow, run, approval, tool, and audit access. Test cross-owner requests and roles, especially resume paths.
- Treat model output as untrusted: enforce tool allowlists, strict schemas, argument bounds, destinations, and server-derived ownership. No arbitrary shell, SQL, URL, or filesystem execution.
- Review immutable approval arguments, approver authorization, durable pause state, cancellation, rejection, duplicate requests, concurrent decisions, and revalidation before effects.
- Check that prompt injection cannot enable tools, impersonate an approver, bypass budgets, or access secrets. Include retrieved documents and tool outputs.
- Check secret storage, password hashing, CSRF and sessions, CORS, error redaction, audit content, and development versus production settings. Never print secrets during review.
- For remote tools, review SSRF restrictions, deadlines, response limits, retry classification, and ambiguous outcomes.
- Back race-sensitive invariants with database constraints or atomic transitions. Audit writes and approval effects must remain consistent.

Use security-best-practices for supported frontend languages when applicable; it does not provide Java-specific guidance. Verify uncertain Spring Security behavior through official documentation. Fix within the authorized task and add regressions for exploitable paths.
