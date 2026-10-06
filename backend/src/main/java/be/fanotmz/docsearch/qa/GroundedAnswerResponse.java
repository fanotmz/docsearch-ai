package be.fanotmz.docsearch.qa;

import java.util.List;

public record GroundedAnswerResponse(
        String question,
        QaStatus status,
        String answer,
        List<GroundedCitation> citations) {
}
