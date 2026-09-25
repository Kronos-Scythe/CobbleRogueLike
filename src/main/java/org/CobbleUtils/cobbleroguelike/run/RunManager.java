package org.CobbleUtils.cobbleroguelike.run;

import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.CobbleUtils.cobbleroguelike.Cobbleroguelike;
import org.CobbleUtils.cobbleroguelike.RogueConfig;
import org.CobbleUtils.cobbleroguelike.compat.CobblemonBridge;
import org.CobbleUtils.cobbleroguelike.run.RunState.NodeType;
import org.CobbleUtils.cobbleroguelike.run.RunState.Phase;
import org.CobbleUtils.cobbleroguelike.ui.RogueMenus;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Owns run lifecycles.
 *
 * <p>Isolation works by swapping the player's party: the real party is written to a journal
 * on disk, then removed, and the run is played with rogue-tagged Pokémon. Ending a run
 * deletes the rogue Pokémon and restores the journal. Only the party is swapped. The
 * player's inventory, position and PC are never touched.
 *
 * <p>Recovery on join follows these rules: a journal with no run file means a swap was
 * interrupted, so it is restored. A run file whose party holds an untagged Pokémon is
 * treated as corrupt and also restored. Restoring deduplicates by Pokémon UUID, so running
 * it twice never duplicates anything.
 */
public final class RunManager {

    private static RunManager instance;

    private final MinecraftServer server;
    private final RunStorage storage;
    private final Map<UUID, RunState> active = new HashMap<>();

    private RunManager(MinecraftServer server) {
        this.server = server;
        this.storage = new RunStorage(server);
    }

    public static void onServerStarted(MinecraftServer server) {
        instance = new RunManager(server);
    }

    public static void onServerStopped() {
        instance = null;
    }

    public static RunManager get() {
        if (instance == null) {
            throw new IllegalStateException("RunManager used before the server started");
        }
        return instance;
    }

    /** Null-safe check for guards that may fire before the server has started. */
    public static boolean isInRun(ServerPlayerEntity player) {
        return instance != null && instance.active.containsKey(player.getUuid());
    }

    public RunState state(ServerPlayerEntity player) {
        return active.get(player.getUuid());
    }

    // ---------------------------------------------------------------- connection lifecycle

    public void onJoin(ServerPlayerEntity player) {
        UUID id = player.getUuid();
        boolean journal = storage.hasJournal(id);
        boolean run = storage.hasRun(id);
        try {
            if (journal && !run) {
                Cobbleroguelike.LOGGER.warn("Found an interrupted rogue swap for {}, restoring their party", player.getName().getString());
                restore(player);
                message(player, "Your last rogue run was interrupted. Your party has been restored.", Formatting.YELLOW);
            } else if (journal) {
                RunState state = storage.readRun(id);
                boolean untagged = CobblemonBridge.partyMembers(player).stream().anyMatch(p -> !CobblemonBridge.isRogue(p));
                if (untagged) {
                    Cobbleroguelike.LOGGER.warn("Rogue run for {} had untagged Pokémon in its party, ending it", player.getName().getString());
                    restore(player);
                    message(player, "Your rogue run could not be resumed and has ended. Your party has been restored.", Formatting.YELLOW);
                } else {
                    active.put(id, state);
                    message(player, "You have a rogue run in progress. Use /rogue to continue.", Formatting.AQUA);
                }
            } else if (run) {
                Cobbleroguelike.LOGGER.warn("Found a rogue run for {} without a journal, discarding it", player.getName().getString());
                storage.deleteRun(id);
            }
        } catch (IOException | RuntimeException e) {
            Cobbleroguelike.LOGGER.error("Failed to recover rogue run for {}", player.getName().getString(), e);
            message(player, "Could not load your rogue run data. Ask an admin to check the server log.", Formatting.RED);
        }
        purgeStrayRogueMons(player);
    }

    public void onDisconnect(ServerPlayerEntity player) {
        // The run and journal are already on disk, so the player can resume later.
        active.remove(player.getUuid());
    }

    // ---------------------------------------------------------------- start / end

    public void start(ServerPlayerEntity player) {
        UUID id = player.getUuid();
        if (active.containsKey(id)) {
            openCurrent(player);
            return;
        }
        if (CobblemonBridge.isInBattle(player)) {
            message(player, "You can't start a run while in a battle.", Formatting.RED);
            return;
        }
        if (storage.hasJournal(id)) {
            message(player, "Your previous run hasn't been cleaned up yet. Rejoin or ask an admin.", Formatting.RED);
            return;
        }

        DynamicRegistryManager registries = player.getRegistryManager();
        List<Pokemon> realParty = CobblemonBridge.partyMembers(player);
        NbtList saved = new NbtList();
        for (Pokemon pokemon : realParty) {
            saved.add(CobblemonBridge.save(pokemon, registries));
        }
        NbtCompound journal = new NbtCompound();
        journal.putInt("version", 1);
        journal.put("party", saved);

        // Journal first: nothing is touched until the real party is safely on disk.
        try {
            storage.writeJournal(id, journal);
        } catch (IOException e) {
            Cobbleroguelike.LOGGER.error("Failed to write rogue journal for {}", player.getName().getString(), e);
            message(player, "Could not start a run (failed to save your party).", Formatting.RED);
            return;
        }

        try {
            for (Pokemon pokemon : realParty) {
                CobblemonBridge.recall(pokemon);
                CobblemonBridge.party(player).remove(pokemon);
            }
            RogueConfig config = RogueConfig.get();
            RunState state = new RunState(id, new Random().nextLong(), config.freeRerolls);
            state.starterOptions = rollStarters(state);
            storage.writeRun(state);
            active.put(id, state);
        } catch (IOException | RuntimeException e) {
            Cobbleroguelike.LOGGER.error("Failed to start rogue run for {}, rolling back", player.getName().getString(), e);
            try {
                restore(player);
            } catch (IOException | RuntimeException rollback) {
                Cobbleroguelike.LOGGER.error("Rollback failed for {}; journal kept on disk", player.getName().getString(), rollback);
            }
            message(player, "Could not start a run. Your party is unchanged.", Formatting.RED);
            return;
        }

        message(player, "Your party is safely stored. Choose your partner!", Formatting.GREEN);
        openCurrent(player);
    }

    /** Ends the run: deletes the rogue party and gives the player back their real one. */
    public void end(ServerPlayerEntity player, String reason) {
        if (CobblemonBridge.isInBattle(player)) {
            message(player, "Finish your battle before ending the run.", Formatting.RED);
            return;
        }
        try {
            restore(player);
            message(player, reason + " Your party has been restored.", Formatting.GOLD);
        } catch (IOException | RuntimeException e) {
            Cobbleroguelike.LOGGER.error("Failed to end rogue run for {}", player.getName().getString(), e);
            message(player, "Failed to restore your party. Nothing was lost; ask an admin to check the log.", Formatting.RED);
        }
    }

    /**
     * Restores the journaled party. It is safe to call repeatedly.
     * <ul>
     *     <li>Rogue-tagged Pokémon in the party are deleted.</li>
     *     <li>Untagged Pokémon that are not from the journal (for example, received in a trade)
     *     are moved to the PC, never deleted.</li>
     *     <li>Journal entries whose UUID is already in the party or PC are not added again.</li>
     * </ul>
     * The journal is deleted last.
     */
    private void restore(ServerPlayerEntity player) throws IOException {
        UUID id = player.getUuid();
        active.remove(id);
        DynamicRegistryManager registries = player.getRegistryManager();

        List<Pokemon> journalMons = new ArrayList<>();
        if (storage.hasJournal(id)) {
            NbtList saved = storage.readJournal(id).getList("party", NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < saved.size(); i++) {
                journalMons.add(CobblemonBridge.load(saved.getCompound(i), registries));
            }
        }
        Set<UUID> journalIds = new HashSet<>();
        journalMons.forEach(p -> journalIds.add(p.getUuid()));

        for (Pokemon pokemon : CobblemonBridge.partyMembers(player)) {
            if (journalIds.contains(pokemon.getUuid())) {
                continue;
            }
            CobblemonBridge.recall(pokemon);
            CobblemonBridge.party(player).remove(pokemon);
            if (!CobblemonBridge.isRogue(pokemon)) {
                CobblemonBridge.pc(player).add(pokemon);
            }
        }

        Set<UUID> present = new HashSet<>();
        CobblemonBridge.partyMembers(player).forEach(p -> present.add(p.getUuid()));
        CobblemonBridge.pcMembers(player).forEach(p -> present.add(p.getUuid()));
        for (Pokemon pokemon : journalMons) {
            if (!present.contains(pokemon.getUuid())) {
                CobblemonBridge.party(player).add(pokemon);
            }
        }

        storage.deleteRun(id);
        storage.deleteJournal(id);
    }

    /** Deletes rogue Pokémon found anywhere they should not be. Returns how many were removed. */
    public int purgeStrayRogueMons(ServerPlayerEntity player) {
        int removed = 0;
        if (!active.containsKey(player.getUuid())) {
            for (Pokemon pokemon : CobblemonBridge.partyMembers(player)) {
                if (CobblemonBridge.isRogue(pokemon)) {
                    CobblemonBridge.recall(pokemon);
                    CobblemonBridge.party(player).remove(pokemon);
                    removed++;
                }
            }
        }
        for (Pokemon pokemon : CobblemonBridge.pcMembers(player)) {
            if (CobblemonBridge.isRogue(pokemon)) {
                CobblemonBridge.pc(player).remove(pokemon);
                removed++;
            }
        }
        if (removed > 0) {
            Cobbleroguelike.LOGGER.warn("Removed {} stray rogue Pokémon from {}", removed, player.getName().getString());
        }
        return removed;
    }

    // ---------------------------------------------------------------- run flow

    public void openCurrent(ServerPlayerEntity player) {
        RunState state = active.get(player.getUuid());
        if (state == null) {
            RogueMenus.hub(player, false);
            return;
        }
        switch (state.phase) {
            case STARTER -> RogueMenus.starter(player, state);
            case CHOOSE_NODE -> RogueMenus.path(player, state);
            case ENCOUNTER -> RogueMenus.encounter(player, state);
            case RELEASE -> RogueMenus.release(player, state);
        }
    }

    public void chooseStarter(ServerPlayerEntity player, int index) {
        RunState state = requirePhase(player, Phase.STARTER);
        if (state == null || index < 0 || index >= state.starterOptions.size()) {
            return;
        }
        String species = state.starterOptions.get(index);
        CobblemonBridge.party(player).add(CobblemonBridge.createRogue(species + " level=" + RogueConfig.get().starterLevel));
        state.starterOptions.clear();
        advanceFloor(state);
        save(player, state);
        openCurrent(player);
    }

    public void rerollStarters(ServerPlayerEntity player) {
        RunState state = requirePhase(player, Phase.STARTER);
        if (state == null || state.rerollsLeft <= 0) {
            return;
        }
        state.rerollsLeft--;
        state.rerollsUsed++;
        state.starterOptions = rollStarters(state);
        save(player, state);
        openCurrent(player);
    }

    public void chooseNode(ServerPlayerEntity player, int index) {
        RunState state = requirePhase(player, Phase.CHOOSE_NODE);
        if (state == null || index < 0 || index >= state.nodeChoices.size()) {
            return;
        }
        switch (state.nodeChoices.get(index)) {
            case ROUTE -> {
                state.encounterOptions = rollEncounters(state);
                state.phase = Phase.ENCOUNTER;
            }
            case REST -> {
                CobblemonBridge.healParty(player);
                message(player, "Your team rested and is fully healed.", Formatting.GREEN);
                advanceFloor(state);
            }
        }
        save(player, state);
        openCurrent(player);
    }

    /** Picks one of the route's Pokémon, like Emerald Rogue's one catch per route. {@code -1} skips. */
    public void chooseEncounter(ServerPlayerEntity player, int index) {
        RunState state = requirePhase(player, Phase.ENCOUNTER);
        if (state == null) {
            return;
        }
        if (index >= 0 && index < state.encounterOptions.size()) {
            String picked = state.encounterOptions.get(index);
            if (CobblemonBridge.partyMembers(player).size() >= 6) {
                state.pendingEncounter = picked;
                state.phase = Phase.RELEASE;
                save(player, state);
                openCurrent(player);
                return;
            }
            CobblemonBridge.party(player).add(CobblemonBridge.createRogue(picked));
        }
        state.encounterOptions.clear();
        advanceFloor(state);
        save(player, state);
        openCurrent(player);
    }

    /** Releases a party member to make room for the pending encounter. {@code -1} keeps the party as is. */
    public void releaseForPending(ServerPlayerEntity player, int partyIndex) {
        RunState state = requirePhase(player, Phase.RELEASE);
        if (state == null) {
            return;
        }
        List<Pokemon> party = CobblemonBridge.partyMembers(player);
        if (partyIndex >= 0 && partyIndex < party.size()) {
            Pokemon released = party.get(partyIndex);
            CobblemonBridge.recall(released);
            CobblemonBridge.party(player).remove(released);
            CobblemonBridge.party(player).add(CobblemonBridge.createRogue(state.pendingEncounter));
        }
        state.pendingEncounter = "";
        state.encounterOptions.clear();
        advanceFloor(state);
        save(player, state);
        openCurrent(player);
    }

    // ---------------------------------------------------------------- helpers

    private RunState requirePhase(ServerPlayerEntity player, Phase phase) {
        RunState state = active.get(player.getUuid());
        if (state == null || state.phase != phase) {
            // Stale menu click (e.g. double click); just show the current screen.
            openCurrent(player);
            return null;
        }
        return state;
    }

    private void advanceFloor(RunState state) {
        state.floor++;
        state.phase = Phase.CHOOSE_NODE;
        state.nodeChoices = rollNodes(state);
    }

    private void save(ServerPlayerEntity player, RunState state) {
        try {
            storage.writeRun(state);
        } catch (IOException e) {
            Cobbleroguelike.LOGGER.error("Failed to save rogue run for {}", player.getName().getString(), e);
        }
    }

    /** Seeded per floor, so relogging or reopening a menu can't reroll choices. */
    private static Random rng(RunState state, int salt) {
        return new Random(state.seed * 31L + state.floor * 1_000_003L + state.rerollsUsed * 7919L + salt);
    }

    private static List<String> rollStarters(RunState state) {
        RogueConfig config = RogueConfig.get();
        return pickDistinct(config.starterPool, config.starterOptions, rng(state, 1));
    }

    private static List<String> rollEncounters(RunState state) {
        RogueConfig config = RogueConfig.get();
        int level = Math.min(config.maxEncounterLevel,
                config.starterLevel + state.floor * config.encounterLevelPerFloor);
        List<String> result = new ArrayList<>();
        for (String species : pickDistinct(config.encounterPool, config.encounterOptions, rng(state, 2))) {
            result.add(species + " level=" + level);
        }
        return result;
    }

    private static List<NodeType> rollNodes(RunState state) {
        Random random = rng(state, 3);
        List<NodeType> nodes = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            nodes.add(random.nextDouble() < RogueConfig.get().restChance ? NodeType.REST : NodeType.ROUTE);
        }
        if (!nodes.contains(NodeType.ROUTE)) {
            nodes.set(random.nextInt(nodes.size()), NodeType.ROUTE);
        }
        return nodes;
    }

    private static List<String> pickDistinct(List<String> pool, int count, Random random) {
        List<String> valid = new ArrayList<>(pool.stream().filter(CobblemonBridge::speciesExists).toList());
        List<String> result = new ArrayList<>();
        while (result.size() < count && !valid.isEmpty()) {
            result.add(valid.remove(random.nextInt(valid.size())));
        }
        return result;
    }

    public static void message(ServerPlayerEntity player, String text, Formatting color) {
        player.sendMessage(Text.literal(text).formatted(color), false);
    }
}
