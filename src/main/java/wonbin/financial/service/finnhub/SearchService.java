package wonbin.financial.service.finnhub;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import wonbin.financial.constant.KoreanStock;
import wonbin.financial.dto.finnhubDto.FinnhubSearchResponseDto;
import wonbin.financial.exception.SearchException;

@Slf4j
@Service
public class SearchService {
    private static final Map<String, String> KOREAN_STOCK_MAP = KoreanStock.KOREAN_STOCK_MAP;
    private final RestClient restClient;
    @Value("${FINNHUB_TOKEN}")
    private String finnhubToken;

    public SearchService() {
        this.restClient = RestClient.builder()
                .baseUrl("https://finnhub.io/api/v1")
                .build();
    }
    public FinnhubSearchResponseDto searchSymbol(String query) {
        boolean isKorean = query.matches(".*[ㄱ-ㅎㅏ-ㅣ가-힣]+.*");
        if (isKorean) {
            List<FinnhubSearchResponseDto.SearchResult> matchedResults = new ArrayList<>();
            for (Map.Entry<String, String> entry : KOREAN_STOCK_MAP.entrySet()) {
                if (entry.getValue().contains(query)) {
                    FinnhubSearchResponseDto.SearchResult sr = new FinnhubSearchResponseDto.SearchResult();
                    sr.setSymbol(entry.getKey());
                    sr.setDisplaySymbol(entry.getKey());
                    sr.setDescription(entry.getValue()); // 한글 이름 세팅
                    matchedResults.add(sr);
                }
            }
            FinnhubSearchResponseDto customResponse = new FinnhubSearchResponseDto();
            customResponse.setResult(matchedResults);
            customResponse.setCount(matchedResults.size());
            return customResponse;
        }
        try {
            return restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/search")
                            .queryParam("q", query)
                            .queryParam("token", finnhubToken)
                            .build())
                    .retrieve()
                    .body(FinnhubSearchResponseDto.class);
        } catch (Exception e) {
            log.error("검색도중에 문제 발생: {}",e.getMessage());
            throw new SearchException();
        }
    }
}
