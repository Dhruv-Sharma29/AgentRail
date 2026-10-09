# Skill setup

Verified 2026-10-09. The repository initially contained no code. No large third-party pack was downloaded.

| Skill | Scope | Source |
| --- | --- | --- |
| create-plan | Global Codex | openai/skills, historical commit `2a1cce6e760886d6cbd6d008708b60c1325d59cb`, `skills/.experimental/create-plan` |
| incremental-implementation | Global Codex | Existing Antigravity `agent-skills` installation |
| test-driven-development | Global Codex | Existing Antigravity `agent-skills` installation |
| debugging-and-error-recovery | Global Codex | Existing Antigravity `agent-skills` installation |
| code-review-and-quality | Global Codex | Existing Antigravity `agent-skills` installation |
| code-simplification | Global Codex | Existing Antigravity `agent-skills` installation |
| security-best-practices | Global Codex | Current official openai/skills curated catalog |
| java-spring-boot | Project `.agents/skills` | Created for AgentFlow |
| ai-agent-tool-calling | Project `.agents/skills` | Created for AgentFlow |
| workflow-engine-reliability | Project `.agents/skills` | Created for AgentFlow |
| agent-security-review | Project `.agents/skills` | Created for AgentFlow |

OpenAI removed the experimental create-plan path in commit `ea6b206c683087da5b503f5ac9d7202b326ac6bb`. It was installed through the official installer at the verified older revision; this is historical code, not a current catalog listing.

Reused skills have their referenced checklists bundled inside each skill folder. Reference paths were repaired for standalone installation. Antigravity-specific browser tool wording now uses available Codex browser tools or repository Playwright tests; project conventions refer to AGENTS.md. Original Antigravity files were not changed.

The official security-best-practices skill supports Python, JavaScript/TypeScript, and Go. AgentFlow's Java/agent review is covered by the project-specific agent-security-review skill. The existing codebase-memory skill remains at `~/.claude/skills/codebase-memory/SKILL.md` and is required for structural exploration.

All 11 skills passed the bundled skill-creator validator and reference-file checks. Newly installed skills become discoverable on the next turn; refresh/restart Codex if the catalog does not update.

## Verified curated installation names

The official installer returned these current curated entries. The development workflow names above are reused local skills rather than claimed official entries:

```text
aspnet-core, chatgpt-apps, cli-creator, cloudflare-deploy, define-goal,
figma, figma-code-connect-components, figma-create-design-system-rules,
figma-create-new-file, figma-generate-design, figma-generate-library,
figma-implement-design, figma-use, gh-address-comments, gh-fix-ci,
hatch-pet, jupyter-notebook, linear, migrate-to-codex, netlify-deploy,
notion-knowledge-capture, notion-meeting-intelligence,
notion-research-documentation, notion-spec-to-implementation,
openai-docs, pdf, playwright, playwright-interactive, render-deploy,
screenshot, security-best-practices, security-ownership-map,
security-threat-model, sentry, speech, transcribe, vercel-deploy,
winui-app, yeet
```
