package wonbin.financial.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "support_resistance")
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SupportResistanceEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String symbol; // 종목 코드

    @Column(columnDefinition = "TEXT", nullable = false)
    private String zonesJson; // 지지/저항선 리스트를 Json형태로 저장

    private LocalDateTime updateAt; // 마지막 업데이트 시간

    @Builder
    public SupportResistanceEntity(String symbol, String zonesJson) {
        this.symbol = symbol;
        this.zonesJson = zonesJson;
        this.updateAt = LocalDateTime.now();
    }

    public void updateZones(String zonesJson) {
        this.zonesJson = zonesJson;
        this.updateAt = LocalDateTime.now();
    }
}
