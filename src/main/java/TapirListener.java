import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.Properties;

public abstract class TapirListener extends ListenerAdapter {

    private final Properties properties;
    private JDA bot;
    private final DBService dbService;

    public TapirListener(Properties properties, DBService dbService, JDA bot) {
        this.dbService = dbService;
        this.properties = properties;
        this.bot = bot;
    }

    /**
     * Check if user is already in db etc
     * */
    public void doUserCheck(User author) {
        dbService.handleUser(author);
    }

    public Properties getProperties() {
        return properties;
    }

    public JDA getBot() {
        return bot;
    }

    public DBService getDbService() {
        return dbService;
    }
}
