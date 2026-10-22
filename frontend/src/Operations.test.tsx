import { afterEach, expect, it, vi } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import { api } from "./api";
import { Operations } from "./Operations";
import { normalizeRoute } from "./navigation";

afterEach(() => vi.restoreAllMocks());
const snapshot = {
  capturedAt: "2026-10-10T14:00:00Z",
  totalRuns: 12,
  pendingRuns: 1,
  runningRuns: 2,
  waitingRuns: 3,
  succeededRuns: 4,
  failedRuns: 1,
  cancelledRuns: 1,
  pendingApprovals: 3,
  overdueApprovals: 1,
  retryingSteps: 2,
  unknownTickets: 1,
  sendingTickets: 1,
};
it("shows owner counts and sends review navigation without executing actions", async () => {
  const request = vi.spyOn(api, "request").mockResolvedValue(snapshot);
  const navigate = vi.fn();
  render(<Operations navigate={navigate} />);
  await screen.findByRole("heading", { name: "Operations" });
  expect(await screen.findByText("3 approvals need review")).toBeVisible();
  expect(screen.getByText("1 approval past its deadline")).toBeVisible();
  expect(
    screen.getByText("1 ticket outcome needs reconciliation"),
  ).toBeVisible();
  expect(screen.getByTestId("operations-failed")).toHaveTextContent("1");
  expect(screen.getByTestId("operations-retries")).toHaveTextContent("2");
  fireEvent.click(screen.getByRole("button", { name: "Open approvals" }));
  expect(navigate).toHaveBeenCalledWith("/approvals");
  expect(request).toHaveBeenCalledWith(
    "/api/operations",
    "GET",
    undefined,
    expect.any(AbortSignal),
  );
  expect(request).toHaveBeenCalledTimes(1);
  expect(normalizeRoute("/operations")).toBe("/operations");
});
it("marks retained counts stale after refresh fails instead of reporting healthy zeros", async () => {
  vi.spyOn(api, "request")
    .mockResolvedValueOnce(snapshot)
    .mockRejectedValueOnce(new Error("Connection unavailable"));
  render(<Operations navigate={() => {}} />);
  await screen.findByText("3 approvals need review");
  fireEvent.click(screen.getByRole("button", { name: "Refresh operations" }));
  expect(await screen.findByRole("alert")).toHaveTextContent(
    "Connection failed. Check the server and try again.",
  );
  expect(screen.getByText(/Counts are stale/)).toBeVisible();
  expect(screen.getByTestId("operations-failed")).toHaveTextContent("1");
});
it("distinguishes an empty workspace from a failed first load", async () => {
  vi.spyOn(api, "request").mockRejectedValue(
    new Error("Connection unavailable"),
  );
  render(<Operations navigate={() => {}} />);
  await screen.findByRole("alert");
  expect(screen.queryByTestId("operations-failed")).not.toBeInTheDocument();
  expect(screen.queryByText("No runs yet")).not.toBeInTheDocument();
});
