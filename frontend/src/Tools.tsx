import { Blocks, ShieldCheck } from "lucide-react";
import { api } from "./api";
import { useResource } from "./useResource";
import type { Tool } from "./types";
import { Badge, ErrorNotice, JsonView } from "./ui";
export function Tools() {
  const resource = useResource(
    (signal) => api.request<Tool[]>("/api/tools", "GET", undefined, signal),
    "tools",
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
      <ErrorNotice>{resource.error}</ErrorNotice>
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
