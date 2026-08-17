package dev.rabauer.bahndemo.workflow;

import dev.rabauer.bahndemo.workflow.node.AdvisorNode;
import dev.rabauer.bahndemo.workflow.node.AnalyzeDelayNode;
import dev.rabauer.bahndemo.workflow.node.ApplyDecisionNode;
import dev.rabauer.bahndemo.workflow.node.HumanDecisionNode;
import org.bsc.langgraph4j.*;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * Wires the delay-handling graph: analyzeDelay -> advisor -> [pause here] -> humanDecision -> applyDecision -> END.
 *
 * The pause uses langgraph4j's documented static interruptBefore(nodeName) + CompiledGraph#updateState /
 * GraphInput#resume() idiom (see WorkflowOrchestrationService), not the dynamic interrupt() function -
 * that function isn't present in langgraph4j-core 1.8.24.
 */
@Configuration
public class DelayWorkflowConfig {

    public static final String NODE_ANALYZE_DELAY = "analyzeDelay";
    public static final String NODE_ADVISOR = "advisor";
    public static final String NODE_HUMAN_DECISION = "humanDecision";
    public static final String NODE_APPLY_DECISION = "applyDecision";

    @Bean
    public BaseCheckpointSaver checkpointSaver() {
        // In-memory only - fine for a single-process demo. Swap for a DB-backed saver
        // (langgraph4j-postgres-saver etc.) if the workflow needs to survive a restart.
        return new MemorySaver();
    }

    @Bean
    public CompiledGraph<DelayWorkflowState> delayWorkflowGraph(
            AnalyzeDelayNode analyzeDelayNode,
            AdvisorNode advisorNode,
            HumanDecisionNode humanDecisionNode,
            ApplyDecisionNode applyDecisionNode,
            BaseCheckpointSaver checkpointSaver) throws GraphStateException {

        StateGraph<DelayWorkflowState> graph = new StateGraph<>(DelayWorkflowState.SCHEMA, DelayWorkflowState::new)
                .addNode(NODE_ANALYZE_DELAY, node_async(analyzeDelayNode))
                .addNode(NODE_ADVISOR, node_async(advisorNode))
                .addNode(NODE_HUMAN_DECISION, node_async(humanDecisionNode))
                .addNode(NODE_APPLY_DECISION, node_async(applyDecisionNode))
                .addEdge(START, NODE_ANALYZE_DELAY)
                .addEdge(NODE_ANALYZE_DELAY, NODE_ADVISOR)
                .addEdge(NODE_ADVISOR, NODE_HUMAN_DECISION)
                .addEdge(NODE_HUMAN_DECISION, NODE_APPLY_DECISION)
                .addEdge(NODE_APPLY_DECISION, END);

        CompileConfig compileConfig = CompileConfig.builder()
                .checkpointSaver(checkpointSaver)
                .interruptBefore(NODE_HUMAN_DECISION)
                .releaseThread(true)
                .build();
        // Print the graph in Mermaid format for debugging / documentation purposes.
        final var mermaidGraph = graph.getGraph(GraphRepresentation.Type.MERMAID, "DelayWorkflowGraph", false);
        System.out.printf("""
                Mermaid representation of the delay-handling graph:
                ===================================================
                %s
                ===================================================
                """, mermaidGraph.content());
        return graph.compile(compileConfig);
    }
}
