import { afterEach, expect, it, vi } from "vitest";
import {
  cleanup,
  render,
  screen,
  fireEvent,
  waitFor,
} from "@testing-library/react";
import { TicketResult } from "./TicketResult";
import { api } from "./api";
import type { Ticket } from "./types";
afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});
const ticket: Ticket = {
  nodeId: "ticket",
  status: "SUCCEEDED",
  createdAt: "",
  deadline: "",
  finishedAt: null,
  issueNumber: 42,
  issueUrl: "https://github.com/Dhruv-Sharma29/test/issues/42",
  errorCode: null,
};
it("shows a trusted receipt after cancellation and refuses a hostile result URL", () => {
  const { rerender } = render(
    <TicketResult runId="run" ticket={ticket} cancelled reload={() => {}} />,
  );
  expect(
    screen
      .getByRole("link", { name: "View GitHub issue #42" })
      .getAttribute("href"),
  ).toBe(ticket.issueUrl);
  expect(screen.getByText(/Cancellation did not undo/)).toBeTruthy();
  rerender(
    <TicketResult
      runId="run"
      ticket={{ ...ticket, issueUrl: "https://evil.test/steal" }}
      cancelled
      reload={() => {}}
    />,
  );
  expect(screen.queryByRole("link")).toBeNull();
});
it("reconciliation is explicit and sends only an issue number through the session API", async () => {
  const request = vi.spyOn(api, "request").mockResolvedValue({});
  const reload = vi.fn();
  render(
    <TicketResult
      runId="run"
      ticket={{
        ...ticket,
        status: "UNKNOWN",
        issueNumber: null,
        issueUrl: null,
      }}
      cancelled={false}
      reload={reload}
    />,
  );
  expect(request).not.toHaveBeenCalled();
  fireEvent.change(screen.getByLabelText("Existing GitHub issue number"), {
    target: { value: "42" },
  });
  fireEvent.click(
    screen.getByRole("button", { name: "Verify existing issue" }),
  );
  await waitFor(() => expect(reload).toHaveBeenCalledOnce());
  expect(request).toHaveBeenCalledExactlyOnceWith(
    "/api/runs/run/ticket/reconcile",
    "POST",
    { issueNumber: 42 },
  );
});
