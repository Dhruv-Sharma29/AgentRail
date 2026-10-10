import { useState } from "react";
import {
  ShieldCheck,
  ArrowUpRight,
  RefreshCw,
  Clock,
  Check,
  X,
} from "lucide-react";
import { api, message } from "./api";
import { useResource } from "./useResource";
import type { Page, Approval } from "./types";
import { Badge, date, ErrorNotice, Pager } from "./ui";
export function Approvals({
  id,
  navigate,
}: {
  id?: string;
  navigate: (path: string) => void;
}) {
  const [page, setPage] = useState(0);
  const inbox = useResource(
    (signal) =>
      api.request<Page<Approval>>(
        `/api/approvals?page=${page}&size=12`,
        "GET",
        undefined,
        signal,
      ),
    `approvals:${page}`,
    3000,
  );
  return (
    <>
      <div className="page-heading">
        <div>
          <span className="eyebrow">A HUMAN IN THE LOOP</span>
          <h1>Approval inbox</h1>
          <p>
            Good automation knows when to pause. The next action is your call.
          </p>
        </div>
        <button onClick={inbox.reload}>
          <RefreshCw size={15} />
          Refresh inbox
        </button>
      </div>
      <ErrorNotice>{inbox.error}</ErrorNotice>
      <div className="approval-layout">
        <div>
          <div className="section-heading">
            <h2>
              Awaiting review <span>{inbox.data?.total ?? "—"}</span>
            </h2>
          </div>
          {inbox.data?.items.map((approval) => (
            <button
              key={approval.id}
              className={`approval-card ${id === approval.id ? "selected" : ""}`}
              onClick={() => navigate("/approvals/" + approval.id)}
            >
              <span className="flow-icon">
                <ShieldCheck size={20} />
              </span>
              <div>
                <strong>{approval.targetNodeId}</strong>
                <small>
                  Run {approval.runId.slice(0, 8)} · {date(approval.createdAt)}
                </small>
                <span>
                  <Clock size={11} />
                  Expires {date(approval.expiresAt)}
                </span>
              </div>
              <ArrowUpRight size={16} />
            </button>
          ))}
          {inbox.loading && !inbox.data ? (
            <p className="empty">Loading approvals…</p>
          ) : (
            inbox.data?.items.length === 0 && (
              <div className="empty">
                <ShieldCheck size={32} />
                <h3>Nothing waiting on you.</h3>
                <p>Actions that need your review will appear here.</p>
              </div>
            )
          )}
          <Pager page={page} total={inbox.data?.total ?? 0} change={setPage} />
        </div>
        {id ? (
          <ApprovalReview
            key={id}
            id={id}
            navigate={navigate}
            resolved={inbox.reload}
          />
        ) : (
          <div className="review-placeholder">
            <ShieldCheck size={30} />
            <h2>A deliberate moment.</h2>
            <p>
              Select a request to inspect the exact action, its inputs and its
              deadline before you decide.
            </p>
          </div>
        )}
      </div>
    </>
  );
}
function ApprovalReview({
  id,
  navigate,
  resolved,
}: {
  id: string;
  navigate: (path: string) => void;
  resolved: () => void;
}) {
  const resource = useResource(
    (signal) =>
      api.request<Approval>(`/api/approvals/${id}`, "GET", undefined, signal),
    `approval:${id}`,
    3000,
  );
  const [reviewedKey, setReviewedKey] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const approval = resource.data;
  const pending = approval?.status === "PENDING";
  const expired =
    approval && Date.now() >= new Date(approval.expiresAt).getTime();
  async function decide(decision: "APPROVE" | "REJECT") {
    if (!approval || busy) return;
    setBusy(true);
    setError("");
    try {
      await api.request(`/api/approvals/${id}/decision`, "POST", {
        decision,
        payloadDigest: approval.payloadDigest,
      });
      setReviewedKey("");
      resource.reload();
      resolved();
    } catch (failure) {
      setError(
        message(failure) +
          " Review the refreshed request before deciding again.",
      );
      setReviewedKey("");
      resource.reload();
      resolved();
    } finally {
      setBusy(false);
    }
  }
  if (!approval)
    return (
      <section className="review-panel">
        <ErrorNotice>{resource.error}</ErrorNotice>
        <p>
          {resource.loading
            ? "Loading the frozen action…"
            : "The request is unavailable."}
        </p>
        <button onClick={resource.reload}>Retry</button>
      </section>
    );
  const payload = approval.payload as {
    operation?: string;
    input?: { subject?: string };
    targetNodeId?: string;
  } | null;
  return (
    <section className="review-panel">
      <div className="review-heading">
        <span className="flow-icon">
          <ShieldCheck size={22} />
        </span>
        <Badge value={approval.status} />
      </div>
      <span className="eyebrow">EXACT ACTION REVIEW</span>
      <h2>{payload?.input?.subject ?? "Proposed action"}</h2>
      <div className="review-meta">
        <div>
          <span>Operation</span>
          <strong>{payload?.operation ?? approval.targetNodeId}</strong>
        </div>
        <div>
          <span>Deadline</span>
          <strong>{date(approval.expiresAt)}</strong>
        </div>
        <div>
          <span>Required role</span>
          <strong>{approval.requiredRole}</strong>
        </div>
      </div>
      <p className="hint">
        This is the frozen action. A decision applies only to the payload below.
      </p>
      <pre className="payload" aria-label="Frozen action payload">
        {JSON.stringify(approval.payload, null, 2)}
      </pre>
      <details className="digest">
        <summary>Payload digest & request identity</summary>
        <code>{approval.payloadDigest}</code>
        <small>Request {approval.id}</small>
      </details>
      <ErrorNotice>{error || resource.error}</ErrorNotice>
      {pending && !expired && (
        <>
          <label className="check review-check">
            <input
              type="checkbox"
              checked={reviewedKey === id + ":" + approval.payloadDigest}
              onChange={(event) =>
                setReviewedKey(
                  event.target.checked ? id + ":" + approval.payloadDigest : "",
                )
              }
            />
            I have reviewed this exact action
          </label>
          <div className="decision-buttons">
            <button
              className="danger"
              onClick={() => decide("REJECT")}
              disabled={busy}
            >
              <X size={15} />
              Reject action
            </button>
            <button
              className="primary"
              onClick={() => decide("APPROVE")}
              disabled={
                busy || reviewedKey !== id + ":" + approval.payloadDigest
              }
            >
              <Check size={15} />
              Approve action
            </button>
          </div>
        </>
      )}
      {expired && pending && (
        <p className="policy-note">
          The review deadline passed. Refresh to see the recorded outcome.
        </p>
      )}
      {!pending && (
        <p className="policy-note">
          Decision recorded: {approval.status.toLowerCase()}
          {approval.decidedAt ? ` · ${date(approval.decidedAt)}` : ""}.
        </p>
      )}
      <button
        className="trace-link"
        onClick={() => navigate("/runs/" + approval.runId)}
      >
        View execution
        <ArrowUpRight size={15} />
      </button>
    </section>
  );
}
