package wonbin.financial.dto.candle;

import java.util.List;
import lombok.Data;

@Data
public class FinnhubCandleResponse {
    private List<Double> c; // close
    private List<Double> h; // high
    private List<Double> l; // low
    private List<Double> o; // open
    private List<Long> t; // timestamp
    private List<Double> v; // volume
    private String s; //state
}
