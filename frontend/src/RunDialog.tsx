import { useRef, useState, type FormEvent } from "react";
import { X, Play, ShieldCheck } from "lucide-react";
import { api, message } from "./api";
import type { Workflow, Run } from "./types";
import { ErrorNotice } from "./ui";
import { Modal } from "./Modal";
export function RunDialog({
  workflow,
  close,
  started,
}: {
  workflow: Workflow;
  close: () => void;
  started: (id: string) => void;
}) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const request = useRef<{ fingerprint: string; key: string } | null>(null);
  const agent = workflow.definition.nodes.some(
    (node) => node.type === "AI_STEP",
  );
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const input = {
      subject: String(form.get("subject")),
      body: String(form.get("body")),
    };
    const fingerprint = JSON.stringify([
      workflow.id,
      workflow.currentVersion,
      input,
    ]);
    if (request.current?.fingerprint !== fingerprint)
      request.current = { fingerprint, key: crypto.randomUUID() };
    setBusy(true);
    setError("");
    try {
      const run = await api.request<Run>(
        `/api/workflows/${workflow.id}/runs`,
        "POST",
        {
          versionNumber: workflow.currentVersion,
          idempotencyKey: request.current.key,
          input,
        },
      );
      started(run.id);
    } catch (failure) {
      setError(
        message(failure) + " Retrying unchanged input reuses this submission.",
      );
    } finally {
      setBusy(false);
    }
  }
  return (
    <Modal titleId="run-title" busy={busy} close={close}>
      <div className="modal-heading">
        <span className="flow-icon">
          <Play size={20} />
        </span>
        <button
          className="quiet"
          aria-label="Close run dialog"
          onClick={close}
          disabled={busy}
        >
          <X size={18} />
        </button>
      </div>
      <h2 id="run-title">Run {workflow.name}</h2>
      <p>Start a new execution of saved version {workflow.currentVersion}.</p>
      <form onSubmit={submit}>
        <label>
          Issue subject
          <input name="subject" required maxLength={200} autoFocus />
        </label>
        <label>
          Issue description
          <textarea name="body" required maxLength={4000} rows={4} />
        </label>
        <div className="policy-note">
          <ShieldCheck size={16} />
          {agent
            ? "Agent steps use the configured provider and may spend API tokens. Approval steps still require your decision."
            : "This workflow uses local actions. Approval steps wait for your decision."}
        </div>
        <ErrorNotice>{error}</ErrorNotice>
        <button className="primary" disabled={busy}>
          {busy ? "Starting…" : "Start run"}
          <Play size={15} />
        </button>
      </form>
    </Modal>
  );
}
