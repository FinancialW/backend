package wonbin.financial.service.candle.pattern;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import wonbin.financial.dto.candle.PivotPoint;
import wonbin.financial.dto.candle.PivotPoint.PivotType;

/** 패턴 디텍터 테스트용 컨텍스트 생성 헬퍼. timestamp는 하루 간격 epoch초로 채운다. */
final class CandleFixtures {

    static final long BASE_TIMESTAMP = 1_700_000_000L;
    static final long DAY_SECONDS = 86_400L;

    private CandleFixtures() {
    }

    static PivotPoint high(double price, int index) {
        return new PivotPoint(price, PivotType.HIGH, index);
    }

    static PivotPoint low(double price, int index) {
        return new PivotPoint(price, PivotType.LOW, index);
    }

    static PatternContext context(List<PivotPoint> swings, List<Double> closes,
            double atr, double currentPrice) {
        List<Long> timestamps = new ArrayList<>();
        for (int i = 0; i < closes.size(); i++) {
            timestamps.add(BASE_TIMESTAMP + i * DAY_SECONDS);
        }
        return PatternContext.builder()
                .symbol("TEST")
                .swings(swings)
                .closes(closes)
                .timestamps(timestamps)
                .atr(atr)
                .currentPrice(currentPrice)
                .dataSize(closes.size())
                .build();
    }

    /** value로 채운 종가 리스트. 이후 인덱스를 지정해 돌파 구간만 덮어쓴다. */
    static List<Double> closes(int size, double value) {
        Double[] arr = new Double[size];
        Arrays.fill(arr, value);
        return new ArrayList<>(Arrays.asList(arr));
    }

    static long timestampOf(int index) {
        return BASE_TIMESTAMP + index * DAY_SECONDS;
    }
}
