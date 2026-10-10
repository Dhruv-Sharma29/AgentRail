package com.agentflow.execution;

import com.agentflow.workflow.GraphValidator;
import com.agentflow.workflow.WorkflowDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.util.List;
import static com.agentflow.workflow.WorkflowDefinition.*;
import static org.assertj.core.api.Assertions.*;

class ExecutionPlanTest {
    private final ExecutionPlan plan = new ExecutionPlan(new GraphValidator());
    private final Node trigger = new Node("start", NodeType.TRIGGER, "Receive");

    @Test void ordersNodesByConnectionsRatherThanArrayOrder() {
        var action = new Node("end", NodeType.ACTION, "Classify", Operation.CLASSIFY_ISSUE);
        assertThat(plan.validate(new WorkflowDefinition(List.of(action, trigger), List.of(new Edge("start", "end")))))
                .extracting(Node::id).containsExactly("start", "end");
    }
    @ParameterizedTest @EnumSource(value = NodeType.class, names = {"AI_STEP", "CONDITION", "APPROVAL"})
    void refusesUnsupportedNodesInsteadOfSkippingThem(NodeType type) {
        var node = new Node("end", type, "Unsupported");
        assertThatIllegalArgumentException().isThrownBy(() -> plan.validate(
                new WorkflowDefinition(List.of(trigger, node), List.of(new Edge("start", "end")))));
    }
    @Test void rejectsBranchesBeforeExecution() {
        var a = new Node("a", NodeType.ACTION, "A", Operation.CLASSIFY_ISSUE);
        var b = new Node("b", NodeType.ACTION, "B", Operation.CLASSIFY_ISSUE);
        assertThatIllegalArgumentException().isThrownBy(() -> plan.validate(new WorkflowDefinition(
                List.of(trigger, a, b), List.of(new Edge("start", "a"), new Edge("start", "b")))));
    }
    @Test void requiresExplicitActionOperationAndValidTriggerOperation() {
        var node = new Node("end", NodeType.ACTION, "CLASSIFY_ISSUE");
        assertThatIllegalArgumentException().isThrownBy(() -> plan.validate(
                new WorkflowDefinition(List.of(trigger, node), List.of(new Edge("start", "end")))));
        assertThatIllegalArgumentException().isThrownBy(() -> plan.validate(new WorkflowDefinition(
                List.of(new Node("start", NodeType.TRIGGER, "Receive", Operation.RECORD_RESULT)), List.of())));
    }
    @Test void rejectsMalformedGraphBeforeOrdering() {
        assertThatIllegalArgumentException().isThrownBy(() -> plan.validate(new WorkflowDefinition(List.of(trigger), List.of(new Edge("start", "missing")))));
    }
    @Test void approvalRequiresBoundedSettingsAndAnImmediateResultAction() {
        var record=new Node("record",NodeType.ACTION,"Record",Operation.RECORD_RESULT);
        var valid=new Node("review",NodeType.APPROVAL,"Review",null,null,new ApprovalSettings(3600));
        var edges=List.of(new Edge("start","review"),new Edge("review","record"));
        assertThat(plan.validate(new WorkflowDefinition(List.of(trigger,valid,record),edges))).hasSize(3);
        var unbounded=new Node("review",NodeType.APPROVAL,"Review",null,null,new ApprovalSettings(86401));
        assertThatIllegalArgumentException().isThrownBy(() -> plan.validate(new WorkflowDefinition(List.of(trigger,unbounded,record),edges)));
        var wrong=new Node("record",NodeType.ACTION,"Classify",Operation.CLASSIFY_ISSUE);
        assertThatIllegalArgumentException().isThrownBy(() -> plan.validate(new WorkflowDefinition(List.of(trigger,valid,wrong),edges)));
        var misplaced=new Node("record",NodeType.ACTION,"Record",Operation.RECORD_RESULT,null,new ApprovalSettings(3600));
        assertThatIllegalArgumentException().isThrownBy(() -> plan.validate(new WorkflowDefinition(List.of(trigger,misplaced),List.of(new Edge("start","record")))));
    }

    @Test void ticketRequiresFinalImmediateApprovalAndCannotBeAnAgentTool() {
        var ticket=new Node("ticket",NodeType.ACTION,"Create",Operation.CREATE_TICKET,null,null,null,new TicketSettings("Dhruv-Sharma29/test"));
        var review=new Node("review",NodeType.APPROVAL,"Review",null,null,new ApprovalSettings(3600));
        assertThat(plan.validate(new WorkflowDefinition(List.of(trigger,review,ticket),List.of(new Edge("start","review"),new Edge("review","ticket"))))).hasSize(3);
        assertThatIllegalArgumentException().isThrownBy(() -> plan.validate(new WorkflowDefinition(List.of(trigger,ticket),List.of(new Edge("start","ticket")))));
        var after=new Node("after",NodeType.ACTION,"After",Operation.RECORD_RESULT);
        assertThatIllegalArgumentException().isThrownBy(() -> plan.validate(new WorkflowDefinition(List.of(trigger,review,ticket,after),List.of(new Edge("start","review"),new Edge("review","ticket"),new Edge("ticket","after")))));
        var agent=new Node("agent",NodeType.AI_STEP,"Agent",null,new AgentSettings(AgentGoal.CLASSIFY_ISSUE,List.of("create_ticket"),1));
        assertThatIllegalArgumentException().isThrownBy(() -> plan.validate(new WorkflowDefinition(List.of(trigger,agent),List.of(new Edge("start","agent")))));
    }
}
