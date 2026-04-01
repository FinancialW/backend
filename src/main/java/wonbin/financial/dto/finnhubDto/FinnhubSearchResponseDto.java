package wonbin.financial.dto.finnhubDto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class FinnhubSearchResponseDto {
    private int count;
    @JsonProperty("result")
    private List<SearchResult> result;

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SearchResult {
        private String description; // 회사명
        private String displaySymbol; // 표시기호
        private String symbol; // 실제 기호
    }
}
