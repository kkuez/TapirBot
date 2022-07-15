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
import net.dv8tion.jda.api.requests.restaction.MessageAction;
import tapir.DBService;
import tapir.ReceiveModule;

import java.io.*;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class PokeModule extends ReceiveModule {

    private static final int POKEMON_TOP_INDEX = 151;
    private static Pokemon currentPokemon;
    private static final int MAXCOUNT = 3;
    private static final Pattern CODE_PATTERN = Pattern.compile("[a-z][a-z] \\t\\| [A-Z][a-z]* Lvl\\..*");
    static final String SWAP_DECLINED_STRING = " hat abgelehnt, Tausch beendet :(";
    static final Set<Swap> SWAP_PAIRS = new HashSet<>(2);
    private final Integer pokemonMaxFreq;
    private final Map<User, LocalDateTime> userFrees = new HashMap<>();

    public PokeModule(DBService dbService, Set<TextChannel> allowedChannels, Integer pokemonMaxFreq,
                      Set<Long> userNotAllowedToAsk) {
        super(dbService, allowedChannels, userNotAllowedToAsk);
        this.pokemonMaxFreq = pokemonMaxFreq;
        startCatchLoop();
    }

    private void startCatchLoop() {

        final Runnable runnable = () -> {
            long oneHourAsMilliSecs = 3600000;
            boolean isDebug = pokemonMaxFreq < 100;
            while (true) {
                //long timeToWait = 10000;
                long timeToWait = 1000;

                while (!isDebug && timeToWait < 300000) {
                    final double random = Math.random();
                    timeToWait = Math.round(random * pokemonMaxFreq * 1000);
                }
                if (isDebug) {
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
        };
        getExecutorService().submit(runnable);
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

    private List<Pokemon> getPokemon(int count) throws IOException {
        List<Pokemon> pokemons = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            pokemons.add(getPokemon());
        }
        return pokemons;
    }

    private Pokemon getPokemon() throws IOException {
        long index = 0;
        while (index == 0 || index == POKEMON_TOP_INDEX + 1) {
            double random = Math.random();
            index = Math.round(152 * random);
        }

        String germanName = PokeAPIProvider.getNameForIndexAndLanguage(index, Optional.empty(), "de");

        final double levelDouble = Math.random() * 100;
        final long level = Math.round(levelDouble);

        return new Pokemon(null, (int) index, germanName, (int) level);
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
                "free",
                "orden",
                "fight",
                "casino"
        );
    }

    @Override
    public void handle(User user, String[] messages, MessageChannel channel, Optional<Event> event) {

        switch (messages[1].toLowerCase()) {
            case "catch":
                if (userFrees.containsKey(user) && LocalDateTime.now().isBefore(userFrees.get(user))) {
                    final LocalDateTime allowedTime = userFrees.get(user).withNano(0);
                    channel.sendMessage(user.getName() + ", du bist noch auf dem Weg zurück zum Pokécenter (!p free) " +
                            "und kannst deshalb noch keine weiteren Pokemon fangen!\n(Du kannst wieder fangen am "
                            + allowedTime.getDayOfMonth() + "."
                            + allowedTime.getMonthValue() + "."
                            + allowedTime.getYear() + " um "
                            + allowedTime.getHour() + ":"
                            + allowedTime.getMinute() + " Uhr)").queue();
                    return;
                }
                processCatch(user, event);
                checkForOrden(user);
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
                    String listMessage = "Welches Pokemon willst du " +
                            "freilassen?\nSchreibe mir die Codes mit !p free <CODE> (wenn du mehrere Pokemons freilassen " +
                            "willst, dann trenne die Codes mit einem Komma, z. B. \"!p free ac,cx,de\")!" +
                            "\n:octagonal_sign: __Es empfiehlt sich, mehrere Pokémon auf einmal freizulassen. Solltest du sie" +
                            " einzeln freilassen wollen, mache nach jedem Pokémon ein !p free um eine aktualisierte Codeliste" +
                            " zu bekommen__ :octagonal_sign: ";
                    postGeneralPokeListMessage(user, listMessage);
                } else {
                    final Map<String, Pokemon> codeMap = getCodeMap(getDbService().getPokemonOfUser(user));
                    final List<Pokemon> pokemonToDelete = Arrays.stream(messages[2].split(","))
                            .map(codeMap::get).collect(Collectors.toList());
                    StringBuilder pokemonBuilder = new StringBuilder();
                    getDbService().removePokemonFromUser(pokemonToDelete);

                    for (Pokemon pokemon : pokemonToDelete) {
                        pokemonBuilder.append("\nFreigelassen: ")
                                .append(pokemon.getName()).append(" Lvl. ").append(pokemon.getLevel());
                    }
                    user.openPrivateChannel().queue((privateChannel) ->
                            privateChannel.sendMessage(pokemonBuilder).queue());
                    removeMessagesFromChannelIfWithCode(((PrivateMessageReceivedEvent) event.get()).getChannel());
                    userFrees.put(user, LocalDateTime.now().plusHours(5));
                }
                break;
            case "orden":
                if (messages.length == 2) {
                    checkForOrden(user);
                    return;
                }

                final ButtonClickEvent buttonClickEvent = (ButtonClickEvent) event.get();
                if (messages.length != 4) {
                    return;
                } else if (messages[3].equals("yes")) {
                    getDbService().registerNewOrden(user);
                    MessageBuilder messageBuilder = new MessageBuilder("Angenommen, herzlichen Glückwunsch zu deinem neuen Orden :)");
                    messageBuilder.setActionRows();
                    buttonClickEvent.editMessage(messageBuilder.build()).queue();
                    getGeneralChannels().forEach(channel1 -> channel1.sendMessage(":medal:" + user.getName()
                            + " hat einen nigelnagelneuen Orden!!!:medal:").queue());
                } else if (messages[3].equals("no")) {
                    MessageBuilder messageBuilder = new MessageBuilder("Nicht angenommen, du behälst deine Pokemon");
                    messageBuilder.append(" und bekommst keinen Orden.");
                    messageBuilder.setActionRows();
                    buttonClickEvent.editMessage(messageBuilder.build()).queue();
                }
                break;
            case "casino":
                processCasino(user, messages, event);
                break;
            case "fight":
                processFight(event, messages, user);
                break;
        }
    }

    private void processFight(Optional<Event> event, String[] messages, User user) {
        try {
            PokeAPIProvider.getAttacksForPokemon(getDbService().getPokemonOfUser(user).get(0));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void processCasino(User user, String[] messages, Optional<Event> event) {
        List<Pokemon> superfluousPokemon = getSuperfluousPokemonOfUser(user);
        final int superfluousCount = superfluousPokemon.size();

        if(superfluousCount < MAXCOUNT) {
            String listMessage = "Du hast zu wenig überflüssige Pokémons fürs Casino!" +
                    "\n(Du hast nur " + superfluousPokemon + " Stück!)";
            MessageBuilder messageBuilder = new MessageBuilder(listMessage);
            user.openPrivateChannel().queue((channel1) -> channel1.sendMessage(messageBuilder.build()).queue());
        }

        if (messages.length == 2) {
            String listMessage = "Mit wievielen überflüssigen Pokémons willst du ins Casino (Du hast " +
                    superfluousCount + " zuviel)?" +
                    "\n*3* = Du gewinnst **ein** neues\n*4* = Du gewinnst **zwei** neue\n*5* = Du gewinnst **drei** neue";
            MessageBuilder messageBuilder = new MessageBuilder(listMessage);
            messageBuilder.setActionRows(ActionRow.of(Button.primary("!p casino 3", "3"),
                    Button.primary("!p casino 4", "4"), Button.primary("!p casino 5", "5"),
                    Button.primary("!p casino no", "Lieber doch nicht")));
            user.openPrivateChannel().queue((channel1) -> channel1.sendMessage(messageBuilder.build()).queue());
        } else {
            if (messages[2].equals("no")) {
                final String message = "Ok, dann halt nicht!";
                removeButtonMessage(event, message);
            } else {
                final int buttonCount = Integer.parseInt(messages[2]);

                if (superfluousCount < buttonCount) {
                    String countOutMessage = superfluousCount == 0 ? "keine überflüssigen"
                            : "nur " + superfluousCount + " überflüssige ";
                    StringBuilder pokemonBuilder = new StringBuilder("Du hast " + countOutMessage + " Pokémon!");
                    removeButtonMessage(event, pokemonBuilder.toString());
                    return;
                }

                Collections.shuffle(superfluousPokemon);
                List<Pokemon> gambledPokemon = null;
                List<Pokemon> pokemonsToRemove = null;
                try {
                    gambledPokemon = getPokemon(buttonCount);
                    pokemonsToRemove = superfluousPokemon.subList(0, buttonCount);
                    getDbService().registerPokemon(user, gambledPokemon);
                    getDbService().removePokemonFromUser(pokemonsToRemove);
                } catch (IOException e) {
                    e.printStackTrace();
                }


                StringBuilder pokemonBuilder = new StringBuilder("Herzlichen Glückwunsch! Du hast");
                gambledPokemon.forEach(pokemon -> {
                    pokemonBuilder.append("\n**").append(pokemon.getName()).append("** Lvl ").append(pokemon.getLevel());
                });
                pokemonBuilder.append(" gewonnen! :)\n\nVerloren hast du dafür \n");

                pokemonsToRemove.forEach(pokemon -> pokemonBuilder.append(pokemon.getName())
                        .append(", Lvl ").append(pokemon.getLevel()).append("\n"));

                final ButtonClickEvent buttonToGambleEvent = (ButtonClickEvent) event.get();
                MessageBuilder messageBuilder = new MessageBuilder(pokemonBuilder.toString());
                messageBuilder.setActionRows();
                buttonToGambleEvent.editMessage(messageBuilder.build()).queue();

                List<Pokemon> finalGambledPokemon = gambledPokemon;
                StringBuilder generalMessageStringBuilder = new StringBuilder("*" + user.getName() + "*");
                generalMessageStringBuilder.append(" hat ");
                finalGambledPokemon.forEach(pokemon -> generalMessageStringBuilder.append("\n**" + pokemon.getName())
                        .append("** Lvl ").append(pokemon.getLevel()));
                generalMessageStringBuilder.append("\n gewonnen!");
                getGeneralChannels().forEach(channel1 -> channel1.sendMessage(generalMessageStringBuilder.toString())
                        .queue());
            }
        }
    }

    private void removeButtonMessage(Optional<Event> event, String message) {
        final ButtonClickEvent buttonToGambleEvent = (ButtonClickEvent) event.get();
        MessageBuilder messageBuilder = new MessageBuilder(message);
        messageBuilder.setActionRows();
        buttonToGambleEvent.editMessage(messageBuilder.build()).queue();
    }

    private List<Pokemon> getSuperfluousPokemonOfUser(User user) {
        List<Pokemon> superfluous = new ArrayList<>();
        final List<Pokemon> pokemonOfUser = getDbService().getPokemonOfUser(user);
        final Set<String> pokemonBaseSet = new HashSet<>();
        for (Pokemon pokemon : pokemonOfUser) {
            if (pokemonBaseSet.contains(pokemon.getName())) {
                superfluous.add(pokemon);
            } else {
                pokemonBaseSet.add(pokemon.getName());
            }
        }
        return superfluous;
    }

    private void checkForOrden(User user) {
        int countUserPokemons = getDbService().getPokemonOfUser(user).stream().map(Pokemon::getName)
                .collect(Collectors.toSet()).size();
        if (countUserPokemons >= POKEMON_TOP_INDEX) {
            user.openPrivateChannel().queue(privateChannel -> {
                MessageBuilder messageBuilder = new MessageBuilder("Hey, du hast ALLE Pokémon gefangen, unfassbar, ");
                messageBuilder.append("Herzlichen Glückwunsch!\nDie Pokeliga-Leitung bietet dir im Tausch gegen ")
                        .append("deine Pokémon einen Orden an, nimmst du an? ;)\n")
                        .append("(Tauscht alle deine Pokémon gegen einen Orden und du kannst von ")
                        .append("vorne anfangen, ein weiterer Schritt zum Sieg!)");
                messageBuilder.setActionRows(
                        ActionRow.of(Button.primary("poke orden " + user.getIdLong() + " yes", "Ja"),
                                Button.primary("poke orden " + user.getIdLong() + " no", "Nein")));

                privateChannel.sendMessage(messageBuilder.build()).queue();
            });
        }
    }

    void removeMessagesFromChannelIfWithCode(PrivateChannel channel) {
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

            final Swap swapPair = new Swap(fromUser, toUser, getDbService());
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
            swapPairOpt.get().processSwapFurther(event, messages, user, this);
        }
    }

    private void postGeneralPokeListMessage(User user, String listMesssage) {
        final List<Pokemon> pokemonOfUser = getDbService().getPokemonOfUser(user);

        StringBuilder builder = new StringBuilder(listMesssage);
        Map<String, Pokemon> codeMap = getCodeMap(pokemonOfUser);
        List<String> codeMapKeys = new ArrayList<>(codeMap.keySet());
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

    Map<String, Pokemon> getCodeMap(List<Pokemon> pokemonOfUser) {
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
        final List<Pokemon> pokemonList = new ArrayList<>();
        StringBuilder builder = new StringBuilder("__*");
        String pokedexURL = "";
        String ordenString = "";
        try {
            if (messages.length > 2 && messages[2].contains("<@") && messages[2].contains(">")) {
                final long id = getUserIdFromMention(messages[2]);

                pokemonList.addAll(getDbService().getPokemonOfUser(id));
                Map<String, String> mentionedUser = getDbService().getUserInfoById(id);
                builder.append(mentionedUser.get("name")).append("* hat");
                pokedexURL = createPokedexPage(pokemonList, mentionedUser.get("name"));
                ordenString = getOrdenString(id);
            } else {
                pokemonList.addAll(getDbService().getPokemonOfUser(user));
                builder.append(user.getName()).append("*, du hast");
                pokedexURL = createPokedexPage(pokemonList, user.getName());
                ordenString = getOrdenString(user.getIdLong());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        final int size = pokemonList.stream().map(Pokemon::getName).collect(Collectors.toSet()).size();
        if (size == 0) {
            builder.append(" noch keine");
        } else if (size < 50) {
            builder.append(" erst **").append(size).append("** unterschiedliche");
        } else {
            builder.append(" schon **").append(size).append("** unterschiedliche");
        }

        builder.append(" Pokémon gefangen und ").append(ordenString).append(" Orden:__\n").append(pokedexURL);
        guildMessageReceivedEvent.getMessage().reply(builder.toString()).queue();
    }

    private String getOrdenString(Long userId) {
        int medalCount = getDbService().getOrdenCount(userId);
        String ordenString = "";
        if (medalCount > 0) {
            for (int i = 0; i < medalCount; i++) {
                ordenString += ":medal:";
            }
        } else {
            ordenString = "noch keine";
        }
        return ordenString;
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
        final String userIdString = message.replace("<", "").replace("@", "").replace("!", "").replace(">", "");
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

}
