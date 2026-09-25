package org.CobbleUtils.cobbleroguelike.run;

import org.CobbleUtils.cobbleroguelike.RogueConfig;

import java.util.List;

/**
 * Level math. The run is split into segments of {@code gymEvery} floors that each end in a
 * boss. Within a segment, levels ramp from the previous cap toward the next one.
 */
public final class Scaling {

    private Scaling() {
    }

    /** Level cap for the next boss: rogue Pokémon can't level past this. */
    public static int levelCap(int badges) {
        List<Integer> caps = RogueConfig.get().levelCaps;
        return caps.get(Math.max(0, Math.min(badges, caps.size() - 1)));
    }

    /** 1-based position of the floor within its segment; the last one is the boss floor. */
    public static int floorInSegment(int floor) {
        int gymEvery = Math.max(1, RogueConfig.get().gymEvery);
        return ((Math.max(1, floor) - 1) % gymEvery) + 1;
    }

    public static boolean isBossFloor(int floor) {
        return floorInSegment(floor) == Math.max(1, RogueConfig.get().gymEvery);
    }

    public static boolean championUnlocked(int badges) {
        return badges >= RogueConfig.get().gymCount;
    }

    /** Level for normal trainers on this floor, ramping toward (but staying under) the cap. */
    public static int trainerLevel(RunState state) {
        RogueConfig config = RogueConfig.get();
        int cap = levelCap(state.badges);
        int previous = state.badges == 0 ? config.starterLevel : levelCap(state.badges - 1);
        int steps = Math.max(1, config.gymEvery - 1);
        double progress = (floorInSegment(state.floor) - 1) / (double) steps;
        return Math.max(2, previous + (int) Math.round((cap - previous) * 0.85 * progress));
    }

    public static int encounterLevel(RunState state) {
        return Math.max(2, trainerLevel(state) - 1);
    }

    /** Base stat total window that fits a level, so early trainers don't field pseudo-legendaries. */
    public static int maxBst(int level) {
        return Math.max(320, Math.min(720, 260 + level * 6));
    }

    public static int minBst(int level) {
        return maxBst(level) - 200;
    }
}
