package org.CobbleUtils.cobbleroguelike.run;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Progress of one player's run. Persisted after every change. */
public final class RunState {

    public enum Phase { CHOOSE_NODE, ENCOUNTER, RELEASE, BATTLE }

    public enum NodeType { ROUTE, REST, TRAINER, GYM, ELITE, CHAMPION, LEGENDARY }

    /** The run's owner (the host in co-op). The run file is stored under this id. */
    public final UUID playerId;
    /** Co-op partner, or null for a solo run. The partner's run file just links to the host's. */
    public UUID partnerId = null;
    /** Co-op route picks: player to option index (-1 = skipped). */
    public Map<UUID, Integer> coopPicks = new HashMap<>();
    /** Co-op: Pokémon waiting for a party slot, per player (their release screen). */
    public Map<UUID, String> coopPending = new HashMap<>();
    /** Co-op: players who pressed Ready for the upcoming battle (not saved). */
    public final Set<UUID> ready = new HashSet<>();
    public final long seed;
    public Phase phase = Phase.CHOOSE_NODE;
    public int floor = 0;
    public int badges = 0;
    /** Run currency. Earned in battles, spent in the shop, and gone when the run ends. */
    public int money = 0;
    /** Run bag: item id to count. It is purely virtual and never touches the real inventory. */
    public Map<String, Integer> bag = new LinkedHashMap<>();
    /** Mega Showdown gimmicks unlocked for this run: mega, z, dynamax, tera. */
    public Set<String> gimmicks = new LinkedHashSet<>();
    public List<String> usedGymTypes = new ArrayList<>();
    /** Current rogue biome id; rolled at the start and after every gym. */
    public String biome = "";
    public List<String> usedBiomes = new ArrayList<>();
    /** Badge count whose guaranteed Legendary card was already offered (-1 = none yet). */
    public int legendaryOfferedAt = -1;
    /** Elite Four members beaten, and the floor where the Elite Four stretch began (-1 = not yet). */
    /** Run modifiers (see {@link Modifiers}). */
    public Set<String> modifiers = new LinkedHashSet<>();
    /** Saved & left: the run party is stored here and the player has their real party back. */
    public boolean suspended = false;
    public NbtList suspendedParty = new NbtList();
    public int eliteWins = 0;
    public int eliteStartFloor = -1;
    public List<String> usedEliteTypes = new ArrayList<>();
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
    /** Co-op: the second opponent (partner trainer, or the legendary's companion). */
    public String battleName2 = "";
    public List<String> battleTeam2 = new ArrayList<>();
    public int battleSkill = 0;
    public boolean battleDoubles = false;
    /** Gimmick the boss will use ("mega", "tera" or empty; Mega Showdown only). */
    public String battleGimmick = "";
    /** Team style of the upcoming boss, e.g. "Rain" or "Trick Room" (empty for a plain team). */
    public String battleArchetype = "";
    /** Boss prep: counter Pokémon offered by "Draft a counter" (property strings with level). */
    public List<String> draftOptions = new ArrayList<>();
    /** Boss prep actions already used, as "uuid:train", "uuid:draft", "uuid:heal". */
    public Set<String> prepUsed = new HashSet<>();

    public RunState(UUID playerId, long seed) {
        this.playerId = playerId;
        this.seed = seed;
    }

    public boolean isCoop() {
        return partnerId != null;
    }

    /** Everyone in the run: the owner, plus the partner in co-op. */
    public List<UUID> members() {
        return partnerId == null ? List.of(playerId) : List.of(playerId, partnerId);
    }

    /** The other co-op player, or null. */
    public UUID other(UUID id) {
        if (partnerId == null) {
            return null;
        }
        return id.equals(playerId) ? partnerId : playerId;
    }

    public int bagCount(String itemId) {
        return bag.getOrDefault(itemId, 0);
    }

    public void addToBag(String itemId, int count) {
        if (count > 0) {
            bag.merge(itemId, count, Integer::sum);
        }
    }

    /** Removes up to {@code count}; returns how many were removed. */
    public int takeFromBag(String itemId, int count) {
        int have = bagCount(itemId);
        int taken = Math.min(have, Math.max(0, count));
        if (have - taken <= 0) {
            bag.remove(itemId);
        } else {
            bag.put(itemId, have - taken);
        }
        return taken;
    }

    public void clearBattle() {
        battleKind = NodeType.TRAINER;
        battleName = "";
        battleType = "";
        battleTeam = new ArrayList<>();
        battleName2 = "";
        battleTeam2 = new ArrayList<>();
        battleSkill = 0;
        battleDoubles = false;
        battleGimmick = "";
        battleArchetype = "";
        draftOptions = new ArrayList<>();
        prepUsed = new HashSet<>();
    }

    public NbtCompound toNbt() {
        NbtCompound tag = new NbtCompound();
        tag.putUuid("player", playerId);
        tag.putLong("seed", seed);
        tag.putString("phase", phase.name());
        tag.putInt("floor", floor);
        tag.putInt("badges", badges);
        tag.putInt("money", money);
        NbtCompound bagTag = new NbtCompound();
        bag.forEach(bagTag::putInt);
        tag.put("bag", bagTag);
        tag.put("gimmicks", writeStrings(new ArrayList<>(gimmicks)));
        tag.put("usedGymTypes", writeStrings(usedGymTypes));
        tag.putString("biome", biome);
        tag.put("usedBiomes", writeStrings(usedBiomes));
        tag.putInt("legendaryOfferedAt", legendaryOfferedAt);
        tag.putInt("eliteWins", eliteWins);
        tag.put("modifiers", writeStrings(new ArrayList<>(modifiers)));
        tag.putBoolean("suspended", suspended);
        tag.put("suspendedParty", suspendedParty.copy());
        tag.putInt("eliteStartFloor", eliteStartFloor);
        tag.put("usedEliteTypes", writeStrings(usedEliteTypes));
        tag.put("nodeChoices", writeStrings(nodeChoices.stream().map(Enum::name).toList()));
        tag.put("encounterOptions", writeStrings(encounterOptions));
        tag.putString("pendingEncounter", pendingEncounter);
        tag.putString("battleKind", battleKind.name());
        tag.putString("battleName", battleName);
        tag.putString("battleType", battleType);
        tag.put("battleTeam", writeStrings(battleTeam));
        tag.putString("battleName2", battleName2);
        tag.put("battleTeam2", writeStrings(battleTeam2));
        if (partnerId != null) {
            tag.putUuid("partner", partnerId);
        }
        NbtCompound picks = new NbtCompound();
        coopPicks.forEach((id, index) -> picks.putInt(id.toString(), index));
        tag.put("coopPicks", picks);
        NbtCompound pending = new NbtCompound();
        coopPending.forEach((id, props) -> pending.putString(id.toString(), props));
        tag.put("coopPending", pending);
        tag.putInt("battleSkill", battleSkill);
        tag.putBoolean("battleDoubles", battleDoubles);
        tag.putString("battleGimmick", battleGimmick);
        tag.putString("battleArchetype", battleArchetype);
        tag.put("draftOptions", writeStrings(draftOptions));
        tag.put("prepUsed", writeStrings(new ArrayList<>(prepUsed)));
        return tag;
    }

    public static RunState fromNbt(NbtCompound tag) {
        RunState state = new RunState(tag.getUuid("player"), tag.getLong("seed"));
        // Tolerate data from older builds: unknown values fall back instead of failing the load.
        state.phase = parse(Phase.class, tag.getString("phase"), Phase.CHOOSE_NODE);
        state.floor = tag.getInt("floor");
        state.badges = tag.getInt("badges");
        state.money = tag.getInt("money");
        NbtCompound bagTag = tag.getCompound("bag");
        for (String key : bagTag.getKeys()) {
            int count = bagTag.getInt(key);
            if (count > 0) {
                state.bag.put(key, count);
            }
        }
        state.gimmicks.addAll(readStrings(tag, "gimmicks"));
        state.usedGymTypes = readStrings(tag, "usedGymTypes");
        state.biome = tag.getString("biome");
        state.usedBiomes = readStrings(tag, "usedBiomes");
        state.legendaryOfferedAt = tag.contains("legendaryOfferedAt") ? tag.getInt("legendaryOfferedAt") : -1;
        state.eliteWins = tag.getInt("eliteWins");
        state.modifiers.addAll(readStrings(tag, "modifiers"));
        state.suspended = tag.getBoolean("suspended");
        state.suspendedParty = tag.getList("suspendedParty", NbtElement.COMPOUND_TYPE);
        state.eliteStartFloor = tag.contains("eliteStartFloor") ? tag.getInt("eliteStartFloor") : -1;
        state.usedEliteTypes = readStrings(tag, "usedEliteTypes");
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
        state.battleName2 = tag.getString("battleName2");
        state.battleTeam2 = readStrings(tag, "battleTeam2");
        if (tag.containsUuid("partner")) {
            state.partnerId = tag.getUuid("partner");
        }
        NbtCompound picks = tag.getCompound("coopPicks");
        for (String key : picks.getKeys()) {
            state.coopPicks.put(UUID.fromString(key), picks.getInt(key));
        }
        NbtCompound pending = tag.getCompound("coopPending");
        for (String key : pending.getKeys()) {
            state.coopPending.put(UUID.fromString(key), pending.getString(key));
        }
        state.battleSkill = tag.getInt("battleSkill");
        state.battleDoubles = tag.getBoolean("battleDoubles");
        state.battleGimmick = tag.getString("battleGimmick");
        state.battleArchetype = tag.getString("battleArchetype");
        state.draftOptions = readStrings(tag, "draftOptions");
        state.prepUsed.addAll(readStrings(tag, "prepUsed"));
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
