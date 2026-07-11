package wonbin.financial.dto.candle.pattern;

import lombok.Builder;
import lombok.Getter;
import wonbin.financial.constant.PatternType;

/** 패턴 탐지 결과. 넥라인/목표가/무효화 가격은 측정 이동(measured move) 규칙으로 계산된다. */
@Getter
@Builder
public class DetectedPatternDto {
    private final String symbol;
    private final PatternType patternType;
    private final double necklinePrice;      // 돌파 시점의 넥라인 값
    private final double targetPrice;        // 측정 이동 목표가
    private final double invalidationPrice;  // 패턴 무효화 기준(손절 참고)
    private final double patternExtremePrice; // 머리/두 번째 천장 등 결정적 극점 가격
    private final double breakoutClose;      // 넥라인을 이탈/돌파한 종가
    private final long breakoutTimestamp;
    private final long keyPivotTimestamp;    // 결정적 극점의 봉 timestamp(중복 알림 방지 키)
}
