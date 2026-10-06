package be.fanotmz.docsearch.documents.persistence;

import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DocumentPageRepository {
    private final JdbcTemplate jdbc;

    public DocumentPageRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(UUID documentId, int pageNumber, String source, String content) {
        jdbc.update("""
                INSERT INTO document_pages (id, document_id, page_number, source, content)
                VALUES (?, ?, ?, ?, ?)
                """, UUID.randomUUID(), documentId, pageNumber, source, content);
    }
}
