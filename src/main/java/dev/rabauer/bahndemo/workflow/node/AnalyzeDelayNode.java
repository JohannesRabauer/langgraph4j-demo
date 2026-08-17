package dev.rabauer.bahndemo.workflow.node;

import dev.rabauer.bahndemo.client.DbApiClient;
import dev.rabauer.bahndemo.workflow.DelayWorkflowState;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Re-searches alternative connections for the delayed journey's route.
 *
 * TODO(stream): call dbApiClient.searchJourneys(...) using the original journey's origin/destination,
 * filter out the original journey, and put the result under "alternatives". Append a human-readable
 * summary line to "log".
 */
@Component
public class AnalyzeDelayNode implements NodeAction<DelayWorkflowState> {

    private final DbApiClient dbApiClient;

    public AnalyzeDelayNode(DbApiClient dbApiClient) {
        this.dbApiClient = dbApiClient;
    }

    @Override
    public Map<String, Object> apply(DelayWorkflowState state) throws Exception {
        return Map.of();
    }
}
