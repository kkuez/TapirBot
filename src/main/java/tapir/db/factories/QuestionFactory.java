package tapir.db.factories;

import tapir.db.DBService;
import tapir.db.entities.QuizQuestionEntity;
import tapir.quiz.QuizAnswer;
import tapir.quiz.QuizQuestion;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class QuestionFactory extends PojoFactory {
    public static QuizQuestion createPojo(DBService dbService, QuizQuestionEntity entity) {
        List<QuizAnswer> answers = new ArrayList<>(4);
        answers.add(new QuizAnswer(entity.getRight_Answer(), "Right_Answer"));
        answers.add(new QuizAnswer(entity.getWrong_Answer_1(), "Wrong_Answer_1"));
        answers.add(new QuizAnswer(entity.getWrong_Answer_2(), "Wrong_Answer_2"));
        answers.add(new QuizAnswer(entity.getWrong_Answer_3(), "Wrong_Answer_3"));

        final Map<String, String> userInfoById = dbService.getUserInfoById(entity.getUser());

        final QuizQuestion question = new QuizQuestion(entity.getId(),
                entity.getText(),
                answers,
                userInfoById.get("name"),
                entity.getExplaination(),
                dbService.getQuestionAttachments(entity.getId()));
        return question;
    }
}
