package wonbin.financial.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import wonbin.financial.constant.PatternType;
import wonbin.financial.entity.DetectedPatternEntity;

@Repository
public interface DetectedPatternRepository extends JpaRepository<DetectedPatternEntity, Long> {
    // 데이터가 쌓이며 극점 인덱스가 1~2봉 흔들릴 수 있어 ±허용 범위로 중복을 검사한다
    boolean existsBySymbolAndPatternTypeAndKeyPivotTimestampBetween(
            String symbol, PatternType patternType, long from, long to);
}
