package wonbin.financial.dto.candle;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SupportResistanceZone {
    private double topPrice;
    private double bottomPrice;
    private double avgPrice;
    private int touchCount;
}
