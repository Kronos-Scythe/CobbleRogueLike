package org.CobbleUtils.cobbleroguelike.compat;

import com.cobblemon.mod.common.api.battles.model.ai.BattleAI;
import com.cobblemon.mod.common.battles.actor.TrainerBattleActor;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;

import java.util.List;
import java.util.UUID;

/** An entity-less AI trainer, used for every run battle. */
public final class RogueTrainerActor extends TrainerBattleActor {

    public RogueTrainerActor(String name, UUID uuid, List<BattlePokemon> team, BattleAI ai) {
        super(name, uuid, team, ai);
    }
}
