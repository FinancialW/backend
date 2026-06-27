package wonbin.financial.exception;

public class ResistanceLineException extends RuntimeException {
    public ResistanceLineException() {
        super("지지/저하선 저장 중 오류가 발생했습니다.");
    }
}
