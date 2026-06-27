package wonbin.financial.service.candle;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import wonbin.financial.dto.candle.SupportResistanceZone;
import wonbin.financial.entity.SupportResistanceEntity;
import wonbin.financial.exception.ResistanceLineException;
import wonbin.financial.repository.SupportResistanceRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class LineService {
    private final ObjectMapper objectMapper;
    private final SupportResistanceRepository supportResistanceRepository;
    @Transactional
    public void saveOrUpdate(String symbol, List<SupportResistanceZone> zones) {
        try {
            String zonesJson = objectMapper.writeValueAsString(zones);
            SupportResistanceEntity supportEntity = supportResistanceRepository.findBySymbol(symbol)
                    .orElse(null);
            if(supportEntity == null) {
                supportEntity = SupportResistanceEntity.builder()
                        .symbol(symbol)
                        .zonesJson(zonesJson)
                        .build();
                supportResistanceRepository.save(supportEntity);
                log.info("[{}] 새로운 지지/저항선 데이터 DB 생성 완료",symbol);
            } else {
                supportEntity.updateZones(zonesJson);
                log.info("[{}] 기존 지지/저항선 데이터 DB 갱신 완료",symbol);
            }
        } catch (Exception e) {
            log.error("[{}] 지지/저항선 Db 저장/업데이트 실패: {}", symbol,e.getMessage());
            throw new ResistanceLineException();
        }
    }

}
