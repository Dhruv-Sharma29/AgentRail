import { ShieldCheck } from "lucide-react";
import { api } from "./api";
import { useResource } from "./useResource";
import type { Tool, TicketConnector } from "./types";
import { Badge, ErrorNotice, JsonView } from "./ui";
export function Tools() {
  const resource = useResource(
    (signal) => api.request<Tool[]>("/api/tools", "GET", undefined, signal),
    "tools",
  );
  const connector = useResource(
    (signal) =>
      api.request<TicketConnector>(
        "/api/tools/github",
        "GET",
        undefined,
        signal,
      ),
    "github-connector",
  );
  return (
    <>
      <div className="page-heading">
        <div>
          <h1>Tool registry</h1>
          <p>Inspect registered capabilities and their execution policies.</p>
        </div>
      </div>
      <ErrorNotice>{resource.error || connector.error}</ErrorNotice>
      <article className="tool-card connector-status">
        <div>
          <h2>GitHub Issues</h2>
          <Badge value={connector.data?.ready ? "READY" : "DISABLED"} />
        </div>
        <p>
          Allowed repository:{" "}
          <code>{connector.data?.repository ?? "Loading…"}</code>
        </p>
        <p>
          Ticket creation is an approval-gated action. Agents can select only
          the read-only tools below.
        </p>
        {!connector.data?.ready && (
          <p className="hint">
            GitHub issue creation is not configured for this workspace.
          </p>
        )}
      </article>
      {resource.loading ? (
        <p className="empty">Loading registered tools…</p>
      ) : (
        resource.data?.map((tool) => (
          <article className="tool-card" key={tool.name}>
            <div>
              <span className="flow-icon">
                <ShieldCheck size={20} />
              </span>
              <h2>{tool.name}</h2>
              <Badge value={tool.risk} />
            </div>
            <p>{tool.description}</p>
            <small>Allowed roles: {tool.roles.join(", ")}</small>
            <JsonView value={tool.argumentSchema} label="Argument schema" />
          </article>
        ))
      )}
    </>
  );
}
