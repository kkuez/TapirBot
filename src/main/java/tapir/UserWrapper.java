package tapir;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.events.message.priv.PrivateMessageReceivedEvent;
import tapir.quiz.QuizModule;

import java.util.*;

public class UserWrapper {
    private Map<Class, ReceiveModule> modules;
    private User user;

    public UserWrapper(User user) {
        modules = new HashMap<>();
        this.user = user;
    }

    public Map<Class, ReceiveModule> getModules() {
        return modules;
    }

    public User getUser() {
        return user;
    }

    public void handleButton(String moduleName, ButtonClickEvent event, List<String> params) {
        final Optional<ReceiveModule> moduleOpt =
                modules.values().stream().filter(module -> module.getCommands().contains(moduleName.toLowerCase()))
                        .findFirst();

        if (moduleOpt.isPresent()) {
            moduleOpt.get().handle(user, event.getButton().getId(), event.getTextChannel(), Optional.of(event));
        } else {
            throw new RuntimeException("No module found for " + moduleName);
        }

    }

    public void handle(GuildMessageReceivedEvent event, DBService dbService, Set<TextChannel> allowedChannels,
                       Set<Long> userNotAllowedToAsk) {
        final String message = event.getMessage().getContentRaw().replace("!", "").split(" ")[0].toLowerCase();
        switch (message) {
            case "q":
            case "quiz":
                modules.computeIfAbsent(QuizModule.class, quizClass -> new QuizModule(dbService, allowedChannels,
                        userNotAllowedToAsk)).handle(user, event.getMessage().getContentRaw(),
                        event.getChannel(), Optional.of(event));
                break;
            default:
                for (ReceiveModule module : modules.values()) {
                    if (module.waitingForAnswer()) {
                        module.handle(user, event.getMessage().getContentRaw(), event.getChannel(), Optional.of(event));
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
            default:
                for (ReceiveModule module : modules.values()) {
                    module.handlePM(user, fullWithoutAusrufezeichen,
                            bot, event.getChannel());
                }
        }
    }
}
