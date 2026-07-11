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

class HeadAndShouldersDetectorTest {

    private final HeadAndShouldersDetector detector = new HeadAndShouldersDetector();

    // atr=1, price=100 → minProminence=1.5, shoulderTolerance=4

    private List<PivotPoint> validSwings() {
        // 왼어깨(96) - 저점(90) - 머리(105) - 저점(91) - 오른어깨(97)
        return List.of(high(96, 10), low(90, 15), high(105, 20), low(91, 25), high(97, 30));
    }

    @Test
    @DisplayName("헤드앤숄더: 기울어진 넥라인 이탈 시 투영값 기준으로 목표가를 계산한다")
    void detectsHeadAndShoulders() {
        // 넥라인 기울기 = (91−90)/(25−15) = 0.1 → necklineAt(j) = 90 + 0.1*(j−15)
        List<Double> c = closes(36, 95.0);
        c.set(31, 93.0); // necklineAt(31)=91.6 위
        c.set(32, 92.0); // necklineAt(32)=91.7 위
        c.set(33, 91.0); // necklineAt(33)=91.8 아래 → 이탈 (dataSize-3=33 이후)
        c.set(34, 90.0);
        c.set(35, 90.0);

        Optional<DetectedPatternDto> result = detector.detect(context(validSwings(), c, 1.0, 100.0));

        assertThat(result).isPresent();
        DetectedPatternDto dto = result.get();
        assertThat(dto.getPatternType()).isEqualTo(PatternType.HEAD_AND_SHOULDERS);
        assertThat(dto.getNecklinePrice()).isEqualTo(91.8);
        // 목표가 = 돌파점 넥라인 91.8 − (머리 105 − 머리 시점 넥라인 90.5) = 77.3
        assertThat(dto.getTargetPrice()).isEqualTo(77.3);
        assertThat(dto.getInvalidationPrice()).isEqualTo(97.0);
        assertThat(dto.getPatternExtremePrice()).isEqualTo(105.0);
        assertThat(dto.getKeyPivotTimestamp()).isEqualTo(timestampOf(20)); // 머리 봉
    }

    @Test
    @DisplayName("머리가 어깨보다 충분히 높지 않으면 헤드앤숄더가 아니다")
    void headNotProminentRejected() {
        List<PivotPoint> swings =
                List.of(high(96, 10), low(90, 15), high(96.5, 20), low(91, 25), high(96, 30));
        List<Double> c = closes(36, 95.0);
        c.set(33, 91.0);

        assertThat(detector.detect(context(swings, c, 1.0, 100.0))).isEmpty();
    }

    @Test
    @DisplayName("두 어깨의 높이 차이가 허용 오차를 넘으면 제외한다")
    void asymmetricShouldersRejected() {
        List<PivotPoint> swings =
                List.of(high(96, 10), low(90, 15), high(105, 20), low(91, 25), high(101, 30));
        List<Double> c = closes(36, 95.0);
        c.set(33, 91.0);

        assertThat(detector.detect(context(swings, c, 1.0, 100.0))).isEmpty();
    }

    @Test
    @DisplayName("넥라인을 이탈하지 않으면 탐지되지 않는다")
    void noBreakoutNoDetection() {
        List<Double> c = closes(36, 95.0); // 전 구간 넥라인 위

        assertThat(detector.detect(context(validSwings(), c, 1.0, 100.0))).isEmpty();
    }
}
