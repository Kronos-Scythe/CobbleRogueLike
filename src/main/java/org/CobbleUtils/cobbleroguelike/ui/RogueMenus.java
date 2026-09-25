package org.CobbleUtils.cobbleroguelike.ui;

import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.CobbleUtils.cobbleroguelike.compat.CobblemonBridge;
import org.CobbleUtils.cobbleroguelike.run.RunManager;
import org.CobbleUtils.cobbleroguelike.run.RunState;

import java.util.List;

/** All run screens. Slots 11/13/15 hold the main choices and row 3 holds controls. */
public final class RogueMenus {

    private static final int[] CHOICE_SLOTS = {11, 13, 15};

    private RogueMenus() {
    }

    public static void hub(ServerPlayerEntity player, boolean confirmAbandon) {
        Menu menu = new Menu(Text.literal("CobbleRogue"));
        if (!RunManager.isInRun(player)) {
            menu.button(13, Menu.stack("cobblemon:poke_ball", Text.literal("Start a run").formatted(Formatting.GREEN), List.of(
                    Text.literal("Begin with a single partner and"),
                    Text.literal("build your team as you go."),
                    Text.literal(""),
                    Text.literal("Your real party is stored safely").formatted(Formatting.YELLOW),
                    Text.literal("and given back when the run ends.").formatted(Formatting.YELLOW))),
                    p -> RunManager.get().start(p));
        } else if (confirmAbandon) {
            menu.button(11, Menu.stack("minecraft:lime_concrete", Text.literal("Keep playing").formatted(Formatting.GREEN), List.of()),
                    p -> RunManager.get().openCurrent(p));
            menu.button(15, Menu.stack("minecraft:red_concrete", Text.literal("Yes, end my run").formatted(Formatting.RED), List.of(
                    Text.literal("Your run Pokémon will be lost."))),
                    p -> RunManager.get().end(p, "You ended your run."));
        } else {
            RunState state = RunManager.get().state(player);
            menu.button(11, Menu.stack("minecraft:compass", Text.literal("Continue").formatted(Formatting.GREEN), List.of(
                    Text.literal("Floor " + state.floor))),
                    p -> RunManager.get().openCurrent(p));
            menu.button(15, Menu.stack("minecraft:barrier", Text.literal("End run").formatted(Formatting.RED), List.of()),
                    p -> hub(p, true));
        }
        menu.open(player);
    }

    public static void starter(ServerPlayerEntity player, RunState state) {
        Menu menu = new Menu(Text.literal("Choose your partner"));
        for (int i = 0; i < state.starterOptions.size() && i < CHOICE_SLOTS.length; i++) {
            int index = i;
            String species = state.starterOptions.get(i);
            menu.button(CHOICE_SLOTS[i], Menu.stack("cobblemon:poke_ball", Text.literal(pretty(species)).formatted(Formatting.AQUA), List.of(
                    Text.literal("Your only Pokémon at the start."),
                    Text.literal("Click to choose."))),
                    p -> RunManager.get().chooseStarter(p, index));
        }
        if (state.rerollsLeft > 0) {
            menu.button(22, Menu.stack("minecraft:ender_pearl", Text.literal("Reroll").formatted(Formatting.LIGHT_PURPLE), List.of(
                    Text.literal(state.rerollsLeft + " left"))),
                    p -> RunManager.get().rerollStarters(p));
        }
        addControls(menu, state);
        menu.open(player);
    }

    public static void path(ServerPlayerEntity player, RunState state) {
        Menu menu = new Menu(Text.literal("Floor " + state.floor + " - choose a path"));
        for (int i = 0; i < state.nodeChoices.size() && i < CHOICE_SLOTS.length; i++) {
            int index = i;
            RunState.NodeType node = state.nodeChoices.get(i);
            switch (node) {
                case ROUTE -> menu.button(CHOICE_SLOTS[i], Menu.stack("minecraft:grass_block",
                        Text.literal("Route").formatted(Formatting.GREEN), List.of(
                                Text.literal("Pick one of several wild Pokémon"),
                                Text.literal("to join your team."))),
                        p -> RunManager.get().chooseNode(p, index));
                case REST -> menu.button(CHOICE_SLOTS[i], Menu.stack("minecraft:campfire",
                        Text.literal("Rest stop").formatted(Formatting.GOLD), List.of(
                                Text.literal("Fully heal your team."))),
                        p -> RunManager.get().chooseNode(p, index));
            }
        }
        addControls(menu, state);
        menu.open(player);
    }

    public static void encounter(ServerPlayerEntity player, RunState state) {
        Menu menu = new Menu(Text.literal("Wild Pokémon appeared!"));
        for (int i = 0; i < state.encounterOptions.size() && i < CHOICE_SLOTS.length; i++) {
            int index = i;
            String properties = state.encounterOptions.get(i);
            menu.button(CHOICE_SLOTS[i], Menu.stack("cobblemon:poke_ball", Text.literal(describeProperties(properties)).formatted(Formatting.AQUA), List.of(
                    Text.literal("Click to add to your team."))),
                    p -> RunManager.get().chooseEncounter(p, index));
        }
        menu.button(22, Menu.stack("minecraft:oak_door", Text.literal("Skip").formatted(Formatting.GRAY), List.of()),
                p -> RunManager.get().chooseEncounter(p, -1));
        addControls(menu, state);
        menu.open(player);
    }

    public static void release(ServerPlayerEntity player, RunState state) {
        Menu menu = new Menu(Text.literal("Party full - release one?"));
        menu.icon(4, Menu.stack("cobblemon:poke_ball", Text.literal("New: " + describeProperties(state.pendingEncounter)).formatted(Formatting.AQUA), List.of()));
        List<Pokemon> party = CobblemonBridge.partyMembers(player);
        for (int i = 0; i < party.size(); i++) {
            int index = i;
            menu.button(10 + i, Menu.stack("cobblemon:great_ball", Text.literal(CobblemonBridge.describe(party.get(i))).formatted(Formatting.YELLOW), List.of(
                    Text.literal("Release this Pokémon"),
                    Text.literal("to make room."))),
                    p -> RunManager.get().releaseForPending(p, index));
        }
        menu.button(22, Menu.stack("minecraft:oak_door", Text.literal("Keep my party").formatted(Formatting.GRAY), List.of()),
                p -> RunManager.get().releaseForPending(p, -1));
        addControls(menu, state);
        menu.open(player);
    }

    private static void addControls(Menu menu, RunState state) {
        menu.icon(18, Menu.stack("minecraft:map", Text.literal("Floor " + state.floor).formatted(Formatting.WHITE), List.of()));
        menu.button(26, Menu.stack("minecraft:barrier", Text.literal("End run").formatted(Formatting.RED), List.of()),
                p -> hub(p, true));
    }

    private static String describeProperties(String properties) {
        String[] parts = properties.split(" ");
        String name = pretty(parts[0]);
        for (String part : parts) {
            if (part.startsWith("level=")) {
                return name + " Lv." + part.substring("level=".length());
            }
        }
        return name;
    }

    private static String pretty(String species) {
        if (species.isEmpty()) {
            return species;
        }
        return Character.toUpperCase(species.charAt(0)) + species.substring(1);
    }
}
