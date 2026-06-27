# Financial Backend

미국 주식 추적 앱을 위한 Spring Boot 백엔드입니다. 실시간 가격 스트리밍, OHLC 캔들 히스토리,
**멀티팩터 컨플루언스 기반 지지/저항 분석**, Kakao OAuth 인증을 제공합니다.

> 백엔드 전용 저장소입니다. 프론트엔드는 별도의 Vite 앱(`http://192.168.0.33:5173`)으로 동작합니다.

---

## 기술 스택

| 분류 | 내용 |
|------|------|
| 언어 / 런타임 | Java 21 |
| 프레임워크 | Spring Boot 4.0.5 (Web, WebFlux, Security, WebSocket, Data JPA) |
| JSON | **Jackson 3** (`tools.jackson.*` — Jackson 2 아님에 주의) |
| DB | MySQL (`localhost:3307`, 스키마 `financial`) |
| 인증 | Kakao OAuth → JWT(HMAC-SHA), HttpOnly 쿠키 |
| 외부 데이터 | Finnhub(실시간 체결·검색), Yahoo Finance(캔들), Kakao(로그인) |
| 수치 계산 | Apache Commons Math3 (DBSCAN 군집화) |

---

## 빠른 시작

### 사전 요구사항
- JDK 21
- `financial` 데이터베이스를 가진 MySQL 인스턴스 (`localhost:3307` — 기본 포트 아님)
- Finnhub · Yahoo Finance · Kakao 로 향하는 아웃바운드 인터넷 접속

### 실행 (Windows PowerShell)
```powershell
.\gradlew.bat bootRun     # :8080 포트로 실행
.\gradlew.bat build       # 컴파일 + 테스트 + 빌드
.\gradlew.bat test        # 전체 테스트
.\gradlew.bat compileJava # 컴파일만(빠른 검증)
```
Unix 환경에서는 `./gradlew`를 사용합니다.

> 스키마는 Hibernate가 자동 관리합니다(`ddl-auto=update`). 별도 마이그레이션 도구는 없습니다.
> 시크릿·호스트 IP·Kakao 리다이렉트 URI는 `src/main/resources/application.properties`에 있습니다.

---

## 아키텍처

단일 Gradle 모듈, 패키지 루트 `wonbin.financial`. 계층:
`controller → service → repository/entity`, 보조 패키지 `dto · event · constant · configuration · exception`.

### 1. 실시간 가격 (이벤트 기반)
Spring `ApplicationEvent`로 업스트림 피드와 클라이언트 세션을 분리합니다.

```
Finnhub WS ──trade──▶ FinnhubWebSocketClient ──PriceUpdateEvent──▶ ClientWebSocketHandler ──▶ /ws (프론트)
                              │                                            ▲
                       FinnhubConnectedEvent                       symbol→Set<session>
                              ▼
                       SubscriptionManager (심볼별 참조 카운팅)
```
- `FinnhubWebSocketClient` — Finnhub WS 연결, 체결마다 `PriceUpdateEvent` 발행
- `SubscriptionManager` — 전역 심볼 참조 카운팅. 마지막 구독자가 떠날 때만 업스트림 구독 해제 (기본 심볼 AAPL/TSLA/MSFT/NVDA는 항상 유지)
- `ClientWebSocketHandler` — 서버 측 `/ws` 엔드포인트. `ENTER`/`ADD`/`REMOVE` 처리, 심볼을 보는 세션에만 `PRICE` 브로드캐스트

### 2. 캔들 / OHLC 히스토리 (REST + 캐시)
`GET /candles` → `CandleService`. resolution을 Yahoo의 `interval`+`range`로 매핑합니다.
**장기 캔들(일봉 `1d`/월봉 `1mo`)만 `candle` 테이블에 영속화**되어 존재 시 DB에서 제공하고,
더 짧은 interval은 항상 실시간으로 가져옵니다. `Schedulerservice`가 06:00(Asia/Seoul)에
구독 중인 심볼의 일봉을 갱신합니다.

### 3. 지지/저항 분석 (멀티팩터 컨플루언스)
👉 [아래 별도 섹션](#지지저항-분석-상세) 참고.

### 4. 인증 (Kakao OAuth → JWT)
`OAuthController` + `AuthService`. code 교환 → Kakao 프로필 조회(`Member` upsert) →
access/refresh JWT 발급. 토큰은 HttpOnly 쿠키(`accessToken`/`refreshToken`)로 전달.
`JwtAuthenticationFilter`가 `Authorization: Bearer` 헤더 또는 `accessToken` 쿠키에서 토큰을 읽어
`SecurityContext`를 채웁니다. 보안은 **stateless**이며 `/auth/**`·`/login`·`/error`·`/test/token`·`/ws`만 허용.

> 사용자 식별자는 **Kakao ID**(숫자 문자열)입니다. JWT subject이자 Spring Security principal이며 `WatchList.userId`입니다.

### 5. 검색 & 관심종목
- `GET /stock/search?q=` — 한국어 질의는 인메모리 `KoreanStock` 맵, 그 외는 Finnhub `/search` 프록시
- `GET /stock/latest-prices?symbols=` — 캔들 테이블의 심볼별 최신 종가
- `/watchlist` (GET/POST/DELETE) — kakaoId 기준 사용자별 심볼 목록

---

## 지지/저항 분석 상세

고수들이 매매 자리를 정할 때 보는 여러 근거(수평선·매물대·이동평균선·추세선)를 **모두 계산한 뒤,
서로 겹치는 자리일수록 강한 지지/저항**으로 점수화하는 컨플루언스 방식입니다.

### 신호 소스 4종 (모두 기존 OHLCV로 계산 — 추가 API 없음)

| # | 소스 | 클래스 | 설명 |
|---|------|--------|------|
| ① | 스윙 피벗 | `SupportResistanceAnalyzer` | 지역 고/저점을 DBSCAN으로 군집화. **거래량 가중 × 최근성 감쇠** — 오래된 터치는 지수적으로 약화 |
| ② | 거래량 매물대 | `VolumeProfileAnalyzer` | 가격대별 거래량 합산 → POC(최대 거래)·HVN(고거래 노드) |
| ③ | 이동평균선 | `LevelAnalysisService` | MA20/60/120/200 동적 지지/저항 (장기선일수록 가중↑) |
| ④ | 추세선 | `TrendLineDetector` | 스윙 저점→상승추세선(지지), 고점→하락추세선(저항). 기울기·양끝점 제공 |

### 컨플루언스 엔진 (`ConfluenceEngine`)
1. 네 소스의 후보 가격선을 ATR 기반 근접도로 군집화
2. `강도 = Σ가중치 × (1 + 0.5 × (서로 다른 근거 종류 수 − 1))` — **여러 종류가 겹칠수록 가산**
3. 현재가 기준 **SUPPORT(아래) / RESISTANCE(위)** 분류, **0~100 상대 강도**, **근거 태그** 부착
4. 강도 내림차순 정렬, 상위 8개

> 결과는 `SupportResistanceEntity`(symbol별, JSON)에 **24시간 캐싱**됩니다. 캐시 포맷에는 `version`이 있어 구버전 캐시는 자동 무효화·재계산됩니다.

### 핵심 파일
```
service/candle/
├── LevelAnalysisService     # 오케스트레이션 + 캐싱(진입점)
├── SupportResistanceAnalyzer# 피벗 추출 + 가중 군집화
├── VolumeProfileAnalyzer    # 매물대(POC/HVN)
├── TrendLineDetector        # 추세선(대각선)
├── ConfluenceEngine         # 후보 통합 + 점수화
└── indicator/TechnicalIndicators  # SMA·ATR·최근성 가중(순수 함수)
```

---

## API 요약

### 캔들
```
GET /candles?symbol=AAPL&resolution=1Y
```
`resolution`: `1D` · `1W` · `1M` · `3M` · `1Y` · `MAX`

### 지지/저항
```
GET /support-resistance?symbol=AAPL&resolution=1Y
GET /support-resistance/analysis?symbol=AAPL&resolution=1Y
```

`/support-resistance` — 컨플루언스 지지/저항 존 목록:
```jsonc
[
  {
    "topPrice": 152.30, "bottomPrice": 150.80, "avgPrice": 151.55,
    "touchCount": 7,
    "strength": 100.0,                 // 0~100 상대 강도
    "role": "SUPPORT",                 // 현재가 기준 SUPPORT | RESISTANCE
    "sources": ["pivot", "ma20", "trendline"]  // 겹친 근거
  }
]
```

`/support-resistance/analysis` — 존 + 드로잉 보조 데이터:
```jsonc
{
  "version": 2,
  "currentPrice": 151.20,
  "atr": 3.14,
  "levels": [ /* 위와 동일한 존 배열 */ ],
  "trendLines": [
    { "type": "SUPPORT", "slope": 0.12,
      "startTimestamp": 1700000000, "startPrice": 140.0,
      "endTimestamp": 1710000000, "endPrice": 150.0,
      "currentProjectedPrice": 151.0, "touchCount": 4, "strength": 80.0 }
  ],
  "movingAverages": [
    { "period": 20, "currentValue": 150.4, "priceAbove": true }
  ]
}
```

### 인증
```
GET  /auth/kakao         # Kakao 로그인 리다이렉트
GET  /auth/kakao/callback
GET  /auth/me
GET  /auth/reissue       # refresh 토큰으로 access 재발급
POST /auth/logout
GET  /test/token         # 로컬 개발용: Kakao 우회 토큰 발급
```

### 검색 & 관심종목
```
GET    /stock/search?q=애플
GET    /stock/latest-prices?symbols=AAPL,TSLA
GET    /watchlist                  # 인증 사용자의 심볼 목록(List<String>)
POST   /watchlist?symbol=AAPL      # 관심종목 추가(쿼리 파라미터)
DELETE /watchlist?symbol=AAPL      # 관심종목 삭제
```

### 실시간 가격 (WebSocket)
```
ws://<host>:8080/ws
→ 클라이언트: { "type": "ENTER" | "ADD" | "REMOVE", "symbol": "AAPL" }
← 서버:      { "type": "PRICE", "symbol": "AAPL", "price": 151.2 }
```

---

## 개발 메모

- **Jackson 3**: `tools.jackson.databind.*`를 사용합니다. `com.fasterxml.jackson.*`를 import 하지 마세요. `JsonNode.asString()`이 기존 `asText()`를 대체합니다.
- **HTTP 클라이언트 혼용**: Yahoo 캔들·Kakao 토큰은 WebFlux `WebClient`, Finnhub 검색은 `RestClient`. 둘 다 `.block()`으로 블로킹 호출합니다.
- 예외는 `GlobalExceptionHandler`(`@RestControllerAdvice`)가 상태 코드로 매핑합니다.

---

## 향후 과제

- **백테스트 하니스**: 과거 데이터로 각 지지/저항 존의 실제 반등/반락 히트율을 측정해, 소스별 가중치(`DIVERSITY_BONUS`, `MA_BASE_WEIGHT` 등)를 데이터 기반으로 튜닝.
- 피보나치 되돌림·라운드 넘버 등 보조 소스 추가 검토.
- 매수/매도 진입가·손절가·손익비(R:R) 신호화.
