package com.agentflow.agent;

import com.agentflow.tools.ToolRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;

@Configuration
@ConditionalOnProperty(name="agentflow.planner.provider",havingValue="openai")
class OpenAiPlannerConfiguration {
    @Bean LivePlanner livePlanner(@Value("${OPENAI_API_KEY:}") String key, ToolRegistry registry, ObjectMapper json) {
        if (key.isBlank()) throw new IllegalStateException("OpenAI planner requires OPENAI_API_KEY");
        return new OpenAiPlanner(OpenAiPlanner.model(key,OpenAiPlanner.boundedHttp()),registry,json);
    }
}
