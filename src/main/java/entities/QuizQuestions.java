package entities;

import javax.annotation.Nullable;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.io.Serializable;

@Entity
@Table(name = "QuizQuestions")
public class QuizQuestions implements Serializable {
    @Id
    @Column(name="id")
    @Nullable
    private Integer id;
    private String text;
    private String Right_Answer;
    private String Wrong_Answer1;
    private String Wrong_Answer2;
    private String Wrong_Answer3;
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

    public String getWrong_Answer1() {
        return Wrong_Answer1;
    }

    public void setWrong_Answer1(String wrong_Answer1) {
        Wrong_Answer1 = wrong_Answer1;
    }

    public String getWrong_Answer2() {
        return Wrong_Answer2;
    }

    public void setWrong_Answer2(String wrong_Answer2) {
        Wrong_Answer2 = wrong_Answer2;
    }

    public String getWrong_Answer3() {
        return Wrong_Answer3;
    }

    public void setWrong_Answer3(String wrong_Answer3) {
        Wrong_Answer3 = wrong_Answer3;
    }

    public Long getUser() {
        return user;
    }

    public void setUser(Long user) {
        this.user = user;
    }
}
