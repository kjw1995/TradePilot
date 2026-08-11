# TradePilot

## 개발 규칙

커밋 메시지와 Pull Request 제목·본문은 한글 작성을 원칙으로 합니다. 타입 식별자, 코드, 명령어, 파일 경로와 제품 고유명사는 예외로 허용합니다.

- [기여 및 커밋·PR 규격](CONTRIBUTING.md)
- 로컬 커밋 템플릿 적용: `git config commit.template .gitmessage`

Spring WebFlux 기반 실시간 주식 시세 수집 애플리케이션의 시작 프로젝트입니다.

## 기술 기준

- Java 21
- Spring Boot 3.5.16
- Spring WebFlux / Reactor Netty
- MySQL 8.4 커스텀 이미지(`tradepilot-mysql:local`): 주문·체결·포지션·시세 원장
- Redis 7.4 커스텀 이미지(`tradepilot-redis:local`): 종목별 최신 시세 캐시
- R2DBC: 논블로킹 MySQL 접근
- Flyway + JDBC: 스키마 마이그레이션

## 저장소 선택

MySQL을 시스템의 원장(source of truth)으로 사용합니다. 주문, 체결, 계좌, 포지션처럼 정합성과 트랜잭션이 중요한 데이터는 관계형 모델이 적합합니다.

Redis는 영구 원장이 아닙니다. 최신 시세, 짧은 TTL의 세션·멱등성 키처럼 유실되어도 MySQL에서 복원할 수 있는 데이터에만 사용합니다. MongoDB는 현재 데이터 형태에서 얻는 이점이 작고 운영 복잡성만 늘어나므로 도입하지 않습니다. 원본 틱이 초당 수만 건 이상으로 증가하고 장기 분석이 필요해질 때 ClickHouse나 전용 시계열 저장소를 별도로 검토합니다.

## 실행

Java 21이 설치되어 있어야 합니다.

```powershell
$env:JAVA_HOME="C:\path\to\jdk-21"
$env:Path="$env:JAVA_HOME\bin;$env:Path"
docker compose up -d
$env:SPRING_PROFILES_ACTIVE="local,simulation"
.\gradlew.bat bootRun
```

`java -version`이 21인지 확인한 후 실행합니다. 로컬 PC의 기본 Java가 8이나 17이면 Spring Boot 3.5 빌드 자체가 시작되지 않습니다.

최초 `docker compose up -d`에서 `docker/mysql`, `docker/redis`의 Dockerfile을 사용해 두 로컬 이미지를 자동 생성합니다. 이미지만 미리 만들려면 다음 명령을 사용합니다.

```powershell
docker compose build mysql redis
docker image ls "tradepilot-*"
```

두 포트는 외부 네트워크에 공개되지 않고 `127.0.0.1:3306`, `127.0.0.1:6379`에만 바인딩됩니다. Compose 파일의 비밀번호는 로컬 개발 전용이며 운영 환경에서는 Docker Secret 또는 외부 Secret Manager로 교체해야 합니다.

시뮬레이터는 삼성전자(`005930`)와 SK하이닉스(`000660`) 틱을 1초마다 생성합니다.

브라우저에서 `http://localhost:8080`을 열면 Spring Boot가 직접 제공하는 실시간 대시보드를 볼 수 있습니다. 별도 프론트엔드 개발 서버나 CORS 설정은 필요하지 않습니다.

```powershell
# 내장 실시간 대시보드
Start-Process "http://localhost:8080"

# 실시간 SSE 구독
curl.exe -N "http://localhost:8080/api/v1/market-data/stream?symbols=005930,000660"

# 최신 시세 조회
curl.exe "http://localhost:8080/api/v1/market-data/quotes/005930?market=KRX"
```

`local` 프로필에서는 수동 틱 입력도 가능합니다.

```powershell
curl.exe -X POST "http://localhost:8080/api/v1/market-data/ticks" `
  -H "Content-Type: application/json" `
  -d '{"symbol":"005930","market":"KRX","price":81000,"volume":10,"tradedAt":"2026-08-11T00:00:00Z","source":"LOCAL"}'
```

## 데이터 흐름

```text
증권사 WebSocket(추가 예정)
        │
        ▼
표준 MarketTick → MySQL(R2DBC) → Redis 최신가 캐시
        │
        └─────────────────────→ Reactor event stream → SSE 클라이언트
```

Redis 장애는 MySQL 저장을 롤백하지 않습니다. 최신가 조회 시 Redis miss 또는 장애가 발생하면 MySQL의 가장 최근 틱으로 대체합니다.

현재 SSE 이벤트 버스는 화면 전송용이므로 느린 구독자의 이벤트가 드롭될 수 있습니다. 향후 주문·체결 이벤트에는 동일한 버스를 사용하지 않고, Outbox와 Kafka 같은 내구성 메시징을 적용해야 합니다.

## 다음 연동 지점

실제 증권사를 결정한 후 `marketdata/adapter/in` 아래에 WebSocket 어댑터를 추가합니다. 어댑터가 증권사별 메시지를 `MarketTick`으로 변환해 `IngestMarketTickUseCase`에 전달하면 나머지 저장·캐시·클라이언트 전송 코드는 바뀌지 않습니다.
