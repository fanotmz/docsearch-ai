package be.fanotmz.docsearch.qa;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/qa")
public class GroundedQaController {
    private final GroundedQaService qaService;

    public GroundedQaController(GroundedQaService qaService) {
        this.qaService = qaService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GroundedAnswerResponse> answer(
            @RequestBody(required = false) GroundedQuestionRequest request) {
        return ResponseEntity.ok(qaService.answer(request));
    }
}
