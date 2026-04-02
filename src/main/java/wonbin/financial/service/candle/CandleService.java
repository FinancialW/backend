package wonbin.financial.service.candle;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import wonbin.financial.dto.candle.FinnhubCandleResponse;
import wonbin.financial.entity.Candle;
import wonbin.financial.repository.CandleRepository;

@Service
@RequiredArgsConstructor
public class CandleService {
    private final WebClient webClient = WebClient.builder().build();
    @Value("${FINNHUB_TOKEN}")
    private String finnhubToken;
    @Value("${FINNHUB_CANDLE_URL}")
    private String candleUrl;
    private CandleRepository candleRepository;

    public FinnhubCandleResponse getCandles(
            String symbol,
            String resolution, // 60,D,M
            long from,
            long to
    ) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(candleUrl)
                        .queryParam("symbol", symbol)
                        .queryParam("resolution", resolution)
                        .queryParam("from", from)
                        .queryParam("to", to)
                        .queryParam("token", finnhubToken)
                        .build()
                )
                .retrieve()
                .bodyToMono(FinnhubCandleResponse.class)
                .block();
    }
    @Transactional
    public void fetchAndSaveCandles(String symbol, String resolution, long from, long to) {
        FinnhubCandleResponse response = getCandles(symbol, resolution, from, to);
        if("ok".equals(response.getS()) && response.getT()!=null) {
            List<Candle> candleList = new ArrayList<>();
            int size = response.getT().size();
            for(int i=0;i<size;i++) {
                Candle candle = Candle.builder()
                        .symbol(symbol)
                        .timestamp(response.getT().get(i))
                        .open(response.getO().get(i))
                        .high(response.getH().get(i))
                        .low(response.getL().get(i))
                        .close(response.getC().get(i))
                        .volume(response.getV().get(i))
                        .build();
                candleList.add(candle);
            }
            candleRepository.saveAll(candleList);
        }
    }
}
