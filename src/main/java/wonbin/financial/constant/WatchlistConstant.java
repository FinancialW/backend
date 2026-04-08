package wonbin.financial.constant;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class WatchlistConstant {
    //기본 종목
    public final Set<String> defaultSymbols = new HashSet<>(Set.of(
            "AAPL","TSLA","MSFT","NVDA"
    ));
    // 현재 구독 중인 전체 종목
    public final Set<String> subscribedSymbols = new HashSet<>();
    // 종목별 참조 카운트
    public final ConcurrentHashMap<String,Integer> symbolRefCount = new ConcurrentHashMap<>();
    // 유저별 관심 종목
    public final ConcurrentHashMap<String,Set<String>> userWatchlist = new ConcurrentHashMap<>();
    private WatchlistConstant(){}
}
