package org.CobbleUtils.cobbleroguelike.run;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
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
import org.CobbleUtils.cobbleroguelike.compat.CobblemonBattles;
import org.CobbleUtils.cobbleroguelike.compat.CobblemonBridge;
import org.CobbleUtils.cobbleroguelike.run.RunState.NodeType;
import org.CobbleUtils.cobbleroguelike.run.RunState.Phase;
import org.CobbleUtils.cobbleroguelike.shop.ShopCatalog;
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
                    repair(state);
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
        active.remove(player.getUuid());
    }

    // ---------------------------------------------------------------- start / end

    /** Opens the partner picker. Nothing is swapped until a Pokémon is chosen. */
    public void start(ServerPlayerEntity player) {
        if (active.containsKey(player.getUuid())) {
            openCurrent(player);
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
        if (CobblemonBridge.isInBattle(player)) {
            message(player, "You can't start a run while in a battle.", Formatting.RED);
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
            CobblemonBridge.party(player).add(partner);
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
        RogueConfig config = RogueConfig.get();
        int floorsCleared = Math.max(0, state.floor - 1);
        int base = floorsCleared * config.tokensPerFloor + state.badges * config.tokensPerBadge
                + (won ? config.championTokenBonus : 0);
        int tokens = (int) Math.round(base * (1.0 + Modifiers.totalBonus(state.modifiers)));
        NbtCompound profile = storage.readProfile(player.getUuid());
        profile.putInt("tokens", profile.getInt("tokens") + tokens);
        profile.putInt("runs", profile.getInt("runs") + 1);
        profile.putInt("wins", profile.getInt("wins") + (won ? 1 : 0));
        profile.putInt("bestFloor", Math.max(profile.getInt("bestFloor"), state.floor));
        profile.putInt("bestBadges", Math.max(profile.getInt("bestBadges"), state.badges));
        saveProfile(player, profile);
        message(player, "+" + tokens + " Rogue Tokens (" + profile.getInt("tokens")
                + " total). Spend them with /rogue shop.", Formatting.LIGHT_PURPLE);
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
        return saveProfile(player, profile);
    }

    private boolean saveProfile(ServerPlayerEntity player, NbtCompound profile) {
        try {
            storage.writeProfile(player.getUuid(), profile);
            return true;
        } catch (IOException e) {
            Cobbleroguelike.LOGGER.error("Failed to save rogue profile for {}", player.getName().getString(), e);
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
        if (CobblemonBridge.isInBattle(player)) {
            message(player, "Finish your battle first. Stuck? Use /rogue endbattle (this ends your run).", Formatting.RED);
            return;
        }
        switch (state.phase) {
            case CHOOSE_NODE -> RogueMenus.path(player, state);
            case ENCOUNTER -> RogueMenus.encounter(player, state);
            case RELEASE -> RogueMenus.release(player, state);
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
                state.phase = Phase.ENCOUNTER;
            }
            case REST -> {
                CobblemonBridge.healParty(player);
                message(player, "Your team rested and is fully healed.", Formatting.GREEN);
                advanceFloor(state);
            }
            case TRAINER, GYM, ELITE, CHAMPION, LEGENDARY -> {
                TrainerGenerator.prepare(state, state.nodeChoices.get(index), rng(state, 10 + index));
                state.phase = Phase.BATTLE;
            }
        }
        save(player, state);
        openCurrent(player);
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
        if (endingByCommand.contains(playerId)) {
            return;
        }
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
        RunState state = active.get(playerId);
        if (player == null || state == null || state.phase != Phase.BATTLE) {
            return;
        }
        if (won == null) {
            message(player, "The battle was interrupted. Use /rogue to challenge again.", Formatting.YELLOW);
            return;
        }
        if (!won) {
            finish(player, "You blacked out on floor " + state.floor + " with " + state.badges + " badge"
                    + (state.badges == 1 ? "" : "s") + ". Your run is over.", false);
            return;
        }
        int reward = battleReward(state);
        state.money += reward;
        if (Modifiers.has(state, Modifiers.NUZLOCKE)) {
            releaseFainted(player);
        }
        switch (state.battleKind) {
            case CHAMPION -> {
                finish(player, "You defeated " + state.battleName + "! Your run is complete!", true);
                return;
            }
            case GYM -> {
                state.badges++;
                state.usedGymTypes.add(state.battleType);
                message(player, "You defeated " + state.battleName + " and earned badge " + state.badges
                        + "! Level cap is now " + Scaling.levelCap(state.badges) + ".", Formatting.GOLD);
                if (RogueConfig.get().healAfterGym) {
                    CobblemonBridge.healParty(player);
                }
                state.biome = Biomes.roll(state, rng(state, 5));
                message(player, "You travel on to the " + Biomes.get(state.biome).name + ".", Formatting.AQUA);
                if (Scaling.championUnlocked(state.badges)) {
                    state.eliteStartFloor = state.floor;
                    message(player, RogueConfig.get().eliteCount > 0
                            ? "All badges earned! The Elite Four awaits." : "All badges earned! The Champion awaits.", Formatting.LIGHT_PURPLE);
                }
            }
            case ELITE -> {
                state.eliteWins++;
                state.usedEliteTypes.add(state.battleType);
                int remaining = Math.max(0, RogueConfig.get().eliteCount - state.eliteWins);
                message(player, "You defeated " + state.battleName + "! " + (remaining > 0
                        ? remaining + " Elite Four member" + (remaining == 1 ? "" : "s") + " left."
                        : "The Champion awaits!"), Formatting.GOLD);
                if (RogueConfig.get().healAfterElite) {
                    CobblemonBridge.healParty(player);
                }
            }
            case LEGENDARY -> {
                if (Modifiers.has(state, Modifiers.SOLO)) {
                    int bonus = reward * 2;
                    state.money += bonus;
                    message(player, "Solo run: " + state.battleName.replaceFirst("^Wild ", "") + " leaves you "
                            + bonus + " bonus coins instead of joining.", Formatting.LIGHT_PURPLE);
                    message(player, "+" + reward + " coins (" + state.money + " total).", Formatting.GOLD);
                    state.clearBattle();
                    advanceFloor(state);
                    save(player, state);
                    openCurrent(player);
                    return;
                }
                String recruit = state.battleTeam.get(0);
                message(player, state.battleName.replaceFirst("^Wild ", "") + " was impressed by your strength and joins your team!", Formatting.LIGHT_PURPLE);
                message(player, "+" + reward + " coins (" + state.money + " total).", Formatting.GOLD);
                state.clearBattle();
                recruit(player, state, recruit);
                return;
            }
            default -> message(player, "You defeated " + state.battleName + "!", Formatting.GREEN);
        }
        message(player, "+" + reward + " coins (" + state.money + " total).", Formatting.GOLD);
        state.clearBattle();
        advanceFloor(state);
        save(player, state);
        openCurrent(player);
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
        message(player, "You leave " + state.battleName.replaceFirst("^Wild ", "") + " in peace.", Formatting.GRAY);
        state.clearBattle();
        advanceFloor(state);
        save(player, state);
        openCurrent(player);
    }

    /** Adds a rogue Pokémon to the party, going through the release screen if it's full. */
    private void recruit(ServerPlayerEntity player, RunState state, String properties) {
        state.encounterOptions.clear();
        if (CobblemonBridge.partyMembers(player).size() >= 6) {
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
        if (Modifiers.has(state, Modifiers.SOLO)) {
            nodes.replaceAll(node -> node == NodeType.ROUTE ? NodeType.TRAINER : node);
        }
        // Legendary card: guaranteed on the first floor after certain badges, rare otherwise.
        boolean guaranteed = Scaling.floorInSegment(state.floor) == 1 && state.badges > state.legendaryOfferedAt
                && config.legendaryAfterBadges.contains(state.badges);
        if (guaranteed || random.nextDouble() < config.legendaryChance) {
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
