package tapir;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

public abstract class TapirListener extends ListenerAdapter {

    private final Properties properties;
    private JDA bot;
    private Set<TextChannel> allowedChannels;
    private final DBService dbService;
    private static Map<String, UserWrapper> userWrapperMap = new HashMap<String, UserWrapper>();

    public TapirListener(Properties properties, DBService dbService, JDA bot, Set<TextChannel> allowedChannels) {
        this.dbService = dbService;
        this.properties = properties;
        this.bot = bot;
        this.allowedChannels = allowedChannels;
    }

    /**
     * Check if user is already in db etc
     *
     *
     * @return*/
    public UserWrapper doUserCheck(User author) {
        final UserWrapper userWrapper = userWrapperMap.computeIfAbsent(author.getId(), id -> new UserWrapper(author));
        dbService.handleUser(userWrapper, allowedChannels);
        return userWrapper;
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

    public Map<String, UserWrapper> getUserWrapperMap() {
        return userWrapperMap;
    }

    public Set<TextChannel> getAllowedChannels() {
        return allowedChannels;
    }
}
