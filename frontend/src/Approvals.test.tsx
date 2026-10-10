import { afterEach, expect, it, vi } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { Approvals } from "./Approvals";
import { api } from "./api";
afterEach(() => vi.restoreAllMocks());
it("shows exact external fields as text and submits only the reviewed digest", async () => {
  const approval = {
    id: "approval",
    runId: "run",
    nodeId: "review",
    targetNodeId: "ticket",
    requiredRole: "USER",
    status: "PENDING",
    payloadDigest: "a".repeat(64),
    createdAt: "2026-10-10T00:00:00Z",
    expiresAt: "2099-10-10T01:00:00Z",
    decidedAt: null,
    decidedBy: null,
    payload: {
      operation: "CREATE_TICKET",
      input: { subject: "Cannot log in" },
      ticket: {
        repository: "Dhruv-Sharma29/test",
        title: "<img src=x onerror=alert(1)>",
        body: "Exact issue body\n<!-- agentflow-ticket:fixture -->",
      },
    },
  };
  const request = vi
    .spyOn(api, "request")
    .mockImplementation(async (path) =>
      path.includes("?page=")
        ? { items: [approval], total: 1, page: 0, size: 12 }
        : approval,
    );
  const { container } = render(<Approvals id="approval" navigate={() => {}} />);
  await screen.findByText("GITHUB ISSUE · EXTERNAL ACTION");
  expect(screen.getByLabelText("Exact GitHub issue body")).toHaveTextContent(
    "<!-- agentflow-ticket:fixture -->",
  );
  expect(screen.getByText("<img src=x onerror=alert(1)>")).toBeInTheDocument();
  expect(container.querySelector("img")).toBeNull();
  const approve = screen.getByRole("button", { name: "Approve action" });
  expect(approve).toBeDisabled();
  fireEvent.click(
    screen.getByRole("checkbox", { name: "I have reviewed this exact action" }),
  );
  fireEvent.click(approve);
  await waitFor(() =>
    expect(request).toHaveBeenCalledWith(
      "/api/approvals/approval/decision",
      "POST",
      { decision: "APPROVE", payloadDigest: approval.payloadDigest },
    ),
  );
});
