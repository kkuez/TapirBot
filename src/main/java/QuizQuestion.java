import java.util.List;
import java.util.Map;
import java.util.Set;

public class QuizQuestion {

    private final String creatorName;
    private int id;
    private String text;
    private List<QuizAnswer> answers;

    public QuizQuestion(int id, String text, List<QuizAnswer> answers, String creatorName) {
        this.id = id;
        this.text = text;
        this.answers = answers;
        this.creatorName = creatorName;
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
}
