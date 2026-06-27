package wonbin.financial.dto.candle;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 추세선(대각선). 스윙 저점들을 잇는 상승 추세선은 지지, 스윙 고점들을 잇는 하락 추세선은 저항으로 작동한다.
 * 프론트가 대각선을 그릴 수 있도록 양 끝점(시간/가격)과 기울기를 제공하고,
 * 컨플루언스 계산을 위해 '현재 시점 투영가'도 함께 내려준다.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrendLine {
    private String type;                  // "SUPPORT"(저점 연결) | "RESISTANCE"(고점 연결)
    private double slope;                 // 봉당 가격 변화량(기울기)
    private long startTimestamp;
    private double startPrice;
    private long endTimestamp;
    private double endPrice;
    private double currentProjectedPrice; // 최신 봉 위치로 투영한 추세선 가격
    private int touchCount;               // 추세선에 닿은 스윙 포인트 수(신뢰도)
    private double strength;              // 0~100 상대 강도
}
