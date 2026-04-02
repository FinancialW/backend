package wonbin.financial.controller;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import wonbin.financial.entity.Member;
import wonbin.financial.service.oauth.AuthService;
import wonbin.financial.service.oauth.WatchListService;

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

    @PostMapping("/watchlist")
    public ResponseEntity<?> likeWatchlist(Authentication authentication,
                                              @RequestParam(required = false, name="symbol") String symbol) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication.getName().equals("anonymousUser")) {
            throw new RuntimeException("인증되지 않은 사용자");
        }

        if(symbol==null || symbol.isBlank()) {
            throw new IllegalArgumentException("Symbol이 비어있습니다.");
        }
        Member byKakaoId = authService.findByKakaoId(authentication.getName());
        watchListService.saveMemberLike(byKakaoId,symbol);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/watchlist") // refactoring 필요
    public ResponseEntity<?> dislikeWatchlist(Authentication authentication,
                                              @RequestParam(required = false,name="symbol") String symbol) {
        if(symbol==null || symbol.isBlank()) {
            throw new IllegalArgumentException("Symbol이 비어있습니다.");
        }
        Member byKakaoId = authService.findByKakaoId(authentication.getName());
        watchListService.deleteMemberWatchlist(byKakaoId, symbol);
        return ResponseEntity.ok().build();
    }
}
