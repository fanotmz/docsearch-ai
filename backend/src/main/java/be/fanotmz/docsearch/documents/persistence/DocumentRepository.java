package be.fanotmz.docsearch.documents.persistence;

import java.time.Instant;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

import be.fanotmz.docsearch.documents.DocumentStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DocumentRepository {
    private final JdbcTemplate jdbc;

    public DocumentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void create(UUID id, String filename, String contentType, Instant now) {
        jdbc.update("""
                INSERT INTO documents (id, original_filename, content_type, page_count, status, created_at, updated_at)
                VALUES (?, ?, ?, 0, ?, ?, ?)
                """, id, filename, contentType, DocumentStatus.PROCESSING.name(), Timestamp.from(now), Timestamp.from(now));
    }

    public void markReady(UUID id, int pageCount, Instant now) {
        jdbc.update("""
                UPDATE documents SET page_count = ?, status = ?, updated_at = ? WHERE id = ?
                """, pageCount, DocumentStatus.READY.name(), Timestamp.from(now), id);
    }

    public void markFailed(UUID id, Instant now) {
        jdbc.update("""
                UPDATE documents SET status = ?, updated_at = ? WHERE id = ?
                """, DocumentStatus.FAILED.name(), Timestamp.from(now), id);
    }

    public List<UUID> findReadyDocumentIds() {
        return jdbc.query("""
                SELECT id
                FROM documents
                WHERE status = ?
                ORDER BY id
                """, (resultSet, rowNumber) -> resultSet.getObject("id", UUID.class),
                DocumentStatus.READY.name());
    }
}
