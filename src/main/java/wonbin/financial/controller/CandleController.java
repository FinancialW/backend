package wonbin.financial.controller;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import wonbin.financial.dto.candle.SupportResistanceZone;
import wonbin.financial.dto.candle.YahooCandleResponse;
import wonbin.financial.service.candle.CandleService;

@RestController
@RequiredArgsConstructor
public class CandleController {
    private final CandleService candleService;
    @GetMapping("/candles")
    public YahooCandleResponse getCandles(
            @RequestParam("symbol") String symbol,
            @RequestParam("resolution") String resolution
    ) {
        return candleService.getCandles(symbol, resolution);
    }

    @GetMapping("/support-resistance")
    public List<SupportResistanceZone> getLevels (@RequestParam(name="symbol") String symbol, @RequestParam(defaultValue = "1Y",name="resolution") String resolution) {
        return candleService.getSupportResistanceZones(symbol, resolution);
    }
}
