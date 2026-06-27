package wonbin.financial.service.candle;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import wonbin.financial.dto.candle.PivotPoint;
import wonbin.financial.dto.candle.PivotPoint.PivotType;
import wonbin.financial.dto.candle.TrendLine;

/**
 * 추세선(대각선) 검출기.
 * 스윙 저점들을 잇는 '상승 추세선'(지지)과 스윙 고점들을 잇는 '하락 추세선'(저항)을 찾는다.
 * 두 앵커를 잇는 직선 중, 한쪽으로 의미 있게 뚫리지 않으면서(유효성) 가장 많은 스윙 포인트가
 * 닿는(touches) 선을 채택한다. 최신 구간만 보도록 lookback으로 한정한다.
 */
@Service
public class TrendLineDetector {

    private static final int LOOKBACK = 160; // 최근 약 7~8개월(일봉 기준)만 추세선 후보로
    private static final int MIN_TOUCHES = 3; // 앵커 2개 + 확인 1개 이상이어야 신뢰
    private static final int MIN_BAR_GAP = 5; // 두 앵커가 너무 붙어있으면 노이즈

    public List<TrendLine> detect(List<PivotPoint> pivots, List<Long> timestamps, int dataSize,
            double atr, double currentPrice) {

        List<TrendLine> result = new ArrayList<>();
        if (pivots == null || pivots.isEmpty() || timestamps == null) {
            return result;
        }
        double tol = Math.max(atr * 0.5, currentPrice * 0.004);
        int minIndex = Math.max(0, dataSize - LOOKBACK);

        List<PivotPoint> lows = new ArrayList<>();
        List<PivotPoint> highs = new ArrayList<>();
        for (PivotPoint p : pivots) {
            if (p.getIndex() < minIndex) {
                continue;
            }
            if (p.getType() == PivotType.LOW) {
                lows.add(p);
            } else {
                highs.add(p);
            }
        }

        TrendLine support = bestLine(lows, timestamps, dataSize, tol, currentPrice, true);
        if (support != null) {
            result.add(support);
        }
        TrendLine resistance = bestLine(highs, timestamps, dataSize, tol, currentPrice, false);
        if (resistance != null) {
            result.add(resistance);
        }
        return result;
    }

    /**
     * isSupport=true면 저점 연결(아래로 뚫리면 무효), false면 고점 연결(위로 뚫리면 무효).
     */
    private TrendLine bestLine(List<PivotPoint> pts, List<Long> timestamps, int dataSize,
            double tol, double currentPrice, boolean isSupport) {

        if (pts.size() < MIN_TOUCHES) {
            return null;
        }
        PivotPoint bestA = null;
        PivotPoint bestB = null;
        int bestTouches = 0;

        for (int i = 0; i < pts.size(); i++) {
            for (int j = i + 1; j < pts.size(); j++) {
                PivotPoint a = pts.get(i);
                PivotPoint b = pts.get(j);
                int gap = Math.abs(b.getIndex() - a.getIndex());
                if (gap < MIN_BAR_GAP) {
                    continue;
                }
                double slope = (b.getPrice() - a.getPrice()) / (b.getIndex() - a.getIndex());

                boolean valid = true;
                int touches = 0;
                for (PivotPoint k : pts) {
                    double lineVal = a.getPrice() + slope * (k.getIndex() - a.getIndex());
                    double diff = k.getPrice() - lineVal;
                    if (isSupport && diff < -tol) {
                        valid = false; // 저점이 추세선 아래로 의미 있게 이탈 → 지지선으로 무효
                        break;
                    }
                    if (!isSupport && diff > tol) {
                        valid = false; // 고점이 추세선 위로 의미 있게 이탈 → 저항선으로 무효
                        break;
                    }
                    if (Math.abs(diff) <= tol) {
                        touches++;
                    }
                }
                if (!valid || touches < MIN_TOUCHES) {
                    continue;
                }
                // 더 많이 닿거나, 같으면 더 최근 앵커를 선호
                boolean better = touches > bestTouches
                        || (touches == bestTouches && bestB != null && b.getIndex() > bestB.getIndex());
                if (better) {
                    bestTouches = touches;
                    bestA = a;
                    bestB = b;
                }
            }
        }

        if (bestA == null) {
            return null;
        }
        double slope = (bestB.getPrice() - bestA.getPrice()) / (bestB.getIndex() - bestA.getIndex());
        double projected = bestA.getPrice() + slope * ((dataSize - 1) - bestA.getIndex());

        // 비현실적으로 멀리 외삽된 추세선은 버린다.
        if (projected < currentPrice * 0.5 || projected > currentPrice * 1.6) {
            return null;
        }

        return TrendLine.builder()
                .type(isSupport ? "SUPPORT" : "RESISTANCE")
                .slope(slope)
                .startTimestamp(safeTs(timestamps, bestA.getIndex()))
                .startPrice(bestA.getPrice())
                .endTimestamp(safeTs(timestamps, bestB.getIndex()))
                .endPrice(bestB.getPrice())
                .currentProjectedPrice(projected)
                .touchCount(bestTouches)
                .strength(Math.min(100.0, bestTouches * 20.0))
                .build();
    }

    private long safeTs(List<Long> timestamps, int index) {
        if (index >= 0 && index < timestamps.size() && timestamps.get(index) != null) {
            return timestamps.get(index);
        }
        return 0L;
    }
}
