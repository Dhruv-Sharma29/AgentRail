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
          <h1>Your workflows</h1>
          <p>Manage workflow definitions, versions, and runs.</p>
        </div>
        <button className="primary" onClick={create}>
          <Plus size={17} />
          New workflow
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
          <h3>No workflows yet</h3>
          <p>Create a workflow to configure and run your first process.</p>
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
                <span>Open workflow →</span>
              </div>
            </button>
          ))}
        </div>
      )}
      <Pager page={page} total={resource.data?.total ?? 0} change={setPage} />
    </>
  );
}
