package tapir.pokemon;

import org.jetbrains.annotations.NotNull;

public class Pokemon implements Comparable<Pokemon>{
    private int rowid;
    private int pokedexIndex;
    private String name;
    private int level;
    private String pictureUrlString;

    public Pokemon(Integer rowid, int pokedexIndex, String name, int level) {
        this.rowid = rowid;
        this.pokedexIndex = pokedexIndex;
        this.name = name;
        this.level = level;
        this.pictureUrlString = "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/"
                + pokedexIndex + ".png";
    }

    public int getPokedexIndex() {
        return pokedexIndex;
    }

    public String getName() {
        return name;
    }

    public int getLevel() {
        return level;
    }

    public String getPictureUrlString() {
        return pictureUrlString;
    }

    public int getRowid() {
        return rowid;
    }

    @Override
    public int compareTo(@NotNull Pokemon o) {
        if (o.getPokedexIndex() == pokedexIndex) {
            return 0;
        } else if (o.getPokedexIndex() < pokedexIndex) {
            return 1;
        } else if (o.getPokedexIndex() > pokedexIndex) {
            return -1;
        }
        return 0;
    }

}
