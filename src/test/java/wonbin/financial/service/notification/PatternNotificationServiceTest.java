package wonbin.financial.service.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import wonbin.financial.constant.PatternType;
import wonbin.financial.dto.candle.YahooCandleResponse;
import wonbin.financial.dto.candle.pattern.DetectedPatternDto;
import wonbin.financial.entity.DetectedPatternEntity;
import wonbin.financial.entity.Member;
import wonbin.financial.entity.WatchList;
import wonbin.financial.exception.KakaoUnauthorizedException;
import wonbin.financial.repository.DetectedPatternRepository;
import wonbin.financial.repository.KakaoMemberRepository;
import wonbin.financial.repository.WatchListRepository;
import wonbin.financial.service.candle.CandleService;
import wonbin.financial.service.candle.SupportResistanceAnalyzer;
import wonbin.financial.service.candle.pattern.PatternContext;
import wonbin.financial.service.candle.pattern.PatternDetector;
import wonbin.financial.service.candle.pattern.ZigZagFilter;
import wonbin.financial.service.oauth.KakaoTokenManager;

@ExtendWith(MockitoExtension.class)
class PatternNotificationServiceTest {

    @Mock CandleService candleService;
    @Mock SupportResistanceAnalyzer analyzer;
    @Mock ZigZagFilter zigZagFilter;
    @Mock PatternDetector detector;
    @Mock DetectedPatternRepository detectedPatternRepository;
    @Mock WatchListRepository watchListRepository;
    @Mock KakaoMemberRepository kakaoMemberRepository;
    @Mock KakaoTokenManager kakaoTokenManager;
    @Mock KakaoMessageService kakaoMessageService;

    private PatternNotificationService service;

    private final DetectedPatternDto dto = DetectedPatternDto.builder()
            .symbol("AAPL")
            .patternType(PatternType.DOUBLE_TOP)
            .necklinePrice(90.0)
            .targetPrice(79.75)
            .invalidationPrice(100.5)
            .patternExtremePrice(100.5)
            .breakoutClose(89.0)
            .breakoutTimestamp(1_700_003_600L)
            .keyPivotTimestamp(1_700_000_000L)
            .build();

    @BeforeEach
    void setUp() {
        service = new PatternNotificationService(candleService, analyzer, zigZagFilter,
                List.of(detector), detectedPatternRepository, watchListRepository,
                kakaoMemberRepository, kakaoTokenManager, kakaoMessageService);
        ReflectionTestUtils.setField(service, "frontBaseUrl", "http://localhost:5173");

        when(candleService.getCandles(eq("AAPL"), anyString())).thenReturn(candleResponse(30));
        when(analyzer.extract(anyList(), anyList(), anyInt())).thenReturn(List.of());
        when(zigZagFilter.filter(anyList(), anyDouble(), anyDouble())).thenReturn(List.of());
        when(detector.detect(any(PatternContext.class))).thenReturn(Optional.of(dto));
    }

    @Test
    @DisplayName("이미 감지된 패턴(dedup)은 저장도 발송도 하지 않는다")
    void duplicateSkipped() {
        when(detectedPatternRepository.existsBySymbolAndPatternTypeAndKeyPivotTimestampBetween(
                anyString(), any(), anyLong(), anyLong())).thenReturn(true);

        List<DetectedPatternDto> result = service.detectForSymbol("AAPL", false);

        assertThat(result).isEmpty();
        verify(detectedPatternRepository, never()).save(any());
        verify(kakaoMessageService, never()).sendToMe(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("신규 패턴은 저장 후 심볼 워처 전원에게 발송한다")
    void newPatternNotifiesAllWatchers() {
        when(detectedPatternRepository.existsBySymbolAndPatternTypeAndKeyPivotTimestampBetween(
                anyString(), any(), anyLong(), anyLong())).thenReturn(false);
        when(detectedPatternRepository.save(any(DetectedPatternEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(watchListRepository.findBySymbol("AAPL"))
                .thenReturn(List.of(new WatchList("1", "AAPL"), new WatchList("2", "AAPL")));
        Member m1 = memberWithKakaoId("1");
        Member m2 = memberWithKakaoId("2");
        when(kakaoMemberRepository.findByKakaoId("1")).thenReturn(Optional.of(m1));
        when(kakaoMemberRepository.findByKakaoId("2")).thenReturn(Optional.of(m2));
        when(kakaoTokenManager.getValidAccessToken(any())).thenReturn(Optional.of("token"));

        List<DetectedPatternDto> result = service.detectForSymbol("AAPL", false);

        assertThat(result).hasSize(1);
        verify(kakaoMessageService, times(2)).sendToMe(eq("token"), anyString(), anyString());
        // 최초 저장 + notifiedAt 마킹 저장
        verify(detectedPatternRepository, times(2)).save(any(DetectedPatternEntity.class));
    }

    @Test
    @DisplayName("401(토큰 만료)이면 갱신 후 정확히 1회 재시도한다")
    void unauthorizedRetriesOnce() {
        when(detectedPatternRepository.existsBySymbolAndPatternTypeAndKeyPivotTimestampBetween(
                anyString(), any(), anyLong(), anyLong())).thenReturn(false);
        when(detectedPatternRepository.save(any(DetectedPatternEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(watchListRepository.findBySymbol("AAPL"))
                .thenReturn(List.of(new WatchList("1", "AAPL")));
        Member member = memberWithKakaoId("1");
        when(kakaoMemberRepository.findByKakaoId("1")).thenReturn(Optional.of(member));
        when(kakaoTokenManager.getValidAccessToken(member)).thenReturn(Optional.of("stale"));
        doThrow(new KakaoUnauthorizedException())
                .when(kakaoMessageService).sendToMe(eq("stale"), anyString(), anyString());
        when(kakaoTokenManager.refreshAndStore(member)).thenReturn(Optional.of("fresh"));

        service.detectForSymbol("AAPL", false);

        verify(kakaoTokenManager, times(1)).refreshAndStore(member);
        verify(kakaoMessageService, times(1)).sendToMe(eq("fresh"), anyString(), anyString());
    }

    @Test
    @DisplayName("카카오 토큰이 없는 사용자는 건너뛰고 나머지에게는 발송한다")
    void memberWithoutTokenSkipped() {
        when(detectedPatternRepository.existsBySymbolAndPatternTypeAndKeyPivotTimestampBetween(
                anyString(), any(), anyLong(), anyLong())).thenReturn(false);
        when(detectedPatternRepository.save(any(DetectedPatternEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(watchListRepository.findBySymbol("AAPL"))
                .thenReturn(List.of(new WatchList("1", "AAPL"), new WatchList("2", "AAPL")));
        Member noToken = memberWithKakaoId("1");
        Member hasToken = memberWithKakaoId("2");
        when(kakaoMemberRepository.findByKakaoId("1")).thenReturn(Optional.of(noToken));
        when(kakaoMemberRepository.findByKakaoId("2")).thenReturn(Optional.of(hasToken));
        when(kakaoTokenManager.getValidAccessToken(noToken)).thenReturn(Optional.empty());
        when(kakaoTokenManager.getValidAccessToken(hasToken)).thenReturn(Optional.of("token"));

        service.detectForSymbol("AAPL", false);

        verify(kakaoMessageService, times(1)).sendToMe(eq("token"), anyString(), anyString());
    }

    @Test
    @DisplayName("dryRun이면 저장/발송 없이 탐지 결과만 반환한다")
    void dryRunOnlyReturns() {
        List<DetectedPatternDto> result = service.detectForSymbol("AAPL", true);

        assertThat(result).hasSize(1);
        verify(detectedPatternRepository, never()).save(any());
        verify(kakaoMessageService, never()).sendToMe(anyString(), anyString(), anyString());
    }

    private Member memberWithKakaoId(String kakaoId) {
        Member member = new Member();
        member.setKakaoId(kakaoId);
        return member;
    }

    /** 최소 유효 봉 데이터. 값 자체는 중요하지 않고 널 가드 통과용이다. */
    private YahooCandleResponse candleResponse(int size) {
        List<Double> prices = new ArrayList<>();
        List<Long> timestamps = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            prices.add(100.0);
            timestamps.add(1_700_000_000L + i * 86_400L);
        }
        YahooCandleResponse.Quote quote = new YahooCandleResponse.Quote();
        quote.setOpen(prices);
        quote.setHigh(prices);
        quote.setLow(prices);
        quote.setClose(prices);
        quote.setVolume(prices);
        YahooCandleResponse.Indicators indicators = new YahooCandleResponse.Indicators();
        indicators.setQuote(List.of(quote));
        YahooCandleResponse.Result result = new YahooCandleResponse.Result();
        result.setTimestamp(timestamps);
        result.setIndicators(indicators);
        YahooCandleResponse.Chart chart = new YahooCandleResponse.Chart();
        chart.setResult(List.of(result));
        YahooCandleResponse response = new YahooCandleResponse();
        response.setChart(chart);
        return response;
    }
}
