package wonbin.financial.dto.candle;

import java.util.List;
import lombok.Data;

@Data
public class YahooCandleResponse {
    private Chart chart;

    @Data
    public static class Chart {
        private List<Result> result;
    }
    @Data
    public static class Result {
        private List<Long> timestamp;
        private Indicators indicators;
    }
    @Data
    public static class Indicators {
        private List<Quote> quote;
    }
    @Data
    public static class Quote {
        private List<Double> open;
        private List<Double> high;
        private List<Double> low;
        private List<Double> close;
        private List<Double> volume;
    }
}
