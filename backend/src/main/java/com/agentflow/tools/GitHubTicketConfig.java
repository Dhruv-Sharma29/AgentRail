package com.agentflow.tools;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public final class GitHubTicketConfig {
    private final boolean enabled;
    private final String repository;
    private final String token;
    public GitHubTicketConfig(@Value("${agentflow.github.enabled:false}") boolean enabled,
            @Value("${agentflow.github.repository:Dhruv-Sharma29/test}") String repository,
            @Value("${agentflow.github.token:}") String token) {
        this.enabled = enabled; this.repository = repository; this.token = token;
    }
    public String repository() { return repository; }
    String token() { return token; }
    public boolean ready() {
        return enabled && repository != null && repository.matches(TicketPolicy.REPOSITORY_PATTERN)
                && token != null && !token.isBlank() && token.length() <= 512
                && token.chars().allMatch(c -> c >= 33 && c <= 126);
    }
}
