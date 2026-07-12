package wonbin.financial.constant;

import java.util.List;

/**
 * 매일 패턴 탐지 배치가 관심종목과 무관하게 스캔하는 미국 대형주 유니버스.
 * 나스닥·S&P 시가총액 상위권 스냅샷(2026-07 기준)이며, 순위 변동 시 수동으로 갱신한다.
 * 티커는 Yahoo Finance 차트 API 형식을 따른다(예: BRK-B).
 */
public final class TopSymbols {

    public static final List<String> TOP_50 = List.of(
            "AAPL", "MSFT", "NVDA", "GOOGL", "AMZN",
            "META", "AVGO", "TSLA", "BRK-B", "LLY",
            "JPM", "V", "XOM", "UNH", "MA",
            "COST", "HD", "PG", "NFLX", "JNJ",
            "ABBV", "BAC", "CRM", "ORCL", "CVX",
            "WMT", "KO", "AMD", "PEP", "ADBE",
            "ACN", "CSCO", "MRK", "TMO", "LIN",
            "MCD", "INTU", "IBM", "GE", "ISRG",
            "QCOM", "CAT", "TXN", "VZ", "AXP",
            "PLTR", "DIS", "PM", "GS", "NOW"
    );

    private TopSymbols() {
    }
}
