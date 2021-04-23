import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;


public class Quiz implements ReceiveModule {

    private final DBService dbService;
    private QuizStatus status;
    private QuizQuestion question;
    public static String RIGHT_ANSWER = "Right_Answer";
    public static String WRONG_ANSWER_1 = "Wrong_Answer_1";
    public static String WRONG_ANSWER_2 = "Wrong_Answer_2";
    public static String WRONG_ANSWER_3 = "Wrong_Answer_3";
    public static String NO_CLUE = "Keine Ahnung!";

    public Quiz(DBService dbService) {
        this.dbService = dbService;
        this.status = QuizStatus.NONE;
    }

    @Override
    public Set<String> getCommands() {
        return Set.of(
                "q",
                "quiz",
                "quiz help",
                "q help",
                "q info",
                "quiz info",
                "q score",
                "quiz score"
        );
    }

    @Override
    public void handle(User user, String message, JDA bot, TextChannel channel) {
        final String[] messages = message.split(" ");
        if (messages.length > 1 && status == QuizStatus.NONE) {
            switch (messages[1]) {
                case "help":
                    //TODO help(user);
                    break;
                case "info":
                    info(channel);
                    break;
                case "score":
                    //TODO score(user);
                    break;
                default:
                    break;
            }
        } else {
            if (status.equals(QuizStatus.WAITING)) {
                checkAnswer(user, message, bot, channel);
            } else {
                //Question + Wait status
                question(user, channel);
            }
        }
        System.out.println();
    }

    private void info(TextChannel channel) {
        StringBuilder builder = new StringBuilder("Rangliste nach Punkten:").append("\n\n");
        final List<RankingTableEntry> userScoresPointRated = dbService.getUserScoresPointRated();
        int i = 1;

        for(RankingTableEntry entry: userScoresPointRated) {
            builder.append(i).append(":\t").append(entry.getUserName()).append("\n");
            i++;
        }

        channel.sendMessage(builder.toString()).queue();
    }


    private void checkAnswer(User user, String message, JDA bot, TextChannel channel) {
        final Optional<QuizAnswer> rightAnswerOpt =
                question.getAnswers().stream().filter(quizAnswer -> quizAnswer.isCorrect()).findFirst();
        String answerOfUser = NO_CLUE;

        if (message.equals(rightAnswerOpt.get().getText())) {
            //send right
            channel.sendMessage("Yessa " + user.getName() + "! Das war richtig, +3 Punkte für dich!").queue();
            answerOfUser = RIGHT_ANSWER;
        } else {
            if (message.equals(NO_CLUE)) {
                // send mid
                channel.sendMessage("Hm ok... Nix gewonnen, nix verloren.").queue();
            } else {
                //send wrong
                channel.sendMessage("Autsch " + user.getName() + " :( Leider falsch, -2 Punkte!").queue();
                answerOfUser = question.getAnswers().get(Integer.parseInt(message) - 1).getColumn();
            }
        }

        dbService.sendAnswer(user.getIdLong(), question.getId(), NO_CLUE);
        status = QuizStatus.NONE;
    }

    private void question(User user, TextChannel channel) {
        final List<QuizQuestion> questionsForUser = dbService.getQuestionsForUser(user);
        if (!questionsForUser.isEmpty()) {
            question = questionsForUser.get(0);
            List<QuizAnswer> answers = question.getAnswers();
            Collections.shuffle(answers);

            StringBuilder questionBuilder = new StringBuilder("Frage: ");
            questionBuilder.append(question.getText()).append("\n");
            questionBuilder.append("Antwort 1: ").append(answers.get(0).getText()).append("\n");
            questionBuilder.append("Antwort 2: ").append(answers.get(1).getText()).append("\n");
            questionBuilder.append("Antwort 3: ").append(answers.get(2).getText()).append("\n");
            questionBuilder.append("Antwort 4: ").append(answers.get(3).getText()).append("\n");
            questionBuilder.append("Antwort 5: ").append(NO_CLUE).append("\n");
            questionBuilder.append("Bitte gib eine Nummer ein und wähle weise...");

            channel.sendMessage(questionBuilder.toString()).queue();
            this.status = QuizStatus.WAITING;
        } else {
            //TODO Meldung dass keine Fragen mehr vorhanden sind
        }
    }

    static class RankingTableEntry {
        private Long userId;
        private String userName;
        private int points;

        public RankingTableEntry(Long userId, String userName, int points) {

            this.userId = userId;
            this.userName = userName;
            this.points = points;
        }

        public String getUserName() {
            return userName;
        }

        public Long getUserId() {
            return userId;
        }

        public int getPoints() {
            return points;
        }
    }
}
