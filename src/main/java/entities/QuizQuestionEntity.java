package entities;

import javax.annotation.Nullable;
import javax.persistence.*;
import java.io.Serializable;
import java.util.List;

@Entity
@Table(name = "QuizQuestions")
public class QuizQuestionEntity implements Serializable {
    @Id
    @Column(name="id")
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Nullable
    private Integer id;
    private String text;
    private String Right_Answer;
    private String Wrong_Answer_1;
    private String Wrong_Answer_2;
    private String Wrong_Answer_3;
    private String Explaination;
    private Long user;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getRight_Answer() {
        return Right_Answer;
    }

    public void setRight_Answer(String right_Answer) {
        Right_Answer = right_Answer;
    }

    public String getWrong_Answer_1() {
        return Wrong_Answer_1;
    }

    public void setWrong_Answer_1(String wrong_Answer_1) {
        Wrong_Answer_1 = wrong_Answer_1;
    }

    public String getWrong_Answer_2() {
        return Wrong_Answer_2;
    }

    public void setWrong_Answer_2(String wrong_Answer_2) {
        Wrong_Answer_2 = wrong_Answer_2;
    }

    public String getWrong_Answer_3() {
        return Wrong_Answer_3;
    }

    public void setWrong_Answer_3(String wrong_Answer_3) {
        Wrong_Answer_3 = wrong_Answer_3;
    }

    public Long getUser() {
        return user;
    }

    public void setUser(Long user) {
        this.user = user;
    }

    public String getExplaination() {
        return Explaination;
    }

    public void setExplaination(String explaination) {
        Explaination = explaination;
    }
}
