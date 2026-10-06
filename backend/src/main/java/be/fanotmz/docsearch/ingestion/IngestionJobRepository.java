package be.fanotmz.docsearch.ingestion;

import java.time.Instant;
import java.sql.Timestamp;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class IngestionJobRepository {
    private final JdbcTemplate jdbc;

    public IngestionJobRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void create(UUID id, UUID documentId, Instant startedAt) {
        jdbc.update("""
                INSERT INTO ingestion_jobs (id, document_id, status, started_at)
                VALUES (?, ?, ?, ?)
                """, id, documentId, IngestionJobStatus.PROCESSING.name(), Timestamp.from(startedAt));
    }

    public void markSucceeded(UUID id, Instant completedAt) {
        jdbc.update("""
                UPDATE ingestion_jobs
                SET status = ?, completed_at = ?
                WHERE id = ?
                """, IngestionJobStatus.SUCCEEDED.name(), Timestamp.from(completedAt), id);
    }

    public void markFailed(UUID id, String errorCode, String message, Instant completedAt) {
        jdbc.update("""
                UPDATE ingestion_jobs
                SET status = ?, error_code = ?, error_message = ?, completed_at = ?
                WHERE id = ?
                """, IngestionJobStatus.FAILED.name(), errorCode, message, Timestamp.from(completedAt), id);
    }
}
