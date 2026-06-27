package wonbin.financial.dto.candle;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 지지/저항 종합 분석 응답.
 * levels = 여러 신호가 합쳐진 컨플루언스 지지/저항 존(핵심 결과),
 * trendLines / movingAverages = 프론트 드로잉 및 근거 표시용 보조 데이터.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LevelAnalysisResponse {
    private int version;        // 캐시 포맷 버전(구버전 캐시 무효화용)
    private double currentPrice;
    private double atr;         // 변동성 참고값
    private List<SupportResistanceZone> levels;
    private List<TrendLine> trendLines;
    private List<MovingAverageLine> movingAverages;
}
