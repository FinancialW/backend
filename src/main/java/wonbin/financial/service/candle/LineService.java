package wonbin.financial.service.candle;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import wonbin.financial.entity.SupportResistanceEntity;
import wonbin.financial.exception.ResistanceLineException;
import wonbin.financial.repository.SupportResistanceRepository;

/**
 * 지지/저항 분석 결과(JSON)를 종목별로 upsert 하는 영속화 헬퍼.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LineService {
    private final SupportResistanceRepository supportResistanceRepository;

    @Transactional
    public void saveOrUpdate(String symbol, String zonesJson) {
        try {
            SupportResistanceEntity supportEntity = supportResistanceRepository.findBySymbol(symbol)
                    .orElse(null);
            if (supportEntity == null) {
                supportEntity = SupportResistanceEntity.builder()
                        .symbol(symbol)
                        .zonesJson(zonesJson)
                        .build();
                supportResistanceRepository.save(supportEntity);
                log.info("[{}] 새로운 지지/저항선 데이터 DB 생성 완료", symbol);
            } else {
                supportEntity.updateZones(zonesJson);
                log.info("[{}] 기존 지지/저항선 데이터 DB 갱신 완료", symbol);
            }
        } catch (Exception e) {
            log.error("[{}] 지지/저항선 DB 저장/업데이트 실패: {}", symbol, e.getMessage());
            throw new ResistanceLineException();
        }
    }
}
