import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.events.message.priv.PrivateMessageReceivedEvent;
import net.dv8tion.jda.internal.JDAImpl;
import net.dv8tion.jda.internal.entities.UserImpl;

import java.util.HashMap;
import java.util.Map;

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

    public void handle(GuildMessageReceivedEvent event, DBService dbService, JDA bot) {
        final String message = event.getMessage().getContentRaw().replace("!", "").split(" ")[0].toLowerCase();

        switch (message) {
            case "q":
            case "quiz":
                modules.computeIfAbsent(Quiz.class, quizClass -> new Quiz(dbService)).handle(user, event.getMessage()
                                .getContentRaw(), bot, event.getChannel());
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

    public void handlePM(PrivateMessageReceivedEvent event, DBService dbService, JDA bot) {
        for(ReceiveModule module: modules.values()) {
            if(module.waitingForAnswer()) {
                module.handlePM(user, event.getMessage().getContentRaw().replace("!", "").toLowerCase(),
                        bot, event.getChannel());
            }
        }
    }
}
