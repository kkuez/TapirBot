public class QuizAnswer {
    private String text;
    private String column;

    public QuizAnswer(String text,  String column) {
        this.text = text;
        this.column = column;
    }

    public String getText() {
        return text;
    }

    public boolean isCorrect() {
        return column.equals(Quiz.RIGHT_ANSWER);
    }

    public String getColumn() {
        return column;
    }
}
