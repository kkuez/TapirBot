package tapir.pokemon;

import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.Event;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.Button;
import tapir.DBService;

import java.util.*;

import static tapir.pokemon.PokeModule.SWAP_DECLINED_STRING;

public class Swap {
    private final User from;
    private final User to;
    private final DBService dbService;
    private final List<Pokemon> fromUserSwapPokemons = new ArrayList<>();
    private final List<Pokemon> toUserSwapPokemons = new ArrayList<>();
    private SwapStatus status = SwapStatus.AWAITING_USER_TWO_SWAP_GRANT;
    private final UUID uuid = UUID.randomUUID();
    private boolean fromAcceptedToSwap = false;
    private boolean toAcceptedToSwap = false;

    public Swap(User from, User to, DBService dbService) {
        this.from = from;
        this.to = to;
        this.dbService = dbService;
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

    public void processSwapFurther(Optional<Event> event, String[] messages, User user, PokeModule pokeModule) {
        switch (status) {
            case AWAITING_USER_TWO_SWAP_GRANT:
                final boolean yes = messages[3].equals("Ja");
                final ButtonClickEvent buttonClickEvent = (ButtonClickEvent) event.get();
                if (!yes) {
                    MessageBuilder fromBuilderToAccept = new MessageBuilder(this.to.getName());
                    fromBuilderToAccept.append(SWAP_DECLINED_STRING);
                    this.from.openPrivateChannel().queue((privateChannel) ->
                            privateChannel.sendMessage(fromBuilderToAccept.build()).queue());

                    MessageBuilder messageBuilder = new MessageBuilder("Arbeitet...");
                    messageBuilder.setActionRows();
                    buttonClickEvent.getMessage().editMessage(messageBuilder.build()).queue();

                    MessageBuilder toBuilderToAccept = new MessageBuilder(this.from.getName());
                    toBuilderToAccept.append(SWAP_DECLINED_STRING);
                    this.to.openPrivateChannel().queue((privateChannel) ->
                            privateChannel.sendMessage(toBuilderToAccept.build()).queue());

                    PokeModule.SWAP_PAIRS.remove(this);
                    return;
                }

                final Map<String, Pokemon> fromCodeMap = pokeModule.getCodeMap(dbService.getPokemonOfUser(from));
                MessageBuilder fromBuilder = new MessageBuilder("Welches Pokemon willst du " +
                        "tauschen?\nSchreibe mir die Codes mit !p swap <CODE> (wenn du mehrere Pokemons tauschen " +
                        "willst, dann trenne die Codes mit einem Komma, z. B. \"!p swap ac,cx,de\")!");

                List<String> fromCodeMapKeys = new ArrayList<>(fromCodeMap.keySet());
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

                MessageBuilder messageBuilderDeleteButtons = new MessageBuilder("...");
                buttonClickEvent.getMessage().editMessage(messageBuilderDeleteButtons.build()).queue();

                final Map<String, Pokemon> toCodeMap = pokeModule.getCodeMap(dbService.getPokemonOfUser(to));
                final MessageBuilder toBuilder = new MessageBuilder("Welches Pokemon willst du " +
                        "tauschen?\nSchreibe mir die Codes mit !p swap <CODE> (wenn du mehrere Pokemons freilassen " +
                        "willst, dann trenne die Codes mit einem Komma, z. B. \"!p swap ac,cx,de\")!\n" +
                        ":octagonal_sign: Wenn du mehrere Pokemon tauschen möchtest, solltest du die Möglichkeit" +
                        " mit der Trennung durch ein Komma nutzen!:octagonal_sign: ");

                List<String> toCodeMapKeys = new ArrayList<>(toCodeMap.keySet());
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

                final String swapString = messages[2].replace(" ", "").replace(".", ",").toLowerCase(Locale.ROOT);
                boolean validSwapString = checkSwapStringValidityAndAnswerIfNeeded(event.get(), user, swapString);
                if(!validSwapString) {
                    return;
                }

                final Map<String, Pokemon> codeMap = pokeModule.getCodeMap(dbService.getPokemonOfUser(user));
                List<Pokemon> pokemonList;
                if (this.from.equals(user)) {
                    pokemonList = this.fromUserSwapPokemons;
                } else if (this.to.equals(user)) {
                    pokemonList = this.toUserSwapPokemons;
                } else {
                    //TODO aufräumen bei solch einem Fall
                    throw new RuntimeException("User konnte nicht in SwapPair gefunden werden!");
                }

                for (String code : swapString.split(",")) {
                    pokemonList.add(codeMap.get(code));
                }
                user.openPrivateChannel().queue((privateChannel) ->
                        privateChannel.sendMessage("Warte auf anderen Spieler....").queue());
                status = SwapStatus.SECOND_USER_OFFER;
            }
            return;
            case SECOND_USER_OFFER: {
                final String swapString = messages[2].replace(" ", "").replace(".", ",").toLowerCase(Locale.ROOT);
                boolean validSwapString = checkSwapStringValidityAndAnswerIfNeeded(event.get(), user, swapString);
                if(!validSwapString) {
                    return;
                }

                final Map<String, Pokemon> codeMap = pokeModule.getCodeMap(dbService.getPokemonOfUser(user));
                List<Pokemon> pokemonList;
                if (this.from.equals(user)) {
                    pokemonList = this.fromUserSwapPokemons;
                } else if (this.to.equals(user)) {
                    pokemonList = this.toUserSwapPokemons;
                } else {
                    //TODO aufräumen bei solch einem Fall
                    throw new RuntimeException("User konnte nicht in SwapPair gefunden werden!");
                }

                for (String code : swapString.split(",")) {
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

                final ButtonClickEvent buttonClickEvent_waitingForPokemonAccept = (ButtonClickEvent) event.get();
                MessageBuilder messageBuilder = new MessageBuilder("Warte auf Spieler...");
                messageBuilder.setActionRows();
                buttonClickEvent_waitingForPokemonAccept.getMessage().editMessage(messageBuilder.build()).queue();
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
                    final ButtonClickEvent buttonClickEventBothAccepted = (ButtonClickEvent) event.get();
                    MessageBuilder messageBuilder = new MessageBuilder(user.getName());
                    messageBuilder.append(SWAP_DECLINED_STRING);
                    messageBuilder.setActionRows();
                    buttonClickEventBothAccepted.getMessage().editMessage(messageBuilder.build()).queue();


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

                dbService.registerPokemon(to, fromUserSwapPokemons);
                dbService.removePokemonFromUser(fromUserSwapPokemons);

                dbService.registerPokemon(from, toUserSwapPokemons);
                dbService.removePokemonFromUser(toUserSwapPokemons);

                MessageBuilder fromBuilderToAccept = new MessageBuilder(this.to.getName());
                fromBuilderToAccept.append(" hat angenommen, Tausch beendet :)");
                this.from.openPrivateChannel().queue((privateChannel) ->
                {
                    privateChannel.sendMessage(fromBuilderToAccept.build()).queue();
                    pokeModule.removeMessagesFromChannelIfWithCode(privateChannel);
                });

                final ButtonClickEvent buttonClickEventBothAccepted = (ButtonClickEvent) event.get();
                MessageBuilder messageBuilder = new MessageBuilder("Arbeitet...");
                messageBuilder.setActionRows();
                buttonClickEventBothAccepted.getMessage().editMessage(messageBuilder.build()).queue();


                MessageBuilder toBuilderToAccept = new MessageBuilder(this.from.getName());
                toBuilderToAccept.append(" hat angenommen, Tausch beendet :)");
                this.to.openPrivateChannel().queue((privateChannel) -> {
                    privateChannel.sendMessage(toBuilderToAccept.build()).queue();
                    pokeModule.removeMessagesFromChannelIfWithCode(privateChannel);
                });
                PokeModule.SWAP_PAIRS.remove(this);
                break;
        }
    }

    private boolean checkSwapStringValidityAndAnswerIfNeeded(Event event, User user, String swapString) {
        for (String code : swapString.split(",")) {
            if(code.length() != 2) {
                user.openPrivateChannel().queue((privateChannel) ->
                        privateChannel.sendMessage("Ich kann den Code \"" + code + "\" nicht lesen." +
                                " \nVersuch es erneut!").queue());
                System.out.println("swapString user " + user.getName() + ": " + swapString);
                return false;
            }
        }
        return true;
    }
}
