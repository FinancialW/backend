package wonbin.financial.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import wonbin.financial.dto.finnhubDto.FinnhubSearchResponseDto;
import wonbin.financial.exception.QueryEmptyException;
import wonbin.financial.service.finnhub.SearchService;

@RestController
@RequiredArgsConstructor
public class StockController {
    private final SearchService searchService;

    @GetMapping("/stock/search")
    public ResponseEntity<?> search(@RequestParam(name="q") String q) {
        if(q==null || q.isBlank()) {
            throw new QueryEmptyException();
        }
        FinnhubSearchResponseDto response = searchService.searchSymbol(q);
        return ResponseEntity.ok(response);
    }
}
