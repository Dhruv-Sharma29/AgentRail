package com.agentflow.tools;

import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/tools")
class ToolController {
    private final ToolRegistry registry;
    private final GitHubTicketConfig github;
    ToolController(ToolRegistry registry,GitHubTicketConfig github) { this.registry = registry; this.github=github; }
    record Connector(String repository,boolean ready) {}
    @GetMapping("/github") Connector github() { return new Connector(github.repository(),github.ready()); }
    @GetMapping List<ToolRegistry.Definition> list() { return registry.definitions(); }
}
