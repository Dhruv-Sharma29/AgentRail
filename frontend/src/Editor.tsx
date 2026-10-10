import { useEffect, useState } from "react";
import {
  useNodesState,
  useEdgesState,
  type Connection,
  type NodeChange,
  type EdgeChange,
} from "@xyflow/react";
import {
  ArrowLeft,
  Save,
  Play,
  Zap,
  Sparkles,
  ShieldCheck,
  CircleCheck,
  Archive,
  Link2,
  X,
} from "lucide-react";
import { api, ApiError, message } from "./api";
import { useResource } from "./useResource";
import type {
  Workflow,
  WorkflowNode,
  NodeType,
  Page,
  Version,
  RunSummary,
} from "./types";
import { Canvas } from "./Canvas";
import { StepInspector } from "./StepInspector";
import {
  supportTemplate,
  toCanvas,
  fromCanvas,
  validate,
  connect,
  type FlowNode,
} from "./graph";
import { RunDialog } from "./RunDialog";
import { Modal } from "./Modal";
import { draftGuard } from "./navigation";
import { Badge, date, ErrorNotice, Pager, JsonView } from "./ui";
export function WorkflowPage({
  id,
  navigate,
}: {
  id: string;
  navigate: (path: string) => void;
}) {
  const resource = useResource(
    (signal) =>
      id === "new"
        ? Promise.resolve(null)
        : api.request<Workflow>(
            `/api/workflows/${id}`,
            "GET",
            undefined,
            signal,
          ),
    `workflow:${id}`,
  );
  if (resource.error)
    return (
      <>
        <ErrorNotice>{resource.error}</ErrorNotice>
        <button onClick={resource.reload}>Retry</button>
      </>
    );
  if (resource.loading || resource.data === undefined)
    return <p className="empty">Opening workflow…</p>;
  return (
    <Editor
      key={id + ":" + resource.data?.currentVersion + ":" + resource.revision}
      initial={resource.data}
      navigate={navigate}
      reload={resource.reload}
    />
  );
}
function Editor({
  initial,
  navigate,
  reload,
}: {
  initial: Workflow | null;
  navigate: (path: string) => void;
  reload: () => void;
}) {
  const [workflow, setWorkflow] = useState(initial);
  const [name, setName] = useState(initial?.name ?? "Support intake");
  const [description, setDescription] = useState(
    initial?.description ??
      "Classify an issue, find helpful documentation, review, and record the result.",
  );
  const canvas = toCanvas(initial?.definition ?? supportTemplate());
  const [nodes, setNodes, changeNodes] = useNodesState<FlowNode>(canvas.nodes);
  const [edges, setEdges, changeEdges] = useEdgesState(canvas.edges);
  const [selected, setSelected] = useState<string | undefined>(nodes[0]?.id);
  const [dirty, setDirty] = useState(!initial);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [tab, setTab] = useState("Builder");
  const [runDialog, setRunDialog] = useState(false);
  const [archiveDialog, setArchiveDialog] = useState(false);
  const [source, setSource] = useState("");
  const [target, setTarget] = useState("");
  useEffect(() => {
    draftGuard.dirty = dirty;
    return () => {
      draftGuard.dirty = false;
    };
  }, [dirty]);
  useEffect(() => {
    const prevent = (event: BeforeUnloadEvent) => {
      if (dirty) event.preventDefault();
    };
    window.addEventListener("beforeunload", prevent);
    return () => window.removeEventListener("beforeunload", prevent);
  }, [dirty]);
  const current = nodes.find((node) => node.id === selected);
  function onNodesChange(changes: NodeChange<FlowNode>[]) {
    changeNodes(changes);
    if (
      changes.some(
        (change) => change.type === "position" || change.type === "remove",
      )
    )
      setDirty(true);
  }
  function onEdgesChange(changes: EdgeChange[]) {
    changeEdges(changes);
    if (changes.some((change) => change.type !== "select")) setDirty(true);
  }
  function connectSteps(connection: Pick<Connection, "source" | "target">) {
    const next = connect(edges, connection.source, connection.target);
    if (next === edges) {
      setError(
        "Choose two different steps with a free input and output. Cycles are not allowed.",
      );
      return;
    }
    setEdges(
      next.map((edge) => ({
        ...edge,
        id: `${edge.source}-${edge.target}`,
        type: "smoothstep",
      })),
    );
    setDirty(true);
    setError("");
  }
  function changeNode(node: WorkflowNode) {
    setNodes((nodes) =>
      nodes.map((n) =>
        n.id === node.id ? { ...n, data: { definition: node } } : n,
      ),
    );
    setDirty(true);
  }
  function add(type: NodeType) {
    const id = type.toLowerCase() + "_" + crypto.randomUUID().slice(0, 8);
    const definition: WorkflowNode = {
      id,
      type,
      label:
        type === "AI_STEP"
          ? "AI agent"
          : type === "APPROVAL"
            ? "Review action"
            : type === "TRIGGER"
              ? "Receive input"
              : "Record result",
      ...(type === "ACTION" && { operation: "RECORD_RESULT" }),
      ...(type === "AI_STEP" && {
        agent: {
          goal: "CLASSIFY_ISSUE",
          allowedTools: ["classify_issue"],
          maxToolCalls: 1,
        },
      }),
      ...(type === "APPROVAL" && { approval: { timeoutSeconds: 3600 } }),
    };
    setNodes((nodes) => [
      ...nodes,
      {
        id,
        type: "workflow",
        data: { definition },
        position: { x: 100 + nodes.length * 100, y: 360 },
      },
    ]);
    setSelected(id);
    setDirty(true);
  }
  async function save() {
    const definition = fromCanvas(nodes, edges);
    const errors = validate(definition);
    if (!name.trim()) errors.unshift("Give this workflow a name.");
    if (errors.length) {
      setError(errors.join(" "));
      return;
    }
    setBusy(true);
    setError("");
    setNotice("");
    try {
      const saved = await api.request<Workflow>(
        workflow ? `/api/workflows/${workflow.id}` : "/api/workflows",
        workflow ? "PUT" : "POST",
        {
          name,
          description,
          definition,
          ...(workflow && { expectedVersion: workflow.currentVersion }),
        },
      );
      draftGuard.dirty = false;
      setWorkflow(saved);
      setDirty(false);
      setName(saved.name);
      setNotice("Version saved.");
      if (!workflow) navigate("/workflows/" + saved.id);
    } catch (failure) {
      setError(
        failure instanceof ApiError && failure.status === 409
          ? "A newer version exists. Your draft is kept. Reload latest to discard it and review the changes."
          : message(failure),
      );
    } finally {
      setBusy(false);
    }
  }
  async function archive() {
    if (!workflow) return;
    setBusy(true);
    setError("");
    try {
      await api.request(`/api/workflows/${workflow.id}`, "DELETE");
      draftGuard.dirty = false;
      setDirty(false);
      navigate("/workflows");
    } catch (failure) {
      setError(message(failure));
      setArchiveDialog(false);
    } finally {
      setBusy(false);
    }
  }
  function leave() {
    navigate("/workflows");
  }
  return (
    <>
      <div className="editor-heading">
        <button
          className="quiet"
          aria-label="Back to workflows"
          onClick={leave}
        >
          <ArrowLeft size={18} />
        </button>
        <div>
          <h1>{workflow?.name ?? "New workflow"}</h1>
          <span className="save-state">
            {dirty ? "Unsaved changes" : `Saved · v${workflow?.currentVersion}`}
          </span>
          {workflow?.archived && <Badge value="ARCHIVED" />}
        </div>
        <div className="editor-actions">
          {workflow && !workflow.archived && (
            <button
              className="quiet"
              aria-label="Archive workflow"
              disabled={busy}
              onClick={() => setArchiveDialog(true)}
            >
              <Archive size={17} />
            </button>
          )}
          <button
            disabled={busy || !dirty || workflow?.archived}
            onClick={save}
          >
            <Save size={15} />
            Save workflow
          </button>
          <button
            className="primary"
            disabled={
              !workflow ||
              dirty ||
              busy ||
              workflow?.archived ||
              validate(fromCanvas(nodes, edges)).length > 0
            }
            onClick={() => setRunDialog(true)}
          >
            <Play size={15} />
            Run workflow
          </button>
        </div>
      </div>
      <ErrorNotice>{error}</ErrorNotice>
      {error.includes("Your draft is kept") && (
        <button
          onClick={() => {
            if (
              window.confirm(
                "Discard your unsaved draft and reload the latest version?",
              )
            )
              reload();
          }}
        >
          Reload latest
        </button>
      )}
      <div aria-live="polite" className="notice">
        {notice}
      </div>
      <div className="editor-tabs">
        {["Builder", "Runs", "Versions"].map((value) => (
          <button
            key={value}
            className={tab === value ? "selected-tab" : ""}
            disabled={value !== "Builder" && !workflow}
            onClick={() => setTab(value)}
          >
            {value}
          </button>
        ))}
        <span>Saving creates an immutable version</span>
      </div>
      {tab === "Builder" && (
        <>
          <div className="workflow-meta">
            <label>
              Workflow name
              <input
                value={name}
                maxLength={120}
                disabled={busy || workflow?.archived}
                onChange={(e) => {
                  setName(e.target.value);
                  setDirty(true);
                }}
              />
            </label>
            <label>
              Description
              <input
                value={description}
                maxLength={2000}
                disabled={busy || workflow?.archived}
                onChange={(e) => {
                  setDescription(e.target.value);
                  setDirty(true);
                }}
              />
            </label>
          </div>
          <div className="editor-workspace">
            <div className="canvas-area">
              <div className="canvas-toolbar">
                <span>ADD A STEP</span>
                {(
                  [
                    ["TRIGGER", Zap, "Trigger"],
                    ["AI_STEP", Sparkles, "Agent"],
                    ["ACTION", CircleCheck, "Action"],
                    ["APPROVAL", ShieldCheck, "Approval"],
                  ] as const
                ).map(([type, Icon, label]) => (
                  <button
                    key={type}
                    disabled={
                      busy ||
                      workflow?.archived ||
                      nodes.length >= 50 ||
                      (type === "TRIGGER" &&
                        nodes.some((n) => n.data.definition.type === "TRIGGER"))
                    }
                    onClick={() => add(type)}
                  >
                    <Icon size={14} />
                    {label}
                  </button>
                ))}
                {!workflow && (
                  <button
                    className="quiet"
                    disabled={busy}
                    onClick={() => {
                      const agent = toCanvas(supportTemplate(true));
                      setNodes(agent.nodes);
                      setEdges(agent.edges);
                      setDirty(true);
                    }}
                  >
                    Agent template
                  </button>
                )}
              </div>
              <div className="canvas">
                <Canvas
                  nodes={nodes}
                  edges={edges}
                  readonly={busy || workflow?.archived}
                  onNodesChange={onNodesChange}
                  onEdgesChange={onEdgesChange}
                  onConnect={connectSteps}
                  onSelect={setSelected}
                />
              </div>
              <div className="canvas-caption">
                <span>
                  <span className="dot" />
                  {nodes.length} steps · {edges.length} connections
                </span>
                <span>
                  Drag to arrange · Connect handles · Select an edge + Delete to
                  disconnect
                </span>
              </div>
            </div>
            <StepInspector
              disabled={busy}
              node={workflow?.archived ? undefined : current?.data.definition}
              position={current?.position}
              change={changeNode}
              move={(position) => {
                setNodes((nodes) =>
                  nodes.map((n) =>
                    n.id === selected ? { ...n, position } : n,
                  ),
                );
                setDirty(true);
              }}
              remove={() => {
                setNodes(nodes.filter((n) => n.id !== selected));
                setEdges(
                  edges.filter(
                    (e) => e.source !== selected && e.target !== selected,
                  ),
                );
                setSelected(undefined);
                setDirty(true);
              }}
            />
          </div>
          <details className="connection-tools">
            <summary>
              <Link2 size={14} />
              Connect steps with the keyboard
            </summary>
            <div>
              <label>
                Source step
                <select
                  value={source}
                  disabled={busy || workflow?.archived}
                  onChange={(e) => setSource(e.target.value)}
                >
                  <option value="">Choose source</option>
                  {nodes.map((n) => (
                    <option key={n.id} value={n.id}>
                      {n.data.definition.label}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                Target step
                <select
                  value={target}
                  disabled={busy || workflow?.archived}
                  onChange={(e) => setTarget(e.target.value)}
                >
                  <option value="">Choose target</option>
                  {nodes.map((n) => (
                    <option key={n.id} value={n.id}>
                      {n.data.definition.label}
                    </option>
                  ))}
                </select>
              </label>
              <button
                disabled={busy || !source || !target || workflow?.archived}
                onClick={() => connectSteps({ source, target })}
              >
                Connect steps
              </button>
            </div>
          </details>
        </>
      )}
      {tab === "Runs" && workflow && (
        <RunHistory workflow={workflow.id} navigate={navigate} />
      )}
      {tab === "Versions" && workflow && <Versions workflow={workflow.id} />}
      {runDialog && workflow && (
        <RunDialog
          workflow={workflow}
          close={() => setRunDialog(false)}
          started={(id) => {
            setRunDialog(false);
            navigate("/runs/" + id);
          }}
        />
      )}
      {archiveDialog && (
        <Modal
          titleId="archive-title"
          busy={busy}
          close={() => setArchiveDialog(false)}
        >
          <h2 id="archive-title">Archive this workflow?</h2>
          <p>
            It leaves the active list. Existing versions, runs and approvals
            remain available.
          </p>
          <div className="modal-buttons">
            <button onClick={() => setArchiveDialog(false)} disabled={busy}>
              Keep workflow
            </button>
            <button className="danger" onClick={archive} disabled={busy}>
              Archive workflow
            </button>
          </div>
          <button
            className="quiet"
            aria-label="Close archive dialog"
            disabled={busy}
            onClick={() => setArchiveDialog(false)}
          >
            <X size={16} />
          </button>
        </Modal>
      )}
    </>
  );
}
function RunHistory({
  workflow,
  navigate,
}: {
  workflow: string;
  navigate: (path: string) => void;
}) {
  const [page, setPage] = useState(0);
  const resource = useResource(
    (signal) =>
      api.request<Page<RunSummary>>(
        `/api/workflows/${workflow}/runs?page=${page}&size=12`,
        "GET",
        undefined,
        signal,
      ),
    `history:${workflow}:${page}`,
    3000,
  );
  return (
    <>
      <ErrorNotice>{resource.error}</ErrorNotice>
      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Execution</th>
              <th>Version</th>
              <th>Status</th>
              <th>Started</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {resource.data?.items.map((run) => (
              <tr key={run.id}>
                <td>
                  <code>{run.id.slice(0, 8)}</code>
                </td>
                <td>v{run.versionNumber}</td>
                <td>
                  <Badge value={run.status} />
                </td>
                <td>{date(run.createdAt)}</td>
                <td>
                  <button onClick={() => navigate("/runs/" + run.id)}>
                    View trace
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {resource.loading && !resource.data ? (
          <p className="empty">Loading executions…</p>
        ) : (
          !resource.data?.items.length && (
            <p className="empty">
              No runs yet. Start a saved workflow to follow its execution.
            </p>
          )
        )}
      </div>
      <Pager page={page} total={resource.data?.total ?? 0} change={setPage} />
    </>
  );
}
function Versions({ workflow }: { workflow: string }) {
  const [page, setPage] = useState(0);
  const resource = useResource(
    (signal) =>
      api.request<Page<Version>>(
        `/api/workflows/${workflow}/versions?page=${page}&size=12`,
        "GET",
        undefined,
        signal,
      ),
    `versions:${workflow}:${page}`,
  );
  return (
    <>
      <ErrorNotice>{resource.error}</ErrorNotice>
      {resource.data?.items.map((version) => (
        <article className="version-card" key={version.id}>
          <div>
            <span className="version">v{version.number}</span>
            <h3>{version.name}</h3>
            <small>{date(version.createdAt)}</small>
          </div>
          <p>{version.description}</p>
          <JsonView
            value={version.definition}
            label="Inspect immutable definition"
          />
        </article>
      ))}
      <Pager page={page} total={resource.data?.total ?? 0} change={setPage} />
    </>
  );
}
