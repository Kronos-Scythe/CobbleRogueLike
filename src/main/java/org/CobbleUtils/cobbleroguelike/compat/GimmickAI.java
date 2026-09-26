package org.CobbleUtils.cobbleroguelike.compat;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.battles.model.ai.BattleAI;
import com.cobblemon.mod.common.battles.ActiveBattlePokemon;
import com.cobblemon.mod.common.battles.BattleSide;
import com.cobblemon.mod.common.battles.MoveActionResponse;
import com.cobblemon.mod.common.battles.ShowdownActionResponse;
import com.cobblemon.mod.common.battles.ShowdownMoveset;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.net.messages.client.battle.BattleHealthChangePacket;

/**
 * Wraps another AI (StrongBattleAI) and adds the boss's gimmick to its move choice. Cobblemon's
 * AIs never pick gimmicks by themselves. For non-player actors Showdown offers Mega Evolution
 * when a mega stone is held and Terastallization when the Pokémon has a Tera type, so all this
 * has to do is say yes, for the ace.
 */
public final class GimmickAI implements BattleAI {

    private final BattleAI delegate;
    private final String gimmick;
    private final String aceSpecies;

    /** {@code gimmick} is "mega" or "tera"; {@code aceSpecies} is the species id of the Pokémon that uses it. */
    public GimmickAI(BattleAI delegate, String gimmick, String aceSpecies) {
        this.delegate = delegate;
        this.gimmick = gimmick;
        this.aceSpecies = aceSpecies;
    }

    @Override
    public ShowdownActionResponse choose(ActiveBattlePokemon activeBattlePokemon, PokemonBattle battle, BattleSide aiSide,
                                         ShowdownMoveset moveset, boolean forceSwitch) {
        ShowdownActionResponse response = delegate.choose(activeBattlePokemon, battle, aiSide, moveset, forceSwitch);
        if (forceSwitch || moveset == null || !(response instanceof MoveActionResponse move) || move.getGimmickID() != null) {
            return response;
        }
        BattlePokemon active = activeBattlePokemon.getBattlePokemon();
        if (active == null || !CobblemonBridge.propertyId(active.getEffectedPokemon().getSpecies()).equals(aceSpecies)) {
            return response;
        }
        if (gimmick.equals("mega") && moveset.getCanMegaEvo()) {
            move.setGimmickID(ShowdownMoveset.Gimmick.MEGA_EVOLUTION.getId());
        } else if (gimmick.equals("tera") && moveset.getCanTerastallize() != null) {
            move.setGimmickID(ShowdownMoveset.Gimmick.TERASTALLIZATION.getId());
        }
        return response;
    }

    @Override
    public void onHealthChange(BattleHealthChangePacket packet) {
        delegate.onHealthChange(packet);
    }
}
