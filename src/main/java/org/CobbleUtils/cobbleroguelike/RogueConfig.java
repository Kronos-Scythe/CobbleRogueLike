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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Server config, stored as config/cobbleroguelike.json. */
public final class RogueConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static RogueConfig instance = new RogueConfig();

    /** Level the run copy of your chosen partner is set to. Your real Pokémon is never changed. */
    public int starterLevel = 5;
    /** If false, the run copy keeps your Pokémon's real level. */
    public boolean resetStarterLevel = true;
    public int encounterOptions = 3;

    /** Every Nth floor is a gym (or the Champion once all badges are earned). */
    public int gymEvery = 5;
    /** Badges needed before the Champion appears. */
    public int gymCount = 8;
    /** Run level cap by badge count; the last entry is the Champion's level. */
    public List<Integer> levelCaps = new ArrayList<>(List.of(15, 21, 27, 33, 40, 47, 54, 61, 70));
    /** Gym team size by badge count; the last entry is the Champion's team size. */
    public List<Integer> gymTeamSizes = new ArrayList<>(List.of(2, 3, 3, 4, 4, 5, 5, 6, 6));
    /** Heal the party after beating a gym, like a Pokémon Center visit. Off by default (hardest). */
    public boolean healAfterGym = false;

    /** Weights for the path cards on normal floors. */
    public double trainerWeight = 0.45;
    public double routeWeight = 0.35;
    public double restWeight = 0.20;

    /** Ticks between scans that remove rogue Pokémon found outside an active run. */
    public int sweepIntervalTicks = 1200;

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

    // ---------------------------------------------------------------- biomes & encounters

    /**
     * Each stretch of floors between gyms takes place in one of these. Wild Pokémon come from
     * Cobblemon's spawn data for the listed biomes/tags ({@code #namespace:tag} or a biome id), so
     * datapacks and addons that add spawns show up automatically. {@code types} themes trainers,
     * and is the fallback when the spawn data has nothing to offer.
     */
    public List<RogueBiome> biomes = new ArrayList<>(List.of(
            new RogueBiome("grasslands", "Grasslands", "minecraft:grass_block", "minecraft:moss_block", List.of("#cobblemon:is_grassland", "#cobblemon:is_plains", "#cobblemon:is_floral"), List.of("normal", "grass", "bug", "flying")),
            new RogueBiome("forest", "Forest", "minecraft:oak_sapling", "minecraft:oak_log", List.of("#cobblemon:is_forest", "#cobblemon:is_taiga"), List.of("grass", "bug", "poison", "normal")),
            new RogueBiome("jungle", "Jungle", "minecraft:jungle_sapling", "minecraft:jungle_log", List.of("#cobblemon:is_jungle", "#cobblemon:is_bamboo", "#cobblemon:is_tropical_island"), List.of("grass", "bug", "poison", "fighting")),
            new RogueBiome("desert", "Desert", "minecraft:sand", "minecraft:sand", List.of("#cobblemon:is_desert", "#cobblemon:is_arid", "#cobblemon:is_badlands"), List.of("ground", "rock", "fire")),
            new RogueBiome("savanna", "Savanna", "minecraft:acacia_sapling", "minecraft:acacia_log", List.of("#cobblemon:is_savanna", "#cobblemon:is_shrubland"), List.of("normal", "ground", "fire", "electric")),
            new RogueBiome("mountains", "Mountains", "minecraft:stone", "minecraft:stone", List.of("#cobblemon:is_mountain", "#cobblemon:is_peak", "#cobblemon:is_highlands", "#cobblemon:is_hills"), List.of("rock", "fighting", "steel", "flying")),
            new RogueBiome("coast", "Ocean & Coast", "minecraft:tropical_fish_bucket", "minecraft:prismarine", List.of("#cobblemon:is_ocean", "#cobblemon:is_beach", "#cobblemon:is_coast", "#cobblemon:is_river", "#cobblemon:is_freshwater"), List.of("water", "flying")),
            new RogueBiome("swamp", "Swamp", "minecraft:lily_pad", "minecraft:mud", List.of("#cobblemon:is_swamp"), List.of("poison", "water", "ground", "bug")),
            new RogueBiome("tundra", "Tundra", "minecraft:snow_block", "minecraft:snow_block", List.of("#cobblemon:is_snowy", "#cobblemon:is_freezing", "#cobblemon:is_tundra", "#cobblemon:is_glacial"), List.of("ice", "water", "steel")),
            new RogueBiome("caves", "Caves", "minecraft:pointed_dripstone", "minecraft:deepslate", List.of("#cobblemon:is_cave", "#cobblemon:is_dripstone", "#cobblemon:is_lush", "#cobblemon:is_deep_dark"), List.of("rock", "ground", "dark", "ghost", "steel")),
            new RogueBiome("volcano", "Volcano", "minecraft:magma_block", "minecraft:magma_block", List.of("#cobblemon:is_volcanic", "#cobblemon:is_thermal", "#minecraft:is_nether"), List.of("fire", "rock", "ground", "dark")),
            new RogueBiome("mystic", "Mystic Grove", "minecraft:amethyst_shard", "minecraft:amethyst_block", List.of("#cobblemon:is_magical", "#cobblemon:is_mushroom", "#cobblemon:is_cherry_blossom", "#cobblemon:is_spooky"), List.of("psychic", "fairy", "ghost", "dragon"))));

    /** Relative weights of Cobblemon spawn buckets for route encounters. */
    public int commonWeight = 60;
    public int uncommonWeight = 28;
    public int rareWeight = 10;
    public int ultraRareWeight = 2;
    /** Spawn entries valid in more than this fraction of all biomes are skipped as "everywhere" spawns. */
    public double maxBiomeSpread = 0.5;
    /** Chances per route/legendary Pokémon; trainers never get these. */
    public double shinyChance = 1.0 / 256.0;
    public double hiddenAbilityChance = 0.10;
    /** Route Pokémon come evolved to the stage that fits their level (level evolutions at their level). */
    public boolean evolveEncounters = true;
    /** ...stone and trade evolutions from this level, friendship and other methods from otherEvolutionLevel. */
    public int itemEvolutionLevel = 32;
    public int otherEvolutionLevel = 30;
    /**
     * Rare encounter path card: 3 strong competitive Pokémon to pick from (no legendaries; those
     * are Legendary cards). Pools use Cobblemon species ids, optionally with a form ("ninetales alolan").
     */
    public double metaEncounterChance = 0.08;
    public int metaEncounterFromBadge = 1;
    public int metaTopFromBadge = 4;
    public double metaHiddenAbilityChance = 0.3;
    public List<String> metaPoolStrong = new ArrayList<>(List.of(
            "gyarados", "lucario", "clefable", "blissey", "hippowdon", "breloom", "conkeldurr", "gardevoir",
            "alakazam", "starmie", "magnezone", "amoonguss", "talonflame", "ninetales alolan", "slowbro",
            "mamoswine", "krookodile", "haxorus", "chandelure", "kingdra", "pelipper", "torkoal", "arcanine",
            "espathra", "garganacl", "armarouge", "tinkaton", "grimmsnarl", "hatterene", "skeledirge",
            "quaquaval", "dondozo", "heracross", "infernape", "swampert", "hawlucha", "sableye", "whimsicott",
            "porygon2", "clodsire", "glimmora", "rotom", "bisharp", "togekiss", "jolteon", "sylveon"));
    public List<String> metaPoolTop = new ArrayList<>(List.of(
            "garchomp", "dragonite", "tyranitar", "metagross", "salamence", "dragapult", "kingambit",
            "gholdengo", "volcarona", "baxcalibur", "hydreigon", "gliscor", "toxapex", "ferrothorn",
            "corviknight", "scizor", "gengar", "greninja", "rillaboom", "cinderace", "excadrill", "mimikyu",
            "weavile", "azumarill", "ceruledge", "meowscarada", "annihilape", "palafin"));

    /** Each badge makes rare route spawns more common (0.075 = rare/common odds flatten 7.5% per badge, max 60%). */
    public double encounterRarityPerBadge = 0.075;

    /** A Legendary card is guaranteed on the first floor after earning each of these badge counts. */
    public List<Integer> legendaryAfterBadges = new ArrayList<>(List.of(3, 6));
    /** Extra chance for a Legendary card on any other normal floor... */
    public double legendaryChance = 0.01;
    /** ...but only from this many badges on. */
    public int legendaryChanceFromBadge = 2;
    /** Box legendaries (Mewtwo, Rayquaza...) only appear from this many badges on. */
    public int restrictedLegendaryBadges = 6;
    /** Ultra Beasts and Paradox Pokémon count as legendary encounters. */
    public boolean includeUltraBeasts = true;
    public boolean includeParadox = true;

    public static final class RogueBiome {
        public String id;
        public String name;
        public String icon;
        public List<String> biomes;
        public List<String> types;
        /** Block shown behind this biome's screens and encounters (a default is used if unset). */
        public String block;

        public RogueBiome(String id, String name, String icon, List<String> biomes, List<String> types) {
            this(id, name, icon, null, biomes, types);
        }

        public RogueBiome(String id, String name, String icon, String block, List<String> biomes, List<String> types) {
            this.id = id;
            this.name = name;
            this.icon = icon;
            this.block = block;
            this.biomes = new ArrayList<>(biomes);
            this.types = new ArrayList<>(types);
        }
    }

    // ---------------------------------------------------------------- economy

    public int startingMoney = 1500;
    /** Trainer reward = base + level * perLevel. Gyms and the Champion multiply it. */
    public int trainerRewardBase = 100;
    public int trainerRewardPerLevel = 25;
    public double gymRewardMultiplier = 4.0;
    public double championRewardMultiplier = 8.0;

    /**
     * Shop categories. {@code action}: "use" applies the item to a Pokémon (potions, mints...),
     * "hold" gives it to a Pokémon to hold, and "auto" uses it if it can be used and holds it otherwise.
     * Unknown item ids are hidden, so entries for items that don't exist are safe.
     */
    public List<ShopCategory> shop = new ArrayList<>(List.of(
            new ShopCategory("Healing", "cobblemon:potion", "use", List.of(
                    new ShopEntry("cobblemon:potion", 200),
                    new ShopEntry("cobblemon:super_potion", 500),
                    new ShopEntry("cobblemon:hyper_potion", 1000),
                    new ShopEntry("cobblemon:max_potion", 1800),
                    new ShopEntry("cobblemon:full_restore", 2500),
                    new ShopEntry("cobblemon:revive", 1500),
                    new ShopEntry("cobblemon:max_revive", 3000),
                    new ShopEntry("cobblemon:full_heal", 400),
                    new ShopEntry("cobblemon:ether", 600),
                    new ShopEntry("cobblemon:max_ether", 1000),
                    new ShopEntry("cobblemon:elixir", 1500),
                    new ShopEntry("cobblemon:max_elixir", 2500))),
            // Priced by how strong they are in competitive play: top picks 6000, staples 4000,
            // niche 2500, weak 1200.
            new ShopCategory("Battle Items", "cobblemon:life_orb", "hold", List.of(
                    new ShopEntry("cobblemon:leftovers", 6000),
                    new ShopEntry("cobblemon:life_orb", 6000),
                    new ShopEntry("cobblemon:choice_band", 6000),
                    new ShopEntry("cobblemon:choice_specs", 6000),
                    new ShopEntry("cobblemon:choice_scarf", 6000),
                    new ShopEntry("cobblemon:focus_sash", 6000),
                    new ShopEntry("cobblemon:assault_vest", 6000),
                    new ShopEntry("cobblemon:rocky_helmet", 4000),
                    new ShopEntry("cobblemon:heavy_duty_boots", 4000),
                    new ShopEntry("cobblemon:eviolite", 6000),
                    new ShopEntry("cobblemon:expert_belt", 4000),
                    new ShopEntry("cobblemon:weakness_policy", 4000),
                    new ShopEntry("cobblemon:black_sludge", 4000),
                    new ShopEntry("cobblemon:air_balloon", 2500),
                    new ShopEntry("cobblemon:light_clay", 4000),
                    new ShopEntry("cobblemon:muscle_band", 2500),
                    new ShopEntry("cobblemon:wise_glasses", 2500),
                    new ShopEntry("cobblemon:scope_lens", 2500),
                    new ShopEntry("cobblemon:quick_claw", 1200),
                    new ShopEntry("cobblemon:shell_bell", 2500),
                    new ShopEntry("cobblemon:eject_button", 2500),
                    new ShopEntry("cobblemon:eject_pack", 2500),
                    new ShopEntry("cobblemon:red_card", 2500),
                    new ShopEntry("cobblemon:white_herb", 2500),
                    new ShopEntry("cobblemon:mental_herb", 2500),
                    new ShopEntry("cobblemon:power_herb", 2500),
                    new ShopEntry("cobblemon:safety_goggles", 2500),
                    new ShopEntry("cobblemon:protective_pads", 2500),
                    new ShopEntry("cobblemon:loaded_dice", 4000),
                    new ShopEntry("cobblemon:clear_amulet", 4000),
                    new ShopEntry("cobblemon:covert_cloak", 4000),
                    new ShopEntry("cobblemon:throat_spray", 2500),
                    new ShopEntry("cobblemon:blunder_policy", 2500),
                    new ShopEntry("cobblemon:flame_orb", 2500),
                    new ShopEntry("cobblemon:toxic_orb", 2500),
                    new ShopEntry("cobblemon:zoom_lens", 1200),
                    new ShopEntry("cobblemon:wide_lens", 1200),
                    new ShopEntry("cobblemon:focus_band", 1200),
                    new ShopEntry("cobblemon:bright_powder", 1200),
                    new ShopEntry("cobblemon:ability_shield", 2500),
                    new ShopEntry("cobblemon:utility_umbrella", 2500),
                    new ShopEntry("cobblemon:terrain_extender", 2500),
                    new ShopEntry("cobblemon:heat_rock", 2500),
                    new ShopEntry("cobblemon:damp_rock", 2500),
                    new ShopEntry("cobblemon:smooth_rock", 2500),
                    new ShopEntry("cobblemon:icy_rock", 2500),
                    new ShopEntry("cobblemon:mirror_herb", 2500),
                    new ShopEntry("cobblemon:punching_glove", 2500),
                    new ShopEntry("cobblemon:shed_shell", 1200),
                    new ShopEntry("cobblemon:big_root", 1200),
                    new ShopEntry("cobblemon:iron_ball", 1200))),
            new ShopCategory("Type Boosters", "cobblemon:charcoal_stick", "hold", List.of(
                    new ShopEntry("cobblemon:silk_scarf", 1500),
                    new ShopEntry("cobblemon:charcoal_stick", 1500),
                    new ShopEntry("cobblemon:mystic_water", 1500),
                    new ShopEntry("cobblemon:miracle_seed", 1500),
                    new ShopEntry("cobblemon:magnet", 1500),
                    new ShopEntry("cobblemon:never_melt_ice", 1500),
                    new ShopEntry("cobblemon:black_belt", 1500),
                    new ShopEntry("cobblemon:poison_barb", 1500),
                    new ShopEntry("cobblemon:soft_sand", 1500),
                    new ShopEntry("cobblemon:sharp_beak", 1500),
                    new ShopEntry("cobblemon:twisted_spoon", 1500),
                    new ShopEntry("cobblemon:silver_powder", 1500),
                    new ShopEntry("cobblemon:hard_stone", 1500),
                    new ShopEntry("cobblemon:spell_tag", 1500),
                    new ShopEntry("cobblemon:dragon_fang", 1500),
                    new ShopEntry("cobblemon:black_glasses", 1500),
                    new ShopEntry("cobblemon:metal_coat", 1500),
                    new ShopEntry("cobblemon:fairy_feather", 1500))),
            new ShopCategory("Berries", "cobblemon:sitrus_berry", "hold", List.of(
                    new ShopEntry("cobblemon:sitrus_berry", 1000),
                    new ShopEntry("cobblemon:lum_berry", 1200),
                    new ShopEntry("cobblemon:oran_berry", 200),
                    new ShopEntry("cobblemon:leppa_berry", 400),
                    new ShopEntry("cobblemon:chesto_berry", 300),
                    new ShopEntry("cobblemon:cheri_berry", 300),
                    new ShopEntry("cobblemon:pecha_berry", 300),
                    new ShopEntry("cobblemon:rawst_berry", 300),
                    new ShopEntry("cobblemon:aspear_berry", 300),
                    new ShopEntry("cobblemon:persim_berry", 300))),
            new ShopCategory("Training", "cobblemon:rare_candy", "use", List.of(
                    new ShopEntry("cobblemon:rare_candy", 1500),
                    new ShopEntry("cobblemon:exp_candy_s", 300),
                    new ShopEntry("cobblemon:exp_candy_m", 800),
                    new ShopEntry("cobblemon:exp_candy_l", 2000),
                    new ShopEntry("cobblemon:ability_capsule", 3000),
                    new ShopEntry("cobblemon:ability_patch", 8000),
                    new ShopEntry("cobblemon:hp_up", 1000),
                    new ShopEntry("cobblemon:protein", 1000),
                    new ShopEntry("cobblemon:iron", 1000),
                    new ShopEntry("cobblemon:calcium", 1000),
                    new ShopEntry("cobblemon:zinc", 1000),
                    new ShopEntry("cobblemon:carbos", 1000),
                    new ShopEntry("cobblemon:pp_up", 1500),
                    new ShopEntry("cobblemon:adamant_mint", 2000),
                    new ShopEntry("cobblemon:bold_mint", 2000),
                    new ShopEntry("cobblemon:brave_mint", 2000),
                    new ShopEntry("cobblemon:calm_mint", 2000),
                    new ShopEntry("cobblemon:careful_mint", 2000),
                    new ShopEntry("cobblemon:gentle_mint", 2000),
                    new ShopEntry("cobblemon:hasty_mint", 2000),
                    new ShopEntry("cobblemon:impish_mint", 2000),
                    new ShopEntry("cobblemon:jolly_mint", 2000),
                    new ShopEntry("cobblemon:lax_mint", 2000),
                    new ShopEntry("cobblemon:lonely_mint", 2000),
                    new ShopEntry("cobblemon:mild_mint", 2000),
                    new ShopEntry("cobblemon:modest_mint", 2000),
                    new ShopEntry("cobblemon:naive_mint", 2000),
                    new ShopEntry("cobblemon:naughty_mint", 2000),
                    new ShopEntry("cobblemon:quiet_mint", 2000),
                    new ShopEntry("cobblemon:rash_mint", 2000),
                    new ShopEntry("cobblemon:relaxed_mint", 2000),
                    new ShopEntry("cobblemon:sassy_mint", 2000),
                    new ShopEntry("cobblemon:serious_mint", 2000),
                    new ShopEntry("cobblemon:timid_mint", 2000))),
            new ShopCategory("Evolution", "cobblemon:fire_stone", "auto", List.of(
                    new ShopEntry("cobblemon:fire_stone", 2000),
                    new ShopEntry("cobblemon:water_stone", 2000),
                    new ShopEntry("cobblemon:thunder_stone", 2000),
                    new ShopEntry("cobblemon:leaf_stone", 2000),
                    new ShopEntry("cobblemon:moon_stone", 2000),
                    new ShopEntry("cobblemon:sun_stone", 2000),
                    new ShopEntry("cobblemon:shiny_stone", 2000),
                    new ShopEntry("cobblemon:dusk_stone", 2000),
                    new ShopEntry("cobblemon:dawn_stone", 2000),
                    new ShopEntry("cobblemon:ice_stone", 2000),
                    new ShopEntry("cobblemon:oval_stone", 2000),
                    new ShopEntry("cobblemon:link_cable", 2500),
                    new ShopEntry("cobblemon:kings_rock", 2500),
                    new ShopEntry("cobblemon:metal_coat", 2500),
                    new ShopEntry("cobblemon:dragon_scale", 2500),
                    new ShopEntry("cobblemon:upgrade", 2500),
                    new ShopEntry("cobblemon:protector", 2500),
                    new ShopEntry("cobblemon:electirizer", 2500),
                    new ShopEntry("cobblemon:magmarizer", 2500),
                    new ShopEntry("cobblemon:dubious_disc", 2500),
                    new ShopEntry("cobblemon:prism_scale", 2500),
                    new ShopEntry("cobblemon:reaper_cloth", 2500),
                    new ShopEntry("cobblemon:sachet", 2500),
                    new ShopEntry("cobblemon:whipped_dream", 2500)))));

    /** Mega Showdown integration (only used when the mod is installed). */
    public boolean megaShowdownShop = true;
    public int gimmickUnlockPrice = 5000;
    /** Mega Stones not listed in {@link #megaStonePrices}. */
    public int megaStonePrice = 5000;
    /**
     * Mega Stone prices by item path, by how strong the Mega is: top-tier Megas (Mega Metagross,
     * Gengar, Kangaskhan, Salamence, Mewtwo...) 12000, solid ones 8000, the rest megaStonePrice.
     */
    public Map<String, Integer> megaStonePrices = defaultMegaStonePrices();
    public int zCrystalPrice = 2500;
    /** Per shard; Mega Showdown needs several shards (its teraShardRequired config) to change a Tera type. */
    public int teraShardPrice = 100;

    private static Map<String, Integer> defaultMegaStonePrices() {
        Map<String, Integer> prices = new LinkedHashMap<>();
        for (String stone : List.of("mewtwonite_x", "mewtwonite_y", "salamencite", "metagrossite", "gengarite",
                "kangaskhanite", "lucarionite", "mawilite", "medichamite", "lopunnite", "garchompite",
                "gardevoirite", "alakazite", "scizorite", "charizardite_x", "charizardite_y", "diancite",
                "latiasite", "latiosite", "tyranitarite", "swampertite", "blazikenite", "pinsirite")) {
            prices.put(stone, 12000);
        }
        for (String stone : List.of("aerodactylite", "altarianite", "ampharosite", "venusaurite", "blastoisinite",
                "gyaradosite", "heracronite", "houndoominite", "manectite", "pidgeotite", "sablenite",
                "sceptilite", "slowbronite", "steelixite", "glalitite", "audinite", "absolite", "galladite",
                "aggronite", "cameruptite", "sharpedonite", "banettite", "beedrillite", "abomasite",
                "dragoninite", "excadrite", "greninjite", "clefablite", "froslassite")) {
            prices.put(stone, 8000);
        }
        return prices;
    }

    public static final class ShopCategory {
        public String name;
        public String icon;
        public String action;
        public List<ShopEntry> items;

        public ShopCategory(String name, String icon, String action, List<ShopEntry> items) {
            this.name = name;
            this.icon = icon;
            this.action = action;
            this.items = new ArrayList<>(items);
        }
    }

    public static final class ShopEntry {
        public String item;
        public int price;

        public ShopEntry(String item, int price) {
            this.item = item;
            this.price = price;
        }
    }

    // ---------------------------------------------------------------- doubles & teams

    /** Which run battles are double battles: "bosses" (gyms + Champion), "all" or "none". */
    public String doubleBattles = "bosses";
    /** With "bosses", the chance that a normal trainer also battles in doubles. */
    public double doubleTrainerChance = 0.2;

    // ---------------------------------------------------------------- Elite Four

    /** Elite Four members to beat (after all badges) before the Champion appears. 0 skips them. */
    public int eliteCount = 4;
    /** Floors between Elite Four battles (1 = back to back). */
    public int eliteEvery = 2;
    public int eliteTeamSize = 5;
    public double eliteRewardMultiplier = 5.0;
    public boolean healAfterElite = false;

    // ---------------------------------------------------------------- difficulty
    // Defaults are the hardest settings. Run modifiers (Nuzlocke, Hard, ...) stay optional toggles.

    /**
     * Added to every trainer's set tier: 0 = best level-up moves, 1 = + TM/egg moves, items,
     * natures, perfect IVs, 2 = + full EV spreads. Tiers are capped at 2, so 2 means everyone is
     * fully built. Lower it for an easier game.
     */
    public int setTierBonus = 2;
    /** Every trainer uses Cobblemon's smartest AI (skill 5). */
    public boolean maxTrainerAi = true;
    /** Normal trainers with 3+ Pokémon use team archetypes from this many badges on (-1 = never). */
    public int trainerArchetypesFromBadge = 0;

    // ---------------------------------------------------------------- balance

    /**
     * Difficulty ramps up to the full config settings over the first gyms: before this many badges,
     * trainers use weaker sets, no max AI, no archetypes for normal trainers and no boss gimmicks.
     */
    public int rampUntilBadge = 2;
    /** The first floors of a run offer routes instead of trainers, so you can build a team first. */
    public int startRouteFloors = 2;
    /** Extra battle EXP for Pokémon far below the level cap (up to 2x more). */
    public boolean catchUpExp = true;

    /** EXP multiplier for run Pokémon (still capped at the level cap). */
    public double expMultiplier = 2.0;
    /** Boss prep on gym / Elite Four / Champion previews, once per boss and per player: */
    public boolean prepTrainToCap = true;
    /** Draft a counter: only in runs with the Counter Draft modifier. */
    public boolean prepDraft = true;
    public boolean prepHeal = true;
    /** Boss prep heal: free before the first gym, then this many coins (+ per badge). */
    public int prepHealPrice = 2000;
    public int prepHealPricePerBadge = 750;
    public int prepDraftOptions = 3;

    // ---------------------------------------------------------------- boss archetypes

    /**
     * Bosses build teams around a plan (Radical Red / Run & Bun style): rain, sun, sand, snow,
     * Trick Room, Tailwind or a terrain, with a setter, abusers and (in doubles) Fake Out /
     * redirection / Intimidate support.
     */
    public boolean bossArchetypes = true;
    /** Gyms use archetypes from this many badges on (the Elite Four and the Champion always do). */
    public int archetypeFromBadge = 0;

    // ---------------------------------------------------------------- boss gimmicks (Mega Showdown)

    /** Gym leaders, the Elite Four and the Champion Mega Evolve or Terastallize (needs Mega Showdown). */
    public boolean bossGimmicks = true;
    /** Gyms use Tera from this many badges on... */
    public int bossTeraFromBadge = 0;
    /** ...and Mega Evolution (when a team member can) from this many badges on. */
    public int bossMegaFromBadge = 0;

    // ---------------------------------------------------------------- co-op

    /** Party size per player in co-op runs (6 for solo). */
    public int coopPartyLimit = 3;
    /** Co-op players must be this close (same dimension) to start a battle together. */
    public int coopMaxDistance = 48;
    /** Seconds an invite stays valid. */
    public int coopInviteSeconds = 120;
    /** Co-op legendaries: guaranteed after these badge counts instead of {@code legendaryAfterBadges}... */
    public List<Integer> coopLegendaryAfterBadges = new ArrayList<>(List.of(4, 7));
    /** ...and never before this many badges (the random chance included). */
    public int coopLegendaryFromBadge = 4;

    // ---------------------------------------------------------------- move tutor

    /** Coins to teach a TM/tutor/egg move. Level-up moves the Pokémon already qualifies for are free. */
    public int moveTutorPrice = 800;

    // ---------------------------------------------------------------- rewards (/rogue shop)

    /** Rogue Tokens paid when a run ends: per floor cleared, per badge, and a bonus for beating the Champion. */
    public int tokensPerFloor = 2;
    public int tokensPerBadge = 15;
    public int championTokenBonus = 100;

    /** Extra Rogue Tokens for each run modifier (0.5 = +50%). */
    public double nuzlockeTokenBonus = 0.5;
    public double soloTokenBonus = 1.0;
    public double hardTokenBonus = 0.5;
    public double noShopTokenBonus = 0.25;
    public double allDoublesTokenBonus = 0.25;
    /** Counter Draft makes runs easier, so it costs tokens instead. */
    public double counterDraftTokenBonus = -0.25;

    /** The /rogue shop reward catalog: real items, bought with Rogue Tokens and kept forever. */
    public List<ShopCategory> tokenShop = new ArrayList<>(List.of(
            new ShopCategory("Nature Mints", "cobblemon:adamant_mint", "give", List.of(
                    new ShopEntry("cobblemon:adamant_mint", 40),
                    new ShopEntry("cobblemon:bold_mint", 40),
                    new ShopEntry("cobblemon:brave_mint", 40),
                    new ShopEntry("cobblemon:calm_mint", 40),
                    new ShopEntry("cobblemon:careful_mint", 40),
                    new ShopEntry("cobblemon:gentle_mint", 40),
                    new ShopEntry("cobblemon:hasty_mint", 40),
                    new ShopEntry("cobblemon:impish_mint", 40),
                    new ShopEntry("cobblemon:jolly_mint", 40),
                    new ShopEntry("cobblemon:lax_mint", 40),
                    new ShopEntry("cobblemon:lonely_mint", 40),
                    new ShopEntry("cobblemon:mild_mint", 40),
                    new ShopEntry("cobblemon:modest_mint", 40),
                    new ShopEntry("cobblemon:naive_mint", 40),
                    new ShopEntry("cobblemon:naughty_mint", 40),
                    new ShopEntry("cobblemon:quiet_mint", 40),
                    new ShopEntry("cobblemon:rash_mint", 40),
                    new ShopEntry("cobblemon:relaxed_mint", 40),
                    new ShopEntry("cobblemon:sassy_mint", 40),
                    new ShopEntry("cobblemon:serious_mint", 40),
                    new ShopEntry("cobblemon:timid_mint", 40))),
            new ShopCategory("Training", "cobblemon:ability_capsule", "give", List.of(
                    new ShopEntry("cobblemon:ability_capsule", 60),
                    new ShopEntry("cobblemon:ability_patch", 150),
                    new ShopEntry("cobblemon:pp_up", 25),
                    new ShopEntry("cobblemon:pp_max", 60),
                    new ShopEntry("cobblemon:hp_up", 15),
                    new ShopEntry("cobblemon:protein", 15),
                    new ShopEntry("cobblemon:iron", 15),
                    new ShopEntry("cobblemon:calcium", 15),
                    new ShopEntry("cobblemon:zinc", 15),
                    new ShopEntry("cobblemon:carbos", 15))),
            new ShopCategory("Breeding & Utility", "cobblemon:destiny_knot", "give", List.of(
                    new ShopEntry("cobblemon:destiny_knot", 120),
                    new ShopEntry("cobblemon:everstone", 30),
                    new ShopEntry("cobblemon:power_weight", 60),
                    new ShopEntry("cobblemon:power_bracer", 60),
                    new ShopEntry("cobblemon:power_belt", 60),
                    new ShopEntry("cobblemon:power_lens", 60),
                    new ShopEntry("cobblemon:power_band", 60),
                    new ShopEntry("cobblemon:power_anklet", 60),
                    new ShopEntry("cobblemon:exp_share", 80),
                    new ShopEntry("cobblemon:lucky_egg", 100),
                    new ShopEntry("cobblemon:soothe_bell", 40))),
            new ShopCategory("Special Balls", "cobblemon:beast_ball", "give", List.of(
                    new ShopEntry("cobblemon:love_ball", 20),
                    new ShopEntry("cobblemon:moon_ball", 20),
                    new ShopEntry("cobblemon:friend_ball", 20),
                    new ShopEntry("cobblemon:lure_ball", 20),
                    new ShopEntry("cobblemon:heavy_ball", 20),
                    new ShopEntry("cobblemon:level_ball", 20),
                    new ShopEntry("cobblemon:fast_ball", 20),
                    new ShopEntry("cobblemon:sport_ball", 20),
                    new ShopEntry("cobblemon:safari_ball", 20),
                    new ShopEntry("cobblemon:park_ball", 20),
                    new ShopEntry("cobblemon:premier_ball", 20),
                    new ShopEntry("cobblemon:luxury_ball", 20),
                    new ShopEntry("cobblemon:heal_ball", 20),
                    new ShopEntry("cobblemon:dream_ball", 30),
                    new ShopEntry("cobblemon:beast_ball", 30),
                    new ShopEntry("cobblemon:cherish_ball", 50),
                    new ShopEntry("cobblemon:master_ball", 750)))));

    /**
     * Players who have the mod installed get the run screen (party, floor tower, biome-themed
     * panels) instead of chest menus. Players without it always get chest menus.
     */
    public boolean clientScreen = true;

    /** Root commands a non-op player cannot run during a run. */
    public List<String> blockedCommands = new ArrayList<>(List.of("pc", "trade"));

    public static RogueConfig get() {
        return instance;
    }

    /** Bump when the default shop prices change; older configs get the new catalog once. */
    private static final int SHOP_VERSION = 2;
    public int shopVersion = 0;

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
        if (instance.shopVersion < SHOP_VERSION) {
            if (Files.exists(path)) {
                Cobbleroguelike.LOGGER.info("Updating the run shop catalog and Mega Stone prices to the new defaults");
            }
            RogueConfig defaults = new RogueConfig();
            instance.shop = defaults.shop;
            instance.megaStonePrice = defaults.megaStonePrice;
            instance.megaStonePrices = defaults.megaStonePrices;
            instance.shopVersion = SHOP_VERSION;
        }
        try (Writer writer = Files.newBufferedWriter(path)) {
            GSON.toJson(instance, writer);
        } catch (IOException e) {
            Cobbleroguelike.LOGGER.error("Failed to write {}", path, e);
        }
    }
}
