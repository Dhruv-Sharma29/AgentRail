// Read-only recording of an existing workflow and completed run.
// Requires the local dev backend/frontend and frontend Playwright dependency.
import { chromium } from "../frontend/node_modules/playwright-core/index.mjs";
import fs from "node:fs/promises";
import { performance } from "node:perf_hooks";
const base = "http://127.0.0.1:5173";
const folder = new URL("../artifacts/readme-demo/", import.meta.url);
const browser = await chromium.launch({ headless: true });
try {
  const login = await browser.newContext();
  const auth = await login.newPage();
  await auth.goto(base);
  await auth.getByLabel("Username", { exact: true }).fill("demo");
  await auth.getByLabel("Password", { exact: true }).fill("demo-local-only");
  await auth.getByRole("button", { name: "Sign in", exact: true }).click();
  await auth.getByRole("heading", { name: "Your workflows" }).waitFor();
  const state = await login.storageState();
  const context = await browser.newContext({
    storageState: state,
    viewport: { width: 1440, height: 900 },
    deviceScaleFactor: 1,
    recordVideo: {
      dir: decodeURIComponent(folder.pathname),
      size: { width: 1440, height: 900 },
    },
  });
  // Prevent this read-only recording from submitting any external action.
  await context.route("**/api/**", (route) =>
    ["GET", "HEAD"].includes(route.request().method())
      ? route.continue()
      : route.abort(),
  );
  const page = await context.newPage();
  const began = performance.now();
  const scenes = [];
  const hold = (ms) => new Promise((resolve) => setTimeout(resolve, ms)); // Intentional reading time, not a readiness check.
  const mark = async (name, seconds, action) => {
    scenes.push({
      name,
      start: (performance.now() - began) / 1000,
      duration: seconds,
    });
    await page.screenshot({
      path: decodeURIComponent(new URL(name + ".png", folder).pathname),
    });
    await hold(seconds * 500);
    if (action) await action();
    await hold(seconds * 500);
  };
  await page.goto(base + "/#/workflows/dda87b8e-98be-4977-811d-b7f4672e4feb");
  await page
    .getByRole("heading", {
      name: "Approved GitHub support ticket",
      exact: true,
    })
    .waitFor();
  await page.locator(".react-flow__node").first().waitFor();
  await page.getByText("Saved · v1", { exact: true }).waitFor();
  await hold(600);
  await mark("01-workflow", 6, () =>
    page
      .locator(".react-flow__node")
      .filter({ hasText: "Propose classification" })
      .click(),
  );
  await page.goto(base + "/#/runs/bd62dec4-3140-4732-946f-2407bbba4516");
  await page
    .getByTestId("run-status")
    .filter({ hasText: "SUCCEEDED" })
    .waitFor();
  await mark("02-execution", 6, () =>
    page.getByText("Step output", { exact: true }).first().click(),
  );
  await page.getByText("Validated proposal", { exact: true }).first().click();
  await page
    .getByText("Validated proposal", { exact: true })
    .first()
    .evaluate((el) => el.scrollIntoView({ block: "center" }));
  await mark("03-tool-validation", 6);
  await page.goto(base + "/#/approvals/e1c7a667-bb2b-4f88-b5e1-8cca8f7e474c");
  await page
    .getByRole("heading", { name: "Cannot log in", exact: true })
    .waitFor();
  await mark("04-human-approval", 6);
  await page.goto(base + "/#/runs/bd62dec4-3140-4732-946f-2407bbba4516");
  await page
    .getByTestId("run-status")
    .filter({ hasText: "SUCCEEDED" })
    .waitFor();
  await page
    .getByRole("heading", { name: "Audit trail", exact: true })
    .evaluate((el) => el.scrollIntoView({ block: "start" }));
  await page.mouse.wheel(0, -100);
  await hold(250);
  await mark("05-audit-trail", 6, () =>
    page.getByText("RUN_SUCCEEDED", { exact: true }).scrollIntoViewIfNeeded(),
  );
  const video = page.video();
  await context.close();
  const path = await video.path();
  await fs.writeFile(
    new URL("recording.json", folder),
    JSON.stringify(
      {
        path,
        scenes,
        description:
          "Read-only walkthrough of an existing successful run; no workflow executed or approval changed.",
      },
      null,
      2,
    ),
  );
  console.log("Recorded actual app walkthrough:", path);
} finally {
  await browser.close();
}
