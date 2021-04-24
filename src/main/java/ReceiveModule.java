import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;

import java.util.Set;

public interface ReceiveModule {
    public Set<String> getCommands();

    void handle(User user, String message, JDA bot, TextChannel channel);

    boolean waitingForAnswer();
}
