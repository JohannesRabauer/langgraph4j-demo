# Demo plan: Deutsche Bahn delay monitoring + langgraph4j human-in-the-loop

Prepared for the YouTube live-coding stream: https://youtube.com/live/bLN1iiTlcLU

**This branch (`feature/langgraph4j-implementation`) is the finished langgraph4j implementation** -
branched off `main` (the pre-stream starting point, where the workflow is a hand-rolled stand-in
with zero langgraph4j involved) with the real `StateGraph`, nodes, checkpointing, and
`interruptBefore`/resume wired back in. It's kept separate from `main` on purpose: `main` stays as
the "before" state for the stream, and this branch is the answer key to merge into `main` afterwards
(or to fall back on live if something goes sideways). See `main`'s `DEMO_PLAN.md` for the
step-by-step build order this branch already completed.

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
- [api.transitous.org](https://transitous.org/) for journey data - a public MOTIS instance
  aggregating GTFS/GTFS-RT feeds across Europe (including Deutsche Bahn's own DELFI feed), free, no
  API key. Previously this used `v6.db.transport.rest` (`db-vendo-client`), but Deutsche Bahn's own
  backend started blocking that whole ecosystem via TLS fingerprinting in 2026 - see
  [DbApiClient](src/main/java/dev/rabauer/bahndemo/client/DbApiClient.java) and the
  [upstream issue](https://github.com/public-transport/db-vendo-client/issues/46). `DbApiClient`
  falls back to a ~20-station offline dataset (spanning Germany, Italy, Austria, Switzerland,
  France, the Netherlands) on any failure (timeout, 503, ...) - worth mentioning live in case
  search looks "wrong": it's the fallback, not a bug.

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

The human-in-the-loop pause uses langgraph4j's documented **static `interruptBefore(nodeName)`**
plus `CompiledGraph#updateState` / `GraphInput#resume()` - not a dynamic `interrupt()` function,
which isn't present in langgraph4j-core 1.8.24. `DelayWorkflowState.SCHEMA` gives `"log"` an
appender channel (`Channels.appender(ArrayList::new)`) so each node's log line accumulates into a
timeline instead of overwriting the previous one - the default (no schema) behavior for every other
key is overwrite, which is what you want for e.g. `outcome`.

Package layout (base package `dev.rabauer.bahndemo`):

- `client` - `DbApiClient` + DTOs for api.transitous.org (MOTIS). DTOs (`JourneyDto`, `LegDto`,
  `LineDto`, `LocationDto`) implement `Serializable`, required because langgraph4j's `MemorySaver`
  checkpointer serializes the whole graph state via `ObjectOutputStream` on every step.
- `workflow` - `DelayWorkflowState` (extends langgraph4j's `AgentState`), `DelayWorkflowConfig`
  (graph wiring: nodes, edges, `MemorySaver` bean, `interruptBefore`), `HumanDecision` enum,
  `workflow.node.*` (the four `NodeAction<DelayWorkflowState>` implementations).
- `service` - `AdvisorService` + `RuleBasedAdvisorService`/`LlmAdvisorService` (both return an
  `AdvisorRecommendation(recommendedIndex, rationale)` pointing at a specific alternative;
  `LlmAdvisorService` falls back to the rule-based one on any Ollama failure),
  `MonitoredJourney`/`MonitoredJourneyRegistry`, `DelayMonitorService` (polling + the "Simulate a
  delay" demo trigger), `WorkflowOrchestrationService` (the Vaadin ↔ langgraph4j bridge - drives
  the compiled graph via `CompletableFuture`-based streaming instead of blocking a thread per run).
- `ui` - `MainView` + `JourneySearchPanel`/`JourneyResultsGrid`/`MonitoringPanel`, including the
  paused-decision UI. `MonitoringPanel.render(DelayWorkflowState)` only depends on the state's
  accessors, so it stayed unchanged when the real graph replaced the primitive orchestration.
- `config` - `AppShellConfig` (`@Push` + the Aura theme `@StyleSheet` - required together),
  `RestClientConfig` (3s timeout), `AsyncConfig` (`workflowExecutor` bean), `BahnDemoProperties`.


## Notable things found while implementing

- langgraph4j's `MemorySaver` checkpointer serializes graph state via `ObjectOutputStream` on every
  step - every DTO stored in `DelayWorkflowState` has to implement `Serializable`, or checkpointing
  throws `NotSerializableException`.
- `log` accumulation needs an explicit schema (see `DelayWorkflowState.SCHEMA`) - without it, each
  node's log line silently overwrites the previous one instead of accumulating.
- `JourneySearchPanel`, `JourneyResultsGrid`, and `MonitoringPanel` must be Spring
  **prototype**-scoped. As plain singleton `@Component` beans they broke on a second browser
  tab/reload with "Can't move a node from one state tree to another" - a Vaadin component can only
  ever belong to one UI's state tree.
- `RuleBasedAdvisorService` and `LlmAdvisorService` are both active `AdvisorService` beans once
  `bahn.advisor.enabled=true`; `LlmAdvisorService` needs `@Primary` so `AdvisorNode`'s single-bean
  injection point resolves without ambiguity.
- `DbApiClient`'s `RestClient` needs an explicit short connect/read timeout (`RestClientConfig`, 3s) -
  without it, a live `v6.db.transport.rest` outage blocks each search for ~10s before falling back to
  offline data.

## Running it

Locally, no external dependencies (rule-based advisor only):

```
mvn spring-boot:run
```

Dockerized, with Ollama (GPU-accelerated) and the LLM-backed advisor:

```
docker compose up
```
