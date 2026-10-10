import { defineConfig } from "@playwright/test";
export default defineConfig({
  testDir: "./e2e",
  fullyParallel: false,
  workers: 1,
  timeout: 45000,
  use: {
    baseURL: "http://127.0.0.1:5174",
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
  },
  webServer: [
    {
      command:
        "java -jar ../backend/target/agentflow-0.1.0-SNAPSHOT.jar --spring.profiles.active=dev --server.port=18085 --spring.datasource.url='jdbc:h2:mem:agentflow-browser;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH' --agentflow.planner.provider=deterministic",
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
