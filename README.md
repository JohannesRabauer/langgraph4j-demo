# langgraph4j-demo

Spring Boot + Vaadin demo of a Deutsche Bahn delay-monitoring workflow built with
[langgraph4j](https://github.com/langgraph4j/langgraph4j), including a human-in-the-loop pause for
deciding what to do about a delay. Prepared for the stream at
https://youtube.com/live/bLN1iiTlcLU.

See [DEMO_PLAN.md](DEMO_PLAN.md) for the architecture.

Run locally (no Ollama, rule-based advisor only):

```
mvn spring-boot:run
```

Run fully dockerized, including Ollama and the LLM-backed advisor:

```
docker compose up
```

First run pulls the `llama3.2` model into a named volume, which can take a few minutes; the app
becomes reachable at http://localhost:8080 once that's done.
