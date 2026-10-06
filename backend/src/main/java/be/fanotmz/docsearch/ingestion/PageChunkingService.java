package be.fanotmz.docsearch.ingestion;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import be.fanotmz.docsearch.documents.persistence.DocumentPageRepository;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PageChunkingService {
    private final TokenTextSplitter splitter = TokenTextSplitter.builder()
            .withChunkSize(800)
            .withMinChunkSizeChars(200)
            .withMinChunkLengthToEmbed(10)
            .withMaxNumChunks(1000)
            .withKeepSeparator(true)
            .build();

    public List<Document> chunk(List<DocumentPageRepository.StoredPage> pages, UUID documentId) {
        List<Document> chunks = new ArrayList<>();
        for (DocumentPageRepository.StoredPage page : pages) {
            if (!StringUtils.hasText(page.content())) {
                continue;
            }
            Document pageDocument = Document.builder()
                    .text(page.content())
                    .metadata("docsearch.document_id", documentId.toString())
                    .metadata("docsearch.source", page.source())
                    .metadata("docsearch.page_number", page.pageNumber())
                    .build();
            List<Document> pageChunks = splitter.split(pageDocument);
            for (int index = 0; index < pageChunks.size(); index++) {
                Document chunk = pageChunks.get(index);
                if (!StringUtils.hasText(chunk.getText())) {
                    continue;
                }
                String chunkId = UUID.nameUUIDFromBytes((documentId + ":" + page.pageNumber() + ":" + index)
                        .getBytes(StandardCharsets.UTF_8)).toString();
                chunks.add(chunk.mutate()
                        .id(chunkId)
                        .metadata("docsearch.document_id", documentId.toString())
                        .metadata("docsearch.source", page.source())
                        .metadata("docsearch.page_number", page.pageNumber())
                        .metadata("docsearch.chunk_index", index)
                        .build());
            }
        }
        return chunks;
    }
}
