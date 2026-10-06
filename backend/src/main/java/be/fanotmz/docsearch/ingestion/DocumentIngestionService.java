package be.fanotmz.docsearch.ingestion;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import be.fanotmz.docsearch.documents.DocumentStatus;
import be.fanotmz.docsearch.documents.DocumentUploadException;
import be.fanotmz.docsearch.documents.DocumentUploadResponse;
import be.fanotmz.docsearch.documents.persistence.DocumentPageRepository;
import be.fanotmz.docsearch.documents.persistence.DocumentRepository;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentIngestionService {
    private static final byte[] PDF_SIGNATURE = "%PDF-".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

    private final DocumentRepository documentRepository;
    private final DocumentPageRepository pageRepository;
    private final IngestionJobRepository jobRepository;

    public DocumentIngestionService(
            DocumentRepository documentRepository,
            DocumentPageRepository pageRepository,
            IngestionJobRepository jobRepository) {
        this.documentRepository = documentRepository;
        this.pageRepository = pageRepository;
        this.jobRepository = jobRepository;
    }

    public DocumentUploadResponse ingest(MultipartFile file) {
        validateUpload(file);

        String filename = normalizedFilename(file.getOriginalFilename());
        String contentType = StringUtils.hasText(file.getContentType())
                ? file.getContentType()
                : "application/pdf";
        UUID documentId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        Instant startedAt = Instant.now();
        documentRepository.create(documentId, filename, contentType, startedAt);
        jobRepository.create(jobId, documentId, startedAt);

        Path temporaryPdf = null;
        try {
            temporaryPdf = Files.createTempFile("docsearch-upload-", ".pdf");
            file.transferTo(temporaryPdf);
            List<Document> pages = new PagePdfDocumentReader(
                    new FileSystemResource(temporaryPdf), PdfDocumentReaderConfig.defaultConfig()).get();
            if (pages.isEmpty() || pages.stream().noneMatch(page -> StringUtils.hasText(page.getText()))) {
                throw new DocumentUploadException(
                        "NO_EXTRACTABLE_TEXT", "The PDF contains no extractable text; OCR is not available");
            }

            for (int index = 0; index < pages.size(); index++) {
                int pageNumber = index + 1;
                Document normalizedPage = pages.get(index).mutate()
                        .metadata("docsearch.document_id", documentId.toString())
                        .metadata("docsearch.source", filename)
                        .metadata("docsearch.page_number", pageNumber)
                        .build();
                pageRepository.insert(
                        documentId,
                        pageNumber,
                        filename,
                        normalizedPage.getText() == null ? "" : normalizedPage.getText());
            }
            Instant completedAt = Instant.now();
            documentRepository.markReady(documentId, pages.size(), completedAt);
            jobRepository.markSucceeded(jobId, completedAt);
            return new DocumentUploadResponse(documentId, filename, pages.size(), DocumentStatus.READY);
        }
        catch (DocumentUploadException exception) {
            fail(documentId, jobId, exception);
            throw exception;
        }
        catch (Exception exception) {
            DocumentUploadException failure = new DocumentUploadException(
                    "MALFORMED_PDF", "The PDF could not be read as a text PDF", exception);
            fail(documentId, jobId, failure);
            throw failure;
        }
        finally {
            if (temporaryPdf != null) {
                try {
                    Files.deleteIfExists(temporaryPdf);
                }
                catch (IOException ignored) {
                    // The temporary file is not part of the durable document state.
                }
            }
        }
    }

    private void validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new DocumentUploadException("EMPTY_FILE", "Multipart field 'file' must contain a PDF");
        }
        String contentType = file.getContentType();
        if (StringUtils.hasText(contentType)
                && !"application/pdf".equalsIgnoreCase(contentType)
                && !"application/octet-stream".equalsIgnoreCase(contentType)) {
            throw new DocumentUploadException("UNSUPPORTED_MEDIA_TYPE", "Only PDF uploads are supported");
        }
        try (var input = file.getInputStream()) {
            byte[] signature = input.readNBytes(PDF_SIGNATURE.length);
            if (!Arrays.equals(PDF_SIGNATURE, signature)) {
                throw new DocumentUploadException("NOT_PDF", "The uploaded content is not a PDF");
            }
        }
        catch (IOException exception) {
            throw new DocumentUploadException("NOT_PDF", "The uploaded content could not be read", exception);
        }
    }

    private String normalizedFilename(String originalFilename) {
        if (!StringUtils.hasText(originalFilename)) {
            return "upload.pdf";
        }
        String filename = StringUtils.cleanPath(originalFilename).replace('\\', '/');
        int slash = filename.lastIndexOf('/');
        filename = slash >= 0 ? filename.substring(slash + 1) : filename;
        return StringUtils.hasText(filename) ? filename : "upload.pdf";
    }

    private void fail(UUID documentId, UUID jobId, DocumentUploadException exception) {
        Instant completedAt = Instant.now();
        documentRepository.markFailed(documentId, completedAt);
        jobRepository.markFailed(jobId, exception.getCode(), exception.getMessage(), completedAt);
    }
}
