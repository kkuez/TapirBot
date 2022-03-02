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

    public abstract Set<String> getCommands();

    public abstract void handle(User user, String messageContentRaw, TextChannel channel, Optional<Event> event);

    public abstract boolean waitingForAnswer();

    public abstract void handlePM(User user, String toLowerCase, JDA bot, PrivateChannel channel);

    public void setGeneralChannels(Set<TextChannel> generalChannels) {
        this.generalChannels = generalChannels;
    }

    public Set<TextChannel> getGeneralChannels() {
        return generalChannels;
    };
}
