import { useState } from "react";
import { ArrowUpRight, GitBranch, Plus, Layers, RefreshCw } from "lucide-react";
import { api } from "./api";
import { useResource } from "./useResource";
import type { Page, WorkflowSummary } from "./types";
import { date, ErrorNotice, Pager } from "./ui";
export function Workflows({
  open,
  create,
}: {
  open: (id: string) => void;
  create: () => void;
}) {
  const [page, setPage] = useState(0);
  const resource = useResource(
    (signal) =>
      api.request<Page<WorkflowSummary>>(
        `/api/workflows?page=${page}&size=12`,
        "GET",
        undefined,
        signal,
      ),
    `workflows:${page}`,
  );
  return (
    <>
      <div className="page-heading">
        <div>
          <span className="eyebrow">DESIGN → EXECUTE → REVIEW</span>
          <h1>Your workflows</h1>
          <p>
            A place for thoughtful automation. Build once, follow every step.
          </p>
        </div>
        <button className="primary" onClick={create}>
          <Plus size={17} />
          New workflow
        </button>
      </div>
      <div className="workspace-banner">
        <div className="banner-icon">
          <GitBranch size={26} />
        </div>
        <div>
          <h2>A little structure. A lot of possibility.</h2>
          <p>
            Start with support intake, connect your steps, and put a review
            where it matters.
          </p>
        </div>
        <button onClick={create}>
          Build a workflow
          <ArrowUpRight size={18} />
        </button>
      </div>
      <div className="section-heading">
        <h2>
          All workflows <span>{resource.data?.total ?? "—"}</span>
        </h2>
        <button
          className="quiet"
          aria-label="Refresh workflows"
          onClick={resource.reload}
        >
          <RefreshCw size={16} />
        </button>
      </div>
      <ErrorNotice>{resource.error}</ErrorNotice>
      {resource.loading && !resource.data ? (
        <p className="empty">Loading your workflows…</p>
      ) : resource.data?.items.length === 0 ? (
        <div className="empty">
          <Layers size={32} />
          <h3>Your first flow starts here.</h3>
          <p>Create a workflow and connect the steps that do the work.</p>
          <button onClick={create}>Create workflow</button>
        </div>
      ) : (
        <div className="workflow-grid">
          {resource.data?.items.map((workflow) => (
            <button
              className="workflow-card"
              key={workflow.id}
              onClick={() => open(workflow.id)}
            >
              <div className="card-top">
                <span className="flow-icon">
                  <GitBranch size={21} />
                </span>
                <span className="version">v{workflow.currentVersion}</span>
                <ArrowUpRight size={17} />
              </div>
              <h3>{workflow.name}</h3>
              <p>{workflow.description || "No description yet."}</p>
              <div className="card-bottom">
                <span>Updated {date(workflow.updatedAt)}</span>
                <span>Open flow →</span>
              </div>
            </button>
          ))}
        </div>
      )}
      <Pager page={page} total={resource.data?.total ?? 0} change={setPage} />
    </>
  );
}
