package tapir.quiz;

import tapir.entities.QuestionAttachmentEntity;
import tapir.entities.QuizQuestionEntity;

import java.util.Optional;

public class AttachmentEntityFactory {
    static QuestionAttachmentEntity create(AttachmentCategory category, String fileName) {
        return create(Optional.empty(), category, fileName);
    }

    static QuestionAttachmentEntity create(Optional<Integer> questionId, AttachmentCategory category, String fileName) {
        final QuestionAttachmentEntity questionAttachmentEntity = new QuestionAttachmentEntity();
        if(questionId.isPresent()) {
            questionAttachmentEntity.setQuestion(questionId.get());
        }
        questionAttachmentEntity.setCategory(category.toString());
        questionAttachmentEntity.setFilename(fileName);
        return questionAttachmentEntity;
    }
}
