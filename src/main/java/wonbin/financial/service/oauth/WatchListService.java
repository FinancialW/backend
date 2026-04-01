package wonbin.financial.service.oauth;

import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import wonbin.financial.entity.Member;
import wonbin.financial.entity.WatchList;
import wonbin.financial.repository.WatchListRepository;

@Service
@RequiredArgsConstructor
public class WatchListService {
    private final WatchListRepository watchListRepository;

    public List<String> getUserWatchListSymbols(String userId) {
        return watchListRepository.findByUserId(userId)
                .stream()
                .map(WatchList::getSymbol)
                .collect(Collectors.toList());
    }

    public void saveMemberLike(Member member, String symbol) {
        if(watchListRepository.existsByUserIdAndSymbol(member.getKakaoId(),symbol)) {
            throw new IllegalStateException("이미 추가된 종목");
        }
        WatchList watchList = new WatchList(member.getKakaoId(), symbol);
        watchListRepository.save(watchList);
    }

    public void deleteMemberWatchlist(Member member, String symbol) {
        WatchList watchList = watchListRepository
                .findByUserIdAndSymbol(member.getKakaoId(), symbol)
                .orElseThrow(() -> new IllegalStateException("존재 하지 않는 종목"));
        watchListRepository.delete(watchList);
    }

}
