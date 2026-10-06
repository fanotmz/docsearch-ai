package be.fanotmz.docsearch.search;

public class SearchValidationException extends RuntimeException {
    private final String code;

    public SearchValidationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
