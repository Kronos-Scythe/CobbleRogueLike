package org.CobbleUtils.cobbleroguelike.compat;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Formatting;
import org.CobbleUtils.cobbleroguelike.run.RunManager;
import kotlin.Unit;

/** Cobblemon event hooks that stop rogue Pokémon from interacting with the main game. */
public final class CobblemonGuards {

    private CobblemonGuards() {
    }

    public static void register() {
        // Outside battles would give rogue Pokémon free EXP and let players catch into the run.
        // Run battles will be started by the mod itself and allowed through here (next milestone).
        CobblemonEvents.BATTLE_STARTED_PRE.subscribe(Priority.NORMAL, event -> {
            for (ServerPlayerEntity player : event.getBattle().getPlayers()) {
                if (RunManager.isInRun(player)) {
                    event.cancel();
                    RunManager.message(player, "You can't battle outside the run while a run is active.", Formatting.RED);
                    break;
                }
            }
            return Unit.INSTANCE;
        });

        // Catching would add main-world Pokémon to the run, or overflow rogue ones into the PC.
        CobblemonEvents.THROWN_POKEBALL_HIT.subscribe(Priority.NORMAL, event -> {
            Entity owner = event.getPokeBall().getOwner();
            if (owner instanceof ServerPlayerEntity player && RunManager.isInRun(player)) {
                event.cancel();
                RunManager.message(player, "You can't catch Pokémon while a run is active.", Formatting.RED);
            }
            return Unit.INSTANCE;
        });

        // Held-item swaps would move items between the rogue party and the real inventory.
        CobblemonEvents.HELD_ITEM_PRE.subscribe(Priority.NORMAL, event -> {
            ServerPlayerEntity player = event.getPokemon().getOwnerPlayer();
            if (player != null && RunManager.isInRun(player)) {
                event.cancel();
                RunManager.message(player, "Held items can't be changed during a run.", Formatting.RED);
            }
            return Unit.INSTANCE;
        });
    }
}
