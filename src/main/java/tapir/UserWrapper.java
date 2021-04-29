package tapir;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.events.message.priv.PrivateMessageReceivedEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

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

    public void handle(GuildMessageReceivedEvent event, DBService dbService, JDA bot, Set<TextChannel> allowedChannels,
                       Set<Long> userNotAllowedToAsk) {
        final String message = event.getMessage().getContentRaw().replace("!", "").split(" ")[0].toLowerCase();
        switch (message) {
            case "q":
            case "quiz":
                modules.computeIfAbsent(Quiz.class, quizClass -> new Quiz(dbService, allowedChannels,
                        userNotAllowedToAsk))
                        .handle(user, event.getMessage().getContentRaw(), bot, event.getChannel());
                break;
            default:
                for(ReceiveModule module: modules.values()) {
                    if(module.waitingForAnswer()) {
                        module.handle(user, event.getMessage().getContentRaw().replace("!", "").toLowerCase(),
                                bot, event.getChannel());
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
                modules.computeIfAbsent(Quiz.class, quizClass -> new Quiz(dbService, allowedChannels,
                        userNotAllowedToAsk)).handlePM(user,
                        fullWithoutAusrufezeichen, bot, event.getChannel());
                break;
            default:
                for(ReceiveModule module: modules.values()) {
                    module.handlePM(user, fullWithoutAusrufezeichen,
                            bot, event.getChannel());
                }
        }
    }
}
