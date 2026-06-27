package wonbin.financial.service.candle.indicator;

import java.util.List;

/**
 * 기술적 지표 계산용 순수 함수 모음. 상태가 없으므로 static 유틸로 둔다.
 * 모든 메서드는 야후 데이터의 휴장일(null)을 방어한다.
 */
public final class TechnicalIndicators {

    private TechnicalIndicators() {
    }

    /**
     * 단순이동평균(SMA). 최근 period개의 종가 평균.
     * 데이터가 부족하거나 구간 내 결측(null)이 있으면 신뢰할 수 없으므로 null 반환.
     */
    public static Double sma(List<Double> closes, int period) {
        if (closes == null || period <= 0 || closes.size() < period) {
            return null;
        }
        double sum = 0;
        for (int i = closes.size() - period; i < closes.size(); i++) {
            Double c = closes.get(i);
            if (c == null) {
                return null;
            }
            sum += c;
        }
        return sum / period;
    }

    /**
     * ATR(Average True Range). 최근 period개 True Range의 평균.
     * 변동성에 비례해 군집 반경을 잡는 데 사용한다.
     */
    public static double atr(List<Double> highs, List<Double> lows, List<Double> closes, int period) {
        int n = highs.size();
        if (n < period + 1) {
            return 0.0;
        }
        double sum = 0;
        int count = 0;
        for (int i = n - period; i < n; i++) {
            if (highs.get(i) == null || lows.get(i) == null || closes.get(i - 1) == null) {
                continue;
            }
            double high = highs.get(i);
            double low = lows.get(i);
            double prevClose = closes.get(i - 1);

            double tr = Math.max(high - low,
                    Math.max(Math.abs(high - prevClose), Math.abs(low - prevClose)));
            sum += tr;
            count++;
        }
        return count > 0 ? sum / count : 0.0;
    }

    /** 가장 최근의 유효한(null 아님) 종가. */
    public static double latestValidClose(List<Double> closes) {
        for (int i = closes.size() - 1; i >= 0; i--) {
            if (closes.get(i) != null) {
                return closes.get(i);
            }
        }
        return 0.0;
    }

    /** 유효 거래량의 평균. 거래량 가중치를 정규화하는 기준값으로 쓴다. */
    public static double averageVolume(List<Double> volumes) {
        if (volumes == null) {
            return 0.0;
        }
        double sum = 0;
        int count = 0;
        for (Double v : volumes) {
            if (v != null && v > 0) {
                sum += v;
                count++;
            }
        }
        return count > 0 ? sum / count : 0.0;
    }

    /**
     * 최근성 가중치. 최신 봉일수록 1.0에 가깝고, 오래될수록 지수적으로 감쇠한다.
     * 11개월 전 터치를 지난주 터치와 동일하게 취급하지 않기 위함.
     *
     * @param index    해당 봉의 인덱스(0 = 가장 과거)
     * @param dataSize 전체 봉 개수
     */
    public static double recencyWeight(int index, int dataSize) {
        if (dataSize <= 1) {
            return 1.0;
        }
        double ageFraction = (double) (dataSize - 1 - index) / (dataSize - 1);
        return Math.exp(-1.5 * ageFraction); // 최신=1.0, 가장 과거≈0.22
    }
}
