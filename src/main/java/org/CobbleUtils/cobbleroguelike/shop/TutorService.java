package org.CobbleUtils.cobbleroguelike.shop;

import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Formatting;
import org.CobbleUtils.cobbleroguelike.RogueConfig;
import org.CobbleUtils.cobbleroguelike.compat.CobblemonBridge;
import org.CobbleUtils.cobbleroguelike.compat.MoveBridge;
import org.CobbleUtils.cobbleroguelike.run.RunManager;
import org.CobbleUtils.cobbleroguelike.run.RunState;
import org.CobbleUtils.cobbleroguelike.ui.TutorMenus;

import java.util.List;

/** Teaches moves to run Pokémon for coins. Only rogue Pokémon in an active run can be taught. */
public final class TutorService {

    private TutorService() {
    }

    public static void teach(ServerPlayerEntity player, int partyIndex, String moveName, int slot) {
        if (!RunManager.isInRun(player) || CobblemonBridge.isInBattle(player)) {
            RunManager.message(player, "The Move Tutor is only available between battles in a run.", Formatting.RED);
            return;
        }
        RunState state = RunManager.get().state(player);
        List<Pokemon> party = CobblemonBridge.partyMembers(player);
        if (partyIndex < 0 || partyIndex >= party.size()) {
            TutorMenus.pickPokemon(player);
            return;
        }
        Pokemon pokemon = party.get(partyIndex);
        MoveBridge.Teachable move = MoveBridge.teachable(pokemon).stream()
                .filter(t -> t.name().equals(moveName)).findFirst().orElse(null);
        if (move == null) {
            RunManager.message(player, pokemon.getSpecies().getName() + " can't learn that move.", Formatting.RED);
            TutorMenus.moves(player, partyIndex, 0);
            return;
        }
        int price = move.free() ? 0 : RogueConfig.get().moveTutorPrice;
        if (state.money < price) {
            RunManager.message(player, "Not enough coins (" + price + " needed).", Formatting.RED);
            TutorMenus.moves(player, partyIndex, 0);
            return;
        }
        if (MoveBridge.teach(pokemon, moveName, slot)) {
            state.money -= price;
            RunManager.get().persist(player, state);
            RunManager.message(player, pokemon.getSpecies().getName() + " learned " + move.displayName().getString()
                    + (price > 0 ? " (-" + price + " coins)." : "."), Formatting.GREEN);
        }
        TutorMenus.moves(player, partyIndex, 0);
    }
}
