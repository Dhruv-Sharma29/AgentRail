package com.agentflow.tools;

import com.agentflow.workflow.WorkflowDefinition.Operation;
import com.fasterxml.jackson.databind.*;
import jakarta.validation.Validator;
import jakarta.validation.constraints.*;
import org.springframework.stereotype.Component;
import java.util.*;

/** Code owns tool capabilities and permissions; workflow/model data cannot register tools. */
@Component
public class ToolRegistry {
    private static final Set<String> NAMES = Set.of("classify_issue", "search_documentation");
    private final ObjectMapper json;
    private final Validator validator;
    public ToolRegistry(ObjectMapper json, Validator validator) { this.json = json; this.validator = validator; }
    public static Set<String> names() { return NAMES; }

    public record Context(String role, String subject, String body, String category) {}
    public record ValidatedCall(String tool, Operation operation, String arguments) {}
    public record Definition(String name, String description, String risk, Set<String> roles, Map<String, Object> argumentSchema) {}
    public record ClassifyArguments(@NotBlank @Size(max = 200) String subject, @NotBlank @Size(max = 4000) String body) {}
    public record SearchArguments(@NotNull @Pattern(regexp = "ACCOUNT|BILLING|GENERAL") String category) {}

    public ValidatedCall validate(String tool, String arguments, Context context) {
        if (!NAMES.contains(tool)) throw new Denied("UNKNOWN_TOOL");
        if (!"USER".equals(context.role())) throw new Denied("TOOL_ROLE_DENIED");
        try {
            JsonNode input = json.readTree(arguments);
            Set<String> fields = tool.equals("classify_issue") ? Set.of("subject", "body") : Set.of("category");
            if (input == null || !input.isObject() || input.size() != fields.size()) throw new Denied("INVALID_TOOL_ARGUMENTS");
            for (String field : fields) {
                if (!input.has(field) || !input.get(field).isTextual()) throw new Denied("INVALID_TOOL_ARGUMENTS");
            }
            Operation operation;
            if (tool.equals("classify_issue")) {
                var args = json.treeToValue(input, ClassifyArguments.class);
                if (!validator.validate(args).isEmpty()) throw new Denied("INVALID_TOOL_ARGUMENTS");
                if (!args.subject().equals(context.subject()) || !args.body().equals(context.body())) throw new Denied("TOOL_CONTEXT_MISMATCH");
                operation = Operation.CLASSIFY_ISSUE;
            } else {
                var args = json.treeToValue(input, SearchArguments.class);
                if (!validator.validate(args).isEmpty()) throw new Denied("INVALID_TOOL_ARGUMENTS");
                if (!args.category().equals(context.category())) throw new Denied("TOOL_CONTEXT_MISMATCH");
                operation = Operation.SEARCH_DOCUMENTATION;
            }
            return new ValidatedCall(tool, operation, json.writeValueAsString(input));
        } catch (Denied denied) { throw denied; }
        catch (Exception invalid) { throw new Denied("INVALID_TOOL_ARGUMENTS"); }
    }

    public List<Definition> definitions() {
        return List.of(new Definition("classify_issue", "Classify the submitted issue using local rules", "READ_ONLY", Set.of("USER"),
                    schema(Map.of("subject", Map.of("type", "string", "minLength", 1, "maxLength", 200),
                                  "body", Map.of("type", "string", "minLength", 1, "maxLength", 4000)))),
                new Definition("search_documentation", "Retrieve one entry from the built-in documentation catalog", "READ_ONLY", Set.of("USER"),
                    schema(Map.of("category", Map.of("type", "string", "enum", List.of("ACCOUNT", "BILLING", "GENERAL"))))),
                new Definition("create_ticket", "Create a GitHub issue only as a final action after exact-payload approval; unavailable to AI steps", "EXTERNAL_WRITE", Set.of("USER"),
                    schema(Map.of("repository",Map.of("type","string","pattern",TicketPolicy.REPOSITORY_PATTERN),
                        "title",Map.of("type","string","minLength",1,"maxLength",200),
                        "body",Map.of("type","string","minLength",1,"maxLength",8000)))));
    }
    private static Map<String, Object> schema(Map<String, Object> properties) {
        return Map.of("type", "object", "additionalProperties", false, "required", properties.keySet().stream().sorted().toList(), "properties", properties);
    }
    public static class Denied extends RuntimeException {
        public Denied(String code) { super(code); }
    }
}
