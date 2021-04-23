public class QuizAnswer {
    private String text;
    private boolean isCorrect;
    private String column;

    public QuizAnswer(String text, boolean isCorrect, String column) {
        this.text = text;
        this.isCorrect = isCorrect;
        this.column = column;
    }

    public String getText() {
        return text;
    }

    public boolean isCorrect() {
        return isCorrect;
    }

    public String getColumn() {
        return column;
    }
}
