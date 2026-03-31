package wonbin.financial.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Data;

@Entity
@Data
public class WatchList {
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    private Long id;

    private String userId;
    private String symbol;

    public WatchList(String userId, String symbol) {
        this.userId=userId;
        this.symbol=symbol;
    }
    // JPA용 기본 생성자
    protected WatchList() {}
}
