package be.fanotmz.docsearch.documents;

import java.util.Map;

import be.fanotmz.docsearch.search.SearchValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice
public class DocumentExceptionHandler {
    @ExceptionHandler(DocumentUploadException.class)
    ResponseEntity<Map<String, String>> handleUpload(DocumentUploadException exception) {
        HttpStatus status = switch (exception.getCode()) {
            case "EMPTY_FILE", "MISSING_FILE" -> HttpStatus.BAD_REQUEST;
            case "UNSUPPORTED_MEDIA_TYPE", "NOT_PDF" -> HttpStatus.UNSUPPORTED_MEDIA_TYPE;
            case "MALFORMED_PDF", "NO_EXTRACTABLE_TEXT" -> HttpStatus.UNPROCESSABLE_ENTITY;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        return ResponseEntity.status(status).body(Map.of(
                "code", exception.getCode(),
                "message", exception.getMessage()));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    ResponseEntity<Map<String, String>> handleMissingPart() {
        return ResponseEntity.badRequest().body(Map.of(
                "code", "MISSING_FILE",
                "message", "Multipart field 'file' is required"));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<Map<String, String>> handleTooLarge() {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(Map.of(
                "code", "FILE_TOO_LARGE",
                "message", "The PDF must not exceed 20 MB"));
    }

    @ExceptionHandler(SearchValidationException.class)
    ResponseEntity<Map<String, String>> handleSearchValidation(SearchValidationException exception) {
        return ResponseEntity.badRequest().body(Map.of(
                "code", exception.getCode(),
                "message", exception.getMessage()));
    }
}
