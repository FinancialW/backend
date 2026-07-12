package wonbin.financial.service.notification;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import wonbin.financial.constant.PatternType;
import wonbin.financial.dto.candle.PivotPoint;
import wonbin.financial.dto.candle.YahooCandleResponse;
import wonbin.financial.dto.candle.YahooCandleResponse.Quote;
import wonbin.financial.dto.candle.YahooCandleResponse.Result;
import wonbin.financial.dto.candle.pattern.DetectedPatternDto;
import wonbin.financial.entity.DetectedPatternEntity;
import wonbin.financial.entity.Member;
import wonbin.financial.entity.WatchList;
import wonbin.financial.exception.KakaoUnauthorizedException;
import wonbin.financial.exception.MemberNotFoundException;
import wonbin.financial.repository.DetectedPatternRepository;
import wonbin.financial.repository.KakaoMemberRepository;
import wonbin.financial.repository.WatchListRepository;
import wonbin.financial.service.candle.CandleService;
import wonbin.financial.service.candle.SupportResistanceAnalyzer;
import wonbin.financial.service.candle.indicator.TechnicalIndicators;
import wonbin.financial.service.candle.pattern.PatternContext;
import wonbin.financial.service.candle.pattern.PatternDetector;
import wonbin.financial.service.candle.pattern.ZigZagFilter;
import wonbin.financial.service.oauth.KakaoTokenManager;

/**
 * 차트 패턴 탐지 → 신규 패턴 저장 → 관심종목 사용자에게 카카오톡 알림 발송 오케스트레이터.
 * 일봉(1Y) 기준으로 동작하며 06:30 배치(Schedulerservice)와 /test/patterns/run에서 호출된다.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PatternNotificationService {

    /** 극점 timestamp 기준 중복 판정 허용 범위(±7일). 데이터 누적으로 피벗이 1~2봉 흔들리는 경우 대비. */
    private static final long DEDUP_TOLERANCE_SECONDS = 7L * 24 * 3600;
    private static final String RESOLUTION = "1Y";

    private final CandleService candleService;
    private final SupportResistanceAnalyzer analyzer;
    private final ZigZagFilter zigZagFilter;
    private final List<PatternDetector> detectors;
    private final DetectedPatternRepository detectedPatternRepository;
    private final WatchListRepository watchListRepository;
    private final KakaoMemberRepository kakaoMemberRepository;
    private final KakaoTokenManager kakaoTokenManager;
    private final KakaoMessageService kakaoMessageService;
    private final PatternChartRenderer chartRenderer;

    @Value("${app.front-base-url}")
    private String frontBaseUrl;

    /** 배치 진입점: 관심종목에 등록된 모든 심볼을 순회한다. */
    public void detectAndNotifyAll() {
        List<String> symbols = watchListRepository.findDistinctSymbols();
        if (symbols.isEmpty()) {
            log.info("관심종목이 없어 패턴 탐지를 건너뜁니다.");
            return;
        }
        log.info("차트 패턴 탐지 배치 시작. 대상 심볼:{}", symbols.size());
        int successCount = 0;
        int failCount = 0;
        for (String symbol : symbols) {
            try {
                detectForSymbol(symbol, false);
                successCount++;
                Thread.sleep(100);
            } catch (InterruptedException e) {
                log.warn("패턴 탐지 배치 스레드가 인터럽트 되었습니다.", e);
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("[{}] 패턴 탐지 중 오류 발생:{}", symbol, e.getMessage());
                failCount++;
            }
        }
        log.info("차트 패턴 탐지 배치 완료. 성공:{}, 실패:{}", successCount, failCount);
    }

    /**
     * 심볼 하나의 패턴을 탐지한다.
     *
     * @param dryRun true면 저장/발송 없이 탐지 결과만 반환(개발용)
     * @return dryRun이면 탐지된 전체 패턴, 아니면 신규(중복 아님) 패턴만
     */
    public List<DetectedPatternDto> detectForSymbol(String symbol, boolean dryRun) {
        PatternContext ctx = buildContext(symbol);
        if (ctx == null) {
            return List.of();
        }

        List<DetectedPatternDto> detections = new ArrayList<>();
        for (PatternDetector detector : detectors) {
            detector.detect(ctx).ifPresent(detections::add);
        }
        if (detections.isEmpty()) {
            return detections;
        }
        if (dryRun) {
            log.info("[{}] (dryRun) 패턴 {}건 탐지", symbol, detections.size());
            return detections;
        }

        List<DetectedPatternDto> fresh = new ArrayList<>();
        for (DetectedPatternDto dto : detections) {
            if (isDuplicate(dto)) {
                log.info("[{}] 이미 감지된 패턴({}), 건너뜁니다.", symbol, dto.getPatternType());
                continue;
            }
            DetectedPatternEntity entity = detectedPatternRepository.save(
                    DetectedPatternEntity.builder()
                            .symbol(dto.getSymbol())
                            .patternType(dto.getPatternType())
                            .keyPivotTimestamp(dto.getKeyPivotTimestamp())
                            .necklinePrice(dto.getNecklinePrice())
                            .targetPrice(dto.getTargetPrice())
                            .invalidationPrice(dto.getInvalidationPrice())
                            .breakoutTimestamp(dto.getBreakoutTimestamp())
                            .build());
            notifyWatchers(dto, ctx);
            entity.markNotified();
            detectedPatternRepository.save(entity);
            fresh.add(dto);
        }
        return fresh;
    }

    private boolean isDuplicate(DetectedPatternDto dto) {
        return detectedPatternRepository.existsBySymbolAndPatternTypeAndKeyPivotTimestampBetween(
                dto.getSymbol(), dto.getPatternType(),
                dto.getKeyPivotTimestamp() - DEDUP_TOLERANCE_SECONDS,
                dto.getKeyPivotTimestamp() + DEDUP_TOLERANCE_SECONDS);
    }

    /** 봉 데이터를 불러와 피벗 추출 → ZigZag 정제 → 탐지 컨텍스트 구성. 데이터 부족 시 null. */
    private PatternContext buildContext(String symbol) {
        YahooCandleResponse response = candleService.getCandles(symbol, RESOLUTION);
        if (response == null || response.getChart() == null
                || response.getChart().getResult() == null
                || response.getChart().getResult().isEmpty()) {
            return null;
        }
        Result result = response.getChart().getResult().get(0);
        if (result.getIndicators() == null || result.getIndicators().getQuote() == null
                || result.getIndicators().getQuote().isEmpty()) {
            return null;
        }
        Quote quote = result.getIndicators().getQuote().get(0);
        List<Double> highs = quote.getHigh();
        List<Double> lows = quote.getLow();
        List<Double> closes = quote.getClose();
        List<Long> timestamps = result.getTimestamp();
        if (highs == null || lows == null || closes == null || closes.isEmpty()
                || timestamps == null || timestamps.size() < closes.size()) {
            return null;
        }

        int dataSize = closes.size();
        int atrPeriod = Math.min(14, Math.max(1, dataSize - 1));
        double atr = TechnicalIndicators.atr(highs, lows, closes, atrPeriod);
        double currentPrice = TechnicalIndicators.latestValidClose(closes);
        if (currentPrice <= 0) {
            return null;
        }
        // LevelAnalysisService와 동일한 스윙 윈도우 공식
        int window = Math.min(8, Math.max(3, dataSize / 60));
        List<PivotPoint> pivots = analyzer.extract(highs, lows, window);
        List<PivotPoint> swings = zigZagFilter.filter(pivots, atr, currentPrice);

        return PatternContext.builder()
                .symbol(symbol)
                .swings(swings)
                .opens(quote.getOpen())
                .highs(highs)
                .lows(lows)
                .closes(closes)
                .timestamps(timestamps)
                .atr(atr)
                .currentPrice(currentPrice)
                .dataSize(dataSize)
                .build();
    }

    /**
     * 해당 심볼을 관심종목에 담은 모든 사용자에게 발송. 실패한 사용자는 건너뛰고 계속한다.
     * 차트 이미지는 심볼당 1회만 렌더링/업로드하고(카카오 CDN URL은 사용자 간 공유 가능),
     * 렌더링·업로드가 실패하면 기존 텍스트 알림으로 대체한다.
     */
    private void notifyWatchers(DetectedPatternDto dto, PatternContext ctx) {
        List<WatchList> watchers = watchListRepository.findBySymbol(dto.getSymbol());
        if (watchers.isEmpty()) {
            return;
        }
        byte[] chartPng = null;
        try {
            chartPng = chartRenderer.render(dto, ctx);
        } catch (Exception e) {
            log.warn("[{}] 차트 렌더링 실패, 텍스트 알림으로 대체합니다: {}", dto.getSymbol(), e.getMessage());
        }
        byte[] png = chartPng;
        AtomicReference<String> imageUrl = new AtomicReference<>();

        int sent = 0;
        for (WatchList watcher : watchers) {
            Member member = kakaoMemberRepository.findByKakaoId(watcher.getUserId()).orElse(null);
            if (member == null) {
                continue;
            }
            Optional<String> token = kakaoTokenManager.getValidAccessToken(member);
            if (token.isEmpty()) {
                continue;
            }
            try {
                sendWithRetry(member, token.get(), accessToken -> deliver(accessToken, dto, png, imageUrl));
                sent++;
            } catch (Exception e) {
                log.warn("[{}] {} 사용자 알림 발송 실패: {}", dto.getSymbol(), member.getKakaoId(),
                        e.getMessage());
            }
        }
        log.info("[{}] {} 알림 발송 완료: {}/{}명", dto.getSymbol(),
                dto.getPatternType().getKoreanName(), sent, watchers.size());
    }

    /** 이미지가 준비되면 feed 템플릿, 아니면 텍스트 템플릿으로 발송한다. 업로드는 최초 1회만 시도된다. */
    private void deliver(String accessToken, DetectedPatternDto dto, byte[] chartPng,
                         AtomicReference<String> imageUrl) {
        if (chartPng != null && imageUrl.get() == null) {
            try {
                imageUrl.set(kakaoMessageService.uploadImage(
                        accessToken, chartPng, dto.getSymbol() + "_pattern.png"));
            } catch (KakaoUnauthorizedException e) {
                throw e; // 토큰 갱신 후 재시도 대상
            } catch (Exception e) {
                log.warn("[{}] 차트 업로드 실패, 텍스트 알림으로 대체합니다: {}", dto.getSymbol(), e.getMessage());
            }
        }
        if (imageUrl.get() != null) {
            kakaoMessageService.sendFeedToMe(accessToken, buildTitle(dto), buildDescription(dto),
                    imageUrl.get(), frontBaseUrl);
        } else {
            kakaoMessageService.sendToMe(accessToken, buildTitle(dto) + "\n" + buildDescription(dto),
                    frontBaseUrl);
        }
    }

    /** 401(토큰 만료)이면 갱신 후 정확히 1회만 재시도한다. */
    private void sendWithRetry(Member member, String accessToken, Consumer<String> delivery) {
        try {
            delivery.accept(accessToken);
        } catch (KakaoUnauthorizedException e) {
            String refreshed = kakaoTokenManager.refreshAndStore(member)
                    .orElseThrow(() -> new RuntimeException("카카오 토큰 갱신 실패"));
            delivery.accept(refreshed);
        }
    }

    private String buildTitle(DetectedPatternDto dto) {
        PatternType type = dto.getPatternType();
        String direction = type.isBullish() ? "상승 반전" : "하락 반전";
        return String.format("[패턴 감지] %s · %s (%s)", dto.getSymbol(), type.getKoreanName(), direction);
    }

    private String buildDescription(DetectedPatternDto dto) {
        String breakLabel = dto.getPatternType().isBullish() ? "넥라인 돌파" : "넥라인 이탈";
        return String.format("%s: $%.2f%n측정 목표가: $%.2f%n무효화 기준: $%.2f%n※ 투자 조언이 아닙니다.",
                breakLabel, dto.getNecklinePrice(), dto.getTargetPrice(), dto.getInvalidationPrice());
    }

    /**
     * 개발용: 실제 봉 데이터 위에 샘플 패턴 값을 얹어 본인에게 알림을 발송한다(탐지/저장 없음).
     * 운영 발송 경로(deliver/sendWithRetry: 차트 렌더링 → 카카오 업로드 → feed 발송)를 그대로 검증한다.
     */
    public String sendSampleToUser(String kakaoId, String symbol) {
        Member member = kakaoMemberRepository.findByKakaoId(kakaoId)
                .orElseThrow(MemberNotFoundException::new);
        Optional<String> token = kakaoTokenManager.getValidAccessToken(member);
        if (token.isEmpty()) {
            return "카카오 토큰이 없습니다. 사이트에서 카카오 로그인(talk_message 동의) 후 다시 시도하세요.";
        }
        PatternContext ctx = buildContext(symbol);
        if (ctx == null) {
            return "봉 데이터를 불러오지 못했습니다: " + symbol;
        }
        DetectedPatternDto dto = buildSampleDto(ctx);
        byte[] png = null;
        try {
            png = chartRenderer.render(dto, ctx);
        } catch (Exception e) {
            log.warn("[{}] (샘플) 차트 렌더링 실패, 텍스트 알림으로 대체합니다: {}", symbol, e.getMessage());
        }
        byte[] chartPng = png;
        AtomicReference<String> imageUrl = new AtomicReference<>();
        sendWithRetry(member, token.get(), accessToken -> deliver(accessToken, dto, chartPng, imageUrl));
        return imageUrl.get() != null ? "샘플 알림 발송 완료(차트 이미지 포함)" : "샘플 알림 발송 완료(텍스트만)";
    }

    /** 기간 내 최저점을 이중 바닥의 결정적 극점으로 가정한 그럴듯한 샘플 패턴 값을 만든다. */
    private DetectedPatternDto buildSampleDto(PatternContext ctx) {
        List<Double> lows = ctx.getLows();
        List<Long> timestamps = ctx.getTimestamps();
        int size = Math.min(lows.size(), timestamps.size());
        double minLow = ctx.getCurrentPrice();
        int minIdx = size - 1;
        for (int i = 0; i < size; i++) {
            Double low = lows.get(i);
            if (low != null && low < minLow) {
                minLow = low;
                minIdx = i;
            }
        }
        double neckline = ctx.getCurrentPrice();
        return DetectedPatternDto.builder()
                .symbol(ctx.getSymbol())
                .patternType(PatternType.DOUBLE_BOTTOM)
                .necklinePrice(neckline)
                .targetPrice(neckline + (neckline - minLow))
                .invalidationPrice(minLow)
                .patternExtremePrice(minLow)
                .breakoutClose(ctx.getCurrentPrice())
                .breakoutTimestamp(timestamps.get(size - 1))
                .keyPivotTimestamp(timestamps.get(minIdx))
                .build();
    }

    /** 개발용: 심볼에서 처음 탐지되는 패턴의 차트 PNG를 렌더링한다(저장/발송 없음). */
    public Optional<byte[]> renderChartForSymbol(String symbol) {
        PatternContext ctx = buildContext(symbol);
        if (ctx == null) {
            return Optional.empty();
        }
        for (PatternDetector detector : detectors) {
            Optional<DetectedPatternDto> detection = detector.detect(ctx);
            if (detection.isPresent()) {
                return Optional.of(chartRenderer.render(detection.get(), ctx));
            }
        }
        return Optional.empty();
    }
}
