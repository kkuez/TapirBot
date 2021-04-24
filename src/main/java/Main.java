import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.TextChannel;

import javax.security.auth.login.LoginException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

public class Main {


    private static Properties properties;
    private static List<Long> channelIds;
    private static DBService dbService;

    public static void main(String[] args) throws LoginException, InterruptedException, IOException {
        setup();
        final JDA bot = setupBot();

        final TextChannel textChannelById = bot.getTextChannelById(channelIds.get(0));
        final Guild guild = textChannelById.getGuild();
        final List<Member> members = guild.getMembers();
        bot.addEventListener(new NoPMListener(properties, dbService, bot));
        bot.addEventListener(new PMListener(properties, dbService, bot));

        Scanner scanner = new Scanner(System.in);
        while(true) {
            if(scanner.hasNextLine()){
                textChannelById.sendMessage(scanner.nextLine()).queue();
            }
            Thread.sleep(200);
        }
    }

    private static JDA setupBot() throws InterruptedException, LoginException {
        final JDA bot = JDABuilder.createDefault(properties.getProperty("token")).build();
        bot.awaitStatus(JDA.Status.CONNECTED);
        return bot;
    }

    private static void setup() throws IOException {
        properties = new Properties();
        final InputStream setupPropertiesStream = new FileInputStream(new File(".", "setup.properties"));
        properties.load(setupPropertiesStream);

        dbService = new DBService(properties);

        final String[] channels = properties.getProperty("channels").split(";");
        channelIds = Arrays.stream(channels).map(Long::parseLong).collect(Collectors.toList());
    }
}
