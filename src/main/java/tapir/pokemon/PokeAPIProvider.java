package tapir.pokemon;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.*;

public class PokeAPIProvider {

    private static String POKE_API_URL_PREFIX = "https://pokeapi.co/api/v2/";
    // For german name resolution
    private static String POKEMON_SPECIES_PREFIX = POKE_API_URL_PREFIX + "pokemon-species/";
    private static String POKEMON_GENERAL_PREFIX = POKE_API_URL_PREFIX + "pokemon/";
    private static String POKEMON_MOVE_PREFIX = POKE_API_URL_PREFIX + "move/";

    static Set<PokeAttack> getAttacksForPokemon(Pokemon pokemon) throws IOException {
        Set<PokeAttack> attacks = new HashSet<>();
        JSONObject json;
        final JSONArray movesJSONArray;
        try (InputStream is = new URL(POKEMON_GENERAL_PREFIX + pokemon.getPokedexIndex()).openStream()) {
            BufferedReader rd = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")));
            String jsonText = readAll(rd);
            json = new JSONObject(jsonText);
            movesJSONArray = json.getJSONArray("moves");
        }


        for (Object o : movesJSONArray) {
            JSONObject moveJson = (JSONObject) o;
            PokeAttack pokeAttack = getPokeAttackFromJSON(moveJson);
            if(pokeAttack == null) {
                continue;
            }
            attacks.add(pokeAttack);
        }


        return attacks;
    }

    private static PokeAttack getPokeAttackFromJSON(JSONObject moveJson) throws IOException {
        moveJson = (JSONObject) moveJson.get("move");
        try (InputStream is = new URL(moveJson.getString("url")).openStream()) {
            BufferedReader rd = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")));
            String jsonText = readAll(rd);
            JSONObject jsonOfMove = new JSONObject(jsonText);

            if(!checkIfAttackIsFirstGeneration(jsonOfMove)) {
                return null;
            }

            final String germanName = getNameForIndexAndLanguage(0, Optional.of(jsonOfMove.getJSONArray("names")), "de");
            final String englishName = getNameForIndexAndLanguage(0, Optional.of(jsonOfMove.getJSONArray("names")), "en");
            System.out.println();
        }
        return null;
    }

    private static boolean checkIfAttackIsFirstGeneration(JSONObject jsonOfMove) {
        return ((JSONObject)jsonOfMove.get("generation")).get("name").equals("generation-i");
    }

    // TODO index macht nur Sinn wenn namesArrayOpotional == Empty > Umcoden
    static String getNameForIndexAndLanguage(long index, Optional<JSONArray> namesArrayOptional, String language)
            throws IOException {
        String name = null;
        JSONObject json;
        JSONArray namesArray;
        if(namesArrayOptional.isEmpty()) {
            try (InputStream is = new URL(POKEMON_SPECIES_PREFIX + index + "/").openStream()) {
                BufferedReader rd = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")));
                String jsonText = readAll(rd);
                json = new JSONObject(jsonText);
                namesArray = json.getJSONArray("names");
            }
        } else {
            namesArray = namesArrayOptional.get();
        }

        for (int i = 0; i < namesArray.length(); i++) {
            final JSONObject jsonObject = namesArray.getJSONObject(i);
            if (jsonObject.getJSONObject("language").getString("name").equals(language)) {
                name = jsonObject.getString("name");
                break;
            }
        }
        return name;
    }

    private static String readAll(Reader rd) throws IOException {
        StringBuilder sb = new StringBuilder();
        int cp;
        while ((cp = rd.read()) != -1) {
            sb.append((char) cp);
        }
        return sb.toString();
    }
}
