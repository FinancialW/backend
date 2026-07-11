package wonbin.financial.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import wonbin.financial.constant.PatternType;

/**
 * 탐지된 차트 패턴 기록. (symbol, patternType, keyPivotTimestamp) 유니크 제약으로
 * 같은 패턴이 매일 재탐지되어도 중복 알림이 나가지 않도록 한다.
 */
@Entity
@Table(name = "detected_pattern",
        uniqueConstraints = @UniqueConstraint(name = "uk_symbol_pattern_key_pivot",
                columnNames = {"symbol", "pattern_type", "key_pivot_timestamp"}))
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DetectedPatternEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(name = "pattern_type", nullable = false, length = 40)
    private PatternType patternType;

    @Column(name = "key_pivot_timestamp", nullable = false)
    private Long keyPivotTimestamp; // 결정적 극점(두 번째 천장/머리)의 봉 timestamp

    private Double necklinePrice;
    private Double targetPrice;
    private Double invalidationPrice;
    private Long breakoutTimestamp;

    private LocalDateTime detectedAt;
    private LocalDateTime notifiedAt; // 알림 발송 시도 완료 시각(미발송이면 null)

    @Builder
    public DetectedPatternEntity(String symbol, PatternType patternType, Long keyPivotTimestamp,
            Double necklinePrice, Double targetPrice, Double invalidationPrice,
            Long breakoutTimestamp) {
        this.symbol = symbol;
        this.patternType = patternType;
        this.keyPivotTimestamp = keyPivotTimestamp;
        this.necklinePrice = necklinePrice;
        this.targetPrice = targetPrice;
        this.invalidationPrice = invalidationPrice;
        this.breakoutTimestamp = breakoutTimestamp;
        this.detectedAt = LocalDateTime.now();
    }

    public void markNotified() {
        this.notifiedAt = LocalDateTime.now();
    }
}
