import { useState, type FormEvent } from "react";
import { ExternalLink } from "lucide-react";
import { api, message } from "./api";
import type { Ticket } from "./types";
import { Badge, ErrorNotice } from "./ui";
export function TicketResult({
  runId,
  ticket,
  cancelled,
  reload,
}: {
  runId: string;
  ticket: Ticket;
  cancelled: boolean;
  reload: () => void;
}) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const trustedUrl =
    ticket.issueNumber != null &&
    ticket.issueNumber > 0 &&
    ticket.issueUrl?.match(
      /^https:\/\/github\.com\/[a-zA-Z0-9][a-zA-Z0-9-]{0,38}\/[a-zA-Z0-9][a-zA-Z0-9_.-]{0,99}\/issues\/[1-9][0-9]*$/,
    ) &&
    ticket.issueUrl.endsWith("/" + ticket.issueNumber);
  async function reconcile(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy) return;
    const issueNumber = Number(
      new FormData(event.currentTarget).get("issueNumber"),
    );
    if (
      !Number.isInteger(issueNumber) ||
      issueNumber < 1 ||
      issueNumber > 2147483647
    )
      return;
    setBusy(true);
    setError("");
    try {
      await api.request(`/api/runs/${runId}/ticket/reconcile`, "POST", {
        issueNumber,
      });
      reload();
    } catch (failure) {
      setError(
        message(failure) +
          " The issue must match the approved title, body and repository. No creation request was sent.",
      );
    } finally {
      setBusy(false);
    }
  }
  return (
    <section
      className="info-card ticket-result"
      aria-label="GitHub ticket result"
    >
      <div className="section-heading">
        <h2>GitHub issue</h2>
        <Badge value={ticket.status} />
      </div>
      {ticket.status === "SENDING" && (
        <p>
          Creation is in flight. The durable intent prevents another worker from
          sending it again.
        </p>
      )}
      {ticket.status === "SUCCEEDED" && trustedUrl && (
        <a
          className="ticket-link"
          href={ticket.issueUrl!}
          target="_blank"
          rel="noopener noreferrer"
        >
          View GitHub issue #{ticket.issueNumber}
          <ExternalLink size={14} />
        </a>
      )}
      {cancelled && (
        <p className="policy-note">
          Cancellation did not undo an action already dispatched. This receipt
          records the external outcome.
        </p>
      )}
      {ticket.status === "FAILED" && (
        <p>{ticket.errorCode}. Creation will not be retried.</p>
      )}
      {ticket.status === "UNKNOWN" && (
        <>
          <p className="policy-note">
            GitHub may have created the issue. AgentFlow will not send another
            creation request.
          </p>
          <p className="hint">
            Check the approved repository. If the issue exists, enter its number
            to verify its exact title, body and correlation marker using a read
            request.
          </p>
          <form onSubmit={reconcile}>
            <label>
              Existing GitHub issue number
              <input
                name="issueNumber"
                type="number"
                min={1}
                max={2147483647}
                required
                disabled={busy}
              />
            </label>
            <button disabled={busy}>
              {busy ? "Verifying…" : "Verify existing issue"}
            </button>
          </form>
        </>
      )}
      <ErrorNotice>{error}</ErrorNotice>
    </section>
  );
}
