package wonbin.financial.service.candle;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import wonbin.financial.constant.Timeframe;
import wonbin.financial.dto.candle.PivotPoint;
import wonbin.financial.dto.candle.SupportResistanceZone;
import wonbin.financial.dto.candle.YahooCandleResponse;
import wonbin.financial.dto.candle.YahooCandleResponse.Quote;
import wonbin.financial.dto.candle.YahooCandleResponse.Result;
import wonbin.financial.entity.Candle;
import wonbin.financial.repository.CandleRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class CandleService {
    private final WebClient webClient = WebClient.builder().build();
    private final CandleRepository candleRepository;
    private final SupportResistanceAnalyzer analyzer;

    public Candle getLastestCandle(String symbol) {
        return candleRepository.findTopBySymbolOrderByTimestampDesc(symbol);
    }
    public YahooCandleResponse getCandles(
            String symbol,
            String resolution
    ) {
        String interval = convertInterval(resolution);
        String range = convertRange(resolution);
        if(isLongTerm(interval)) {
            Timeframe timeframe = convertToTimeframe(interval);
            List<Candle> savedCandles = candleRepository.findBySymbolAndTimeframeOrderByTimestampAsc(
                    symbol, timeframe);
            // DB에 데이터가 있는 경우
            if(!savedCandles.isEmpty()) {
                return convertEntityToYahooResponse(symbol,savedCandles);
            }
            // DB에 데이터가 아예 없는 경우 API 호출
            YahooCandleResponse response = fetchFromYahooAPI(symbol, interval, range);
            saveCandleToDB(symbol,interval,response);
            return response;
        }
        return fetchFromYahooAPI(symbol,interval,range);
    }

    private boolean isLongTerm(String interval) { // 일봉, 월봉인지 확인하는 메서드
        return "1d".equals(interval) || "1mo".equals(interval);
    }

    public YahooCandleResponse fetchFromYahooAPI(String symbol, String interval, String range) { // api 호출 후 candle 데이터 가져오는 메서드
        YahooCandleResponse response = webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .scheme("https")
                        .host("query1.finance.yahoo.com")
                        .path("/v8/finance/chart/{symbol}")
                        .queryParam("interval", interval)
                        .queryParam("range", range)
                        .build(symbol)
                )
                .retrieve()
                .onStatus(status -> status.isError(), res ->
                        res.bodyToMono(String.class)
                                .map(body -> new RuntimeException("Yahoo API error: " + body))
                )
                .bodyToMono(YahooCandleResponse.class)
                .block();

        if (response == null ||
                response.getChart() == null ||
                response.getChart().getResult() == null ||
                response.getChart().getResult().isEmpty()) {
            throw new IllegalStateException("Yahoo API 응답 없음");
        }

        return response;
    }

    private void saveCandleToDB(String symbol, String interval, YahooCandleResponse response) {
        Result result = response.getChart().getResult().get(0);
        List<Long> timestamps = result.getTimestamp();
        if(timestamps == null || timestamps.isEmpty()) {
            return; // 저장할 데이터가 없으면 종료
        }
        Quote quote = result.getIndicators().getQuote().get(0);
        List<Double> opens = quote.getOpen();
        List<Double> highs = quote.getHigh();
        List<Double> lows = quote.getLow();
        List<Double> closes = quote.getClose();
        List<Double> volumes = quote.getVolume();
        Timeframe timeframe = convertToTimeframe(interval);

        Set<Long> existingTimestamps = candleRepository.findTimestampsBySymbolAndTimeframe(symbol,
                timeframe);
        List<Candle> candleToSave = new ArrayList<>();
        for(int i=0;i<timestamps.size();i++) {
            Long currentTimestamp = timestamps.get(i);
            // 휴장일이면 무시
            if(opens.get(i)==null || closes.get(i)==null) {
                continue;
            }
            // 이미 존재하는 시간대면 무시
            if(existingTimestamps.contains(currentTimestamp)) {
                continue;
            }
            Candle candle = Candle.builder()
                    .symbol(symbol)
                    .timeframe(timeframe)
                    .timestamp(currentTimestamp)
                    .open(opens.get(i))
                    .high(highs.get(i))
                    .low(lows.get(i))
                    .close(closes.get(i))
                    .volume(volumes.get(i) != null ? volumes.get(i).doubleValue() : 0.0)
                    .build();
            candleToSave.add(candle);
        }
        if(!candleToSave.isEmpty()) {
            candleRepository.saveAll(candleToSave); // 모아둔거 한번에 저장
        }
    }

    public void updateDailyCandle(String symbol) {
        try {
            YahooCandleResponse response = fetchFromYahooAPI(symbol, "1d", "5d");
            saveCandleToDB(symbol,"1d",response);
        } catch (Exception e) {
            throw e;
        }
    }

    private YahooCandleResponse convertEntityToYahooResponse(String symbol, List<Candle> candles) {
        List<Long> timestamps = new ArrayList<>();
        List<Double> opens = new ArrayList<>();
        List<Double> highs = new ArrayList<>();
        List<Double> lows = new ArrayList<>();
        List<Double> closes = new ArrayList<>();
        List<Double> volumes = new ArrayList<>();

        for (Candle candle : candles) {
            timestamps.add(candle.getTimestamp());
            opens.add(candle.getOpen());
            highs.add(candle.getHigh());
            lows.add(candle.getLow());
            closes.add(candle.getClose());
            volumes.add(candle.getVolume()); // 엔티티의 Double을 DTO의 Long으로 변환
        }

        YahooCandleResponse.Quote quote = new YahooCandleResponse.Quote();
        quote.setOpen(opens);
        quote.setHigh(highs);
        quote.setLow(lows);
        quote.setClose(closes);
        quote.setVolume(volumes);

        YahooCandleResponse.Indicators indicators = new YahooCandleResponse.Indicators();
        indicators.setQuote(List.of(quote));

        YahooCandleResponse.Result result = new YahooCandleResponse.Result();
        result.setTimestamp(timestamps);
        result.setIndicators(indicators);

        YahooCandleResponse.Chart chart = new YahooCandleResponse.Chart();
        chart.setResult(List.of(result));

        YahooCandleResponse response = new YahooCandleResponse();
        response.setChart(chart);

        return response;
    }

    // 지금은 단기적 결과만 가지고 있음.
    // 장기적 결과 로직 추가 필요
    public List<SupportResistanceZone> calculateSupportResistance(String symbol, String resolution) {

        YahooCandleResponse response = getCandles(symbol, resolution);

        // 데이터 파싱 전 방어 로직
        if (response == null || response.getChart().getResult() == null || response.getChart().getResult().isEmpty()) {
            return new ArrayList<>();
        }

        Quote quote = response.getChart().getResult().get(0).getIndicators().getQuote().get(0);
        List<Double> highs = quote.getHigh();
        List<Double> lows = quote.getLow();
        List<Double> closes = quote.getClose();

        if (highs == null || lows == null || closes == null || highs.isEmpty()) {
            return new ArrayList<>();
        }

        int atrPeriod = 180;
        double currentAtr = calculateATR(highs, lows, closes, atrPeriod);
        log.info("currentATR : {}", currentAtr);
        double latestClose = getLatestValidClose(closes);

        // 기본 오차 범위는 일일 변동성의 절반(0.5)으로 설정
        double epsilon = (currentAtr > 0) ? (currentAtr * 0.5) : (latestClose * 0.02);

        // [핵심 해결책 1] Epsilon의 하한선을 낮춰 저변동성 종목(애플 등) 방어
        double minEpsilon = latestClose * 0.005; // 1.5% -> 0.5%로 대폭 하향
        double maxEpsilon = latestClose * 0.04;  // 상한선은 4.0%로 유지

        // epsilon 값을 minEpsilon과 maxEpsilon 사이로 강제 고정
        epsilon = Math.max(minEpsilon, Math.min(epsilon, maxEpsilon));

        // 3. Pivot 추출
        int windowSize = 3;
        List<PivotPoint> pivots = analyzer.extract(highs, lows, windowSize);

        double thresholdRatio = 0.30;
        double upperBound = latestClose * (1.0 + thresholdRatio);
        double lowerBound = latestClose * (1.0 - thresholdRatio);

        List<PivotPoint> filteredPivots = new ArrayList<>();
        for(PivotPoint p : pivots) {
            double pivotPrice = p.getPrice();
            if(pivotPrice >= lowerBound && pivotPrice <=upperBound) {
                filteredPivots.add(p);
            }
        }

        int minPts = Math.max(3, (int)(filteredPivots.size() * 0.05));

        // [핵심 해결책] Grid Search 방식의 Auto-Tuning 적용
        List<SupportResistanceZone> bestZones = new ArrayList<>();

        // 기본 Epsilon을 기준으로 비율을 조절해가며 최적의 선 개수를 찾음
        // 순서: 100% -> 80% -> 120% -> 60% -> 150% -> 40% -> 200%
        double[] multipliers = {1.0, 0.8, 1.2, 0.6, 1.5, 0.4, 2.0};

        for (double multiplier : multipliers) {
            double testEpsilon = epsilon * multiplier;
            List<SupportResistanceZone> currentZones = analyzer.clusterPivotsAsZone(filteredPivots, testEpsilon, minPts);

            // 3~6개 사이의 이상적인 개수가 나오면 즉시 채택하고 루프 종료
            if (currentZones.size() >= 3 && currentZones.size() <= 6) {
                bestZones = currentZones;
                break;
            }

            // 목표 개수를 단번에 찾지 못했을 경우를 대비한 차선책 (안전망):
            // 1. 아직 아무것도 못 찾았을 경우(bestZones가 비어있을 때) 우선 현재 결과를 저장 (단, 0개가 아닐 때만 유의미하지만 0개라도 일단 저장)
            // 2. 현재 결과가 기존 최고 기록보다 개수가 많으면서, 너무 난잡하지 않을 때(8개 이하) 업데이트
            if (bestZones.isEmpty() || (currentZones.size() > bestZones.size() && currentZones.size() <= 8)) {
                bestZones = currentZones;
            }
        }

        List<SupportResistanceZone> zones = bestZones;

        // 6. 결과 정렬 및 개수 제한
        // 터치 횟수(touchCount)가 많은, 즉 신뢰도가 높은 지지/저항선부터 내림차순 정렬
        zones.sort((z1, z2) -> Integer.compare(z2.getTouchCount(), z1.getTouchCount()));

        // 그래도 선이 6개를 초과한다면 가장 신뢰도 높은 상위 6개만 반환
        if (zones.size() > 6) {
            zones = new ArrayList<>(zones.subList(0, 6));
        }

        return zones;
    }

    // 가격 변동성(ATR - 평균 진폭)을 확인하는 메서드
    public double calculateATR(List<Double> highs, List<Double> lows, List<Double> closes, int period) {

        if (highs.size() < period + 1) return 0.0;

        double sumTrueRange = 0;
        int count = 0;

        // 가장 최근 period 만큼만 반복하여 평균을 냄
        for (int i = highs.size() - period; i < highs.size(); i++) {
            if (highs.get(i) == null || lows.get(i) == null || closes.get(i - 1) == null) continue;

            double high = highs.get(i);
            double low = lows.get(i);
            double prevClose = closes.get(i - 1);

            // True Range(TR) 계산: 다음 3가지 중 가장 큰 값
            // 1. 당일 고가 - 당일 저가
            // 2. |당일 고가 - 전일 종가|
            // 3. |당일 저가 - 전일 종가|
            double tr1 = high - low;
            double tr2 = Math.abs(high - prevClose);
            double tr3 = Math.abs(low - prevClose);

            double trueRange = Math.max(tr1, Math.max(tr2, tr3));
            sumTrueRange += trueRange;
            count++;
        }

        return count > 0 ? sumTrueRange / count : 0.0;
    }

    private double getLatestValidClose(List<Double> closes) {
        for (int i = closes.size() - 1; i >= 0; i--) {
            if (closes.get(i) != null) {
                return closes.get(i);
            }
        }
        return 0.0;
    }

    public String convertInterval(String resolution) {
        switch (resolution) {
            case "1D": return "5m";   // 하루 → 5분봉
            case "1W": return "15m";  // 일주일 → 15분봉
            case "1M": return "1h";   // 한달 → 1시간봉
            case "3M": return "1d";   // 3개월 → 일봉
            case "1Y": return "1d";   // 1년 → 일봉
            case "MAX": return "1mo"; // 전체 → 월봉
            default:
                throw new IllegalArgumentException("지원하지 않는 resolution: " + resolution);
        }
    }
    public String convertRange(String resolution) {
        switch (resolution) {
            case "1D": return "1d";
            case "1W": return "5d";
            case "1M": return "1mo";
            case "3M": return "3mo";
            case "1Y": return "1y";
            case "MAX": return "max";
            default:
                throw new IllegalArgumentException("지원하지 않는 resolution: " + resolution);
        }
    }

    private Timeframe convertToTimeframe(String interval) {
        if("1d".equals(interval)) {
            return Timeframe.DAY;
        } else if("1mo".equals(interval)) {
            return Timeframe.MONTH;
        }
        throw new IllegalArgumentException("지원하지 않는 Timeframe interval: " + interval);
    }
}
