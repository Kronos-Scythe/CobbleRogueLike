package org.CobbleUtils.cobbleroguelike;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Server config, stored as config/cobbleroguelike.json. */
public final class RogueConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static RogueConfig instance = new RogueConfig();

    public int starterLevel = 5;
    public int starterOptions = 3;
    public int freeRerolls = 1;
    public int encounterOptions = 3;
    public int encounterLevelPerFloor = 1;
    public int maxEncounterLevel = 100;
    /** Chance (0-1) that a path choice is a rest stop instead of a route. */
    public double restChance = 0.35;
    /** Ticks between scans that remove rogue Pokémon found outside an active run. */
    public int sweepIntervalTicks = 1200;

    public List<String> starterPool = new ArrayList<>(List.of(
            "bulbasaur", "charmander", "squirtle", "chikorita", "cyndaquil", "totodile",
            "treecko", "torchic", "mudkip", "turtwig", "chimchar", "piplup",
            "snivy", "tepig", "oshawott", "chespin", "fennekin", "froakie",
            "rowlet", "litten", "popplio", "grookey", "scorbunny", "sobble",
            "sprigatito", "fuecoco", "quaxly", "pikachu", "eevee"));

    public List<String> encounterPool = new ArrayList<>(List.of(
            "caterpie", "weedle", "pidgey", "rattata", "spearow", "ekans", "sandshrew", "nidoranf",
            "nidoranm", "vulpix", "zubat", "oddish", "paras", "venonat", "diglett", "meowth",
            "psyduck", "mankey", "growlithe", "poliwag", "abra", "machop", "bellsprout", "tentacool",
            "geodude", "ponyta", "slowpoke", "magnemite", "doduo", "seel", "grimer", "shellder",
            "gastly", "onix", "drowzee", "krabby", "voltorb", "exeggcute", "cubone", "koffing",
            "rhyhorn", "horsea", "goldeen", "staryu", "magikarp", "dratini", "sentret", "hoothoot",
            "ledyba", "spinarak", "mareep", "hoppip", "sunkern", "wooper", "murkrow", "misdreavus",
            "snubbull", "teddiursa", "slugma", "swinub", "houndour", "larvitar", "poochyena", "zigzagoon",
            "wurmple", "lotad", "seedot", "taillow", "wingull", "ralts", "surskit", "shroomish",
            "slakoth", "makuhita", "aron", "electrike", "carvanha", "numel", "spoink", "trapinch",
            "swablu", "barboach", "baltoy", "shuppet", "duskull", "snorunt", "spheal", "bagon",
            "beldum", "starly", "bidoof", "shinx", "budew", "cranidos", "shieldon", "buizel",
            "gible", "riolu", "hippopotas", "skorupi", "croagunk", "snover", "lillipup", "purrloin",
            "pidove", "blitzle", "roggenrola", "drilbur", "timburr", "sandile", "litwick", "axew",
            "deino", "fletchling", "litleo", "flabebe", "honedge", "goomy", "noibat", "pikipek",
            "yungoos", "rockruff", "mudbray", "jangmo-o", "rookidee", "wooloo", "rolycoly", "dreepy",
            "lechonk", "pawmi", "tinkatink", "frigibax", "gimmighoul"));

    /** Root commands a non-op player cannot run during a run. */
    public List<String> blockedCommands = new ArrayList<>(List.of("pc", "trade"));

    public static RogueConfig get() {
        return instance;
    }

    public static void load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve(Cobbleroguelike.MOD_ID + ".json");
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path)) {
                RogueConfig loaded = GSON.fromJson(reader, RogueConfig.class);
                if (loaded != null) {
                    instance = loaded;
                }
            } catch (IOException | RuntimeException e) {
                Cobbleroguelike.LOGGER.error("Failed to read {}, using defaults", path, e);
            }
        }
        try (Writer writer = Files.newBufferedWriter(path)) {
            GSON.toJson(instance, writer);
        } catch (IOException e) {
            Cobbleroguelike.LOGGER.error("Failed to write {}", path, e);
        }
    }
}
