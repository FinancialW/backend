package wonbin.financial.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import wonbin.financial.constant.Timeframe;
import wonbin.financial.entity.Candle;

@Repository
public interface CandleRepository extends JpaRepository<Candle,Long> {
    List<Candle> findBySymbolAndTimeframeOrderByTimestampAsc(String symbol, Timeframe timeframe);

    Optional<Candle> findBySymbolAndTimeframeAndTimestamp(
            String symbol,
            Timeframe timeframe,
            Long timestamp
    );

    boolean existsBySymbolAndTimeframeAndTimestamp(
            String symbol,
            Timeframe timeframe,
            Long timestamp
    );
}
