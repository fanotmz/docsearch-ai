package be.fanotmz.docsearch.qa;

public class QaValidationException extends RuntimeException {
    private final String code;

    public QaValidationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
