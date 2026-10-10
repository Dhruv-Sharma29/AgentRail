package com.agentflow.execution;

import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.EnableScheduling;
import java.time.Clock;

@Configuration
@EnableScheduling
class ExecutionConfiguration {
    @Bean Clock executionClock() { return Clock.systemUTC(); }
}
