package wonbin.financial.controller;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import wonbin.financial.entity.Member;
import wonbin.financial.service.AuthService;
import wonbin.financial.service.WatchListService;

@RestController
@RequiredArgsConstructor
public class WatchListController {
    private final WatchListService watchListService;
    private final AuthService authService;
    @GetMapping("/watchlist")
    public List<String> userWatchList(Authentication authentication) {
        Member byKakaoId = authService.findByKakaoId(authentication.getName());
        return watchListService.getUserWatchListSymbols(byKakaoId.getKakaoId());
    }
}
