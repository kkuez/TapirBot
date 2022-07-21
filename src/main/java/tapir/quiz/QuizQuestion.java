package tapir.quiz;

import entities.QuestionAttachmentEntity;

import java.util.ArrayList;
import java.util.List;

public class QuizQuestion {

    private final String creatorName;
    private String explaination;
    private List<QuestionAttachmentEntity> attachments = new ArrayList<>();
    private int id;
    private String text;
    private List<QuizAnswer> answers;

    public QuizQuestion(int id, String text, List<QuizAnswer> answers, String creatorName, String explaination,
                        List<QuestionAttachmentEntity> attachments) {
        this.id = id;
        this.text = text;
        this.answers = answers;
        this.creatorName = creatorName;
        this.explaination = explaination;
        this.attachments = attachments;
    }

    public String getCreatorName() {
        return creatorName;
    }

    public int getId() {
        return id;
    }

    public String getText() {
        return text;
    }

    public List<QuizAnswer> getAnswers() {
        return answers;
    }

    public String getExplaination() {
        return explaination;
    }

    public List<QuestionAttachmentEntity> getAttachments() {
        return attachments;
    }


}
