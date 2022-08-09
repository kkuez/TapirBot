package tapir;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import tapir.db.DBService;
import tapir.exception.TapirException;

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
        final Set<TextChannel> pokeChannels = getPokeChannels(bot);
        Set<Long> userNotAllowedToAsk = getUserNotAllowedToAsk();

        bot.addEventListener(new NoPMListener(properties, dbService, bot, allowedChannels, userNotAllowedToAsk));
        bot.addEventListener(new PMListener(properties, dbService, bot, allowedChannels, userNotAllowedToAsk));
        UserWrapper.init(dbService, allowedChannels, pokeChannels,
                Integer.parseInt((String) properties.get("pokemonMaxFreq")), userNotAllowedToAsk, bot);

        try(Scanner scanner = new Scanner(System.in);)
        {
            while(true) {
                if(scanner.hasNextLine()) {
                    String line = scanner.nextLine();
                    final String[] cmds = line.split(" ");
                    if(cmds.length > 1) {
                        switch (cmds[0].toLowerCase()) {
                            case "noask":
                                final Map<String, String> userInfo = dbService.getUserInfoByName(cmds[1]);
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
                            case "chat":
                                String channelName = cmds[1];
                                String message = getMessageString(cmds, line);
                                final HashMap<String, TextChannel> allChannels = new HashMap<>(allowedChannels.size() + pokeChannels.size());
                                allowedChannels.forEach(channel -> allChannels.put(channel.getName(), channel));
                                pokeChannels.forEach(channel -> allChannels.put(channel.getName(), channel));
                                allChannels.get(channelName).sendMessage(new StringBuilder(message)).queue();
                                break;
                            default:
                                System.out.println("Hab ich nicht verstanden...");
                        }
                    } else {
                        System.out.println("Hab ich nicht verstanden...");
                    }
                }
            }
        }
    }

    private static String getMessageString(String[] cmds, String line) {
        String message = line.replace(cmds[0], "").replace(cmds[1], "");
        while(message.startsWith(" ")) {
            message = message.replaceFirst(" ", "");
        }

        return message;
    }

    public static boolean isDev() {
        final File[] files = new File(".").listFiles();
        return Arrays.stream(files).filter(file -> file.getName().toLowerCase().contains("isdev")).findAny().isPresent();
    }

    private static Set<TextChannel> getPokeChannels(JDA bot) {
        Set<TextChannel> pokemonChannels = new HashSet<>();
        try(InputStream setupPropertiesStream = new FileInputStream(new File(".", "setup.properties"))) {
            Properties properties = new Properties();
            properties.load(setupPropertiesStream);
            final String pokemonChannelProperty = (String) properties.get("pokemonChannels");
            final Set<String> generalChannelNames =
                    Arrays.stream(pokemonChannelProperty.split(";")).collect(Collectors.toSet());

            bot.getTextChannels().forEach(channel -> {
                if(generalChannelNames.contains(channel.getName())) {
                    pokemonChannels.add(channel);
                }
            });
        } catch (IOException e) {
            throw new TapirException("Could not get PokeChannels!", e);
        }
        return pokemonChannels;
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
            throw new TapirException("Could not get allowed channels!", e);
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
