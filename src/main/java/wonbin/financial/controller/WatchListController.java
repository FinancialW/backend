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

    @PostMapping("/watchlist/like")
    public ResponseEntity<?> likeWatchlist(Authentication authentication,
                                              @RequestParam(required = false, name="symbol") String symbol) {
        if(symbol==null || symbol.isBlank()) {
            return ResponseEntity.badRequest().body("Symbol이 비어있습니다");
        }
        try {
            Member byKakaoId = authService.findByKakaoId(authentication.getName());
            watchListService.saveMemberLike(byKakaoId,symbol);
            return ResponseEntity.ok().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/watchlist/dislike")
    public ResponseEntity<?> dislikeWatchlist(Authentication authentication,
                                              @RequestParam(required = false,name="symbol") String symbol) {
        if(symbol==null || symbol.isBlank()) {
            return ResponseEntity.badRequest().body("symbol이 비어있습니다");
        }
        try {
            Member byKakaoId = authService.findByKakaoId(authentication.getName());
            watchListService.deleteMemberWatchlist(byKakaoId,symbol);
            return ResponseEntity.ok().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
