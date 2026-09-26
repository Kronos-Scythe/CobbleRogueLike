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
    /** Heal the party after beating a gym, like a Pokémon Center visit. */
    public boolean healAfterGym = true;

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
            new ShopCategory("Battle Items", "cobblemon:life_orb", "hold", List.of(
                    new ShopEntry("cobblemon:leftovers", 3000),
                    new ShopEntry("cobblemon:life_orb", 4000),
                    new ShopEntry("cobblemon:choice_band", 4000),
                    new ShopEntry("cobblemon:choice_specs", 4000),
                    new ShopEntry("cobblemon:choice_scarf", 4000),
                    new ShopEntry("cobblemon:focus_sash", 2500),
                    new ShopEntry("cobblemon:assault_vest", 3500),
                    new ShopEntry("cobblemon:rocky_helmet", 2500),
                    new ShopEntry("cobblemon:heavy_duty_boots", 2500),
                    new ShopEntry("cobblemon:eviolite", 3000),
                    new ShopEntry("cobblemon:expert_belt", 2500),
                    new ShopEntry("cobblemon:weakness_policy", 2500),
                    new ShopEntry("cobblemon:black_sludge", 2000),
                    new ShopEntry("cobblemon:air_balloon", 1500),
                    new ShopEntry("cobblemon:light_clay", 2000),
                    new ShopEntry("cobblemon:muscle_band", 2000),
                    new ShopEntry("cobblemon:wise_glasses", 2000),
                    new ShopEntry("cobblemon:scope_lens", 2000),
                    new ShopEntry("cobblemon:quick_claw", 1500),
                    new ShopEntry("cobblemon:shell_bell", 2000),
                    new ShopEntry("cobblemon:eject_button", 1500),
                    new ShopEntry("cobblemon:eject_pack", 1500),
                    new ShopEntry("cobblemon:red_card", 1500),
                    new ShopEntry("cobblemon:white_herb", 1500),
                    new ShopEntry("cobblemon:mental_herb", 1500),
                    new ShopEntry("cobblemon:power_herb", 1500),
                    new ShopEntry("cobblemon:safety_goggles", 1500),
                    new ShopEntry("cobblemon:protective_pads", 1500),
                    new ShopEntry("cobblemon:loaded_dice", 2000),
                    new ShopEntry("cobblemon:clear_amulet", 2000),
                    new ShopEntry("cobblemon:covert_cloak", 2000),
                    new ShopEntry("cobblemon:throat_spray", 1500),
                    new ShopEntry("cobblemon:blunder_policy", 1500),
                    new ShopEntry("cobblemon:flame_orb", 1500),
                    new ShopEntry("cobblemon:toxic_orb", 1500),
                    new ShopEntry("cobblemon:zoom_lens", 1500),
                    new ShopEntry("cobblemon:wide_lens", 1500),
                    new ShopEntry("cobblemon:focus_band", 1500),
                    new ShopEntry("cobblemon:bright_powder", 1500),
                    new ShopEntry("cobblemon:ability_shield", 2000),
                    new ShopEntry("cobblemon:utility_umbrella", 1500),
                    new ShopEntry("cobblemon:terrain_extender", 1500),
                    new ShopEntry("cobblemon:heat_rock", 1000),
                    new ShopEntry("cobblemon:damp_rock", 1000),
                    new ShopEntry("cobblemon:smooth_rock", 1000),
                    new ShopEntry("cobblemon:icy_rock", 1000),
                    new ShopEntry("cobblemon:mirror_herb", 1500),
                    new ShopEntry("cobblemon:punching_glove", 1500),
                    new ShopEntry("cobblemon:shed_shell", 1000),
                    new ShopEntry("cobblemon:big_root", 1000),
                    new ShopEntry("cobblemon:iron_ball", 1000))),
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
                    new ShopEntry("cobblemon:sitrus_berry", 600),
                    new ShopEntry("cobblemon:lum_berry", 600),
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
    public int megaStonePrice = 3000;
    public int zCrystalPrice = 2500;
    /** Per shard; Mega Showdown needs several shards (its teraShardRequired config) to change a Tera type. */
    public int teraShardPrice = 100;

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
