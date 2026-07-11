package wonbin.financial.service.candle.pattern;

import java.util.Optional;
import wonbin.financial.dto.candle.pattern.DetectedPatternDto;

/**
 * 차트 패턴 디텍터 전략 인터페이스. 구현체는 @Component로 등록하면
 * PatternNotificationService가 List로 주입받아 전부 실행한다.
 */
public interface PatternDetector {

    /** 돌파 확정 봉이 이 개수 이내(최근)여야 알림 대상 — 오래된 돌파 재알림 방지. */
    int CONFIRM_WINDOW = 3;
    /** 패턴 폭(봉 개수) 허용 범위. 너무 좁으면 노이즈, 너무 넓으면 이미 소멸한 구조. */
    int MIN_WIDTH = 10;
    int MAX_WIDTH = 130;

    Optional<DetectedPatternDto> detect(PatternContext ctx);

    /** 두 고점(저점)이 "같은 높이"로 인정되는 허용 오차. */
    static double peakTolerance(double atr, double price) {
        return Math.max(0.5 * atr, price * 0.02);
    }

    /** 패턴 깊이(고점-넥라인)의 최소값. 얕은 패턴은 신뢰도가 낮아 제외. */
    static double minDepth(double atr, double price) {
        return Math.max(2.0 * atr, price * 0.03);
    }

    static long timestampAt(PatternContext ctx, int index) {
        if (ctx.getTimestamps() == null || index >= ctx.getTimestamps().size()
                || ctx.getTimestamps().get(index) == null) {
            return 0L;
        }
        return ctx.getTimestamps().get(index);
    }

    static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
