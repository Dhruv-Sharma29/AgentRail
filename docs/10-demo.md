# 10 — Demo and recording

## Run a local demonstration

Start the app using [getting started](01-getting-started.md). Use the deterministic planner and a synthetic support issue for a repeatable demonstration without provider charges or external writes.

1. Sign in and create a workflow from **Agent template**. Show the trigger, classification/search agents, review checkpoint and result action.
2. Save the workflow version, then run it with subject **Cannot log in** and body **Password reset failed; please help restore account access.**
3. Open the execution trace and inspect the Java-validated proposals. At `WAITING_FOR_APPROVAL`, review the frozen action and explicitly approve or reject it.
4. For approval, follow the run to `SUCCEEDED` and inspect the local result and ordered audit events. For rejection, verify the result action did not execute.
5. Open **Operations** to inspect owner-scoped outcomes and pending work, then show the workflow's immutable version history.

The planner selects local classification and documentation tools. This demonstration records a local result; it does not create an external ticket. Use a synthetic request you created for the demonstration, not another person's pending approval.

A live-provider or GitHub segment requires [explicit configuration](05-configuration.md). GitHub creation requires review of the exact repository, title and body and creates a real issue. Prefer showing a previously completed receipt when recording repeated takes.

## README recording

The README links to [the app walkthrough](media/agentflow-walkthrough.mp4), with [an animated preview](media/agentflow-walkthrough.gif). This is a silent, 30-second, 1080p recording of an existing completed run. It predates the AgentRail rename and shows AgentFlow in the interface.

The recording script signs in before capture, keeps session state in memory and blocks mutation requests from the capture browser. It reviews the builder, execution trace, accepted proposal, saved approval and audit trail without creating another run or issue.

To re-record, start the development backend on port 8080 and frontend on 5173. Install frontend dependencies, Playwright Chromium and FFmpeg/ffprobe. Update the explicit workflow/run/approval IDs in `scripts/record-readme-demo.mjs` to reference an existing completed demonstration in your local database. From the repository root:

```sh
node scripts/record-readme-demo.mjs
python3 scripts/export-readme-demo.py
```

The script uses the public development account. Raw capture, scene timings, screenshots, contact sheet and media probe are written to ignored `artifacts/readme-demo/`. The exporter produces the MP4, GIF and poster in `docs/media/`, trims navigation loading and preserves the browser aspect ratio with charcoal margins. Inspect the full video and contact sheet before replacing the README demo.

## Container acceptance recording

With Docker running and frontend dependencies/Playwright installed:

```sh
python3 scripts/compose-test.py --build --browser
```

This uses a disposable PostgreSQL stack and synthetic fixture. A successful run writes `artifacts/agentflow-demo.webm` and `artifacts/agentflow-execution.png`, then removes its test database. These are test captures, separate from the README walkthrough. See [testing](08-testing.md) for the complete prerequisites and verification scope.
