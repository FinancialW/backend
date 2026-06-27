# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 프로젝트 개요

주식 추적 앱을 위한 Spring Boot **4.0.5** 백엔드(Java 21)입니다. 미국 주식의 실시간 가격을
스트리밍하고, OHLC 캔들 히스토리를 제공하며, Kakao OAuth로 사용자를 인증합니다.
이 저장소는 백엔드 전용이며, 프론트엔드는 별도의 Vite 앱(`http://192.168.0.33:5173`에서 동작 예상)입니다.
주석과 로그 메시지는 한국어로 작성되어 있습니다.

## 명령어

셸은 Windows PowerShell이므로 `.bat` 래퍼(`.\gradlew.bat`)를 사용합니다. Unix에서는 `./gradlew`를 사용합니다.

```powershell
.\gradlew.bat bootRun     # :8080 포트로 앱 실행
.\gradlew.bat build       # 컴파일 + 테스트 + 빌드 산출물 생성
.\gradlew.bat test        # 전체 테스트 실행 (JUnit 5)
.\gradlew.bat test --tests "wonbin.financial.FinancialApplicationTests"   # 단일 테스트 클래스 실행
```

실행하려면 `financial` 데이터베이스를 가진 **MySQL** 인스턴스가 `localhost:3307`(주의: 기본 포트 아님)에
있어야 하고, Finnhub·Yahoo Finance·Kakao로의 아웃바운드 인터넷 접속이 필요합니다.
스키마는 Hibernate가 자동 관리하며(`ddl-auto=update`), 별도의 마이그레이션 도구는 없습니다.

## 핵심 규칙

- **Jackson 3이며 Jackson 2가 아닙니다.** Spring Boot 4는 Jackson 3을 사용하며, 패키지가
  `tools.jackson.databind.*`입니다(예: `tools.jackson.databind.ObjectMapper`/`JsonNode`).
  `com.fasterxml.jackson.*`를 import 하지 **마세요** — 주입되는 `ObjectMapper`가 아닙니다.
  또한 `JsonNode.asString()`(Jackson 3)이 기존 `asText()`를 대체합니다.
- **사용자 식별자는 Kakao ID**(숫자 문자열)입니다. 이 값이 JWT subject이자 Spring Security
  principal(`authentication.getName()`)이며 `WatchList.userId`입니다. 내부 user-id 추상화가
  따로 없으니 kakaoId를 그대로 전달하세요.
- 시크릿, 하드코딩된 LAN IP(`192.168.0.33`), Kakao 리다이렉트 URI가
  `src/main/resources/application.properties`에 직접 들어 있습니다. 호스트/IP를 바꾸려면 CORS
  origin(`CorsConfig`), 리다이렉트 URI(`application.properties` + `OAuthController.redirectToKakao`),
  프론트엔드 로그인 성공 리다이렉트 URL(`OAuthController.callback`)을 함께 수정해야 합니다.

## 아키텍처

단일 Gradle 모듈이며, 패키지 루트는 `wonbin.financial`입니다. 계층 구조는
`controller` → `service` → `repository`/`entity`이며, `dto`·`event`·`constant`·
`configuration`·`exception` 패키지가 이를 보조합니다.

### 실시간 가격 (핵심 이벤트 기반 흐름)

Spring `ApplicationEvent`로 세 협력 객체를 분리하여, 업스트림 피드가 클라이언트 세션과 직접
통신하지 않도록 합니다:

1. `service/websocket/FinnhubWebSocketClient` — `@PostConstruct` 시점에 Finnhub(`FINNHUB_WS_URL`)로
   클라이언트 WebSocket을 엽니다. 연결되면 `FinnhubConnectedEvent`를 발행하고, 들어오는 각 `trade`
   메시지마다 `PriceUpdateEvent(symbol, price)`를 발행합니다. 업스트림 구독을 전달하기 위해
   `subscribe()`/`unsubscribe()`를 노출합니다.
2. `service/websocket/SubscriptionManager` — `FinnhubConnectedEvent`를 수신합니다(기본 심볼 집합
   AAPL/TSLA/MSFT/NVDA 구독). 모든 사용자에 걸친 심볼별 **참조 카운팅**(`symbolRefCount`)을 소유하여,
   마지막 구독자가 떠날 때만 업스트림에서 구독 해제합니다. 기본 심볼은 절대 해제되지 않습니다.
   상태 변경은 원자성을 위해 `ConcurrentHashMap.compute`를 사용합니다.
3. `service/websocket/ClientWebSocketHandler` — 프론트엔드 클라이언트용 **서버 측** WebSocket
   엔드포인트 `/ws`입니다(`configuration/WebSocketConfig`에 등록). 클라이언트의
   `ENTER`/`ADD`/`REMOVE` 메시지를 파싱해 `SubscriptionManager`를 구동하고,
   `symbol → Set<session>` 매핑을 유지하며, 각 `PriceUpdateEvent`마다 해당 심볼을 보고 있는
   세션에만 `PRICE` JSON 메시지를 브로드캐스트합니다. `/ws`는 서블릿 `SecurityFilterChain`
   바깥에 등록됩니다.

### 캔들 / OHLC 히스토리 (REST + 캐시)

`controller/CandleController`의 `GET /candles?symbol=&resolution=` → `service/candle/CandleService`.
resolution(`1D`/`1W`/`1M`/`3M`/`1Y`/`MAX`)은 Yahoo Finance의 `interval`+`range`로 매핑됩니다
(`convertInterval`/`convertRange`). 데이터는 Finnhub가 아니라 **Yahoo Finance의 비공식 차트
API**(`query1.finance.yahoo.com/v8/finance/chart/{symbol}`)에서 가져옵니다. 캐싱 규칙:
**장기 캔들(일봉 `1d` / 월봉 `1mo`)만 `candle` 테이블에 영속화**되어 존재 시 DB에서 제공하고,
더 짧은 interval은 항상 실시간으로 가져옵니다. `entity/Candle`은 `(symbol, timeframe, timestamp)`에
유니크 제약이 있으며, `saveCandleToDB`는 기존 timestamp와 중복을 제거합니다.
`service/candle/CandleBatchScheduler`(`@Scheduled`, 06:00 Asia/Seoul)는 현재 구독 중인 모든
심볼의 일봉을 갱신합니다 — `@EnableScheduling`은 메인 애플리케이션 클래스에 있습니다.

### 인증 (Kakao OAuth → JWT)

`controller/OAuthController`가 Kakao 리다이렉트/콜백, `/auth/me`, `/auth/reissue`, `/auth/logout`을
처리합니다. `service/oauth/AuthService`가 로그인을 조율합니다: code 교환 → Kakao 프로필 조회
(`KakaoTokenService`/`KakaoLoginService`, `Member` upsert) → access+refresh JWT 발급
(`JwtTokenBuilder`, `jwt.secret` 기반 HMAC-SHA). refresh 토큰은 `Member`에 저장되고 재발급 시
다시 검증됩니다. 토큰은 **HttpOnly 쿠키**(`accessToken`/`refreshToken`)로 전달됩니다.

`configuration/JwtAuthenticationFilter`(`UsernamePasswordAuthenticationFilter` 앞에 위치하는
`OncePerRequestFilter`)는 `Authorization: Bearer` 헤더 **또는** `accessToken` 쿠키에서 토큰을 읽어
`SecurityContext`를 채웁니다. `configuration/SecurityConfigFilter`는 **stateless**(세션 없음)이며,
CSRF 비활성화, `/auth/**`·`/login`·`/error`·`/test/token`만 허용하고 나머지는 모두 인증을 요구합니다.
`GET /test/token`은 `jwt.test.id`에 대한 토큰을 발급합니다 — Kakao 플로우를 우회하는 로컬 개발용 단축 경로입니다.

### 검색 & 관심종목(Watchlist)

- `controller/StockController`의 `GET /stock/search?q=` → `service/finnhub/SearchService`: 한국어
  질의는 인메모리 `constant/KoreanStock` 맵과 매칭하고, 비한국어 질의는 Finnhub `/search`로
  프록시합니다(`RestClient` 사용). `GET /stock/latest-prices?symbols=`는 캔들 테이블에서 심볼별
  최신 종가 캐시를 반환합니다.
- `controller/WatchListController`의 `/watchlist`(GET/POST/DELETE)는 사용자별 심볼 목록을
  영속화합니다(`entity/WatchList`, kakaoId를 키로 사용).

### 공통 관심사

- `exception/GlobalExceptionHandler`(`@RestControllerAdvice`)가 도메인 예외를 상태 코드로 매핑합니다
  (예: `DuplicateWatchlistException` → 409, `MemberNotFoundException` → 404).
- HTTP 클라이언트가 혼용됩니다: WebFlux `WebClient`(Yahoo 캔들, Kakao 토큰)와 `RestClient`(Finnhub
  검색) — 클래스패스에 WebFlux가 있음에도 둘 다 `.block()`으로 **블로킹** 호출됩니다.
