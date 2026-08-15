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
  no API key, wraps `db-vendo-client`. Rate limit: 100 requests/minute.
- Optional `langchain4j` + `langchain4j-open-ai` "advisor" node, fully gated behind
  `bahn.advisor.enabled` so the app builds and runs with zero API keys by default.

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

## What's scaffolded vs. left for the stream

Already wired against verified real APIs so the stream doesn't have to fight library signatures:
the full `pom.xml`, package structure, `DelayWorkflowConfig` (graph + `interruptBefore` +
`MemorySaver`), `WorkflowOrchestrationService` (start/resume/getState + `UI.access` push pattern),
`DelayWorkflowState`, and the `AdvisorService` conditional-bean wiring. `mvn spring-boot:run` starts
cleanly with zero environment variables.

Left as `// TODO(stream)` - the actual demoable logic:

1. **`DbApiClient`** - implement the HTTP calls. First `curl` `/locations` and `/journeys` on
   `v6.db.transport.rest` live to confirm exact JSON shapes (especially `/journeys/{refreshToken}`'s
   envelope) before writing the DTO mapping.
2. **Search UI** - wire `JourneySearchPanel` + `JourneyResultsGrid` to the real client. First
   satisfying milestone: search Berlin → Munich, see a grid of journeys.
3. **Monitoring registration** - select a journey, register a `MonitoredJourney`, confirm the
   "Simulate delay" button flips state and the registry/UI plumbing works, before graph complexity
   enters.
4. **`DelayMonitorService.pollAll()`** - real polling via `refreshJourney`, threshold comparison.
5. **Graph nodes, linear happy path, no interrupt yet** - implement `AnalyzeDelayNode`, a trivial
   rule-based `AdvisorNode`, and pass-through `HumanDecisionNode`/`ApplyDecisionNode`; smoke-test with
   `invoke()` before adding the interrupt.
6. **Confirm the interrupt actually halts** - log-verify `outcome` stays empty after `analyzeDelay`/
   `advisor` run.
7. **Push the pause to the UI + resume wiring** - the "wow" moment: trigger a simulated delay, watch
   the UI reactively show the paused decision, click through it, watch it resume and finish.
8. **Real rule-based advisor logic** - earliest-actual-arrival heuristic.
9. *(stretch)* **LLM advisor** - flip `bahn.advisor.enabled=true`, implement `LlmAdvisorService`'s
   prompt + call, compare against the rule-based recommendation.
10. *(stretch)* **Polish** - loading states, DB API error handling, `KEEP_WAITING` re-arming the
    poller, a `state.log()`-driven timeline panel.

## Running it now

```
mvn spring-boot:run
```

Serves an empty-but-reachable Vaadin UI at `http://localhost:8080` - the search form, an empty
results grid, and an inert monitoring panel. No environment variables required.
