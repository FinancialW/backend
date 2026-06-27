package wonbin.financial.service.candle;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import wonbin.financial.dto.candle.LevelCandidate;
import wonbin.financial.dto.candle.SupportResistanceZone;

/**
 * 컨플루언스 엔진.
 * 여러 신호 소스(피벗/매물대/이평선/추세선)에서 나온 후보선들을 가격 근접도로 묶고,
 * "서로 다른 종류의 근거가 한자리에 겹칠수록" 강도를 가산해 최종 지지/저항 존을 만든다.
 * 고수들이 '여러 근거가 겹치는 자리'에서 매매를 결정하는 방식을 점수로 모델링한 것.
 */
@Service
public class ConfluenceEngine {

    private static final double DIVERSITY_BONUS = 0.5; // 서로 다른 소스 종류가 늘 때마다 +50%
    private static final int MAX_ZONES = 8;

    /**
     * @param candidates  모든 소스의 후보선
     * @param mergeEps    이 가격 간격 이내면 같은 존으로 병합
     * @param atr         존 밴드 최소 폭 계산용
     * @param currentPrice 지지/저항 역할 분류 기준
     */
    public List<SupportResistanceZone> merge(List<LevelCandidate> candidates, double mergeEps,
            double atr, double currentPrice) {

        List<SupportResistanceZone> zones = new ArrayList<>();
        if (candidates == null || candidates.isEmpty()) {
            return zones;
        }

        // 가격 오름차순 정렬 후, 가격 간격이 mergeEps를 넘으면 새 군집으로 끊는다(체이닝 군집).
        List<LevelCandidate> sorted = new ArrayList<>(candidates);
        sorted.sort((a, b) -> Double.compare(a.getPrice(), b.getPrice()));

        List<List<LevelCandidate>> clusters = new ArrayList<>();
        List<LevelCandidate> current = new ArrayList<>();
        double prevPrice = Double.NaN;
        for (LevelCandidate c : sorted) {
            if (current.isEmpty() || (c.getPrice() - prevPrice) <= mergeEps) {
                current.add(c);
            } else {
                clusters.add(current);
                current = new ArrayList<>();
                current.add(c);
            }
            prevPrice = c.getPrice();
        }
        if (!current.isEmpty()) {
            clusters.add(current);
        }

        double maxRaw = 0;
        List<double[]> rawZones = new ArrayList<>(); // {avgPrice, top, bottom, rawScore, touchCount}
        List<List<String>> zoneSources = new ArrayList<>();

        for (List<LevelCandidate> cluster : clusters) {
            double weightSum = 0;
            double weightedPriceSum = 0;
            double minPrice = Double.MAX_VALUE;
            double maxPrice = -Double.MAX_VALUE;
            int touchCount = 0;
            Set<String> sourceTags = new LinkedHashSet<>();
            Set<String> sourceTypes = new LinkedHashSet<>();

            for (LevelCandidate c : cluster) {
                weightSum += c.getWeight();
                weightedPriceSum += c.getWeight() * c.getPrice();
                minPrice = Math.min(minPrice, c.getPrice());
                maxPrice = Math.max(maxPrice, c.getPrice());
                touchCount += c.getTouchCount();
                sourceTags.add(c.getSource());
                sourceTypes.add(normalizeType(c.getSource()));
            }
            if (weightSum <= 0) {
                continue;
            }
            double avgPrice = weightedPriceSum / weightSum;
            double diversityMultiplier = 1.0 + DIVERSITY_BONUS * (sourceTypes.size() - 1);
            double rawScore = weightSum * diversityMultiplier;

            // 존 밴드: 후보들의 가격 범위. 너무 얇으면 ATR 기반 최소 폭으로 확장.
            double top = maxPrice;
            double bottom = minPrice;
            double minBand = atr * 0.2;
            if (top - bottom < minBand) {
                top = avgPrice + minBand / 2;
                bottom = avgPrice - minBand / 2;
            }

            rawZones.add(new double[]{avgPrice, top, bottom, rawScore, touchCount});
            zoneSources.add(new ArrayList<>(sourceTags));
            maxRaw = Math.max(maxRaw, rawScore);
        }

        if (maxRaw <= 0) {
            return zones;
        }

        // 상대 강도 0~100 정규화 + 역할(지지/저항) 분류
        for (int i = 0; i < rawZones.size(); i++) {
            double[] z = rawZones.get(i);
            double avgPrice = z[0];
            double strength = 100.0 * z[3] / maxRaw;
            String role = avgPrice <= currentPrice ? "SUPPORT" : "RESISTANCE";

            zones.add(SupportResistanceZone.builder()
                    .avgPrice(round(avgPrice))
                    .topPrice(round(z[1]))
                    .bottomPrice(round(z[2]))
                    .touchCount((int) z[4])
                    .strength(round(strength))
                    .role(role)
                    .sources(zoneSources.get(i))
                    .build());
        }

        // 강도 내림차순 정렬 후 상위 MAX_ZONES개만
        zones.sort((a, b) -> Double.compare(b.getStrength(), a.getStrength()));
        if (zones.size() > MAX_ZONES) {
            zones = new ArrayList<>(zones.subList(0, MAX_ZONES));
        }
        return zones;
    }

    /** 소스 태그를 '종류'로 정규화. ma20/ma60 → "ma", volumePOC/volumeHVN → "volume". */
    private String normalizeType(String source) {
        if (source == null) {
            return "etc";
        }
        if (source.startsWith("ma")) {
            return "ma";
        }
        if (source.startsWith("volume")) {
            return "volume";
        }
        return source; // pivot, trendline 등은 그대로
    }

    private double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
