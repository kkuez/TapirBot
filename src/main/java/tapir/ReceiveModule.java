package tapir;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.PrivateChannel;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.Event;
import tapir.db.DBService;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public abstract class ReceiveModule {
    private Set<TextChannel> generalChannels;
    private Set<Long> userNotAllowedToAsk;
    private DBService dbService;
    private static final ExecutorService EXECUTOR_SERVICE = Executors.newScheduledThreadPool(2);

    public ReceiveModule(DBService dbService, Set<TextChannel> generalChannels, Set<Long> userNotAllowedToAsk) {
        this.dbService = dbService;
        this.generalChannels = generalChannels;
        this.userNotAllowedToAsk = userNotAllowedToAsk;
    }

    public abstract Set<String> getCommands();

    public abstract void handle(User user, String[] messages, MessageChannel channel, Optional<Event> event);

    public abstract boolean waitingForAnswer();

    public Set<TextChannel> getGeneralChannels() {
        return generalChannels;
    };

    public Set<Long> getUserNotAllowedToAsk() {
        return userNotAllowedToAsk;
    }

    public DBService getDbService() {
        return dbService;
    }

    public static ExecutorService getExecutorService() {
        return EXECUTOR_SERVICE;
    }

    public abstract void handlePM(User user, String toLowerCase, JDA bot, PrivateChannel channel, Optional<Event> event);
}
