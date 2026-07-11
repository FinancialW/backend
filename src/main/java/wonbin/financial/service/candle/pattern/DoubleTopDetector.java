package wonbin.financial.service.candle.pattern;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import wonbin.financial.constant.PatternType;
import wonbin.financial.dto.candle.PivotPoint;
import wonbin.financial.dto.candle.PivotPoint.PivotType;
import wonbin.financial.dto.candle.pattern.DetectedPatternDto;

/**
 * 이중 천장(Double Top): 비슷한 높이의 고점 2개 + 사이 저점(넥라인)을 종가가 하향 이탈하면 하락 반전.
 * 목표가 = 넥라인 − (고점 평균 − 넥라인), 무효화 = 두 고점 중 높은 값 재돌파.
 */
@Component
public class DoubleTopDetector implements PatternDetector {

    @Override
    public Optional<DetectedPatternDto> detect(PatternContext ctx) {
        List<PivotPoint> swings = ctx.getSwings();
        double tolerance = PatternDetector.peakTolerance(ctx.getAtr(), ctx.getCurrentPrice());
        double minDepth = PatternDetector.minDepth(ctx.getAtr(), ctx.getCurrentPrice());

        // 최신 패턴을 우선하기 위해 스윙 끝에서부터 고점-저점-고점 조합을 탐색
        for (int i = swings.size() - 1; i >= 2; i--) {
            PivotPoint secondTop = swings.get(i);
            PivotPoint valley = swings.get(i - 1);
            PivotPoint firstTop = swings.get(i - 2);
            if (secondTop.getType() != PivotType.HIGH || valley.getType() != PivotType.LOW
                    || firstTop.getType() != PivotType.HIGH) {
                continue;
            }
            int width = secondTop.getIndex() - firstTop.getIndex();
            if (width < MIN_WIDTH || width > MAX_WIDTH) {
                continue;
            }
            if (Math.abs(firstTop.getPrice() - secondTop.getPrice()) > tolerance) {
                continue;
            }
            double neckline = valley.getPrice();
            if (Math.min(firstTop.getPrice(), secondTop.getPrice()) - neckline < minDepth) {
                continue;
            }

            // 두 번째 고점 이후 넥라인 하향 이탈 종가 탐색
            int breakIndex = -1;
            double breakClose = 0;
            for (int j = secondTop.getIndex() + 1; j < ctx.getDataSize(); j++) {
                Double close = ctx.getCloses().get(j);
                if (close == null) {
                    continue;
                }
                if (close < neckline) {
                    breakIndex = j;
                    breakClose = close;
                    break;
                }
            }
            // 돌파가 없거나 이미 오래된 돌파면 알림 대상이 아님
            if (breakIndex < 0 || breakIndex < ctx.getDataSize() - CONFIRM_WINDOW) {
                continue;
            }

            double height = (firstTop.getPrice() + secondTop.getPrice()) / 2.0 - neckline;
            double extreme = Math.max(firstTop.getPrice(), secondTop.getPrice());
            return Optional.of(DetectedPatternDto.builder()
                    .symbol(ctx.getSymbol())
                    .patternType(PatternType.DOUBLE_TOP)
                    .necklinePrice(PatternDetector.round(neckline))
                    .targetPrice(PatternDetector.round(neckline - height))
                    .invalidationPrice(PatternDetector.round(extreme))
                    .patternExtremePrice(PatternDetector.round(extreme))
                    .breakoutClose(PatternDetector.round(breakClose))
                    .breakoutTimestamp(PatternDetector.timestampAt(ctx, breakIndex))
                    .keyPivotTimestamp(PatternDetector.timestampAt(ctx, secondTop.getIndex()))
                    .build());
        }
        return Optional.empty();
    }
}
