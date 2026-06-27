package wonbin.financial.controller;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import wonbin.financial.dto.candle.LevelAnalysisResponse;
import wonbin.financial.dto.candle.SupportResistanceZone;
import wonbin.financial.dto.candle.YahooCandleResponse;
import wonbin.financial.service.candle.CandleService;
import wonbin.financial.service.candle.LevelAnalysisService;

@RestController
@RequiredArgsConstructor
public class CandleController {
    private final CandleService candleService;
    private final LevelAnalysisService levelAnalysisService;

    @GetMapping("/candles")
    public YahooCandleResponse getCandles(
            @RequestParam("symbol") String symbol,
            @RequestParam("resolution") String resolution
    ) {
        return candleService.getCandles(symbol, resolution);
    }

    // 컨플루언스 지지/저항 존 목록(기존 호환).
    @GetMapping("/support-resistance")
    public List<SupportResistanceZone> getLevels(
            @RequestParam(name = "symbol") String symbol,
            @RequestParam(defaultValue = "1Y", name = "resolution") String resolution) {
        return levelAnalysisService.getLevels(symbol, resolution);
    }

    // 종합 분석: 지지/저항 존 + 추세선 + 이동평균선 + 현재가/변동성.
    @GetMapping("/support-resistance/analysis")
    public LevelAnalysisResponse getAnalysis(
            @RequestParam(name = "symbol") String symbol,
            @RequestParam(defaultValue = "1Y", name = "resolution") String resolution) {
        return levelAnalysisService.getAnalysis(symbol, resolution);
    }
}
