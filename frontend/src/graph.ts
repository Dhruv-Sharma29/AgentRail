import type { Node, Edge } from "@xyflow/react";
import type { Definition, WorkflowNode } from "./types";
export type FlowNode = Node<{ definition: WorkflowNode }, "workflow">;
export function supportTemplate(agent = false): Definition {
  const nodes: WorkflowNode[] = [
    { id: "receive", type: "TRIGGER", label: "Receive issue" },
    agent
      ? {
          id: "classify",
          type: "AI_STEP",
          label: "Classify issue",
          agent: {
            goal: "CLASSIFY_ISSUE",
            allowedTools: ["classify_issue"],
            maxToolCalls: 1,
          },
        }
      : {
          id: "classify",
          type: "ACTION",
          label: "Classify issue",
          operation: "CLASSIFY_ISSUE",
        },
    agent
      ? {
          id: "search",
          type: "AI_STEP",
          label: "Find documentation",
          agent: {
            goal: "FIND_DOCUMENTATION",
            allowedTools: ["search_documentation"],
            maxToolCalls: 1,
          },
        }
      : {
          id: "search",
          type: "ACTION",
          label: "Find documentation",
          operation: "SEARCH_DOCUMENTATION",
        },
    {
      id: "review",
      type: "APPROVAL",
      label: "Review result",
      approval: { timeoutSeconds: 3600 },
    },
    {
      id: "record",
      type: "ACTION",
      label: "Record result",
      operation: "RECORD_RESULT",
    },
  ];
  return {
    nodes: nodes.map((node, i) => ({
      ...node,
      position: { x: 80 + i * 240, y: 200 },
    })),
    edges: nodes
      .slice(1)
      .map((node, i) => ({ source: nodes[i].id, target: node.id })),
  };
}
export function toCanvas(definition: Definition): {
  nodes: FlowNode[];
  edges: Edge[];
} {
  return {
    nodes: definition.nodes.map((node, i) => ({
      id: node.id,
      type: "workflow",
      position: node.position ?? { x: 80 + i * 240, y: 200 },
      data: { definition: node },
    })),
    edges: definition.edges.map((edge) => ({
      id: `${edge.source}-${edge.target}`,
      source: edge.source,
      target: edge.target,
      type: "default",
    })),
  };
}
export function fromCanvas(nodes: FlowNode[], edges: Edge[]): Definition {
  const bounded = (n: number) =>
    Math.max(-10000, Math.min(10000, Number.isFinite(n) ? n : 0));
  return {
    nodes: nodes.map(({ data: { definition: n }, position }) => ({
      id: n.id,
      label: n.label,
      type: n.type,
      ...(n.operation != null && { operation: n.operation }),
      ...(n.agent != null && { agent: n.agent }),
      ...(n.approval != null && { approval: n.approval }),
      position: { x: bounded(position.x), y: bounded(position.y) },
    })),
    edges: edges.map(({ source, target }) => ({ source, target })),
  };
}
export function connect(
  edges: Definition["edges"],
  source: string,
  target: string,
) {
  if (
    source === target ||
    edges.some((e) => e.source === source || e.target === target)
  )
    return edges;
  const next = [...edges, { source, target }];
  let at: string | undefined = target;
  const visited = new Set<string>();
  while (at) {
    if (at === source || visited.has(at)) return edges;
    visited.add(at);
    at = next.find((e) => e.source === at)?.target;
  }
  return next;
}
export function validate(definition: Definition): string[] {
  const errors: string[] = [];
  const ids = definition.nodes.map((n) => n.id);
  const triggers = definition.nodes.filter((n) => n.type === "TRIGGER");
  if (triggers.length !== 1) errors.push("Use exactly one trigger.");
  if (
    !definition.nodes.length ||
    definition.nodes.length > 50 ||
    new Set(ids).size !== ids.length
  )
    errors.push("Use 1–50 nodes with unique IDs.");
  if (definition.nodes.some((n) => !n.label.trim()))
    errors.push("Every step needs a label.");
  if (
    definition.nodes.some((n) => n.type === "CONDITION") ||
    definition.edges.some(
      (e) => !ids.includes(e.source) || !ids.includes(e.target),
    ) ||
    definition.edges.length !== definition.nodes.length - 1 ||
    ids.some(
      (id) =>
        definition.edges.filter((e) => e.source === id).length > 1 ||
        definition.edges.filter((e) => e.target === id).length > 1,
    )
  )
    errors.push("Only one connected linear chain can run.");
  let at = triggers[0]?.id;
  const visited = new Set<string>();
  while (at && !visited.has(at)) {
    visited.add(at);
    at = definition.edges.find((e) => e.source === at)?.target ?? "";
  }
  if (visited.size !== ids.length || at)
    errors.push("Connect all steps in order, without cycles.");
  for (const node of definition.nodes) {
    if (
      node.type === "ACTION" &&
      (!node.operation || node.operation === "RECEIVE_INPUT")
    )
      errors.push("Choose an action for every action step.");
    if (
      node.type === "AI_STEP" &&
      (!node.agent || !node.agent.allowedTools.length)
    )
      errors.push("Choose approved tools for every agent.");
    if (node.type === "APPROVAL") {
      const target = definition.nodes.find(
        (n) =>
          n.id === definition.edges.find((e) => e.source === node.id)?.target,
      );
      if (target?.type !== "ACTION" || target.operation !== "RECORD_RESULT")
        errors.push("Approval must immediately precede Record result.");
      if (
        !node.approval ||
        node.approval.timeoutSeconds < 60 ||
        node.approval.timeoutSeconds > 86400
      )
        errors.push("Approval timeout must be 60–86,400 seconds.");
    }
  }
  return [...new Set(errors)];
}
