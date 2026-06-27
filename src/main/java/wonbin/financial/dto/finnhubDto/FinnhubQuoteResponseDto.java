package wonbin.financial.dto.finnhubDto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
@JsonIgnoreProperties(ignoreUnknown = true) // 필요 없는 필드는 무시
public class FinnhubQuoteResponseDto {
    private double c;  // 현재가 (장중=실시간가, 마감=마지막 체결가=직전 거래일 종가)
    private double d;  // 변화량
    private double dp; // 변화율(%)
    private double h;  // 당일 고가
    private double l;  // 당일 저가
    private double o;  // 당일 시가
    private double pc; // 전일 종가
    private long t;    // timestamp
}
