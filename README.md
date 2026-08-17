# langgraph4j-demo

[![Watch the YouTube session](https://img.youtube.com/vi/bLN1iiTlcLU/maxresdefault.jpg)](https://youtube.com/live/bLN1iiTlcLU)

Spring Boot + Vaadin demo of a Deutsche Bahn delay-monitoring workflow, including a
human-in-the-loop pause for deciding what to do about a delay. Being rebuilt on
[langgraph4j](https://github.com/langgraph4j/langgraph4j) live on stream at
https://youtube.com/live/bLN1iiTlcLU.

## What it does

You search for a Deutsche Bahn connection, pick one to monitor, and the app polls it for delays in
the background. Once a delay crosses a configurable threshold, a workflow kicks in: it looks for
alternative connections, asks an advisor (rule-based, or a local LLM) to recommend one, and then
**pauses and waits for a human decision** in the browser before finalizing an outcome.

**This branch (`stream/pre-langgraph4j`) is the pre-stream starting point**: that workflow exists
today as a plain, hand-rolled Java method chain in `WorkflowOrchestrationService` - no langgraph4j
involved yet. The stream's job is to replace it with a real **langgraph4j** `StateGraph`, with
`CompiledGraph`, checkpointing, and `interruptBefore`/resume mechanics, without changing anything
the UI depends on. See [DEMO_PLAN.md](DEMO_PLAN.md) for the exact before/after and the implementation
order.

## Workflow (as it stands today, pre-stream)

```
search (client.DbApiClient) -> pick a journey -> monitor it (service.DelayMonitorService,
@Scheduled polling or the "Simulate delay" button) -> threshold breach ->
service.WorkflowOrchestrationService.startWorkflow(journey), on workflowExecutor:
  findAlternatives(journey) via dbApiClient.searchJourneys(...)
  -> advisorService.recommend(...)
  -> stash a DelayWorkflowState snapshot in an in-memory Map<journeyId, state>
  -> push it to the browser (Vaadin server push) as "paused, your decision is needed"
-> user picks a decision -> resumeWithDecision(journeyId, decision, index):
  look up the stashed state -> resolve the outcome -> push the final state to the browser
```

No graph, no checkpointer, no nodes - `WorkflowOrchestrationService`'s Javadoc spells out exactly
what a real langgraph4j graph replaces this with.

## Tech stack

- Spring Boot 4.1.0 + Vaadin 25.2.6 (Flow) + Java 21.
- [`org.bsc.langgraph4j:langgraph4j-core:1.8.24`](https://github.com/langgraph4j/langgraph4j) is
  already in `pom.xml` for the workflow engine, but no code uses it yet - that's the stream's job.
- [v6.db.transport.rest](https://v6.db.transport.rest/) for Deutsche Bahn data - free, unofficial,
  no API key, wraps `db-vendo-client`. Rate limit: 100 requests/minute. `DbApiClient` falls back to
  a small hardcoded offline dataset on any failure (timeout, 503, ...) so the demo stays reliable
  regardless of that API's availability.
- Optional LLM advisor node via **Spring AI's Ollama integration**
  (`spring-ai-starter-model-ollama`, model `llama3.2`), gated behind `bahn.advisor.enabled` so the
  app builds and runs with zero external dependencies by default. `docker-compose.yml` runs Ollama
  alongside the app and enables it.

## Running it

Locally, no external dependencies (rule-based advisor only):

```bash
mvn spring-boot:run
```

Fully dockerized, including Ollama and the LLM-backed advisor:

```bash
docker compose up
```

First run pulls the `llama3.2` model into a named volume, which can take a few minutes; the app
becomes reachable at http://localhost:8080 once that's done.

Run the tests:

```bash
mvn test
```

## Configuration

Set in [`application.yml`](src/main/resources/application.yml), overridable via environment
variables (as `docker-compose.yml` does):

| Property | Default | Purpose |
| --- | --- | --- |
| `bahn.api.base-url` | `https://v6.db.transport.rest` | Deutsche Bahn journey API |
| `bahn.api.default-results` | `5` | Number of connections returned per search |
| `bahn.delay.threshold-seconds` | `300` | Delay (in seconds) that triggers the workflow |
| `bahn.delay.poll-interval-ms` | `30000` | Monitoring poll interval - stays well under the API's 100 req/min limit |
| `bahn.advisor.enabled` | `false` | Enables the LLM-backed advisor node (needs Ollama reachable) |
| `spring.ai.ollama.base-url` | `http://localhost:11434` | Ollama endpoint |
| `spring.ai.ollama.chat.options.model` | `llama3.2` | Model used by the advisor node |

## Project structure

Base package: `dev.rabauer.bahndemo`

- `client` - `DbApiClient` + DTOs for v6.db.transport.rest.
- `workflow` - `DelayWorkflowState` (plain, immutable state snapshot - not yet a langgraph4j
  `AgentState`), `HumanDecision` enum. `DelayWorkflowConfig` and `workflow.node.*` don't exist yet -
  built live on stream.
- `service` - `AdvisorService` (+ rule-based/LLM implementations), `MonitoredJourney` /
  `MonitoredJourneyRegistry`, `DelayMonitorService` (polling + the demo-safety "simulate delay"
  trigger), `WorkflowOrchestrationService` (the hand-rolled orchestration described above - becomes
  the Vaadin ↔ langgraph4j bridge on stream).
- `ui` - `MainView` + `JourneySearchPanel` / `JourneyResultsGrid` / `MonitoringPanel`.
- `config` - `AppShellConfig` (`@Push`), `RestClientConfig`, `AsyncConfig`, `BahnDemoProperties`.

See [DEMO_PLAN.md](DEMO_PLAN.md) for the full architecture write-up, including implementation
notes and gotchas encountered while building this (serialization requirements for the
`MemorySaver` checkpointer, Vaadin component scoping, advisor bean resolution, and API timeouts).
