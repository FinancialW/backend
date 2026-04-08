package wonbin.financial.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import wonbin.financial.dto.finnhubDto.FinnhubSearchResponseDto;
import wonbin.financial.entity.Candle;
import wonbin.financial.exception.QueryEmptyException;
import wonbin.financial.service.candle.CandleService;
import wonbin.financial.service.finnhub.SearchService;

@RestController
@RequiredArgsConstructor
public class StockController {
    private final SearchService searchService;
    private final CandleService candleService;

    @GetMapping("/stock/search")
    public ResponseEntity<?> search(@RequestParam(name="q") String q) {
        if(q==null || q.isBlank()) {
            throw new QueryEmptyException();
        }
        FinnhubSearchResponseDto response = searchService.searchSymbol(q);
        return ResponseEntity.ok(response);
    }
    @GetMapping("/stock/latest-prices")
    public ResponseEntity<Map<String,Double>> getLatestPrices(@RequestParam(name="symbols") List<String> symbols) {
        Map<String,Double> lastestPrices = new HashMap<>();
        for(String symbol : symbols) {
            Candle lastestCandle = candleService.getLastestCandle(symbol);
            if(lastestCandle!=null) {
                lastestPrices.put(symbol,lastestCandle.getClose());
            }
        }
        return ResponseEntity.ok(lastestPrices);
    }
}
