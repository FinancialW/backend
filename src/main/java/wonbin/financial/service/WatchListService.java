package wonbin.financial.service;

import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
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
}
