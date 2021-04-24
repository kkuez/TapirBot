package tapir;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.PrivateChannel;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;

import java.util.Set;

public abstract class ReceiveModule {
    public abstract Set<String> getCommands();

    public abstract void handle(User user, String message, JDA bot, TextChannel channel);

    public abstract boolean waitingForAnswer();

    public abstract void handlePM(User user, String toLowerCase, JDA bot, PrivateChannel channel);
}
