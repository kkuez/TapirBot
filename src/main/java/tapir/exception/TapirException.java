package tapir.exception;

public class TapirException extends RuntimeException{

    private String message;
    private Exception e;

    public TapirException(String message, Exception e) {
        this.message = message;
        this.e = e;
    }

    @Override
    public String getMessage() {
        return message;
    }

    public Exception getInnerException() {
        return e;
    }
}
