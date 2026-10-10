import { test, expect } from "@playwright/test";
import type { Workflow } from "../src/types";

test("workflow block uses real nodes, accessible additions and durable drag positions", async ({
  page,
}, testInfo) => {
  const errors: string[] = [];
  page.on("pageerror", (error) => errors.push(error.message));
  await page.goto("/");
  await page.getByLabel("Username", { exact: true }).fill("demo");
  await page.getByLabel("Password", { exact: true }).fill("demo-local-only");
  await page.getByRole("button", { name: "Sign in", exact: true }).click();
  await page.getByRole("button", { name: "New workflow", exact: true }).click();
  const builder = page.getByRole("region", { name: "Workflow builder" });
  await expect(builder).toBeVisible();
  const zoomIcon = builder
    .getByRole("button", { name: "Zoom In", exact: true })
    .locator("svg");
  expect((await zoomIcon.boundingBox())?.width).toBeGreaterThanOrEqual(10);
  await expect(builder.getByText("5 nodes", { exact: true })).toBeVisible();
  await expect(
    builder.getByText("4 connections", { exact: true }),
  ).toBeVisible();
  await builder
    .getByRole("button", { name: "Add node", exact: true })
    .press("Enter");
  await expect(
    page.getByRole("menuitem", { name: "Trigger", exact: true }),
  ).toBeDisabled();
  await expect(
    page.getByRole("menuitem", { name: "Condition", exact: true }),
  ).toHaveCount(0);
  await page
    .getByRole("menuitem", { name: "Action", exact: true })
    .press("Enter");
  await expect(builder.getByText("6 nodes", { exact: true })).toBeVisible();
  await expect(
    builder.getByText("4 connections", { exact: true }),
  ).toBeVisible();
  await expect(builder.getByText("Unconnected", { exact: true })).toBeVisible();
  await page
    .getByRole("button", { name: "Save workflow", exact: true })
    .click();
  await expect(page.getByRole("alert")).toContainText(
    "Only one connected linear chain can run",
  );
  await page.getByRole("button", { name: "Remove step", exact: true }).click();
  await expect(builder.getByText("5 nodes", { exact: true })).toBeVisible();
  await page.getByLabel("Workflow name").fill("Workflow block drag check");
  await page
    .getByRole("button", { name: "Save workflow", exact: true })
    .click();
  await expect(page.getByText("Saved · v1", { exact: true })).toBeVisible();
  const workflowUrl = "/api/workflows/" + page.url().split("/").pop();
  const before: Workflow = await (await page.request.get(workflowUrl)).json();
  const node = page.locator('.react-flow__node[data-id="classify"]');
  const box = await node.boundingBox();
  if (!box) throw new Error("The node was not rendered");
  await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2);
  await page.mouse.down();
  await page.mouse.move(
    box.x + box.width / 2 + 60,
    box.y + box.height / 2 + 80,
    { steps: 12 },
  );
  await page.mouse.up();
  await expect(
    page.getByText("Unsaved changes", { exact: true }),
  ).toBeVisible();
  await page
    .getByRole("button", { name: "Save workflow", exact: true })
    .click();
  await expect(page.getByText("Saved · v2", { exact: true })).toBeVisible();
  const after: Workflow = await (await page.request.get(workflowUrl)).json();
  const position = (workflow: Workflow) => {
    const value = workflow.definition.nodes.find(
      (node) => node.id === "classify",
    )?.position;
    if (!value) throw new Error("The saved node position is missing");
    return value;
  };
  expect(position(after)).not.toEqual(position(before));
  await page.reload();
  await page.locator('.react-flow__node[data-id="classify"]').click();
  await page.getByText("Canvas position", { exact: true }).click();
  await expect(page.getByLabel("X position")).toHaveValue(
    String(Math.round(position(after).x)),
  );
  await expect(page.getByLabel("Y position")).toHaveValue(
    String(Math.round(position(after).y)),
  );
  await builder
    .getByRole("button", { name: "Add node", exact: true })
    .press("Enter");
  await page.getByRole("menu").press("Escape");
  await expect(
    builder.getByRole("button", { name: "Add node", exact: true }),
  ).toBeFocused();
  await page.screenshot({
    path: testInfo.outputPath("workflow-block.png"),
    fullPage: true,
  });
  await page.setViewportSize({ width: 390, height: 844 });
  await page.emulateMedia({ reducedMotion: "reduce" });
  await builder.getByRole("button", { name: "Add node", exact: true }).click();
  const menu = page.getByRole("menu");
  await expect(menu).toHaveCSS("animation-name", "none");
  const menuBox = await menu.boundingBox();
  if (!menuBox) throw new Error("The mobile node menu was not rendered");
  expect(menuBox.x).toBeGreaterThanOrEqual(0);
  expect(menuBox.x + menuBox.width).toBeLessThanOrEqual(390);
  expect(
    await page.evaluate(() => document.documentElement.scrollWidth),
  ).toBeLessThanOrEqual(390);
  await page.screenshot({
    path: testInfo.outputPath("workflow-block-mobile.png"),
    fullPage: true,
  });
  await menu.press("Escape");
  expect(errors).toEqual([]);
});
