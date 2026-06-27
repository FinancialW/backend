package wonbin.financial.service.candle;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import wonbin.financial.dto.candle.LevelCandidate;
import wonbin.financial.service.candle.indicator.TechnicalIndicators;

/**
 * 거래량 매물대(Volume Profile) 분석.
 * 가격대를 버킷으로 나누고 각 버킷에 쌓인 거래량을 합산해, 거래가 가장 몰린 가격(POC)과
 * 그 다음으로 거래가 두꺼운 가격대(HVN)를 강한 지지/저항 후보로 만든다.
 * 실전에서 매물대는 가장 신뢰도 높은 지지/저항 근거 중 하나다.
 */
@Service
public class VolumeProfileAnalyzer {

    private static final int BUCKET_COUNT = 50;
    private static final double POC_BASE_WEIGHT = 6.0;  // 매물대 후보의 기본 가중(피벗 군집과 비슷한 스케일)
    private static final double HVN_RATIO = 0.6;        // POC 대비 이 비율 이상이면 HVN으로 채택
    private static final int MAX_NODES = 3;             // POC 포함 최대 후보 개수

    /**
     * @param lowerBound,upperBound 관심 가격 범위(현재가 ±N%)로 한정해 노이즈를 줄인다.
     * @param dataSize              최근성 가중에 사용할 전체 봉 개수
     */
    public List<LevelCandidate> compute(List<Double> highs, List<Double> lows, List<Double> volumes,
            double lowerBound, double upperBound, int dataSize) {

        List<LevelCandidate> result = new ArrayList<>();
        if (highs == null || lows == null || volumes == null || upperBound <= lowerBound) {
            return result;
        }

        double range = upperBound - lowerBound;
        double bucketSize = range / BUCKET_COUNT;
        if (bucketSize <= 0) {
            return result;
        }
        double[] bucketVolume = new double[BUCKET_COUNT];

        int n = highs.size();
        for (int i = 0; i < n; i++) {
            Double high = highs.get(i);
            Double low = lows.get(i);
            Double vol = i < volumes.size() ? volumes.get(i) : null;
            if (high == null || low == null || vol == null || vol <= 0) {
                continue;
            }
            // 최근 거래일수록 매물대 신뢰가 높으므로 최근성 가중을 곱한다.
            double effVolume = vol * TechnicalIndicators.recencyWeight(i, dataSize);

            // 봉의 저가~고가가 걸치는 버킷들에 거래량을 균등 배분한다.
            double barLow = Math.max(low, lowerBound);
            double barHigh = Math.min(high, upperBound);
            if (barHigh < barLow) {
                continue; // 관심 범위 밖
            }
            int fromBucket = bucketIndex(barLow, lowerBound, bucketSize);
            int toBucket = bucketIndex(barHigh, lowerBound, bucketSize);
            int spanned = toBucket - fromBucket + 1;
            double share = effVolume / spanned;
            for (int b = fromBucket; b <= toBucket; b++) {
                bucketVolume[b] += share;
            }
        }

        // POC(최대 거래량 버킷) 탐색
        int pocBucket = -1;
        double pocVolume = 0;
        for (int b = 0; b < BUCKET_COUNT; b++) {
            if (bucketVolume[b] > pocVolume) {
                pocVolume = bucketVolume[b];
                pocBucket = b;
            }
        }
        if (pocBucket < 0 || pocVolume <= 0) {
            return result;
        }

        // POC + 지역 최대(local maxima) HVN 후보 수집
        List<int[]> nodeBuckets = new ArrayList<>(); // {bucketIndex}
        nodeBuckets.add(new int[]{pocBucket});
        for (int b = 1; b < BUCKET_COUNT - 1; b++) {
            if (b == pocBucket) {
                continue;
            }
            boolean localMax = bucketVolume[b] >= bucketVolume[b - 1]
                    && bucketVolume[b] >= bucketVolume[b + 1];
            if (localMax && bucketVolume[b] >= pocVolume * HVN_RATIO) {
                nodeBuckets.add(new int[]{b});
            }
        }

        // 거래량 큰 순으로 정렬 후 상위 MAX_NODES개만 채택
        nodeBuckets.sort((a, c) -> Double.compare(bucketVolume[c[0]], bucketVolume[a[0]]));
        int limit = Math.min(MAX_NODES, nodeBuckets.size());
        for (int k = 0; k < limit; k++) {
            int b = nodeBuckets.get(k)[0];
            double price = lowerBound + (b + 0.5) * bucketSize; // 버킷 중심가
            double weight = POC_BASE_WEIGHT * (bucketVolume[b] / pocVolume);
            String source = (b == pocBucket) ? "volumePOC" : "volumeHVN";
            result.add(new LevelCandidate(price, weight, source, 0));
        }
        return result;
    }

    private int bucketIndex(double price, double lowerBound, double bucketSize) {
        int idx = (int) ((price - lowerBound) / bucketSize);
        if (idx < 0) {
            return 0;
        }
        if (idx >= BUCKET_COUNT) {
            return BUCKET_COUNT - 1;
        }
        return idx;
    }
}
