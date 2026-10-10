import { Blocks, ShieldCheck } from "lucide-react";
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
          <span className="eyebrow">APPROVED CAPABILITIES</span>
          <h1>Tool registry</h1>
          <p>
            A clear boundary for your agents. Only registered tools can be
            proposed.
          </p>
        </div>
        <span className="flow-icon">
          <Blocks size={24} />
        </span>
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
            On the server, set AGENTFLOW_GITHUB_ENABLED=true and configure
            GITHUB_TOKEN in your ignored environment file. The token needs
            Issues write access for this repository. Restart the backend after
            configuration.
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
