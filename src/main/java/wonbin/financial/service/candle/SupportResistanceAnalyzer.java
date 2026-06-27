package wonbin.financial.service.candle;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.commons.math3.ml.clustering.Cluster;
import org.apache.commons.math3.ml.clustering.DBSCANClusterer;
import org.apache.commons.math3.ml.clustering.DoublePoint;
import org.springframework.stereotype.Service;
import wonbin.financial.dto.candle.PivotPoint;
import wonbin.financial.dto.candle.PivotPoint.PivotType;
import wonbin.financial.dto.candle.SupportResistanceZone;

@Service
@RequiredArgsConstructor
public class SupportResistanceAnalyzer {
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
            if (currentHigh == null || currentLow == null) continue;

            boolean isPivotHigh = true;
            boolean isPivotLow = true;

            for (int j = i - windowSize; j <= i + windowSize; j++) {
                if (i == j) continue; // 자기 자신은 비교 제외

                Double compareHigh = highs.get(j);
                Double compareLow = lows.get(j);

                if (compareHigh == null || compareLow == null) continue;

                // 주변에 나보다 더 높은 고가가 있다면, 나는 지역 고점이 아님
                if (currentHigh < compareHigh) {
                    isPivotHigh = false;
                }
                // 주변에 나보다 더 낮은 저가가 있다면, 나는 지역 저점이 아님
                if (currentLow > compareLow) {
                    isPivotLow = false;
                }
            }

            // 조건에 부합하면 결과 리스트에 담기
            if (isPivotHigh) {
                pivots.add(new PivotPoint(currentHigh, PivotType.HIGH, i));
            }
            if (isPivotLow) {
                pivots.add(new PivotPoint(currentLow, PivotType.LOW, i));
            }
        }
        return pivots;
    }
    public List<SupportResistanceZone> clusterPivotsAsZone(List<PivotPoint> pivots, double epsilon, int minPts) {
        List<DoublePoint> points = pivots.stream()
                .map(p -> new DoublePoint(new double[]{p.getPrice()}))
                .collect(Collectors.toList());
        DBSCANClusterer<DoublePoint> clusterer = new DBSCANClusterer<>(epsilon, minPts);
        List<Cluster<DoublePoint>> clusters = clusterer.cluster(points);
        List<SupportResistanceZone> zones = new ArrayList<>();
        for (Cluster<DoublePoint> cluster : clusters) {
            List<DoublePoint> clusteredPoints = cluster.getPoints();
            // 1. 군집 내 최댓값 (Zone 상단)
            double topPrice = clusteredPoints.stream()
                    .mapToDouble(p -> p.getPoint()[0])
                    .max()
                    .orElse(0.0);

            // 2. 군집 내 최솟값 (Zone 하단)
            double bottomPrice = clusteredPoints.stream()
                    .mapToDouble(p -> p.getPoint()[0])
                    .min()
                    .orElse(0.0);
            // 3. 중심 가격 (선형 차트 호환용)
            double avgPrice = clusteredPoints.stream()
                    .mapToDouble(p -> p.getPoint()[0])
                    .average()
                    .orElse(0.0);

            // Zone 객체로 저장
            zones.add(new SupportResistanceZone(topPrice, bottomPrice, avgPrice, clusteredPoints.size()));
        }

        zones.sort((a, b) -> Integer.compare(b.getTouchCount(), a.getTouchCount()));
        return zones;
    }
}

