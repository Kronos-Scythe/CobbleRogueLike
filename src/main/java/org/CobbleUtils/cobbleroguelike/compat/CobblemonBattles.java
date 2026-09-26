package org.CobbleUtils.cobbleroguelike.compat;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.battles.BattleFormat;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.battles.BattleRules;
import com.cobblemon.mod.common.battles.BattleSide;
import com.cobblemon.mod.common.battles.BattleStartResult;
import com.cobblemon.mod.common.battles.SuccessfulBattleStart;
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor;
import com.cobblemon.mod.common.battles.actor.PokemonBattleActor;
import com.cobblemon.mod.common.battles.ai.StrongBattleAI;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.entity.npc.NPCBattleActor;
import com.cobblemon.mod.common.entity.npc.NPCEntity;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import kotlin.Unit;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import org.CobbleUtils.cobbleroguelike.util.Scheduler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** Starts run battles against AI trainers and reports how they ended. */
public final class CobblemonBattles {

    private CobblemonBattles() {
    }

    /**
     * Starts a singles battle between the player's (rogue) party and an AI trainer.
     * A temporary Cobblemon NPC is spawned in front of the player to send the trainer's
     * Pokémon out, and it is despawned a few seconds after the battle ends.
     * The player's party is used directly, so damage, fainting and EXP carry over within the run.
     * The trainer's Pokémon are battle clones and can't be caught. The Bag Clause stops
     * real-inventory items from being used.
     *
     * @return the battle, or null if it could not start
     */
    public static PokemonBattle startTrainerBattle(ServerPlayerEntity player, String trainerName,
                                                   List<Pokemon> trainerTeam, int aiSkill) {
        List<BattlePokemon> playerTeam = new ArrayList<>(CobblemonBridge.party(player).toBattleTeam(false, false, null));
        playerTeam.sort(Comparator.comparing(pokemon -> pokemon.getHealth() <= 0));
        if (playerTeam.isEmpty() || playerTeam.get(0).getHealth() <= 0 || trainerTeam.isEmpty()) {
            return null;
        }
        PlayerBattleActor playerActor = new PlayerBattleActor(player.getUuid(), playerTeam);

        List<BattlePokemon> team = new ArrayList<>();
        int topLevel = 1;
        for (Pokemon pokemon : trainerTeam) {
            team.add(BattlePokemon.Companion.safeCopyOf(pokemon));
            topLevel = Math.max(topLevel, pokemon.getLevel());
        }

        NPCEntity npc = RunEntities.spawn(player, trainerName, topLevel);
        if (npc == null) {
            return null;
        }
        int skill = Math.max(0, Math.min(5, aiSkill));
        NPCBattleActor trainer = new NPCBattleActor(npc, team, skill, new StrongBattleAI(skill));

        BattleFormat format = singlesWithBagClause();

        // canPreempt = false: run battles skip BATTLE_STARTED_PRE, so neither our own
        // outside-battle guard nor other mods (e.g. level-cap mods) can cancel them.
        BattleStartResult result = BattleRegistry.startBattle(format, new BattleSide(playerActor), new BattleSide(trainer), false);
        if (!(result instanceof SuccessfulBattleStart success)) {
            RunEntities.despawn(npc);
            return null;
        }
        PokemonBattle battle = success.getBattle();
        MinecraftServer server = player.getServer();
        onEnd(battle, ended -> server.execute(() ->
                // Give the NPC time to recall its Pokémon before it disappears.
                Scheduler.runLater(60, () -> RunEntities.despawn(npc))));
        return battle;
    }

    /**
     * Starts a wild battle against a Legendary spawned in front of the player. Otherwise it works
     * like {@link #startTrainerBattle}: same party handling and Bag Clause, and the Pokémon is
     * uncatchable and despawned after the battle. {@code properties} is the Pokémon to fight.
     */
    public static PokemonBattle startWildBattle(ServerPlayerEntity player, String properties, int aiSkill) {
        List<BattlePokemon> playerTeam = new ArrayList<>(CobblemonBridge.party(player).toBattleTeam(false, false, null));
        playerTeam.sort(Comparator.comparing(pokemon -> pokemon.getHealth() <= 0));
        if (playerTeam.isEmpty() || playerTeam.get(0).getHealth() <= 0) {
            return null;
        }
        PlayerBattleActor playerActor = new PlayerBattleActor(player.getUuid(), playerTeam);

        PokemonEntity entity = RunEntities.spawnWild(player, properties);
        if (entity == null) {
            return null;
        }
        Pokemon wild = entity.getPokemon();
        BattlePokemon battlePokemon = new BattlePokemon(wild, wild, ignored -> Unit.INSTANCE);
        int skill = Math.max(0, Math.min(5, aiSkill));
        PokemonBattleActor wildActor = new PokemonBattleActor(wild.getUuid(), battlePokemon, 32.0F, new StrongBattleAI(skill));

        BattleStartResult result = BattleRegistry.startBattle(singlesWithBagClause(), new BattleSide(playerActor), new BattleSide(wildActor), false);
        if (!(result instanceof SuccessfulBattleStart success)) {
            RunEntities.despawn(entity);
            return null;
        }
        PokemonBattle battle = success.getBattle();
        entity.setBattleId(battle.getBattleId());
        MinecraftServer server = player.getServer();
        onEnd(battle, ended -> server.execute(() -> Scheduler.runLater(40, () -> RunEntities.despawn(entity))));
        return battle;
    }

    private static BattleFormat singlesWithBagClause() {
        BattleFormat singles = BattleFormat.Companion.getGEN_9_SINGLES();
        Set<String> rules = new HashSet<>(singles.getRuleSet());
        rules.add(BattleRules.BAG_CLAUSE);
        return BattleFormat.Companion.setBattleRules(singles, rules);
    }

    /** Force-stops the battle the player is in. Returns false if they aren't in one. */
    public static boolean stopBattle(ServerPlayerEntity player) {
        PokemonBattle battle = BattleRegistry.getBattleByParticipatingPlayer(player);
        if (battle == null) {
            return false;
        }
        battle.stop();
        return true;
    }

    public static void onEnd(PokemonBattle battle, Consumer<PokemonBattle> handler) {
        battle.getOnEndHandlers().add(ended -> {
            handler.accept(ended);
            return Unit.INSTANCE;
        });
    }

    /** TRUE if the player won, FALSE if they lost or forfeited, null if the battle was interrupted. */
    public static Boolean playerWon(PokemonBattle battle, UUID playerId) {
        if (containsActor(battle.getWinners(), playerId)) {
            return Boolean.TRUE;
        }
        if (containsActor(battle.getLosers(), playerId)) {
            return Boolean.FALSE;
        }
        return null;
    }

    private static boolean containsActor(List<BattleActor> actors, UUID playerId) {
        for (BattleActor actor : actors) {
            if (actor.getUuid().equals(playerId)) {
                return true;
            }
        }
        return false;
    }
}
