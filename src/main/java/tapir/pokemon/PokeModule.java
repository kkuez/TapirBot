package tapir.pokemon;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.Event;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.events.message.priv.PrivateMessageReceivedEvent;
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
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class PokeModule extends ReceiveModule {

    private static Pokemon currentPokemon;
    private Integer pokemonMaxFreq;
    private static JDA bot;
    private static final int MAXCOUNT = 3;
    private static final Set<Swap> SWAP_PAIRS = new HashSet<>(2);
    private static final String WRITE_ME_THE_CODE_WITH = "Schreibe mir die Codes mit";
    private static final String SWAP_DECLINED_STRING = " hat abgelehnt, Tausch beendet :(";
    private static final Pattern CODE_PATTERN = Pattern.compile("[a-z][a-z] \\t\\| [A-Z][a-z]* Lvl\\..*");

    public PokeModule(DBService dbService, Set<TextChannel> allowedChannels, Integer pokemonMaxFreq,
                      Set<Long> userNotAllowedToAsk, JDA bot) {
        super(dbService, allowedChannels, userNotAllowedToAsk);
        this.pokemonMaxFreq = pokemonMaxFreq;
        this.bot = bot;
        startCatchLoop();
    }

    private void startCatchLoop() {

        final Runnable runnable = () -> {
            long oneHourAsMilliSecs = 3600000;
            boolean isDebug = false;
            if(pokemonMaxFreq < 100) {
                isDebug = true;
            }
            while (true) {
                //long timeToWait = 10000;
                long timeToWait =  1000;

                while (!isDebug && timeToWait < 300000) {
                    final double random = Math.random();
                    timeToWait = Math.round(random * pokemonMaxFreq * 1000);
                }
                if(isDebug) {
                    timeToWait = pokemonMaxFreq * 1000;
                }
                System.out.println("Starting new Pokemon-Loop, next pokemon: "
                        + LocalDateTime.now().plusSeconds(timeToWait / 1000).withNano(0).toString());
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
        }; final Runnable loopRunnable = runnable;
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
        while (index == 0 || index == 152) {
            double random = Math.random();
            index = Math.round(152 * random);
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

        return new Pokemon(null, (int) index, name, (int) level);
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
                "pokédex",
                "swap",
                "free"
        );
    }

    @Override
    public void handle(User user, String[] messages, MessageChannel channel, Optional<Event> event) {

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
                processSwap(user, messages, event);
                break;
            case "free":
                if (messages.length == 2) {
                    postGeneralFreeMessage(user);
                } else {
                    final Map<String, Pokemon> codeMap = getCodeMap(getDbService().getPokemonOfUser(user));
                    final List<Pokemon> pokemonToDelete = Arrays.stream(messages[2].split(","))
                            .map(code -> codeMap.get(code)).collect(Collectors.toList());
                    StringBuilder pokemonBuilder = new StringBuilder();
                    getDbService().removePokemonFromUser(pokemonToDelete);

                    for (Pokemon pokemon : pokemonToDelete) {
                        pokemonBuilder.append("\nFreigelassen: ")
                                .append(pokemon.getName()).append(" Lvl. ").append(pokemon.getLevel());
                    }
                    user.openPrivateChannel().queue((privateChannel) ->
                            privateChannel.sendMessage(pokemonBuilder).queue());
                    removeMessagesFromChannelIfWithCode(((PrivateMessageReceivedEvent) event.get()).getChannel());
                }
                break;
        }
    }

    private void removeMessagesFromChannelIfWithCode(PrivateChannel channel) {
        channel.getIterableHistory()
                .takeAsync(100)
                .thenApply(list -> {
                    final List<Message> messagesToEdit = new ArrayList<>();
                    listLoop:
                    for (Message message : list) {
                        lineLoop:
                        for (String line : message.getContentRaw().split("\n")) {
                            if (CODE_PATTERN.matcher(line).matches()) {
                                messagesToEdit.add(message);
                                break;
                            }
                        }
                    }

                    messagesToEdit.forEach(message -> {
                        MessageBuilder messageBuilder = new MessageBuilder("CodeListe ist benutzt worden...");
                        message.editMessage(messageBuilder.build()).queue();
                    });
                    return messagesToEdit;
                });
    }

    private void processSwap(User user, String[] messages, Optional<Event> event) {
        if (messages.length < 3) return;

        Optional<Swap> swapPairOpt = Optional.empty();
        try {
            swapPairOpt = SWAP_PAIRS.stream().filter(sp -> sp.containsUsers(user)).findAny();
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (swapPairOpt.isEmpty() && event.get() instanceof GuildMessageReceivedEvent) {
            final GuildMessageReceivedEvent guildMessageReceivedEvent = (GuildMessageReceivedEvent) event.get();
            final User fromUser = guildMessageReceivedEvent.getAuthor();
            final User toUser = event.get().getJDA().getUserById(getUserIdFromMention(messages[2]));
            final RestAction<PrivateChannel> privateChannelRestAction =
                    event.get().getJDA().openPrivateChannelById(toUser.getIdLong());

            final Swap swapPair = new Swap(fromUser, toUser);
            SWAP_PAIRS.add(swapPair);

            MessageBuilder toMessageBuilder = new MessageBuilder(fromUser.getName()).append(" fragt dich ob du " +
                    "Pokemon tauschen möchtest?");
            final Message toBuild = toMessageBuilder.setActionRows(ActionRow.of(Button.primary("poke swap "
                    + swapPair.getUuid() + " Ja", "Ja"), Button.primary("poke swap "
                    + swapPair.getUuid() + " Nein", "Nein"))).build();
            toUser.openPrivateChannel().queue((channel1) -> channel1.sendMessage(toBuild).queue());

            MessageBuilder fromMessageBuilder = new MessageBuilder("Tauschen: Warte auf Bestätigung von ")
                    .append(toUser.getName()).append("...");
            fromUser.openPrivateChannel().queue((channel1) -> channel1.sendMessage(fromMessageBuilder.build())
                    .queue());
        } else if (swapPairOpt.isPresent()) {
            swapPairOpt.get().processSwapFurther(event, messages, user);
        }
    }

    private void postGeneralFreeMessage(User user) {
        final List<Pokemon> pokemonOfUser = getDbService().getPokemonOfUser(user);

        StringBuilder builder = new StringBuilder("Welches Pokemon willst du " +
                "freilassen?\n" + WRITE_ME_THE_CODE_WITH + " !p free <CODE> (wenn du mehrere Pokemons freilassen " +
                "willst, dann trenne die Codes mit einem Komma, z. B. \"!p free ac,cx,de\")!");
        builder.append("\n:octagonal_sign: __Es empfiehlt sich, mehrere Pokémon auf einmal freizulassen. Solltest du sie" +
                " einzeln freilassen wollen, mache nach jedem Pokémon ein !p free um eine aktualisierte Codeliste" +
                " zu bekommen__ :octagonal_sign: ");

        Map<String, Pokemon> codeMap = getCodeMap(pokemonOfUser);
        List<String> codeMapKeys = codeMap.keySet().stream().collect(Collectors.toList());
        Collections.sort(codeMapKeys);

        int index = 0;
        for (String codeMapKey : codeMapKeys) {
            final Pokemon pokemon = codeMap.get(codeMapKey);
            builder.append("\n" + codeMapKey + " \t| " + pokemon.getName() + " Lvl. " + pokemon.getLevel());
            if (index != 0 && (index % 50 == 0 || codeMapKeys.size() == index + 1) && builder.length() != 0) {
                String outputString = builder.toString();
                user.openPrivateChannel().queue((channel1) -> channel1.sendMessage(outputString).queue());
                builder = new StringBuilder("\n");
            }
            index++;
        }
    }

    private Map<String, Pokemon> getCodeMap(List<Pokemon> pokemonOfUser) {
        Map<String, Pokemon> codeMap = new HashMap<>(pokemonOfUser.size());
        char a = 97;
        char aa = 97;
        for (Pokemon pokemon : pokemonOfUser) {
            codeMap.put(a + "" + aa, pokemon);
            aa++;
            if (aa == 122) {
                aa = 97;
                a++;
            }
        }
        return codeMap;
    }

    private void processPokedex(User user, String[] messages, Optional<Event> event) {
        if (!(event.get() instanceof GuildMessageReceivedEvent)) {
            //TODO
            user.openPrivateChannel().queue((channel1) -> channel1.sendMessage("!p dex ist leider nicht in privaten" +
                    " Nachrichten möglich :/ (Aber auf der TODO Liste!)").queue());
            return;
        }

        final GuildMessageReceivedEvent guildMessageReceivedEvent = (GuildMessageReceivedEvent) event.get();
        List<Pokemon> pokemonList;
        StringBuilder builder = new StringBuilder("__*");
        // TODO das riesige try catch begrenzen
        try {
            final String pokedexURL;
            if (messages.length > 2 && messages[2].contains("<@!") && messages[2].contains(">")) {
                final long id = getUserIdFromMention(messages[2]);

                pokemonList = getDbService().getPokemonOfUser(id);
                Map<String, String> mentionedUser = getDbService().getUserInfoById(id);
                builder.append(mentionedUser.get("name")).append("* hat");
                pokedexURL = createPokedexPage(pokemonList, mentionedUser.get("name"));

            } else {
                pokemonList = getDbService().getPokemonOfUser(user);
                builder.append(user.getName()).append("*, du hast");
                pokedexURL = createPokedexPage(pokemonList, user.getName());
            }

            final int size = pokemonList.stream().map(Pokemon::getName).collect(Collectors.toSet()).size();
            if (size == 0) {
                builder.append(" noch keine");
            } else if (size < 50) {
                builder.append(" erst **").append(size).append("** unterschiedliche");
            } else {
                builder.append(" schon **").append(size).append("** unterschiedliche");
            }
            builder.append(" Pokémon gefangen:__\n").append(pokedexURL);

            guildMessageReceivedEvent.getMessage().reply(builder.toString()).queue();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String createPokedexPage(List<Pokemon> pokemonList, String username) throws IOException {
        final LocalDateTime now = LocalDateTime.now().withNano(0);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm:ss");
        StringBuilder builder = new StringBuilder("<html><head><meta charset=\"utf-8\"><title>")
                .append(username)
                .append("s Pokedex</title></head><body style=\"background-color:#36393f;\">")
                .append("<center><font style=\"color:#fff\"><b><h2>").append(username).append("s Pokedex vom ")
                .append(now.format(formatter)).append(" Uhr:</h2></b></font>")
                .append("<table style=\"color:#fff\"><tr><th>IndexNr.<th>")
                .append("</th><th>Name</th><th>Level</th></tr>");

        for (Pokemon pokemonFromList : pokemonList) {
            builder.append("\n<tr><th>").append(pokemonFromList.getPokedexIndex()).append("</th><th>")
                    .append("<img src=\"").append(pokemonFromList.getPictureUrlString())
                    .append("\"  width=\"30\" height=\"30\">").append("</th><th>")
                    .append(pokemonFromList.getName()).append("</th><th>").append(pokemonFromList.getLevel())
                    .append("</th></tr>");
        }

        builder.append("</table></center></body></html>");
        final File file = new File("pokedexe", username.replace(" ", "") + ".html");
        if (file.exists()) {
            file.delete();
        }
        file.createNewFile();
        try (final BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(file))) {
            bos.write(builder.toString().getBytes());
            bos.flush();
            System.out.println("Written " + file.getAbsolutePath());
        }

        return "http://nussbot.kkuez.de/" + file.getName();
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
        final List<Pokemon> pokemonOfUser = getDbService().getPokemonOfUser(interactedUser.getIdLong());
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

            if (Math.random() > 0.5D) {
                int countToSteal = 5;
                builder.append("\n\n*...Und was ist das?!* \n**Team Rocket** ist erschienen und hat dir **")
                        .append(countToSteal).append("** zufällige Pokemon stibitzt D:");
                Collections.shuffle(pokemonOfUser);
                final List<Pokemon> pokemonsToRemove = pokemonOfUser.subList(0, countToSteal);
                getDbService().removePokemonFromUser(pokemonsToRemove);
                pokemonsToRemove.forEach(pokemonToRemove -> {
                    builder.append("\n**").append(pokemonToRemove.getName())
                            .append("**, Level:*").append(pokemonToRemove.getLevel()).append("*");
                });
                builder.append("\nhttps://www.pokewiki.de/images/7/77/Team_Rocket_Anime.jpg");
            }
            buttonClickEvent.getChannel().sendMessage(builder.build()).queue();
            return;
        }

        getDbService().registerPokemon(user, pokemon);
        final Message message = new MessageBuilder()
                .append(buttonClickEvent.getMessage().getContentRaw())
                .append("\n*")
                .append(userName)
                .append("* hats gefangen!")
                .build();
        buttonClickEvent.getMessage().editMessage(message).queue();

    }

    @Override
    public boolean waitingForAnswer() {
        return false;
    }

    @Override
    public void handlePM(User user, String toLowerCase, JDA bot, PrivateChannel channel, Optional<Event> eventOpt) {
        handle(user, toLowerCase.split(" "), channel, eventOpt);
    }

    private class Swap {
        private User from;
        private User to;
        private final List<Pokemon> fromUserSwapPokemons = new ArrayList<>();
        private final List<Pokemon> toUserSwapPokemons = new ArrayList<>();
        private SwapStatus status = SwapStatus.AWAITING_USER_TWO_SWAP_GRANT;
        private UUID uuid = UUID.randomUUID();
        private boolean fromAcceptedToSwap = false;
        private boolean toAcceptedToSwap = false;

        public Swap(User from, User to) {
            this.from = from;
            this.to = to;
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

        public UUID getUuid() {
            return uuid;
        }

        boolean containsUsers(User... users) {
            boolean fromUserContained = false;
            boolean toUserContained = false;
            for (User user : users) {
                if (!fromUserContained && from.equals(user)) {
                    fromUserContained = true;
                }

                if (!toUserContained && to.equals(user)) {
                    toUserContained = true;
                }

            }
            return fromUserContained || toUserContained;
        }

        public void processSwapFurther(Optional<Event> event, String[] messages, User user) {
            switch (status) {
                case AWAITING_USER_TWO_SWAP_GRANT:
                    final boolean yes = messages[3].equals("Ja");
                    if (!yes) {
                        MessageBuilder fromBuilderToAccept = new MessageBuilder(this.to.getName());
                        fromBuilderToAccept.append(SWAP_DECLINED_STRING);
                        this.from.openPrivateChannel().queue((privateChannel) ->
                                privateChannel.sendMessage(fromBuilderToAccept.build()).queue());

                        final ButtonClickEvent buttonClickEvent = (ButtonClickEvent) event.get();
                        MessageBuilder messageBuilder = new MessageBuilder("Arbeitet...");
                        messageBuilder.setActionRows();
                        buttonClickEvent.getMessage().editMessage(messageBuilder.build()).queue();

                        MessageBuilder toBuilderToAccept = new MessageBuilder(this.from.getName());
                        toBuilderToAccept.append(SWAP_DECLINED_STRING);
                        this.to.openPrivateChannel().queue((privateChannel) ->
                                privateChannel.sendMessage(toBuilderToAccept.build()).queue());

                        SWAP_PAIRS.remove(this);
                        return;
                    }

                    final Map<String, Pokemon> fromCodeMap = getCodeMap(getDbService().getPokemonOfUser(from));
                    MessageBuilder fromBuilder = new MessageBuilder("Welches Pokemon willst du " +
                            "tauschen?\n" + WRITE_ME_THE_CODE_WITH + " !p swap <CODE> (wenn du mehrere Pokemons tauschen " +
                            "willst, dann trenne die Codes mit einem Komma, z. B. \"!p swap ac,cx,de\")!");

                    List<String> fromCodeMapKeys = fromCodeMap.keySet().stream().collect(Collectors.toList());
                    Collections.sort(fromCodeMapKeys);

                    int index = 0;
                    for (String codeMapKey : fromCodeMapKeys) {
                        final Pokemon pokemon = fromCodeMap.get(codeMapKey);
                        fromBuilder.append("\n" + codeMapKey + " \t| " + pokemon.getName() + " Lvl. " + pokemon.getLevel());
                        if (index != 0 && (index % 50 == 0 || fromCodeMapKeys.size() == index + 1)) {
                            from.openPrivateChannel().queue((channel1) -> channel1.sendMessage(fromBuilder.build()).queue());
                            fromBuilder.setContent("");
                        }
                        index++;
                    }

                    final Map<String, Pokemon> toCodeMap = getCodeMap(getDbService().getPokemonOfUser(to));
                    final MessageBuilder toBuilder = new MessageBuilder("Welches Pokemon willst du " +
                            "tauschen?\n" + WRITE_ME_THE_CODE_WITH + " !p swap <CODE> (wenn du mehrere Pokemons freilassen " +
                            "willst, dann trenne die Codes mit einem Komma, z. B. \"!p swap ac,cx,de\")!\n" +
                            ":octagonal_sign: Wenn du mehrere Pokemon tauschen möchtest, solltest du die Möglichkeit" +
                            " mit der Trennung durch ein Komma nutzen!:octagonal_sign: ");

                    List<String> toCodeMapKeys = toCodeMap.keySet().stream().collect(Collectors.toList());
                    Collections.sort(toCodeMapKeys);

                    index = 0;
                    for (String codeMapKey : toCodeMapKeys) {
                        final Pokemon pokemon = toCodeMap.get(codeMapKey);
                        toBuilder.append("\n" + codeMapKey + " \t| " + pokemon.getName() + " Lvl. " + pokemon.getLevel());
                        if (index != 0 && (index % 50 == 0 || toCodeMapKeys.size() == index + 1)) {
                            to.openPrivateChannel().queue((channel1) -> channel1.sendMessage(toBuilder.build()).queue());
                            toBuilder.setContent("");
                        }
                        index++;
                    }
                    status = SwapStatus.FIRST_USER_OFFER;
                    return;
                case FIRST_USER_OFFER: {
                    try {
                        UUID.fromString(messages[2]);
                        return;
                    } catch (Exception e) {
                        //do nothing cuz planned
                    }

                    final Map<String, Pokemon> codeMap = getCodeMap(getDbService().getPokemonOfUser(user));
                    List<Pokemon> pokemonList;
                    if (this.from.equals(user)) {
                        pokemonList = this.fromUserSwapPokemons;
                    } else if (this.to.equals(user)) {
                        pokemonList = this.toUserSwapPokemons;
                    } else {
                        //TODO aufräumen bei solch einem Fall
                        throw new RuntimeException("User konnte nicht in SwapPair gefunden werden!");
                    }

                    for (String code : messages[2].split(",")) {
                        pokemonList.add(codeMap.get(code));
                    }
                    user.openPrivateChannel().queue((privateChannel) ->
                            privateChannel.sendMessage("Warte auf anderen Spieler....").queue());
                    status = SwapStatus.SECOND_USER_OFFER;
                }
                return;
                case SECOND_USER_OFFER: {
                    final Map<String, Pokemon> codeMap = getCodeMap(getDbService().getPokemonOfUser(user));
                    List<Pokemon> pokemonList;
                    if (this.from.equals(user)) {
                        pokemonList = this.fromUserSwapPokemons;
                    } else if (this.to.equals(user)) {
                        pokemonList = this.toUserSwapPokemons;
                    } else {
                        //TODO aufräumen bei solch einem Fall
                        throw new RuntimeException("User konnte nicht in SwapPair gefunden werden!");
                    }

                    for (String code : messages[2].split(",")) {
                        pokemonList.add(codeMap.get(code));
                    }

                    MessageBuilder fromBuilderToAccept = new MessageBuilder(this.to.getName());
                    fromBuilderToAccept.append(" bietet dir folgende Pokemon an:");
                    for (Pokemon toUserSwapPokemon : toUserSwapPokemons) {
                        fromBuilderToAccept.append("\n").append(toUserSwapPokemon.getName()).append(" Lvl. ")
                                .append(toUserSwapPokemon.getLevel());
                    }
                    fromBuilderToAccept.setActionRows(ActionRow.of(Button.primary("poke swap yes", "Annehmen"),
                            Button.primary("poke swap no", "Ausschlagen")));
                    this.from.openPrivateChannel().queue((privateChannel) ->
                            privateChannel.sendMessage(fromBuilderToAccept.build()).queue());

                    MessageBuilder toBuilderToAccept = new MessageBuilder(this.from.getName());
                    toBuilderToAccept.append(" bietet dir folgende Pokemon an:");
                    for (Pokemon toUserSwapPokemon : fromUserSwapPokemons) {
                        toBuilderToAccept.append("\n").append(toUserSwapPokemon.getName()).append(" Lvl. ")
                                .append(toUserSwapPokemon.getLevel());
                    }
                    toBuilderToAccept.setActionRows(ActionRow.of(Button.primary("poke swap yes", "Annehmen"),
                            Button.primary("poke swap no", "Ausschlagen")));
                    this.to.openPrivateChannel().queue((privateChannel) ->
                            privateChannel.sendMessage(toBuilderToAccept.build()).queue());

                    status = SwapStatus.WAITING_FOR_POKEMON_ACCEPT;
                }
                return;
                case WAITING_FOR_POKEMON_ACCEPT: {
                    if (user.equals(from) && messages[2].equals("yes")) {
                        fromAcceptedToSwap = true;
                    } else if (user.equals(to) && messages[2].equals("yes")) {
                        toAcceptedToSwap = true;
                    }

                    final ButtonClickEvent buttonClickEvent = (ButtonClickEvent) event.get();
                    MessageBuilder messageBuilder = new MessageBuilder("Warte auf Spieler...");
                    messageBuilder.setActionRows();
                    buttonClickEvent.getMessage().editMessage(messageBuilder.build()).queue();
                    status = SwapStatus.BOTH_ACCEPTED_POKEMON_SELECT;
                }
                return;
                case BOTH_ACCEPTED_POKEMON_SELECT:
                    status = SwapStatus.DECLINED;
                    if (user.equals(from) && messages[2].equals("yes") && !fromAcceptedToSwap) {
                        fromAcceptedToSwap = true;
                    } else if (user.equals(to) && messages[2].equals("yes") && !toAcceptedToSwap) {
                        toAcceptedToSwap = true;
                    } else if (messages[2].equals("no")) {
                        final ButtonClickEvent buttonClickEvent = (ButtonClickEvent) event.get();
                        MessageBuilder messageBuilder = new MessageBuilder(user.getName());
                        messageBuilder.append(SWAP_DECLINED_STRING);
                        messageBuilder.setActionRows();
                        buttonClickEvent.getMessage().editMessage(messageBuilder.build()).queue();


                        if (!user.equals(from)) {
                            MessageBuilder toBuilderToNo = new MessageBuilder(this.to.getName());
                            toBuilderToNo.append(SWAP_DECLINED_STRING);
                            this.from.openPrivateChannel().queue((privateChannel) ->
                                    privateChannel.sendMessage(toBuilderToNo.build()).queue());
                        } else {
                            MessageBuilder toBuilderToNo = new MessageBuilder(this.from.getName());
                            toBuilderToNo.append(SWAP_DECLINED_STRING);
                            to.openPrivateChannel().queue((privateChannel) ->
                                    privateChannel.sendMessage(toBuilderToNo.build()).queue());
                        }
                        return;
                    } else {
                        return;
                    }

                    for (Pokemon fromUserSwapPokemon : fromUserSwapPokemons) {
                        getDbService().registerPokemon(to, fromUserSwapPokemon);
                    }
                    getDbService().removePokemonFromUser(fromUserSwapPokemons);

                    for (Pokemon toUserSwapPokemon : toUserSwapPokemons) {
                        getDbService().registerPokemon(from, toUserSwapPokemon);
                    }
                    getDbService().removePokemonFromUser(toUserSwapPokemons);

                    MessageBuilder fromBuilderToAccept = new MessageBuilder(this.to.getName());
                    fromBuilderToAccept.append(" hat angenommen, Tausch beendet :)");
                    this.from.openPrivateChannel().queue((privateChannel) ->
                    {
                        privateChannel.sendMessage(fromBuilderToAccept.build()).queue();
                        removeMessagesFromChannelIfWithCode(privateChannel);
                    });

                    final ButtonClickEvent buttonClickEvent = (ButtonClickEvent) event.get();
                    MessageBuilder messageBuilder = new MessageBuilder("Arbeitet...");
                    messageBuilder.setActionRows();
                    buttonClickEvent.getMessage().editMessage(messageBuilder.build()).queue();


                    MessageBuilder toBuilderToAccept = new MessageBuilder(this.from.getName());
                    toBuilderToAccept.append(" hat angenommen, Tausch beendet :)");
                    this.to.openPrivateChannel().queue((privateChannel) -> {
                        privateChannel.sendMessage(toBuilderToAccept.build()).queue();
                        removeMessagesFromChannelIfWithCode(privateChannel);
                    });
                    SWAP_PAIRS.remove(this);
                    break;
            }
        }
    }

    private enum SwapStatus {
        AWAITING_USER_TWO_SWAP_GRANT, FIRST_USER_OFFER, SECOND_USER_OFFER,
        BOTH_ACCEPTED_POKEMON_SELECT, FIRST_USER_ACCEPTED_EXCHANGE, DECLINED, WAITING_FOR_POKEMON_ACCEPT
    }
}
