package tapir.quiz;

import java.util.Optional;

public class QuestionAttachment {
    private Optional<Integer> questionId;
    private String fileName;
    private AttachmentCategory category;

    public QuestionAttachment(Optional<Integer> questionId, String fileName, AttachmentCategory category) {
        this.questionId = questionId;
        this.fileName = fileName;
        this.category = category;
    }

    public Optional<Integer> getQuestionId() {
        return questionId;
    }

    public String getFileName() {
        return fileName;
    }

    public AttachmentCategory getCategory() {
        return category;
    }

    public void setQuestionId(Optional<Integer> questionId) {
        this.questionId = questionId;
    }

    public void setCategory(AttachmentCategory category) {
        this.category = category;
    }
}
