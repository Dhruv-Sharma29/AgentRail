import { afterEach, describe, expect, it, vi } from "vitest";
import { Api, ApiError } from "./api";
const csrf = (token: string) => ({
  token,
  headerName: "X-CSRF-TOKEN",
  parameterName: "_csrf",
});
function responses(...items: { status?: number; body?: unknown }[]) {
  const fetcher = vi.fn();
  for (const item of items)
    fetcher.mockResolvedValueOnce(
      new Response(item.body === undefined ? null : JSON.stringify(item.body), {
        status: item.status ?? 200,
      }),
    );
  vi.stubGlobal("fetch", fetcher);
  return fetcher;
}
afterEach(() => vi.unstubAllGlobals());
describe("session API boundary", () => {
  it("refreshes CSRF after login and sends the new token on mutations", async () => {
    const fetcher = responses(
      { body: csrf("before") },
      { status: 204 },
      { body: csrf("after") },
      { body: { username: "alice", roles: ["ROLE_USER"] } },
      { body: { id: "saved" } },
    );
    const api = new Api();
    await api.login("alice", "test-password");
    await api.request("/api/workflows", "POST", { name: "Draft" });
    expect(fetcher.mock.calls.map((c) => c[0])).toEqual([
      "/api/auth/csrf",
      "/api/auth/login",
      "/api/auth/csrf",
      "/api/auth/me",
      "/api/workflows",
    ]);
    expect(fetcher.mock.calls[1][1].body.toString()).toContain("_csrf=before");
    expect(fetcher.mock.calls[4][1].headers["X-CSRF-TOKEN"]).toBe("after");
    expect(fetcher.mock.calls[4][1].credentials).toBe("same-origin");
  });
  it("does not retry a rejected mutation or expose provider exception bodies", async () => {
    const fetcher = responses(
      { body: csrf("token") },
      { status: 403, body: { secret: "private-provider-body" } },
    );
    await expect(
      new Api().request("/api/approvals/id/decision", "POST", {
        decision: "APPROVE",
      }),
    ).rejects.toThrow("Permission denied");
    expect(fetcher).toHaveBeenCalledTimes(2);
  });
  it("clears session state and reports protected-route expiry", async () => {
    responses({ status: 401 });
    const api = new Api();
    const expired = vi.fn();
    api.onUnauthorized = expired;
    await expect(api.request("/api/workflows")).rejects.toBeInstanceOf(
      ApiError,
    );
    expect(expired).toHaveBeenCalledOnce();
  });
  it("ignores an old session failure after a new successful sign-in", async () => {
    let resolveOld!: (value: Response) => void;
    const fetcher = vi.fn().mockImplementationOnce(
      () =>
        new Promise<Response>((resolve) => {
          resolveOld = resolve;
        }),
    );
    for (const item of [
      { body: csrf("before"), status: 200 },
      { body: undefined, status: 204 },
      { body: csrf("after"), status: 200 },
      { body: { username: "bob", roles: ["ROLE_USER"] }, status: 200 },
    ])
      fetcher.mockResolvedValueOnce(
        new Response(
          item.body === undefined ? null : JSON.stringify(item.body),
          { status: item.status },
        ),
      );
    vi.stubGlobal("fetch", fetcher);
    const api = new Api();
    const expired = vi.fn();
    api.onUnauthorized = expired;
    const oldRequest = api.request("/api/workflows").catch(() => undefined);
    await api.login("bob", "test-password");
    resolveOld(new Response(null, { status: 401 }));
    await oldRequest;
    expect(expired).not.toHaveBeenCalled();
  });
  it("refuses credentialed requests outside the fixed API origin", async () => {
    const fetcher = responses();
    await expect(
      new Api().request("https://example.com/steal"),
    ).rejects.toThrow("Invalid API path");
    expect(fetcher).not.toHaveBeenCalled();
  });
});
