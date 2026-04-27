package wonbin.financial.dto.candle;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class SupportResistanceZone {
    private double topPrice;
    private double bottomPrice;
    private double avgPrice;
    private int touchCount;
}
