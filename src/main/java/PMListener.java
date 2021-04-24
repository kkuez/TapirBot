import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.events.message.priv.PrivateMessageReceivedEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Properties;

public class PMListener extends TapirListener {
    public PMListener(Properties properties, DBService dbService, JDA bot) {
        super(properties, dbService, bot);
    }

    @Override
    public void onPrivateMessageReceived(@NotNull PrivateMessageReceivedEvent event) {
        super.onPrivateMessageReceived(event);

        final UserWrapper userWrapper = doUserCheck(event.getAuthor());

        userWrapper.handlePM(event, getDbService(), getBot());
    }
}
