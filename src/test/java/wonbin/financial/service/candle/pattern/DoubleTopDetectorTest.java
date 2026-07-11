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

class DoubleTopDetectorTest {

    private final DoubleTopDetector detector = new DoubleTopDetector();

    // atr=1, price=100 → tolerance=2, minDepth=3

    private List<PivotPoint> validSwings() {
        return List.of(high(100, 20), low(90, 30), high(100.5, 40));
    }

    @Test
    @DisplayName("이중 천장: 넥라인 이탈 시 측정 목표가와 무효화 가격을 계산한다")
    void detectsDoubleTop() {
        List<Double> c = closes(45, 95.0);
        c.set(41, 91.0);
        c.set(42, 89.0); // 넥라인(90) 하향 이탈 — dataSize-3=42 이후라 신선한 돌파
        c.set(43, 88.0);
        c.set(44, 88.0);

        Optional<DetectedPatternDto> result = detector.detect(context(validSwings(), c, 1.0, 100.0));

        assertThat(result).isPresent();
        DetectedPatternDto dto = result.get();
        assertThat(dto.getPatternType()).isEqualTo(PatternType.DOUBLE_TOP);
        assertThat(dto.getNecklinePrice()).isEqualTo(90.0);
        // 목표가 = 넥라인 − (고점평균 100.25 − 넥라인 90) = 79.75
        assertThat(dto.getTargetPrice()).isEqualTo(79.75);
        assertThat(dto.getInvalidationPrice()).isEqualTo(100.5);
        assertThat(dto.getBreakoutClose()).isEqualTo(89.0);
        assertThat(dto.getKeyPivotTimestamp()).isEqualTo(timestampOf(40));
        assertThat(dto.getBreakoutTimestamp()).isEqualTo(timestampOf(42));
    }

    @Test
    @DisplayName("넥라인을 이탈하지 않으면 탐지되지 않는다")
    void noBreakoutNoDetection() {
        List<Double> c = closes(45, 95.0); // 전 구간 넥라인 위

        assertThat(detector.detect(context(validSwings(), c, 1.0, 100.0))).isEmpty();
    }

    @Test
    @DisplayName("돌파가 CONFIRM_WINDOW보다 오래됐으면 알림 대상이 아니다")
    void staleBreakoutRejected() {
        List<Double> c = closes(60, 95.0);
        c.set(41, 89.0); // 이탈은 했지만 dataSize-3=57보다 훨씬 과거

        assertThat(detector.detect(context(validSwings(), c, 1.0, 100.0))).isEmpty();
    }

    @Test
    @DisplayName("두 고점의 높이 차이가 허용 오차를 넘으면 이중 천장이 아니다")
    void peaksOutsideTolerance() {
        List<PivotPoint> swings = List.of(high(100, 20), low(90, 30), high(95, 40));
        List<Double> c = closes(45, 95.0);
        c.set(42, 89.0);

        assertThat(detector.detect(context(swings, c, 1.0, 100.0))).isEmpty();
    }

    @Test
    @DisplayName("패턴 폭이 최소 봉 수보다 좁으면 노이즈로 제외한다")
    void tooNarrowRejected() {
        List<PivotPoint> swings = List.of(high(100, 20), low(90, 22), high(100.5, 24));
        List<Double> c = closes(28, 95.0);
        c.set(26, 89.0);

        assertThat(detector.detect(context(swings, c, 1.0, 100.0))).isEmpty();
    }

    @Test
    @DisplayName("휴장일(null) 종가는 건너뛰고 다음 유효 종가로 판정한다")
    void skipsNullCloses() {
        List<Double> c = closes(45, 95.0);
        c.set(41, null);
        c.set(42, 89.0);

        Optional<DetectedPatternDto> result = detector.detect(context(validSwings(), c, 1.0, 100.0));

        assertThat(result).isPresent();
        assertThat(result.get().getBreakoutClose()).isEqualTo(89.0);
    }
}
