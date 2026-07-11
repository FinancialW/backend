package wonbin.financial.service.candle.pattern;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import wonbin.financial.constant.PatternType;
import wonbin.financial.dto.candle.PivotPoint;
import wonbin.financial.dto.candle.PivotPoint.PivotType;
import wonbin.financial.dto.candle.pattern.DetectedPatternDto;

/**
 * 이중 바닥(Double Bottom): 비슷한 깊이의 저점 2개 + 사이 고점(넥라인)을 종가가 상향 돌파하면 상승 반전.
 * 목표가 = 넥라인 + (넥라인 − 저점 평균), 무효화 = 두 저점 중 낮은 값 재이탈.
 */
@Component
public class DoubleBottomDetector implements PatternDetector {

    @Override
    public Optional<DetectedPatternDto> detect(PatternContext ctx) {
        List<PivotPoint> swings = ctx.getSwings();
        double tolerance = PatternDetector.peakTolerance(ctx.getAtr(), ctx.getCurrentPrice());
        double minDepth = PatternDetector.minDepth(ctx.getAtr(), ctx.getCurrentPrice());

        for (int i = swings.size() - 1; i >= 2; i--) {
            PivotPoint secondBottom = swings.get(i);
            PivotPoint peak = swings.get(i - 1);
            PivotPoint firstBottom = swings.get(i - 2);
            if (secondBottom.getType() != PivotType.LOW || peak.getType() != PivotType.HIGH
                    || firstBottom.getType() != PivotType.LOW) {
                continue;
            }
            int width = secondBottom.getIndex() - firstBottom.getIndex();
            if (width < MIN_WIDTH || width > MAX_WIDTH) {
                continue;
            }
            if (Math.abs(firstBottom.getPrice() - secondBottom.getPrice()) > tolerance) {
                continue;
            }
            double neckline = peak.getPrice();
            if (neckline - Math.max(firstBottom.getPrice(), secondBottom.getPrice()) < minDepth) {
                continue;
            }

            // 두 번째 저점 이후 넥라인 상향 돌파 종가 탐색
            int breakIndex = -1;
            double breakClose = 0;
            for (int j = secondBottom.getIndex() + 1; j < ctx.getDataSize(); j++) {
                Double close = ctx.getCloses().get(j);
                if (close == null) {
                    continue;
                }
                if (close > neckline) {
                    breakIndex = j;
                    breakClose = close;
                    break;
                }
            }
            if (breakIndex < 0 || breakIndex < ctx.getDataSize() - CONFIRM_WINDOW) {
                continue;
            }

            double height = neckline - (firstBottom.getPrice() + secondBottom.getPrice()) / 2.0;
            double extreme = Math.min(firstBottom.getPrice(), secondBottom.getPrice());
            return Optional.of(DetectedPatternDto.builder()
                    .symbol(ctx.getSymbol())
                    .patternType(PatternType.DOUBLE_BOTTOM)
                    .necklinePrice(PatternDetector.round(neckline))
                    .targetPrice(PatternDetector.round(neckline + height))
                    .invalidationPrice(PatternDetector.round(extreme))
                    .patternExtremePrice(PatternDetector.round(extreme))
                    .breakoutClose(PatternDetector.round(breakClose))
                    .breakoutTimestamp(PatternDetector.timestampAt(ctx, breakIndex))
                    .keyPivotTimestamp(PatternDetector.timestampAt(ctx, secondBottom.getIndex()))
                    .build());
        }
        return Optional.empty();
    }
}
