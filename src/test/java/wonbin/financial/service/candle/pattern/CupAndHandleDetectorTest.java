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

class CupAndHandleDetectorTest {

    private final CupAndHandleDetector detector = new CupAndHandleDetector();

    // atr=1, price=100 → rimTolerance=4
    // 컵: 왼테두리 100(20) - 바닥 80(40) - 오른테두리 99(60), 깊이 19.5(≈20%)

    private List<PivotPoint> validSwings() {
        return List.of(high(100, 20), low(80, 40), high(99, 60));
    }

    /** 선행 상승(초반 84 < 88) + 핸들(95~96) + 돌파(100.5)를 갖춘 종가 시퀀스. */
    private List<Double> validCloses() {
        List<Double> c = closes(70, 84.0);
        for (int k = 61; k <= 66; k++) {
            c.set(k, k % 2 == 1 ? 95.0 : 96.0); // 얕은 핸들
        }
        c.set(67, 100.5); // 테두리(99) 종가 돌파 — dataSize-3=67이라 신선
        c.set(68, 101.0);
        c.set(69, 101.0);
        return c;
    }

    @Test
    @DisplayName("컵앤핸들: 테두리 돌파 시 컵 깊이만큼 위로 목표가를 계산한다")
    void detectsCupAndHandle() {
        Optional<DetectedPatternDto> result =
                detector.detect(context(validSwings(), validCloses(), 1.0, 100.0));

        assertThat(result).isPresent();
        DetectedPatternDto dto = result.get();
        assertThat(dto.getPatternType()).isEqualTo(PatternType.CUP_AND_HANDLE);
        assertThat(dto.getNecklinePrice()).isEqualTo(99.0);   // 오른테두리(돌파 기준선)
        assertThat(dto.getTargetPrice()).isEqualTo(118.5);    // 99 + 컵 깊이 19.5
        assertThat(dto.getInvalidationPrice()).isEqualTo(95.0); // 핸들 저점
        assertThat(dto.getBreakoutClose()).isEqualTo(100.5);
        assertThat(dto.getKeyPivotTimestamp()).isEqualTo(timestampOf(60));
    }

    @Test
    @DisplayName("테두리를 돌파하지 않으면 탐지되지 않는다")
    void noBreakoutNoDetection() {
        List<Double> c = validCloses();
        c.set(67, 95.0);
        c.set(68, 95.0);
        c.set(69, 95.0);

        assertThat(detector.detect(context(validSwings(), c, 1.0, 100.0))).isEmpty();
    }

    @Test
    @DisplayName("핸들이 너무 깊으면(컵 깊이의 절반 초과) 제외한다")
    void deepHandleRejected() {
        List<Double> c = validCloses();
        for (int k = 61; k <= 66; k++) {
            c.set(k, 88.0); // 테두리 대비 11 하락 > 깊이의 절반(9.75)
        }

        assertThat(detector.detect(context(validSwings(), c, 1.0, 100.0))).isEmpty();
    }

    @Test
    @DisplayName("컵 깊이가 10% 미만이면 노이즈로 제외한다")
    void shallowCupRejected() {
        List<PivotPoint> swings = List.of(high(100, 20), low(95, 40), high(99, 60));

        assertThat(detector.detect(context(swings, validCloses(), 1.0, 100.0))).isEmpty();
    }

    @Test
    @DisplayName("선행 상승이 없으면 지속 패턴으로 인정하지 않는다")
    void noPriorUptrendRejected() {
        List<Double> c = closes(70, 95.0); // 왼테두리 이전에도 이미 95 수준(상승분 5% 미만)
        for (int k = 61; k <= 66; k++) {
            c.set(k, 95.0);
        }
        c.set(67, 100.5);
        c.set(68, 101.0);
        c.set(69, 101.0);

        assertThat(detector.detect(context(validSwings(), c, 1.0, 100.0))).isEmpty();
    }

    @Test
    @DisplayName("핸들 없이 곧바로 돌파하면(핸들 4봉 미만) 컵앤핸들이 아니다")
    void tooShortHandleRejected() {
        List<Double> c = closes(66, 84.0);
        c.set(61, 95.0);
        c.set(62, 95.0);
        c.set(63, 100.5); // 핸들 2봉 만에 돌파 (dataSize-3=63이라 신선하긴 함)
        c.set(64, 101.0);
        c.set(65, 101.0);

        assertThat(detector.detect(context(validSwings(), c, 1.0, 100.0))).isEmpty();
    }
}
