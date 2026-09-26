package org.CobbleUtils.cobbleroguelike.run;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.CobbleUtils.cobbleroguelike.Cobbleroguelike;
import org.CobbleUtils.cobbleroguelike.RogueConfig;
import org.CobbleUtils.cobbleroguelike.compat.CobblemonBattles;
import org.CobbleUtils.cobbleroguelike.compat.CobblemonBridge;
import org.CobbleUtils.cobbleroguelike.run.RunState.NodeType;
import org.CobbleUtils.cobbleroguelike.run.RunState.Phase;
import org.CobbleUtils.cobbleroguelike.shop.ShopCatalog;
import org.CobbleUtils.cobbleroguelike.ui.MenuScreenHandler;
import org.CobbleUtils.cobbleroguelike.ui.RogueMenus;
import org.CobbleUtils.cobbleroguelike.util.Scheduler;

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
    /** Players whose battle is being force-stopped by /rogue endbattle; their run ends right after. */
    private final Set<UUID> endingByCommand = new HashSet<>();
    /** Modifiers picked on the setup screen, applied when the run begins. */
    private final Map<UUID, Set<String>> pendingModifiers = new HashMap<>();

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
            } else if (journal && isSuspended(readRunOrNull(player))) {
                // A save & leave (or a continue) was interrupted: finish putting the run away.
                Cobbleroguelike.LOGGER.warn("Finishing an interrupted save & leave for {}", player.getName().getString());
                putAway(player);
                message(player, "Your saved rogue run is safe. Use /rogue to continue it.", Formatting.AQUA);
            } else if (journal) {
                RunState state = readRunOrNull(player);
                List<Pokemon> party = CobblemonBridge.partyMembers(player);
                boolean untagged = party.stream().anyMatch(p -> !CobblemonBridge.isRogue(p));
                if (state == null || untagged || party.isEmpty()) {
                    Cobbleroguelike.LOGGER.warn("Rogue run for {} could not be resumed (unreadable: {}, untagged party: {}), ending it",
                            player.getName().getString(), state == null, untagged);
                    restore(player);
                    message(player, "Your rogue run could not be resumed and has ended. Your party has been restored.", Formatting.YELLOW);
                } else {
                    state = liveInstance(state);
                    repair(state);
                    active.put(id, state);
                    if (state.isCoop()) {
                        tell(state, player.getName().getString() + " is back in the co-op run.", Formatting.AQUA);
                    }
                    message(player, "You have a rogue run in progress. Use /rogue to continue.", Formatting.AQUA);
                }
            } else if (run) {
                if (isSuspended(readRunOrNull(player))) {
                    message(player, "You have a saved rogue run. Use /rogue to continue it.", Formatting.AQUA);
                } else {
                    Cobbleroguelike.LOGGER.warn("Found a rogue run for {} without a journal, discarding it", player.getName().getString());
                    storage.deleteRun(id);
                }
            }
        } catch (IOException | RuntimeException e) {
            Cobbleroguelike.LOGGER.error("Failed to recover rogue run for {}", player.getName().getString(), e);
            message(player, "Could not load your rogue run data. Ask an admin to check the server log.", Formatting.RED);
        }
        purgeStrayRogueMons(player);
    }

    /** Fixes up states loaded from older builds so the menus always have something to show. */
    private void repair(RunState state) {
        if (Scaling.championUnlocked(state.badges) && state.eliteStartFloor < 0) {
            state.eliteStartFloor = Math.max(0, state.floor - 1); // runs from before the Elite Four existed
        }
        if (state.biome.isEmpty()) {
            state.biome = Biomes.roll(state, rng(state, 5));
        }
        if (state.phase == Phase.BATTLE && state.battleTeam.isEmpty()) {
            state.phase = Phase.CHOOSE_NODE;
        }
        if (state.phase == Phase.ENCOUNTER && state.encounterOptions.isEmpty()) {
            state.phase = Phase.CHOOSE_NODE;
        }
        if (state.phase == Phase.RELEASE && state.pendingEncounter.isEmpty()) {
            state.phase = Phase.CHOOSE_NODE;
        }
        if (state.phase == Phase.CHOOSE_NODE && state.nodeChoices.isEmpty()) {
            advanceFloor(state);
        }
    }

    private RunState readRunOrNull(ServerPlayerEntity player) {
        try {
            return storage.readRun(player.getUuid());
        } catch (IOException | RuntimeException e) {
            Cobbleroguelike.LOGGER.error("Failed to read rogue run for {}", player.getName().getString(), e);
            return null;
        }
    }

    /**
     * Force-cleans a player's run data: ends any run, deletes rogue Pokémon and gives back the
     * journaled party. Safe to call at any time (restore is idempotent). Returns false only if
     * cleanup couldn't happen (e.g. the player is mid-battle or the restore failed).
     */
    public boolean clean(ServerPlayerEntity player) {
        UUID id = player.getUuid();
        if (CobblemonBridge.isInBattle(player)) {
            message(player, "Finish your battle before cleaning up.", Formatting.RED);
            return false;
        }
        RunState current = active.get(id);
        if (current != null && current.isCoop()) {
            finish(player, "You ended the co-op run.", false);
            return true;
        }
        if (!active.containsKey(id) && !storage.hasJournal(id) && isSuspended(readRunOrNull(player))) {
            purgeStrayRogueMons(player);
            message(player, "Your saved run is safe, and there's nothing else to clean up.", Formatting.GREEN);
            return true;
        }
        if (!active.containsKey(id) && !storage.hasJournal(id) && !storage.hasRun(id)) {
            int removed = purgeStrayRogueMons(player);
            message(player, removed > 0
                    ? "Removed " + removed + " leftover run Pokémon. Nothing else to clean up."
                    : "Nothing to clean up.", Formatting.GREEN);
            return true;
        }
        try {
            restore(player);
            purgeStrayRogueMons(player);
            message(player, "Old run data cleaned up. Your party has been restored.", Formatting.GREEN);
            return true;
        } catch (IOException | RuntimeException e) {
            Cobbleroguelike.LOGGER.error("Failed to clean rogue run data for {}", player.getName().getString(), e);
            message(player, "Cleanup failed. Your saved party is still on disk; ask an admin to check the log.", Formatting.RED);
            return false;
        }
    }

    public void onDisconnect(ServerPlayerEntity player) {
        // The run and journal are already on disk, so the player can resume later.
        RunState state = active.remove(player.getUuid());
        if (state != null) {
            state.ready.remove(player.getUuid());
            if (state.isCoop()) {
                tell(state, player.getName().getString() + " left. Battles wait until they're back.", Formatting.YELLOW);
                maybeFinishCoopRoute(state);
            }
        }
        leaveLobby(player);
        invites.remove(player.getUuid());
    }

    // ---------------------------------------------------------------- start / end

    /** Opens the partner picker. Nothing is swapped until a Pokémon is chosen. */
    public void start(ServerPlayerEntity player) {
        if (active.containsKey(player.getUuid())) {
            openCurrent(player);
            return;
        }
        if (savedRun(player) != null) {
            RogueMenus.hub(player, false);
            return;
        }
        // Leftover data from a run that couldn't be resumed: clean it up first.
        if (storage.hasJournal(player.getUuid()) && !clean(player)) {
            return;
        }
        RogueMenus.setup(player);
    }

    public Set<String> pendingModifiers(ServerPlayerEntity player) {
        return pendingModifiers.computeIfAbsent(player.getUuid(), id -> new java.util.LinkedHashSet<>());
    }

    public void toggleModifier(ServerPlayerEntity player, String id) {
        Set<String> modifiers = pendingModifiers(player);
        if (!modifiers.remove(id)) {
            modifiers.add(id);
        }
        RogueMenus.setup(player);
    }

    /**
     * Starts a run with a copy of one of the player's own Pokémon (party or PC) as the only
     * partner. The original stays in the journal or PC and never gains EXP or changes.
     */
    public void beginRun(ServerPlayerEntity player, UUID partnerId) {
        UUID id = player.getUuid();
        if (active.containsKey(id)) {
            openCurrent(player);
            return;
        }
        Lobby lobby = lobbies.get(id);
        if (lobby != null) {
            lobbyPick(player, lobby, partnerId);
            return;
        }
        if (CobblemonBridge.isInBattle(player)) {
            message(player, "You can't start a run while in a battle.", Formatting.RED);
            return;
        }
        if (savedRun(player) != null) {
            // Never overwrite a saved run.
            RogueMenus.hub(player, false);
            return;
        }
        if (storage.hasJournal(id)) {
            // The picker was opened before a stale run was found; clean it up and pick again.
            if (clean(player)) {
                RogueMenus.partnerPicker(player, 0);
            }
            return;
        }
        Pokemon original = CobblemonBridge.findOwned(player, partnerId);
        if (original == null || CobblemonBridge.isRogue(original)) {
            message(player, "That Pokémon isn't available anymore.", Formatting.RED);
            RogueMenus.partnerPicker(player, 0);
            return;
        }

        DynamicRegistryManager registries = player.getRegistryManager();
        RogueConfig config = RogueConfig.get();
        Pokemon partner = CobblemonBridge.createRogueCopy(original, registries,
                config.resetStarterLevel ? config.starterLevel : 0);

        // Journal first: nothing is touched until the real party is safely on disk.
        if (!writeJournal(player)) {
            message(player, "Could not start a run (failed to save your party).", Formatting.RED);
            return;
        }

        try {
            swapInPartner(player, partner);
            RunState state = new RunState(id, new Random().nextLong());
            state.money = RogueConfig.get().startingMoney;
            state.modifiers.addAll(pendingModifiers.getOrDefault(id, Set.of()));
            pendingModifiers.remove(id);
            state.biome = Biomes.roll(state, new Random(state.seed ^ 0x5EEDB10EL));
            advanceFloor(state);
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

        message(player, "Your party is safely stored. Good luck, you and your "
                + partner.getSpecies().getName() + "!", Formatting.GREEN);
        openCurrent(player);
    }

    /** Journals the player's real party. Must succeed before anything is swapped. */
    private boolean writeJournal(ServerPlayerEntity player) {
        DynamicRegistryManager registries = player.getRegistryManager();
        NbtList saved = new NbtList();
        for (Pokemon pokemon : CobblemonBridge.partyMembers(player)) {
            saved.add(CobblemonBridge.save(pokemon, registries));
        }
        NbtCompound journal = new NbtCompound();
        journal.putInt("version", 1);
        journal.put("party", saved);
        try {
            storage.writeJournal(player.getUuid(), journal);
            return true;
        } catch (IOException e) {
            Cobbleroguelike.LOGGER.error("Failed to write rogue journal for {}", player.getName().getString(), e);
            return false;
        }
    }

    /** Replaces the (journaled) real party with the run partner. */
    private void swapInPartner(ServerPlayerEntity player, Pokemon partner) {
        for (Pokemon pokemon : CobblemonBridge.partyMembers(player)) {
            CobblemonBridge.recall(pokemon);
            CobblemonBridge.party(player).remove(pokemon);
        }
        CobblemonBridge.party(player).add(partner);
    }

    /** Ends the run: deletes the rogue party and gives the player back their real one. */
    public void end(ServerPlayerEntity player, String reason) {
        if (CobblemonBridge.isInBattle(player)) {
            message(player, "Finish your battle before ending the run.", Formatting.RED);
            return;
        }
        finish(player, reason, false);
    }

    /** Ends the run with a Rogue Token payout for the progress made, then restores the real party. */
    private void finish(ServerPlayerEntity player, String reason, boolean won) {
        RunState state = active.get(player.getUuid());
        if (state != null && state.isCoop()) {
            finishCoop(state, reason, won);
            return;
        }
        try {
            restore(player);
            message(player, reason + " Your party has been restored.", Formatting.GOLD);
            if (state != null) {
                payOut(player, state, won);
            }
        } catch (IOException | RuntimeException e) {
            Cobbleroguelike.LOGGER.error("Failed to end rogue run for {}", player.getName().getString(), e);
            message(player, "Failed to restore your party. Nothing was lost; ask an admin to check the log.", Formatting.RED);
        }
    }

    private void payOut(ServerPlayerEntity player, RunState state, boolean won) {
        payOut(player.getUuid(), player, state, won);
    }

    /** {@code player} may be null (offline co-op member): tokens are still paid. */
    private void payOut(UUID id, ServerPlayerEntity player, RunState state, boolean won) {
        RogueConfig config = RogueConfig.get();
        int floorsCleared = Math.max(0, state.floor - 1);
        int base = floorsCleared * config.tokensPerFloor + state.badges * config.tokensPerBadge
                + (won ? config.championTokenBonus : 0);
        int tokens = (int) Math.round(base * (1.0 + Modifiers.totalBonus(state.modifiers)));
        NbtCompound profile = storage.readProfile(id);
        profile.putInt("tokens", profile.getInt("tokens") + tokens);
        profile.putInt("runs", profile.getInt("runs") + 1);
        profile.putInt("wins", profile.getInt("wins") + (won ? 1 : 0));
        profile.putInt("bestFloor", Math.max(profile.getInt("bestFloor"), state.floor));
        profile.putInt("bestBadges", Math.max(profile.getInt("bestBadges"), state.badges));
        saveProfile(id, profile);
        if (player != null) {
            message(player, "+" + tokens + " Rogue Tokens (" + profile.getInt("tokens")
                    + " total). Spend them in the Rogue Shop.", Formatting.LIGHT_PURPLE);
        }
    }

    /** The player's persistent profile: tokens, runs, wins, bestFloor, bestBadges. */
    public NbtCompound profile(ServerPlayerEntity player) {
        return storage.readProfile(player.getUuid());
    }

    public int tokens(ServerPlayerEntity player) {
        return profile(player).getInt("tokens");
    }

    /** Adds (or with a negative amount, removes) tokens. Returns false if it would go below zero. */
    public boolean changeTokens(ServerPlayerEntity player, int amount) {
        NbtCompound profile = profile(player);
        int updated = profile.getInt("tokens") + amount;
        if (updated < 0) {
            return false;
        }
        profile.putInt("tokens", updated);
        return saveProfile(player.getUuid(), profile);
    }

    private boolean saveProfile(UUID id, NbtCompound profile) {
        try {
            storage.writeProfile(id, profile);
            return true;
        } catch (IOException e) {
            Cobbleroguelike.LOGGER.error("Failed to save rogue profile for {}", id, e);
            return false;
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
        restoreParty(player);
        storage.deleteRun(id);
        storage.deleteJournal(id);
    }

    /**
     * Swaps the journaled real party back in (see {@link #restore}) but keeps the run file. Used by
     * Save & leave, after the run party has been written into the run file.
     */
    private void putAway(ServerPlayerEntity player) throws IOException {
        active.remove(player.getUuid());
        restoreParty(player);
        storage.deleteJournal(player.getUuid());
    }

    private static boolean isSuspended(RunState state) {
        return state != null && state.suspended;
    }

    /** Party half of {@link #restore}: rogue Pokémon out, journal Pokémon in (deduplicated by UUID). */
    private void restoreParty(ServerPlayerEntity player) throws IOException {
        UUID id = player.getUuid();
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
    }

    // ---------------------------------------------------------------- save & leave / continue

    /** The player's saved (suspended) run, or null. */
    public RunState savedRun(ServerPlayerEntity player) {
        if (active.containsKey(player.getUuid()) || !storage.hasRun(player.getUuid())) {
            return null;
        }
        RunState state = readRunOrNull(player);
        return isSuspended(state) ? state : null;
    }

    /**
     * Save & leave. The run party is written into the run file first, then the rogue Pokémon are
     * removed and the real party is restored. If this is interrupted, login finishes it (see onJoin).
     */
    public void saveAndLeave(ServerPlayerEntity player) {
        RunState state = active.get(player.getUuid());
        if (state == null) {
            message(player, "You don't have an active run.", Formatting.RED);
            return;
        }
        if (state.isCoop()) {
            message(player, "Save & leave isn't available in co-op runs yet. You can log off; the run waits for you.", Formatting.YELLOW);
            return;
        }
        if (CobblemonBridge.isInBattle(player)) {
            message(player, "Finish your battle before saving.", Formatting.RED);
            return;
        }
        DynamicRegistryManager registries = player.getRegistryManager();
        NbtList saved = new NbtList();
        for (Pokemon pokemon : CobblemonBridge.partyMembers(player)) {
            if (CobblemonBridge.isRogue(pokemon)) {
                saved.add(CobblemonBridge.save(pokemon, registries));
            }
        }
        state.suspended = true;
        state.suspendedParty = saved;
        try {
            storage.writeRun(state);
        } catch (IOException e) {
            state.suspended = false;
            state.suspendedParty = new NbtList();
            Cobbleroguelike.LOGGER.error("Failed to save rogue run for {}", player.getName().getString(), e);
            message(player, "Couldn't save your run; it's still active.", Formatting.RED);
            return;
        }
        try {
            putAway(player);
            player.closeHandledScreen();
            message(player, "Run saved on floor " + state.floor + ". Your party is back. Use /rogue to continue any time.", Formatting.GREEN);
        } catch (IOException | RuntimeException e) {
            Cobbleroguelike.LOGGER.error("Failed to put away rogue run for {}", player.getName().getString(), e);
            message(player, "Your run is saved, but restoring your party failed. Rejoin to finish; nothing was lost.", Formatting.RED);
        }
    }

    /** Continues a saved run: the real party is journaled first, then the run party comes back. */
    public void continueSaved(ServerPlayerEntity player) {
        UUID id = player.getUuid();
        if (active.containsKey(id)) {
            openCurrent(player);
            return;
        }
        if (CobblemonBridge.isInBattle(player)) {
            message(player, "Finish your battle first.", Formatting.RED);
            return;
        }
        RunState state = savedRun(player);
        if (state == null) {
            message(player, "You don't have a saved run.", Formatting.RED);
            return;
        }
        if (storage.hasJournal(id)) {
            message(player, "Your run data needs a moment; rejoin and try again.", Formatting.RED);
            return;
        }
        DynamicRegistryManager registries = player.getRegistryManager();
        List<Pokemon> realParty = CobblemonBridge.partyMembers(player);
        NbtList journalParty = new NbtList();
        for (Pokemon pokemon : realParty) {
            journalParty.add(CobblemonBridge.save(pokemon, registries));
        }
        NbtCompound journal = new NbtCompound();
        journal.putInt("version", 1);
        journal.put("party", journalParty);
        try {
            storage.writeJournal(id, journal);
        } catch (IOException e) {
            Cobbleroguelike.LOGGER.error("Failed to write rogue journal for {}", player.getName().getString(), e);
            message(player, "Couldn't continue (failed to save your party).", Formatting.RED);
            return;
        }
        try {
            for (Pokemon pokemon : realParty) {
                CobblemonBridge.recall(pokemon);
                CobblemonBridge.party(player).remove(pokemon);
            }
            for (int i = 0; i < state.suspendedParty.size(); i++) {
                CobblemonBridge.party(player).add(CobblemonBridge.load(state.suspendedParty.getCompound(i), registries));
            }
            state.suspended = false;
            state.suspendedParty = new NbtList();
            repair(state);
            storage.writeRun(state);
            active.put(id, state);
        } catch (IOException | RuntimeException e) {
            Cobbleroguelike.LOGGER.error("Failed to continue rogue run for {}, rolling back", player.getName().getString(), e);
            try {
                putAway(player);
            } catch (IOException | RuntimeException rollback) {
                Cobbleroguelike.LOGGER.error("Rollback failed for {}; data kept on disk", player.getName().getString(), rollback);
            }
            message(player, "Couldn't continue your run. Your party is unchanged.", Formatting.RED);
            return;
        }
        message(player, "Welcome back! Your run continues on floor " + state.floor + ".", Formatting.GREEN);
        openCurrent(player);
    }

    /** Ends a saved run without continuing it; tokens are paid for its progress. */
    public void abandonSaved(ServerPlayerEntity player) {
        RunState state = savedRun(player);
        if (state == null) {
            message(player, "You don't have a saved run.", Formatting.RED);
            return;
        }
        try {
            storage.deleteRun(player.getUuid());
        } catch (IOException e) {
            Cobbleroguelike.LOGGER.error("Failed to delete saved run for {}", player.getName().getString(), e);
            message(player, "Couldn't end the saved run; try again.", Formatting.RED);
            return;
        }
        message(player, "You ended your saved run.", Formatting.GOLD);
        payOut(player, state, false);
        RogueMenus.hub(player, false);
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


    // ---------------------------------------------------------------- co-op

    private static final class Lobby {
        final UUID host;
        final UUID guest;
        UUID hostPick;
        UUID guestPick;

        Lobby(UUID host, UUID guest) {
            this.host = host;
            this.guest = guest;
        }

        UUID other(UUID id) {
            return id.equals(host) ? guest : host;
        }
    }

    private record Invite(UUID from, long expiresAt) {
    }

    /** Invitee to their pending invite. */
    private final Map<UUID, Invite> invites = new HashMap<>();
    /** Both lobby members map to their lobby (before the co-op run starts). */
    private final Map<UUID, Lobby> lobbies = new HashMap<>();

    private ServerPlayerEntity online(UUID id) {
        return id == null ? null : server.getPlayerManager().getPlayer(id);
    }

    /** Party size limit for a run: 3 per player in co-op (configurable), 6 solo. */
    public static int partyLimit(RunState state) {
        return state.isCoop() ? Math.max(1, Math.min(6, RogueConfig.get().coopPartyLimit)) : 6;
    }

    public boolean inLobby(ServerPlayerEntity player) {
        return lobbies.containsKey(player.getUuid());
    }

    /** Name of the player who invited this one, or null (invites expire). */
    public String pendingInviteFrom(ServerPlayerEntity player) {
        Invite invite = invites.get(player.getUuid());
        if (invite == null || invite.expiresAt() < System.currentTimeMillis()) {
            invites.remove(player.getUuid());
            return null;
        }
        ServerPlayerEntity from = online(invite.from());
        return from == null ? null : from.getName().getString();
    }

    private boolean busy(ServerPlayerEntity player) {
        UUID id = player.getUuid();
        return active.containsKey(id) || lobbies.containsKey(id) || storage.hasJournal(id) || savedRun(player) != null;
    }

    public void invite(ServerPlayerEntity inviter, ServerPlayerEntity target) {
        if (inviter.getUuid().equals(target.getUuid())) {
            message(inviter, "You can't invite yourself.", Formatting.RED);
            return;
        }
        if (busy(inviter)) {
            message(inviter, "Finish (or end) your current run first.", Formatting.RED);
            return;
        }
        if (busy(target)) {
            message(inviter, target.getName().getString() + " is already in a run.", Formatting.RED);
            return;
        }
        invites.put(target.getUuid(), new Invite(inviter.getUuid(),
                System.currentTimeMillis() + RogueConfig.get().coopInviteSeconds * 1000L));
        message(inviter, "Co-op invite sent to " + target.getName().getString() + ".", Formatting.GREEN);
        target.sendMessage(Text.literal(inviter.getName().getString() + " invited you to a co-op rogue run! ").formatted(Formatting.AQUA)
                .append(Text.literal("[Accept]").formatted(Formatting.GREEN, Formatting.BOLD)
                        .styled(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/rogue accept"))))
                .append(Text.literal(" "))
                .append(Text.literal("[Decline]").formatted(Formatting.RED)
                        .styled(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/rogue decline")))), false);
    }

    public void accept(ServerPlayerEntity player) {
        Invite invite = invites.remove(player.getUuid());
        ServerPlayerEntity host = invite == null || invite.expiresAt() < System.currentTimeMillis() ? null : online(invite.from());
        if (host == null) {
            message(player, "You don't have a valid co-op invite.", Formatting.RED);
            return;
        }
        if (busy(player) || busy(host)) {
            message(player, "One of you is already in a run.", Formatting.RED);
            return;
        }
        Lobby lobby = new Lobby(host.getUuid(), player.getUuid());
        lobbies.put(host.getUuid(), lobby);
        lobbies.put(player.getUuid(), lobby);
        int limit = Math.max(1, Math.min(6, RogueConfig.get().coopPartyLimit));
        for (ServerPlayerEntity member : List.of(host, player)) {
            message(member, "Co-op lobby with " + online(lobby.other(member.getUuid())).getName().getString()
                    + ". Pick your partner! (Up to " + limit + " Pokémon each.)", Formatting.AQUA);
            RogueMenus.partnerPicker(member, 0);
        }
    }

    public void decline(ServerPlayerEntity player) {
        Invite invite = invites.remove(player.getUuid());
        if (invite == null) {
            message(player, "You don't have a co-op invite.", Formatting.RED);
            return;
        }
        message(player, "Invite declined.", Formatting.GRAY);
        ServerPlayerEntity from = online(invite.from());
        if (from != null) {
            message(from, player.getName().getString() + " declined your co-op invite.", Formatting.GRAY);
        }
    }

    public void leaveLobby(ServerPlayerEntity player) {
        Lobby lobby = lobbies.remove(player.getUuid());
        if (lobby == null) {
            return;
        }
        UUID otherId = lobby.other(player.getUuid());
        lobbies.remove(otherId);
        ServerPlayerEntity other = online(otherId);
        if (other != null) {
            message(other, player.getName().getString() + " left the co-op lobby.", Formatting.YELLOW);
        }
    }

    private void lobbyPick(ServerPlayerEntity player, Lobby lobby, UUID pokemonId) {
        Pokemon original = CobblemonBridge.findOwned(player, pokemonId);
        if (original == null || CobblemonBridge.isRogue(original)) {
            message(player, "That Pokémon isn't available anymore.", Formatting.RED);
            RogueMenus.partnerPicker(player, 0);
            return;
        }
        if (player.getUuid().equals(lobby.host)) {
            lobby.hostPick = pokemonId;
        } else {
            lobby.guestPick = pokemonId;
        }
        if (lobby.hostPick != null && lobby.guestPick != null) {
            beginCoopRun(lobby);
            return;
        }
        player.closeHandledScreen();
        ServerPlayerEntity other = online(lobby.other(player.getUuid()));
        message(player, "Partner chosen! Waiting for " + (other == null ? "your partner" : other.getName().getString()) + "...", Formatting.GREEN);
        if (other != null) {
            message(other, player.getName().getString() + " has picked their partner.", Formatting.AQUA);
        }
    }

    private void beginCoopRun(Lobby lobby) {
        lobbies.remove(lobby.host);
        lobbies.remove(lobby.guest);
        ServerPlayerEntity host = online(lobby.host);
        ServerPlayerEntity guest = online(lobby.guest);
        List<ServerPlayerEntity> members = new ArrayList<>();
        if (host != null) {
            members.add(host);
        }
        if (guest != null) {
            members.add(guest);
        }
        if (host == null || guest == null) {
            members.forEach(p -> message(p, "Your co-op partner went offline.", Formatting.RED));
            return;
        }
        for (ServerPlayerEntity member : members) {
            if (CobblemonBridge.isInBattle(member) || storage.hasJournal(member.getUuid()) || savedRun(member) != null) {
                members.forEach(p -> message(p, member.getName().getString() + " can't start a run right now.", Formatting.RED));
                return;
            }
        }
        RogueConfig config = RogueConfig.get();
        Pokemon hostOriginal = CobblemonBridge.findOwned(host, lobby.hostPick);
        Pokemon guestOriginal = CobblemonBridge.findOwned(guest, lobby.guestPick);
        if (hostOriginal == null || guestOriginal == null) {
            members.forEach(p -> message(p, "A chosen partner isn't available anymore.", Formatting.RED));
            return;
        }
        Pokemon hostCopy = CobblemonBridge.createRogueCopy(hostOriginal, host.getRegistryManager(), config.resetStarterLevel ? config.starterLevel : 0);
        Pokemon guestCopy = CobblemonBridge.createRogueCopy(guestOriginal, guest.getRegistryManager(), config.resetStarterLevel ? config.starterLevel : 0);

        // Both journals first; nothing is swapped until both real parties are on disk.
        if (!writeJournal(host)) {
            members.forEach(p -> message(p, "Could not start the run (failed to save a party).", Formatting.RED));
            return;
        }
        if (!writeJournal(guest)) {
            try {
                storage.deleteJournal(host.getUuid());
            } catch (IOException ignored) {
                // restored on the host's next login at worst
            }
            members.forEach(p -> message(p, "Could not start the run (failed to save a party).", Formatting.RED));
            return;
        }
        RunState state;
        try {
            swapInPartner(host, hostCopy);
            swapInPartner(guest, guestCopy);
            state = new RunState(host.getUuid(), new Random().nextLong());
            state.partnerId = guest.getUuid();
            state.money = config.startingMoney;
            state.modifiers.addAll(pendingModifiers.getOrDefault(host.getUuid(), Set.of()));
            pendingModifiers.remove(host.getUuid());
            state.biome = Biomes.roll(state, new Random(state.seed ^ 0x5EEDB10EL));
            advanceFloor(state);
            storage.writeRun(state);
            active.put(host.getUuid(), state);
            active.put(guest.getUuid(), state);
        } catch (IOException | RuntimeException e) {
            Cobbleroguelike.LOGGER.error("Failed to start co-op run, rolling back", e);
            for (ServerPlayerEntity member : members) {
                try {
                    restore(member);
                } catch (IOException | RuntimeException rollback) {
                    Cobbleroguelike.LOGGER.error("Rollback failed for {}; journal kept on disk", member.getName().getString(), rollback);
                }
                message(member, "Could not start the co-op run. Your party is unchanged.", Formatting.RED);
            }
            return;
        }
        tell(state, "Co-op run started! " + host.getName().getString() + " & " + guest.getName().getString()
                + ", every battle is a 2v2 and you both need to be ready to fight.", Formatting.GREEN);
        members.forEach(this::openCurrent);
    }

    /** Messages every online member of the run. */
    private void tell(RunState state, String text, Formatting color) {
        for (UUID id : state.members()) {
            ServerPlayerEntity member = online(id);
            if (member != null) {
                message(member, text, color);
            }
        }
    }

    /** Re-opens run menus for co-op members (except one) who currently have one of our menus open. */
    private void refreshOthers(RunState state, UUID except) {
        if (!state.isCoop()) {
            return;
        }
        for (UUID id : state.members()) {
            ServerPlayerEntity member = online(id);
            if (member != null && !id.equals(except) && member.currentScreenHandler instanceof MenuScreenHandler) {
                openCurrent(member);
            }
        }
    }

    /** Reuse the live shared state when the other co-op player is already online. */
    private RunState liveInstance(RunState loaded) {
        for (UUID id : loaded.members()) {
            RunState live = active.get(id);
            if (live != null) {
                return live;
            }
        }
        return loaded;
    }

    public String partnerName(RunState state, UUID viewer) {
        UUID other = state.other(viewer);
        if (other == null) {
            return "";
        }
        ServerPlayerEntity player = online(other);
        return player != null ? player.getName().getString() : "your partner (offline)";
    }

    /** Co-op: a player is done with the route once they picked, skipped, or are offline. */
    private void maybeFinishCoopRoute(RunState state) {
        if (state.phase != Phase.ENCOUNTER) {
            return;
        }
        for (UUID id : state.members()) {
            if (!state.coopPicks.containsKey(id) && online(id) != null) {
                return;
            }
        }
        state.coopPicks.clear();
        state.coopOptions.clear();
        state.encounterOptions.clear();
        advanceFloor(state);
    }

    /** Route options for a player: their own roll in co-op, the shared list otherwise. */
    public static List<String> encounterOptionsFor(RunState state, UUID player) {
        List<String> own = state.coopOptions.get(player);
        return own != null ? own : state.encounterOptions;
    }

    /** Gives a player a rogue Pokémon now if there's room, else queues their release screen. */
    private void give(RunState state, UUID id, String properties) {
        ServerPlayerEntity member = online(id);
        if (member != null && CobblemonBridge.partyMembers(member).size() < partyLimit(state)) {
            CobblemonBridge.party(member).add(CobblemonBridge.createRogue(properties));
        } else {
            state.coopPending.put(id, properties);
        }
    }

    /** Co-op legendary reward: the clicking player takes one, the partner gets the other. */
    public void claimLegendary(ServerPlayerEntity player, int index) {
        RunState state = active.get(player.getUuid());
        if (state == null || state.coopClaim.size() < 2 || index < 0 || index > 1) {
            openCurrent(player);
            return;
        }
        UUID id = player.getUuid();
        String mine = state.coopClaim.get(index);
        String theirs = state.coopClaim.get(1 - index);
        state.coopClaim.clear();
        give(state, id, mine);
        UUID other = state.other(id);
        if (other != null) {
            give(state, other, theirs);
        }
        tell(state, player.getName().getString() + " takes " + speciesName(mine) + ", and "
                + partnerName(state, id) + " gets " + speciesName(theirs) + "!", Formatting.LIGHT_PURPLE);
        save(player, state);
        onlineMembers(state).forEach(this::openCurrent);
    }

    private static String speciesName(String properties) {
        String species = properties.split(" ")[0];
        species = species.contains(":") ? species.substring(species.indexOf(':') + 1) : species;
        return species.isEmpty() ? species : Character.toUpperCase(species.charAt(0)) + species.substring(1);
    }

    private void coopChooseEncounter(ServerPlayerEntity player, RunState state, int index) {
        UUID id = player.getUuid();
        if (state.coopPicks.containsKey(id)) {
            message(player, "You already chose; waiting for " + partnerName(state, id) + ".", Formatting.YELLOW);
            openCurrent(player);
            return;
        }
        List<String> options = encounterOptionsFor(state, id);
        if (index >= 0) {
            if (index >= options.size()) {
                openCurrent(player);
                return;
            }
            String picked = options.get(index);
            if (CobblemonBridge.partyMembers(player).size() >= partyLimit(state)) {
                state.coopPending.put(id, picked);
            } else {
                CobblemonBridge.party(player).add(CobblemonBridge.createRogue(picked));
            }
        }
        state.coopPicks.put(id, index);
        maybeFinishCoopRoute(state);
        save(player, state);
        openCurrent(player);
        refreshOthers(state, id);
    }

    private void coopRelease(ServerPlayerEntity player, RunState state, int partyIndex) {
        UUID id = player.getUuid();
        String pending = state.coopPending.remove(id);
        if (pending != null) {
            List<Pokemon> party = CobblemonBridge.partyMembers(player);
            if (partyIndex >= 0 && partyIndex < party.size()) {
                Pokemon released = party.get(partyIndex);
                CobblemonBridge.recall(released);
                CobblemonBridge.party(player).remove(released);
                CobblemonBridge.party(player).add(CobblemonBridge.createRogue(pending));
            }
        }
        maybeFinishCoopRoute(state);
        save(player, state);
        openCurrent(player);
        refreshOthers(state, id);
    }

    /** Co-op battles start once both players pressed Ready and stand close together. */
    private void coopReady(ServerPlayerEntity player, RunState state) {
        UUID id = player.getUuid();
        state.ready.add(id);
        ServerPlayerEntity other = online(state.other(id));
        if (other == null) {
            message(player, "Ready! " + partnerName(state, id) + " needs to come back online to fight.", Formatting.YELLOW);
            openCurrent(player);
            return;
        }
        if (!state.ready.contains(other.getUuid())) {
            message(player, "Ready! Waiting for " + other.getName().getString() + "...", Formatting.GREEN);
            other.sendMessage(Text.literal(player.getName().getString() + " is ready to fight " + state.battleName + "! ").formatted(Formatting.AQUA)
                    .append(Text.literal("[Ready]").formatted(Formatting.GREEN, Formatting.BOLD)
                            .styled(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/rogue ready")))), false);
            openCurrent(player);
            refreshOthers(state, id);
            return;
        }
        ServerPlayerEntity host = online(state.playerId);
        ServerPlayerEntity guest = online(state.partnerId);
        state.ready.clear();
        for (ServerPlayerEntity member : List.of(host, guest)) {
            if (CobblemonBridge.isInBattle(member)) {
                tell(state, member.getName().getString() + " is already in a battle.", Formatting.RED);
                return;
            }
            if (CobblemonBridge.healthyCount(member) == 0) {
                tell(state, member.getName().getString() + " has no Pokémon able to fight. Heal with the Bag first.", Formatting.RED);
                return;
            }
        }
        int maxDistance = RogueConfig.get().coopMaxDistance;
        if (host.getServerWorld() != guest.getServerWorld() || host.squaredDistanceTo(guest) > (double) maxDistance * maxDistance) {
            tell(state, "Stand together (within " + maxDistance + " blocks) to start the battle.", Formatting.YELLOW);
            return;
        }
        if (state.battleTeam.isEmpty() || state.battleTeam2.isEmpty()) {
            tell(state, "This battle isn't set up for co-op; skipping it.", Formatting.RED);
            state.clearBattle();
            advanceFloor(state);
            save(player, state);
            state.members().forEach(m -> {
                ServerPlayerEntity p = online(m);
                if (p != null) {
                    openCurrent(p);
                }
            });
            return;
        }
        PokemonBattle battle;
        if (state.battleKind == NodeType.LEGENDARY) {
            battle = CobblemonBattles.startCoopWildBattle(host, guest, state.battleTeam.get(0), state.battleTeam2.get(0), state.battleSkill);
        } else {
            List<Pokemon> team1 = new ArrayList<>();
            state.battleTeam.forEach(props -> team1.add(CobblemonBridge.create(props)));
            List<Pokemon> team2 = new ArrayList<>();
            state.battleTeam2.forEach(props -> team2.add(CobblemonBridge.create(props)));
            battle = CobblemonBattles.startCoopTrainerBattle(host, guest, state.battleName, team1, state.battleName2, team2,
                    state.battleSkill, state.battleGimmick);
        }
        if (battle == null) {
            tell(state, "The battle couldn't start. Make sure both of you have a Pokémon able to fight.", Formatting.RED);
            return;
        }
        host.closeHandledScreen();
        guest.closeHandledScreen();
        UUID hostId = state.playerId;
        CobblemonBattles.onEnd(battle, ended -> {
            Boolean won = CobblemonBattles.playerWon(ended, hostId);
            server.execute(() -> onBattleEnded(hostId, won));
        });
    }

    /** Co-op legendary reward: joins whoever has room (host first); otherwise the host gets a release screen. */
    private void coopRecruit(RunState state, String properties) {
        int limit = partyLimit(state);
        for (UUID id : state.members()) {
            ServerPlayerEntity member = online(id);
            if (member != null && CobblemonBridge.partyMembers(member).size() < limit) {
                CobblemonBridge.party(member).add(CobblemonBridge.createRogue(properties));
                tell(state, "It joins " + member.getName().getString() + "'s team!", Formatting.LIGHT_PURPLE);
                return;
            }
        }
        UUID target = online(state.playerId) != null ? state.playerId : state.partnerId;
        state.coopPending.put(target, properties);
    }

    private void finishCoop(RunState state, String reason, boolean won) {
        for (UUID id : state.members()) {
            active.remove(id);
            ServerPlayerEntity member = online(id);
            if (member != null) {
                try {
                    restore(member);
                    message(member, reason + " Your party has been restored.", Formatting.GOLD);
                } catch (IOException | RuntimeException e) {
                    Cobbleroguelike.LOGGER.error("Failed to end co-op run for {}", member.getName().getString(), e);
                    message(member, "Failed to restore your party. Nothing was lost; ask an admin to check the log.", Formatting.RED);
                }
            }
            // Offline members are restored on their next login (their run link no longer resolves).
            payOut(id, member, state, won);
        }
        try {
            storage.deleteRun(state.playerId);
        } catch (IOException e) {
            Cobbleroguelike.LOGGER.error("Failed to delete co-op run file", e);
        }
    }

    // ---------------------------------------------------------------- boss prep

    public static boolean isBoss(NodeType kind) {
        return kind == NodeType.GYM || kind == NodeType.ELITE || kind == NodeType.CHAMPION;
    }

    /** True if this player already used the given prep action ("train", "draft", "heal") for this boss. */
    public static boolean prepUsed(RunState state, ServerPlayerEntity player, String action) {
        return state.prepUsed.contains(player.getUuid() + ":" + action);
    }

    private RunState requireBossPrep(ServerPlayerEntity player, String action) {
        RunState state = requirePhase(player, Phase.BATTLE);
        if (state == null) {
            return null;
        }
        if (!isBoss(state.battleKind)) {
            openCurrent(player);
            return null;
        }
        if (CobblemonBridge.isInBattle(player)) {
            message(player, "Finish your battle first.", Formatting.RED);
            return null;
        }
        if (prepUsed(state, player, action)) {
            message(player, "You already used that before this battle.", Formatting.YELLOW);
            openCurrent(player);
            return null;
        }
        return state;
    }

    /** Boss prep: raises the player's run team to the level cap with real EXP (moves and evolutions happen). */
    public void prepTrain(ServerPlayerEntity player) {
        if (!RogueConfig.get().prepTrainToCap) {
            return;
        }
        RunState state = requireBossPrep(player, "train");
        if (state == null) {
            return;
        }
        int cap = Scaling.levelCap(state.badges);
        int trained = 0;
        for (Pokemon pokemon : CobblemonBridge.partyMembers(player)) {
            if (CobblemonBridge.isRogue(pokemon) && pokemon.getLevel() < cap && CobblemonBridge.trainToLevel(player, pokemon, cap)) {
                trained++;
            }
        }
        state.prepUsed.add(player.getUuid() + ":train");
        message(player, trained > 0 ? "Your team trained up to the level cap (Lv. " + cap + ")."
                : "Your team is already at the level cap.", Formatting.GREEN);
        save(player, state);
        openCurrent(player);
    }

    /** Boss prep: a free full heal. */
    public void prepHeal(ServerPlayerEntity player) {
        if (!RogueConfig.get().prepHeal) {
            return;
        }
        RunState state = requireBossPrep(player, "heal");
        if (state == null) {
            return;
        }
        CobblemonBridge.healParty(player);
        state.prepUsed.add(player.getUuid() + ":heal");
        message(player, "Your team is fully healed and ready.", Formatting.GREEN);
        save(player, state);
        openCurrent(player);
    }

    /** Boss prep: take one of the counter Pokémon (release screen if the team is full). */
    public void prepDraft(ServerPlayerEntity player, int index) {
        if (!RogueConfig.get().prepDraft) {
            return;
        }
        RunState state = requireBossPrep(player, "draft");
        if (state == null || index < 0 || index >= state.draftOptions.size()) {
            return;
        }
        String picked = state.draftOptions.get(index);
        state.prepUsed.add(player.getUuid() + ":draft");
        if (CobblemonBridge.partyMembers(player).size() >= partyLimit(state)) {
            state.coopPending.put(player.getUuid(), picked);
        } else {
            CobblemonBridge.party(player).add(CobblemonBridge.createRogue(picked));
            message(player, "A new teammate joins you for the fight!", Formatting.GREEN);
        }
        save(player, state);
        openCurrent(player);
    }

    // ---------------------------------------------------------------- run flow

    public void openCurrent(ServerPlayerEntity player) {
        RunState state = active.get(player.getUuid());
        if (state == null) {
            RogueMenus.hub(player, false);
            return;
        }
        if (CobblemonBridge.isInBattle(player)) {
            message(player, "Finish your battle first. Stuck? Use /rogue endbattle (this ends your run).", Formatting.RED);
            return;
        }
        if (state.coopClaim.size() >= 2) {
            RogueMenus.claim(player, state);
            return;
        }
        String coopPending = state.coopPending.get(player.getUuid());
        if (coopPending != null && CobblemonBridge.partyMembers(player).size() < partyLimit(state)) {
            // There's room now (e.g. they were offline when it was given): just add it.
            state.coopPending.remove(player.getUuid());
            CobblemonBridge.party(player).add(CobblemonBridge.createRogue(coopPending));
            message(player, speciesName(coopPending) + " joined your team.", Formatting.GREEN);
            save(player, state);
            coopPending = null;
        }
        if (coopPending != null) {
            RogueMenus.release(player, state, coopPending);
            return;
        }
        switch (state.phase) {
            case CHOOSE_NODE -> RogueMenus.path(player, state);
            case ENCOUNTER -> RogueMenus.encounter(player, state);
            case RELEASE -> RogueMenus.release(player, state, state.pendingEncounter);
            case BATTLE -> RogueMenus.battle(player, state);
        }
    }

    public void chooseNode(ServerPlayerEntity player, int index) {
        RunState state = requirePhase(player, Phase.CHOOSE_NODE);
        if (state == null || index < 0 || index >= state.nodeChoices.size()) {
            return;
        }
        switch (state.nodeChoices.get(index)) {
            case ROUTE -> {
                state.encounterOptions = rollEncounters(state);
                state.coopPicks.clear();
                state.coopOptions.clear();
                if (state.isCoop()) {
                    // Each player gets their own roll, so nobody has to fight over a pick.
                    List<UUID> members = state.members();
                    for (int i = 0; i < members.size(); i++) {
                        state.coopOptions.put(members.get(i), i == 0 ? new ArrayList<>(state.encounterOptions)
                                : Encounters.roll(state, rng(state, 2 + 97 * i)));
                    }
                }
                state.phase = Phase.ENCOUNTER;
            }
            case REST -> {
                onlineMembers(state).forEach(CobblemonBridge::healParty);
                tell(state, state.isCoop() ? "Your teams rested and are fully healed." : "Your team rested and is fully healed.", Formatting.GREEN);
                advanceFloor(state);
            }
            case TRAINER, GYM, ELITE, CHAMPION, LEGENDARY -> {
                TrainerGenerator.prepare(state, state.nodeChoices.get(index), rng(state, 10 + index));
                state.ready.clear();
                state.phase = Phase.BATTLE;
            }
        }
        save(player, state);
        openCurrent(player);
        refreshOthers(state, player.getUuid());
    }

    /** Starts the prepared battle. The outcome arrives later through {@link #onBattleEnded}. */
    public void startBattle(ServerPlayerEntity player) {
        RunState state = requirePhase(player, Phase.BATTLE);
        if (state == null) {
            return;
        }
        if (CobblemonBridge.isInBattle(player)) {
            message(player, "You're already in a battle.", Formatting.RED);
            return;
        }
        if (state.isCoop()) {
            coopReady(player, state);
            return;
        }
        List<Pokemon> team = new ArrayList<>();
        for (String properties : state.battleTeam) {
            team.add(CobblemonBridge.create(properties));
        }
        boolean doubles = state.battleDoubles;
        if (doubles && CobblemonBridge.healthyCount(player) < 2) {
            doubles = false;
            message(player, "You only have one Pokémon able to fight, so this is a single battle.", Formatting.YELLOW);
        }
        PokemonBattle battle = state.battleKind == NodeType.LEGENDARY
                ? CobblemonBattles.startWildBattle(player, state.battleTeam.get(0), state.battleSkill)
                : CobblemonBattles.startTrainerBattle(player, state.battleName, team, state.battleSkill, doubles, state.battleGimmick);
        if (battle == null) {
            message(player, "The battle couldn't start. Is your lead Pokémon able to fight?", Formatting.RED);
            return;
        }
        player.closeHandledScreen();
        UUID playerId = player.getUuid();
        // End handlers can run mid-teardown; handle the result on the next tick instead.
        CobblemonBattles.onEnd(battle, ended -> {
            Boolean won = CobblemonBattles.playerWon(ended, playerId);
            server.execute(() -> onBattleEnded(playerId, won));
        });
    }

    /**
     * {@code won == null} means the battle was interrupted (e.g. a disconnect). The same trainer
     * can be challenged again, and damage taken so far carries over. A loss or forfeit ends the run.
     */
    private void onBattleEnded(UUID playerId, Boolean won) {
        RunState state = active.get(playerId);
        if (state == null || state.phase != Phase.BATTLE
                || state.members().stream().anyMatch(endingByCommand::contains)) {
            return;
        }
        // Any online member can carry the result (in co-op the host may have logged off).
        ServerPlayerEntity player = online(playerId);
        for (UUID id : state.members()) {
            if (player == null) {
                player = online(id);
            }
        }
        if (player == null) {
            return;
        }
        state.ready.clear();
        if (won == null) {
            tell(state, "The battle was interrupted. Use /rogue to challenge again.", Formatting.YELLOW);
            return;
        }
        if (!won) {
            finish(player, (state.isCoop() ? "You were both defeated" : "You blacked out") + " on floor " + state.floor
                    + " with " + state.badges + " badge" + (state.badges == 1 ? "" : "s") + ". Your run is over.", false);
            return;
        }
        int reward = battleReward(state);
        state.money += reward;
        if (Modifiers.has(state, Modifiers.NUZLOCKE)) {
            for (ServerPlayerEntity member : onlineMembers(state)) {
                releaseFainted(member);
                if (CobblemonBridge.partyMembers(member).isEmpty()) {
                    finish(member, member.getName().getString() + " has no Pokémon left. The run is over.", false);
                    return;
                }
            }
        }
        switch (state.battleKind) {
            case CHAMPION -> {
                finish(player, "You defeated " + state.battleName + "! Your run is complete!", true);
                return;
            }
            case GYM -> {
                state.badges++;
                state.usedGymTypes.add(state.battleType);
                tell(state, "You defeated " + state.battleName + " and earned badge " + state.badges
                        + "! Level cap is now " + Scaling.levelCap(state.badges) + ".", Formatting.GOLD);
                if (RogueConfig.get().healAfterGym) {
                    onlineMembers(state).forEach(CobblemonBridge::healParty);
                }
                state.biome = Biomes.roll(state, rng(state, 5));
                tell(state, "You travel on to the " + Biomes.get(state.biome).name + ".", Formatting.AQUA);
                if (Scaling.championUnlocked(state.badges)) {
                    state.eliteStartFloor = state.floor;
                    tell(state, RogueConfig.get().eliteCount > 0
                            ? "All badges earned! The Elite Four awaits." : "All badges earned! The Champion awaits.", Formatting.LIGHT_PURPLE);
                }
            }
            case ELITE -> {
                state.eliteWins++;
                state.usedEliteTypes.add(state.battleType);
                int remaining = Math.max(0, RogueConfig.get().eliteCount - state.eliteWins);
                tell(state, "You defeated " + state.battleName + "! " + (remaining > 0
                        ? remaining + " Elite Four member" + (remaining == 1 ? "" : "s") + " left."
                        : "The Champion awaits!"), Formatting.GOLD);
                if (RogueConfig.get().healAfterElite) {
                    onlineMembers(state).forEach(CobblemonBridge::healParty);
                }
            }
            case LEGENDARY -> {
                String name = state.battleName.replaceFirst("^Wild ", "");
                if (Modifiers.has(state, Modifiers.SOLO)) {
                    int bonus = reward * 2;
                    state.money += bonus;
                    tell(state, "Solo run: " + name + " leaves you " + bonus + " bonus coins instead of joining.", Formatting.LIGHT_PURPLE);
                    tell(state, "+" + reward + " coins (" + state.money + " total).", Formatting.GOLD);
                    state.clearBattle();
                    advanceFloor(state);
                    save(player, state);
                    onlineMembers(state).forEach(this::openCurrent);
                    return;
                }
                String recruit = state.battleTeam.get(0);
                String companion = state.battleTeam2.isEmpty() ? "" : state.battleTeam2.get(0);
                tell(state, name + " was impressed by your strength!", Formatting.LIGHT_PURPLE);
                tell(state, "+" + reward + " coins (" + state.money + " total).", Formatting.GOLD);
                state.clearBattle();
                if (state.isCoop()) {
                    if (companion.isEmpty()) {
                        coopRecruit(state, recruit);
                    } else {
                        // One of you takes the legendary, the other its companion.
                        state.coopClaim = new ArrayList<>(List.of(recruit, companion));
                    }
                    advanceFloor(state);
                    save(player, state);
                    onlineMembers(state).forEach(this::openCurrent);
                } else {
                    message(player, name + " joins your team!", Formatting.LIGHT_PURPLE);
                    recruit(player, state, recruit);
                }
                return;
            }
            default -> tell(state, "You defeated " + state.battleName
                    + (state.battleName2.isEmpty() ? "" : " and " + state.battleName2) + "!", Formatting.GREEN);
        }
        tell(state, "+" + reward + " coins (" + state.money + " total).", Formatting.GOLD);
        state.clearBattle();
        advanceFloor(state);
        save(player, state);
        onlineMembers(state).forEach(this::openCurrent);
    }

    private List<ServerPlayerEntity> onlineMembers(RunState state) {
        List<ServerPlayerEntity> result = new ArrayList<>();
        for (UUID id : state.members()) {
            ServerPlayerEntity member = online(id);
            if (member != null) {
                result.add(member);
            }
        }
        return result;
    }

    /**
     * Force-stops the player's current battle and ends their run. Players can only use this on
     * themselves while in a run, so it can't be used to escape PvP battles. Ops can use it on anyone.
     */
    public void endBattle(ServerPlayerEntity player) {
        UUID id = player.getUuid();
        boolean hadBattle;
        endingByCommand.add(id);
        try {
            hadBattle = CobblemonBattles.stopBattle(player);
        } catch (RuntimeException e) {
            Cobbleroguelike.LOGGER.error("Failed to stop battle for {}", player.getName().getString(), e);
            hadBattle = true;
        }
        // The battle closes during stop(); finish on a later tick once it's fully torn down.
        Scheduler.runLater(5, () -> {
            endingByCommand.remove(id);
            if (CobblemonBridge.isInBattle(player)) {
                message(player, "Couldn't stop the battle. Try relogging, then /rogue clean.", Formatting.RED);
                return;
            }
            if (active.containsKey(id) || storage.hasJournal(id)) {
                finish(player, "You ended the battle and your run.", false);
            } else {
                message(player, "Battle ended.", Formatting.GOLD);
            }
        });
        if (!hadBattle) {
            message(player, "You're not in a battle; ending your run.", Formatting.YELLOW);
        }
    }

    /** Nuzlocke: rogue Pokémon that fainted are gone for good. */
    private void releaseFainted(ServerPlayerEntity player) {
        for (Pokemon pokemon : CobblemonBridge.partyMembers(player)) {
            if (pokemon.getCurrentHealth() <= 0 && CobblemonBridge.isRogue(pokemon)) {
                CobblemonBridge.recall(pokemon);
                CobblemonBridge.party(player).remove(pokemon);
                message(player, pokemon.getSpecies().getName() + " fainted and is gone for good.", Formatting.DARK_RED);
            }
        }
    }

    /** Walks away from a Legendary encounter without fighting it. */
    public void skipLegendary(ServerPlayerEntity player) {
        RunState state = requirePhase(player, Phase.BATTLE);
        if (state == null || state.battleKind != NodeType.LEGENDARY) {
            return;
        }
        tell(state, "You leave " + state.battleName.replaceFirst("^Wild ", "") + " in peace.", Formatting.GRAY);
        state.clearBattle();
        advanceFloor(state);
        save(player, state);
        openCurrent(player);
        refreshOthers(state, player.getUuid());
    }

    /** Adds a rogue Pokémon to the party, going through the release screen if it's full. */
    private void recruit(ServerPlayerEntity player, RunState state, String properties) {
        state.encounterOptions.clear();
        if (CobblemonBridge.partyMembers(player).size() >= partyLimit(state)) {
            state.pendingEncounter = properties;
            state.phase = Phase.RELEASE;
        } else {
            CobblemonBridge.party(player).add(CobblemonBridge.createRogue(properties));
            advanceFloor(state);
        }
        save(player, state);
        openCurrent(player);
    }

    /** Current run level cap for a player, or -1 if they aren't in a run. */
    public static int levelCap(ServerPlayerEntity player) {
        if (instance == null) {
            return -1;
        }
        RunState state = instance.active.get(player.getUuid());
        return state == null ? -1 : Scaling.levelCap(state.badges);
    }

    /** Picks one of the route's Pokémon, like Emerald Rogue's one catch per route. {@code -1} skips. */
    public void chooseEncounter(ServerPlayerEntity player, int index) {
        RunState state = requirePhase(player, Phase.ENCOUNTER);
        if (state == null) {
            return;
        }
        if (state.isCoop()) {
            coopChooseEncounter(player, state, index);
            return;
        }
        if (index >= 0 && index < state.encounterOptions.size()) {
            String picked = state.encounterOptions.get(index);
            if (CobblemonBridge.partyMembers(player).size() >= partyLimit(state)) {
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
        RunState coopState = active.get(player.getUuid());
        if (coopState != null && (coopState.isCoop() || coopState.coopPending.containsKey(player.getUuid()))) {
            // Per-player pending Pokémon: co-op routes, co-op legendaries and boss-prep drafts.
            coopRelease(player, coopState, partyIndex);
            return;
        }
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

    /** Coins for beating the prepared battle: base + strongest level * perLevel, multiplied for bosses. */
    private static int battleReward(RunState state) {
        RogueConfig config = RogueConfig.get();
        int maxLevel = 1;
        for (String member : state.battleTeam) {
            for (String part : member.split(" ")) {
                if (part.startsWith("level=")) {
                    try {
                        maxLevel = Math.max(maxLevel, Integer.parseInt(part.substring(6)));
                    } catch (NumberFormatException ignored) {
                        // keep the current max
                    }
                }
            }
        }
        double multiplier = switch (state.battleKind) {
            case GYM -> config.gymRewardMultiplier;
            case ELITE -> config.eliteRewardMultiplier;
            case CHAMPION -> config.championRewardMultiplier;
            default -> 1.0;
        };
        return (int) Math.round((config.trainerRewardBase + maxLevel * config.trainerRewardPerLevel) * multiplier);
    }

    /** Saves run state changed from outside this class (e.g. the shop). */
    public void persist(ServerPlayerEntity player, RunState state) {
        save(player, state);
    }

    /**
     * Used by the Mega Showdown mixin: true if the player is in a run that unlocked the gimmick
     * granted by the given key-item tag (e.g. {@code mega_bracelet}).
     */
    public static boolean hasGimmickTag(ServerPlayerEntity player, String tagPath) {
        if (instance == null) {
            return false;
        }
        RunState state = instance.active.get(player.getUuid());
        if (state == null) {
            return false;
        }
        for (String gimmick : state.gimmicks) {
            if (tagPath.equals(ShopCatalog.GIMMICK_TAGS.get(gimmick))) {
                return true;
            }
        }
        return false;
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
        return new Random(state.seed * 31L + state.floor * 1_000_003L + salt);
    }

    private static List<String> rollEncounters(RunState state) {
        return Encounters.roll(state, rng(state, 2));
    }

    private static List<NodeType> rollNodes(RunState state) {
        if (Scaling.isBossFloor(state)) {
            NodeType boss = !Scaling.championUnlocked(state.badges) ? NodeType.GYM
                    : state.eliteWins < RogueConfig.get().eliteCount ? NodeType.ELITE : NodeType.CHAMPION;
            return new ArrayList<>(List.of(boss));
        }
        RogueConfig config = RogueConfig.get();
        Random random = rng(state, 3);
        double total = Math.max(1e-9, config.trainerWeight + config.routeWeight + config.restWeight);
        List<NodeType> nodes = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            double roll = random.nextDouble() * total;
            if (roll < config.trainerWeight) {
                nodes.add(NodeType.TRAINER);
            } else if (roll < config.trainerWeight + config.routeWeight) {
                nodes.add(NodeType.ROUTE);
            } else {
                nodes.add(NodeType.REST);
            }
        }
        if (nodes.stream().allMatch(node -> node == NodeType.REST)) {
            nodes.set(random.nextInt(nodes.size()), NodeType.ROUTE);
        }
        if (state.badges == 0 && state.floor <= config.startRouteFloors && !Modifiers.has(state, Modifiers.SOLO)) {
            // Build a team before the first fights.
            nodes.replaceAll(node -> node == NodeType.TRAINER ? NodeType.ROUTE : node);
        }
        if (Modifiers.has(state, Modifiers.SOLO)) {
            nodes.replaceAll(node -> node == NodeType.ROUTE ? NodeType.TRAINER : node);
        }
        // Legendary card: guaranteed on the first floor after certain badges, rare otherwise.
        // Co-op gets them later: two players clear the early floors more easily.
        List<Integer> after = state.isCoop() ? config.coopLegendaryAfterBadges : config.legendaryAfterBadges;
        int from = state.isCoop() ? Math.max(config.legendaryChanceFromBadge, config.coopLegendaryFromBadge)
                : config.legendaryChanceFromBadge;
        boolean guaranteed = Scaling.floorInSegment(state.floor) == 1 && state.badges > state.legendaryOfferedAt
                && after.contains(state.badges) && (!state.isCoop() || state.badges >= config.coopLegendaryFromBadge);
        boolean lucky = state.badges >= from && random.nextDouble() < config.legendaryChance;
        if (guaranteed || lucky) {
            nodes.set(random.nextInt(nodes.size()), NodeType.LEGENDARY);
            if (guaranteed) {
                state.legendaryOfferedAt = state.badges;
            }
        }
        return nodes;
    }

    public static void message(ServerPlayerEntity player, String text, Formatting color) {
        player.sendMessage(Text.literal(text).formatted(color), false);
    }
}
