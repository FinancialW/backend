package wonbin.financial.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
@JsonIgnoreProperties(ignoreUnknown = true) // 필요 없는 필드는 무시
public class FinnhubResponseDto {
    private String type; // "trade" 또는 "ping"
    private List<TradeData> data;

    @Getter
    @ToString
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TradeData {
        private String s; // 심볼
        private double p; // 현재 가격
        private long t;   // 거래 시간 (timestamp)
        private double v; // 거래량
    }
}
