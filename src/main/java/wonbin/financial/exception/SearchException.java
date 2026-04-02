package wonbin.financial.exception;

public class SearchException extends RuntimeException {
    public SearchException() {
        super("Finnhub 요청 중 오류 발생");
    }
}
