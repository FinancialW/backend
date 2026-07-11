package wonbin.financial.controller;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import wonbin.financial.dto.candle.pattern.DetectedPatternDto;
import wonbin.financial.service.notification.PatternNotificationService;

@RestController
@RequiredArgsConstructor
public class PatternController {
    private final PatternNotificationService patternNotificationService;

    /**
     * 개발용 수동 트리거 — 배포 전 반드시 제거하거나 인증을 걸어야 한다.
     * symbol이 있으면 해당 심볼만 탐지(dryRun 기본), 없으면 전체 배치를 즉시 실행한다.
     */
    @PostMapping("/test/patterns/run")
    public ResponseEntity<?> run(@RequestParam(required = false) String symbol,
                                 @RequestParam(defaultValue = "true") boolean dryRun) {
        if (symbol != null && !symbol.isBlank()) {
            List<DetectedPatternDto> detections =
                    patternNotificationService.detectForSymbol(symbol, dryRun);
            return ResponseEntity.ok(detections);
        }
        patternNotificationService.detectAndNotifyAll();
        return ResponseEntity.ok("전체 패턴 탐지 배치 완료");
    }
}
