package wonbin.financial.service.finnhub;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import wonbin.financial.dto.finnhubDto.FinnhubQuoteResponseDto;
import wonbin.financial.service.candle.CandleService;

@Slf4j
@Service
public class QuoteService {
    private static final long CACHE_TTL_MS = 10_000L; // 10초

    private final RestClient restClient;
    private final CandleService candleService;
    @Value("${FINNHUB_TOKEN}")
    private String finnhubToken;

    // Finnhub 무료 티어(60 req/min) 보호용 단기 캐시. 동시 ENTER 시 심볼당 호출을 합침.
    private final Map<String, CachedPrice> priceCache = new ConcurrentHashMap<>();

    public QuoteService(CandleService candleService) {
        this.candleService = candleService;
        this.restClient = RestClient.builder()
                .baseUrl("https://finnhub.io/api/v1")
                .build();
    }

    // 현재가 우선, 실패 시 DB 일봉 종가로 폴백. 컨트롤러/WS 핸들러 공통 진입점.
    public Double getLatestPrice(String symbol) {
        Double price = getQuotePrice(symbol);
        if (price != null) {
            return price;
        }
        return candleService.getLatestDailyClose(symbol);
    }

    // Finnhub /quote 의 c(현재가). 장중=실시간가, 마감(주말·공휴일)=직전 거래일 종가.
    public Double getQuotePrice(String symbol) {
        CachedPrice cached = priceCache.get(symbol);
        if (cached != null && System.currentTimeMillis() - cached.fetchedAt < CACHE_TTL_MS) {
            return cached.price;
        }
        try {
            FinnhubQuoteResponseDto response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/quote")
                            .queryParam("symbol", symbol)
                            .queryParam("token", finnhubToken)
                            .build())
                    .retrieve()
                    .body(FinnhubQuoteResponseDto.class);
            if (response != null && response.getC() > 0) {
                double price = response.getC();
                priceCache.put(symbol, new CachedPrice(price, System.currentTimeMillis()));
                return price;
            }
        } catch (Exception e) {
            log.error("Finnhub quote 조회 실패 ({}): {}", symbol, e.getMessage());
        }
        return null;
    }

    private static class CachedPrice {
        final double price;
        final long fetchedAt;

        CachedPrice(double price, long fetchedAt) {
            this.price = price;
            this.fetchedAt = fetchedAt;
        }
    }
}
