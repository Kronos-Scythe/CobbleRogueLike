package org.CobbleUtils.cobbleroguelike.compat;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Formatting;
import org.CobbleUtils.cobbleroguelike.run.RunManager;

/** Cobblemon event hooks that stop rogue Pokémon from interacting with the main game. */
public final class CobblemonGuards {

    /** Set while the run bag swaps a held item, so the held-item guard lets it through. */
    public static boolean allowHeldItemChange = false;

    private CobblemonGuards() {
    }

    public static void register() {
        // Outside battles would give rogue Pokémon free EXP and let players catch into the run.
        // Run battles are started with canPreempt = false, so they never reach this listener.
        CobblemonEvents.BATTLE_STARTED_PRE.subscribe(Priority.NORMAL, event -> {
            for (ServerPlayerEntity player : event.getBattle().getPlayers()) {
                if (RunManager.isInRun(player)) {
                    event.cancel();
                    RunManager.message(player, "You can't battle outside the run while a run is active.", Formatting.RED);
                    break;
                }
            }
        });

        // Catching would add main-world Pokémon to the run, or overflow rogue ones into the PC.
        CobblemonEvents.THROWN_POKEBALL_HIT.subscribe(Priority.NORMAL, event -> {
            Entity owner = event.getPokeBall().getOwner();
            if (owner instanceof ServerPlayerEntity player && RunManager.isInRun(player)) {
                event.cancel();
                RunManager.message(player, "You can't catch Pokémon while a run is active.", Formatting.RED);
            }
        });

        // Run level cap: rogue Pokémon can't gain EXP past the next boss's level.
        CobblemonEvents.EXPERIENCE_GAINED_EVENT_PRE.subscribe(Priority.NORMAL, event -> {
            Pokemon pokemon = event.getPokemon();
            if (!CobblemonBridge.isRogue(pokemon)) {
                return;
            }
            ServerPlayerEntity owner = pokemon.getOwnerPlayer();
            int cap = owner == null ? -1 : RunManager.levelCap(owner);
            if (cap < 0) {
                return;
            }
            if (pokemon.getLevel() >= cap) {
                event.cancel();
                return;
            }
            org.CobbleUtils.cobbleroguelike.RogueConfig config = org.CobbleUtils.cobbleroguelike.RogueConfig.get();
            double multiplier = config.expMultiplier;
            if (config.catchUpExp) {
                // Catch-up: up to 2x more for Pokémon 10+ levels under the cap (new recruits, the early game).
                multiplier *= 1 + Math.min(10, cap - pokemon.getLevel()) / 10.0;
            }
            if (multiplier > 0 && multiplier != 1.0 && event.getSource() instanceof com.cobblemon.mod.common.api.pokemon.experience.BattleExperienceSource) {
                event.setExperience((int) Math.round(event.getExperience() * multiplier));
            }
            int room = pokemon.getExperienceGroup().getExperience(cap) - pokemon.getExperience();
            if (event.getExperience() > room) {
                event.setExperience(Math.max(0, room));
            }
        });

        // Held-item swaps would move items between the rogue party and the real inventory.
        CobblemonEvents.HELD_ITEM_PRE.subscribe(Priority.NORMAL, event -> {
            if (allowHeldItemChange) {
                return;
            }
            ServerPlayerEntity player = event.getPokemon().getOwnerPlayer();
            if (player != null && RunManager.isInRun(player)) {
                event.cancel();
                RunManager.message(player, "Use the run Bag (/rogue → Bag) to change held items during a run.", Formatting.RED);
            }
        });
    }
}
