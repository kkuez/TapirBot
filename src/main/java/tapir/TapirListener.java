package tapir;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import tapir.quiz.QuizModule;

import java.util.*;
import java.util.stream.Collectors;

public abstract class TapirListener extends ListenerAdapter {

    private final Properties properties;
    private final Set<Long> userNotAllowedToAsk;
    private JDA bot;
    private Set<TextChannel> allowedChannels;
    private final DBService dbService;
    private static Map<String, UserWrapper> userWrapperMap = new HashMap<String, UserWrapper>();

    public TapirListener(Properties properties, DBService dbService, JDA bot, Set<TextChannel> allowedChannels,
                         Set<Long> userNotAllowedToAsk) {
        this.dbService = dbService;
        this.properties = properties;
        this.bot = bot;
        this.allowedChannels = allowedChannels;
        this.userNotAllowedToAsk = userNotAllowedToAsk;
    }

    /**
     * Check if user is already in db etc
     *
     *
     * @return*/
    public UserWrapper doUserCheck(User author) {
        final UserWrapper userWrapper = userWrapperMap.computeIfAbsent(author.getId(), id -> new UserWrapper(author));
        dbService.handleUser(userWrapper);
        return userWrapper;
    }


    @Override
    public void onButtonClick(ButtonClickEvent event) {
         doUserCheck(event.getUser());
         doUserCheck(event.getInteraction().getMember().getUser());
         String buttonId = doButtonStringValidityCheck(event);

         //TODO Ab hbier mal refactorn
        final String[] split = buttonId.split(QuizModule.MESSAGE_SEPERATOR + "");
        final String userIdString = split[0];
        getUserWrapperMap().get(userIdString).handleButton(event, split);
    }

    /**
     * We replace the users number here as Buttons get either called by the user, f.e. a user asking for a Quizquestion,
     * or when its called by the system without a user calling, f.e. when posting pokemon.
     * In this case we put the pressing users number in front the be able to dispatch the request to the relating
     * module.
     * */
    private String doButtonStringValidityCheck(ButtonClickEvent event) {
        final String id = event.getButton().getId();
        final String[] split = id.split(" ");
        if(split[0].equals(ReceiveModule.NON_VALID_USER + "")) {
            return id
                    .replace(ReceiveModule.NON_VALID_USER + "", event.getInteraction().getMember().getId());
        }
        return id;
    }

    private List<String> getParamsFromButtonId(String[] split) {
        final List<String> collect = Arrays.stream(split).collect(Collectors.toList());
        collect.remove(split[0]);
        collect.remove(split[1]);
        return collect;
    }

    public Properties getProperties() {
        return properties;
    }

    public Set<Long> getUserNotAllowedToAsk() {
         return userNotAllowedToAsk;
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
