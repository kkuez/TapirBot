package tapir;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.PrivateChannel;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.events.message.priv.PrivateMessageReceivedEvent;
import tapir.db.DBService;
import tapir.exception.TapirException;
import tapir.pokemon.PokeModule;
import tapir.quiz.QuizModule;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.ZoneId;
import java.util.*;

public class UserWrapper {
    private static PokeModule pokeModule;
    private static final String PROGRAM_NAME = "TapirBot.jar";
    private Map<Class, ReceiveModule> modules;
    private final User user;
    private Long lastInteraction = System.currentTimeMillis() - 1000;

    public UserWrapper(User user) {
        if (modules == null) {
            modules = new HashMap<>();
        }
        this.user = user;
    }

    public static void init(DBService dbService, Set<TextChannel> allowedChannels, Set<TextChannel> pokeChannels,
                            Integer pokemonMaxFreq, Set<Long> userNotAllowedToAsk, JDA bot) {
        pokeModule = new PokeModule(dbService, pokeChannels, pokemonMaxFreq, userNotAllowedToAsk);
    }

    public Map<Class, ReceiveModule> getModules() {
        return modules;
    }

    public User getUser() {
        return user;
    }

    public void handleButton(ButtonClickEvent event, String inputWhole) {
    if(!checkForLastInteraction()) {
        return;
    }
        final String[] split = inputWhole.split(QuizModule.MESSAGE_SEPERATOR + "");
        switch (split[0].toLowerCase().replace("!", "")) {
            case "quiz":
                if (!split[2].equals(event.getInteraction().getUser().getId())) {
                    final String userNamePressedButton = event.getInteraction().getMember().getUser().getName();
                    event.getChannel().sendMessage("Sorry " + userNamePressedButton + ", das ist" +
                            " nicht dein Button!!! :o").queue();
                    return;
                }

                if(event.getChannel() instanceof PrivateChannel) {
                    modules.get(QuizModule.class).handlePM(user, inputWhole, event.getJDA(),
                            (PrivateChannel) event.getChannel(), Optional.of(event));
                } else {
                    modules.get(QuizModule.class).handle(user, event.getButton().getId().split(" "), event.getChannel(),
                            Optional.of(event));
                    break;
                }
            case "p":
            case "poke":
                modules.computeIfAbsent(PokeModule.class, pokeClass -> {
                    return pokeModule;
                }).handle(event.getUser(),
                        event.getButton().getId().split(" "), event.getChannel(), Optional.of(event));
                break;
        }

        // https://stackoverflow.com/questions/70386672/button-interaction-failed
        // Buttons always have to be acknowledged like this
        try{
            event.deferEdit().queue();
        } catch (Exception exception) {
            System.out.println(exception.getMessage());
        }
    }

    public void handle(GuildMessageReceivedEvent event, DBService dbService, Set<TextChannel> allowedChannels,
                       Set<Long> userNotAllowedToAsk) {
        if(!checkForLastInteraction()) {
            return;
        }
        final String message = event.getMessage().getContentRaw().replace("!", "").split(" ")[0].toLowerCase();
        //TODO Very late init of modules here, init in constructor
        switch (message) {
            case "q":
            case "quiz":
                modules.computeIfAbsent(QuizModule.class, quizClass -> new QuizModule(dbService, allowedChannels,
                        userNotAllowedToAsk)).handle(user, event.getMessage().getContentRaw().split(" "),
                        event.getChannel(), Optional.of(event));
                break;
            case "p":
            case "poke":
            case "poké":
            case "pokemon":
            case "pokémon":
                //Direkt adressing as there is only one global PokeModul in opposite to the many Quizmodules.
                //TODO Refactorn (s. oben)?
                pokeModule.handle(user, event.getMessage().getContentRaw().split(" "),
                        event.getChannel(), Optional.of(event));
                break;
            case "v":
            case "version":
                final String[] filesInProgramPath = new File(".").getAbsoluteFile().getParentFile().list();
                final Optional<String> tapirJar = Arrays.stream(filesInProgramPath)
                        .filter(fileName -> fileName.contains(PROGRAM_NAME)).findAny();
                if (tapirJar.isEmpty()) {
                    event.getMessage().reply("Das scheint nicht Prod zu sein, kein Version möglich :o").queue();
                } else {
                    try {
                        final FileTime lastModifiedTimeUniversalTime =
                                Files.getLastModifiedTime(Path.of(tapirJar.get()));
                        final String rightTime =
                                lastModifiedTimeUniversalTime.toInstant().atZone(ZoneId.systemDefault()).toString();
                        event.getChannel().sendMessage(rightTime).queue();
                    } catch (IOException e) {
                        throw new TapirException("Could not get version!", e);
                    }
                }
                break;
            default:
                for (ReceiveModule module : modules.values()) {
                    if (module.waitingForAnswer()) {
                        module.handle(user, event.getMessage().getContentRaw().split(" "), event.getChannel(),
                                Optional.of(event));
                    }
                }
        }
    }

    private boolean checkForLastInteraction() {
        boolean valid = true;
        final Long now = System.currentTimeMillis();
        if((now - lastInteraction) < 1000) {
            valid = false;
        }
        lastInteraction = now;
        return valid;
    }

    public void handlePM(PrivateMessageReceivedEvent event, DBService dbService, JDA bot,
                         Set<TextChannel> allowedChannels, Set<Long> userNotAllowedToAsk) {
        if(!checkForLastInteraction()) {
            return;
        }
        final String fullWithoutAusrufezeichen = event.getMessage().getContentRaw().replace("!", "");
        final String message = fullWithoutAusrufezeichen.split(" ")[0].toLowerCase();
        switch (message) {
            case "q":
            case "quiz":
                modules.computeIfAbsent(QuizModule.class, quizClass -> new QuizModule(dbService, allowedChannels,
                        userNotAllowedToAsk)).handlePM(user,
                        fullWithoutAusrufezeichen, bot, event.getChannel(), Optional.empty());
                break;
            case "p":
            case "poke":
            case "poké":
            case "pokemon":
            case "pokémon":
                //Direkt adressing as there is only one global PokeModul in opposite to the many Quizmudols.
                //TODO Refactorn (s. oben)?
                pokeModule.handlePM(user, fullWithoutAusrufezeichen, bot, event.getChannel(), Optional.of(event));
                break;
            default:
                for (ReceiveModule module : modules.values()) {
                    if (module.waitingForAnswer()) {
                        module.handlePM(user, fullWithoutAusrufezeichen, bot, event.getChannel(), Optional.of(event));
                    }
                }
        }
    }
}
