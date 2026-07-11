package wonbin.financial.service.candle.pattern;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;
import wonbin.financial.dto.candle.PivotPoint;
import wonbin.financial.dto.candle.PivotPoint.PivotType;

/**
 * 프랙탈 피벗 목록을 고점/저점이 번갈아 나오는 ZigZag 스윙으로 정제한다.
 * SupportResistanceAnalyzer.extract는 같은 타입이 연속되거나 한 봉이 HIGH+LOW 둘 다일 수 있어,
 * 패턴 규칙(예: 고점-저점-고점)을 적용하려면 교대 보장이 선행돼야 한다.
 */
@Component
public class ZigZagFilter {

    /**
     * @return index 오름차순으로 정렬된, HIGH/LOW가 교대하는 스윙 목록
     */
    public List<PivotPoint> filter(List<PivotPoint> pivots, double atr, double currentPrice) {
        List<PivotPoint> swings = new ArrayList<>();
        if (pivots == null || pivots.isEmpty()) {
            return swings;
        }
        List<PivotPoint> sorted = new ArrayList<>(pivots);
        sorted.sort(Comparator.comparingInt(PivotPoint::getIndex));

        // 최소 반전폭: 변동성(ATR)에 비례하되, 저변동 종목에서도 의미 있는 반전만 남기도록 % 하한을 둔다.
        double minReversal = Math.max(2.0 * atr, currentPrice * 0.03);

        for (PivotPoint p : sorted) {
            if (swings.isEmpty()) {
                swings.add(p);
                continue;
            }
            PivotPoint last = swings.get(swings.size() - 1);
            if (p.getType() == last.getType()) {
                // 같은 방향의 연속 피벗은 더 극단적인 값으로 교체
                boolean moreExtreme = (p.getType() == PivotType.HIGH && p.getPrice() > last.getPrice())
                        || (p.getType() == PivotType.LOW && p.getPrice() < last.getPrice());
                if (moreExtreme) {
                    swings.set(swings.size() - 1, p);
                }
            } else if (Math.abs(p.getPrice() - last.getPrice()) >= minReversal) {
                swings.add(p);
            }
            // 반전폭 미달인 반대 방향 피벗은 노이즈로 보고 버린다
        }
        return swings;
    }
}
