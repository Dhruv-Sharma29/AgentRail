import { test, expect } from "@playwright/test";

test("pipeline preview pauses, respects reduced motion and fits mobile", async ({
  page,
}, testInfo) => {
  const errors: string[] = [];
  const mutations: string[] = [];
  page.on("pageerror", (error) => errors.push(error.message));
  page.on("request", (request) => {
    if (
      request.url().includes("/api/") &&
      !["GET", "HEAD"].includes(request.method())
    )
      mutations.push(request.url());
  });
  await page.goto("/");
  const preview = page.getByRole("region", {
    name: "AgentRail workflow preview",
  });
  await expect(preview).toBeVisible();
  await expect(
    preview.getByText("Preview only · no actions run"),
  ).toBeVisible();
  const log = preview.getByTestId("pipeline-message");
  await expect(log).toContainText("Received");
  await expect(log).toContainText("proposes", { timeout: 10000 });
  await preview.getByRole("button", { name: "Pause preview" }).click();
  const pausedMessage = await log.textContent();
  await expect(preview.locator("animateMotion")).toHaveCount(0);
  // This wait deliberately crosses the preview interval to verify it really paused.
  await page.waitForTimeout(3000);
  await expect(log).toHaveText(pausedMessage!);
  await preview.getByRole("button", { name: "Play preview" }).click();
  await expect(preview.locator("animateMotion")).not.toHaveCount(0);
  await page.screenshot({ path: testInfo.outputPath("pipeline-desktop.png") });

  await page.emulateMedia({ reducedMotion: "reduce" });
  await expect(
    preview.getByRole("button", { name: "Play preview" }),
  ).toBeDisabled();
  await expect(preview.locator("animateMotion")).toHaveCount(0);
  await page.reload();
  await expect(
    preview.getByRole("button", { name: "Play preview" }),
  ).toBeVisible();
  await expect(preview.locator("animateMotion")).toHaveCount(0);
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(
    page.getByRole("button", { name: "Sign in", exact: true }),
  ).toBeInViewport();
  await preview.scrollIntoViewIfNeeded();
  await expect(
    preview.getByRole("list").getByText("Human approval", { exact: true }),
  ).toBeVisible();
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= window.innerWidth,
    ),
  ).toBe(true);
  await page.screenshot({
    path: testInfo.outputPath("pipeline-mobile.png"),
    fullPage: true,
  });
  expect(errors).toEqual([]);
  expect(mutations).toEqual([]);
});
