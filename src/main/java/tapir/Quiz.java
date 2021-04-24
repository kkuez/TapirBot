package tapir;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.PrivateChannel;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;


public class Quiz extends ReceiveModule {

    private final DBService dbService;
    private QuizStatus status;
    private QuizQuestion question;
    public static String RIGHT_ANSWER = "Right_Answer";
    public static String WRONG_ANSWER_1 = "Wrong_Answer_1";
    public static String WRONG_ANSWER_2 = "Wrong_Answer_2";
    public static String WRONG_ANSWER_3 = "Wrong_Answer_3";
    public static String NO_CLUE = "Keine Ahnung!";
    private List<QuizAnswer> answers;

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
                "quiz score",
                "q new",
                "quiz new"
        );
    }

    @Override
    public void handle(User user, String message, JDA bot, TextChannel channel) {
        final String[] messages = message.split(" ");
        if (messages.length > 1 && status == QuizStatus.NONE) {
            switch (messages[1]) {
                case "help":
                    help(channel);
                    break;
                case "info":
                    info(channel);
                    break;
                case "score":
                    //TODO score(user);
                    break;
                case "new":
                    newQuestion(channel, user);
                    break;
                default:
                    break;
            }
        } else {
            if (status.equals(QuizStatus.WAITING_ANSWER)) {
                if (isInteger(message)) {
                    checkAnswer(user, Integer.parseInt(message));
                }
            } else {
                //Question + Wait status
                if (status.equals(QuizStatus.NONE)) {
                    question(user, channel);
                }
            }
        }
        System.out.println();
    }

    private void newQuestion(TextChannel channel, User user) {
        channel.sendMessage(user.getName() + ", schreib mir bitte jetzt eine PM mit deiner Frage!").queue();
        status = QuizStatus.WAITING_QUESTION;
        answers = new ArrayList<>(4);
        question = null;
    }

    private void help(TextChannel channel) {
        String helpText = "Willkommen zum Quizmodul des TapirBots!" +
                "\nEs gibt folgende Befehle:" +
                "\n\t!q oder !quiz" +
                "\n\t\tGibt dir eine Frage die du noch nicht beantwortet hast" +
                "\n\t!q info oder !quiz info" +
                "\n\t\tGibt dir die aktuelle Tabelle" +
                "\n\n Viel Spass beim Rätseln :)";
        channel.sendMessage(helpText).queue();
    }

    private boolean isInteger(String message) {
        try {
            Integer.parseInt(message);
        } catch (NumberFormatException e) {
            System.out.println(message + "is not Integer");
            return false;
        }
        return true;
    }

    @Override
    public boolean waitingForAnswer() {
        return status.equals(QuizStatus.WAITING_ANSWER) || status.equals(QuizStatus.WAITING_QUESTION) ||
                status.equals(QuizStatus.WAITING_QUESTION_ANSWERS);
    }

    @Override
    public void handlePM(User user, String input, JDA bot, PrivateChannel channel) {
        if (status.equals(QuizStatus.WAITING_QUESTION)) {
            question = new QuizQuestion(99, input, null, null);
            channel.sendMessage("Wie lautet die richtige Antwort?").queue();
            status = QuizStatus.WAITING_QUESTION_ANSWERS;
        } else {
            if (status.equals(QuizStatus.WAITING_QUESTION_ANSWERS)) {
                for (int i = 0; i < 4; i++) {
                    if (answers.size() == i) {
                        answers.add(new QuizAnswer(input, i == 0 ? RIGHT_ANSWER :
                                i == 1 ? WRONG_ANSWER_1 :
                                        i == 2 ? WRONG_ANSWER_2 :
                                                i == 3 ? WRONG_ANSWER_3 : ""));
                        if (i == 0) {
                            channel.sendMessage("...und die erste falsche?").queue();
                            break;
                        } else {
                            if (i == 1) {
                                channel.sendMessage("...und die zweite falsche?").queue();
                                break;
                            } else {
                                if (i == 2) {
                                    channel.sendMessage("...und die dritte falsche?").queue();
                                    break;
                                } else {
                                    channel.sendMessage("Danke, das gibt einen Punkt für dich :)").queue();
                                    channel.sendMessage("Danke :)").queue();
                                    dbService.enterQuestions(user, question, answers);
                                    status = QuizStatus.NONE;
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void info(TextChannel channel) {
        StringBuilder builder = new StringBuilder("Rangliste nach Punkten:").append("\n");
        final List<RankingTableEntry> userScoresPointRated = dbService.getUserScoresPointRated();
        int i = 1;

        for (RankingTableEntry entry : userScoresPointRated) {
            String rankAndName = i + ": " + entry.getUserName();
            builder.append(rankAndName);

            int spaces = 30 - rankAndName.length();
            for (int j = 0; j < spaces; j++) {
                builder.append(" ");
            }

            builder.append(entry.getPoints()).append(" Punkte bei ").append("\t").append(entry.getAnswered())
                    .append(" Fragen").append("\n");
            i++;
        }

        channel.sendMessage(builder.toString()).queue();
    }


    private void checkAnswer(User user, int answerNr) {
        int rightAnswerIndex = getRightAnswerIndex();
        answerNr = answerNr - 1;

        String answerOfUser = NO_CLUE;
        final String sendToUser;
        if (answerNr == rightAnswerIndex) {
            //send right
            sendToUser = "Yessa " + user.getName() + "! Das war richtig, +3 Punkte für dich!";
            answerOfUser = RIGHT_ANSWER;
        } else {
            if (answerNr == 4) {
                // send mid
                sendToUser = "Hm ok... Nix gewonnen, nix verloren.";
            } else {
                //send wrong
                sendToUser = "Autsch " + user.getName() + " :( Leider falsch, -2 Punkte!\n Die richtige Antwort ist: " +
                answers.get(rightAnswerIndex).getText();
                answerOfUser = answers.get(answerNr).getColumn();
            }
        }

        user.openPrivateChannel().queue((channel) -> channel.sendMessage(sendToUser).queue());
        dbService.sendAnswer(user.getIdLong(), question.getId(), answerOfUser);
        status = QuizStatus.NONE;
    }

    private int getRightAnswerIndex() {
        for (int i = 0; i < 4; i++) {
            if (answers.get(i).isCorrect()) {
                return i;
            }
        }

        return 99;
    }

    private void question(User user, TextChannel channel) {
        List<QuizQuestion> questionsForUser = dbService.getFilteredQuestionsForUser(user);
        if (!questionsForUser.isEmpty()) {
            Collections.shuffle(questionsForUser);
            question = questionsForUser.get(0);
            List<QuizAnswer> answers = question.getAnswers();
            Collections.shuffle(answers);
            this.answers = answers;

            StringBuilder questionBuilder = new StringBuilder(user.getName()).append(", deine Frage von " +
                    question.getCreatorName() + ": ");
            questionBuilder.append(question.getText()).append("\n");
            questionBuilder.append("Antwort 1: ").append(answers.get(0).getText()).append("\n");
            questionBuilder.append("Antwort 2: ").append(answers.get(1).getText()).append("\n");
            questionBuilder.append("Antwort 3: ").append(answers.get(2).getText()).append("\n");
            questionBuilder.append("Antwort 4: ").append(answers.get(3).getText()).append("\n");
            questionBuilder.append("Antwort 5: ").append(NO_CLUE).append("\n");
            questionBuilder.append("Bitte gib eine Nummer ein und wähle weise...");

            channel.sendMessage(questionBuilder.toString()).queue();
            this.status = QuizStatus.WAITING_ANSWER;
        } else {
            channel.sendMessage("Sorry " + user.getName() + ", Du hast schon alle Fragen beantwortet. Warte " +
                    "bis es neue gibt ;)").queue();
        }
    }


    static class RankingTableEntry {
        private Long userId;
        private String userName;
        private int points;
        private int answered;

        public RankingTableEntry(Long userId, String userName, int points, int answered) {

            this.userId = userId;
            this.userName = userName;
            this.points = points;
            this.answered = answered;
        }

        public int getAnswered() {
            return answered;
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
