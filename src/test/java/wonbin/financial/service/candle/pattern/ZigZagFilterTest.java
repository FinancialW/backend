package wonbin.financial.service.candle.pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static wonbin.financial.service.candle.pattern.CandleFixtures.high;
import static wonbin.financial.service.candle.pattern.CandleFixtures.low;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import wonbin.financial.dto.candle.PivotPoint;

class ZigZagFilterTest {

    private final ZigZagFilter filter = new ZigZagFilter();

    // atr=1, price=100 → minReversal = max(2*1, 100*0.03) = 3

    @Test
    @DisplayName("같은 방향의 연속 고점은 더 높은 고점 하나로 병합된다")
    void consecutiveHighsCollapse() {
        List<PivotPoint> result = filter.filter(
                List.of(high(100, 5), high(102, 8), low(95, 12)), 1.0, 100.0);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getPrice()).isEqualTo(102);
        assertThat(result.get(0).getIndex()).isEqualTo(8);
        assertThat(result.get(1).getPrice()).isEqualTo(95);
    }

    @Test
    @DisplayName("최소 반전폭 미달인 반대 방향 피벗은 노이즈로 제거된다")
    void subThresholdReversalDropped() {
        List<PivotPoint> result = filter.filter(
                List.of(high(100, 5), low(98, 8)), 1.0, 100.0);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPrice()).isEqualTo(100);
    }

    @Test
    @DisplayName("한 봉이 HIGH와 LOW 둘 다인 경우에도 교대가 유지된다")
    void sameBarHighAndLow() {
        List<PivotPoint> result = filter.filter(
                List.of(high(100, 5), low(99.5, 5), low(90, 10), high(97, 15)), 1.0, 100.0);

        for (int i = 1; i < result.size(); i++) {
            assertThat(result.get(i).getType()).isNotEqualTo(result.get(i - 1).getType());
        }
    }

    @Test
    @DisplayName("결과는 항상 HIGH/LOW가 교대한다")
    void alternationInvariant() {
        List<PivotPoint> result = filter.filter(
                List.of(high(100, 2), high(101, 4), low(96, 6), low(95, 8),
                        high(99, 10), low(94, 12), high(98, 14)), 1.0, 100.0);

        assertThat(result).isNotEmpty();
        for (int i = 1; i < result.size(); i++) {
            assertThat(result.get(i).getType()).isNotEqualTo(result.get(i - 1).getType());
            assertThat(result.get(i).getIndex()).isGreaterThan(result.get(i - 1).getIndex());
        }
    }

    @Test
    @DisplayName("빈 입력은 빈 결과를 반환한다")
    void emptyInput() {
        assertThat(filter.filter(List.of(), 1.0, 100.0)).isEmpty();
        assertThat(filter.filter(null, 1.0, 100.0)).isEmpty();
    }
}
