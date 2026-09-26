package org.CobbleUtils.cobbleroguelike.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.CobbleUtils.cobbleroguelike.RogueConfig;
import org.CobbleUtils.cobbleroguelike.compat.SpawnData;
import org.CobbleUtils.cobbleroguelike.run.RunManager;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * {@code /rogue} opens the menu. {@code /rogue end} ends the run. {@code /rogue endbattle} force-stops
 * the current battle and ends the run. {@code /rogue clean} force-cleans leftover run data and gives
 * back the saved party.
 * Admin: {@code /rogue admin end|endbattle|clean <player>}, {@code /rogue admin reload}.
 */
public final class RogueCommand {

    private RogueCommand() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("rogue")
                .executes(ctx -> {
                    RunManager.get().openCurrent(ctx.getSource().getPlayerOrThrow());
                    return 1;
                })
                .then(literal("end").executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                    if (!RunManager.isInRun(player)) {
                        ctx.getSource().sendError(Text.literal("You don't have a run in progress."));
                        return 0;
                    }
                    RunManager.get().end(player, "You ended your run.");
                    return 1;
                }))
                .then(literal("endbattle").executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                    if (!RunManager.isInRun(player) && !ctx.getSource().hasPermissionLevel(2)) {
                        ctx.getSource().sendError(Text.literal("You can only end battles during a rogue run."));
                        return 0;
                    }
                    RunManager.get().endBattle(player);
                    return 1;
                }))
                .then(literal("clean").executes(ctx -> {
                    RunManager.get().clean(ctx.getSource().getPlayerOrThrow());
                    return 1;
                }))
                .then(literal("admin")
                        .requires(source -> source.hasPermissionLevel(2))
                        .then(literal("end").then(argument("player", EntityArgumentType.player()).executes(ctx -> {
                            ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
                            RunManager.get().end(target, "An admin ended your run.");
                            ctx.getSource().sendFeedback(() -> Text.literal("Ended rogue run for " + target.getName().getString()), true);
                            return 1;
                        })))
                        .then(literal("endbattle").then(argument("player", EntityArgumentType.player()).executes(ctx -> {
                            ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
                            RunManager.get().endBattle(target);
                            ctx.getSource().sendFeedback(() -> Text.literal("Ending battle and rogue run for " + target.getName().getString()), true);
                            return 1;
                        })))
                        .then(literal("clean").then(argument("player", EntityArgumentType.player()).executes(ctx -> {
                            ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
                            boolean cleaned = RunManager.get().clean(target);
                            ctx.getSource().sendFeedback(() -> Text.literal((cleaned ? "Cleaned" : "Could not clean")
                                    + " rogue run data for " + target.getName().getString()), true);
                            return cleaned ? 1 : 0;
                        })))
                        .then(literal("reload").executes(ctx -> {
                            RogueConfig.load();
                            SpawnData.clearCache();
                            ctx.getSource().sendFeedback(() -> Text.literal("Reloaded CobbleRogueLike config"), true);
                            return 1;
                        }))));
    }
}
