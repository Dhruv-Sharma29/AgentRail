package com.agentflow.tools;

import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/tools")
class ToolController {
    private final ToolRegistry registry;
    ToolController(ToolRegistry registry) { this.registry = registry; }
    @GetMapping List<ToolRegistry.Definition> list() { return registry.definitions(); }
}
