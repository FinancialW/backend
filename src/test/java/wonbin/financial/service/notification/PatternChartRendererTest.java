package wonbin.financial.service.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import wonbin.financial.constant.PatternType;
import wonbin.financial.dto.candle.PivotPoint;
import wonbin.financial.dto.candle.PivotPoint.PivotType;
import wonbin.financial.dto.candle.pattern.DetectedPatternDto;
import wonbin.financial.service.candle.pattern.PatternContext;

class PatternChartRendererTest {

    private static final long BASE_TS = 1_735_689_600L; // 2025-01-01 UTC
    private static final long DAY = 86_400L;

    private final PatternChartRenderer renderer = new PatternChartRenderer();

    @Test
    void 이중천장_패턴을_PNG로_렌더링한다() throws IOException {
        PatternContext ctx = doubleTopContext();
        DetectedPatternDto dto = DetectedPatternDto.builder()
                .symbol("TEST")
                .patternType(PatternType.DOUBLE_TOP)
                .necklinePrice(92.0)
                .targetPrice(84.0)
                .invalidationPrice(101.0)
                .patternExtremePrice(100.0)
                .breakoutClose(90.5)
                .breakoutTimestamp(BASE_TS + 110 * DAY)
                .keyPivotTimestamp(BASE_TS + 70 * DAY)
                .build();

        byte[] png = renderer.render(dto, ctx);

        assertTrue(png.length > 1000, "PNG 데이터가 비정상적으로 작습니다.");
        assertEquals((byte) 0x89, png[0]);
        assertEquals('P', png[1]);
        assertEquals('N', png[2]);
        assertEquals('G', png[3]);

        // 시각 확인용 산출물 (build/test-charts/double-top.png)
        Path out = Path.of("build", "test-charts", "double-top.png");
        Files.createDirectories(out.getParent());
        Files.write(out, png);
    }

    /** 상승 → 천장(100) → 되돌림(92) → 천장(100) → 넥라인 이탈로 이어지는 합성 이중 천장 데이터. */
    private PatternContext doubleTopContext() {
        int n = 120;
        double[] path = new double[n];
        for (int i = 0; i < n; i++) {
            if (i <= 40) {
                path[i] = 80 + i * 0.5;                 // 80 → 100 상승
            } else if (i <= 55) {
                path[i] = 100 - (i - 40) * 0.53;        // 100 → 92 되돌림
            } else if (i <= 70) {
                path[i] = 92 + (i - 55) * 0.53;         // 92 → 100 재상승
            } else if (i <= 110) {
                path[i] = 100 - (i - 70) * 0.24;        // 100 → 90.5 하락(넥라인 이탈)
            } else {
                path[i] = 90.5 - (i - 110) * 0.3;
            }
        }
        List<Double> opens = new ArrayList<>();
        List<Double> highs = new ArrayList<>();
        List<Double> lows = new ArrayList<>();
        List<Double> closes = new ArrayList<>();
        List<Long> timestamps = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            timestamps.add(BASE_TS + i * DAY);
            if (i == 20 || i == 60) { // 휴장일(null 봉) 스킵 로직 검증
                opens.add(null);
                highs.add(null);
                lows.add(null);
                closes.add(null);
                continue;
            }
            double close = path[i];
            double open = i == 0 ? close : path[i - 1];
            opens.add(open);
            highs.add(Math.max(open, close) + 0.6);
            lows.add(Math.min(open, close) - 0.6);
            closes.add(close);
        }
        List<PivotPoint> swings = List.of(
                new PivotPoint(79.4, PivotType.LOW, 0),
                new PivotPoint(100.6, PivotType.HIGH, 40),
                new PivotPoint(91.4, PivotType.LOW, 55),
                new PivotPoint(100.6, PivotType.HIGH, 70),
                new PivotPoint(86.9, PivotType.LOW, 119));

        return PatternContext.builder()
                .symbol("TEST")
                .swings(swings)
                .opens(opens)
                .highs(highs)
                .lows(lows)
                .closes(closes)
                .timestamps(timestamps)
                .atr(1.5)
                .currentPrice(path[n - 1])
                .dataSize(n)
                .build();
    }
}
