package tapir;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.PrivateChannel;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.Event;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;

import java.util.Optional;
import java.util.Set;

public abstract class ReceiveModule {
    private Set<TextChannel> generalChannels;
    private Set<Long> userNotAllowedToAsk;
    private DBService dbService;
    public static final int NON_VALID_USER = 999;

    public ReceiveModule(DBService dbService, Set<TextChannel> generalChannels, Set<Long> userNotAllowedToAsk) {

        this.dbService = dbService;
        this.generalChannels = generalChannels;
        this.userNotAllowedToAsk = userNotAllowedToAsk;
    }

    public abstract Set<String> getCommands();

    public abstract void handle(User user, String[] messages, TextChannel channel, Optional<Event> event);

    public abstract boolean waitingForAnswer();

    public abstract void handlePM(User user, String toLowerCase, JDA bot, PrivateChannel channel);

    public Set<TextChannel> getGeneralChannels() {
        return generalChannels;
    };

    public Set<Long> getUserNotAllowedToAsk() {
        return userNotAllowedToAsk;
    }

    public DBService getDbService() {
        return dbService;
    }


}
