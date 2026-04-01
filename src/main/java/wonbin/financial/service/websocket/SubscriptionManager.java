package wonbin.financial.service.websocket;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionManager {
    private final FinnhubWebSocketClient client;
    //기본 종목
    private final Set<String> defaultSymbols = new HashSet<>(Set.of(
            "AAPL","TSLA","MSFT","NVDA"
    ));
    // 현재 구독 중인 전체 종목
    private final Set<String> subscribedSymbols = new HashSet<>();
    // 종목별 참조 카운트
    private final ConcurrentHashMap<String,Integer> symbolRefCount = new ConcurrentHashMap<>();
    // 유저별 관심 종목
    private final ConcurrentHashMap<String,Set<String>> userWatchlist = new ConcurrentHashMap<>();

    @EventListener(FinnhubConnectedEvent.class)
    public void init() {
        log.info("웹소켓 연결 감지");
        try {
            for(String symbol : defaultSymbols) {
                subscribeInternal(symbol);
                symbolRefCount.put(symbol,1);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void onUserLogin(String userId, List<String> symbols) {
        Set<String> safeSymbols = ConcurrentHashMap.newKeySet();
        safeSymbols.addAll(symbols);
        userWatchlist.put(userId, safeSymbols);

        for (String symbol : symbols) {
            // compute 메서드를 사용하면 카운트 증가를 원자적(Atomic)으로 처리 가능
            symbolRefCount.compute(symbol, (key, count) -> {
                if (count == null || count == 0) {
                    try {
                        subscribeInternal(symbol);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    return 1;
                }
                return count + 1;
            });
        }
    }

    public void onUserLogout(String userId) {
        Set<String> symbols = userWatchlist.remove(userId);
        if (symbols == null) return;
        for (String symbol : symbols) {
            // compute 메서드를 사용하여 카운트 감소를 원자적으로 처리
            symbolRefCount.compute(symbol, (key, count) -> {
                if (count == null) return null; // 발생하면 안 되는 예외 케이스 방어

                int newCount = count - 1;
                if (newCount <= 0 && !defaultSymbols.contains(symbol)) {
                    try {
                        unsubscribeInternal(symbol);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    return null; // Map에서 해당 키를 완전히 제거
                }
                return newCount;
            });
        }
    }

    private void subscribeInternal(String symbol) throws Exception {
        if(!subscribedSymbols.contains(symbol)) {
            client.subscribe(symbol);
            subscribedSymbols.add(symbol);
        }
    }

    private void unsubscribeInternal(String symbol) throws Exception {
        if(subscribedSymbols.contains(symbol)) {
            client.unsubscribe(symbol);
            subscribedSymbols.remove(symbol);
        }
    }
}
