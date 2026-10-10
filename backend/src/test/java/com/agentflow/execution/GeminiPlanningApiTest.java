package com.agentflow.execution;

import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties={"agentflow.planner.provider=gemini", "agentflow.planner.model=gemini-3.5-flash-lite"})
class GeminiPlanningApiTest extends LivePlanningApiTest {
    @Override String expectedMode() { return "GEMINI_PLANNER"; }
}
