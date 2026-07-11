package wonbin.financial.service.candle.pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static wonbin.financial.service.candle.pattern.CandleFixtures.closes;
import static wonbin.financial.service.candle.pattern.CandleFixtures.context;
import static wonbin.financial.service.candle.pattern.CandleFixtures.high;
import static wonbin.financial.service.candle.pattern.CandleFixtures.low;
import static wonbin.financial.service.candle.pattern.CandleFixtures.timestampOf;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import wonbin.financial.constant.PatternType;
import wonbin.financial.dto.candle.PivotPoint;
import wonbin.financial.dto.candle.pattern.DetectedPatternDto;

class DoubleBottomDetectorTest {

    private final DoubleBottomDetector detector = new DoubleBottomDetector();

    // atr=1, price=100 → tolerance=2, minDepth=3

    private List<PivotPoint> validSwings() {
        return List.of(low(90, 20), high(100, 30), low(89.5, 40));
    }

    @Test
    @DisplayName("이중 바닥: 넥라인 돌파 시 측정 목표가와 무효화 가격을 계산한다")
    void detectsDoubleBottom() {
        List<Double> c = closes(45, 95.0);
        c.set(41, 99.0);
        c.set(42, 101.0); // 넥라인(100) 상향 돌파
        c.set(43, 102.0);
        c.set(44, 102.0);

        Optional<DetectedPatternDto> result = detector.detect(context(validSwings(), c, 1.0, 100.0));

        assertThat(result).isPresent();
        DetectedPatternDto dto = result.get();
        assertThat(dto.getPatternType()).isEqualTo(PatternType.DOUBLE_BOTTOM);
        assertThat(dto.getNecklinePrice()).isEqualTo(100.0);
        // 목표가 = 넥라인 + (넥라인 100 − 저점평균 89.75) = 110.25
        assertThat(dto.getTargetPrice()).isEqualTo(110.25);
        assertThat(dto.getInvalidationPrice()).isEqualTo(89.5);
        assertThat(dto.getBreakoutClose()).isEqualTo(101.0);
        assertThat(dto.getKeyPivotTimestamp()).isEqualTo(timestampOf(40));
    }

    @Test
    @DisplayName("넥라인을 돌파하지 않으면 탐지되지 않는다")
    void noBreakoutNoDetection() {
        List<Double> c = closes(45, 95.0);

        assertThat(detector.detect(context(validSwings(), c, 1.0, 100.0))).isEmpty();
    }

    @Test
    @DisplayName("두 저점의 깊이 차이가 허용 오차를 넘으면 이중 바닥이 아니다")
    void bottomsOutsideTolerance() {
        List<PivotPoint> swings = List.of(low(90, 20), high(100, 30), low(95, 40));
        List<Double> c = closes(45, 95.0);
        c.set(42, 101.0);

        assertThat(detector.detect(context(swings, c, 1.0, 100.0))).isEmpty();
    }

    @Test
    @DisplayName("패턴 깊이가 최소 기준보다 얕으면 제외한다")
    void tooShallowRejected() {
        // 넥라인(91) − 저점(90/89.5) 깊이가 minDepth(3) 미만
        List<PivotPoint> swings = List.of(low(90, 20), high(91, 30), low(89.5, 40));
        List<Double> c = closes(45, 90.5);
        c.set(42, 92.0);

        assertThat(detector.detect(context(swings, c, 1.0, 100.0))).isEmpty();
    }
}
