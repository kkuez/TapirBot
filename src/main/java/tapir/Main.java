package tapir;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;

import javax.security.auth.login.LoginException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;


public class Main {
    private static Properties properties;
    private static DBService dbService;

    public static void main(String[] args) throws LoginException, InterruptedException, IOException {
        setup();
        final JDA bot = setupBot();

        final Set<TextChannel> allowedChannels = getAllowedChannels(bot);

        bot.addEventListener(new NoPMListener(properties, dbService, bot, allowedChannels));
        bot.addEventListener(new PMListener(properties, dbService, bot, allowedChannels));

        while(true) {
            Thread.sleep(200);
        }
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
