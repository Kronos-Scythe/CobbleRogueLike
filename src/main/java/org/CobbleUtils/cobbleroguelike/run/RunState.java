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

    public enum Phase { STARTER, CHOOSE_NODE, ENCOUNTER, RELEASE }

    public enum NodeType { ROUTE, REST }

    public final UUID playerId;
    public final long seed;
    public Phase phase = Phase.STARTER;
    public int floor = 0;
    public int rerollsLeft;
    public int rerollsUsed = 0;
    public List<String> starterOptions = new ArrayList<>();
    public List<NodeType> nodeChoices = new ArrayList<>();
    /** Property strings, e.g. {@code "zubat level=7"}. */
    public List<String> encounterOptions = new ArrayList<>();
    /** Encounter waiting for a party slot while in {@link Phase#RELEASE}. */
    public String pendingEncounter = "";

    public RunState(UUID playerId, long seed, int rerollsLeft) {
        this.playerId = playerId;
        this.seed = seed;
        this.rerollsLeft = rerollsLeft;
    }

    public NbtCompound toNbt() {
        NbtCompound tag = new NbtCompound();
        tag.putUuid("player", playerId);
        tag.putLong("seed", seed);
        tag.putString("phase", phase.name());
        tag.putInt("floor", floor);
        tag.putInt("rerollsLeft", rerollsLeft);
        tag.putInt("rerollsUsed", rerollsUsed);
        tag.put("starterOptions", writeStrings(starterOptions));
        tag.put("nodeChoices", writeStrings(nodeChoices.stream().map(Enum::name).toList()));
        tag.put("encounterOptions", writeStrings(encounterOptions));
        tag.putString("pendingEncounter", pendingEncounter);
        return tag;
    }

    public static RunState fromNbt(NbtCompound tag) {
        RunState state = new RunState(tag.getUuid("player"), tag.getLong("seed"), tag.getInt("rerollsLeft"));
        state.phase = Phase.valueOf(tag.getString("phase"));
        state.floor = tag.getInt("floor");
        state.rerollsUsed = tag.getInt("rerollsUsed");
        state.starterOptions = readStrings(tag, "starterOptions");
        state.nodeChoices = new ArrayList<>(readStrings(tag, "nodeChoices").stream().map(NodeType::valueOf).toList());
        state.encounterOptions = readStrings(tag, "encounterOptions");
        state.pendingEncounter = tag.getString("pendingEncounter");
        return state;
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
