package wonbin.financial.service.candle;

import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import wonbin.financial.service.websocket.SubscriptionManager;

@Slf4j
@Service
@RequiredArgsConstructor
public class Schedulerservice {
    private final CandleService candleService;
    private final SubscriptionManager subscriptionManager;
    private final LevelAnalysisService levelAnalysisService;

    @Scheduled(cron = "0 0 6 * * *", zone = "Asia/Seoul")
    public void updateDailyCandles() {
        Set<String> subscribedSymbols = subscriptionManager.getSubscribedSymbols();
        if(subscribedSymbols.isEmpty()) {
            log.info("구독 중인 정보가 없어서 일부 업데이트를 건너뜁니다.");
        }

        log.info("일봉 배치 업데이트. 크기:{}",subscribedSymbols.size());
        int successCount = 0;
        int failCount=0;
        for(String symbol : subscribedSymbols) {
            try {
                candleService.updateDailyCandle(symbol);
                successCount++;
                Thread.sleep(100);
            } catch (InterruptedException e) {
                log.warn("배치 스레드가 인터럽트 되었습니다.",e);
                Thread.currentThread().interrupt(); // 인터럽트 상태 복구
                break;
            } catch (Exception e) {
                log.error("[{}] 일봉 업데이트 중 오류 발생:{}", symbol, e.getMessage());
                failCount++;
            }
        }
        log.info("일봉 배치 업데이트 완료. 성공:{}, 실패:{}", successCount, failCount);
    }

    @Scheduled(cron = "0 15 6 * * *")
    public void updateSupportResistance() {
        String resolution = "1Y";
        Set<String> subscribedSymbols = subscriptionManager.getSubscribedSymbols();
        if(subscribedSymbols.isEmpty()) {
            log.info("구독 중인 정보가 없어서 일부 업데이트를 건너뜁니다.");
            return;
        }
        log.info("새벽 6시 15분: 총 {}개 종목의 지지선/저항성 업데이트 배치를 시작합니다.",subscribedSymbols.size());
        for(String symbol : subscribedSymbols) {
            try {
                levelAnalysisService.refresh(symbol, resolution);
            } catch (Exception e) {
                log.error("[{}] 지지/저항선 업데이트 중 오류 발생: {}",symbol, e.getMessage());
            }
        }
    }
}
