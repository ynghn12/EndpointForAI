# AI Forwarder Service

Scala + Akka HTTP 기반의 간단한 REST API 서비스다. `/ai_ears` 엔드포인트에서 JSON POST만 허용하며, 요청을 수신하면 현재는 확인 응답을 반환한다. 향후 A 시스템으로 전달하는 로직을 이 자리에 구현하면 된다.

## 개발 환경
- Java 11
- Maven 3.8+
- Scala 2.13 + Akka HTTP 10.2

## 빌드 & 실행
```bash
mvn clean compile
mvn exec:java
```

서버는 기본적으로 `http://0.0.0.0:8080/ai_ears` 에서 요청을 대기한다. 종료하려면 터미널에서 `ENTER` 입력.

## 예시 요청
```bash
curl -X POST http://localhost:8080/ai_ears \
  -H 'Content-Type: application/json' \
  -d '{"eqpId":"EQP-12345","scenario":"heat-treatment-v1"}'
```

성공 시 `202 Accepted`와 JSON 확인 메시지를 돌려준다.
