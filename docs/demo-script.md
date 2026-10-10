# Portfolio demo guide

The recorded acceptance demo uses a disposable PostgreSQL deployment, compiled frontend, and synthetic issue. Generate it with `python3 scripts/compose-test.py --build --browser`. It writes `artifacts/agentflow-demo.webm` and `artifacts/agentflow-execution.png`, then removes the test database. The recording is a browser test capture, without narration; IDs shown in it are not routes into your personal database.

For a narrated two-minute demonstration on the running workspace:

1. Open the workspace and sign in. Explain: “AI proposes actions; Java validates, authorizes and executes them.”
2. Create a workflow from **Agent template**. Show the trigger, classification agent, documentation agent, human review and result action. Save its immutable version.
3. Explicitly start it with subject **Cannot log in** and body **Password reset failed; please help restore account access.** Docker uses a deterministic planner, so explain that this is a repeatable tool-policy demonstration. A separately configured live Gemini planner is optional and may incur API costs.
4. Show the execution trace pausing at **WAITING_FOR_APPROVAL**. Open the approval inbox and inspect the frozen payload and digest. Check the review acknowledgement and explicitly approve the synthetic action.
5. Show **SUCCEEDED**, each step's output, the two Java-approved tool calls and the persisted approval/run audit events. Explain that the result is recorded locally; no external ticket was created.
6. Show immutable version history and briefly describe the verified backup/restart behavior. The Java process can be replaced while an approval remains paused; a repeated decision cannot duplicate the result.

For a rejection demo, start another synthetic issue and explicitly reject its proposal. The result action must not execute. Do not reuse a real user's pending request for demonstrations.

Discuss the current limits candidly: one linear chain, owner approvals with one USER role, deterministic Docker default, local classification/documentation tools, no external side-effecting tool, and no public HTTPS hosting. These are deliberate bounds on the first executable portfolio workflow, with integration tests behind them.
