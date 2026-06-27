package wonbin.financial.dto.candle;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 이동평균선의 '현재 값'. 동적 지지/저항선으로 작동한다.
 * 프론트는 캔들 데이터로 곡선을 직접 그리되, 이 값으로 현재 지지/저항 위치를 표시할 수 있다.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MovingAverageLine {
    private int period;          // 20, 60, 120, 200
    private double currentValue; // 최신 이동평균 값
    private boolean priceAbove;  // 현재가가 이 이평선 위에 있는지(정배열/역배열 판단 보조)
}
