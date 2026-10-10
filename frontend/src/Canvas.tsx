import {
  ReactFlow,
  Background,
  Controls,
  MiniMap,
  Handle,
  Position,
  type NodeProps,
  type Edge,
  type NodeChange,
  type EdgeChange,
  type Connection,
  BackgroundVariant,
} from "@xyflow/react";
import {
  Zap,
  Sparkles,
  ShieldCheck,
  CircleCheck,
  GitBranch,
} from "lucide-react";
import type { FlowNode } from "./graph";
const icons = {
  TRIGGER: Zap,
  AI_STEP: Sparkles,
  APPROVAL: ShieldCheck,
  ACTION: CircleCheck,
  CONDITION: GitBranch,
};
const kinds = {
  TRIGGER: "TRIGGER",
  AI_STEP: "AI AGENT",
  APPROVAL: "HUMAN REVIEW",
  ACTION: "ACTION",
  CONDITION: "CONDITION",
};
function WorkflowCard({ data, selected }: NodeProps<FlowNode>) {
  const node = data.definition;
  const Icon = icons[node.type];
  const detail =
    node.type === "TRIGGER"
      ? "Support issue input"
      : node.type === "APPROVAL"
        ? "Pause for your decision"
        : node.type === "AI_STEP"
          ? `${node.agent?.allowedTools.length ?? 0} approved tools`
          : (node.operation?.replaceAll("_", " ").toLowerCase() ??
            "Configure this step");
  return (
    <div
      className={`canvas-card ${node.type.toLowerCase()} ${selected ? "selected" : ""}`}
    >
      <div className="node-kind">
        <Icon size={15} />
        <span>{kinds[node.type]}</span>
        <span className="node-dot" />
      </div>
      <strong>{node.label}</strong>
      <span className="node-detail">{detail}</span>
      {node.type !== "TRIGGER" && (
        <Handle type="target" position={Position.Left} />
      )}
      <Handle type="source" position={Position.Right} />
    </div>
  );
}
const nodeTypes = { workflow: WorkflowCard };
export function Canvas({
  nodes,
  edges,
  readonly = false,
  onNodesChange,
  onEdgesChange,
  onConnect,
  onSelect,
}: {
  nodes: FlowNode[];
  edges: Edge[];
  readonly?: boolean;
  onNodesChange?: (changes: NodeChange<FlowNode>[]) => void;
  onEdgesChange?: (changes: EdgeChange[]) => void;
  onConnect?: (connection: Connection) => void;
  onSelect?: (id: string) => void;
}) {
  return (
    <ReactFlow<FlowNode>
      nodes={nodes}
      edges={edges}
      nodeTypes={nodeTypes}
      onNodesChange={onNodesChange}
      onEdgesChange={onEdgesChange}
      onConnect={onConnect}
      onNodeClick={(_, node) => onSelect?.(node.id)}
      onSelectionChange={({ nodes }) => {
        if (nodes[0]) onSelect?.(nodes[0].id);
      }}
      nodesDraggable={!readonly}
      nodesConnectable={!readonly}
      edgesReconnectable={false}
      deleteKeyCode={readonly ? null : ["Backspace", "Delete"]}
      nodeExtent={[
        [-10000, -10000],
        [10000, 10000],
      ]}
      fitView
      fitViewOptions={{ padding: 0.15, maxZoom: 1 }}
      minZoom={0.25}
      maxZoom={1.5}
      defaultEdgeOptions={{
        type: "smoothstep",
        style: { stroke: "#a5b993", strokeWidth: 1.5 },
      }}
      aria-label="Workflow canvas"
    >
      <Background
        variant={BackgroundVariant.Dots}
        color="#cbd5c1"
        gap={22}
        size={1}
      />
      <Controls showInteractive={false} />
      <MiniMap pannable zoomable nodeColor="#b7cd9f" maskColor="#f4f7eeaa" />
    </ReactFlow>
  );
}
