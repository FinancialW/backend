package wonbin.financial.exception;

/** 카카오 API가 401을 반환한 경우(액세스 토큰 만료). 갱신 후 1회 재시도 신호로만 사용한다. */
public class KakaoUnauthorizedException extends RuntimeException {
    public KakaoUnauthorizedException() {
        super("카카오 액세스 토큰이 만료되었습니다.");
    }
}
