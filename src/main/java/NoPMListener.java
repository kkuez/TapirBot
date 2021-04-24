import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import java.util.Properties;
import java.util.Set;

public class NoPMListener extends TapirListener {

    private Set<ReceiveModule> receiveModules;

    public NoPMListener(Properties properties, DBService dbService, JDA bot) {
        super(properties, dbService, bot);
        receiveModules = Set.of(new Quiz(dbService));
    }

    @Override
    public void onGuildMessageReceived(@Nonnull GuildMessageReceivedEvent event) {
        final User author = event.getAuthor();
        doUserCheck(author);

        String messageRaw = event.getMessage().getContentDisplay();
        if(!messageRaw.startsWith("!")) { return; }

        final String message = messageRaw.replace("!", "").toLowerCase();
        for(ReceiveModule receiveModule: receiveModules) {
            if(receiveModule.waitingForAnswer() || receiveModule.getCommands().contains(message.split(" ")[0])) {
                receiveModule.handle(author, message, getBot(), event.getChannel());
            }
        }

        System.out.println();
    }
}
