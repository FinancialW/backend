package wonbin.financial.service.candle.pattern;

import java.util.List;
import lombok.Builder;
import lombok.Getter;
import wonbin.financial.dto.candle.PivotPoint;

/**
 * 심볼 하나에 대한 패턴 탐지 입력. 오케스트레이터가 봉 데이터에서 한 번 만들어 모든 디텍터가 공유한다.
 * closes에는 휴장일(null)이 섞여 있을 수 있으므로 디텍터는 null 봉을 건너뛰어야 한다.
 */
@Getter
@Builder
public class PatternContext {
    private final String symbol;
    private final List<PivotPoint> swings;   // ZigZag 정제 완료(HIGH/LOW 교대)
    private final List<Double> closes;
    private final List<Long> timestamps;
    private final double atr;
    private final double currentPrice;
    private final int dataSize;
}
