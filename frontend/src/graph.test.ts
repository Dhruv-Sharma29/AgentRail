import { describe, expect, it } from "vitest";
import {
  supportTemplate,
  toCanvas,
  fromCanvas,
  validate,
  connect,
} from "./graph";
describe("workflow editor boundary", () => {
  it("round-trips approved settings and positions without React Flow internals", () => {
    const definition = supportTemplate(true);
    const canvas = toCanvas(definition);
    canvas.nodes[0].selected = true;
    canvas.nodes[0].position = { x: 225.25, y: -60 };
    const saved = fromCanvas(canvas.nodes, canvas.edges);
    expect(saved.nodes[0].position).toEqual({ x: 225.25, y: -60 });
    expect(saved.nodes[1].agent?.allowedTools).toEqual(["classify_issue"]);
    expect(saved.nodes[3].approval?.timeoutSeconds).toBe(3600);
    expect(saved.nodes[0]).not.toHaveProperty("selected");
    expect(saved.nodes[0]).not.toHaveProperty("data");
  });
  it("does not silently turn an old unsupported node into a runnable action", () => {
    const definition = supportTemplate();
    definition.nodes[1] = { id: "old", label: "Legacy", type: "CONDITION" };
    const canvas = toCanvas(definition);
    expect(fromCanvas(canvas.nodes, canvas.edges).nodes[1].type).toBe(
      "CONDITION",
    );
    expect(validate(definition)).toContain(
      "Only one connected linear chain can run.",
    );
  });
  it("rejects cycles, disconnected graphs and approval bypasses", () => {
    const definition = supportTemplate();
    expect(validate(definition)).toEqual([]);
    definition.edges.push({ source: "record", target: "receive" });
    expect(validate(definition).length).toBeGreaterThan(0);
    const disconnected = supportTemplate();
    disconnected.edges.pop();
    expect(validate(disconnected).length).toBeGreaterThan(0);
    const bypass = supportTemplate();
    bypass.nodes[4].operation = "CLASSIFY_ISSUE";
    expect(validate(bypass)).toContain(
      "Approval must immediately precede Record result.",
    );
  });
  it("refuses duplicate, self and branching connections", () => {
    const edges = [{ source: "a", target: "b" }];
    expect(connect(edges, "a", "a")).toEqual(edges);
    expect(connect(edges, "a", "b")).toEqual(edges);
    expect(connect(edges, "a", "c")).toEqual(edges);
    expect(connect(edges, "b", "c")).toHaveLength(2);
  });
});
