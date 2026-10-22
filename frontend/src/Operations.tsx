import { RefreshCw } from "lucide-react";
import { api } from "./api";
import type { OperationsSnapshot } from "./types";
import { useResource } from "./useResource";
import { date, ErrorNotice } from "./ui";

export function Operations({ navigate }: { navigate: (path: string) => void }) {
  const resource = useResource(
    (signal) =>
      api.request<OperationsSnapshot>(
        "/api/operations",
        "GET",
        undefined,
        signal,
      ),
    "operations",
    15000,
  );
  const data = resource.data;
  return (
    <>
      <div className="page-heading">
        <div>
          <h1>Operations</h1>
          <p>Your execution queues and outcomes. Updated every 15 seconds.</p>
        </div>
        <button
          onClick={resource.reload}
          aria-label="Refresh operations"
          disabled={resource.loading}
        >
          <RefreshCw size={15} /> Refresh
        </button>
      </div>
      <ErrorNotice>{resource.error}</ErrorNotice>
      {resource.loading && <p role="status">Loading operations…</p>}
      {data && (
        <>
          <p className="hint operations-updated">
            {resource.error
              ? "Counts are stale. Last successful update: "
              : "Last updated: "}
            {date(data.capturedAt)}
          </p>
          <div className="run-summary operations-summary">
            <div>
              <span>ACTIVE RUNS</span>
              <strong>
                {data.pendingRuns + data.runningRuns}
                <small>Queued or running</small>
              </strong>
            </div>
            <div>
              <span>FAILED RUNS</span>
              <strong data-testid="operations-failed">
                {data.failedRuns}
                <small>Across retained history</small>
              </strong>
            </div>
            <div>
              <span>RETRY QUEUE</span>
              <strong data-testid="operations-retries">
                {data.retryingSteps}
                <small>Steps with a scheduled retry</small>
              </strong>
            </div>
            <div>
              <span>AWAITING REVIEW</span>
              <strong>
                {data.pendingApprovals}
                <small>Pending owner decisions</small>
              </strong>
            </div>
          </div>
          <div className="execution-layout">
            <section className="execution-steps">
              <h2>Run history</h2>
              {!data.totalRuns && <p className="hint">No runs yet</p>}
              <dl className="operations-list">
                {(
                  [
                    ["Total", data.totalRuns],
                    ["Queued", data.pendingRuns],
                    ["Running", data.runningRuns],
                    ["Waiting for approval", data.waitingRuns],
                    ["Succeeded", data.succeededRuns],
                    ["Failed", data.failedRuns],
                    ["Cancelled", data.cancelledRuns],
                  ] as const
                ).map(([label, count]) => (
                  <div key={label}>
                    <dt>{label}</dt>
                    <dd>{count}</dd>
                  </div>
                ))}
              </dl>
              <button onClick={() => navigate("/workflows")}>
                Open workflows
              </button>
            </section>
            <section className="execution-steps operations-attention">
              <h2>Needs attention</h2>
              <p>
                {data.pendingApprovals}{" "}
                {data.pendingApprovals === 1
                  ? "approval needs"
                  : "approvals need"}{" "}
                review
              </p>
              <p>
                {data.overdueApprovals}{" "}
                {data.overdueApprovals === 1
                  ? "approval past its deadline"
                  : "approvals past their deadlines"}
              </p>
              <button onClick={() => navigate("/approvals")}>
                Open approvals
              </button>
              <hr />
              <p>
                {data.unknownTickets}{" "}
                {data.unknownTickets === 1
                  ? "ticket outcome needs"
                  : "ticket outcomes need"}{" "}
                reconciliation
              </p>
              <p className="hint">
                Inspect the original execution trace and confirm the existing
                GitHub issue. Starting another run may create a duplicate.
              </p>
              <p>
                {data.sendingTickets}{" "}
                {data.sendingTickets === 1
                  ? "ticket dispatch is"
                  : "ticket dispatches are"}{" "}
                in progress
              </p>
              <p className="hint">
                In-flight or uncertain ticket outcomes can remain after
                cancellation.
              </p>
            </section>
          </div>
        </>
      )}
    </>
  );
}
