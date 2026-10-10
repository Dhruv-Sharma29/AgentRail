import {
  ReactFlow,
  Background,
  Controls,
  MiniMap,
  Handle,
  Position,
  BackgroundVariant,
  MarkerType,
  useNodeConnections,
  type NodeProps,
  type Edge,
  type NodeChange,
  type EdgeChange,
  type Connection,
} from "@xyflow/react";
import {
  ArrowRight,
  CircleCheck,
  GitBranch,
  Plus,
  ShieldCheck,
  Sparkles,
  Zap,
} from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { cn } from "@/lib/utils";
import type { FlowNode } from "@/graph";
import type { NodeType } from "@/types";

const icons = {
  TRIGGER: Zap,
  AI_STEP: Sparkles,
  APPROVAL: ShieldCheck,
  ACTION: CircleCheck,
  CONDITION: GitBranch,
};
const kinds = {
  TRIGGER: "Trigger",
  AI_STEP: "AI agent",
  APPROVAL: "Human review",
  ACTION: "Action",
  CONDITION: "Condition",
};
const addableTypes = [
  ["TRIGGER", "Trigger"],
  ["AI_STEP", "Agent"],
  ["ACTION", "Action"],
  ["APPROVAL", "Approval"],
] as const;

function WorkflowCard({ data, selected }: NodeProps<FlowNode>) {
  const node = data.definition;
  const Icon = icons[node.type];
  const connections = useNodeConnections();
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
    <Card
      className={cn(
        "workflow-node relative w-[200px] gap-2 rounded-xl border p-3 transition-shadow hover:shadow-md",
        `workflow-node-${node.type.toLowerCase()}`,
        selected &&
          "ring-2 ring-primary/40 ring-offset-2 ring-offset-background",
      )}
      role="article"
      aria-label={`${kinds[node.type]} node: ${node.label}`}
    >
      <div className="flex items-center gap-2.5">
        <div
          className="workflow-node-icon flex size-9 shrink-0 items-center justify-center rounded-lg border border-current/20 bg-card"
          aria-hidden="true"
        >
          <Icon size={17} />
        </div>
        <div className="min-w-0 flex-1">
          <Badge
            variant="outline"
            className="mb-1 rounded-full border-current/20 bg-card px-1.5 py-0 text-[9px] uppercase tracking-wider text-inherit"
          >
            {kinds[node.type]}
          </Badge>
          <strong className="block text-[13px] leading-snug font-semibold break-words text-foreground">
            {node.label}
          </strong>
        </div>
      </div>
      <span className="workflow-node-detail text-[11px] leading-relaxed text-muted-foreground">
        {detail}
      </span>
      <div className="workflow-node-links flex items-center gap-1.5 border-t border-current/10 pt-2 text-[10px] text-muted-foreground">
        <ArrowRight size={12} aria-hidden="true" />
        {connections.length
          ? `${connections.length} ${connections.length === 1 ? "link" : "links"}`
          : "Unconnected"}
      </div>
      {node.type !== "TRIGGER" && (
        <Handle type="target" position={Position.Left} />
      )}
      <Handle type="source" position={Position.Right} />
    </Card>
  );
}
const nodeTypes = { workflow: WorkflowCard };

// Controlled by the editor: positions, edges and node additions enter the same saved definition.
export function N8nWorkflowBlock({
  nodes,
  edges,
  readonly = false,
  status = "Draft",
  onNodesChange,
  onEdgesChange,
  onConnect,
  onSelect,
  onAdd,
  onAgentTemplate,
}: {
  nodes: FlowNode[];
  edges: Edge[];
  readonly?: boolean;
  status?: "Draft" | "Saved" | "Read only" | "Saving";
  onNodesChange?: (changes: NodeChange<FlowNode>[]) => void;
  onEdgesChange?: (changes: EdgeChange[]) => void;
  onConnect?: (connection: Connection) => void;
  onSelect?: (id: string) => void;
  onAdd: (type: Exclude<NodeType, "CONDITION">) => void;
  onAgentTemplate?: () => void;
}) {
  return (
    <section
      aria-label="Workflow builder"
      className="workflow-block min-w-0 rounded-2xl bg-background/60 p-4"
    >
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-2.5">
          <Badge
            variant="outline"
            className="rounded-full border-primary/20 bg-primary/5 px-2.5 py-1 text-[10px] uppercase tracking-wider text-primary"
          >
            {status}
          </Badge>
          <span className="text-[11px] uppercase tracking-[0.15em] text-muted-foreground">
            Workflow builder
          </span>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          {onAgentTemplate && (
            <Button
              variant="ghost"
              size="sm"
              disabled={readonly}
              onClick={onAgentTemplate}
              className="text-xs"
            >
              Agent template
            </Button>
          )}
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button
                variant="outline"
                size="sm"
                disabled={readonly || nodes.length >= 50}
                aria-label="Add node"
                className="rounded-lg text-xs"
              >
                <Plus size={14} aria-hidden="true" />
                Add node
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent
              align="end"
              className="w-44 motion-reduce:animate-none!"
            >
              {addableTypes.map(([type, label]) => {
                const Icon = icons[type];
                return (
                  <DropdownMenuItem
                    key={type}
                    disabled={
                      readonly ||
                      nodes.length >= 50 ||
                      (type === "TRIGGER" &&
                        nodes.some((n) => n.data.definition.type === "TRIGGER"))
                    }
                    onSelect={() => onAdd(type)}
                  >
                    <Icon size={15} aria-hidden="true" />
                    {label}
                  </DropdownMenuItem>
                );
              })}
            </DropdownMenuContent>
          </DropdownMenu>
        </div>
      </div>
      <div className="canvas overflow-hidden rounded-xl border border-border/80 bg-card">
        <ReactFlow<FlowNode>
          colorMode="dark"
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
            type: "default",
            markerEnd: {
              type: MarkerType.ArrowClosed,
              width: 14,
              height: 14,
              color: "#869797",
            },
          }}
          aria-label="Workflow canvas"
        >
          <Background
            variant={BackgroundVariant.Dots}
            color="#293c39"
            gap={22}
            size={1}
          />
          <Controls showInteractive={false} />
          <MiniMap
            pannable
            zoomable
            nodeColor="#315344"
            maskColor="#101618bb"
          />
        </ReactFlow>
      </div>
      <div className="mt-4 flex flex-wrap items-center justify-between gap-3 rounded-lg border border-border/70 bg-card px-3 py-2.5 text-[11px] text-muted-foreground">
        <div
          role="status"
          aria-live="polite"
          className="flex flex-wrap items-center gap-4"
        >
          <span className="flex items-center gap-2">
            <span
              className="size-1.5 rounded-full bg-primary"
              aria-hidden="true"
            />
            {nodes.length} {nodes.length === 1 ? "node" : "nodes"}
          </span>
          <span>
            {edges.length} {edges.length === 1 ? "connection" : "connections"}
          </span>
        </div>
        <span className="text-[10px]">
          {readonly
            ? "Canvas is read only"
            : "Drag to arrange · Connect handles"}
        </span>
      </div>
    </section>
  );
}
