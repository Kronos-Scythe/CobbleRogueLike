package org.CobbleUtils.cobbleroguelike.ui;

import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.CobbleUtils.cobbleroguelike.RogueConfig;
import org.CobbleUtils.cobbleroguelike.compat.CobblemonBridge;
import org.CobbleUtils.cobbleroguelike.run.RunManager;
import org.CobbleUtils.cobbleroguelike.run.Biomes;
import org.CobbleUtils.cobbleroguelike.run.RunState;
import org.CobbleUtils.cobbleroguelike.run.Scaling;
import org.CobbleUtils.cobbleroguelike.run.TrainerGenerator;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** All run screens. Slots 11/13/15 hold the main choices and row 3 holds controls. */
public final class RogueMenus {

    private static final int[] CHOICE_SLOTS = {11, 13, 15};

    private RogueMenus() {
    }

    public static void hub(ServerPlayerEntity player, boolean confirmAbandon) {
        Menu menu = new Menu(Text.literal("CobbleRogue"));
        if (!RunManager.isInRun(player)) {
            menu.button(13, Menu.stack("cobblemon:poke_ball", Text.literal("Start a run").formatted(Formatting.GREEN), List.of(
                    Text.literal("Pick one of your own Pokémon as your"),
                    Text.literal("only partner and build a team as you go."),
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

    private static final int PICKER_PAGE_SIZE = 45;

    /**
     * A PC-style view of the player's party and PC. Clicking a Pokémon starts a run with a
     * copy of it, and the original is never changed.
     */
    public static void partnerPicker(ServerPlayerEntity player, int page) {
        List<Pokemon> party = CobblemonBridge.partyMembers(player);
        Set<UUID> partyIds = new HashSet<>();
        party.forEach(p -> partyIds.add(p.getUuid()));
        List<Pokemon> all = new ArrayList<>(party);
        all.addAll(CobblemonBridge.pcMembers(player));
        all.removeIf(CobblemonBridge::isRogue);

        int pages = Math.max(1, (all.size() + PICKER_PAGE_SIZE - 1) / PICKER_PAGE_SIZE);
        int current = Math.max(0, Math.min(page, pages - 1));
        Menu menu = new Menu(Text.literal("Choose your partner (" + (current + 1) + "/" + pages + ")"), 6);

        RogueConfig config = RogueConfig.get();
        for (int slot = 0; slot < PICKER_PAGE_SIZE; slot++) {
            int index = current * PICKER_PAGE_SIZE + slot;
            if (index >= all.size()) {
                break;
            }
            Pokemon pokemon = all.get(index);
            UUID id = pokemon.getUuid();
            List<Text> lore = new ArrayList<>();
            lore.add(Text.literal("Lv. " + pokemon.getLevel() + " - " + (partyIds.contains(id) ? "Party" : "PC")));
            if (pokemon.getShiny()) {
                lore.add(Text.literal("Shiny").formatted(Formatting.GOLD));
            }
            lore.add(Text.literal("Nature: ").append(CobblemonBridge.natureName(pokemon)));
            lore.add(Text.literal("Ability: ").append(CobblemonBridge.abilityName(pokemon)));
            lore.add(Text.literal(""));
            if (config.resetStarterLevel) {
                lore.add(Text.literal("Joins the run as a Lv. " + config.starterLevel + " copy.").formatted(Formatting.YELLOW));
            } else {
                lore.add(Text.literal("Joins the run as a copy.").formatted(Formatting.YELLOW));
            }
            lore.add(Text.literal("Click to start a run.").formatted(Formatting.GREEN));
            menu.button(slot, Menu.stack(CobblemonBridge.icon(pokemon),
                    CobblemonBridge.displayName(pokemon).copy().formatted(Formatting.AQUA), lore),
                    p -> RunManager.get().beginRun(p, id));
        }

        if (all.isEmpty()) {
            menu.icon(22, Menu.stack("minecraft:barrier", Text.literal("You don't have any Pokémon").formatted(Formatting.RED), List.of()));
        }
        if (current > 0) {
            menu.button(45, Menu.stack("minecraft:arrow", Text.literal("Previous page"), List.of()),
                    p -> partnerPicker(p, current - 1));
        }
        menu.icon(49, Menu.stack("minecraft:book", Text.literal("Pick your partner").formatted(Formatting.WHITE), List.of(
                Text.literal("You start the run with a copy of"),
                Text.literal("the Pokémon you pick, and nothing else."),
                Text.literal("Your real Pokémon never gains EXP,"),
                Text.literal("evolves or changes during a run."))));
        if (current < pages - 1) {
            menu.button(53, Menu.stack("minecraft:arrow", Text.literal("Next page"), List.of()),
                    p -> partnerPicker(p, current + 1));
        }
        menu.open(player);
    }

    public static void path(ServerPlayerEntity player, RunState state) {
        Menu menu = new Menu(Text.literal("Floor " + state.floor + " - " + Biomes.get(state.biome).name));
        for (int i = 0; i < state.nodeChoices.size() && i < CHOICE_SLOTS.length; i++) {
            int index = i;
            RunState.NodeType node = state.nodeChoices.get(i);
            switch (node) {
                case ROUTE -> menu.button(CHOICE_SLOTS[i], Menu.stack("minecraft:grass_block",
                        Text.literal("Route").formatted(Formatting.GREEN), List.of(
                                Text.literal("Pick one of several wild Pokémon"),
                                Text.literal("from the " + Biomes.get(state.biome).name + " to join your team."))),
                        p -> RunManager.get().chooseNode(p, index));
                case REST -> menu.button(CHOICE_SLOTS[i], Menu.stack("minecraft:campfire",
                        Text.literal("Rest stop").formatted(Formatting.GOLD), List.of(
                                Text.literal("Fully heal your team."))),
                        p -> RunManager.get().chooseNode(p, index));
                case TRAINER -> menu.button(CHOICE_SLOTS[i], Menu.stack("minecraft:iron_sword",
                        Text.literal("Trainer battle").formatted(Formatting.RED), List.of(
                                Text.literal("Fight a trainer around Lv. " + Scaling.trainerLevel(state) + "."),
                                Text.literal("Your team gains EXP up to the level cap."))),
                        p -> RunManager.get().chooseNode(p, index));
                case GYM -> menu.button(CHOICE_SLOTS[i], Menu.stack("minecraft:gold_block",
                        Text.literal("Gym battle").formatted(Formatting.GOLD, Formatting.BOLD), List.of(
                                Text.literal("Badge " + (state.badges + 1) + " of " + RogueConfig.get().gymCount + "."),
                                Text.literal("Leader's ace is Lv. " + Scaling.levelCap(state.badges) + "."),
                                Text.literal("Losing ends the run!").formatted(Formatting.RED))),
                        p -> RunManager.get().chooseNode(p, index));
                case CHAMPION -> menu.button(CHOICE_SLOTS[i], Menu.stack("minecraft:dragon_head",
                        Text.literal("The Champion").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD), List.of(
                                Text.literal("The final battle, Lv. " + Scaling.levelCap(state.badges) + "."),
                                Text.literal("Win to complete your run!"))),
                        p -> RunManager.get().chooseNode(p, index));
                case LEGENDARY -> menu.button(CHOICE_SLOTS[i], Menu.stack("minecraft:nether_star",
                        Text.literal("Legendary encounter").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD), List.of(
                                Text.literal("A legendary Pokémon has appeared!"),
                                Text.literal("Defeat it and it joins your team."),
                                Text.literal("You can still walk away after seeing it."))),
                        p -> RunManager.get().chooseNode(p, index));
            }
        }
        addControls(menu, state);
        menu.open(player);
    }

    public static void battle(ServerPlayerEntity player, RunState state) {
        Menu menu = new Menu(Text.literal(state.battleName));
        String icon = switch (state.battleKind) {
            case GYM -> "minecraft:gold_block";
            case CHAMPION -> "minecraft:dragon_head";
            case LEGENDARY -> "minecraft:nether_star";
            default -> "minecraft:iron_sword";
        };
        boolean legendary = state.battleKind == RunState.NodeType.LEGENDARY && !state.battleTeam.isEmpty();
        int maxLevel = 0;
        for (String member : state.battleTeam) {
            maxLevel = Math.max(maxLevel, levelOf(member));
        }
        List<Text> lore = new ArrayList<>();
        if (!state.battleType.isEmpty()) {
            lore.add(Text.literal("Type: " + TrainerGenerator.capitalize(state.battleType)).formatted(Formatting.YELLOW));
        }
        if (legendary) {
            lore.add(Text.literal(describeProperties(state.battleTeam.get(0))).formatted(Formatting.LIGHT_PURPLE));
            lore.add(Text.literal("Defeat it and it joins your team!").formatted(Formatting.GREEN));
        } else {
            lore.add(Text.literal("Team: " + state.battleTeam.size() + " Pokémon"));
            lore.add(Text.literal("Strongest: Lv. " + maxLevel));
        }
        lore.add(Text.literal(""));
        lore.add(Text.literal("Your damage carries over between battles.").formatted(Formatting.GRAY));
        lore.add(Text.literal("Losing or forfeiting ends the run!").formatted(Formatting.RED));
        ItemStack iconStack = legendary ? speciesIcon(state.battleTeam.get(0)) : Menu.stack(icon, Text.literal(""), List.of());
        menu.icon(13, Menu.stack(iconStack, Text.literal(state.battleName).formatted(Formatting.GOLD), lore));
        if (legendary) {
            menu.button(24, Menu.stack("minecraft:oak_door", Text.literal("Leave it").formatted(Formatting.GRAY), List.of(
                    Text.literal("Skip this encounter and move on."))),
                    p -> RunManager.get().skipLegendary(p));
        }
        menu.button(22, Menu.stack("minecraft:lime_concrete", Text.literal("Fight!").formatted(Formatting.GREEN, Formatting.BOLD), List.of()),
                p -> RunManager.get().startBattle(p));
        addControls(menu, state);
        menu.open(player);
    }

    private static int levelOf(String properties) {
        for (String part : properties.split(" ")) {
            if (part.startsWith("level=")) {
                try {
                    return Integer.parseInt(part.substring("level=".length()));
                } catch (NumberFormatException ignored) {
                    return 0;
                }
            }
        }
        return 0;
    }

    public static void encounter(ServerPlayerEntity player, RunState state) {
        Menu menu = new Menu(Text.literal("Wild Pokémon appeared!"));
        for (int i = 0; i < state.encounterOptions.size() && i < CHOICE_SLOTS.length; i++) {
            int index = i;
            String properties = state.encounterOptions.get(i);
            menu.button(CHOICE_SLOTS[i], Menu.stack(speciesIcon(properties), Text.literal(describeProperties(properties)).formatted(Formatting.AQUA), List.of(
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
        menu.icon(4, Menu.stack(speciesIcon(state.pendingEncounter), Text.literal("New: " + describeProperties(state.pendingEncounter)).formatted(Formatting.AQUA), List.of()));
        List<Pokemon> party = CobblemonBridge.partyMembers(player);
        for (int i = 0; i < party.size(); i++) {
            int index = i;
            menu.button(10 + i, Menu.stack(CobblemonBridge.icon(party.get(i)), Text.literal(CobblemonBridge.describe(party.get(i))).formatted(Formatting.YELLOW), List.of(
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
        menu.icon(18, Menu.stack("minecraft:map", Text.literal("Floor " + state.floor).formatted(Formatting.WHITE), List.of(
                Text.literal("Badges: " + state.badges + "/" + RogueConfig.get().gymCount),
                Text.literal("Biome: " + Biomes.get(state.biome).name).formatted(Formatting.AQUA),
                Text.literal("Level cap: " + Scaling.levelCap(state.badges)),
                Text.literal("Coins: " + state.money).formatted(Formatting.GOLD))));
        menu.button(19, Menu.stack("minecraft:emerald", Text.literal("Shop").formatted(Formatting.GREEN), List.of(
                Text.literal(state.money + " coins"),
                Text.literal("Healing, battle items, training..."))), ShopMenus::shop);
        menu.button(20, Menu.stack("minecraft:chest", Text.literal("Bag & team items").formatted(Formatting.AQUA), List.of(
                Text.literal("Use items and manage held items."))), p -> ShopMenus.bag(p, 0));
        menu.button(26, Menu.stack("minecraft:barrier", Text.literal("End run").formatted(Formatting.RED), List.of()),
                p -> hub(p, true));
    }

    /** Model icon for a property string like {@code "zubat level=7"}, falling back to a Poké Ball. */
    /** Model icon matching the property string (form, shiny...), falling back to a Poké Ball. */
    private static ItemStack speciesIcon(String properties) {
        ItemStack icon = CobblemonBridge.iconFromProperties(properties);
        return icon != null ? icon : Menu.stack("cobblemon:poke_ball", Text.literal(""), List.of());
    }

    /** e.g. {@code "vulpix alolan level=12 shiny=yes"} becomes "Vulpix (Alolan) Lv.12 ★ Shiny". */
    private static String describeProperties(String properties) {
        String[] parts = properties.split(" ");
        String species = parts[0].contains(":") ? parts[0].substring(parts[0].indexOf(':') + 1) : parts[0];
        StringBuilder name = new StringBuilder(pretty(species));
        List<String> forms = new ArrayList<>();
        String level = "";
        boolean shiny = false;
        boolean hidden = false;
        for (int i = 1; i < parts.length; i++) {
            String part = parts[i];
            if (part.startsWith("level=")) {
                level = part.substring("level=".length());
            } else if (part.equals("shiny=yes")) {
                shiny = true;
            } else if (part.equals("hiddenability=yes")) {
                hidden = true;
            } else if (!part.contains("=")) {
                forms.add(pretty(part));
            }
        }
        if (!forms.isEmpty()) {
            name.append(" (").append(String.join(", ", forms)).append(")");
        }
        if (!level.isEmpty()) {
            name.append(" Lv.").append(level);
        }
        if (shiny) {
            name.append(" ★ Shiny");
        }
        if (hidden) {
            name.append(" [Hidden Ability]");
        }
        return name.toString();
    }

    private static String pretty(String species) {
        if (species.isEmpty()) {
            return species;
        }
        return Character.toUpperCase(species.charAt(0)) + species.substring(1);
    }
}
