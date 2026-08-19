package dev.rabauer.bahndemo.workflow;

import dev.rabauer.bahndemo.client.DbApiClient;
import dev.rabauer.bahndemo.service.AdvisorService;
import dev.rabauer.bahndemo.workflow.node.*;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * Spring configuration that assembles the delay-handling langgraph4j {@link StateGraph}.
 *
 * <p>Graph shape:
 * <pre>
 *   START → analyzeDelay → advisor → [interrupt] → humanDecision → applyDecision → END
 * </pre>
 *
 * <p>{@code interruptBefore("humanDecision")} tells the compiled graph to checkpoint and halt
 * immediately before {@code humanDecision} executes. {@code WorkflowOrchestrationService} then
 * injects the operator's choice into state and calls {@code graph.stream(GraphInput.resume(), config)}
 * to continue from that checkpoint.
 *
 * <p>The {@link MemorySaver} is exposed as a bean so {@code WorkflowOrchestrationService} can call
 * {@code graph.getState(config)} when needed (e.g. for tests).
 */
@Configuration
public class DelayWorkflowConfig {

    @Bean
    public MemorySaver delayWorkflowMemorySaver() {
        return new MemorySaver();
    }

    @Bean
    public CompiledGraph<DelayWorkflowState> delayWorkflowGraph(
            DbApiClient dbApiClient,
            AdvisorService advisorService,
            MemorySaver delayWorkflowMemorySaver) throws Exception {

        var analyzeDelayNode  = new AnalyzeDelayNode(dbApiClient);
        var advisorNode       = new AdvisorNode(advisorService);
        var humanDecisionNode = new HumanDecisionNode();
        var applyDecisionNode = new ApplyDecisionNode();

        var graph = new StateGraph<>(DelayWorkflowState.SCHEMA, DelayWorkflowState::new)
                .addNode("analyzeDelay",   node_async(analyzeDelayNode::apply))
                .addNode("advisor",        node_async(advisorNode::apply))
                .addNode("humanDecision",  node_async(humanDecisionNode::apply))
                .addNode("applyDecision",  node_async(applyDecisionNode::apply))
                .addEdge(START,            "analyzeDelay")
                .addEdge("analyzeDelay",   "advisor")
                .addEdge("advisor",        "humanDecision")
                .addEdge("humanDecision",  "applyDecision")
                .addEdge("applyDecision",  END);

        var compileConfig = CompileConfig.builder()
                .checkpointSaver(delayWorkflowMemorySaver)
                .interruptBefore("humanDecision")
                .build();

        return graph.compile(compileConfig);
    }
}
