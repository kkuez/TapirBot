package tapir.db.entities;

import javax.annotation.Nullable;
import javax.persistence.*;
import java.io.Serializable;

@Entity
@Table(name = "Question_Attachment")
public class QuestionAttachmentEntity implements Serializable {

    @Id
    @Column(name="question")
    @Nullable
    private int question;

    @Id
    @Column(name="filename")
    @Nullable
    private String filename;

    @Column(name="category")
    private String category;

    @Nullable
    public int getQuestion() {
        return question;
    }

    public void setQuestion(@Nullable int question) {
        this.question = question;
    }

    @Nullable
    public String getFilename() {
        return filename;
    }

    public void setFilename(@Nullable String filename) {
        this.filename = filename;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

}
