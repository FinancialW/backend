package wonbin.financial.service.candle;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import wonbin.financial.constant.Timeframe;
import wonbin.financial.dto.candle.YahooCandleResponse;
import wonbin.financial.dto.candle.YahooCandleResponse.Quote;
import wonbin.financial.dto.candle.YahooCandleResponse.Result;
import wonbin.financial.entity.Candle;
import wonbin.financial.repository.CandleRepository;

@Service
@RequiredArgsConstructor
public class CandleService {
    private final WebClient webClient = WebClient.builder().build();
    private final CandleRepository candleRepository;

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
