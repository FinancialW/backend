package wonbin.financial.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import wonbin.financial.entity.SupportResistanceEntity;

public interface SupportResistanceRepository extends JpaRepository<SupportResistanceEntity,Long> {
    Optional<SupportResistanceEntity> findBySymbol(String symbol);
}
