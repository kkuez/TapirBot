package tapir.db.entities;

import javax.annotation.Nullable;
import javax.persistence.*;
import java.io.Serializable;

@Entity
@Table(name = "User_QuizQuestions")
public class UserQuizQuestionEntity implements Serializable {
    @Id
    @Column(name = "question")
    @Nullable
    Integer question;
    @Id
    @Column(name = "user")
    @Nullable
    long user;
    String answer;

    public int getQuestion() {
        return question;
    }

    public void setQuestion(int question) {
        this.question = question;
    }

    public long getUser() {
        return user;
    }

    public void setUser(long user) {
        this.user = user;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

}
