import { defineConfig } from "@playwright/test";
const deployed = process.env.AGENTFLOW_E2E_BASE_URL;
if (deployed && !/^http:\/\/127\.0\.0\.1:\d+$/.test(deployed))
  throw new Error("Container tests require an explicit loopback origin");
export default defineConfig({
  testDir: "./e2e",
  fullyParallel: false,
  workers: 1,
  timeout: 45000,
  use: {
    baseURL: deployed ?? "http://127.0.0.1:5174",
    video: deployed
      ? { mode: "on", size: { width: 1280, height: 720 } }
      : "off",
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
  },
  webServer: deployed
    ? []
    : [
        {
          command:
            "java -jar ../backend/target/agentflow-0.1.0-SNAPSHOT.jar --spring.profiles.active=dev --server.port=18085 --spring.datasource.url='jdbc:h2:mem:agentflow-browser;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH' --agentflow.planner.provider=deterministic --agentflow.github.enabled=false",
          url: "http://127.0.0.1:18085/actuator/health",
          timeout: 45000,
          reuseExistingServer: false,
        },
        {
          command: "npm run dev -- --port 5174",
          env: { AGENTFLOW_API_TARGET: "http://127.0.0.1:18085" },
          url: "http://127.0.0.1:5174",
          timeout: 30000,
          reuseExistingServer: false,
        },
      ],
});
