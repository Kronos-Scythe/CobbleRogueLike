package org.CobbleUtils.cobbleroguelike.ui;

import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.CobbleUtils.cobbleroguelike.RogueConfig;
import org.CobbleUtils.cobbleroguelike.compat.CobblemonBridge;
import org.CobbleUtils.cobbleroguelike.compat.MoveBridge;
import org.CobbleUtils.cobbleroguelike.run.RunManager;
import org.CobbleUtils.cobbleroguelike.run.RunState;
import org.CobbleUtils.cobbleroguelike.shop.TutorService;

import java.util.ArrayList;
import java.util.List;

/** Move Tutor screens: pick a Pokémon, pick a move, pick the slot to replace. */
public final class TutorMenus {

    private static final int PAGE_SIZE = 45;
    private static final int[] SLOT_POSITIONS = {10, 12, 14, 16};

    private TutorMenus() {
    }

    public static void pickPokemon(ServerPlayerEntity player) {
        RunState state = runState(player);
        if (state == null) {
            return;
        }
        Menu menu = new Menu(Text.literal("Move Tutor - " + state.money + " coins"));
        List<Pokemon> party = CobblemonBridge.partyMembers(player);
        for (int i = 0; i < party.size() && i < 6; i++) {
            int index = i;
            Pokemon pokemon = party.get(i);
            List<Text> lore = new ArrayList<>();
            for (Text move : MoveBridge.currentMoves(pokemon)) {
                lore.add(move == null ? Text.literal("- (empty)") : Text.literal("- ").append(move));
            }
            lore.add(Text.literal("Click to see moves it can learn.").formatted(Formatting.YELLOW));
            menu.button(10 + i, Menu.stack(CobblemonBridge.icon(pokemon),
                    Text.literal(CobblemonBridge.describe(pokemon)).formatted(Formatting.AQUA), lore),
                    p -> moves(p, index, 0));
        }
        menu.icon(4, Menu.stack("minecraft:book", Text.literal("Move Tutor").formatted(Formatting.WHITE), List.of(
                Text.literal("Level-up moves it already qualifies for are free."),
                Text.literal("TM, tutor and egg moves cost " + RogueConfig.get().moveTutorPrice + " coins."),
                Text.literal("Replaced moves can be swapped back in"),
                Text.literal("from the Pokémon's summary screen."))));
        menu.button(22, Menu.stack("minecraft:oak_door", Text.literal("Back to run"), List.of()),
                p -> RunManager.get().openCurrent(p));
        menu.open(player);
    }

    public static void moves(ServerPlayerEntity player, int partyIndex, int page) {
        RunState state = runState(player);
        if (state == null) {
            return;
        }
        List<Pokemon> party = CobblemonBridge.partyMembers(player);
        if (partyIndex < 0 || partyIndex >= party.size()) {
            pickPokemon(player);
            return;
        }
        Pokemon pokemon = party.get(partyIndex);
        List<MoveBridge.Teachable> moves = MoveBridge.teachable(pokemon);
        int pages = Math.max(1, (moves.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int current = Math.max(0, Math.min(page, pages - 1));
        Menu menu = new Menu(Text.literal(pokemon.getSpecies().getName() + " - " + state.money + " coins"), 6);
        int price = RogueConfig.get().moveTutorPrice;
        for (int slot = 0; slot < PAGE_SIZE; slot++) {
            int index = current * PAGE_SIZE + slot;
            if (index >= moves.size()) {
                break;
            }
            MoveBridge.Teachable move = moves.get(index);
            List<Text> lore = new ArrayList<>();
            lore.add(Text.literal(capitalize(move.type()) + " - " + capitalize(move.category())));
            lore.add(Text.literal("Power: " + (move.power() > 0 ? move.power() : "-")
                    + "  Accuracy: " + (move.accuracy() > 0 ? move.accuracy() : "-")));
            lore.add(move.free()
                    ? Text.literal("Free").formatted(Formatting.GREEN)
                    : Text.literal(price + " coins").formatted(Formatting.GOLD));
            lore.add(Text.literal("Click to choose which move it replaces.").formatted(Formatting.YELLOW));
            menu.button(slot, Menu.stack(move.free() ? "minecraft:book" : "minecraft:enchanted_book",
                    move.displayName().copy().formatted(Formatting.WHITE), lore),
                    p -> replace(p, partyIndex, move));
        }
        if (moves.isEmpty()) {
            menu.icon(22, Menu.stack("minecraft:barrier", Text.literal("Nothing new to learn").formatted(Formatting.RED), List.of()));
        }
        if (current > 0) {
            menu.button(45, Menu.stack("minecraft:arrow", Text.literal("Previous page"), List.of()), p -> moves(p, partyIndex, current - 1));
        }
        if (current < pages - 1) {
            menu.button(53, Menu.stack("minecraft:arrow", Text.literal("Next page"), List.of()), p -> moves(p, partyIndex, current + 1));
        }
        menu.button(49, Menu.stack("minecraft:oak_door", Text.literal("Back"), List.of()), TutorMenus::pickPokemon);
        menu.open(player);
    }

    private static void replace(ServerPlayerEntity player, int partyIndex, MoveBridge.Teachable move) {
        List<Pokemon> party = CobblemonBridge.partyMembers(player);
        if (partyIndex < 0 || partyIndex >= party.size()) {
            pickPokemon(player);
            return;
        }
        Pokemon pokemon = party.get(partyIndex);
        Menu menu = new Menu(Text.literal("Replace which move?"));
        menu.icon(4, Menu.stack("minecraft:enchanted_book", move.displayName().copy().formatted(Formatting.AQUA), List.of(
                Text.literal(capitalize(move.type()) + " - " + capitalize(move.category())))));
        List<Text> current = MoveBridge.currentMoves(pokemon);
        for (int i = 0; i < 4; i++) {
            int slot = i;
            Text name = current.get(i);
            menu.button(SLOT_POSITIONS[i], Menu.stack(name == null ? "minecraft:glass_bottle" : "minecraft:paper",
                    name == null ? Text.literal("(empty slot)") : name.copy().formatted(Formatting.WHITE), List.of(
                            Text.literal(name == null ? "Click to learn here." : "Click to forget this move.").formatted(Formatting.YELLOW))),
                    p -> TutorService.teach(p, partyIndex, move.name(), slot));
        }
        menu.button(22, Menu.stack("minecraft:oak_door", Text.literal("Cancel"), List.of()), p -> moves(p, partyIndex, 0));
        menu.open(player);
    }

    private static String capitalize(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private static RunState runState(ServerPlayerEntity player) {
        if (!RunManager.isInRun(player)) {
            RunManager.message(player, "You need an active run.", Formatting.RED);
            return null;
        }
        if (CobblemonBridge.isInBattle(player)) {
            RunManager.message(player, "Finish your battle first.", Formatting.RED);
            return null;
        }
        return RunManager.get().state(player);
    }
}
