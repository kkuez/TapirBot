package tapir;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;

import javax.annotation.Nonnull;
import java.util.Properties;

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
