# Demo plan: Deutsche Bahn delay monitoring + langgraph4j human-in-the-loop

Prepared for the YouTube live-coding stream: https://youtube.com/live/bLN1iiTlcLU

This branch (`stream/pre-langgraph4j`) is the starting point for the stream: everything around
langgraph4j is built and working - search, monitoring, the advisor's recommendation logic, the UI,
Docker/Ollama/GPU - so the stream can focus entirely on **langgraph4j itself**: the graph, its state,
the four nodes, and the human-in-the-loop pause/resume. See "What's left for the stream" below.

## What this app demonstrates

A Spring Boot + Vaadin app where a user searches Deutsche Bahn connections, picks one to monitor,
and - when a delay is detected - a **langgraph4j** workflow kicks in: it looks for alternatives,
asks an advisor (rule-based, or an LLM via Ollama) to recommend one, then **pauses and waits for a
human decision** before finalizing an outcome. langgraph4j's `StateGraph`, checkpointing, and
interrupt/resume mechanics are the centerpiece.

## Stack

- Spring Boot 4.1.0 + Vaadin 25.2.6 (Flow) + Java 21. Vaadin 25 (not 24) because Vaadin 24.x moved
  into paid Extended Maintenance and refuses to start without a license - re-check
  https://vaadin.com/docs/latest/compatibility if that's changed again by stream time.
- `org.bsc.langgraph4j:langgraph4j-core:1.8.24` for the workflow engine.
- [v6.db.transport.rest](https://v6.db.transport.rest/) for Deutsche Bahn data - free, unofficial,
  no API key, wraps `db-vendo-client`. Rate limit: 100 requests/minute. `DbApiClient` falls back to a
  ~20-station offline dataset (spanning Germany, Italy, Austria, Switzerland, France, the
  Netherlands) on any failure (timeout, 503, ...), since that live API has been observed down for
  hours at a time - worth mentioning live in case search looks "wrong": it's the fallback, not a bug.
- LLM advisor via **Spring AI's Ollama integration** (`spring-ai-starter-model-ollama`, model
  `llama3.2`, GPU-accelerated in `docker-compose.yml`), gated behind `bahn.advisor.enabled` so the
  app builds and runs standalone with zero external dependencies.

## Architecture

```
search (client.DbApiClient) -> pick a journey -> monitor it (service.DelayMonitorService,
@Scheduled polling or the "Simulate a delay" button) -> threshold breach ->
service.WorkflowOrchestrationService starts a langgraph4j run (workflow.DelayWorkflowConfig) ->
analyzeDelay -> advisor -> [graph halts: interruptBefore("humanDecision")] ->
push paused state to the browser (Vaadin server push) -> user picks a decision ->
graph.updateState(...) + graph.stream(GraphInput.resume(), config) ->
humanDecision -> applyDecision -> END -> push final outcome to the browser
```

The human-in-the-loop pause should use langgraph4j's documented **static `interruptBefore(nodeName)`**
plus `CompiledGraph#updateState` / `GraphInput#resume()` - not a dynamic `interrupt()` function, which
isn't present in langgraph4j-core 1.8.24.

Package layout (base package `dev.rabauer.bahndemo`):

- `client` - `DbApiClient` + DTOs for v6.db.transport.rest. **Fully implemented**, including the
  offline fallback. DTOs (`JourneyDto`, `LegDto`, `LineDto`, `LocationDto`) implement `Serializable` -
  needed later for langgraph4j's `MemorySaver`, which checkpoints state via `ObjectOutputStream`.
- `workflow` - `DelayWorkflowState` (the graph's state - accessors **pre-built**, including
  `advisorRecommendedIndex()`), `DelayWorkflowConfig` (graph wiring - **TODO(stream)**),
  `HumanDecision` enum, `workflow.node.*` (the four `NodeAction` implementations - **TODO(stream)**,
  see each class's Javadoc for exactly what to fill in).
- `service` - `AdvisorService` + `RuleBasedAdvisorService`/`LlmAdvisorService` (**fully implemented**:
  both return an `AdvisorRecommendation(recommendedIndex, rationale)` pointing at a specific
  alternative, not just free text; `LlmAdvisorService` falls back to the rule-based one on any Ollama
  failure), `MonitoredJourney`/`MonitoredJourneyRegistry`, `DelayMonitorService` (polling + the
  "Simulate a delay" demo trigger, **fully implemented**), `WorkflowOrchestrationService` (the
  Vaadin↔langgraph4j bridge, **fully implemented**).
- `ui` - `MainView` + `JourneySearchPanel`/`JourneyResultsGrid`/`MonitoringPanel`, **fully
  implemented** - including the paused-decision UI (a heading, the advisor's rationale, an "Accept
  suggested" button naming the specific connection, one button per alternative with the recommended
  one marked, "Keep waiting"). It renders correctly against an empty/stub `DelayWorkflowState` today;
  once the graph nodes are implemented, it should "just light up" with no UI changes needed.
- `config` - `AppShellConfig` (`@Push` + the Aura theme `@StyleSheet` - required together, see its
  Javadoc), `RestClientConfig` (3s timeout), `AsyncConfig`, `BahnDemoProperties`.

## What's left for the stream

This is the actual content: implementing langgraph4j. In order:

1. **`DelayWorkflowConfig`** - build the `StateGraph`: four nodes, edges
   `START -> analyzeDelay -> advisor -> humanDecision -> applyDecision -> END`, a `MemorySaver`
   checkpointer bean, and `CompileConfig.interruptBefore("humanDecision")`. Also pass a schema to the
   `StateGraph` constructor with `Channels.appender(ArrayList::new)` for the `"log"` key - without an
   explicit schema, each node's log line silently *overwrites* the previous one instead of
   accumulating into a timeline, which is a good live "gotcha" to show.
2. **`AnalyzeDelayNode`** - call `dbApiClient.searchJourneys(...)` using the original journey's
   origin/destination, filter out the original by `refreshToken`, put the result under
   `"alternatives"`, append a log line.
3. **`AdvisorNode`** - call `advisorService.recommend(...)`, which returns an
   `AdvisorRecommendation(recommendedIndex, rationale)` - put `recommendedIndex` under
   `"advisorRecommendedIndex"` (when present) and `rationale` under `"advisorRecommendation"`.
4. **Smoke-test the happy path with `invoke()`** before adding the interrupt, then confirm the
   interrupt actually halts (log-verify `outcome` stays empty after `analyzeDelay`/`advisor` run).
5. **`HumanDecisionNode`** - validate `state.humanDecision()` is present, append a log line.
6. **`ApplyDecisionNode`** - branch on `HumanDecision`: `ACCEPT_SUGGESTED` and `PICK_ALTERNATIVE`
   both resolve an `alternatives()` index (from `advisorRecommendedIndex()` / `selectedAlternativeIndex()`
   respectively) to a description and an outcome message; `KEEP_WAITING` just describes that
   monitoring continues. Sets `"outcome"` - the signal `WorkflowOrchestrationService`/`MonitoringPanel`
   already key off of.
7. **The "wow" moment** - trigger "Simulate a delay" in the running UI, watch it reactively show the
   paused decision (already fully rendered), click through it, watch it resume and finish.

Once this compiles, add back a human-in-the-loop test (there was one on the main implementation
branch, `DelayWorkflowHumanInTheLoopTest`, removed here since there's nothing to test yet): drive
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
