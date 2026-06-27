package wonbin.financial.dto.candle;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 컨플루언스로 합쳐진 지지/저항 존 한 개.
 * top/bottom/avg/touchCount는 기존 호환 필드이며, 아래 세 필드가 고도화로 추가된 정보다.
 * - strength: 0~100 상대 강도(여러 근거가 겹칠수록 높음)
 * - role: 현재가 기준 SUPPORT(아래) / RESISTANCE(위)
 * - sources: 이 존을 형성한 근거 태그 목록(예: ["pivot","ma20","trendline"])
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupportResistanceZone {
    private double topPrice;
    private double bottomPrice;
    private double avgPrice;
    private int touchCount;
    private double strength;
    private String role;
    private List<String> sources;
}
