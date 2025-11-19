# Repository Guidelines

## Project Structure & Module Organization
Scala sources live under `src/main/scala/com/example/ai`, with `AiEarsServer.scala` hosting the Akka HTTP entrypoint and routing. Put shared domain models next to the server to reuse JSON formats. Future integration helpers (e.g., A-system client) belong in subpackages such as `com.example.ai.forwarding`. Tests should mirror this layout in `src/test/scala`. Service-level design notes sit in `docs/design.md`, while operational snippets stay in `README.md`. Use `pom.xml` for dependency and plugin changes; avoid ad-hoc build scripts.

## Build, Test, and Development Commands
- `mvn clean compile` — compile Scala sources via scala-maven-plugin and fail fast on syntax issues.
- `mvn exec:java` — run `com.example.ai.AiEarsServer` locally on `http://0.0.0.0:8080/ai_ears` (press ENTER to stop).
- `mvn test` — execute future ScalaTest suites with Akka HTTP TestKit; run before every PR.
- `curl -X POST http://localhost:8080/ai_ears -d '{"eqpId":"EQP-1","scenario":"demo"}' -H 'Content-Type: application/json'` — quick smoke check after code changes.
Cache-intensive workflows (IDE import, formatter) should reuse Maven; avoid mixing SBT.

## Coding Style & Naming Conventions
Follow idiomatic Scala style: two-space indentation, trailing commas avoided, and immutable values (`val`) preferred. Classes, objects, and traits use PascalCase (`AiEarsServer`), while methods and values are camelCase. JSON case classes keep lowerCamel parameters to align with spray-json defaults. Keep routes declarative by composing Akka directives, and extract shared logic into private helpers rather than deeply nested lambdas. If you add formatting, wire `scalafmt` via Maven before enforcing it repo-wide.

## Testing Guidelines
Use ScalaTest (`AnyWordSpec` or `FunSuite`) plus `akka-http-testkit` for route verification. Name suites after the component under test (e.g., `AiEarsServerSpec`). Prefer behavior-driven descriptions (`"POST /ai_ears" should ...`). Mock downstream HTTP calls with Akka HTTP `RouteTest` or lightweight stubs; avoid real network calls in CI. Add at least one regression test when fixing bugs, and target critical paths (payload validation, destination lookup, timeout handling). Capture coverage expectations in PR descriptions until a formal threshold is defined.

## Commit & Pull Request Guidelines
Commit messages mirror the existing history: short (under 72 chars), imperative verbs (`Add`, `Fix`, `Refactor`), and one feature per commit. For pull requests, include: summary of changes, testing notes (`mvn test`, manual curl), outstanding TODOs, and linked issues or Jira IDs when applicable. Screenshots or logs are only needed if UI or protocol responses change. Keep branches rebased over `main` to prevent noisy merge commits.

## Security & Configuration Tips
Never hardcode A-system URLs or credentials; load them from environment variables or Kubernetes ConfigMaps as described in `docs/design.md`. Log payload metadata but exclude sensitive fields. Review dependencies in `pom.xml` before adding new ones, and prefer Typesafe libraries to stay aligned with Akka’s ecosystem.

## Session Context

### Completed Work To Date
- Documented the forwarder microservice requirements, API draft, and open TODOs in `docs/design.md`, including context for the upcoming `/forward` flow.
- Bootstrapped the Maven + Scala 2.13 + Akka HTTP service skeleton (`pom.xml`, `src/main/scala/com/example/ai/AiEarsServer.scala`) exposing `POST /ai_ears` on port 8080 with a `202 Accepted` acknowledgement payload.
- Added README guidance for building/running (`mvn clean compile`, `mvn exec:java`), IntelliJ workflow tips, and the `curl` smoke command for exercising `POST /ai_ears`.
- Resolved a serialization import issue by adding the `akka-http-spray-json_2.13` dependency (around line 47 of `pom.xml`), ensuring IntelliJ can run `AiEarsServer` cleanly.
- Manually verified the endpoint via `curl -X POST http://localhost:8080/ai_ears -H "Content-Type: application/json" -d '{"eqpId":"EQP-12345","scenario":"heat-treatment-v1"}'`, which returns `202` with the acknowledgement response.
- Current git state: the dependency fix in `pom.xml` is committed locally but not yet pushed; context notes previously lived in `AGENT.md`/`summary.md` as uncommitted helpers.

### Outstanding / Next Steps
1. Flesh out the `/ai_ears` handler per `docs/design.md`: call A 시스템 (GET destination info, POST transformed payload) and encapsulate the transformation logic.
2. Define precise JSON schemas and authentication for both client requests and the downstream A 시스템 integration.
3. Implement robust error handling, logging, and alerting aligned with the design doc, covering validation, timeouts, and retries.
4. Add automated route tests using ScalaTest + akka-http-testkit for both the happy path and representative failure cases before landing new behavior.
