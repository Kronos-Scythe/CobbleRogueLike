package org.CobbleUtils.cobbleroguelike.ui;

import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.CobbleUtils.cobbleroguelike.RogueConfig;
import org.CobbleUtils.cobbleroguelike.compat.CobblemonBridge;
import org.CobbleUtils.cobbleroguelike.run.RunManager;
import org.CobbleUtils.cobbleroguelike.run.Biomes;
import org.CobbleUtils.cobbleroguelike.run.Modifiers;
import org.CobbleUtils.cobbleroguelike.run.RunState;
import org.CobbleUtils.cobbleroguelike.run.Scaling;
import org.CobbleUtils.cobbleroguelike.run.TrainerGenerator;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** All run screens. Slots 11/13/15 hold the main choices and row 3 holds controls. */
public final class RogueMenus {

    private static final int[] CHOICE_SLOTS = {11, 13, 15};

    private RogueMenus() {
    }

    /**
     * The start page. The run screen shows only the main choices as cards (Start a run, Co-op run,
     * or Continue a saved run); How to play and the Rogue Shop are footer buttons.
     */
    public static void hub(ServerPlayerEntity player, boolean confirmAbandon) {
        Menu menu = new Menu(Text.literal("CobbleRogue")).layout(Menu.Layout.CARDS);
        RunState saved = RunManager.isInRun(player) ? null : RunManager.get().savedRun(player);
        Consumer<ServerPlayerEntity> backToHub = p -> hub(p, false);
        if (saved != null && confirmAbandon) {
            menu.button(11, Menu.stack("minecraft:lime_concrete", Text.literal("Keep my saved run").formatted(Formatting.GREEN), List.of(
                    Text.literal("Go back to the start page."))),
                    backToHub);
            menu.button(15, Menu.stack("minecraft:red_concrete", Text.literal("Yes, end my saved run").formatted(Formatting.RED), List.of(
                    Text.literal("Its Pokémon will be lost."),
                    Text.literal("You still get Rogue Tokens for its progress."))),
                    p -> RunManager.get().abandonSaved(p));
        } else if (saved != null) {
            menu.footer(11, guideButton(), p -> guide(p, backToHub));
            menu.button(13, Menu.stack("minecraft:compass", Text.literal("Continue saved run").formatted(Formatting.GREEN, Formatting.BOLD), List.of(
                    Text.literal("Floor " + saved.floor + " - " + Biomes.get(saved.biome).name),
                    Text.literal(saved.badges + " badges, " + saved.money + " coins"),
                    Text.literal(saved.suspendedParty.size() + " Pokémon waiting"),
                    Text.literal(""),
                    Text.literal("Your real party is stored while you play.").formatted(Formatting.YELLOW))),
                    p -> RunManager.get().continueSaved(p));
            menu.footer(15, shopButton(player), RewardMenus::shop);
            menu.footer(22, Menu.stack("minecraft:barrier", Text.literal("End saved run").formatted(Formatting.RED), List.of(
                    Text.literal("Asks for confirmation."))),
                    p -> hub(p, true));
        } else if (RunManager.get().inLobby(player)) {
            menu.footer(11, guideButton(), p -> guide(p, backToHub));
            menu.button(13, Menu.stack("cobblemon:poke_ball", Text.literal("Pick your partner").formatted(Formatting.GREEN, Formatting.BOLD), List.of(
                    Text.literal("You're in a co-op lobby."),
                    Text.literal("The run starts once you both picked."))),
                    p -> partnerPicker(p, 0));
            menu.button(15, Menu.stack("minecraft:oak_door", Text.literal("Leave lobby").formatted(Formatting.RED), List.of(
                    Text.literal("Cancel the co-op run."))),
                    p -> {
                        RunManager.get().leaveLobby(p);
                        hub(p, false);
                    });
        } else if (!RunManager.isInRun(player)) {
            menu.footer(11, guideButton(), p -> guide(p, backToHub));
            menu.button(13, Menu.stack("cobblemon:poke_ball", Text.literal("Start a run").formatted(Formatting.GREEN, Formatting.BOLD), List.of(
                    Text.literal("Pick one of your own Pokémon as your"),
                    Text.literal("only partner and build a team as you go."),
                    Text.literal(""),
                    Text.literal("Your real party is stored safely").formatted(Formatting.YELLOW),
                    Text.literal("and given back when the run ends.").formatted(Formatting.YELLOW))),
                    p -> RunManager.get().start(p));
            menu.footer(15, shopButton(player), RewardMenus::shop);
            String inviter = RunManager.get().pendingInviteFrom(player);
            if (inviter != null) {
                menu.button(22, Menu.stack("minecraft:player_head", Text.literal("Join " + inviter + "'s co-op run").formatted(Formatting.AQUA, Formatting.BOLD), List.of(
                        Text.literal("You were invited to a co-op run!"),
                        Text.literal("Click to accept."))),
                        p -> RunManager.get().accept(p));
            } else {
                menu.button(22, Menu.stack("minecraft:player_head", Text.literal("Co-op run").formatted(Formatting.AQUA, Formatting.BOLD), List.of(
                        Text.literal("Invite a friend: every battle is a 2 vs 2,"),
                        Text.literal("each of you with up to " + Math.max(1, Math.min(6, RogueConfig.get().coopPartyLimit)) + " Pokémon."))),
                        RogueMenus::invite);
            }
        } else if (confirmAbandon) {
            menu.button(11, Menu.stack("minecraft:lime_concrete", Text.literal("Keep playing").formatted(Formatting.GREEN), List.of(
                    Text.literal("Back to your run."))),
                    p -> RunManager.get().openCurrent(p));
            RunState ending = RunManager.get().state(player);
            menu.button(15, Menu.stack("minecraft:red_concrete", Text.literal("Yes, end my run").formatted(Formatting.RED), List.of(
                    Text.literal(ending != null && ending.isCoop() ? "Ends the run for both of you." : "Your run Pokémon will be lost."),
                    Text.literal("You still get Rogue Tokens for your progress."))),
                    p -> RunManager.get().end(p, "You ended your run."));
        } else {
            RunState state = RunManager.get().state(player);
            menu.button(11, Menu.stack("minecraft:compass", Text.literal("Continue").formatted(Formatting.GREEN), List.of(
                    Text.literal("Floor " + state.floor))),
                    p -> RunManager.get().openCurrent(p));
            menu.button(15, Menu.stack("minecraft:barrier", Text.literal("End run").formatted(Formatting.RED), List.of(
                    Text.literal("Asks for confirmation."))),
                    p -> hub(p, true));
        }
        menu.open(player);
    }

    private static ItemStack guideButton() {
        return Menu.stack("minecraft:book", Text.literal("How to play").formatted(Formatting.WHITE), List.of(
                Text.literal("Rules, bosses, co-op and commands.")));
    }

    private static ItemStack shopButton(ServerPlayerEntity player) {
        return Menu.stack("minecraft:amethyst_shard", Text.literal("Rogue Shop").formatted(Formatting.LIGHT_PURPLE), List.of(
                Text.literal(RunManager.get().tokens(player) + " Rogue Tokens"),
                Text.literal("Spend tokens from past runs on real items.")));
    }

    /** The How to play page. {@code back} returns to wherever it was opened from. */
    public static void guide(ServerPlayerEntity player, Consumer<ServerPlayerEntity> back) {
        RogueConfig config = RogueConfig.get();
        Menu menu = new Menu(Text.literal("How to play")).layout(Menu.Layout.PAGE);
        List<ItemStack> sections = List.of(
                Menu.stack("cobblemon:poke_ball", Text.literal("Your run").formatted(Formatting.GREEN), List.of(
                        Text.literal("Pick one of your own Pokémon from your party or PC. A copy joins the run at Lv. "
                                + config.starterLevel + ", and that's your whole team to start."),
                        Text.literal("Your real Pokémon are stored safely and never gain EXP, evolve or change."))),
                Menu.stack("minecraft:filled_map", Text.literal("Path cards").formatted(Formatting.AQUA), List.of(
                        Text.literal("Each floor, pick one of 3 cards:"),
                        Text.literal("Route: add a wild Pokémon from the current biome."),
                        Text.literal("Trainer: a battle that pays coins."),
                        Text.literal("Rest stop: fully heal your team."),
                        Text.literal("Legendary: beat it and it joins you (rare)."))),
                Menu.stack("minecraft:gold_block", Text.literal("Gyms and the Champion").formatted(Formatting.GOLD), List.of(
                        Text.literal("Every " + config.gymEvery + "th floor is a gym. Each badge raises the level cap."),
                        Text.literal("After " + config.gymCount + " badges come the Elite Four and the Champion. Beat them to win."))),
                Menu.stack("minecraft:writable_book", Text.literal("Boss prep").formatted(Formatting.YELLOW), List.of(
                        Text.literal("Before every boss, once each: train your team to the level cap, draft a counter Pokémon, or fully heal."))),
                Menu.stack("minecraft:emerald", Text.literal("Coins, Shop and Bag").formatted(Formatting.GREEN), List.of(
                        Text.literal("Coins from battles buy healing, held items, mints and more in the run Shop."),
                        Text.literal("The Bag uses items and moves held items between your Pokémon. The Move Tutor teaches moves."))),
                Menu.stack("minecraft:skeleton_skull", Text.literal("Losing").formatted(Formatting.RED), List.of(
                        Text.literal("Losing or forfeiting a battle ends the run. You always earn Rogue Tokens for how far you got."),
                        Text.literal("Spend them on real items in the Rogue Shop on the start page."))),
                Menu.stack("minecraft:player_head", Text.literal("Co-op").formatted(Formatting.AQUA), List.of(
                        Text.literal("Invite a friend: every battle is a 2 vs 2, and each of you keeps up to "
                                + Math.max(1, Math.min(6, config.coopPartyLimit)) + " Pokémon."),
                        Text.literal("Floors, badges and coins are shared. Both press Ready to start a battle."))),
                Menu.stack("minecraft:ender_chest", Text.literal("Saving").formatted(Formatting.YELLOW), List.of(
                        Text.literal("Save & leave between battles to get your real party back. Continue later from /rogue."))),
                Menu.stack("minecraft:command_block", Text.literal("Commands").formatted(Formatting.GRAY), List.of(
                        Text.literal("/rogue, /rogue shop, /rogue save, /rogue tutor, /rogue invite <player>"),
                        Text.literal("/rogue end, /rogue endbattle (stuck battles), /rogue clean"))));
        for (int i = 0; i < sections.size() && i < 9; i++) {
            menu.icon(9 + i, sections.get(i));
            menu.role(9 + i, Menu.Role.CONTENT);
        }
        menu.back(22, "Back", back);
        menu.open(player);
    }

    /** Run setup: toggle modifiers, then go on to pick a partner. */
    public static void setup(ServerPlayerEntity player) {
        Set<String> chosen = RunManager.get().pendingModifiers(player);
        Menu menu = new Menu(Text.literal("New run - modifiers")).layout(Menu.Layout.CARDS);
        int[] slots = {10, 11, 12, 13, 14, 15};
        for (int i = 0; i < Modifiers.ALL.size() && i < slots.length; i++) {
            Modifiers.Info info = Modifiers.ALL.get(i);
            boolean on = chosen.contains(info.id());
            List<Text> lore = new ArrayList<>();
            lore.add(Text.literal(info.description()));
            long bonus = Math.round(Modifiers.bonus(info.id()) * 100);
            lore.add(bonus == 0 ? Text.literal("No token bonus").formatted(Formatting.DARK_GRAY)
                    : Text.literal((bonus > 0 ? "+" : "") + bonus + "% Rogue Tokens").formatted(Formatting.LIGHT_PURPLE));
            lore.add(on ? Text.literal("ON - click to turn off").formatted(Formatting.GREEN)
                    : Text.literal("OFF - click to turn on").formatted(Formatting.GRAY));
            ItemStack icon = Menu.stack(info.icon(), Text.literal(""), List.of());
            if (on) {
                icon.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true); // glows while on
            }
            menu.button(slots[i], Menu.stack(icon, Text.literal(info.name()).formatted(on ? Formatting.GOLD : Formatting.WHITE), lore),
                    p -> RunManager.get().toggleModifier(p, info.id()));
        }
        menu.icon(4, Menu.stack("minecraft:book", Text.literal("Run modifiers").formatted(Formatting.WHITE), List.of(
                Text.literal("Optional rules for extra Rogue Tokens."),
                Text.literal("Current bonus: +" + Math.round(Modifiers.totalBonus(chosen) * 100) + "%").formatted(Formatting.LIGHT_PURPLE))));
        menu.button(16, Menu.stack("cobblemon:poke_ball", Text.literal("Choose your partner").formatted(Formatting.GREEN, Formatting.BOLD), List.of(
                Text.literal("Continue to the partner picker."))),
                p -> partnerPicker(p, 0));
        menu.role(16, Menu.Role.FOOTER);
        menu.back(22, "Back", p -> hub(p, false));
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
        Menu menu = new Menu(Text.literal("Choose your partner (" + (current + 1) + "/" + pages + ")"), 6)
                .layout(Menu.Layout.GRID);

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
            menu.footer(45, Menu.stack("minecraft:arrow", Text.literal("Previous page"), List.of()),
                    p -> partnerPicker(p, current - 1));
        }
        menu.icon(49, Menu.stack("minecraft:book", Text.literal("Pick your partner").formatted(Formatting.WHITE), List.of(
                Text.literal("You start the run with a copy of"),
                Text.literal("the Pokémon you pick, and nothing else."),
                Text.literal("Your real Pokémon never gains EXP,"),
                Text.literal("evolves or changes during a run."))));
        if (current < pages - 1) {
            menu.footer(53, Menu.stack("minecraft:arrow", Text.literal("Next page"), List.of()),
                    p -> partnerPicker(p, current + 1));
        }
        // A co-op lobby comes from the start page, a solo run from the modifiers.
        menu.back(48, "Back", p -> {
            if (RunManager.get().inLobby(p)) {
                hub(p, false);
            } else {
                setup(p);
            }
        });
        menu.open(player);
    }

    public static void path(ServerPlayerEntity player, RunState state) {
        Menu menu = new Menu(Text.literal("Floor " + state.floor + " - " + Biomes.get(state.biome).name), RUN_ROWS)
                .layout(Menu.Layout.CARDS).screenTitle(Text.literal("Choose your path"));
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
                                Text.literal(bossDoubles() ? "Double battle, competitive sets." : "Competitive sets.").formatted(Formatting.YELLOW),
                                Text.literal("Losing ends the run!").formatted(Formatting.RED))),
                        p -> RunManager.get().chooseNode(p, index));
                case ELITE -> menu.button(CHOICE_SLOTS[i], Menu.stack("minecraft:amethyst_block",
                        Text.literal("Elite Four").formatted(Formatting.DARK_PURPLE, Formatting.BOLD), List.of(
                                Text.literal("Member " + (state.eliteWins + 1) + " of " + RogueConfig.get().eliteCount + "."),
                                Text.literal("Around Lv. " + Scaling.eliteLevel(state) + ", full competitive team."),
                                Text.literal(bossDoubles() ? "Double battle." : "Single battle.").formatted(Formatting.YELLOW),
                                Text.literal("Losing ends the run!").formatted(Formatting.RED))),
                        p -> RunManager.get().chooseNode(p, index));
                case CHAMPION -> menu.button(CHOICE_SLOTS[i], Menu.stack("minecraft:dragon_head",
                        Text.literal("The Champion").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD), List.of(
                                Text.literal("The final battle, Lv. " + Scaling.levelCap(state.badges) + "."),
                                Text.literal(bossDoubles() ? "Double battle, full competitive team." : "Full competitive team.").formatted(Formatting.YELLOW),
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
        addControls(menu, state, player.getUuid());
        menu.open(player);
    }

    public static void battle(ServerPlayerEntity player, RunState state) {
        Menu menu = new Menu(Text.literal(state.battleName), RUN_ROWS).layout(Menu.Layout.CARDS);
        String icon = switch (state.battleKind) {
            case GYM -> "minecraft:gold_block";
            case ELITE -> "minecraft:amethyst_block";
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
        if (state.battleDoubles) {
            lore.add(Text.literal("Double Battle!").formatted(Formatting.AQUA, Formatting.BOLD));
        }
        if (!state.battleArchetype.isEmpty()) {
            lore.add(Text.literal("Team style: " + state.battleArchetype).formatted(Formatting.GOLD));
        }
        if (state.battleGimmick.equals("mega")) {
            lore.add(Text.literal("Their ace can Mega Evolve!").formatted(Formatting.LIGHT_PURPLE));
        } else if (state.battleGimmick.equals("tera")) {
            lore.add(Text.literal("Their ace can Terastallize!").formatted(Formatting.LIGHT_PURPLE));
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
        if (RunManager.isBoss(state.battleKind)) {
            addBossPrep(menu, state, player);
        }
        if (state.isCoop()) {
            boolean meReady = state.ready.contains(player.getUuid());
            UUID other = state.other(player.getUuid());
            boolean otherReady = other != null && state.ready.contains(other);
            String partner = RunManager.get().partnerName(state, player.getUuid());
            menu.button(22, Menu.stack(meReady ? "minecraft:lime_concrete" : "minecraft:yellow_concrete",
                    Text.literal(meReady ? "Ready! (" + (otherReady ? 2 : 1) + "/2)" : "Ready up (" + (otherReady ? 1 : 0) + "/2)")
                            .formatted(Formatting.GREEN, Formatting.BOLD), List.of(
                            Text.literal("You: " + (meReady ? "ready" : "not ready")).formatted(meReady ? Formatting.GREEN : Formatting.GRAY),
                            Text.literal(partner + ": " + (otherReady ? "ready" : "not ready")).formatted(otherReady ? Formatting.GREEN : Formatting.GRAY),
                            Text.literal("The battle starts when you're both ready").formatted(Formatting.DARK_GRAY),
                            Text.literal("and standing together.").formatted(Formatting.DARK_GRAY))),
                    p -> RunManager.get().startBattle(p));
            if (!state.battleName2.isEmpty()) {
                menu.icon(15, Menu.stack(state.battleKind == RunState.NodeType.LEGENDARY && !state.battleTeam2.isEmpty()
                                ? speciesIcon(state.battleTeam2.get(0)) : Menu.stack("minecraft:iron_sword", Text.literal(""), List.of()),
                        Text.literal(state.battleName2).formatted(Formatting.GOLD), List.of(
                                Text.literal("Second opponent: " + state.battleTeam2.size() + " Pokémon"),
                                Text.literal("Co-op battles are 2 vs 2.").formatted(Formatting.AQUA))));
            }
        } else {
            menu.button(22, Menu.stack("minecraft:lime_concrete", Text.literal("Fight!").formatted(Formatting.GREEN, Formatting.BOLD), List.of()),
                    p -> RunManager.get().startBattle(p));
        }
        addControls(menu, state, player.getUuid());
        menu.open(player);
    }

    /** Top row of a boss preview: free, once-per-boss prep so gyms are beatable without grinding. */
    private static void addBossPrep(Menu menu, RunState state, ServerPlayerEntity player) {
        RogueConfig config = RogueConfig.get();
        int cap = Scaling.levelCap(state.badges);
        menu.icon(1, Menu.stack("minecraft:writable_book", Text.literal("Boss prep").formatted(Formatting.GOLD), List.of(
                Text.literal("Free help before this fight,"),
                Text.literal("once each per boss."))));
        menu.chestOnly(1); // on the run screen the prep options speak for themselves
        if (config.prepTrainToCap) {
            boolean used = RunManager.prepUsed(state, player, "train");
            menu.button(3, Menu.stack(used ? "minecraft:gray_dye" : "cobblemon:exp_candy_xl",
                    Text.literal("Train to level cap").formatted(used ? Formatting.GRAY : Formatting.GREEN), List.of(
                            Text.literal("Raise your whole team to Lv. " + cap + "."),
                            Text.literal("New moves and evolutions happen as usual."),
                            used ? Text.literal("Used").formatted(Formatting.GRAY) : Text.literal("Click to train").formatted(Formatting.YELLOW))),
                    p -> RunManager.get().prepTrain(p));
        }
        if (config.prepDraft && Modifiers.has(state, Modifiers.DRAFT) && !state.draftOptions.isEmpty()) {
            boolean used = RunManager.prepUsed(state, player, "draft");
            menu.button(4, Menu.stack(used ? "minecraft:gray_dye" : "cobblemon:great_ball",
                    Text.literal("Draft a counter").formatted(used ? Formatting.GRAY : Formatting.AQUA), List.of(
                            Text.literal("Pick 1 of " + state.draftOptions.size() + " Pokémon at Lv. " + cap),
                            Text.literal(state.battleType.isEmpty() ? "that can take on this team."
                                    : "strong against " + TrainerGenerator.capitalize(state.battleType) + "."),
                            used ? Text.literal("Used").formatted(Formatting.GRAY) : Text.literal("Click to see them").formatted(Formatting.YELLOW))),
                    p -> draft(p, RunManager.get().state(p)));
        }
        if (config.prepHeal) {
            boolean used = RunManager.prepUsed(state, player, "heal");
            menu.button(5, Menu.stack(used ? "minecraft:gray_dye" : "cobblemon:full_restore",
                    Text.literal("Full heal").formatted(used ? Formatting.GRAY : Formatting.LIGHT_PURPLE), List.of(
                            Text.literal("Restore your team's HP, PP and status."),
                            used ? Text.literal("Used").formatted(Formatting.GRAY) : Text.literal("Click to heal").formatted(Formatting.YELLOW))),
                    p -> RunManager.get().prepHeal(p));
        }
    }

    /** Boss prep: choose a counter Pokémon. */
    public static void draft(ServerPlayerEntity player, RunState state) {
        if (state == null) {
            return;
        }
        Menu menu = new Menu(Text.literal("Draft a counter"), RUN_ROWS).layout(Menu.Layout.CARDS);
        for (int i = 0; i < state.draftOptions.size() && i < CHOICE_SLOTS.length; i++) {
            int index = i;
            String properties = state.draftOptions.get(i);
            menu.button(CHOICE_SLOTS[i], Menu.stack(speciesIcon(properties), Text.literal(describeProperties(properties)).formatted(Formatting.AQUA), List.of(
                    Text.literal("Joins your team for the rest of the run."),
                    Text.literal("Click to draft.").formatted(Formatting.YELLOW))),
                    p -> RunManager.get().prepDraft(p, index));
        }
        menu.back(22, "Back", p -> RunManager.get().openCurrent(p));
        addControls(menu, state, player.getUuid());
        menu.open(player);
    }

    private static boolean bossDoubles() {
        return !RogueConfig.get().doubleBattles.equalsIgnoreCase("none");
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
        Menu menu = new Menu(Text.literal("Wild Pokémon appeared!"), RUN_ROWS).layout(Menu.Layout.CARDS);
        String block = Biomes.block(state.biome);
        menu.filler(block).theme(block, true);
        UUID id = player.getUuid();
        Integer myPick = state.coopPicks.get(id);
        UUID otherId = state.other(id);
        List<String> options = RunManager.encounterOptionsFor(state, id);
        for (int i = 0; i < options.size() && i < CHOICE_SLOTS.length; i++) {
            int index = i;
            String properties = options.get(i);
            List<Text> lore = new ArrayList<>();
            if (myPick != null && myPick == index) {
                lore.add(Text.literal("Your pick!").formatted(Formatting.GREEN));
            } else if (myPick != null) {
                lore.add(Text.literal("Waiting for " + RunManager.get().partnerName(state, id) + "...").formatted(Formatting.GRAY));
            } else {
                lore.add(Text.literal("Click to add to your team."));
            }
            if (state.isCoop()) {
                lore.add(Text.literal("These are your own options (max " + RunManager.partyLimit(state) + " Pokémon each).").formatted(Formatting.DARK_GRAY));
            }
            menu.button(CHOICE_SLOTS[i], Menu.stack(speciesIcon(properties), Text.literal(describeProperties(properties)).formatted(Formatting.AQUA), lore),
                    p -> RunManager.get().chooseEncounter(p, index));
        }
        if (otherId != null) {
            boolean done = state.coopPicks.containsKey(otherId);
            menu.icon(4, Menu.stack(done ? "minecraft:lime_dye" : "minecraft:clock",
                    Text.literal(RunManager.get().partnerName(state, id) + (done ? " has picked" : " is choosing...")).formatted(done ? Formatting.GREEN : Formatting.GRAY),
                    List.of()));
        }
        menu.button(22, Menu.stack("minecraft:oak_door", Text.literal("Skip").formatted(Formatting.GRAY), List.of(
                Text.literal("Take nothing and move on."))),
                p -> RunManager.get().chooseEncounter(p, -1));
        addControls(menu, state, id);
        menu.open(player);
    }

    /** Co-op legendary win: one player takes the legendary, the other gets its companion. */
    public static void claim(ServerPlayerEntity player, RunState state) {
        Menu menu = new Menu(Text.literal("Split the reward"), RUN_ROWS).layout(Menu.Layout.CARDS);
        String partner = RunManager.get().partnerName(state, player.getUuid());
        menu.icon(4, Menu.stack("minecraft:nether_star", Text.literal("You won!").formatted(Formatting.LIGHT_PURPLE), List.of(
                Text.literal("One of you takes the legendary,"),
                Text.literal("the other gets its companion."),
                Text.literal("Whoever clicks first chooses.").formatted(Formatting.DARK_GRAY))));
        String[] labels = {"Legendary", "Companion"};
        int[] slots = {11, 15};
        for (int i = 0; i < 2 && i < state.coopClaim.size(); i++) {
            int index = i;
            String properties = state.coopClaim.get(i);
            menu.button(slots[i], Menu.stack(speciesIcon(properties), Text.literal(describeProperties(properties)).formatted(Formatting.AQUA), List.of(
                    Text.literal(labels[i]).formatted(Formatting.GOLD),
                    Text.literal(partner + " gets the other one."),
                    Text.literal("Click to take.").formatted(Formatting.YELLOW))),
                    p -> RunManager.get().claimLegendary(p, index));
        }
        addControls(menu, state, player.getUuid());
        menu.open(player);
    }

    public static void release(ServerPlayerEntity player, RunState state, String pending) {
        Menu menu = new Menu(Text.literal("Party full - release one?"), RUN_ROWS).layout(Menu.Layout.CARDS);
        menu.icon(4, Menu.stack(speciesIcon(pending), Text.literal("New: " + describeProperties(pending)).formatted(Formatting.AQUA), List.of(
                Text.literal("Party limit: " + RunManager.partyLimit(state)))));
        List<Pokemon> party = CobblemonBridge.partyMembers(player);
        for (int i = 0; i < party.size(); i++) {
            int index = i;
            menu.button(10 + i, Menu.stack(CobblemonBridge.icon(party.get(i)), Text.literal(CobblemonBridge.describe(party.get(i))).formatted(Formatting.YELLOW), List.of(
                    Text.literal("Release this Pokémon"),
                    Text.literal("to make room."))),
                    p -> RunManager.get().releaseForPending(p, index));
        }
        menu.button(22, Menu.stack("minecraft:oak_door", Text.literal("Keep my party").formatted(Formatting.GRAY), List.of(
                Text.literal("Let the new Pokémon go."))),
                p -> RunManager.get().releaseForPending(p, -1));
        addControls(menu, state, player.getUuid());
        menu.open(player);
    }

    private static final int RUN_ROWS = 4;
    private static final int NAV_ROW = 3;

    /**
     * The nav bar on every run screen (bottom row): run info, then Shop, Bag, Move Tutor, Save &
     * leave and End run. The rows above hold the screen's own content. How to play is only on the
     * start page.
     */
    private static void addControls(Menu menu, RunState state, UUID viewer) {
        RogueConfig config = RogueConfig.get();
        int base = NAV_ROW * 9;
        menu.fillRow(NAV_ROW, "minecraft:black_stained_glass_pane");
        menu.navRow(NAV_ROW).chestOnly(base); // the run screen shows the floor info itself

        List<Text> info = new ArrayList<>();
        info.add(Text.literal("Biome: " + Biomes.get(state.biome).name).formatted(Formatting.AQUA));
        info.add(Text.literal("Badges: " + state.badges + "/" + config.gymCount
                + (Scaling.championUnlocked(state.badges) && config.eliteCount > 0
                ? "  Elite Four: " + state.eliteWins + "/" + config.eliteCount : "")));
        info.add(Text.literal("Level cap: " + Scaling.levelCap(state.badges)));
        info.add(Text.literal("Coins: " + state.money).formatted(Formatting.GOLD));
        if (state.isCoop()) {
            info.add(Text.literal("Co-op with " + RunManager.get().partnerName(state, viewer)).formatted(Formatting.AQUA));
        }
        if (!state.modifiers.isEmpty()) {
            info.add(Text.literal("Modifiers: " + String.join(", ", state.modifiers.stream()
                    .map(id -> Modifiers.ALL.stream().filter(m -> m.id().equals(id)).map(Modifiers.Info::name).findFirst().orElse(id))
                    .toList())).formatted(Formatting.DARK_PURPLE));
        }
        menu.icon(base, Menu.stack("minecraft:filled_map", Text.literal("Floor " + state.floor).formatted(Formatting.WHITE), info));

        if (Modifiers.has(state, Modifiers.NO_SHOP)) {
            menu.icon(base + 2, Menu.stack("minecraft:gray_dye", Text.literal("Shop (closed)").formatted(Formatting.GRAY), List.of(
                    Text.literal("No Shop run."))));
        } else {
            menu.button(base + 2, Menu.stack("minecraft:emerald", Text.literal("Shop").formatted(Formatting.GREEN), List.of(
                    Text.literal(state.money + " coins"),
                    Text.literal("Healing, battle items, training…"))), ShopMenus::shop);
        }
        menu.button(base + 3, Menu.stack("minecraft:chest", Text.literal("Bag").formatted(Formatting.AQUA), List.of(
                Text.literal("Use items, manage held items."))), p -> ShopMenus.bag(p, 0));
        menu.button(base + 4, Menu.stack("minecraft:enchanted_book", Text.literal("Move Tutor").formatted(Formatting.LIGHT_PURPLE), List.of(
                Text.literal("Teach TM, tutor and egg moves."))), TutorMenus::pickPokemon);
        if (state.isCoop()) {
            menu.icon(base + 5, Menu.stack("minecraft:player_head", Text.literal("Co-op with " + RunManager.get().partnerName(state, viewer)).formatted(Formatting.AQUA), List.of(
                    Text.literal("Party limit: " + RunManager.partyLimit(state) + " each"),
                    Text.literal("Log off any time; the run waits for you."),
                    Text.literal("Save & leave isn't available in co-op.").formatted(Formatting.DARK_GRAY))));
            menu.chestOnly(base + 5); // the run screen shows the partner in the stats
        } else {
            menu.button(base + 5, Menu.stack("minecraft:ender_chest", Text.literal("Save & leave").formatted(Formatting.YELLOW), List.of(
                    Text.literal("Put the run away and get your real"),
                    Text.literal("party back. Continue any time from /rogue."))),
                    p -> RunManager.get().saveAndLeave(p));
        }
        menu.button(base + 8, Menu.stack("minecraft:barrier", Text.literal("End run").formatted(Formatting.RED), List.of(
                Text.literal("Asks for confirmation."))), p -> hub(p, true));
    }

    /** Pick an online player to invite to a co-op run. */
    public static void invite(ServerPlayerEntity player) {
        Menu menu = new Menu(Text.literal("Invite a friend"), 6).layout(Menu.Layout.GRID);
        int slot = 0;
        for (ServerPlayerEntity other : player.getServer().getPlayerManager().getPlayerList()) {
            if (other == player || slot >= 45) {
                continue;
            }
            boolean busy = RunManager.isInRun(other) || RunManager.get().inLobby(other);
            ItemStack head = new ItemStack(Items.PLAYER_HEAD);
            head.set(DataComponentTypes.PROFILE, new ProfileComponent(other.getGameProfile()));
            menu.button(slot++, Menu.stack(head, Text.literal(other.getName().getString()).formatted(busy ? Formatting.GRAY : Formatting.AQUA), List.of(
                    busy ? Text.literal("Already in a run").formatted(Formatting.RED) : Text.literal("Click to invite").formatted(Formatting.YELLOW))),
                    p -> {
                        Menu.close(p);
                        RunManager.get().invite(p, other);
                    });
        }
        if (slot == 0) {
            menu.icon(22, Menu.stack("minecraft:barrier", Text.literal("Nobody else is online").formatted(Formatting.RED), List.of()));
        }
        menu.icon(48, Menu.stack("minecraft:book", Text.literal("Co-op runs").formatted(Formatting.WHITE), List.of(
                Text.literal("Every battle is a 2 vs 2 against two trainers."),
                Text.literal("You both press Ready to start a fight."),
                Text.literal("Routes: each of you takes a different Pokémon."),
                Text.literal("Coins, floors and badges are shared."),
                Text.literal("Or use /rogue invite <player>.").formatted(Formatting.DARK_GRAY))));
        menu.back(49, "Back", p -> hub(p, false));
        menu.footer(50, Menu.stack("minecraft:netherite_sword", Text.literal("Run modifiers").formatted(Formatting.GOLD), List.of(
                Text.literal("As the host, your modifiers apply"),
                Text.literal("to the co-op run. Set them, then come"),
                Text.literal("back here to invite."))), RogueMenus::setup);
        menu.open(player);
    }

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
