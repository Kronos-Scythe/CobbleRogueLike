package org.CobbleUtils.cobbleroguelike.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.CobbleUtils.cobbleroguelike.RogueConfig;
import org.CobbleUtils.cobbleroguelike.compat.SpawnData;
import org.CobbleUtils.cobbleroguelike.run.RunManager;
import org.CobbleUtils.cobbleroguelike.ui.RewardMenus;
import org.CobbleUtils.cobbleroguelike.ui.TutorMenus;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * {@code /rogue} opens the menu. {@code /rogue shop} spends Rogue Tokens on real items.
 * {@code /rogue tutor} opens the run's Move Tutor. {@code /rogue save} saves & leaves the run (continue from {@code /rogue}). {@code /rogue end} ends the run. {@code /rogue endbattle} force-stops
 * the current battle and ends the run. {@code /rogue clean} force-cleans leftover run data and gives
 * back the saved party.
 * Admin: {@code /rogue admin end|endbattle|clean <player>}, {@code /rogue admin tokens <player> <amount>},
 * {@code /rogue admin reload}.
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
                .then(literal("shop").executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                    if (RunManager.isInRun(player)) {
                        ctx.getSource().sendError(Text.literal("The Rogue Shop is on the /rogue start page. Save & leave your run first."));
                        return 0;
                    }
                    RewardMenus.shop(player);
                    return 1;
                }))
                .then(literal("save").executes(ctx -> {
                    RunManager.get().saveAndLeave(ctx.getSource().getPlayerOrThrow());
                    return 1;
                }))
                .then(literal("tutor").executes(ctx -> {
                    TutorMenus.pickPokemon(ctx.getSource().getPlayerOrThrow());
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
                        .then(literal("tokens").then(argument("player", EntityArgumentType.player())
                                .then(argument("amount", IntegerArgumentType.integer()).executes(ctx -> {
                                    ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
                                    int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                    boolean ok = RunManager.get().changeTokens(target, amount);
                                    int total = RunManager.get().tokens(target);
                                    ctx.getSource().sendFeedback(() -> Text.literal((ok ? "Changed" : "Could not change")
                                            + " Rogue Tokens for " + target.getName().getString() + " (now " + total + ")"), true);
                                    return ok ? 1 : 0;
                                }))))
                        .then(literal("reload").executes(ctx -> {
                            RogueConfig.load();
                            SpawnData.clearCache();
                            ctx.getSource().sendFeedback(() -> Text.literal("Reloaded CobbleRogueLike config"), true);
                            return 1;
                        }))));
    }
}
