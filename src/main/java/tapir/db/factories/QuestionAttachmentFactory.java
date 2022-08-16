package tapir.db.factories;

import tapir.db.entities.QuestionAttachmentEntity;
import tapir.quiz.AttachmentCategory;
import tapir.quiz.QuestionAttachment;

import java.util.Optional;

/**
 * Pojos <-> Entities
 * */
public class QuestionAttachmentFactory extends PojoFactory {

    public static QuestionAttachmentEntity createEntity(QuestionAttachment questionAttachment) {
        return createEntity(questionAttachment.getQuestionId(), questionAttachment.getCategory(), questionAttachment.getFileName());
    }

    public static QuestionAttachmentEntity createEntity(AttachmentCategory category, String fileName) {
        return createEntity(Optional.empty(), category, fileName);
    }

    public static QuestionAttachmentEntity createEntity(Optional<Integer> questionId, AttachmentCategory category, String fileName) {
        final QuestionAttachmentEntity questionAttachmentEntity = new QuestionAttachmentEntity();
        if(questionId.isPresent()) {
            questionAttachmentEntity.setQuestion(questionId.get());
        }
        questionAttachmentEntity.setCategory(category.toString());
        questionAttachmentEntity.setFilename(fileName);
        return questionAttachmentEntity;
    }

    public static QuestionAttachment createPojo(QuestionAttachmentEntity entity) {
        final AttachmentCategory attachmentCategory = AttachmentCategory.valueOf(entity.getCategory());
        return new QuestionAttachment(Optional.of(entity.getQuestion()), entity.getFilename(), attachmentCategory);
    }

    public static QuestionAttachment createPojo(Optional<Integer> questionIdOpt, AttachmentCategory category, String fileName) {
        return new QuestionAttachment(questionIdOpt, fileName, category);
    }
}
