package wonbin.financial.service.candle;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import wonbin.financial.dto.candle.LevelAnalysisResponse;
import wonbin.financial.dto.candle.LevelCandidate;
import wonbin.financial.dto.candle.MovingAverageLine;
import wonbin.financial.dto.candle.PivotPoint;
import wonbin.financial.dto.candle.SupportResistanceZone;
import wonbin.financial.dto.candle.TrendLine;
import wonbin.financial.dto.candle.YahooCandleResponse;
import wonbin.financial.dto.candle.YahooCandleResponse.Quote;
import wonbin.financial.dto.candle.YahooCandleResponse.Result;
import wonbin.financial.entity.SupportResistanceEntity;
import wonbin.financial.repository.SupportResistanceRepository;
import wonbin.financial.service.candle.indicator.TechnicalIndicators;

/**
 * 지지/저항 종합 분석 오케스트레이터.
 * 여러 신호 소스(피벗/매물대/이평선/추세선)를 모아 컨플루언스 엔진으로 합치고, 24시간 캐싱한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LevelAnalysisService {

    private final CandleService candleService;
    private final SupportResistanceAnalyzer analyzer;
    private final VolumeProfileAnalyzer volumeProfileAnalyzer;
    private final TrendLineDetector trendLineDetector;
    private final ConfluenceEngine confluenceEngine;
    private final SupportResistanceRepository supportResistanceRepository;
    private final LineService lineService;
    private final ObjectMapper objectMapper;

    private static final int CACHE_VERSION = 2;       // 응답 포맷이 바뀌면 올려 구버전 캐시를 무효화
    private static final int[] MA_PERIODS = {20, 60, 120, 200};
    private static final double FOCUS_RATIO = 0.30;   // 현재가 ±30% 범위만 관심
    private static final double MA_BASE_WEIGHT = 4.0;
    private static final double TREND_BASE_WEIGHT = 3.0;

    /** 캐시 우선 조회. 신선하지 않거나 포맷이 다르면 재계산 후 저장. */
    public LevelAnalysisResponse getAnalysis(String symbol, String resolution) {
        SupportResistanceEntity entity = supportResistanceRepository.findBySymbol(symbol).orElse(null);
        if (entity != null && entity.getZonesJson() != null && isFresh(entity.getUpdateAt())) {
            try {
                LevelAnalysisResponse cached = objectMapper.readValue(entity.getZonesJson(),
                        LevelAnalysisResponse.class);
                if (cached != null && cached.getVersion() == CACHE_VERSION) {
                    log.info("[{}] 캐시된 지지/저항 분석을 사용합니다.", symbol);
                    return cached;
                }
            } catch (Exception e) {
                log.warn("[{}] 캐시 파싱 실패, 새로 계산합니다: {}", symbol, e.getMessage());
            }
        }
        LevelAnalysisResponse fresh = compute(symbol, resolution);
        persist(symbol, fresh);
        return fresh;
    }

    /** 컨플루언스 지지/저항 존 목록만 반환(기존 엔드포인트 호환). */
    public List<SupportResistanceZone> getLevels(String symbol, String resolution) {
        return getAnalysis(symbol, resolution).getLevels();
    }

    /** 배치 스케줄러용: 강제 재계산 후 캐시 저장. */
    public void refresh(String symbol, String resolution) {
        LevelAnalysisResponse fresh = compute(symbol, resolution);
        persist(symbol, fresh);
    }

    private LevelAnalysisResponse compute(String symbol, String resolution) {
        YahooCandleResponse response = candleService.getCandles(symbol, resolution);

        if (response == null || response.getChart() == null
                || response.getChart().getResult() == null
                || response.getChart().getResult().isEmpty()) {
            return emptyResponse(0.0);
        }

        Result result = response.getChart().getResult().get(0);
        if (result.getIndicators() == null || result.getIndicators().getQuote() == null
                || result.getIndicators().getQuote().isEmpty()) {
            return emptyResponse(0.0);
        }
        Quote quote = result.getIndicators().getQuote().get(0);
        List<Double> highs = quote.getHigh();
        List<Double> lows = quote.getLow();
        List<Double> closes = quote.getClose();
        List<Double> volumes = quote.getVolume();
        List<Long> timestamps = result.getTimestamp();

        if (highs == null || lows == null || closes == null || highs.isEmpty()) {
            return emptyResponse(0.0);
        }

        int dataSize = closes.size();
        int atrPeriod = Math.min(14, Math.max(1, dataSize - 1));
        double atr = TechnicalIndicators.atr(highs, lows, closes, atrPeriod);
        double currentPrice = TechnicalIndicators.latestValidClose(closes);
        double avgVolume = TechnicalIndicators.averageVolume(volumes);

        if (currentPrice <= 0) {
            return emptyResponse(0.0);
        }

        double lowerBound = currentPrice * (1.0 - FOCUS_RATIO);
        double upperBound = currentPrice * (1.0 + FOCUS_RATIO);

        // === 1. 스윙 피벗 추출(추세선은 전체 범위 피벗 사용) ===
        int window = Math.min(8, Math.max(3, dataSize / 60));
        List<PivotPoint> allPivots = analyzer.extract(highs, lows, window);

        // === 2. 수평 지지/저항 후보(관심 범위 내 피벗 군집, 거래량·최근성 가중) ===
        List<PivotPoint> focusedPivots = new ArrayList<>();
        for (PivotPoint p : allPivots) {
            if (p.getPrice() >= lowerBound && p.getPrice() <= upperBound) {
                focusedPivots.add(p);
            }
        }
        double epsilon = clusterEpsilon(atr, currentPrice);
        List<LevelCandidate> candidates = new ArrayList<>(
                analyzer.clusterWeightedCandidates(focusedPivots, volumes, avgVolume, dataSize,
                        epsilon, 2));

        // === 3. 거래량 매물대(POC/HVN) ===
        candidates.addAll(volumeProfileAnalyzer.compute(highs, lows, volumes,
                lowerBound, upperBound, dataSize));

        // === 4. 이동평균선(동적 지지/저항) ===
        List<MovingAverageLine> maLines = new ArrayList<>();
        for (int period : MA_PERIODS) {
            Double ma = TechnicalIndicators.sma(closes, period);
            if (ma == null) {
                continue;
            }
            maLines.add(MovingAverageLine.builder()
                    .period(period)
                    .currentValue(round(ma))
                    .priceAbove(currentPrice >= ma)
                    .build());
            // 현재가 근처(관심 범위)의 이평선만 컨플루언스 후보로 투입
            if (ma >= lowerBound && ma <= upperBound) {
                candidates.add(new LevelCandidate(ma, MA_BASE_WEIGHT * maPeriodFactor(period),
                        "ma" + period, 0));
            }
        }

        // === 5. 추세선(대각선) ===
        List<TrendLine> trendLines = trendLineDetector.detect(allPivots, timestamps, dataSize,
                atr, currentPrice);
        for (TrendLine tl : trendLines) {
            double projected = tl.getCurrentProjectedPrice();
            if (projected >= lowerBound && projected <= upperBound) {
                candidates.add(new LevelCandidate(projected,
                        TREND_BASE_WEIGHT * tl.getTouchCount(), "trendline", tl.getTouchCount()));
            }
        }

        // === 6. 컨플루언스 통합 + 점수화 ===
        double mergeEps = mergeEpsilon(atr, currentPrice);
        List<SupportResistanceZone> levels = confluenceEngine.merge(candidates, mergeEps, atr,
                currentPrice);

        return LevelAnalysisResponse.builder()
                .version(CACHE_VERSION)
                .currentPrice(round(currentPrice))
                .atr(round(atr))
                .levels(levels)
                .trendLines(trendLines)
                .movingAverages(maLines)
                .build();
    }

    private double clusterEpsilon(double atr, double currentPrice) {
        double eps = atr > 0 ? atr * 0.5 : currentPrice * 0.02;
        return Math.max(currentPrice * 0.005, Math.min(eps, currentPrice * 0.04));
    }

    private double mergeEpsilon(double atr, double currentPrice) {
        double eps = atr > 0 ? atr * 0.5 : currentPrice * 0.015;
        return Math.max(currentPrice * 0.006, Math.min(eps, currentPrice * 0.03));
    }

    /** 장기 이평선일수록 구조적으로 더 강한 지지/저항으로 본다. */
    private double maPeriodFactor(int period) {
        return switch (period) {
            case 20 -> 1.0;
            case 60 -> 1.2;
            case 120 -> 1.4;
            case 200 -> 1.6;
            default -> 1.0;
        };
    }

    private LevelAnalysisResponse emptyResponse(double currentPrice) {
        return LevelAnalysisResponse.builder()
                .version(CACHE_VERSION)
                .currentPrice(currentPrice)
                .atr(0.0)
                .levels(new ArrayList<>())
                .trendLines(new ArrayList<>())
                .movingAverages(new ArrayList<>())
                .build();
    }

    private void persist(String symbol, LevelAnalysisResponse response) {
        try {
            String json = objectMapper.writeValueAsString(response);
            lineService.saveOrUpdate(symbol, json);
        } catch (Exception e) {
            log.error("[{}] 지지/저항 분석 저장 실패: {}", symbol, e.getMessage());
        }
    }

    private boolean isFresh(LocalDateTime updateAt) {
        return updateAt != null && updateAt.isAfter(LocalDateTime.now().minusHours(24));
    }

    private double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
