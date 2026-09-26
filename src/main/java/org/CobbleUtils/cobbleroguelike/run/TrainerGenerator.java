package org.CobbleUtils.cobbleroguelike.run;

import org.CobbleUtils.cobbleroguelike.RogueConfig;
import org.CobbleUtils.cobbleroguelike.compat.CobblemonBridge;
import org.CobbleUtils.cobbleroguelike.compat.MegaData;
import org.CobbleUtils.cobbleroguelike.shop.ShopCatalog;
import org.CobbleUtils.cobbleroguelike.compat.TeamBuilder;
import org.CobbleUtils.cobbleroguelike.run.RunState.NodeType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

/** Fills a {@link RunState} with the next battle: a random trainer, a typed gym, or the Champion. */
public final class TrainerGenerator {

    private static final String[] TRAINER_CLASSES = {
            "Youngster", "Lass", "Bug Catcher", "Hiker", "Swimmer", "Picnicker", "Camper", "Fisher",
            "Ace Trainer", "Black Belt", "Psychic", "Beauty", "Scientist", "Ranger", "Veteran", "Rival"};
    private static final String[] NAMES = {
            "Joey", "Ava", "Kai", "Mira", "Theo", "Lena", "Ravi", "Nora", "Otis", "Iris", "Hugo",
            "Sage", "Remy", "Juno", "Felix", "Wren", "Cyrus", "Dahlia", "Emil", "Tess"};

    private TrainerGenerator() {
    }

    public static void prepare(RunState state, NodeType kind, Random random) {
        RogueConfig config = RogueConfig.get();
        state.clearBattle();
        state.battleKind = kind;
        String name = NAMES[random.nextInt(NAMES.length)];
        boolean boss = kind == NodeType.GYM || kind == NodeType.ELITE || kind == NodeType.CHAMPION;
        boolean doubles = switch (config.doubleBattles.toLowerCase(Locale.ROOT)) {
            case "all" -> true;
            case "none" -> false;
            default -> boss || random.nextDouble() < config.doubleTrainerChance;
        };
        switch (kind) {
            case GYM -> {
                String type = pickGymType(state, random);
                int cap = Scaling.levelCap(state.badges);
                state.battleType = type;
                state.battleName = "Gym Leader " + name + " (" + capitalize(type) + ")";
                state.battleSkill = Math.min(5, 2 + state.badges / 2);
                state.battleTeam = buildTeam(type, teamSize(config.gymTeamSizes, state.badges), cap, true, false, random);
            }
            case ELITE -> {
                String type = pickEliteType(state, random);
                int level = Scaling.eliteLevel(state);
                state.battleType = type;
                state.battleName = "Elite Four " + name + " (" + capitalize(type) + ")";
                state.battleSkill = 5;
                state.battleTeam = buildTeam(type, Math.max(1, Math.min(6, config.eliteTeamSize)), level, true, false, random);
            }
            case CHAMPION -> {
                int cap = Scaling.levelCap(state.badges);
                state.battleName = "Champion " + name;
                state.battleSkill = 5;
                state.battleTeam = buildTeam(null, teamSize(config.gymTeamSizes, state.badges), cap, true, false, random);
            }
            case LEGENDARY -> {
                if (!prepareLegendary(state, random)) {
                    // Nothing eligible (e.g. legendaries disabled by datapack): fall back to a trainer.
                    prepare(state, NodeType.TRAINER, random);
                }
                return; // legendary encounters are always singles and use their natural set
            }
            default -> {
                int level = Scaling.trainerLevel(state);
                int size = Math.min(6, 1 + state.badges / 2 + random.nextInt(2));
                if (doubles) {
                    size = Math.max(2, size);
                }
                state.battleName = TRAINER_CLASSES[random.nextInt(TRAINER_CLASSES.length)] + " " + name;
                state.battleSkill = Math.min(5, 1 + state.badges / 2);
                // Themed by the current biome: its real spawns and its types.
                List<String> biomePool = Encounters.trainerPool(state, level);
                state.battleTeam = biomePool.size() >= size
                        ? teamFrom(biomePool, size, level, false, random)
                        : buildTeam(null, size, level, false, false, random);
            }
        }
        state.battleDoubles = doubles && state.battleTeam.size() >= 2;
        state.battleTeam = strengthen(state, state.battleDoubles, random);
        applyGimmick(state, random);
    }

    /**
     * With Mega Showdown installed, bosses get a gimmick for their ace. It is Mega Evolution if a
     * suitable Mega-capable Pokémon can lead the team (a gym's or Elite's ace must share its type),
     * otherwise Terastallization into the gym/Elite type (or the ace's own type for the Champion).
     */
    private static void applyGimmick(RunState state, Random random) {
        RogueConfig config = RogueConfig.get();
        boolean boss = state.battleKind == NodeType.GYM || state.battleKind == NodeType.ELITE
                || state.battleKind == NodeType.CHAMPION;
        if (!boss || !config.bossGimmicks || !ShopCatalog.megaShowdownLoaded() || state.battleTeam.isEmpty()) {
            return;
        }
        boolean lateBoss = state.battleKind != NodeType.GYM;
        int aceIndex = state.battleTeam.size() - 1;
        String ace = state.battleTeam.get(aceIndex);

        if (lateBoss || state.badges >= config.bossMegaFromBadge) {
            Map<String, List<String>> stones = MegaData.stonesBySpecies();
            // Prefer a team member that can already Mega Evolve; make it the ace.
            for (int i = aceIndex; i >= 0 && !stones.isEmpty(); i--) {
                String species = speciesOf(state.battleTeam.get(i));
                if (stones.containsKey(species)) {
                    String member = state.battleTeam.remove(i);
                    List<String> options = stones.get(species);
                    state.battleTeam.add(withToken(member, "held_item", options.get(random.nextInt(options.size()))));
                    state.battleGimmick = "mega";
                    return;
                }
            }
            // Otherwise bring in a fitting Mega-capable ace (same type for gyms and Elites).
            List<String> candidates = new ArrayList<>();
            for (String species : stones.keySet()) {
                if (state.battleType.isEmpty() || CobblemonBridge.speciesHasType(species, state.battleType)) {
                    candidates.add(species);
                }
            }
            if (!candidates.isEmpty()) {
                candidates.sort(String::compareTo);
                String species = candidates.get(random.nextInt(candidates.size()));
                int level = levelOf(ace);
                Set<String> usedItems = new HashSet<>();
                String built = TeamBuilder.build(species + " level=" + level, 2, state.battleDoubles, false, usedItems, random);
                List<String> options = stones.get(species);
                state.battleTeam.set(aceIndex, withToken(built, "held_item", options.get(random.nextInt(options.size()))));
                state.battleGimmick = "mega";
                return;
            }
        }
        if (lateBoss || state.badges >= config.bossTeraFromBadge) {
            String teraType = !state.battleType.isEmpty() ? state.battleType : CobblemonBridge.primaryType(speciesOf(ace));
            if (teraType != null) {
                state.battleTeam.set(aceIndex, withToken(ace, "tera_type", teraType));
                state.battleGimmick = "tera";
            }
        }
    }

    /** Species id at the start of a property string. */
    public static String speciesOf(String properties) {
        return properties.split(" ")[0];
    }

    private static int levelOf(String properties) {
        for (String part : properties.split(" ")) {
            if (part.startsWith("level=")) {
                try {
                    return Integer.parseInt(part.substring(6));
                } catch (NumberFormatException ignored) {
                    return 50;
                }
            }
        }
        return 50;
    }

    /** Replaces (or adds) a {@code key=value} token in a property string. */
    private static String withToken(String properties, String key, String value) {
        StringBuilder result = new StringBuilder();
        for (String part : properties.split(" ")) {
            if (!part.startsWith(key + "=")) {
                result.append(result.length() == 0 ? "" : " ").append(part);
            }
        }
        return result + " " + key + "=" + value;
    }

    private static String pickEliteType(RunState state, Random random) {
        List<String> types = new ArrayList<>(CobblemonBridge.typeNames());
        types.removeAll(state.usedEliteTypes);
        if (types.isEmpty()) {
            types = CobblemonBridge.typeNames();
        }
        return types.get(random.nextInt(types.size()));
    }

    /**
     * Upgrades the prepared team into competitive sets (see {@link TeamBuilder}).
     * <ul>
     *     <li>Gyms: tier 0 at the first gym, tier 1 until 4 badges, then tier 2. Everyone holds
     *     an item from the second gym on (only the ace at the first).</li>
     *     <li>The Champion: tier 2, and everyone holds an item.</li>
     *     <li>Normal trainers: tier 0 movesets; the ace holds an item from 4 badges on.</li>
     * </ul>
     * Items follow the item clause (no duplicates).
     */
    private static List<String> strengthen(RunState state, boolean doubles, Random random) {
        int tier;
        boolean itemsForAll;
        switch (state.battleKind) {
            case GYM -> {
                tier = state.badges == 0 ? 0 : state.badges < 4 ? 1 : 2;
                itemsForAll = tier >= 1;
            }
            case ELITE, CHAMPION -> {
                tier = 2;
                itemsForAll = true;
            }
            default -> {
                tier = 0;
                itemsForAll = false;
            }
        }
        boolean aceItem = state.battleKind != NodeType.TRAINER || state.badges >= 4;
        Set<String> usedItems = new HashSet<>();
        List<String> result = new ArrayList<>();
        for (int i = 0; i < state.battleTeam.size(); i++) {
            boolean ace = i == state.battleTeam.size() - 1;
            boolean giveItem = itemsForAll || (aceItem && ace);
            result.add(TeamBuilder.build(state.battleTeam.get(i), tier, doubles, giveItem, usedItems, random));
        }
        return result;
    }

    /**
     * A single wild Legendary at the level cap. The tier grows with badges, and box legendaries
     * only appear late. Returns false if nothing is eligible.
     */
    private static boolean prepareLegendary(RunState state, Random random) {
        RogueConfig config = RogueConfig.get();
        int maxBst = state.badges < 3 ? 600 : state.badges < 5 ? 680 : 800;
        List<String> pool = CobblemonBridge.legendaryPool(maxBst, state.badges >= config.restrictedLegendaryBadges,
                config.includeUltraBeasts, config.includeParadox);
        if (pool.isEmpty()) {
            return false;
        }
        pool.sort(String::compareTo);
        String species = pool.get(random.nextInt(pool.size()));
        int level = Scaling.levelCap(state.badges);
        state.battleKind = NodeType.LEGENDARY;
        state.battleName = "Wild " + capitalize(species.contains(":") ? species.substring(species.indexOf(':') + 1) : species);
        state.battleSkill = Math.min(5, 2 + state.badges / 2);
        state.battleTeam = new ArrayList<>(List.of(Encounters.withExtras(species + " level=" + level, random)));
        return true;
    }

    /** Picks {@code size} distinct entries from a property-string pool and adds levels. */
    private static List<String> teamFrom(List<String> pool, int size, int level, boolean boss, Random random) {
        List<String> remaining = new ArrayList<>(pool);
        List<String> team = new ArrayList<>();
        for (int i = 0; i < size && !remaining.isEmpty(); i++) {
            String properties = remaining.remove(random.nextInt(remaining.size()));
            int memberLevel = boss
                    ? (i == size - 1 ? level : level - 1 - random.nextInt(3))
                    : level - random.nextInt(3);
            team.add(properties + " level=" + Math.max(2, memberLevel));
        }
        return team;
    }

    /**
     * Builds property strings. Bosses get an ace at {@code level} and the rest 1–3 levels lower.
     * Normal trainers vary by up to 2 levels under {@code level}.
     */
    private static List<String> buildTeam(String type, int size, int level, boolean boss, boolean allowLegendary, Random random) {
        List<String> pool = CobblemonBridge.speciesPool(type, Scaling.minBst(level), Scaling.maxBst(level), allowLegendary);
        if (pool.size() < size) {
            pool = CobblemonBridge.speciesPool(type, 0, Scaling.maxBst(level) + 100, allowLegendary);
        }
        if (pool.isEmpty()) {
            pool = new ArrayList<>(RogueConfig.get().encounterPool);
        }
        List<String> team = new ArrayList<>();
        List<String> remaining = new ArrayList<>(pool);
        for (int i = 0; i < size && !remaining.isEmpty(); i++) {
            String species = remaining.remove(random.nextInt(remaining.size()));
            int memberLevel = boss
                    ? (i == size - 1 ? level : level - 1 - random.nextInt(3))
                    : level - random.nextInt(3);
            team.add(species + " level=" + Math.max(2, memberLevel));
        }
        return team;
    }

    private static String pickGymType(RunState state, Random random) {
        List<String> types = new ArrayList<>(CobblemonBridge.typeNames());
        types.removeAll(state.usedGymTypes);
        if (types.isEmpty()) {
            types = CobblemonBridge.typeNames();
        }
        return types.get(random.nextInt(types.size()));
    }

    private static int teamSize(List<Integer> sizes, int badges) {
        if (sizes.isEmpty()) {
            return 6;
        }
        return Math.max(1, Math.min(6, sizes.get(Math.min(badges, sizes.size() - 1))));
    }

    public static String capitalize(String text) {
        return text.isEmpty() ? text : text.substring(0, 1).toUpperCase(Locale.ROOT) + text.substring(1);
    }
}
