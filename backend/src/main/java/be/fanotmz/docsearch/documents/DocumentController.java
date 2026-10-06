package be.fanotmz.docsearch.documents;

import be.fanotmz.docsearch.ingestion.DocumentIngestionService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import be.fanotmz.docsearch.documents.persistence.DocumentRepository;

@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {
    private final DocumentIngestionService ingestionService;
    private final DocumentRepository documentRepository;

    public DocumentController(DocumentIngestionService ingestionService, DocumentRepository documentRepository) {
        this.ingestionService = ingestionService;
        this.documentRepository = documentRepository;
    }

    @GetMapping
    public List<DocumentSummary> list() {
        return documentRepository.findAllSummaries();
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentUploadResponse> upload(@RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(ingestionService.ingest(file));
    }
}
