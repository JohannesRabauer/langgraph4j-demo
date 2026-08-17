# Demo plan: Deutsche Bahn delay monitoring + langgraph4j human-in-the-loop

Prepared for the YouTube live-coding stream: https://youtube.com/live/bLN1iiTlcLU

This branch (`stream/pre-langgraph4j`) is the starting point for the stream. The app works
end-to-end today - search, monitoring, alternative-finding, the advisor's recommendation, the
human-in-the-loop pause/resume, the UI - but the workflow orchestration
(`service.WorkflowOrchestrationService`) is a **hand-rolled stand-in**: plain sequential method
calls plus a `ConcurrentHashMap` for the "paused" state, with zero langgraph4j involved. The stream
is a refactor: replace that stand-in with a real langgraph4j `StateGraph` - nodes, checkpointing,
`interruptBefore`/resume - and show what it buys over the primitive version. See "What's left for
the stream" below.

## What this app demonstrates

A Spring Boot + Vaadin app where a user searches Deutsche Bahn connections, picks one to monitor,
and - when a delay is detected - a workflow kicks in: it looks for alternatives, asks an advisor
(rule-based, or an LLM via Ollama) to recommend one, then **pauses and waits for a human decision**
before finalizing an outcome. Today that workflow is a plain Java method chain. By the end of the
stream it's a **langgraph4j** `StateGraph`, with `CompiledGraph`, checkpointing, and
interrupt/resume mechanics as the centerpiece.

## Stack

- Spring Boot 4.1.0 + Vaadin 25.2.6 (Flow) + Java 21. Vaadin 25 (not 24) because Vaadin 24.x moved
  into paid Extended Maintenance and refuses to start without a license - re-check
  https://vaadin.com/docs/latest/compatibility if that's changed again by stream time.
- `org.bsc.langgraph4j:langgraph4j-core:1.8.24` already in `pom.xml` for the workflow engine - not
  used by any code yet, that's the stream's job.
- [v6.db.transport.rest](https://v6.db.transport.rest/) for Deutsche Bahn data - free, unofficial,
  no API key, wraps `db-vendo-client`. Rate limit: 100 requests/minute. `DbApiClient` falls back to a
  ~20-station offline dataset (spanning Germany, Italy, Austria, Switzerland, France, the
  Netherlands) on any failure (timeout, 503, ...), since that live API has been observed down for
  hours at a time - worth mentioning live in case search looks "wrong": it's the fallback, not a bug.
- LLM advisor via **Spring AI's Ollama integration** (`spring-ai-starter-model-ollama`, model
  `llama3.2`, GPU-accelerated in `docker-compose.yml`), gated behind `bahn.advisor.enabled` so the
  app builds and runs standalone with zero external dependencies.

## Architecture (as it stands today, pre-stream)

```
search (client.DbApiClient) -> pick a journey -> monitor it (service.DelayMonitorService,
@Scheduled polling or the "Simulate a delay" button) -> threshold breach ->
service.WorkflowOrchestrationService.startWorkflow(journey), on workflowExecutor:
  findAlternatives(journey) via dbApiClient.searchJourneys(...)
  -> advisorService.recommend(...)
  -> build a DelayWorkflowState, stash it in an in-memory Map<journeyId, state>
  -> push it to the browser (Vaadin server push) as the "paused, your decision is needed" state
-> user picks a decision -> resumeWithDecision(journeyId, decision, index), on workflowExecutor:
  look up the stashed state -> resolve the outcome -> push the final state to the browser
```

`DelayWorkflowState` is a plain immutable value class (builder-based) - no langgraph4j `AgentState`,
no channels, no reducers. `WorkflowOrchestrationService`'s Javadoc spells out exactly what a real
graph buys over this: a durable per-journey checkpoint instead of a process-local map, a formal node
graph instead of inlined method calls, and `interruptBefore`/`resume` instead of hand-managing pause
state.

Package layout (base package `dev.rabauer.bahndemo`):

- `client` - `DbApiClient` + DTOs for v6.db.transport.rest. **Fully implemented**, including the
  offline fallback. DTOs (`JourneyDto`, `LegDto`, `LineDto`, `LocationDto`) already implement
  `Serializable` - needed once langgraph4j's `MemorySaver` checkpoints state via `ObjectOutputStream`.
- `workflow` - `DelayWorkflowState` (plain state snapshot, **fully implemented** as a primitive
  value type - gets rebuilt to extend langgraph4j's `AgentState` during the stream) and
  `HumanDecision` enum. `DelayWorkflowConfig` and `workflow.node.*` **don't exist yet** - that's the
  stream's job, see below.
- `service` - `AdvisorService` + `RuleBasedAdvisorService`/`LlmAdvisorService` (**fully implemented**:
  both return an `AdvisorRecommendation(recommendedIndex, rationale)` pointing at a specific
  alternative, not just free text; `LlmAdvisorService` falls back to the rule-based one on any Ollama
  failure), `MonitoredJourney`/`MonitoredJourneyRegistry`, `DelayMonitorService` (polling + the
  "Simulate a delay" demo trigger, **fully implemented**), `WorkflowOrchestrationService` (**the
  primitive stand-in described above - gets rebuilt on langgraph4j during the stream**).
- `ui` - `MainView` + `JourneySearchPanel`/`JourneyResultsGrid`/`MonitoringPanel`, **fully
  implemented** - including the paused-decision UI (a heading, the advisor's rationale, an "Accept
  suggested" button naming the specific connection, one button per alternative with the recommended
  one marked, "Keep waiting"). It already works end to end against the primitive orchestration today,
  and needs no changes once the graph is wired in - `MonitoringPanel.render(DelayWorkflowState)` only
  depends on `DelayWorkflowState`'s accessors, not on how the state got produced.
- `config` - `AppShellConfig` (`@Push` + the Aura theme `@StyleSheet` - required together, see its
  Javadoc), `RestClientConfig` (3s timeout), `AsyncConfig` (`workflowExecutor` bean - already used by
  the primitive orchestration, reusable as the graph's executor later), `BahnDemoProperties`.

## What's left for the stream

This is the actual content: replacing `WorkflowOrchestrationService`'s hand-rolled orchestration with
langgraph4j. In order:

1. **`DelayWorkflowState`** - change it to extend langgraph4j's `AgentState` (backed by
   `Map<String, Object>`) instead of being a plain builder-based class, keeping the same accessor
   method signatures so `MonitoringPanel` doesn't need to change.
2. **`workflow.node.*`** - extract the logic currently inlined in
   `WorkflowOrchestrationService.startWorkflow`/`resumeWithDecision` into four `NodeAction<DelayWorkflowState>`
   implementations: `AnalyzeDelayNode` (the `findAlternatives` logic), `AdvisorNode` (the
   `advisorService.recommend(...)` call), `HumanDecisionNode` (validates `state.humanDecision()` is
   present), `ApplyDecisionNode` (the `describeOutcome`/`describeSwitch` logic).
3. **`DelayWorkflowConfig`** - build the `StateGraph`: four nodes, edges
   `START -> analyzeDelay -> advisor -> humanDecision -> applyDecision -> END`, a `MemorySaver`
   checkpointer bean, and `CompileConfig.interruptBefore("humanDecision")`. Pass a schema to the
   `StateGraph` constructor with `Channels.appender(ArrayList::new)` for the `"log"` key - without an
   explicit schema, each node's log line silently *overwrites* the previous one instead of
   accumulating into a timeline, a good live "gotcha" to show (the primitive version sidesteps this
   entirely by just doing `new ArrayList<>(paused.log())` by hand).
4. **Rewire `WorkflowOrchestrationService`** to drive the compiled graph (`graph.stream(GraphInput.args(...))`,
   `graph.updateState(...)` + `graph.stream(GraphInput.resume(), config)`) instead of the
   `pausedByJourneyId` map, keeping the same public `startWorkflow(journey)` /
   `resumeWithDecision(journeyId, decision, index)` signatures so `DelayMonitorService` and
   `MainView` don't need to change either.
5. **Smoke-test the happy path with `invoke()`** before adding the interrupt, then confirm the
   interrupt actually halts (log-verify the graph's `outcome` stays empty after
   `analyzeDelay`/`advisor` run).
6. **The "wow" moment** - trigger "Simulate a delay" in the running UI, watch it reactively show the
   paused decision (already fully rendered, working the same way it does today), click through it,
   watch it resume and finish - now backed by a real, checkpointed graph instead of a `Map`.

Once this compiles, add a human-in-the-loop test (`DelayWorkflowHumanInTheLoopTest` on the main
implementation branch is a reference for the shape): drive
`WorkflowOrchestrationService.startWorkflow(...)`, await the pause via `graph.getState(config)`,
resume with a decision, assert the outcome.

## Running it

Locally, no external dependencies (rule-based advisor only):

```
mvn spring-boot:run
```

Dockerized, with Ollama (GPU-accelerated) and the LLM-backed advisor:

```
docker compose up
```
