export type NodeType =
  "TRIGGER" | "ACTION" | "AI_STEP" | "APPROVAL" | "CONDITION";
export type Operation =
  | "RECEIVE_INPUT"
  | "CLASSIFY_ISSUE"
  | "SEARCH_DOCUMENTATION"
  | "RECORD_RESULT"
  | "CREATE_TICKET";
export interface WorkflowNode {
  id: string;
  type: NodeType;
  label: string;
  operation?: Operation | null;
  agent?: {
    goal: "CLASSIFY_ISSUE" | "FIND_DOCUMENTATION";
    allowedTools: string[];
    maxToolCalls: number;
  } | null;
  approval?: { timeoutSeconds: number } | null;
  ticket?: { repository: string } | null;
  position?: { x: number; y: number } | null;
}
export interface Definition {
  nodes: WorkflowNode[];
  edges: { source: string; target: string }[];
}
export interface WorkflowSummary {
  id: string;
  name: string;
  description: string;
  currentVersion: number;
  updatedAt: string;
}
export interface Workflow extends WorkflowSummary {
  archived: boolean;
  createdAt: string;
  definition: Definition;
}
export interface Version {
  id: string;
  number: number;
  name: string;
  description: string;
  definition: Definition;
  createdAt: string;
}
export interface Page<T> {
  items: T[];
  total: number;
  page: number;
  size: number;
}
export interface User {
  username: string;
  roles: string[];
}
export type Status =
  | "PENDING"
  | "RUNNING"
  | "WAITING_FOR_APPROVAL"
  | "SUCCEEDED"
  | "FAILED"
  | "CANCELLED";
export interface RunSummary {
  id: string;
  workflowId: string;
  versionNumber: number;
  status: Status;
  createdAt: string;
  startedAt: string | null;
  finishedAt: string | null;
  errorCode: string | null;
}
export interface Approval {
  id: string;
  runId: string;
  nodeId: string;
  targetNodeId: string;
  requiredRole: string;
  status: "PENDING" | "APPROVED" | "REJECTED" | "CANCELLED" | "EXPIRED";
  payload: unknown;
  payloadDigest: string;
  createdAt: string;
  expiresAt: string;
  decidedAt: string | null;
  decidedBy: string | null;
}
export interface Step {
  nodeId: string;
  ordinal: number;
  operation: Operation | null;
  status: Status;
  attempts: number;
  input: unknown;
  output: unknown;
  errorCode: string | null;
  startedAt: string | null;
  finishedAt: string | null;
  plannerId: string | null;
  proposal: unknown;
  policyStatus: string | null;
  decisionCode: string | null;
  planningState: string | null;
  planningDeadline: string | null;
  nextAttemptAt: string | null;
}
export interface Run extends RunSummary {
  mode: string;
  input: unknown;
  context: unknown;
  steps: Step[];
  events: {
    sequence: number;
    nodeId: string | null;
    eventType: string;
    occurredAt: string;
    toolName: string | null;
    decisionCode: string | null;
  }[];
  plannerCalls: number;
  toolCalls: number;
  reservedPlannerTokens: number;
  plannerTokens: number;
  approvals: Approval[];
  tickets: Ticket[];
}
export interface Tool {
  name: string;
  description: string;
  risk: string;
  roles: string[];
  argumentSchema: unknown;
}
export const terminal = (status: Status) =>
  ["SUCCEEDED", "FAILED", "CANCELLED"].includes(status);

export interface Ticket {
  nodeId: string;
  status: "SENDING" | "SUCCEEDED" | "FAILED" | "UNKNOWN";
  createdAt: string;
  deadline: string;
  finishedAt: string | null;
  issueNumber: number | null;
  issueUrl: string | null;
  errorCode: string | null;
}
export interface TicketConnector {
  repository: string;
  ready: boolean;
}
