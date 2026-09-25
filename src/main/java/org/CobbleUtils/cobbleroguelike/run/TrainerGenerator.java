package org.CobbleUtils.cobbleroguelike.run;

import org.CobbleUtils.cobbleroguelike.RogueConfig;
import org.CobbleUtils.cobbleroguelike.compat.CobblemonBridge;
import org.CobbleUtils.cobbleroguelike.run.RunState.NodeType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

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
        switch (kind) {
            case GYM -> {
                String type = pickGymType(state, random);
                int cap = Scaling.levelCap(state.badges);
                state.battleType = type;
                state.battleName = "Gym Leader " + name + " (" + capitalize(type) + ")";
                state.battleSkill = Math.min(5, 2 + state.badges / 2);
                state.battleTeam = buildTeam(type, teamSize(config.gymTeamSizes, state.badges), cap, true, false, random);
            }
            case CHAMPION -> {
                int cap = Scaling.levelCap(state.badges);
                state.battleName = "Champion " + name;
                state.battleSkill = 5;
                state.battleTeam = buildTeam(null, teamSize(config.gymTeamSizes, state.badges), cap, true, false, random);
            }
            default -> {
                int level = Scaling.trainerLevel(state);
                int size = Math.min(6, 1 + state.badges / 2 + random.nextInt(2));
                state.battleName = TRAINER_CLASSES[random.nextInt(TRAINER_CLASSES.length)] + " " + name;
                state.battleSkill = Math.min(5, 1 + state.badges / 2);
                state.battleTeam = buildTeam(null, size, level, false, false, random);
            }
        }
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
