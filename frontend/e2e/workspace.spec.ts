import { test, expect, type Page } from "@playwright/test";
async function login(page: Page) {
  await page.goto("/");
  await page.getByLabel("Username", { exact: true }).fill("demo");
  await page.getByLabel("Password", { exact: true }).fill("demo-local-only");
  await page.getByRole("button", { name: "Sign in", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "Your workflows" }),
  ).toBeVisible();
}
async function create(page: Page, name: string, agent = false) {
  await page.getByRole("button", { name: "New workflow", exact: true }).click();
  if (agent)
    await page
      .getByRole("button", { name: "Agent template", exact: true })
      .click();
  await page.getByLabel("Workflow name").fill(name);
  await page
    .getByRole("button", { name: "Save workflow", exact: true })
    .click();
  await expect(page.getByText("Saved · v1", { exact: true })).toBeVisible();
}
async function start(page: Page) {
  await page.getByRole("button", { name: "Run workflow", exact: true }).click();
  await page.getByLabel("Issue subject").fill("Cannot log in");
  await page.getByLabel("Issue description").fill("Password reset failed");
  await page.getByRole("button", { name: "Start run", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "Execution trace" }),
  ).toBeVisible();
  await expect(page.getByTestId("run-status")).toHaveText(
    "WAITING FOR APPROVAL",
    { timeout: 20000 },
  );
}
test("canvas save/reload, immutable versions, explicit approval and live trace", async ({
  page,
}, testInfo) => {
  const errors: string[] = [];
  page.on("pageerror", (error) => errors.push(error.message));
  await login(page);
  await create(page, "Browser approval flow", true);
  await expect(page.locator(".react-flow__node")).toHaveCount(5);
  await page.reload();
  await expect(page.getByLabel("Workflow name")).toHaveValue(
    "Browser approval flow",
  );
  await page.getByLabel("Workflow name").fill("Browser approval flow v2");
  await page
    .getByRole("button", { name: "Save workflow", exact: true })
    .click();
  await expect(page.getByText("Saved · v2", { exact: true })).toBeVisible();
  await page.getByRole("button", { name: "Versions", exact: true }).click();
  await expect(
    page.getByText("Browser approval flow", { exact: true }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Builder", exact: true }).click();
  await start(page);
  await page
    .getByRole("button", { name: "Review approval", exact: true })
    .click();
  await expect(page.getByText("RECORD_RESULT", { exact: true })).toBeVisible();
  await page
    .getByRole("checkbox", { name: "I have reviewed this exact action" })
    .check();
  await page
    .getByRole("button", { name: "Approve action", exact: true })
    .click();
  await page
    .getByRole("button", { name: "View execution", exact: true })
    .click();
  await expect(page.getByTestId("run-status")).toHaveText("SUCCEEDED", {
    timeout: 15000,
  });
  await expect(page.getByText("RUN_SUCCEEDED", { exact: true })).toBeVisible();
  await expect(
    page.getByText("DETERMINISTIC PLANNER", { exact: true }),
  ).toBeVisible();
  const trace = await (
    await page.request.get("/api/runs/" + page.url().split("/").pop())
  ).json();
  expect(trace.plannerCalls).toBe(2);
  expect(trace.toolCalls).toBe(2);
  expect(
    trace.steps.filter(
      (step: { policyStatus: string }) => step.policyStatus === "APPROVED",
    ),
  ).toHaveLength(2);
  await page.screenshot({
    path: testInfo.outputPath("execution.png"),
    fullPage: true,
  });
  await page.getByRole("button", { name: "Sign out", exact: true }).click();
  await expect(
    page.getByRole("button", { name: "Sign in", exact: true }),
  ).toBeVisible();
  await expect(
    page.getByRole("heading", { name: "Execution trace" }),
  ).not.toBeVisible();
  expect(errors).toEqual([]);
});
test("rejects a reviewed action and cancellation prevents future effects", async ({
  page,
}) => {
  await login(page);
  await create(page, "Browser rejection flow");
  await start(page);
  await page
    .getByRole("button", { name: "Review approval", exact: true })
    .click();
  await page
    .getByRole("button", { name: "Reject action", exact: true })
    .click();
  await page
    .getByRole("button", { name: "View execution", exact: true })
    .click();
  await expect(page.getByTestId("run-status")).toHaveText("FAILED");
  await expect(
    page.getByText("APPROVAL_REJECTED", { exact: true }).first(),
  ).toBeVisible();
  await page
    .getByRole("button", { name: "Back to workflow", exact: true })
    .click();
  await start(page);
  await page.getByRole("button", { name: "Cancel run", exact: true }).click();
  await expect(page.getByTestId("run-status")).toHaveText("CANCELLED");
});
test("preserves a conflicted draft and renders untrusted names as text", async ({
  page,
}) => {
  await login(page);
  await create(page, "<img src=x onerror=alert(1)>");
  const workflowId = page.url().split("/").pop()!;
  const changed = await page.request.get("/api/workflows/" + workflowId);
  const workflow = await changed.json();
  const token = await (await page.request.get("/api/auth/csrf")).json();
  await page.request.put("/api/workflows/" + workflowId, {
    headers: { [token.headerName]: token.token },
    data: {
      name: "Other edit",
      description: workflow.description,
      definition: workflow.definition,
      expectedVersion: 1,
    },
  });
  await page.getByLabel("Workflow name").fill("My unsaved draft");
  await page
    .getByRole("button", { name: "Save workflow", exact: true })
    .click();
  await expect(page.getByRole("alert")).toContainText("Your draft is kept");
  await expect(page.getByLabel("Workflow name")).toHaveValue(
    "My unsaved draft",
  );
  await page.getByRole("button", { name: "Versions", exact: true }).click();
  await expect(
    page.getByText("<img src=x onerror=alert(1)>", { exact: true }),
  ).toBeVisible();
  expect(await page.locator('img[src="x"]').count()).toBe(0);
});

test("keyboard layout editing, reconnecting, archive and modal focus behavior", async ({
  page,
}, testInfo) => {
  await page.setViewportSize({ width: 1440, height: 1000 });
  await login(page);
  await create(page, "Editable canvas flow");
  await page.screenshot({
    path: testInfo.outputPath("editor-desktop.png"),
    fullPage: true,
  });
  await page.setViewportSize({ width: 390, height: 844 });
  await page.screenshot({
    path: testInfo.outputPath("editor-mobile.png"),
    fullPage: true,
  });
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= window.innerWidth,
    ),
  ).toBe(true);
  await page.setViewportSize({ width: 1440, height: 1000 });
  const edge = page.getByRole("group", {
    name: "Edge from receive to classify",
    exact: true,
  });
  await edge.focus();
  await edge.press("Enter");
  await expect(edge).toHaveClass(/selected/);
  await edge.press("Delete");
  await expect(page.locator(".react-flow__edge")).toHaveCount(3);
  await page
    .getByText("Connect steps with the keyboard", { exact: true })
    .click();
  await page
    .getByRole("combobox", { name: "Source step", exact: true })
    .selectOption("receive");
  await page
    .getByRole("combobox", { name: "Target step", exact: true })
    .selectOption("classify");
  await page
    .getByRole("button", { name: "Connect steps", exact: true })
    .click();
  await expect(page.locator(".react-flow__edge")).toHaveCount(4);
  await page.getByText("Canvas position", { exact: true }).click();
  await page.getByLabel("X position", { exact: true }).fill("155");
  await page
    .getByRole("button", { name: "Save workflow", exact: true })
    .click();
  await expect(page.getByText("Saved · v2", { exact: true })).toBeVisible();
  await page.reload();
  await page.getByText("Canvas position", { exact: true }).click();
  await expect(page.getByLabel("X position", { exact: true })).toHaveValue(
    "155",
  );
  await page.getByRole("button", { name: "Run workflow", exact: true }).click();
  await expect(page.getByRole("dialog")).toBeVisible();
  await page.keyboard.press("Escape");
  await expect(page.getByRole("dialog")).not.toBeVisible();
  await page.getByRole("button", { name: "Runs", exact: true }).click();
  await expect(
    page.getByText(
      "No runs yet. Start a saved workflow to follow its execution.",
    ),
  ).toBeVisible();
  await page.getByRole("button", { name: "Builder", exact: true }).click();
  await page
    .getByRole("button", { name: "Archive workflow", exact: true })
    .click();
  await page
    .getByRole("dialog")
    .getByRole("button", { name: "Archive workflow", exact: true })
    .click();
  await expect(
    page.getByRole("heading", { name: "Your workflows" }),
  ).toBeVisible();
  await expect(
    page.getByRole("heading", { name: "Editable canvas flow" }),
  ).not.toBeVisible();
});

test("blocks unreviewed approval and hides protected content after session expiry", async ({
  page,
}) => {
  await login(page);
  await create(page, "Explicit review guard");
  await start(page);
  await page
    .getByRole("button", { name: "Review approval", exact: true })
    .click();
  await expect(
    page.getByRole("button", { name: "Approve action", exact: true }),
  ).toBeDisabled();
  const token = await (await page.request.get("/api/auth/csrf")).json();
  await page.request.post("/api/auth/logout", {
    headers: { [token.headerName]: token.token },
  });
  await page
    .getByRole("button", { name: "Refresh inbox", exact: true })
    .click();
  await expect(
    page.getByRole("button", { name: "Sign in", exact: true }),
  ).toBeVisible();
  await expect(page.getByLabel("Frozen action payload")).not.toBeVisible();
});

test("an uncertain start reuses its key and creates one run", async ({
  page,
}) => {
  await login(page);
  await create(page, "Uncertain submission flow");
  const workflowId = page.url().split("/").pop()!;
  let loseResponse = true;
  const keys: string[] = [];
  await page.route("**/api/workflows/*/runs", async (route) => {
    if (route.request().method() !== "POST") return route.continue();
    keys.push(route.request().postDataJSON().idempotencyKey);
    if (!loseResponse) return route.continue();
    loseResponse = false;
    const committed = await route.fetch();
    expect(committed.ok()).toBe(true);
    await route.abort("failed");
  });
  await page.getByRole("button", { name: "Run workflow", exact: true }).click();
  await page.getByLabel("Issue subject").fill("Cannot log in");
  await page.getByLabel("Issue description").fill("Password reset failed");
  await page.getByRole("button", { name: "Start run", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("reuses this submission");
  await page.getByRole("button", { name: "Start run", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "Execution trace" }),
  ).toBeVisible();
  expect(keys).toHaveLength(2);
  expect(keys[1]).toBe(keys[0]);
  const runs = await (
    await page.request.get(`/api/workflows/${workflowId}/runs`)
  ).json();
  expect(runs.total).toBe(1);
  await page.getByRole("button", { name: "Cancel run", exact: true }).click();
  await expect(page.getByTestId("run-status")).toHaveText("CANCELLED");
});

test("a save in progress cannot silently overwrite further editor changes", async ({
  page,
}) => {
  await login(page);
  await create(page, "Save serialization flow");
  let release!: () => void;
  const held = new Promise<void>((resolve) => {
    release = resolve;
  });
  await page.route("**/api/workflows/*", async (route) => {
    if (route.request().method() !== "PUT") return route.continue();
    const response = await route.fetch();
    await held;
    await route.fulfill({ response });
  });
  await page.getByLabel("Workflow name").fill("Saved revision");
  await page
    .getByRole("button", { name: "Save workflow", exact: true })
    .click();
  try {
    await expect(page.getByLabel("Workflow name")).toBeDisabled();
    await expect(page.getByLabel("Step label", { exact: true })).toBeDisabled();
    await expect(
      page.getByRole("button", { name: "Add node", exact: true }),
    ).toBeDisabled();
  } finally {
    release();
  }
  await expect(page.getByText("Saved · v2", { exact: true })).toBeVisible();
  await expect(page.getByLabel("Workflow name")).toBeEnabled();
});

test("saves a GitHub ticket destination and fails closed with a disabled connector", async ({
  page,
}, testInfo) => {
  await login(page);
  await page.getByRole("button", { name: "New workflow", exact: true }).click();
  await page
    .getByRole("article", { name: "Action node: Record result", exact: true })
    .click();
  await page
    .getByRole("combobox", { name: "Action", exact: true })
    .selectOption("CREATE_TICKET");
  await expect(
    page.getByLabel("GitHub repository", { exact: true }),
  ).toHaveValue("Dhruv-Sharma29/test");
  await expect(page.getByText(/Connector disabled/)).toBeVisible();
  await page.getByLabel("Workflow name").fill("Approved GitHub ticket");
  await page
    .getByRole("button", { name: "Save workflow", exact: true })
    .click();
  await expect(page.getByText("Saved · v1", { exact: true })).toBeVisible();
  const workflowId = page.url().split("/").pop()!;
  const saved = await (
    await page.request.get("/api/workflows/" + workflowId)
  ).json();
  expect(saved.definition.nodes[4].ticket.repository).toBe(
    "Dhruv-Sharma29/test",
  );
  await page.reload();
  await page
    .getByRole("article", { name: "Action node: Record result", exact: true })
    .click();
  await expect(
    page.getByLabel("GitHub repository", { exact: true }),
  ).toHaveValue("Dhruv-Sharma29/test");
  await page.screenshot({
    path: testInfo.outputPath("github-ticket-editor.png"),
    fullPage: true,
  });
  await page.getByRole("button", { name: "Run workflow", exact: true }).click();
  await expect(
    page.getByText(/This workflow creates an external GitHub issue/),
  ).toBeVisible();
  await page.getByLabel("Issue subject").fill("Cannot log in");
  await page.getByLabel("Issue description").fill("Password reset failed");
  await page.getByRole("button", { name: "Start run", exact: true }).click();
  await expect(page.getByTestId("run-status")).toHaveText("FAILED", {
    timeout: 20000,
  });
  await expect(page.getByRole("alert")).toContainText(
    "TICKET_CONNECTOR_DISABLED",
  );
  const trace = await (
    await page.request.get("/api/runs/" + page.url().split("/").pop())
  ).json();
  expect(trace.tickets).toEqual([]);
  expect(trace.approvals).toEqual([]);
  await page
    .getByRole("button", { name: "Tool registry", exact: true })
    .click();
  await expect(page.locator(".connector-status")).toContainText("DISABLED");
});
