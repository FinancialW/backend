package wonbin.financial.dto.candle;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PivotPoint {
    private double price;
    private PivotType type;
    private int index;

    public enum PivotType {
        HIGH,LOW
    }
}
