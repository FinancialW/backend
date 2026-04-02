package wonbin.financial.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import wonbin.financial.constant.Timeframe;

@Entity
@Table(
        name = "candle",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_symbol_timeframe_timestamp",
                        columnNames = {"symbol", "timeframe", "timestamp"}
                )
        },
        indexes = {
                @Index(
                        name = "idx_symbol_timeframe_timestamp",
                        columnList = "symbol, timeframe, timestamp"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Getter
public class Candle {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    //종목
    @Column(nullable = false,length=20)
    private String symbol;

    //봉타입(시봉, 일봉, 월봉)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length=10)
    private Timeframe timeframe;

    @Column(nullable = false)
    private Long timestamp;

    //OHLC
    @Column(nullable = false)
    private Double open;

    @Column(nullable = false)
    private Double high;

    @Column(nullable = false)
    private Double low;

    @Column(nullable = false)
    private Double close;

    //거래량
    @Column(nullable = false)
    private Double volume;
}
