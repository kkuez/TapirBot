package tapir.quiz;

import java.util.ArrayList;
import java.util.List;

public class QuizQuestion {

    private final String creatorName;
    private String explaination;
    private List<String> attachmentsFileNames = new ArrayList<>();
    private int id;
    private String text;
    private List<QuizAnswer> answers;

    public QuizQuestion(int id, String text, List<QuizAnswer> answers, String creatorName, String explaination,
                        List<String> attachmentsFileNames) {
        this.id = id;
        this.text = text;
        this.answers = answers;
        this.creatorName = creatorName;
        this.explaination = explaination;
        this.attachmentsFileNames = attachmentsFileNames;
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

    public List<String> getAttachmentsFileNames() {
        return attachmentsFileNames;
    }
}
