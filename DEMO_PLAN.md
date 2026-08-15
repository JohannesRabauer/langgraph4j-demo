# Demo plan: Deutsche Bahn delay monitoring + langgraph4j human-in-the-loop

Prepared for the YouTube live-coding stream: https://youtube.com/live/bLN1iiTlcLU

## What this app demonstrates

A Spring Boot + Vaadin app where a user searches Deutsche Bahn connections, picks one to monitor,
and - when a delay is detected - a **langgraph4j** workflow kicks in: it looks for alternatives,
optionally asks an LLM to recommend one, then **pauses and waits for a human decision** before
finalizing an outcome. langgraph4j's `StateGraph`, checkpointing, and interrupt/resume mechanics are
the centerpiece.

## Stack

- Spring Boot 4.1.0 + Vaadin 25.2.6 (Flow) + Java 21. Originally planned as Vaadin 24.10.9 + Spring
  Boot 3.5.16 + Java 17 for "boring and stable," but verified live: Vaadin 24 has since moved
  entirely into paid Extended Maintenance and refuses to start - even in dev mode, even on
  `localhost` - without a Vaadin Pro/Prime license (`LicenseException: Unable to validate the
  license`). Vaadin 25 is the current free/community major version, hence the newer baseline. If
  you hit a license wall again by the time of the stream, re-check
  https://vaadin.com/docs/latest/compatibility for whichever version is *currently* free.
- `org.bsc.langgraph4j:langgraph4j-core:1.8.24` for the workflow engine.
- [v6.db.transport.rest](https://v6.db.transport.rest/) for Deutsche Bahn data - free, unofficial,
  no API key, wraps `db-vendo-client`. Rate limit: 100 requests/minute. `DbApiClient` falls back to a
  small hardcoded offline dataset on any failure (timeout, 503, ...) so the demo stays reliable
  regardless of that API's availability - observed flaky in practice while building this.
- Optional LLM advisor node via **Spring AI's Ollama integration** (`spring-ai-starter-model-ollama`,
  model `llama3.2`), gated behind `bahn.advisor.enabled` so the app builds and runs with zero external
  dependencies by default. `docker-compose.yml` runs Ollama alongside the app and enables it.

## Architecture

```
search (client.DbApiClient) -> pick a journey -> monitor it (service.DelayMonitorService,
@Scheduled polling or the "Simulate delay" button) -> threshold breach ->
service.WorkflowOrchestrationService starts a langgraph4j run (workflow.DelayWorkflowConfig) ->
analyzeDelay -> advisor -> [graph halts: interruptBefore("humanDecision")] ->
push paused state to the browser (Vaadin server push) -> user picks a decision ->
graph.updateState(...) + graph.stream(GraphInput.resume(), config) ->
humanDecision -> applyDecision -> END -> push final outcome to the browser
```

The human-in-the-loop pause uses langgraph4j's documented **static `interruptBefore(nodeName)`**
plus `CompiledGraph#updateState` / `GraphInput#resume()` - not a dynamic `interrupt()` function,
which isn't present in langgraph4j-core 1.8.24.

Package layout (base package `dev.rabauer.bahndemo`):

- `client` - `DbApiClient` + DTOs for v6.db.transport.rest.
- `workflow` - `DelayWorkflowState` (the graph's state), `DelayWorkflowConfig` (graph wiring,
  **fully implemented**, highest API-signature risk), `HumanDecision` enum, `workflow.node.*`
  (the four `NodeAction` implementations, stubbed).
- `service` - `AdvisorService` (+ rule-based/LLM impls), `MonitoredJourney`/`MonitoredJourneyRegistry`,
  `DelayMonitorService` (polling + the demo-safety "simulate delay" trigger),
  `WorkflowOrchestrationService` (the Vaadin↔langgraph4j bridge, **fully implemented**).
- `ui` - `MainView` + `JourneySearchPanel`/`JourneyResultsGrid`/`MonitoringPanel`.
- `config` - `AppShellConfig` (`@Push`), `RestClientConfig`, `AsyncConfig`, `AdvisorConfig`,
  `BahnDemoProperties`.

## Status

Fully implemented and verified end to end, including the human-in-the-loop pause/resume, both locally
(`mvn test`, `mvn spring-boot:run`) and dockerized (`docker compose up`, with the real `llama3.2`
model via Ollama). Notable things found and fixed while implementing:

- langgraph4j's `MemorySaver` checkpointer serializes graph state via `ObjectOutputStream` on every
  step - every DTO stored in `DelayWorkflowState` (`JourneyDto`, `LegDto`, `LocationDto`, `LineDto`)
  has to implement `Serializable`, or checkpointing throws `NotSerializableException`.
- `log` accumulation needs an explicit schema: `DelayWorkflowState.SCHEMA` uses
  `Channels.appender(ArrayList::new)`, passed into `new StateGraph<>(SCHEMA, DelayWorkflowState::new)` -
  without it, each node's log line silently overwrites the previous one instead of accumulating.
- `JourneySearchPanel`, `JourneyResultsGrid`, and `MonitoringPanel` must be Spring
  **prototype**-scoped. As plain singleton `@Component` beans they broke on a second browser tab/reload
  with "Can't move a node from one state tree to another" - a Vaadin component can only ever belong to
  one UI's state tree.
- `RuleBasedAdvisorService` and `LlmAdvisorService` are both active `AdvisorService` beans once
  `bahn.advisor.enabled=true`; `LlmAdvisorService` needs `@Primary` so `AdvisorNode`'s single-bean
  injection point resolves without ambiguity.
- `DbApiClient`'s `RestClient` needs an explicit short connect/read timeout (`RestClientConfig`, 3s) -
  without it, a live `v6.db.transport.rest` outage blocks each search for ~10s (the underlying
  reactor-netty client's default) before falling back to offline data.

## Running it

Locally, no external dependencies (rule-based advisor only):

```
mvn spring-boot:run
```

Dockerized, with Ollama and the LLM-backed advisor:

```
docker compose up
```
