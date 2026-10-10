import { useState } from "react";
import {
  ArrowLeft,
  RefreshCw,
  ShieldCheck,
  Square,
  Clock,
  Check,
  Zap,
} from "lucide-react";
import { api, message } from "./api";
import { useResource } from "./useResource";
import { terminal, type Run } from "./types";
import { Badge, date, ErrorNotice, JsonView } from "./ui";
import { TicketResult } from "./TicketResult";
export function RunView({
  id,
  navigate,
}: {
  id: string;
  navigate: (path: string) => void;
}) {
  const resource = useResource(
    (signal) => api.request<Run>(`/api/runs/${id}`, "GET", undefined, signal),
    `run:${id}`,
    1500,
    (run) =>
      terminal(run.status) &&
      !run.tickets?.some((ticket) => ticket.status === "SENDING"),
  );
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const run = resource.data;
  async function cancel() {
    setBusy(true);
    setError("");
    try {
      await api.request(`/api/runs/${id}/cancel`, "POST");
      resource.reload();
    } catch (failure) {
      setError(message(failure));
      resource.reload();
    } finally {
      setBusy(false);
    }
  }
  if (!run)
    return (
      <>
        <ErrorNotice>{resource.error}</ErrorNotice>
        <p className="empty">
          {resource.loading ? "Loading execution…" : "Execution unavailable."}
        </p>
        <button onClick={resource.reload}>Retry</button>
      </>
    );
  const approval = run.approvals.find((a) => a.status === "PENDING");
  const issue = run.input as { subject?: string } | null;
  return (
    <>
      <div className="page-heading">
        <div>
          <button
            className="back-link"
            onClick={() => navigate("/workflows/" + run.workflowId)}
          >
            <ArrowLeft size={14} />
            Back to workflow
          </button>
          <h1>Execution trace</h1>
          <p>
            {issue?.subject ?? "Workflow execution"}{" "}
            <span className="slash">/</span> Version {run.versionNumber}
          </p>
        </div>
        <div className="run-actions">
          <button onClick={resource.reload} aria-label="Refresh execution">
            <RefreshCw size={16} />
          </button>
          {!terminal(run.status) && (
            <button className="danger" onClick={cancel} disabled={busy}>
              <Square size={13} />
              Cancel run
            </button>
          )}
        </div>
      </div>
      <ErrorNotice>{error || resource.error}</ErrorNotice>
      <div className="run-summary">
        <div>
          <span className="eyebrow">RUN STATUS</span>
          <div data-testid="run-status">
            <Badge value={run.status} />
          </div>
        </div>
        <div>
          <span>Planner</span>
          <strong>{run.mode.replaceAll("_", " ")}</strong>
        </div>
        <div>
          <span>Tool calls</span>
          <strong>
            {run.toolCalls} <small>approved calls</small>
          </strong>
        </div>
        <div>
          <span>Reported tokens</span>
          <strong>
            {run.plannerTokens.toLocaleString()}{" "}
            <small>{run.reservedPlannerTokens.toLocaleString()} reserved</small>
          </strong>
        </div>
      </div>
      {approval && (
        <div className="approval-banner">
          <ShieldCheck size={23} />
          <div>
            <h2>Waiting for your review</h2>
            <p>
              The next action is paused. Inspect its exact payload before
              deciding.
            </p>
          </div>
          <button
            className="primary"
            onClick={() => navigate("/approvals/" + approval.id)}
          >
            Review approval
            <ArrowLeft className="reverse" size={15} />
          </button>
        </div>
      )}
      {run.errorCode && <ErrorNotice>{run.errorCode}</ErrorNotice>}
      <div className="execution-layout">
        <section className="execution-steps">
          <div className="section-heading">
            <h2>Execution path</h2>
            <small>{run.steps.length} steps</small>
          </div>
          {run.steps.map((step) => (
            <article
              className={`trace-step ${step.status.toLowerCase()}`}
              key={step.nodeId}
            >
              <div className="step-number">
                {step.status === "SUCCEEDED" ? (
                  <Check size={15} />
                ) : (
                  step.ordinal + 1
                )}
              </div>
              <div className="step-body">
                <div>
                  <h3>{step.nodeId}</h3>
                  <Badge value={step.status} />
                </div>
                <p>
                  {step.operation?.replaceAll("_", " ").toLowerCase() ??
                    (step.plannerId ? "Agent step" : "Human review")}{" "}
                  · {step.attempts} attempt{step.attempts === 1 ? "" : "s"}
                </p>
                {step.plannerId && (
                  <div className="planner-line">
                    <Zap size={12} />
                    {step.plannerId}
                    <span>
                      {step.policyStatus} · {step.decisionCode}
                    </span>
                  </div>
                )}
                {step.nextAttemptAt && (
                  <p className="retry">
                    <Clock size={12} />
                    Next attempt {date(step.nextAttemptAt)}
                  </p>
                )}
                {step.errorCode && (
                  <p className="step-error">{step.errorCode}</p>
                )}
                {step.proposal != null && (
                  <JsonView value={step.proposal} label="Validated proposal" />
                )}
                {step.output != null && (
                  <JsonView value={step.output} label="Step output" />
                )}
              </div>
            </article>
          ))}
        </section>
        <aside className="execution-info">
          {run.tickets?.map((ticket) => (
            <TicketResult
              key={ticket.nodeId}
              runId={run.id}
              ticket={ticket}
              cancelled={run.status === "CANCELLED"}
              reload={resource.reload}
            />
          ))}
          <div className="info-card">
            <span className="eyebrow">EXECUTION DETAILS</span>
            <dl>
              <dt>Run ID</dt>
              <dd>
                <code>{run.id}</code>
              </dd>
              <dt>Created</dt>
              <dd>{date(run.createdAt)}</dd>
              <dt>Started</dt>
              <dd>{date(run.startedAt)}</dd>
              <dt>Finished</dt>
              <dd>{date(run.finishedAt)}</dd>
            </dl>
            <JsonView value={run.input} label="Submitted input" />
            <JsonView value={run.context} label="Current checkpoint & result" />
          </div>
          <div className="info-card">
            <h2>Audit trail</h2>
            <p className="hint">Persisted events in execution order.</p>
            <ol className="audit-events">
              {run.events.map((event) => (
                <li key={event.sequence}>
                  <span className="event-dot" />
                  <div>
                    <strong>{event.eventType}</strong>
                    <small>
                      {event.nodeId ?? "Workflow"} · {date(event.occurredAt)}
                    </small>
                    {event.toolName && <code>{event.toolName}</code>}
                  </div>
                </li>
              ))}
            </ol>
          </div>
        </aside>
      </div>
    </>
  );
}
