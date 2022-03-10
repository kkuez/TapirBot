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
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.Button;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.MessageAction;
import org.json.JSONArray;
import org.json.JSONObject;
import tapir.DBService;
import tapir.ReceiveModule;

import java.io.*;
import java.net.URL;
import java.nio.charset.Charset;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

public class PokeModule extends ReceiveModule {

    private static Pokemon currentPokemon;
    private Integer pokemonMaxFreq;
    private static JDA bot;
    private static final int MAXCOUNT = 3;

    public PokeModule(DBService dbService, Set<TextChannel> allowedChannels, Integer pokemonMaxFreq,
                      Set<Long> userNotAllowedToAsk, JDA bot) {
        super(dbService, allowedChannels, userNotAllowedToAsk);
        this.pokemonMaxFreq = pokemonMaxFreq;
        this.bot = bot;
        startCatchLoop();
    }

    private void startCatchLoop() {
        final Runnable loopRunnable = () -> {
            long oneHourAsMilliSecs = 3600000;

            while (true) {
                //long timeToWait = 10000;
                long timeToWait = 0;
                while (timeToWait < 300000) {
                    final double random = Math.random();
                    timeToWait = Math.round(random * pokemonMaxFreq * 1000);
                }
                System.out.println(LocalDateTime.now().withNano(0).toString() + " Starting new Pokemon-Loop, waiting "
                        + timeToWait / 1000);
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
        };
        getExecutorService().submit(loopRunnable);
    }

    private void makeOldDisappear() {
        final Runnable disappearRunnable = () -> {
            System.out.println("Starting disappearThread");
            if (currentPokemon != null) {
                try {
                    Thread.sleep(300000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                final String disappearedMessage = "\n...und wieder verschwunden.";
                getGeneralChannels().forEach(channel -> {
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
                                    messageBuilder.append(message.getContentRaw()).append(disappearedMessage)
                                            .setActionRows();
                                    message.editMessage(messageBuilder.build()).queue();
                                });
                                return messagesToEdit;
                            });
                });
                currentPokemon = null;
            }
        };

        getExecutorService().submit(disappearRunnable);
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
            getGeneralChannels().forEach(channel -> {
                final MessageAction messageAction = channel
                        .sendMessage("Ein wildes **" + currentPokemon.getName() + "** ist erschienen!");
                messageAction
                        .addFile(pictureTemp)
                        .setActionRow(Button.primary("poke catch", "Fangen!"))
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
                "dex",
                "pokedex",
                "pokédex"
        );
    }

    @Override
    public void handle(User user, String[] messages, TextChannel channel, Optional<Event> event) {

        switch (messages[1].toLowerCase()) {
            case "catch":
                processCatch(user, event);
                break;
            case "dex":
            case "pokedex":
            case "pokédex":
                processPokedex(user, messages, event);
                break;
            case "swap":
                if(messages.length != 3) return;
                final long idFromMention = getUserIdFromMention(messages[2]);
                final RestAction<PrivateChannel> privateChannelRestAction = event.get().getJDA().openPrivateChannelById(idFromMention);
                MessageBuilder testBuilder = new MessageBuilder("asd");
                final Message build = testBuilder.setActionRows(ActionRow.of(Button.primary("asd", "asd"))).build();
                final GuildMessageReceivedEvent event1 = (GuildMessageReceivedEvent) event.get();
                event1.getMessage().getAuthor().openPrivateChannel().queue((channel1) -> channel1.sendMessage(build).queue());
                break;
        }
    }

    private void processPokedex(User user, String[] messages, Optional<Event> event) {
        final GuildMessageReceivedEvent guildMessageReceivedEvent = (GuildMessageReceivedEvent) event.get();
        List<Pokemon> pokemonList;
        StringBuilder builder = new StringBuilder("__*");
        if (messages.length > 2 && messages[2].contains("<@!") && messages[2].contains(">")) {
            final long id = getUserIdFromMention(messages[2]);

            pokemonList = getDbService().getPokemonOfUser(id);
            Map<String, String> mentionedUser = getDbService().getUserInfoById(id);
            builder.append(mentionedUser.get("name")).append("* hat");
        } else {
            pokemonList = getDbService().getPokemonOfUser(user);
            builder.append(user.getName()).append("*, du hast");
        }

        final int size = pokemonList.stream().map(Pokemon::getName).collect(Collectors.toSet()).size();
        if (size == 0) {
            builder.append(" noch keine");
        } else if (size < 50) {
            builder.append(" erst **").append(size).append("** unterschiedliche");
        } else {
            builder.append(" schon **").append(size).append("** unterschiedliche");
        }
        builder.append(" Pokémon gefangen:__");

        for (Pokemon pokemonFromList : pokemonList) {
            builder.append("\n").append(pokemonFromList.getPokedexIndex()).append(": **")
                    .append(pokemonFromList.getName()).append("**, Level: *").append(pokemonFromList.getLevel())
                    .append("*");
        }

        guildMessageReceivedEvent.getMessage().reply(builder.toString()).queue();
    }

    private long getUserIdFromMention(String message) {
        final String userIdString = message.replace("<@!", "").replace(">", "");
        return Long.parseLong(userIdString);
    }

    private void processCatch(User user, Optional<Event> event) {
        if (currentPokemon == null) {
            return;
        }

        final ButtonClickEvent buttonClickEvent = (ButtonClickEvent) event.get();
        Pokemon pokemon = currentPokemon;
        currentPokemon = null;
        final User interactedUser = buttonClickEvent.getInteraction().getUser();
        final List<Pokemon> pokemonOfUser =
                getDbService().getPokemonOfUser(interactedUser.getIdLong());
        int count = 0;

        for (Pokemon pokemon1 : pokemonOfUser) {
            if (pokemon.getName().equals(pokemon1.getName())) {
                count++;
            }
        }

        final String userName = buttonClickEvent.getInteraction().getMember().getUser().getName();
        if (count > MAXCOUNT) {
            MessageBuilder builder = new MessageBuilder();
            builder.append("Sorry ").append(userName).append(", du hast schon ").append(MAXCOUNT)
                    .append(" Stück!\n").append(pokemon.getName()).append(" ist entkommen.");

            if(Math.random() > 0.5D) {
                int countToSteal = 3;
                builder.append("\n\n...Und was ist das?! \n*Team Rocket* ist erschienen und hat dir **")
                        .append(countToSteal).append("** zufällige Pokemon stibitzt D:");
                Collections.shuffle(pokemonOfUser);
                pokemonOfUser.subList(0, countToSteal).forEach(pokemonToRemove -> {
                    getDbService().removePokemonFromUser(pokemonToRemove, interactedUser);
                builder.append("\n**").append(pokemonToRemove.getName())
                        .append("**, Level:*").append(pokemonToRemove.getLevel()).append("*")
;                });
            }
            buttonClickEvent.getChannel().sendMessage(builder.build()).queue();
            return;
        }

        final Message message = new MessageBuilder()
                .append(buttonClickEvent.getMessage().getContentRaw())
                .append("\n*")
                .append(userName)
                .append("* hats gefangen!")
                .build();
        buttonClickEvent.getMessage().editMessage(message).queue();

        getDbService().registerCaughtPokemon(user, pokemon);
    }

    @Override
    public boolean waitingForAnswer() {
        return false;
    }

    @Override
    public void handlePM(User user, String toLowerCase, JDA bot, PrivateChannel channel) {

    }

    private class SwapPair {
        private User from;
        private User to;
        private SwapStatus status;

        public SwapPair(User from, User to) {
            this.from = from;
            this.to = to;
            status = SwapStatus.NONE;
        }

        public User getFrom() {
            return from;
        }

        public User getTo() {
            return to;
        }

        public SwapStatus getStatus() {
            return status;
        }

        public void setStatus(SwapStatus status) {
            this.status = status;
        }
    }

    private enum SwapStatus {
        NONE, USER_ONE_TO_SWAP, BOTH_ACCEPTED_POKEMON_SELECT, USER_ONE_ACCEPTED_EXCHANGE;
    }
}
