package org.CobbleUtils.cobbleroguelike.ui;

import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.CobbleUtils.cobbleroguelike.compat.CobblemonBridge;
import org.CobbleUtils.cobbleroguelike.compat.ItemBridge;
import org.CobbleUtils.cobbleroguelike.run.Modifiers;
import org.CobbleUtils.cobbleroguelike.run.RunManager;
import org.CobbleUtils.cobbleroguelike.run.RunState;
import org.CobbleUtils.cobbleroguelike.shop.ShopCatalog;
import org.CobbleUtils.cobbleroguelike.shop.ShopService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Shop and bag screens. Reachable from every run menu, and always open between battles. */
public final class ShopMenus {

    private static final int[] CATEGORY_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
    private static final int PAGE_SIZE = 45;
    private static final int BAG_PAGE_SIZE = 36;

    private ShopMenus() {
    }

    public static void shop(ServerPlayerEntity player) {
        RunState state = runState(player);
        if (state == null) {
            return;
        }
        if (Modifiers.has(state, Modifiers.NO_SHOP)) {
            RunManager.message(player, "The shop is closed on No Shop runs.", Formatting.RED);
            return;
        }
        Menu menu = new Menu(Text.literal("Shop - " + state.money + " coins"), 6).layout(Menu.Layout.CARDS)
                .screenTitle(Text.literal("Shop"));
        List<ShopCatalog.Category> categories = ShopCatalog.categories();
        for (int i = 0; i < categories.size() && i < CATEGORY_SLOTS.length; i++) {
            int index = i;
            ShopCatalog.Category category = categories.get(i);
            menu.button(CATEGORY_SLOTS[i], Menu.stack(iconOf(category.icon()), Text.literal(category.name()).formatted(Formatting.AQUA), List.of(
                    Text.literal(category.items().size() + " items"))),
                    p -> category(p, index, 0));
        }
        addFooter(menu, state, false);
        menu.open(player);
    }

    public static void category(ServerPlayerEntity player, int categoryIndex, int page) {
        RunState state = runState(player);
        if (state == null) {
            return;
        }
        List<ShopCatalog.Category> categories = ShopCatalog.categories();
        if (categoryIndex < 0 || categoryIndex >= categories.size()) {
            shop(player);
            return;
        }
        ShopCatalog.Category category = categories.get(categoryIndex);
        List<ShopCatalog.Entry> entries = category.items();
        int pages = Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int current = Math.max(0, Math.min(page, pages - 1));
        Menu menu = new Menu(Text.literal(category.name() + " - " + state.money + " coins"), 6)
                .screenTitle(Text.literal("Shop - " + category.name()));

        for (int slot = 0; slot < PAGE_SIZE; slot++) {
            int index = current * PAGE_SIZE + slot;
            if (index >= entries.size()) {
                break;
            }
            ShopCatalog.Entry entry = entries.get(index);
            List<Text> lore = new ArrayList<>();
            lore.add(Text.literal("Price: " + entry.price() + " coins").formatted(Formatting.GOLD));
            if (entry.action().equals(ShopCatalog.UNLOCK)) {
                String gimmick = entry.item().substring("gimmick:".length());
                lore.add(Text.literal("Unlocks this gimmick for the rest of the run."));
                lore.add(Text.literal("No item needed: it works as if equipped."));
                if (gimmick.equals("dynamax")) {
                    lore.add(Text.literal("May still need a Power Spot (Mega Showdown config).").formatted(Formatting.DARK_GRAY));
                }
                lore.add(state.gimmicks.contains(gimmick)
                        ? Text.literal("Unlocked").formatted(Formatting.GREEN)
                        : Text.literal("Click to unlock").formatted(Formatting.YELLOW));
            } else {
                lore.add(Text.literal("In bag: " + state.bagCount(entry.item())));
                lore.add(Text.literal(actionHint(entry.action(), entry.item())).formatted(Formatting.DARK_GRAY));
                lore.add(Text.literal("Left-click: buy 1 - Right-click: buy 5").formatted(Formatting.YELLOW));
            }
            int entryIndex = index;
            menu.clickButton(slot, Menu.stack(iconOf(entry.icon()),
                    Text.literal(ShopService.displayName(entry)).formatted(Formatting.WHITE), lore),
                    (p, right) -> ShopService.buy(p, categoryIndex, entryIndex, right ? 5 : 1, current));
        }
        if (current > 0) {
            menu.footer(45, Menu.stack("minecraft:arrow", Text.literal("Previous page"), List.of()),
                    p -> category(p, categoryIndex, current - 1));
        }
        if (current < pages - 1) {
            menu.footer(53, Menu.stack("minecraft:arrow", Text.literal("Next page"), List.of()),
                    p -> category(p, categoryIndex, current + 1));
        }
        menu.icon(48, coins(state)).chestOnly(48);
        menu.back(49, "Back to shop", ShopMenus::shop);
        menu.footer(50, Menu.stack("minecraft:chest", Text.literal("Bag").formatted(Formatting.AQUA), List.of()), p -> bag(p, 0));
        menu.open(player);
    }

    /** Party with held items on the top row, bag items below. */
    public static void bag(ServerPlayerEntity player, int page) {
        RunState state = runState(player);
        if (state == null) {
            return;
        }
        Menu menu = new Menu(Text.literal("Bag - " + state.money + " coins"), 6).layout(Menu.Layout.GRID)
                .screenTitle(Text.literal("Bag"));
        boolean screen = Menu.usesScreen(player);

        List<Pokemon> party = CobblemonBridge.partyMembers(player);
        for (int i = 0; i < party.size() && i < 6; i++) {
            int index = i;
            Pokemon pokemon = party.get(i);
            ItemStack held = ItemBridge.held(pokemon);
            if (screen && held.isEmpty()) {
                continue; // the run screen already shows the party; only list held items to take back
            }
            List<Text> lore = new ArrayList<>();
            lore.add(Text.literal("HP: " + pokemon.getCurrentHealth() + "/" + pokemon.getMaxHealth()));
            lore.add(Text.literal("Holding: " + (held.isEmpty() ? "nothing" : held.getName().getString())));
            if (!held.isEmpty()) {
                lore.add(Text.literal("Click to take the held item.").formatted(Formatting.YELLOW));
            }
            menu.button(i, Menu.stack(CobblemonBridge.icon(pokemon),
                    Text.literal(CobblemonBridge.describe(pokemon)).formatted(Formatting.AQUA), lore),
                    p -> ShopService.takeHeld(p, index));
        }
        menu.icon(8, Menu.stack("minecraft:book", Text.literal("Your run bag").formatted(Formatting.WHITE), List.of(
                Text.literal("Click an item, then a Pokémon to use or"),
                Text.literal("give it. Click a Pokémon above to take"),
                Text.literal("its held item back into the bag."),
                Text.literal("Items stay in the run and are lost when it ends.")))).chestOnly(8);

        List<Map.Entry<String, Integer>> items = new ArrayList<>(state.bag.entrySet());
        int pages = Math.max(1, (items.size() + BAG_PAGE_SIZE - 1) / BAG_PAGE_SIZE);
        int current = Math.max(0, Math.min(page, pages - 1));
        for (int slot = 0; slot < BAG_PAGE_SIZE; slot++) {
            int index = current * BAG_PAGE_SIZE + slot;
            if (index >= items.size()) {
                break;
            }
            String itemId = items.get(index).getKey();
            int count = items.get(index).getValue();
            ItemStack icon = ItemBridge.stack(itemId);
            if (icon.isEmpty()) {
                continue;
            }
            icon.setCount(Math.max(1, Math.min(count, icon.getMaxCount())));
            menu.button(9 + slot, Menu.stack(icon, Text.literal(icon.getName().getString() + " x" + count).formatted(Formatting.WHITE), List.of(
                    Text.literal(actionHint(ShopCatalog.actionFor(itemId), itemId)).formatted(Formatting.DARK_GRAY),
                    Text.literal("Click to choose a Pokémon.").formatted(Formatting.YELLOW))),
                    p -> target(p, itemId));
        }
        if (current > 0) {
            menu.footer(45, Menu.stack("minecraft:arrow", Text.literal("Previous page"), List.of()), p -> bag(p, current - 1));
        }
        if (current < pages - 1) {
            menu.footer(53, Menu.stack("minecraft:arrow", Text.literal("Next page"), List.of()), p -> bag(p, current + 1));
        }
        addFooter(menu, state, true);
        menu.open(player);
    }

    public static void target(ServerPlayerEntity player, String itemId) {
        RunState state = runState(player);
        if (state == null) {
            return;
        }
        boolean use = ShopCatalog.actionFor(itemId).equals(ShopCatalog.USE);
        ItemStack itemIcon = ItemBridge.stack(itemId);
        Menu menu = new Menu(Text.literal((use ? "Use " : "Give ") + itemIcon.getName().getString() + " to...")).layout(Menu.Layout.CARDS);
        menu.icon(4, Menu.stack(itemIcon, Text.literal(itemIcon.getName().getString() + " x" + state.bagCount(itemId)), List.of()));
        List<Pokemon> party = CobblemonBridge.partyMembers(player);
        for (int i = 0; i < party.size() && i < 6; i++) {
            int index = i;
            Pokemon pokemon = party.get(i);
            ItemStack held = ItemBridge.held(pokemon);
            menu.button(10 + i, Menu.stack(CobblemonBridge.icon(pokemon),
                    Text.literal(CobblemonBridge.describe(pokemon)).formatted(Formatting.AQUA), List.of(
                            Text.literal("HP: " + pokemon.getCurrentHealth() + "/" + pokemon.getMaxHealth()),
                            Text.literal("Holding: " + (held.isEmpty() ? "nothing" : held.getName().getString())),
                            Text.literal(use ? "Click to use." : "Click to give (swaps its held item).").formatted(Formatting.YELLOW))),
                    p -> ShopService.apply(p, itemId, index));
        }
        menu.back(22, "Back to bag", p -> bag(p, 0));
        menu.open(player);
    }

    private static void addFooter(Menu menu, RunState state, boolean inBag) {
        menu.icon(48, coins(state)).chestOnly(48);
        menu.back(49, "Back to run", p -> RunManager.get().openCurrent(p));
        if (inBag) {
            menu.footer(50, Menu.stack("minecraft:emerald", Text.literal("Shop").formatted(Formatting.GREEN), List.of()), ShopMenus::shop);
        } else {
            menu.footer(50, Menu.stack("minecraft:chest", Text.literal("Bag").formatted(Formatting.AQUA), List.of()), p -> bag(p, 0));
        }
    }

    private static ItemStack coins(RunState state) {
        return Menu.stack("minecraft:gold_nugget", Text.literal(state.money + " coins").formatted(Formatting.GOLD), List.of(
                Text.literal("Earned by winning battles."),
                Text.literal("Coins only exist during this run.")));
    }

    private static ItemStack iconOf(String itemId) {
        ItemStack stack = ItemBridge.stack(itemId);
        return stack.isEmpty() ? ItemBridge.stack("minecraft:paper") : stack;
    }

    private static String actionHint(String action, String itemId) {
        return switch (action) {
            case ShopCatalog.USE -> "Used on a Pokémon.";
            case ShopCatalog.HOLD -> "Given to a Pokémon to hold.";
            default -> ItemBridge.isUsable(itemId) ? "Used on a Pokémon." : "Given to a Pokémon to hold.";
        };
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
