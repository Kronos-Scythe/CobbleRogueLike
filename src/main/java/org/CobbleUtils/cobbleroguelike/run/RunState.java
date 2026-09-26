package org.CobbleUtils.cobbleroguelike.run;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Progress of one player's run. Persisted after every change. */
public final class RunState {

    public enum Phase { CHOOSE_NODE, ENCOUNTER, RELEASE, BATTLE }

    public enum NodeType { ROUTE, REST, TRAINER, GYM, CHAMPION }

    public final UUID playerId;
    public final long seed;
    public Phase phase = Phase.CHOOSE_NODE;
    public int floor = 0;
    public int badges = 0;
    public List<String> usedGymTypes = new ArrayList<>();
    public List<NodeType> nodeChoices = new ArrayList<>();
    /** Property strings, e.g. {@code "zubat level=7"}. */
    public List<String> encounterOptions = new ArrayList<>();
    /** Encounter waiting for a party slot while in {@link Phase#RELEASE}. */
    public String pendingEncounter = "";

    // The upcoming battle while in {@link Phase#BATTLE}. Stored so a retry faces the same trainer.
    public NodeType battleKind = NodeType.TRAINER;
    public String battleName = "";
    /** Gym type, e.g. {@code "fire"}; empty for other battles. */
    public String battleType = "";
    public List<String> battleTeam = new ArrayList<>();
    public int battleSkill = 0;

    public RunState(UUID playerId, long seed) {
        this.playerId = playerId;
        this.seed = seed;
    }

    public void clearBattle() {
        battleKind = NodeType.TRAINER;
        battleName = "";
        battleType = "";
        battleTeam = new ArrayList<>();
        battleSkill = 0;
    }

    public NbtCompound toNbt() {
        NbtCompound tag = new NbtCompound();
        tag.putUuid("player", playerId);
        tag.putLong("seed", seed);
        tag.putString("phase", phase.name());
        tag.putInt("floor", floor);
        tag.putInt("badges", badges);
        tag.put("usedGymTypes", writeStrings(usedGymTypes));
        tag.put("nodeChoices", writeStrings(nodeChoices.stream().map(Enum::name).toList()));
        tag.put("encounterOptions", writeStrings(encounterOptions));
        tag.putString("pendingEncounter", pendingEncounter);
        tag.putString("battleKind", battleKind.name());
        tag.putString("battleName", battleName);
        tag.putString("battleType", battleType);
        tag.put("battleTeam", writeStrings(battleTeam));
        tag.putInt("battleSkill", battleSkill);
        return tag;
    }

    public static RunState fromNbt(NbtCompound tag) {
        RunState state = new RunState(tag.getUuid("player"), tag.getLong("seed"));
        // Tolerate data from older builds: unknown values fall back instead of failing the load.
        state.phase = parse(Phase.class, tag.getString("phase"), Phase.CHOOSE_NODE);
        state.floor = tag.getInt("floor");
        state.badges = tag.getInt("badges");
        state.usedGymTypes = readStrings(tag, "usedGymTypes");
        for (String node : readStrings(tag, "nodeChoices")) {
            NodeType type = parse(NodeType.class, node, null);
            if (type != null) {
                state.nodeChoices.add(type);
            }
        }
        state.encounterOptions = readStrings(tag, "encounterOptions");
        state.pendingEncounter = tag.getString("pendingEncounter");
        state.battleKind = parse(NodeType.class, tag.getString("battleKind"), NodeType.TRAINER);
        state.battleName = tag.getString("battleName");
        state.battleType = tag.getString("battleType");
        state.battleTeam = readStrings(tag, "battleTeam");
        state.battleSkill = tag.getInt("battleSkill");
        return state;
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String name, E fallback) {
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private static NbtList writeStrings(List<String> values) {
        NbtList list = new NbtList();
        for (String value : values) {
            list.add(NbtString.of(value));
        }
        return list;
    }

    private static List<String> readStrings(NbtCompound tag, String key) {
        List<String> values = new ArrayList<>();
        NbtList list = tag.getList(key, NbtElement.STRING_TYPE);
        for (int i = 0; i < list.size(); i++) {
            values.add(list.getString(i));
        }
        return values;
    }
}
