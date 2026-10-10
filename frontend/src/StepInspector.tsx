import { Trash2, SlidersHorizontal } from "lucide-react";
import type { WorkflowNode, NodeType, Operation } from "./types";
const actions: [Operation, string][] = [
  ["CLASSIFY_ISSUE", "Classify issue"],
  ["SEARCH_DOCUMENTATION", "Find documentation"],
  ["RECORD_RESULT", "Record result"],
];
export function StepInspector({
  node,
  disabled = false,
  change,
  remove,
  move,
  position,
}: {
  node?: WorkflowNode;
  disabled?: boolean;
  change: (node: WorkflowNode) => void;
  remove: () => void;
  move: (position: { x: number; y: number }) => void;
  position?: { x: number; y: number };
}) {
  if (!node)
    return (
      <aside className="inspector">
        <div className="inspector-heading">
          <SlidersHorizontal size={16} />
          <h2>Step settings</h2>
        </div>
        <p className="inspector-empty">
          Select a step on the canvas to give it a purpose.
        </p>
      </aside>
    );
  function typeChanged(type: NodeType) {
    if (!node) return;
    change({
      id: node.id,
      label: node.label,
      type,
      position: node.position,
      ...(type === "ACTION" && { operation: "RECORD_RESULT" as Operation }),
      ...(type === "AI_STEP" && {
        agent: {
          goal: "CLASSIFY_ISSUE",
          allowedTools: ["classify_issue"],
          maxToolCalls: 1,
        },
      }),
      ...(type === "APPROVAL" && { approval: { timeoutSeconds: 3600 } }),
    });
  }
  return (
    <aside className="inspector">
      <div className="inspector-heading">
        <SlidersHorizontal size={16} />
        <h2>Step settings</h2>
        <span className="version">{node.type}</span>
      </div>
      <fieldset className="inspector-fields" disabled={disabled}>
        <label>
          Step label
          <input
            value={node.label}
            maxLength={120}
            onChange={(event) => change({ ...node, label: event.target.value })}
          />
        </label>
        <label>
          Step type
          <select
            value={node.type}
            onChange={(event) => typeChanged(event.target.value as NodeType)}
          >
            {[
              "TRIGGER",
              "ACTION",
              "AI_STEP",
              "APPROVAL",
              ...(node.type === "CONDITION" ? ["CONDITION"] : []),
            ].map((type) => (
              <option key={type}>{type}</option>
            ))}
          </select>
        </label>
        {node.type === "ACTION" && (
          <label>
            Action
            <select
              value={node.operation ?? ""}
              onChange={(event) =>
                change({ ...node, operation: event.target.value as Operation })
              }
            >
              <option value="">Choose an action</option>
              {actions.map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </select>
          </label>
        )}
        {node.type === "AI_STEP" && (
          <>
            <label>
              Agent goal
              <select
                value={node.agent?.goal ?? "CLASSIFY_ISSUE"}
                onChange={(event) => {
                  const goal = event.target.value as
                    "CLASSIFY_ISSUE" | "FIND_DOCUMENTATION";
                  change({
                    ...node,
                    agent: {
                      goal,
                      allowedTools: [
                        goal === "CLASSIFY_ISSUE"
                          ? "classify_issue"
                          : "search_documentation",
                      ],
                      maxToolCalls: 1,
                    },
                  });
                }}
              >
                <option value="CLASSIFY_ISSUE">Classify issue</option>
                <option value="FIND_DOCUMENTATION">Find documentation</option>
              </select>
            </label>
            <fieldset>
              <legend>Approved tools</legend>
              {["classify_issue", "search_documentation"].map((tool) => (
                <label className="check" key={tool}>
                  <input
                    type="checkbox"
                    checked={node.agent?.allowedTools.includes(tool) ?? false}
                    onChange={(event) =>
                      change({
                        ...node,
                        agent: {
                          goal: node.agent?.goal ?? "CLASSIFY_ISSUE",
                          maxToolCalls: node.agent?.maxToolCalls ?? 1,
                          allowedTools: event.target.checked
                            ? [...(node.agent?.allowedTools ?? []), tool]
                            : (node.agent?.allowedTools.filter(
                                (t) => t !== tool,
                              ) ?? []),
                        },
                      })
                    }
                  />
                  {tool.replaceAll("_", " ")}
                </label>
              ))}
            </fieldset>
            <label>
              Maximum tool calls
              <input
                type="number"
                min={1}
                max={3}
                value={node.agent?.maxToolCalls ?? 1}
                onChange={(event) =>
                  change({
                    ...node,
                    agent: {
                      goal: node.agent?.goal ?? "CLASSIFY_ISSUE",
                      allowedTools: node.agent?.allowedTools ?? [],
                      maxToolCalls: Number(event.target.value),
                    },
                  })
                }
              />
            </label>
            <p className="hint">
              Uses the configured planner. A live provider may spend API tokens.
            </p>
          </>
        )}
        {node.type === "APPROVAL" && (
          <>
            <label>
              Approval timeout (seconds)
              <input
                type="number"
                min={60}
                max={86400}
                value={node.approval?.timeoutSeconds ?? 3600}
                onChange={(event) =>
                  change({
                    ...node,
                    approval: { timeoutSeconds: Number(event.target.value) },
                  })
                }
              />
            </label>
            <div className="policy-note">
              Execution pauses here. Only your explicit decision can authorize
              the next result action.
            </div>
          </>
        )}
        {node.type === "CONDITION" && (
          <p className="hint">
            This saved step is inspectable. Conditions are not supported by the
            current execution engine.
          </p>
        )}
        <details className="layout-settings">
          <summary>Canvas position</summary>
          <label>
            X position
            <input
              type="number"
              min={-10000}
              max={10000}
              value={Math.round(position?.x ?? 0)}
              onChange={(event) =>
                move({ x: Number(event.target.value), y: position?.y ?? 0 })
              }
            />
          </label>
          <label>
            Y position
            <input
              type="number"
              min={-10000}
              max={10000}
              value={Math.round(position?.y ?? 0)}
              onChange={(event) =>
                move({ x: position?.x ?? 0, y: Number(event.target.value) })
              }
            />
          </label>
        </details>
        <div className="step-id">
          STEP ID <code>{node.id}</code>
        </div>
        <button
          className="danger"
          disabled={node.type === "TRIGGER"}
          onClick={remove}
        >
          <Trash2 size={14} />
          Remove step
        </button>
      </fieldset>
    </aside>
  );
}
