# AgentFlow development

For structural codebase exploration, use the installed `codebase-memory` skill. Verify graph coverage for every source file relied on; read missed ranges directly.

Build small, working increments. Specify acceptance criteria, write behavior tests before implementation, run relevant checks, and review correctness and security before committing a phase.

Use `create-plan` for explicit read-only planning requests, then `incremental-implementation`, `test-driven-development`, `debugging-and-error-recovery`, `code-review-and-quality`, and `code-simplification` as relevant. A user request to implement takes precedence over the planning skill's read-only mode; keep planning and implementation as distinct steps.

Project skills in `.agents/skills/` cover `java-spring-boot`, `ai-agent-tool-calling`, `workflow-engine-reliability`, and `agent-security-review`. The official `security-best-practices` skill supplies supported frontend guidance; Java security is covered by the project skill.

The central invariant is: **AI proposes actions; Java validates, authorizes, and controls execution.** A model cannot directly invoke a side-effecting tool. Use a deterministic planner until the execution boundary is tested and live model integration is configured.
