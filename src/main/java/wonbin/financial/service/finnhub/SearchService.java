package wonbin.financial.service.finnhub;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import wonbin.financial.dto.finnhubDto.FinnhubSearchResponseDto;

@Slf4j
@Service
public class SearchService {
    private static final Map<String, String> KOREAN_STOCK_MAP = new HashMap<>();
    static {
        KOREAN_STOCK_MAP.put("AAPL", "애플");
        KOREAN_STOCK_MAP.put("MSFT", "마이크로소프트");
        KOREAN_STOCK_MAP.put("NVDA", "엔비디아");
        KOREAN_STOCK_MAP.put("GOOGL", "알파벳(구글)");
        KOREAN_STOCK_MAP.put("GOOG", "알파벳(구글)");
        KOREAN_STOCK_MAP.put("AMZN", "아마존");
        KOREAN_STOCK_MAP.put("META", "메타(페이스북)");
        KOREAN_STOCK_MAP.put("TSLA", "테슬라");
        KOREAN_STOCK_MAP.put("AVGO", "브로드컴");
        KOREAN_STOCK_MAP.put("TSM", "TSMC");
        KOREAN_STOCK_MAP.put("ASML", "ASML");
        KOREAN_STOCK_MAP.put("ORCL", "오라클");
        KOREAN_STOCK_MAP.put("AMD", "AMD");
        KOREAN_STOCK_MAP.put("INTC", "인텔");
        KOREAN_STOCK_MAP.put("QCOM", "퀄컴");
        KOREAN_STOCK_MAP.put("TXN", "텍사스 인스트루먼트");
        KOREAN_STOCK_MAP.put("CSCO", "시스코");
        KOREAN_STOCK_MAP.put("ADBE", "어도비");
        KOREAN_STOCK_MAP.put("CRM", "세일즈포스");
        KOREAN_STOCK_MAP.put("IBM", "IBM");
        KOREAN_STOCK_MAP.put("NFLX", "넷플릭스");

        KOREAN_STOCK_MAP.put("BRK.B", "버크셔 해서웨이");
        KOREAN_STOCK_MAP.put("JPM", "제이피모간");
        KOREAN_STOCK_MAP.put("V", "비자");
        KOREAN_STOCK_MAP.put("MA", "마스터카드");
        KOREAN_STOCK_MAP.put("BAC", "뱅크오브아메리카");
        KOREAN_STOCK_MAP.put("WFC", "웰스 파고");
        KOREAN_STOCK_MAP.put("GS", "골드만삭스");
        KOREAN_STOCK_MAP.put("MS", "모건스탠리");

        KOREAN_STOCK_MAP.put("LLY", "일라이 릴리");
        KOREAN_STOCK_MAP.put("UNH", "유나이티드헬스");
        KOREAN_STOCK_MAP.put("JNJ", "존슨앤존슨");
        KOREAN_STOCK_MAP.put("MRK", "머크");
        KOREAN_STOCK_MAP.put("ABBV", "애브비");
        KOREAN_STOCK_MAP.put("PFE", "화이자");
        KOREAN_STOCK_MAP.put("TMO", "써모 피셔");
        KOREAN_STOCK_MAP.put("ABT", "애보트");

        KOREAN_STOCK_MAP.put("WMT", "월마트");
        KOREAN_STOCK_MAP.put("PG", "프록터앤갬블(P&G)");
        KOREAN_STOCK_MAP.put("XOM", "엑슨모빌");
        KOREAN_STOCK_MAP.put("CVX", "쉐브론");
        KOREAN_STOCK_MAP.put("HD", "홈디포");
        KOREAN_STOCK_MAP.put("COST", "코스트코");
        KOREAN_STOCK_MAP.put("KO", "코카콜라");
        KOREAN_STOCK_MAP.put("PEP", "펩시코");
        KOREAN_STOCK_MAP.put("MCD", "맥도날드");
        KOREAN_STOCK_MAP.put("DIS", "디즈니");
        KOREAN_STOCK_MAP.put("NKE", "나이키");
        KOREAN_STOCK_MAP.put("TMUS", "티모바일");
        KOREAN_STOCK_MAP.put("VZ", "버라이즌");
        KOREAN_STOCK_MAP.put("BA", "보잉");
        KOREAN_STOCK_MAP.put("CAT", "캐터필러");
    }

    private final RestClient restClient;
    @Value("${FINNHUB_TOKEN}")
    private String finnhubToken;

    public SearchService() {
        this.restClient = RestClient.builder()
                .baseUrl("https://finnhub.io/api/v1")
                .build();
    }
    public FinnhubSearchResponseDto searchSymbol(String query) {
        String upperQuery = query.toUpperCase();
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
            log.error(e.getMessage());
            return null;
        }
    }
}
