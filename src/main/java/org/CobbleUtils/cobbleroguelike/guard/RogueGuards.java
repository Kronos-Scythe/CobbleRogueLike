package org.CobbleUtils.cobbleroguelike.guard;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.registry.Registries;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.CobbleUtils.cobbleroguelike.RogueConfig;
import org.CobbleUtils.cobbleroguelike.compat.CobblemonGuards;
import org.CobbleUtils.cobbleroguelike.run.RunManager;

import java.util.Locale;

/** Keeps rogue Pokémon inside the run and real Pokémon out of it. */
public final class RogueGuards {

    private static final Identifier PC_BLOCK = Identifier.of("cobblemon", "pc");
    private static int ticks = 0;

    private RogueGuards() {
    }

    public static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (world.isClient() || !(player instanceof ServerPlayerEntity serverPlayer) || !RunManager.isInRun(serverPlayer)) {
                return ActionResult.PASS;
            }
            if (Registries.BLOCK.getId(world.getBlockState(hit.getBlockPos()).getBlock()).equals(PC_BLOCK)) {
                RunManager.message(serverPlayer, "The PC is locked while a run is active.", Formatting.RED);
                return ActionResult.FAIL;
            }
            return ActionResult.PASS;
        });

        // Last line of defense: remove rogue Pokémon that got out some other way (e.g. a trade).
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (++ticks < Math.max(20, RogueConfig.get().sweepIntervalTicks)) {
                return;
            }
            ticks = 0;
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                RunManager.get().purgeStrayRogueMons(player);
            }
        });

        CobblemonGuards.register();
    }

    /** Called from the command mixin. Ops bypass the block list. */
    public static boolean shouldBlockCommand(ServerCommandSource source, String command) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null || source.hasPermissionLevel(2) || !RunManager.isInRun(player)) {
            return false;
        }
        String root = command.startsWith("/") ? command.substring(1) : command;
        int space = root.indexOf(' ');
        root = (space < 0 ? root : root.substring(0, space)).toLowerCase(Locale.ROOT);
        // Also match namespaced forms like "cobblemon:pc".
        int colon = root.indexOf(':');
        String bare = colon < 0 ? root : root.substring(colon + 1);
        if (RogueConfig.get().blockedCommands.contains(bare)) {
            RunManager.message(player, "/" + bare + " is disabled while a run is active.", Formatting.RED);
            return true;
        }
        return false;
    }
}
