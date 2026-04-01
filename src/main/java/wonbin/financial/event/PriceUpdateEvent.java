package wonbin.financial.event;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PriceUpdateEvent {
    private String symbol;
    private double price;
}
