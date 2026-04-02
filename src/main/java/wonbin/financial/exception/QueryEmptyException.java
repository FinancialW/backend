package wonbin.financial.exception;

public class QueryEmptyException extends RuntimeException {
    public QueryEmptyException() {
        super("검색창이 비어있습니다.");
    }
}
