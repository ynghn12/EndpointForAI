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
- 2025-11-19: `/ai_ears` 라우트가 요청 헤더/바디 검증, `X-Request-Id` 전달, 필수 필드만 추려서 A 시스템에 POST, 응답 바디를 그대로 upstream으로 반환하도록 확장됨. 5xx/예외 시 재시도 없이 `502` JSON 에러와 상세 로그를 남기며, `A_SYSTEM_URL` 환경변수로 다운스트림 URL을 주입하도록 구성. 관련 변경 사항은 `Implement downstream forwarding` 커밋으로 `origin/main`에 푸시됨.
- 같은 날 `mvn clean compile`를 시도했으나 실행 환경에 Maven CLI가 없어 실패; 로컬에서 재확인이 필요.

### Outstanding / Next Steps
1. 바디 변환 및 목적지 조회(사전 GET `EARS/Service/Multi?index=...`) 로직을 설계서 수준까지 구현해 현재 임시 환경변수 기반 POST를 대체.
2. 외부 요청 및 다운스트림 A 시스템 프롬프트의 JSON 스키마·인증 세부정보 확정 후 코드/테스트에 반영.
3. 기존 로그/검증을 확장해 타임아웃, 재시도 없음 정책, 알림 훅을 포함한 회복력 시나리오를 완성.
4. ScalaTest + akka-http-testkit으로 정상/오류 경로를 검증하는 회귀 테스트를 추가하고 `mvn test` 파이프라인에서 실행 가능하도록 환경(Maven) 상태를 확인.
