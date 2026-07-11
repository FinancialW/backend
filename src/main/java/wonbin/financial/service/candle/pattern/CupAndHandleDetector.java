package wonbin.financial.service.candle.pattern;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import wonbin.financial.constant.PatternType;
import wonbin.financial.dto.candle.PivotPoint;
import wonbin.financial.dto.candle.PivotPoint.PivotType;
import wonbin.financial.dto.candle.pattern.DetectedPatternDto;

/**
 * 컵앤핸들: 선행 상승 → 컵(테두리 고점 → 12~35% 깊이의 바닥 → 테두리 부근 회복)
 * → 얕은 핸들(컵 상단 절반에서의 짧은 되돌림) → 오른쪽 테두리를 종가로 돌파하면 상승 지속.
 * 컵 테두리/바닥은 ZigZag 스윙으로, 핸들은 스윙에 안 잡힐 만큼 얕으므로 종가로 직접 검사한다.
 * 목표가 = 테두리 + 컵 깊이, 무효화 = 핸들 저점 이탈.
 */
@Component
public class CupAndHandleDetector implements PatternDetector {

    private static final int MIN_CUP_WIDTH = 15;          // 컵 최소 폭(봉)
    private static final double MIN_DEPTH_RATIO = 0.10;   // 컵 최소 깊이(테두리 대비)
    private static final double MAX_DEPTH_RATIO = 0.35;   // 이보다 깊으면 컵이 아니라 추세 붕괴
    private static final double BOTTOM_CENTER_RATIO = 0.2; // 바닥이 컵 중앙부에 있어야 함(양끝 20% 제외)
    private static final int UPTREND_LOOKBACK = 15;       // 선행 상승 확인 구간(봉)
    private static final double UPTREND_MIN_RISE = 0.88;  // 왼테두리 가격의 88% 미만이어야(≈12%+ 상승)
    private static final int MIN_HANDLE_BARS = 4;
    private static final int MAX_HANDLE_BARS = 40;
    private static final double MAX_HANDLE_DEPTH_RATIO = 0.5; // 핸들 깊이는 컵 깊이의 절반 이하

    @Override
    public Optional<DetectedPatternDto> detect(PatternContext ctx) {
        List<PivotPoint> swings = ctx.getSwings();
        double rimTolerance = Math.max(1.5 * ctx.getAtr(), ctx.getCurrentPrice() * 0.04);

        // 최신 패턴 우선: 오른쪽 테두리 후보를 끝에서부터 탐색
        for (int i = swings.size() - 1; i >= 2; i--) {
            PivotPoint rightRim = swings.get(i);
            if (rightRim.getType() != PivotType.HIGH) {
                continue;
            }
            for (int j = i - 2; j >= 0; j -= 2) { // 교대 보장이므로 HIGH는 두 칸 간격
                PivotPoint leftRim = swings.get(j);
                if (leftRim.getType() != PivotType.HIGH) {
                    break;
                }
                int width = rightRim.getIndex() - leftRim.getIndex();
                if (width > MAX_WIDTH) {
                    break; // 더 과거로 가면 폭만 커지므로 중단
                }
                if (width < MIN_CUP_WIDTH
                        || Math.abs(leftRim.getPrice() - rightRim.getPrice()) > rimTolerance) {
                    continue;
                }

                // 컵 바닥: 테두리 사이의 최저 스윙 저점
                PivotPoint bottom = lowestLowBetween(swings, j, i);
                if (bottom == null) {
                    continue;
                }
                double rimAvg = (leftRim.getPrice() + rightRim.getPrice()) / 2.0;
                double depth = rimAvg - bottom.getPrice();
                double depthRatio = depth / rimAvg;
                if (depthRatio < MIN_DEPTH_RATIO || depthRatio > MAX_DEPTH_RATIO) {
                    continue;
                }
                // 바닥이 한쪽 끝에 치우치면 V자 반등/급락이지 컵이 아님
                int minGap = (int) (width * BOTTOM_CENTER_RATIO);
                if (bottom.getIndex() - leftRim.getIndex() < minGap
                        || rightRim.getIndex() - bottom.getIndex() < minGap) {
                    continue;
                }
                // 선행 상승: 왼테두리 이전에 의미 있는 상승이 있어야 지속 패턴으로 인정
                if (!hasPriorUptrend(ctx, leftRim)) {
                    continue;
                }

                Optional<DetectedPatternDto> result = findHandleBreakout(ctx, rightRim, bottom, depth);
                if (result.isPresent()) {
                    return result;
                }
            }
        }
        return Optional.empty();
    }

    /** 오른테두리 이후의 얕은 핸들과 테두리 종가 돌파를 검사한다. */
    private Optional<DetectedPatternDto> findHandleBreakout(PatternContext ctx,
            PivotPoint rightRim, PivotPoint bottom, double depth) {
        double rim = rightRim.getPrice();
        double handleLow = Double.MAX_VALUE;
        int breakIndex = -1;
        double breakClose = 0;

        for (int k = rightRim.getIndex() + 1; k < ctx.getDataSize(); k++) {
            Double close = ctx.getCloses().get(k);
            if (close == null) {
                continue;
            }
            if (close > rim) {
                breakIndex = k;
                breakClose = close;
                break;
            }
            handleLow = Math.min(handleLow, close);
        }
        if (breakIndex < 0 || breakIndex < ctx.getDataSize() - CONFIRM_WINDOW) {
            return Optional.empty();
        }
        int handleBars = breakIndex - rightRim.getIndex() - 1;
        if (handleBars < MIN_HANDLE_BARS || handleBars > MAX_HANDLE_BARS
                || handleLow == Double.MAX_VALUE) {
            return Optional.empty();
        }
        // 핸들은 얕아야 하고(컵 깊이의 절반 이하) 컵 상단 절반에 머물러야 한다
        if (rim - handleLow > depth * MAX_HANDLE_DEPTH_RATIO
                || handleLow < bottom.getPrice() + depth * 0.5) {
            return Optional.empty();
        }

        return Optional.of(DetectedPatternDto.builder()
                .symbol(ctx.getSymbol())
                .patternType(PatternType.CUP_AND_HANDLE)
                .necklinePrice(PatternDetector.round(rim))
                .targetPrice(PatternDetector.round(rim + depth))
                .invalidationPrice(PatternDetector.round(handleLow))
                .patternExtremePrice(PatternDetector.round(bottom.getPrice()))
                .breakoutClose(PatternDetector.round(breakClose))
                .breakoutTimestamp(PatternDetector.timestampAt(ctx, breakIndex))
                .keyPivotTimestamp(PatternDetector.timestampAt(ctx, rightRim.getIndex()))
                .build());
    }

    private PivotPoint lowestLowBetween(List<PivotPoint> swings, int fromExclusive, int toExclusive) {
        PivotPoint lowest = null;
        for (int k = fromExclusive + 1; k < toExclusive; k++) {
            PivotPoint p = swings.get(k);
            if (p.getType() == PivotType.LOW
                    && (lowest == null || p.getPrice() < lowest.getPrice())) {
                lowest = p;
            }
        }
        return lowest;
    }

    private boolean hasPriorUptrend(PatternContext ctx, PivotPoint leftRim) {
        int idx = leftRim.getIndex() - UPTREND_LOOKBACK;
        if (idx < 0) {
            return false; // 선행 구간이 안 보이면 판단 불가로 제외
        }
        // 휴장일(null)은 더 과거로 물러나며 첫 유효 종가를 찾는다
        for (int k = idx; k >= 0; k--) {
            Double close = ctx.getCloses().get(k);
            if (close != null) {
                return close < leftRim.getPrice() * UPTREND_MIN_RISE;
            }
        }
        return false;
    }
}
