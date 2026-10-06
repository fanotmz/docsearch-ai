package be.fanotmz.docsearch.qa;

public class GenerationFailureException extends RuntimeException {
    public GenerationFailureException(String message, Throwable cause) {
        super(message, cause);
    }
}
