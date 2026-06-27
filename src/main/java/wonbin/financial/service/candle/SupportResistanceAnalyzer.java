package wonbin.financial.service.candle;

import java.util.ArrayList;
import java.util.List;
import org.apache.commons.math3.ml.clustering.Cluster;
import org.apache.commons.math3.ml.clustering.Clusterable;
import org.apache.commons.math3.ml.clustering.DBSCANClusterer;
import org.springframework.stereotype.Service;
import wonbin.financial.dto.candle.LevelCandidate;
import wonbin.financial.dto.candle.PivotPoint;
import wonbin.financial.dto.candle.PivotPoint.PivotType;
import wonbin.financial.service.candle.indicator.TechnicalIndicators;

/**
 * 수평 지지/저항 후보를 만든다.
 * 1) 스윙 고점/저점(피벗)을 추출하고
 * 2) 가까운 피벗들을 DBSCAN으로 군집화해, 거래량·최근성으로 가중된 후보선(LevelCandidate)으로 변환한다.
 */
@Service
public class SupportResistanceAnalyzer {

    /**
     * 스윙 피벗 추출. 좌우 windowSize 봉보다 모두 높으면(고가) 지역 고점, 모두 낮으면(저가) 지역 저점.
     */
    public List<PivotPoint> extract(List<Double> highs, List<Double> lows, int windowSize) {
        List<PivotPoint> pivots = new ArrayList<>();

        // 데이터가 탐색 범위(좌 + 우 + 기준점)보다 적으면 계산 불가
        if (highs == null || lows == null || highs.size() < (windowSize * 2 + 1)) {
            return pivots;
        }
        int dataSize = highs.size();
        // 양 끝의 windowSize 만큼은 좌우 완벽한 비교가 안 되므로 반복문에서 제외
        for (int i = windowSize; i < dataSize - windowSize; i++) {
            Double currentHigh = highs.get(i);
            Double currentLow = lows.get(i);

            // 야후 파이낸스 데이터의 휴장일(null) 처리
            if (currentHigh == null || currentLow == null) {
                continue;
            }

            boolean isPivotHigh = true;
            boolean isPivotLow = true;

            for (int j = i - windowSize; j <= i + windowSize; j++) {
                if (i == j) {
                    continue; // 자기 자신은 비교 제외
                }
                Double compareHigh = highs.get(j);
                Double compareLow = lows.get(j);
                if (compareHigh == null || compareLow == null) {
                    continue;
                }
                // 주변에 나보다 더 높은 고가가 있다면, 나는 지역 고점이 아님
                if (currentHigh < compareHigh) {
                    isPivotHigh = false;
                }
                // 주변에 나보다 더 낮은 저가가 있다면, 나는 지역 저점이 아님
                if (currentLow > compareLow) {
                    isPivotLow = false;
                }
            }

            if (isPivotHigh) {
                pivots.add(new PivotPoint(currentHigh, PivotType.HIGH, i));
            }
            if (isPivotLow) {
                pivots.add(new PivotPoint(currentLow, PivotType.LOW, i));
            }
        }
        return pivots;
    }

    /**
     * 피벗들을 가격 근접도로 군집화하여 가중 후보선으로 변환한다.
     * 각 피벗의 가중치 = 최근성(오래될수록 감쇠) × 거래량비(거래 많은 날의 터치일수록 신뢰↑).
     * 군집의 후보 가격은 가중 평균, 가중치는 합산, touchCount는 피벗 개수.
     *
     * @param volumes   봉별 거래량(피벗 index로 참조)
     * @param avgVolume 거래량 정규화 기준
     * @param dataSize  전체 봉 개수(최근성 계산용)
     * @param epsilon   군집 반경(가격 단위)
     * @param minPts    군집 최소 밀도
     */
    public List<LevelCandidate> clusterWeightedCandidates(List<PivotPoint> pivots,
            List<Double> volumes, double avgVolume, int dataSize, double epsilon, int minPts) {

        List<WeightedPoint> points = new ArrayList<>();
        for (PivotPoint p : pivots) {
            double recency = TechnicalIndicators.recencyWeight(p.getIndex(), dataSize);
            double volRatio = 1.0;
            if (avgVolume > 0 && volumes != null && p.getIndex() < volumes.size()) {
                Double v = volumes.get(p.getIndex());
                if (v != null && v > 0) {
                    // 거래량비는 0.3~3.0으로 제한해 극단값이 한 점을 지배하지 못하게 한다.
                    volRatio = Math.max(0.3, Math.min(3.0, v / avgVolume));
                }
            }
            points.add(new WeightedPoint(p.getPrice(), recency * volRatio));
        }

        DBSCANClusterer<WeightedPoint> clusterer = new DBSCANClusterer<>(epsilon, minPts);
        List<Cluster<WeightedPoint>> clusters = clusterer.cluster(points);

        List<LevelCandidate> candidates = new ArrayList<>();
        for (Cluster<WeightedPoint> cluster : clusters) {
            List<WeightedPoint> members = cluster.getPoints();
            double weightSum = 0;
            double weightedPriceSum = 0;
            for (WeightedPoint wp : members) {
                weightSum += wp.weight;
                weightedPriceSum += wp.weight * wp.price;
            }
            if (weightSum <= 0) {
                continue;
            }
            double avgPrice = weightedPriceSum / weightSum;
            candidates.add(new LevelCandidate(avgPrice, weightSum, "pivot", members.size()));
        }
        return candidates;
    }

    /** DBSCAN 입력용 래퍼. 가격은 군집 좌표, weight는 후보 가중치 누적에 사용한다. */
    private static class WeightedPoint implements Clusterable {
        private final double price;
        private final double weight;

        WeightedPoint(double price, double weight) {
            this.price = price;
            this.weight = weight;
        }

        @Override
        public double[] getPoint() {
            return new double[]{price};
        }
    }
}
