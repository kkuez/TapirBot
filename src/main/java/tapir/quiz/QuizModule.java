package tapir.quiz;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.PrivateChannel;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.Event;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.interactions.components.Button;
import net.dv8tion.jda.api.interactions.components.Component;
import tapir.DBService;
import tapir.ReceiveModule;

import java.util.*;
import java.util.stream.Collectors;


public class QuizModule extends ReceiveModule {

    private QuizStatus status;
    private QuizQuestion question;
    private List<QuizAnswer> answers;
    public static final String RIGHT_ANSWER = "Right_Answer";
    public static final String WRONG_ANSWER_1 = "Wrong_Answer_1";
    public static final String WRONG_ANSWER_2 = "Wrong_Answer_2";
    public static final String WRONG_ANSWER_3 = "Wrong_Answer_3";
    public static final String NO_CLUE = "Keine Ahnung!";
    public static final String QUIZ = "Quiz";
    public static final String MESSAGE_SEPERATOR = " ";

    public QuizModule(DBService dbService, Set<TextChannel> generalChannels, Set<Long> userNotAllowedToAsk) {
        super(dbService, generalChannels, userNotAllowedToAsk);
        status = QuizStatus.NONE;
    }

    @Override
    public Set<String> getCommands() {
        return Set.of(
                "q",
                "quiz",
                "quiz help",
                "q help",
                "q info",
                "q info global",
                "quiz info",
                "quiz info global",
                "q new",
                "quiz new"
        );
    }

    @Override
    public void handle(User user, String[] messages, TextChannel channel, Optional<Event> event) {
        if (messages.length > 1 && status == QuizStatus.NONE) {
            switch (messages[1].toLowerCase()) {
                case "help":
                    help(channel);
                    break;
                case "info":
                    boolean global = messages.length == 3 && messages[2].equals("global");
                    info(channel, global);
                    break;
                case "new":
                    channel.sendMessage(user.getName() + ", Fragen werden jetzt per !q new in dem " +
                            "Privaten Channel gestellt hrhr...").queue();
                default:
                    break;
            }
        } else {
            if (status.equals(QuizStatus.WAITING_ANSWER) && messages.length >= 1) {
                final String numberString = messages[messages.length - 1];
                if (isInteger(numberString)) {
                    final int answerNr = Integer.parseInt(numberString);
                    checkAnswer(user, answerNr);
                    final ButtonClickEvent buttonClickEvent = (ButtonClickEvent) event.get();
                    final String questionAfterTrim = buttonClickEvent.getMessage().getContentRaw()
                            .substring(0, buttonClickEvent.getMessage().getContentRaw().indexOf("\n*Antwort 1"));
                    final String answerText = answerNr == 4 ? "Keine Ahnung!" :answers.get(answerNr).getText();
                    final String editText = questionAfterTrim + "*" + user.getName() + "* hat geantwortet mit \"*"
                            + answerText + "*\"!";
                    final Message message = new MessageBuilder().append(editText).build();
                    buttonClickEvent.editMessage(message).queue();
                } else {
                    channel.sendMessage("Sorry " + user.getName() + ", scheinbar bist du noch im " +
                            "Fragemodus? (!abbruch in einer PM zum abbrechen)").queue();
                }
            } else {
                //Question + Wait status
                if (status.equals(QuizStatus.NONE) && event.isPresent()) {
                    final GuildMessageReceivedEvent guildMessageReceivedEvent = (GuildMessageReceivedEvent) event.get();
                    question(user, guildMessageReceivedEvent);
                }
            }
        }
        System.out.println();
    }


    private void newQuestion(User user) {
        if(getUserNotAllowedToAsk().contains(user.getIdLong())) {
            return;
        }

        String sendToUser = user.getName() + ", schreib mir bitte jetzt eine PM mit deiner Frage! (Abbrechen mit" +
                " !abbruch)";
        user.openPrivateChannel().queue((channel) -> channel.sendMessage(sendToUser).queue());
        status = QuizStatus.WAITING_QUESTION;
        answers = new ArrayList<>(4);
        question = null;
    }

    private void help(TextChannel channel) {
        String helpText = "Willkommen zum Quizmodul des TapirBots!" +
                "\nEs gibt folgende Befehle:" +
                "\n\t__Allgemeiner Channel:__" +
                "\n\t\t**!q** oder **!quiz**: Gibt dir eine Frage die du noch nicht beantwortet hast" +
                "\n\t\t**!q info** oder **!quiz info**: Gibt dir die aktuelle Tabelle für den Channel" +
                "\n\t\t**!q info global** oder **!quiz info global**: Gibt dir die aktuelle Tabelle aller Channels" +
                "\n\t__Privater Channel:__" +
                "\n\t\t**!q new** oder **!quiz new**: Gib eine neue Frage ein und kassier einen Punkt!" +
                "\n\t\t...außerdem kommt hier das Ergebnis deiner Antwort!" +
                "\n\t__Punktevergabe:__" +
                "\n\t\t**3 Punkte**: \tRichtige Antwort" +
                "\n\t\t**1 Punkt**: \tErstellen einer Frage" +
                "\n\t\t**0 Punkte**: \t!5 (Keine Ahnung-Antwort)" +
                "\n\t\t**-2 Punkte**: \tFalsche Antwort" +
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
    public void handlePM(User user, String message, JDA bot, PrivateChannel channel) {
        final String[] messages = message.split(MESSAGE_SEPERATOR);
        if (message.toLowerCase().equals("abbruch")) {
            cancel(channel);
            return;
        } else {
            if ((message.startsWith("q ") || message.startsWith("quiz ")) && messages.length > 1
                    && status == QuizStatus.NONE) {
                switch (messages[1]) {
                    case "new":
                        newQuestion(user);
                        return;
                }
            }
        }

        switch (status) {
            case WAITING_QUESTION:
                enterNewQuestionViaPM(message, channel);
                break;
            case WAITING_QUESTION_ANSWERS:
                enterAnswersViaPM(user, message, channel);
                break;
            default:
        }
    }

    private void cancel(PrivateChannel channel) {
        question = null;
        answers = null;
        status = QuizStatus.NONE;
        channel.sendMessage("Aktion abgebrochen!").queue();
    }

    private void enterAnswersViaPM(User user, String input, PrivateChannel channel) {
        for (int i = 0; i < 4; i++) {
            if (answers.size() == i) {
                answers.add(new QuizAnswer(input, i == 0 ? RIGHT_ANSWER :
                        i == 1 ? WRONG_ANSWER_1 :
                                i == 2 ? WRONG_ANSWER_2 :
                                        i == 3 ? WRONG_ANSWER_3 : ""));
                if (i == 0) {
                    channel.sendMessage("...und die erste falsche? (Abbrechen mit !abbruch hier via PM)").queue();
                    break;
                } else {
                    if (i == 1) {
                        channel.sendMessage("...und die zweite falsche? (Abbrechen mit !abbruch hier via PM)").queue();
                        break;
                    } else {
                        if (i == 2) {
                            channel.sendMessage("...und die dritte falsche? (Abbrechen mit !abbruch hier via PM)").queue();
                            break;
                        } else {
                            channel.sendMessage("Danke, das gibt einen Punkt für dich :)").queue();
                            getGeneralChannels().forEach(channel1 ->
                                    channel1.sendMessage(user.getName() + " hat eine neue Frage erstellt!").queue());
                            getDbService().enterQuestions(user, question, answers);
                            status = QuizStatus.NONE;
                            break;
                        }
                    }
                }
            }
        }
    }

    private void enterNewQuestionViaPM(String input, PrivateChannel channel) {
        question = new QuizQuestion(99, input, null, null);
        channel.sendMessage("Wie lautet die richtige Antwort? (Abbrechen mit !abbruch hier via PM)").queue();
        status = QuizStatus.WAITING_QUESTION_ANSWERS;
    }

    private void info(TextChannel channel, boolean global) {
        List<RankingTableEntry> userScores = getDbService().getUserScoresPointRated();

        if(!global) {
            filterMembers(channel, userScores);
        }

        int i = 1;
        int amountOfQuestions = 0;
        String toReplace = "%%";
        StringBuilder builder = new StringBuilder("__Rangliste nach Punkten:__\n*(Es gibt %% Fragen)*").append("\n");
        //Point rated
        for (RankingTableEntry entry : userScores) {
            String rank = getRank(i);

            String rankAndName = rank + ": " + entry.getUserName();
            builder.append(rankAndName);

            final int createdByUser = entry.getCreated();
            builder.append("\t\t**").append(entry.getPoints() + createdByUser).append("** Punkte (")
                    .append(createdByUser).append(" Fragen erstellt)").append("\n");
            amountOfQuestions += createdByUser;
            i++;
        }
        builder.append("\n");
        //Rate rated, whole new algoryth, less performant but better overview. Some methods will be called twice
        builder.append("__Rangliste nach Rate:__\n*(Punkte ohne erstellte Fragen / Anzahl Beantwortete Fragen, " +
                "ab 10 beantwortete Fragen)*\n");
        i = 1;
        userScores.sort(Comparator.comparing(rankingTableEntry -> rankingTableEntry.getRate()));
        Collections.reverse(userScores);
        for (RankingTableEntry entry : userScores) {
            if(entry.getAnswered() < 10) {
                continue;
            }
            String rank = getRank(i);
            String rankAndName = rank + ": " + entry.getUserName();
            builder.append(rankAndName);

            builder.append("\t\t**").append(entry.getRate()).append("** Rate (")
                    .append(entry.getAnswered()).append(" Fragen beantwortet)").append("\n");
            i++;
        }

        final String tableText = builder.toString().replace(toReplace, amountOfQuestions + "");
        channel.sendMessage(tableText).queue();
    }

    private String getRank(int i) {
        switch (i) {
            case 1:
                return ":first_place:";
            case 2:
                return ":second_place:";
            case 3:
                return  ":third_place:";
            default:
                return i + "";
        }
    }

    private void filterMembers(TextChannel channel, List<RankingTableEntry> userScores) {
        final Set<Long> memberInChanIds = channel.getMembers().stream()
                .map(member -> member.getIdLong()).collect(Collectors.toSet());

        for(int i =0;i<userScores.size();i++) {
            if(!memberInChanIds.contains(userScores.get(i).getUserId())) {
                userScores.remove(i);
                //lower i cuz list shrinks of 1 element
                i--;
            }
        }
    }


    private void checkAnswer(User user, int answerNr) {
        int rightAnswerIndex = getRightAnswerIndex();
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
        getDbService().sendAnswer(user.getIdLong(), question.getId(), answerOfUser);
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

    private void question(User user, GuildMessageReceivedEvent event) {
        List<QuizQuestion> questionsForUser = getDbService().getFilteredQuestionsForUser(user);
        if (!questionsForUser.isEmpty()) {
            Collections.shuffle(questionsForUser);
            question = questionsForUser.get(0);
            List<QuizAnswer> answers = question.getAnswers();
            Collections.shuffle(answers);
            this.answers = answers;

            StringBuilder questionBuilder = new StringBuilder();
            questionBuilder.append(user.getName()).append(", deine Frage von **").append(question.getCreatorName())
                    .append("**:\n ");
            questionBuilder.append("**").append(question.getText()).append("**\n\n");
            questionBuilder.append("*Antwort 1:*\t** ").append(answers.get(0).getText()).append("**").append("\n");
            questionBuilder.append("*Antwort 2:*\t** ").append(answers.get(1).getText()).append("**").append("\n");
            questionBuilder.append("*Antwort 3:*\t** ").append(answers.get(2).getText()).append("**").append("\n");
            questionBuilder.append("*Antwort 4:*\t** ").append(answers.get(3).getText()).append("**").append("\n");
            questionBuilder.append("*Antwort 5:*\t** ").append(NO_CLUE).append("**").append("\n");

            final String buttonIdBeginn = user.getId() + MESSAGE_SEPERATOR + QUIZ + MESSAGE_SEPERATOR + "answer"
                    + MESSAGE_SEPERATOR;
            event.getMessage().reply(questionBuilder.toString())
                    .setActionRow(
                            Button.primary(buttonIdBeginn + 0, "Antwort 1"),
                            Button.primary(buttonIdBeginn + 1, "Antwort 2"),
                            Button.primary(buttonIdBeginn + 2, "Antwort 3"),
                            Button.primary(buttonIdBeginn + 3, "Antwort 4"),
                            Button.primary(buttonIdBeginn + 4, "Keine Ahnung!"))
                    .queue();

            this.status = QuizStatus.WAITING_ANSWER;
        } else {
            event.getChannel().sendMessage("Sorry " + user.getName()
                    + ", Du hast schon alle Fragen beantwortet. Warte bis es neue gibt ;)").queue();
        }
    }

    public static class RankingTableEntry {
        private Long userId;
        private String userName;
        private int points;
        private int answered;
        private int created;

        public RankingTableEntry(Long userId, String userName, int points, int answered, int created) {

            this.userId = userId;
            this.userName = userName;
            this.points = points;
            this.answered = answered;
            this.created = created;
        }

        public float getRate() {
            return (float) points / (float) answered;
        }

        public int getCreated() {
            return created;
        }

        public int getAnswered() {
            return answered;
        }

        public String getUserName() {
            return userName;
        }

        public int getPoints() {
            return points;
        }

        public Long getUserId() {
            return userId;
        }
    }
}
