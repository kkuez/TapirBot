package tapir;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.events.message.priv.PrivateMessageReceivedEvent;
import tapir.pokemon.PokeModule;
import tapir.quiz.QuizModule;

import java.util.*;

public class UserWrapper {
    private static PokeModule pokeModule;
    private Map<Class, ReceiveModule> modules;
    private User user;

    public UserWrapper(User user) {
        if(modules == null) {
            modules = new HashMap<>();
        }
        this.user = user;
    }

    public static void init(DBService dbService, Set<TextChannel> allowedChannels, Set<TextChannel> pokeChannels,
                            Set<Long> userNotAllowedToAsk, JDA bot) {
        pokeModule = new PokeModule(dbService, pokeChannels, userNotAllowedToAsk, bot);
    }

    public Map<Class, ReceiveModule> getModules() {
        return modules;
    }

    public User getUser() {
        return user;
    }

    public void handleButton(ButtonClickEvent event, String[] split) {

        switch (split[0].toLowerCase()) {
            case "quiz":
                if(!split[2].equals(event.getInteraction().getMember().getId())) {
                    final String userNamePressedButton = event.getInteraction().getMember().getUser().getName();
                    event.getChannel().sendMessage("Sorry " + userNamePressedButton + ", das ist" +
                            " nicht dein Button!!! :o").queue();
                    return;
                }

                modules.get(QuizModule.class).handle(user, event.getButton().getId().split(" "), event.getTextChannel()
                        , Optional.of(event));
                break;
            case "poke":
                modules.computeIfAbsent(PokeModule.class, pokeClass -> {
                    return pokeModule;
                }).handle(event.getInteraction().getMember().getUser(),
                        event.getButton().getId().split(" "), event.getTextChannel(), Optional.of(event));
                break;
        }
    }

    public void handle(GuildMessageReceivedEvent event, DBService dbService, Set<TextChannel> allowedChannels,
                       Set<Long> userNotAllowedToAsk) {
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
            default:
                for (ReceiveModule module : modules.values()) {
                    if (module.waitingForAnswer()) {
                        module.handle(user, event.getMessage().getContentRaw().split(" "), event.getChannel(),
                                Optional.of(event));
                    }
                }
        }
    }

    public void handlePM(PrivateMessageReceivedEvent event, DBService dbService, JDA bot,
                         Set<TextChannel> allowedChannels, Set<Long> userNotAllowedToAsk) {
        final String fullWithoutAusrufezeichen = event.getMessage().getContentRaw().replace("!", "");
        final String message = fullWithoutAusrufezeichen.split(" ")[0].toLowerCase();
        switch (message) {
            case "q":
            case "quiz":
                modules.computeIfAbsent(QuizModule.class, quizClass -> new QuizModule(dbService, allowedChannels,
                        userNotAllowedToAsk)).handlePM(user,
                        fullWithoutAusrufezeichen, bot, event.getChannel());
                break;
            case "p":
            case "poke":
            case "poké":
            case "pokemon":
            case "pokémon":
                //Direkt adressing as there is only one global PokeModul in opposite to the many Quizmudols.
                //TODO Refactorn (s. oben)?
                pokeModule.handlePM(user, fullWithoutAusrufezeichen, bot, event.getChannel());
                break;
            default:
                for (ReceiveModule module : modules.values()) {
                    module.handlePM(user, fullWithoutAusrufezeichen,
                            bot, event.getChannel());
                }
        }
    }
}
