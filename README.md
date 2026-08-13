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

## 내 포트폴리오 비교

대시보드의 `내 포트폴리오` 영역은 MySQL에 저장된 계좌·보유 종목과 Redis/MySQL의 최신 시세를 결합해 총자산, 평가금액, 평가손익과 수익률을 보여줍니다. 최초 화면 로드 시 스냅샷 API를 호출하고, 이후에는 기존 시장 데이터 SSE 이벤트로 보유 종목 평가를 실시간 갱신합니다.

```powershell
curl.exe "http://localhost:8080/api/v1/portfolio/accounts/local-account/summary"
```

로컬 개발용 `local-account`와 삼성전자·SK하이닉스 보유 정보는 Flyway `V2__create_portfolio.sql`에서 생성합니다. 계좌와 보유 원장은 정합성이 중요한 관계형 데이터이므로 MySQL에 저장하며, Redis에는 원장 데이터를 중복 저장하지 않고 최신 시세 캐시만 유지합니다.

실제 증권사 연동 시에는 증권사 계좌 API 응답을 `portfolio` 애플리케이션 포트에 맞춰 동기화하는 어댑터를 추가하고, 토큰과 계좌번호 원문은 저장소나 프론트엔드에 노출하지 않아야 합니다.

## 관심종목 관리

관심종목은 MySQL을 원장으로 사용하며 계정별 최대 30개까지 저장합니다. 대시보드에서 종목코드와 종목명을 입력해 추가하고, 위·아래 이동 버튼으로 표시 순서를 바꾸거나 삭제할 수 있습니다. 목록이 바뀌면 화면의 SSE 구독 대상도 자동으로 다시 연결됩니다.

```powershell
# 목록 조회
curl.exe "http://localhost:8080/api/v1/accounts/local-account/watchlist"

# 종목 추가
curl.exe -X POST "http://localhost:8080/api/v1/accounts/local-account/watchlist/items" `
  -H "Content-Type: application/json" `
  -d '{"symbol":"035420","market":"KRX","name":"NAVER"}'

# 전체 순서 변경: 현재 목록의 모든 종목을 정확히 한 번씩 전달
curl.exe -X PATCH "http://localhost:8080/api/v1/accounts/local-account/watchlist/order" `
  -H "Content-Type: application/json" `
  -d '{"items":[{"market":"KRX","symbol":"035420"},{"market":"KRX","symbol":"005930"},{"market":"KRX","symbol":"000660"}]}'

# 종목 삭제
curl.exe -X DELETE "http://localhost:8080/api/v1/accounts/local-account/watchlist/items/035420?market=KRX"
```

로컬 기본 관심종목은 Flyway `V3__create_watchlist.sql`에서 생성합니다. 현재 로컬 시뮬레이터가 KRX 두 종목만 생성하므로 화면 입력도 KRX로 제한했으며, 실제 증권사 해외 종목 마스터가 연결되면 시장 선택 범위를 확장할 수 있습니다.

### 종목 검색

관심종목 추가 화면은 MySQL의 종목 마스터를 종목명 또는 코드로 검색해 선택하는 자동완성을 제공합니다. 검색 결과는 코드 완전 일치, 종목명 완전 일치, 접두사 일치 순으로 우선 표시하며 이미 관심종목에 등록된 항목은 다시 선택할 수 없습니다.

```powershell
curl.exe "http://localhost:8080/api/v1/instruments/search?market=KRX&query=삼성&limit=8"
curl.exe "http://localhost:8080/api/v1/instruments/search?market=KRX&query=005930&limit=8"
```

로컬 종목 마스터와 대표 KRX 종목은 Flyway `V4__create_security_instruments.sql`에서 생성합니다. 운영 환경에서는 한국투자증권 종목정보 파일을 주기적으로 내려받아 `security_instruments`에 upsert하는 동기화 어댑터를 추가할 수 있습니다. 검색 API와 관심종목 UI는 동기화 방식이 바뀌어도 그대로 유지됩니다.

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
