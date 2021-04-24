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

    public NoPMListener(Properties properties, DBService dbService, JDA bot) {
        super(properties, dbService, bot);
    }

    @Override
    public void onGuildMessageReceived(@Nonnull GuildMessageReceivedEvent event) {
        final UserWrapper userWrapper = doUserCheck(event.getAuthor());

        String messageRaw = event.getMessage().getContentDisplay();
        if(!messageRaw.startsWith("!")) { return; }

        userWrapper.handle(event, getDbService(), getBot());
    }
}
