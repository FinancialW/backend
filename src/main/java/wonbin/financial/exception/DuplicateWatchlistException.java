package wonbin.financial.exception;

public class DuplicateWatchlistException extends RuntimeException {
    public DuplicateWatchlistException() {
        super("이미 추가된 종목입니다.");
    }
}
