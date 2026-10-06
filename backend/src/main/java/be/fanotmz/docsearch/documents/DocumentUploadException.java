package be.fanotmz.docsearch.documents;

public class DocumentUploadException extends RuntimeException {
    private final String code;

    public DocumentUploadException(String code, String message) {
        super(message);
        this.code = code;
    }

    public DocumentUploadException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
