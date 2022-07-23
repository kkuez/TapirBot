package tapir.quiz;

import tapir.db.factories.QuestionAttachmentFactory;
import tapir.db.entities.QuestionAttachmentEntity;
import tapir.db.entities.QuizQuestionEntity;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.Message.Attachment;
import net.dv8tion.jda.api.events.Event;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.events.message.priv.PrivateMessageReceivedEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.Button;
import tapir.db.DBService;
import tapir.ReceiveModule;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
    public static final Pattern HTTP_PICTURE_PATTERN = Pattern.compile("(http|https)://.*(png|bmp|tiff|jpg|jpeg)");
    private String explaination;

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
    public void handle(User user, String[] messages, MessageChannel channel, Optional<Event> event) {
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
                    final String answerText = answerNr == 4 ? "Keine Ahnung!" : answers.get(answerNr).getText();

                    final Message message = new MessageBuilder()
                            .append(questionAfterTrim).append("\n*").append(user.getName())
                            .append("* hat geantwortet mit \"*")
                            .append(answerText).append("*\"!\n")
                            .append("Das Tapir-Orakel meint: *\"").append(getRandomWhoKnowsString()).append("\"*")
                            .build();
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

    private String getRandomWhoKnowsString() {
        final List<String> of = List.of(
                "Ob das wohl stimmt...?",
                "Ob das wirklich richtig ist, seht ihr wenn...ihr sie/ihn fragt!! (...oder so)",
                "Kann ja auch sein?",
                "Schien mir aber ganz schön unentschlossen beim Tippen...",
                "DAS HAT WOHL DER TEUFEL VERRATEN!!",
                "Hm... wirklich?",
                "Ach, jetzt ernsthaft oder was?",
                "Wer weiß, ob das stimmt.",
                "...thihihi... eh bestimmt alles gelogen O:)",
                "...so wahr ich Knut heiße!",
                "Klingt doch ganz gut die Antwort!?",
                "Naja... ich weiß ja nicht!",
                "Weise Worte werden nicht jedem zu teil...",
                "Da sprach wohl der Alkohol aus dir.",
                "Früher hätte man sich für die Antwort verkriechen und schämen müssen!",
                "Berti sagt \"C ist richtig!\"",
                "Igitt!!!",
                "Vertraue IMMER auf dein Bauchgefühl, außer du hast Diarrhoe!",
                "Ob das wohl so klug war?",
                "Da war wohl Verzweiflung am Werk!"
        );
        final ArrayList<String> strings = new ArrayList<>(of);
        Collections.shuffle(strings);
        return strings.get(0);
    }

    private void newQuestion(User user) {
        if (getUserNotAllowedToAsk().contains(user.getIdLong())) {
            return;
        }

        String sendToUser = user.getName() + ", schreib mir bitte jetzt eine PM mit deiner Frage! (Abbrechen mit" +
                " !abbruch)";
        user.openPrivateChannel().queue((channel) -> channel.sendMessage(sendToUser).queue());
        status = QuizStatus.WAITING_QUESTION;
        answers = new ArrayList<>(4);
        question = null;
    }

    private void help(MessageChannel channel) {
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
        return status.equals(QuizStatus.WAITING_ANSWER) ||
                status.equals(QuizStatus.WAITING_QUESTION) ||
                status.equals(QuizStatus.WAITING_QUESTION_ANSWERS) ||
                status.equals(QuizStatus.WAITING_ANSWER_EXPLAINATION);
    }

    @Override
    public void handlePM(User user, String message, JDA bot, PrivateChannel channel, Optional<Event> event) {
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
                enterNewQuestionViaPM(message, channel, event);
                break;
            case WAITING_QUESTION_ANSWERS:
            case WAITING_ANSWER_EXPLAINATION:
                enterAnswersViaPM(user, message, channel, event);
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

    private void enterAnswersViaPM(User user, String input, PrivateChannel channel, Optional<Event> event) {
        for (int i = 0; i < 6; i++) {
            if (answers.size() == i) {
                answers.add(new QuizAnswer(input, i == 0 ? RIGHT_ANSWER :
                        i == 1 ? WRONG_ANSWER_1 :
                                i == 2 ? WRONG_ANSWER_2 :
                                        i == 3 ? WRONG_ANSWER_3 : ""));
                if (i == 0) {
                    channel.sendMessage("...und die erste falsche? (Abbrechen mit !abbruch hier via PM)").queue();
                    break;
                } else if (i == 1) {
                    channel.sendMessage("...und die zweite falsche? (Abbrechen mit !abbruch hier via PM)").queue();
                    break;
                } else if (i == 2) {
                    channel.sendMessage("...und die dritte falsche? (Abbrechen mit !abbruch hier via PM)").queue();
                    break;
                } else if (i == 3) {
                    MessageBuilder messageBuilder = new MessageBuilder("Möchtest du der richtigen Antwort eine Erklärung anfügen?");
                    messageBuilder.setActionRows(ActionRow.of(
                            Button.primary("!quiz " + "yesButton " + user.getIdLong(), "Ja"),
                            Button.primary("!quiz " + "noButton " + user.getIdLong(), "Nein")));
                    channel.sendMessage(messageBuilder.build()).queue();
                    break;
                } else if (i == 4) {
                    final ButtonClickEvent buttonClickEvent = (ButtonClickEvent) event.get();
                    MessageBuilder messageBuilder = new MessageBuilder("Möchtest du der richtigen Antwort eine Erklärung anfügen?");
                    buttonClickEvent.editMessage(messageBuilder.build()).queue();

                    final String[] split = input.split(" ");
                    if (split[1].startsWith("yes")) {
                        status = QuizStatus.WAITING_ANSWER_EXPLAINATION;
                        channel.sendMessage("Tippe bitte jetzt die Erklärung zur richtigen Antwort ein.").queue();
                        break;
                    }
                } else if (i == 5) {
                    final PrivateMessageReceivedEvent privateMessageReceivedEvent = (PrivateMessageReceivedEvent) event.get();
                    try {
                        final QuestionAttachmentsAndDescriptionWrapper questionAttachmentsAndDescriptionWrapper =
                                findAndReplaceAndGetAttachmentOfHttpLinks(privateMessageReceivedEvent.getMessage().getContentRaw(), Optional.empty());
                        questionAttachmentsAndDescriptionWrapper.getAttachments()
                                .forEach(ea -> ea.setCategory(AttachmentCategory.EXPLAINATION));
                        explaination = questionAttachmentsAndDescriptionWrapper.getDescription();
                        question.getAttachments().addAll(questionAttachmentsAndDescriptionWrapper.getAttachments());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    getDbService().enterQuestion(user, question, answers, explaination);
                    if (status.equals(QuizStatus.WAITING_ANSWER_EXPLAINATION)) {
                        explaination = input;
                    }
                    StringBuilder messageBuilder = new StringBuilder("Danke, das gibt einen Punkt für dich :)");
                    if (!question.getAttachments().isEmpty() && question.getAttachments().size() > 1) {
                        messageBuilder.append("\nHinweis: Es werden zwar alle Bilder gespeichert, derzeit wird aber ")
                                .append("nur das erste dem Fragenden angezeigt!");
                    }

                    channel.sendMessage(messageBuilder).queue();
                    getGeneralChannels().forEach(channel1 ->
                            channel1.sendMessage(user.getName() + " hat eine neue Frage erstellt!").queue());
                    status = QuizStatus.NONE;
                    break;
                }
            }
        }
    }

    private void enterNewQuestionViaPM(String description, PrivateChannel channel, Optional<Event> event) {
        final PrivateMessageReceivedEvent privateMessageReceivedEvent = (PrivateMessageReceivedEvent) event.get();
        final List<Attachment> attachments = privateMessageReceivedEvent.getMessage().getAttachments();
        final List<QuestionAttachment> attachmentsPojos = new ArrayList<>(attachments.size());
        File attachmentsFolder = getAttachmentsFolder();

        for (Attachment attachment : attachments) {
            final UUID uuid = UUID.randomUUID();
            final String attachmentFileName = uuid + "." + attachment.getFileExtension();
            attachment.downloadToFile(new File(attachmentsFolder, attachmentFileName));
            final QuestionAttachment questionAttachment =
                    QuestionAttachmentFactory.createPojo(AttachmentCategory.DESCRIPTION, attachmentFileName);
            attachmentsPojos.add(questionAttachment);
        }

        try {
            final QuestionAttachmentsAndDescriptionWrapper questionAttachmentsAndDescriptionWrapper =
                    findAndReplaceAndGetAttachmentOfHttpLinks(description, Optional.empty());
            attachmentsPojos.addAll(questionAttachmentsAndDescriptionWrapper.getAttachments());
            question = new QuizQuestion(99, questionAttachmentsAndDescriptionWrapper.getDescription(), null, null, "",
                    attachmentsPojos);

            channel.sendMessage("Wie lautet die richtige Antwort? (Abbrechen mit !abbruch hier via PM)").queue();
            status = QuizStatus.WAITING_QUESTION_ANSWERS;
        } catch (IOException e) {
            e.printStackTrace();
            channel.sendMessage("Sorry, es konnte kein Bild mit dem Link gefunden werden, ist der wirklich richtig?")
                    .queue();
        }
    }

    private QuestionAttachmentsAndDescriptionWrapper findAndReplaceAndGetAttachmentOfHttpLinks(String description, Optional<Integer> questionIdOpt)
            throws IOException {
        List<QuestionAttachment> attachments = new ArrayList<>(0);
        final Matcher matcher = HTTP_PICTURE_PATTERN.matcher(description);
        while (matcher.find()) {
            final String linkToPicture = matcher.group();
            String fileNameStored = UUID.randomUUID() + linkToPicture.substring(linkToPicture.lastIndexOf("."));
            final File file = new File(getAttachmentsFolder(), fileNameStored);
            try (final BufferedInputStream bis = new BufferedInputStream(new URL(linkToPicture).openStream());
                 final FileOutputStream fos = new FileOutputStream(file)) {
                bis.transferTo(fos);
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }
            description = description.replace(linkToPicture, "");
            final QuestionAttachment questionAttachmentEntity =
                    QuestionAttachmentFactory.createPojo(questionIdOpt, AttachmentCategory.DESCRIPTION, file.getName());
            attachments.add(questionAttachmentEntity);
        }

        return new QuestionAttachmentsAndDescriptionWrapper(description, attachments);
    }

    private File getAttachmentsFolder() {
        File attachmentsFolder = new File(".", "QuestionAttachments");
        if (attachmentsFolder.exists() && attachmentsFolder.isDirectory()) {
            return attachmentsFolder;
        }

        attachmentsFolder.mkdir();
        return attachmentsFolder;
    }

    private void info(MessageChannel channel, boolean global) {
        List<RankingTableEntry> userScores = getDbService().getUserScoresPointRated();

        if (!global) {
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
            if (entry.getAnswered() < 10) {
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
                return ":third_place:";
            default:
                return i + "";
        }
    }

    private void filterMembers(MessageChannel channel, List<RankingTableEntry> userScores) {
        TextChannel textChannel = (TextChannel) channel;
        final Set<Long> memberInChanIds = textChannel.getMembers().stream()
                .map(member -> member.getIdLong()).collect(Collectors.toSet());

        for (int i = 0; i < userScores.size(); i++) {
            if (!memberInChanIds.contains(userScores.get(i).getUserId())) {
                userScores.remove(i);
                //lower i cuz list shrinks of 1 element
                i--;
            }
        }
    }

    private void checkAnswer(User user, int answerNr) {
        int rightAnswerIndex = getRightAnswerIndex();
        String answerOfUser = NO_CLUE;

        final MessageBuilder sendToUserBuilder = new MessageBuilder();
        if (answerNr == rightAnswerIndex) {
            //send right
            sendToUserBuilder.append("Yessa ").append(user.getName()).append("! Das war richtig, +3 Punkte für dich!");

            if (question.getExplaination() != null && !question.getExplaination().equals("")) {
                sendToUserBuilder.append("\nAls Erklärung schrieb ").append(question.getCreatorName()).append(":\n\"")
                        .append(question.getExplaination()).append("\"");
            }

            answerOfUser = RIGHT_ANSWER;
        } else {
            if (answerNr == 4) {
                // send mid
                sendToUserBuilder.append("Hm ok... Nix gewonnen, nix verloren.");
            } else {
                //send wrong
                sendToUserBuilder.append("Autsch ").append(user.getName()).append(" :( Leider falsch, -2 Punkte!\n ")
                        .append("Die richtige Antwort ist: ").append(answers.get(rightAnswerIndex).getText());

                if (question.getExplaination() != null && !question.getExplaination().equals("")) {
                    sendToUserBuilder.append("\nAls Erklärung schrieb ").append(question.getCreatorName()).append(":\n\"")
                            .append(question.getExplaination()).append("\"");
                }
                answerOfUser = answers.get(answerNr).getColumn();
            }
        }

        getDbService().sendAnswer(user.getIdLong(), question.getId(), answerOfUser);

        final List<QuestionAttachment> explainationAttachments =
                question.getAttachments().stream()
                        .filter(attachment -> attachment.getCategory().equals(AttachmentCategory.EXPLAINATION))
                        .collect(Collectors.toList());
        if(explainationAttachments.isEmpty()) {
            user.openPrivateChannel().queue((channel) -> channel.sendMessage(sendToUserBuilder.build()).queue());
        } else {
            final String attachmentFileName = explainationAttachments.get(0).getFileName();
            final File attachmentFile = new File(getAttachmentsFolder(), attachmentFileName);
            user.openPrivateChannel().queue((channel) -> channel.sendMessage(sendToUserBuilder.build())
                    .addFile(attachmentFile).queue());
        }
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
            //TODO Either use the entity or a quesiton object!!!!!
            question = questionsForUser.get(0);

            QuizQuestionEntity questionEntity = getDbService().getQuestionById(question.getId());
            List<QuizAnswer> answers = question.getAnswers();
            Collections.shuffle(answers);
            this.answers = answers;
            final List<QuestionAttachment> questionAttachments = getDbService().getQuestionAttachments(question.getId());
            question.getAttachments().addAll(questionAttachments);

            try {
                findAndReplaceAttachmentsInLinks(questionEntity);

                MessageBuilder questionBuilder = new MessageBuilder();
                final String userName = getDbService().getUserInfoById(questionEntity.getUser()).get("name");
                questionBuilder.append(user.getName()).append(", deine Frage von **").append(userName)
                        .append("**:\n ");
                questionBuilder.append("**").append(questionEntity.getText()).append("**\n");
                questionBuilder.append("*Antwort 1:*\t** ").append(answers.get(0).getText()).append("**").append("\n");
                questionBuilder.append("*Antwort 2:*\t** ").append(answers.get(1).getText()).append("**").append("\n");
                questionBuilder.append("*Antwort 3:*\t** ").append(answers.get(2).getText()).append("**").append("\n");
                questionBuilder.append("*Antwort 4:*\t** ").append(answers.get(3).getText()).append("**").append("\n");

                final String buttonIdBeginn = QUIZ + MESSAGE_SEPERATOR + "answer"
                        + MESSAGE_SEPERATOR + user.getId() + MESSAGE_SEPERATOR;
                questionBuilder.setActionRows(ActionRow.of(
                        Button.primary(buttonIdBeginn + 0, "Antwort 1"),
                        Button.primary(buttonIdBeginn + 1, "Antwort 2"),
                        Button.primary(buttonIdBeginn + 2, "Antwort 3"),
                        Button.primary(buttonIdBeginn + 3, "Antwort 4"),
                        Button.primary(buttonIdBeginn + 4, "Keine Ahnung!")));
                final List<QuestionAttachment> descriptionAttachments =
                        question.getAttachments().stream().filter(questionAttachment -> questionAttachment.getCategory()
                        .equals(AttachmentCategory.DESCRIPTION)).collect(Collectors.toList());

                if (descriptionAttachments.size() > 0) {
                    final File attachmentFile =
                            new File(getAttachmentsFolder(), descriptionAttachments.get(0).getFileName());
                    event.getMessage().reply(questionBuilder.build()).addFile(attachmentFile).queue();
                } else {
                    event.getMessage().reply(questionBuilder.build()).queue();
                }

                this.status = QuizStatus.WAITING_ANSWER;
            } catch (IOException e) {
                e.printStackTrace();
                event.getChannel().sendMessage("Sorry, in der Frage gibt es noch einen alten Link zu einem Bild, das " +
                                "nicht mehr gefunden werden kann. Ich stelle die Frage erstmal zurück. Sag @kkuez Bescheid!")
                        .queue();
                System.out.println("Kaputte Frage: " + question.getText());
            }

        } else {
            event.getChannel().sendMessage("Sorry " + user.getName()
                    + ", Du hast schon alle Fragen beantwortet. Warte bis es neue gibt ;)").queue();
        }
    }

    /**
     * To replace pictures in questions with old links in http. Those should be refreshed here
      */
    private void findAndReplaceAttachmentsInLinks(QuizQuestionEntity questionEntity) throws IOException {
        final QuestionAttachmentsAndDescriptionWrapper questionAttachmentsAndDescriptionWrapper =
                findAndReplaceAndGetAttachmentOfHttpLinks(question.getText(), Optional.of(question.getId()));
        if (!questionAttachmentsAndDescriptionWrapper.getAttachments().isEmpty()) {
            question.getAttachments().addAll(questionAttachmentsAndDescriptionWrapper.getAttachments());
            getDbService().addQuestionAttachments(questionAttachmentsAndDescriptionWrapper.getAttachments(),
                    Optional.of(question.getId()));
            questionEntity.setText(questionAttachmentsAndDescriptionWrapper.getDescription());
            getDbService().updateQuestionEntity(questionEntity);
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

    /**
     * Just a helper class to return description AND all attachments in #findAndReplaceAndGetAttachmentOfHttpLinks
     */
    private class QuestionAttachmentsAndDescriptionWrapper {
        private String description;
        private List<QuestionAttachment> attachment;

        public QuestionAttachmentsAndDescriptionWrapper(String description, List<QuestionAttachment> attachment) {
            this.description = description;
            this.attachment = attachment;
        }

        public String getDescription() {
            return description;
        }

        public List<QuestionAttachment> getAttachments() {
            return attachment;
        }
    }
}
