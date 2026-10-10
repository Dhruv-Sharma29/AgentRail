import { test, expect } from "@playwright/test";

test("console theme covers login, editable workflows, menus and dialogs", async ({
  page,
}, testInfo) => {
  await page.emulateMedia({ colorScheme: "light", reducedMotion: "reduce" });
  await page.goto("/");
  await expect(page.locator("html")).toHaveAttribute("data-theme", "console");
  await expect(page.locator("body")).toHaveCSS(
    "background-color",
    "rgb(25, 31, 33)",
  );
  await expect(
    page.getByRole("button", { name: "Sign in", exact: true }),
  ).toHaveCSS("background-color", "rgb(154, 235, 187)");
  await page.getByLabel("Username", { exact: true }).fill("demo");
  await expect(page.getByLabel("Username", { exact: true })).toHaveCSS(
    "outline-color",
    "rgb(154, 235, 187)",
  );
  await page.getByLabel("Password", { exact: true }).fill("demo-local-only");
  await page.getByRole("button", { name: "Sign in", exact: true }).click();
  await page.getByRole("button", { name: "New workflow", exact: true }).click();
  const builder = page.getByRole("region", { name: "Workflow builder" });
  await expect(builder).toBeVisible();
  await expect(builder.locator(".react-flow__minimap")).toHaveCSS(
    "background-color",
    "rgb(16, 22, 24)",
  );
  await expect(
    builder.getByRole("article", {
      name: "Trigger node: Receive issue",
      exact: true,
    }),
  ).toHaveCSS("background-color", "rgb(22, 34, 30)");
  await expect(
    builder.getByRole("article", {
      name: "Human review node: Review result",
      exact: true,
    }),
  ).toHaveCSS("background-color", "rgb(41, 37, 28)");
  await builder.getByRole("button", { name: "Add node", exact: true }).click();
  await expect(page.getByRole("menu")).toHaveCSS(
    "background-color",
    "rgb(19, 25, 27)",
  );
  await page.getByRole("menu").press("Escape");
  await page.getByLabel("Workflow name").fill("Console theme check");
  await page
    .getByRole("button", { name: "Save workflow", exact: true })
    .click();
  await expect(page.getByText("Saved · v1", { exact: true })).toBeVisible();
  await page.screenshot({
    path: testInfo.outputPath("console-builder.png"),
    fullPage: true,
  });
  await page.getByRole("button", { name: "Run workflow", exact: true }).click();
  await expect(page.getByRole("dialog")).toHaveCSS(
    "background-color",
    "rgb(19, 25, 27)",
  );
  await expect(page.getByRole("dialog")).toHaveCSS(
    "color",
    "rgb(237, 242, 240)",
  );
  await page
    .getByRole("button", { name: "Close run dialog", exact: true })
    .click();
  await page
    .getByRole("button", { name: "Tool registry", exact: true })
    .click();
  await expect(page.locator(".tool-card").first()).toHaveCSS(
    "background-color",
    "rgb(19, 25, 27)",
  );
  await page.getByRole("button", { name: "Approvals", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "Approval inbox", exact: true }),
  ).toHaveCSS("color", "rgb(237, 242, 240)");
  await page.setViewportSize({ width: 390, height: 844 });
  expect(
    await page.evaluate(() => document.documentElement.scrollWidth),
  ).toBeLessThanOrEqual(390);
});
