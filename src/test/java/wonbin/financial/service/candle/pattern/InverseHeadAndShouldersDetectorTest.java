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

class InverseHeadAndShouldersDetectorTest {

    private final InverseHeadAndShouldersDetector detector = new InverseHeadAndShouldersDetector();

    // atr=1, price=100 → minProminence=1.5, shoulderTolerance=4

    private List<PivotPoint> validSwings() {
        // 왼어깨(104) - 고점(110) - 머리(95) - 고점(109) - 오른어깨(103)
        return List.of(low(104, 10), high(110, 15), low(95, 20), high(109, 25), low(103, 30));
    }

    @Test
    @DisplayName("역헤드앤숄더: 넥라인 상향 돌파 시 측정 목표가를 계산한다")
    void detectsInverseHeadAndShoulders() {
        // 넥라인 기울기 = (109−110)/(25−15) = −0.1 → necklineAt(j) = 110 − 0.1*(j−15)
        List<Double> c = closes(36, 105.0);
        c.set(31, 107.0); // necklineAt(31)=108.4 아래
        c.set(32, 108.0); // necklineAt(32)=108.3 아래
        c.set(33, 109.0); // necklineAt(33)=108.2 위 → 돌파
        c.set(34, 110.0);
        c.set(35, 110.0);

        Optional<DetectedPatternDto> result = detector.detect(context(validSwings(), c, 1.0, 100.0));

        assertThat(result).isPresent();
        DetectedPatternDto dto = result.get();
        assertThat(dto.getPatternType()).isEqualTo(PatternType.INVERSE_HEAD_AND_SHOULDERS);
        assertThat(dto.getNecklinePrice()).isEqualTo(108.2);
        // 목표가 = 돌파점 넥라인 108.2 + (머리 시점 넥라인 109.5 − 머리 95) = 122.7
        assertThat(dto.getTargetPrice()).isEqualTo(122.7);
        assertThat(dto.getInvalidationPrice()).isEqualTo(103.0);
        assertThat(dto.getKeyPivotTimestamp()).isEqualTo(timestampOf(20));
    }

    @Test
    @DisplayName("머리가 어깨보다 충분히 낮지 않으면 역헤드앤숄더가 아니다")
    void headNotProminentRejected() {
        List<PivotPoint> swings =
                List.of(low(104, 10), high(110, 15), low(103.5, 20), high(109, 25), low(104, 30));
        List<Double> c = closes(36, 105.0);
        c.set(33, 109.0);

        assertThat(detector.detect(context(swings, c, 1.0, 100.0))).isEmpty();
    }

    @Test
    @DisplayName("넥라인을 돌파하지 않으면 탐지되지 않는다")
    void noBreakoutNoDetection() {
        List<Double> c = closes(36, 105.0);

        assertThat(detector.detect(context(validSwings(), c, 1.0, 100.0))).isEmpty();
    }
}
