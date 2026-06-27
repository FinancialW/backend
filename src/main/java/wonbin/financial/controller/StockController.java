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
import wonbin.financial.exception.QueryEmptyException;
import wonbin.financial.service.finnhub.QuoteService;
import wonbin.financial.service.finnhub.SearchService;

@RestController
@RequiredArgsConstructor
public class StockController {
    private final SearchService searchService;
    private final QuoteService quoteService;

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
            // 현재가(장중)/직전 거래일 종가(마감), 실패 시 DB 일봉 종가로 폴백
            Double price = quoteService.getLatestPrice(symbol);
            if(price!=null) {
                lastestPrices.put(symbol,price);
            }
        }
        return ResponseEntity.ok(lastestPrices);
    }
}
