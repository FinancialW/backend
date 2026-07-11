package wonbin.financial.service.candle.pattern;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import wonbin.financial.constant.PatternType;
import wonbin.financial.dto.candle.PivotPoint;
import wonbin.financial.dto.candle.PivotPoint.PivotType;
import wonbin.financial.dto.candle.pattern.DetectedPatternDto;

/**
 * 헤드앤숄더: 왼어깨-머리-오른어깨 3개 고점(머리가 가장 높음) + 두 저점을 잇는 넥라인을
 * 종가가 하향 이탈하면 하락 반전. 넥라인은 기울기를 가질 수 있어 직선으로 투영한다.
 * 목표가 = 돌파점 넥라인 − (머리 − 머리 시점 넥라인), 무효화 = 오른어깨 재돌파.
 */
@Component
public class HeadAndShouldersDetector implements PatternDetector {

    @Override
    public Optional<DetectedPatternDto> detect(PatternContext ctx) {
        List<PivotPoint> swings = ctx.getSwings();
        // 머리는 어깨보다 유의미하게 높아야 하고, 두 어깨는 비슷한 높이여야 한다
        double minProminence = Math.max(ctx.getAtr(), ctx.getCurrentPrice() * 0.015);
        double shoulderTolerance = Math.max(1.5 * ctx.getAtr(), ctx.getCurrentPrice() * 0.04);

        for (int i = swings.size() - 1; i >= 4; i--) {
            PivotPoint rightShoulder = swings.get(i);
            PivotPoint rightValley = swings.get(i - 1);
            PivotPoint head = swings.get(i - 2);
            PivotPoint leftValley = swings.get(i - 3);
            PivotPoint leftShoulder = swings.get(i - 4);
            if (rightShoulder.getType() != PivotType.HIGH || rightValley.getType() != PivotType.LOW
                    || head.getType() != PivotType.HIGH || leftValley.getType() != PivotType.LOW
                    || leftShoulder.getType() != PivotType.HIGH) {
                continue;
            }
            int width = rightShoulder.getIndex() - leftShoulder.getIndex();
            if (width < MIN_WIDTH || width > MAX_WIDTH) {
                continue;
            }
            if (head.getPrice() - leftShoulder.getPrice() < minProminence
                    || head.getPrice() - rightShoulder.getPrice() < minProminence) {
                continue;
            }
            if (Math.abs(leftShoulder.getPrice() - rightShoulder.getPrice()) > shoulderTolerance) {
                continue;
            }

            // 넥라인: 두 저점을 잇는 직선을 봉 인덱스 기준으로 투영
            double slope = (rightValley.getPrice() - leftValley.getPrice())
                    / (rightValley.getIndex() - leftValley.getIndex());

            int breakIndex = -1;
            double breakClose = 0;
            for (int j = rightShoulder.getIndex() + 1; j < ctx.getDataSize(); j++) {
                Double close = ctx.getCloses().get(j);
                if (close == null) {
                    continue;
                }
                double necklineAtJ = necklineAt(leftValley, slope, j);
                if (close < necklineAtJ) {
                    breakIndex = j;
                    breakClose = close;
                    break;
                }
            }
            if (breakIndex < 0 || breakIndex < ctx.getDataSize() - CONFIRM_WINDOW) {
                continue;
            }

            double necklineAtBreak = necklineAt(leftValley, slope, breakIndex);
            double necklineAtHead = necklineAt(leftValley, slope, head.getIndex());
            double height = head.getPrice() - necklineAtHead;
            return Optional.of(DetectedPatternDto.builder()
                    .symbol(ctx.getSymbol())
                    .patternType(PatternType.HEAD_AND_SHOULDERS)
                    .necklinePrice(PatternDetector.round(necklineAtBreak))
                    .targetPrice(PatternDetector.round(necklineAtBreak - height))
                    .invalidationPrice(PatternDetector.round(rightShoulder.getPrice()))
                    .patternExtremePrice(PatternDetector.round(head.getPrice()))
                    .breakoutClose(PatternDetector.round(breakClose))
                    .breakoutTimestamp(PatternDetector.timestampAt(ctx, breakIndex))
                    .keyPivotTimestamp(PatternDetector.timestampAt(ctx, head.getIndex()))
                    .build());
        }
        return Optional.empty();
    }

    private double necklineAt(PivotPoint leftValley, double slope, int index) {
        return leftValley.getPrice() + slope * (index - leftValley.getIndex());
    }
}
