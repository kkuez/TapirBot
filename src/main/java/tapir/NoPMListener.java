package tapir;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import tapir.db.DBService;
import tapir.exception.TapirException;

import java.util.Properties;
import java.util.Set;
import javax.annotation.Nonnull;


public class NoPMListener extends TapirListener {

    public NoPMListener(Properties properties, DBService dbService, JDA bot, Set<TextChannel> allowedChannels,
                        Set<Long> userNotAllowedToAsk) {
        super(properties, dbService, bot, allowedChannels, userNotAllowedToAsk);
    }

    @Override
    public void onGuildMessageReceived(@Nonnull GuildMessageReceivedEvent event) {
        try {
            String messageRaw = event.getMessage().getContentDisplay();
            if (!messageRaw.startsWith("!")) {
                return;
            }
            logEvent(event);
            final UserWrapper userWrapper = doUserCheck(event.getAuthor());
            userWrapper.handle(event, getDbService(), getAllowedChannels(), getUserNotAllowedToAsk());
        } catch (Exception e) {
            event.getChannel().sendMessage("Ups!\nDa ist was schiefgegangen :(\n@kkuez");
            throw new TapirException("", e);
        }
    }
}
