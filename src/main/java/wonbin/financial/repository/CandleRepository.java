package wonbin.financial.repository;

import java.util.List;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import wonbin.financial.constant.Timeframe;
import wonbin.financial.entity.Candle;

@Repository
public interface CandleRepository extends JpaRepository<Candle,Long> {
    List<Candle> findBySymbolAndTimeframeOrderByTimestampAsc(String symbol, Timeframe timeframe);
    Candle findTopBySymbolOrderByTimestampDesc(String symbol);

    List<Candle> timeframe(Timeframe timeframe);
    // 특정 종목(symbol)과 봉 타입(timeframe)에 해당하는 모든 timestamp만 Set으로 조회
    @Query("SELECT c.timestamp FROM Candle c WHERE c.symbol = :symbol AND c.timeframe = :timeframe")
    Set<Long> findTimestampsBySymbolAndTimeframe(@Param("symbol") String symbol, @Param("timeframe") Timeframe timeframe);
}
