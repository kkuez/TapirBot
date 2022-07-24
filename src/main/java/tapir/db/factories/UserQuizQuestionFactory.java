package tapir.db.factories;

import tapir.db.entities.UserQuizQuestionEntity;

public class UserQuizQuestionFactory extends PojoFactory{


    public static UserQuizQuestionEntity createEntity(long userId, int questionId, String answer) {
        final UserQuizQuestionEntity userQuizQuestionEntity = new UserQuizQuestionEntity();
        userQuizQuestionEntity.setQuestion(questionId);
        userQuizQuestionEntity.setUser(userId);
        userQuizQuestionEntity.setAnswer(answer);
        return userQuizQuestionEntity;
    }
}
