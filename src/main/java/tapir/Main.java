package tapir;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;

import javax.security.auth.login.LoginException;
import java.io.*;
import java.util.*;
import java.util.stream.Collectors;


public class Main {
    private static Properties properties;
    private static DBService dbService;

    public static void main(String[] args) throws LoginException, InterruptedException, IOException {
        setup();
        final JDA bot = setupBot();

        final Set<TextChannel> allowedChannels = getAllowedChannels(bot);
        Set<Long> userNotAllowedToAsk = getUserNotAllowedToAsk();

        bot.addEventListener(new NoPMListener(properties, dbService, bot, allowedChannels, userNotAllowedToAsk));
        bot.addEventListener(new PMListener(properties, dbService, bot, allowedChannels, userNotAllowedToAsk));

        try(Scanner scanner = new Scanner(System.in);)
        {
            while(true) {
                if(scanner.hasNextLine()) {
                    String line = scanner.nextLine();
                    final String[] cmds = line.split(" ");
                    if(cmds.length > 1) {
                        switch (cmds[0].toLowerCase()) {
                            case "noask":
                                final Map<String, String> userInfo = dbService.getUserInfo(cmds[1]);
                                userNotAllowedToAsk = new HashSet<>();
                                userNotAllowedToAsk.add(Long.parseLong(userInfo.get("id")));
                                for(String userIdAsString: ((String) properties.get("userNotAllowedToAsk")).split(";")) {
                                    if(!userIdAsString.equals("")) {
                                        userNotAllowedToAsk.add(Long.parseLong(userIdAsString));
                                    }
                                }

                                String replaceString = userNotAllowedToAsk.stream().map(userId -> userId + ";").collect(Collectors.joining());
                                properties.replace("userNotAllowedToAsk", replaceString);
                                properties.store(new FileOutputStream(new File(".", "setup.properties")),"");
                                break;
                            case "delque":
                                dbService.deleteQuestionWhereLike(line.replace(cmds[0] + " ", ""));
                                break;
                            case "harvest":

                                break;
                            default:
                        }
                    }
                }
            }
        }
    }

    private static Set<Long> getUserNotAllowedToAsk() {
        final String usersNotAllowedToAsk = (String) properties.get("userNotAllowedToAsk");
        Set<Long> usersNotAllowedToAskSet;
        if(!usersNotAllowedToAsk.equals("")) {
            usersNotAllowedToAskSet = Arrays.stream((usersNotAllowedToAsk).split(";"))
                    .map(longAsString -> Long.parseLong(longAsString)).collect(Collectors.toSet());
        } else {
            usersNotAllowedToAskSet = new HashSet<>();
        }
        return usersNotAllowedToAskSet;
    }

    private static Set<TextChannel> getAllowedChannels(JDA bot) {
        Set<TextChannel> generalChannels = new HashSet<>();
        try(InputStream setupPropertiesStream = new FileInputStream(new File(".", "setup.properties"))) {
            Properties properties = new Properties();
            properties.load(setupPropertiesStream);
            final String generalChannelProperty = (String) properties.get("generalChannels");
            final Set<String> generalChannelNames =
                    Arrays.stream(generalChannelProperty.split(";")).collect(Collectors.toSet());

            bot.getTextChannels().forEach(channel -> {
                if(generalChannelNames.contains(channel.getName())) {
                    generalChannels.add(channel);
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
        return generalChannels;
    }

    private static JDA setupBot() throws InterruptedException, LoginException {
        final JDA bot = JDABuilder.createDefault(properties.getProperty("token"))
                .setChunkingFilter(ChunkingFilter.ALL) // enable member chunking for all guilds
                .setMemberCachePolicy(MemberCachePolicy.ALL)
                .enableIntents(GatewayIntent.GUILD_MEMBERS).build();
        bot.awaitStatus(JDA.Status.CONNECTED);
        return bot;
    }

    private static void setup() throws IOException {
        properties = new Properties();
        final InputStream setupPropertiesStream = new FileInputStream(new File(".", "setup.properties"));
        properties.load(setupPropertiesStream);

        dbService = new DBService(properties);
    }
}
