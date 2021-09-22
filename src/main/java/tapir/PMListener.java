package tapir;

import java.util.Properties;
import java.util.Set;
import javax.annotation.Nonnull;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.message.priv.PrivateMessageReceivedEvent;

public class PMListener extends TapirListener {

    public PMListener(Properties properties, DBService dbService, JDA bot, Set<TextChannel> allowedChannels
            , Set<Long> userNotAllowedToAsk) {
        super(properties, dbService, bot, allowedChannels, userNotAllowedToAsk);
    }

    @Override
    public void onPrivateMessageReceived(@Nonnull PrivateMessageReceivedEvent event) {
        super.onPrivateMessageReceived(event);
        final UserWrapper userWrapper = doUserCheck(event.getAuthor());
        userWrapper.handlePM(event, getDbService(), getBot(), getAllowedChannels(), getUserNotAllowedToAsk());
    }
}
