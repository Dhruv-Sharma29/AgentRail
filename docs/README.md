# AgentRail — Documentation

A workflow engine for AI agents. A **visual editor** saves immutable workflow versions, a
**planner** (deterministic, Gemini or OpenAI) proposes tool calls, a **Java policy layer** accepts
or denies each proposal, a **human approval** binds the exact action before anything leaves the
system, and an **audit trail** records every step.

These docs describe the implemented system. Read them in order the first time and use them as
reference afterwards.

## Reading order

| # | Doc | What it settles |
|---|---|---|
| 00 | [Product specification](00-specification.md) | What is built, the data model, acceptance scenarios, non-goals |
| 01 | [Getting started](01-getting-started.md) | Run it locally with H2 or with Docker and PostgreSQL |
| 02 | [User guide](02-user-guide.md) | Build a workflow, run it, review an approval, create a GitHub issue |
| 03 | [Architecture](03-architecture.md) | Modules, run lifecycle, transactions, the planner and GitHub boundaries |
| 04 | [API reference](04-api-reference.md) | Every route, request body, limit and error code |
| 05 | [Configuration](05-configuration.md) | Profiles, environment variables, Gemini/OpenAI, the GitHub connector |
| 06 | [Security model](06-security.md) | Trust boundaries, prompt injection, open dependency findings |
| 07 | [Operations](07-operations.md) | Compose lifecycle, monitoring, backup and restore |
| 08 | [Testing](08-testing.md) | Which checks to run for which change, and what CI covers |
| 09 | [Troubleshooting](09-troubleshooting.md) | Symptoms, error codes and the next action for each |
| 10 | [Demo and recording](10-demo.md) | A repeatable demo script and how the README video is made |

Contributing guidelines are in [`CONTRIBUTING.md`](../CONTRIBUTING.md).

## The one-paragraph version

Most agent demos let the model call tools directly and hope the prompt holds. AgentRail is built
around the opposite assumption — **model output is untrusted input**. A planner can only *propose*
calls to registered tools; Java checks the whole batch against the workflow's allowlist, the
caller's role and the original request before any handler runs. Anything with an external effect
waits for a human decision bound to a digest of the exact payload, and a lost response from an
external service is recorded as **unknown** rather than retried, because a blind resend can
duplicate the effect.

## Examples

The JSON files are workflow-creation request bodies. Saving a definition does not run it; see the
[API reference](04-api-reference.md) for session/CSRF handling and the separate start request.

| Example | Behavior |
|---|---|
| [Support intake](../examples/support-intake.json) | Local actions, local result |
| [Agent support intake](../examples/agent-support-intake.json) | Planner proposes two registered local tools |
| [Approval checkpoint](../examples/approval-support-intake.json) | Human decision before recording a local result |
| [Agent with approval](../examples/agent-approval-support-intake.json) | Planner steps followed by human review |
| [Approved GitHub ticket](../examples/agent-github-ticket.json) | Opt-in external issue creation after exact approval |

## Conventions used in these docs

- Commands name their working directory and assume a macOS/Linux shell unless stated otherwise.
- Local URLs use `127.0.0.1` throughout, so session cookies stay on one host.
- Provider and GitHub credentials are server-side configuration. No example needs a live model or
  makes an external write unless it says so.
- The API reference describes the implemented contract; configuration, migrations and tests remain
  the executable source of truth. Update the relevant doc whenever a command, limit, endpoint,
  persisted state or deployment assumption changes.
