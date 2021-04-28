package tapir;

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

import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.FileAppender;
import org.apache.log4j.PatternLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class Main {
    private static Properties properties;
    private static DBService dbService;

    public static void main(String[] args) throws LoginException, InterruptedException, IOException {
        setup();
        final JDA bot = setupBot();

        bot.addEventListener(new NoPMListener(properties, dbService, bot));
        bot.addEventListener(new PMListener(properties, dbService, bot));

        while(true) {
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
    }
}
