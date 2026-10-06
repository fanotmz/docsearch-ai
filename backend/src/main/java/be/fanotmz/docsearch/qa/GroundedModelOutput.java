package be.fanotmz.docsearch.qa;

public class GroundedModelOutput {
    private String status;
    private String answer;

    public GroundedModelOutput() {
    }

    public GroundedModelOutput(String status, String answer) {
        this.status = status;
        this.answer = answer;
    }

    public String status() {
        return status;
    }

    public String getStatus() {
        return status;
    }

    public String answer() {
        return answer;
    }

    public String getAnswer() {
        return answer;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }
}
