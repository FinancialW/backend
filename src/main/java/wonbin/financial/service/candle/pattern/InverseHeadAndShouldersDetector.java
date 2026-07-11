package wonbin.financial.service.candle.pattern;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import wonbin.financial.constant.PatternType;
import wonbin.financial.dto.candle.PivotPoint;
import wonbin.financial.dto.candle.PivotPoint.PivotType;
import wonbin.financial.dto.candle.pattern.DetectedPatternDto;

/**
 * 역헤드앤숄더: 왼어깨-머리-오른어깨 3개 저점(머리가 가장 낮음) + 두 고점을 잇는 넥라인을
 * 종가가 상향 돌파하면 상승 반전. HeadAndShouldersDetector의 상하 대칭.
 */
@Component
public class InverseHeadAndShouldersDetector implements PatternDetector {

    @Override
    public Optional<DetectedPatternDto> detect(PatternContext ctx) {
        List<PivotPoint> swings = ctx.getSwings();
        double minProminence = Math.max(ctx.getAtr(), ctx.getCurrentPrice() * 0.015);
        double shoulderTolerance = Math.max(1.5 * ctx.getAtr(), ctx.getCurrentPrice() * 0.04);

        for (int i = swings.size() - 1; i >= 4; i--) {
            PivotPoint rightShoulder = swings.get(i);
            PivotPoint rightPeak = swings.get(i - 1);
            PivotPoint head = swings.get(i - 2);
            PivotPoint leftPeak = swings.get(i - 3);
            PivotPoint leftShoulder = swings.get(i - 4);
            if (rightShoulder.getType() != PivotType.LOW || rightPeak.getType() != PivotType.HIGH
                    || head.getType() != PivotType.LOW || leftPeak.getType() != PivotType.HIGH
                    || leftShoulder.getType() != PivotType.LOW) {
                continue;
            }
            int width = rightShoulder.getIndex() - leftShoulder.getIndex();
            if (width < MIN_WIDTH || width > MAX_WIDTH) {
                continue;
            }
            if (leftShoulder.getPrice() - head.getPrice() < minProminence
                    || rightShoulder.getPrice() - head.getPrice() < minProminence) {
                continue;
            }
            if (Math.abs(leftShoulder.getPrice() - rightShoulder.getPrice()) > shoulderTolerance) {
                continue;
            }

            double slope = (rightPeak.getPrice() - leftPeak.getPrice())
                    / (rightPeak.getIndex() - leftPeak.getIndex());

            int breakIndex = -1;
            double breakClose = 0;
            for (int j = rightShoulder.getIndex() + 1; j < ctx.getDataSize(); j++) {
                Double close = ctx.getCloses().get(j);
                if (close == null) {
                    continue;
                }
                double necklineAtJ = necklineAt(leftPeak, slope, j);
                if (close > necklineAtJ) {
                    breakIndex = j;
                    breakClose = close;
                    break;
                }
            }
            if (breakIndex < 0 || breakIndex < ctx.getDataSize() - CONFIRM_WINDOW) {
                continue;
            }

            double necklineAtBreak = necklineAt(leftPeak, slope, breakIndex);
            double necklineAtHead = necklineAt(leftPeak, slope, head.getIndex());
            double height = necklineAtHead - head.getPrice();
            return Optional.of(DetectedPatternDto.builder()
                    .symbol(ctx.getSymbol())
                    .patternType(PatternType.INVERSE_HEAD_AND_SHOULDERS)
                    .necklinePrice(PatternDetector.round(necklineAtBreak))
                    .targetPrice(PatternDetector.round(necklineAtBreak + height))
                    .invalidationPrice(PatternDetector.round(rightShoulder.getPrice()))
                    .patternExtremePrice(PatternDetector.round(head.getPrice()))
                    .breakoutClose(PatternDetector.round(breakClose))
                    .breakoutTimestamp(PatternDetector.timestampAt(ctx, breakIndex))
                    .keyPivotTimestamp(PatternDetector.timestampAt(ctx, head.getIndex()))
                    .build());
        }
        return Optional.empty();
    }

    private double necklineAt(PivotPoint leftPeak, double slope, int index) {
        return leftPeak.getPrice() + slope * (index - leftPeak.getIndex());
    }
}
