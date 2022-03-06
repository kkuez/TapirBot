package tapir.pokemon;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.PrivateChannel;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.Event;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.interactions.components.Button;
import net.dv8tion.jda.api.requests.restaction.MessageAction;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;
import tapir.DBService;
import tapir.ReceiveModule;

import java.io.*;
import java.net.URL;
import java.nio.charset.Charset;
import java.sql.SQLOutput;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class PokeModule extends ReceiveModule {

    private static Pokemon currentPokemon;
    private static JDA bot;
    private static Thread catchThread;

    public PokeModule(DBService dbService, Set<TextChannel> allowedChannels, Set<Long> userNotAllowedToAsk, JDA bot) {
        super(dbService, allowedChannels, userNotAllowedToAsk);
        this.bot = bot;
        startCatchLoop();
    }

    private void startCatchLoop() {
        if (catchThread != null) {
            return;
        }

        catchThread = new Thread(() -> {
            long oneHourAsMilliSecs = 3600000;
            while (true) {
                long timeToWait = 0;
                while (timeToWait < 300000) {
                    final double random = Math.random();
                    timeToWait = Math.round(random * oneHourAsMilliSecs);
                }
                System.out.println(LocalDateTime.now().withNano(0).toString() + " Starting new Pokemon-Loop, waiting "
                        + timeToWait/1000);
                try {
                    Thread.sleep(timeToWait);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                try {
                    currentPokemon = getPokemon();
                    makeCurrentAppear();
                    makeOldDisappear();
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        });
        catchThread.start();
    }

    private void makeOldDisappear() {
        Thread disappearThread = new Thread(() -> {
            System.out.println("Starting disappearThread");
            if (currentPokemon != null) {
                try {
                    Thread.sleep(300000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                final String disappearedMessage = "\n...und wieder verschwunden.";
                bot.getTextChannels().stream().filter(channel -> channel.getName().contains("pokemon")).forEach(channel -> {
                    channel.getIterableHistory()
                            .takeAsync(100)
                            .thenApply(list -> {
                                final List<Message> messagesToEdit = list.stream()
                                        .filter(message -> message.getContentRaw().contains("ist erschienen!")
                                                && !message.getContentRaw().contains("hats gefangen!")
                                        && !message.getContentRaw().contains(disappearedMessage))
                                        .collect(Collectors.toList());

                                messagesToEdit.forEach(message -> {
                                    MessageBuilder messageBuilder = new MessageBuilder();
                                    messageBuilder.append(message.getContentRaw() + disappearedMessage)
                                            .setActionRows();
                                    message.editMessage(messageBuilder.build()).queue();
                                });
                                return messagesToEdit;
                            });
                });
                currentPokemon = null;
            }
        });
        disappearThread.start();
    }

    private void makeCurrentAppear() {
        File pictureTemp;
        try {
            pictureTemp = File.createTempFile("pokemon", ".png)");
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        try (FileOutputStream fos = new FileOutputStream(pictureTemp);
             final BufferedInputStream bis =
                     new BufferedInputStream(new URL(currentPokemon.getPictureUrlString()).openStream())) {
            bis.transferTo(fos);
            bot.getTextChannels().stream().filter(channel -> channel.getName().contains("pokemon"))
                    .forEach(channel -> {
                        final MessageAction messageAction = channel
                                .sendMessage("Ein wildes **" + currentPokemon.getName() + "** ist erschienen!");
                        messageAction
                                .addFile(pictureTemp)
                                .setActionRow(Button.primary(NON_VALID_USER + " poke catch", "Fangen!"))
                                .queue();
                    });
            pictureTemp.delete();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private Pokemon getPokemon() throws IOException {
        long index = 0;
        while (index == 0) {
            double random = Math.random();
            index = Math.round(151 * random);

        }

        JSONObject json;
        try (InputStream is = new URL("https://pokeapi.co/api/v2/pokemon-species/" + index + "/").openStream()) {
            BufferedReader rd = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")));
            String jsonText = readAll(rd);
            json = new JSONObject(jsonText);
        }

        String name = null;
        final JSONArray genera = json.getJSONArray("names");
        for (int i = 0; i < genera.length(); i++) {
            final JSONObject jsonObject = genera.getJSONObject(i);
            if (jsonObject.getJSONObject("language").getString("name").equals("de")) {
                name = jsonObject.getString("name");
                break;
            }
        }

        final double levelDouble = Math.random() * 100;
        final long level = Math.round(levelDouble);

        return new Pokemon((int) index, name, (int) level);
    }

    private static String readAll(Reader rd) throws IOException {
        StringBuilder sb = new StringBuilder();
        int cp;
        while ((cp = rd.read()) != -1) {
            sb.append((char) cp);
        }
        return sb.toString();
    }

    @Override
    public Set<String> getCommands() {
        return Set.of(
                "p",
                "poke",
                "pokemon",
                "poké",
                "pokémon",
                "catch",
                "fangen",
                "dex"
        );
    }

    @Override
    public void handle(User user, String[] messages, TextChannel channel, Optional<Event> event) {

        switch (messages[messages.length - 1].toLowerCase()) {
            case "catch":
                if (currentPokemon == null) {
                    return;
                }

                final ButtonClickEvent buttonClickEvent = (ButtonClickEvent) event.get();
                Pokemon pokemon = currentPokemon;
                currentPokemon = null;
                final Message message = new MessageBuilder().append(buttonClickEvent.getMessage().getContentRaw()
                                + "\n*" +
                                buttonClickEvent.getInteraction().getMember().getUser().getName() + "* hats gefangen!")
                        .build();
                buttonClickEvent.getMessage().editMessage(message).queue();

                getDbService().registerCaughtPokemon(user, pokemon);
                break;
            case "dex":
            case "pokedex":
            case "pokédex":
                final GuildMessageReceivedEvent guildMessageReceivedEvent = (GuildMessageReceivedEvent) event.get();
                List<Pokemon> pokemonList = getDbService().getPokemonOfUser(user);
                StringBuilder builder = new StringBuilder();

                builder.append("*").append(user.getName());
                if (pokemonList.size() == 0) {
                    builder.append("*, du hast noch keine Pokémon gefangen.");
                } else if (pokemonList.size() < 50) {
                    builder.append("*, du hast erst ").append(pokemonList.size()).append(" Pokémon gefangen\n");
                } else {
                    builder.append("*, du hast schon ").append(pokemonList.size()).append(" Pokémon gefangen :o\n");
                }

                for (Pokemon pokemonFromList : pokemonList) {
                    builder.append("\n").append(pokemonFromList.getPokedexIndex()).append(": **")
                            .append(pokemonFromList.getName()).append("**, Level: *").append(pokemonFromList.getLevel())
                            .append("*");
                }

                guildMessageReceivedEvent.getMessage().reply(builder.toString()).queue();
                break;
        }
    }

    @Override
    public boolean waitingForAnswer() {
        return false;
    }

    @Override
    public void handlePM(User user, String toLowerCase, JDA bot, PrivateChannel channel) {

    }
}
