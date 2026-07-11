package wonbin.financial.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 탐지 대상 차트 패턴 종류. */
@Getter
@RequiredArgsConstructor
public enum PatternType {
    DOUBLE_TOP("이중 천장", false),
    DOUBLE_BOTTOM("이중 바닥", true),
    HEAD_AND_SHOULDERS("헤드앤숄더", false),
    INVERSE_HEAD_AND_SHOULDERS("역헤드앤숄더", true),
    CUP_AND_HANDLE("컵앤핸들", true);

    private final String koreanName;
    private final boolean bullish; // true = 상승 반전, false = 하락 반전
}
