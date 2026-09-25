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
import com.cobblemon.mod.common.battles.ai.StrongBattleAI;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.pokemon.Pokemon;
import kotlin.Unit;
import net.minecraft.server.network.ServerPlayerEntity;

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
        for (Pokemon pokemon : trainerTeam) {
            team.add(BattlePokemon.Companion.safeCopyOf(pokemon));
        }
        RogueTrainerActor trainer = new RogueTrainerActor(trainerName, UUID.randomUUID(), team, new StrongBattleAI(aiSkill));

        BattleFormat singles = BattleFormat.Companion.getGEN_9_SINGLES();
        Set<String> rules = new HashSet<>(singles.getRuleSet());
        rules.add(BattleRules.BAG_CLAUSE);
        BattleFormat format = BattleFormat.Companion.setBattleRules(singles, rules);

        // canPreempt = false: run battles skip BATTLE_STARTED_PRE, so neither our own
        // outside-battle guard nor other mods (e.g. level-cap mods) can cancel them.
        BattleStartResult result = BattleRegistry.startBattle(format, new BattleSide(playerActor), new BattleSide(trainer), false);
        return result instanceof SuccessfulBattleStart success ? success.getBattle() : null;
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
