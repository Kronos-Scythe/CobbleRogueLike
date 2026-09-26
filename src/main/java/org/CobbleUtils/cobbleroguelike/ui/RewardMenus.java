package org.CobbleUtils.cobbleroguelike.ui;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.CobbleUtils.cobbleroguelike.RogueConfig;
import org.CobbleUtils.cobbleroguelike.compat.ItemBridge;
import org.CobbleUtils.cobbleroguelike.run.RunManager;
import org.CobbleUtils.cobbleroguelike.shop.RewardService;

import java.util.List;

/** The /rogue shop screens: spend Rogue Tokens on real items. */
public final class RewardMenus {

    private static final int[] CATEGORY_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
    private static final int PAGE_SIZE = 45;

    private RewardMenus() {
    }

    public static void shop(ServerPlayerEntity player) {
        int tokens = RunManager.get().tokens(player);
        Menu menu = new Menu(Text.literal("Rogue Shop - " + tokens + " tokens"), 6);
        List<RewardService.Category> catalog = RewardService.catalog();
        for (int i = 0; i < catalog.size() && i < CATEGORY_SLOTS.length; i++) {
            int index = i;
            RewardService.Category category = catalog.get(i);
            menu.button(CATEGORY_SLOTS[i], Menu.stack(iconOf(category.icon()), Text.literal(category.name()).formatted(Formatting.LIGHT_PURPLE), List.of(
                    Text.literal(category.items().size() + " items"))),
                    p -> category(p, index, 0));
        }
        menu.icon(49, profileIcon(player));
        menu.open(player);
    }

    public static void category(ServerPlayerEntity player, int categoryIndex, int page) {
        List<RewardService.Category> catalog = RewardService.catalog();
        if (categoryIndex < 0 || categoryIndex >= catalog.size()) {
            shop(player);
            return;
        }
        int tokens = RunManager.get().tokens(player);
        RewardService.Category category = catalog.get(categoryIndex);
        List<RewardService.Entry> entries = category.items();
        int pages = Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int current = Math.max(0, Math.min(page, pages - 1));
        Menu menu = new Menu(Text.literal(category.name() + " - " + tokens + " tokens"), 6);
        for (int slot = 0; slot < PAGE_SIZE; slot++) {
            int index = current * PAGE_SIZE + slot;
            if (index >= entries.size()) {
                break;
            }
            RewardService.Entry entry = entries.get(index);
            ItemStack icon = ItemBridge.stack(entry.item());
            int entryIndex = index;
            menu.clickButton(slot, Menu.stack(icon, Text.literal(icon.getName().getString()).formatted(Formatting.WHITE), List.of(
                    Text.literal("Price: " + entry.price() + " tokens").formatted(Formatting.LIGHT_PURPLE),
                    Text.literal("A real item for your inventory.").formatted(Formatting.DARK_GRAY),
                    Text.literal("Left-click: buy 1 - Right-click: buy 5").formatted(Formatting.YELLOW))),
                    (p, right) -> RewardService.buy(p, categoryIndex, entryIndex, right ? 5 : 1, current));
        }
        if (current > 0) {
            menu.button(45, Menu.stack("minecraft:arrow", Text.literal("Previous page"), List.of()), p -> category(p, categoryIndex, current - 1));
        }
        if (current < pages - 1) {
            menu.button(53, Menu.stack("minecraft:arrow", Text.literal("Next page"), List.of()), p -> category(p, categoryIndex, current + 1));
        }
        menu.icon(48, profileIcon(player));
        menu.button(49, Menu.stack("minecraft:oak_door", Text.literal("Back"), List.of()), RewardMenus::shop);
        menu.open(player);
    }

    private static ItemStack profileIcon(ServerPlayerEntity player) {
        NbtCompound profile = RunManager.get().profile(player);
        RogueConfig config = RogueConfig.get();
        return Menu.stack("minecraft:amethyst_shard", Text.literal(profile.getInt("tokens") + " Rogue Tokens").formatted(Formatting.LIGHT_PURPLE), List.of(
                Text.literal("Runs: " + profile.getInt("runs") + "  Wins: " + profile.getInt("wins")),
                Text.literal("Best: floor " + profile.getInt("bestFloor") + ", " + profile.getInt("bestBadges") + " badges"),
                Text.literal(""),
                Text.literal("Earned when a run ends:").formatted(Formatting.GRAY),
                Text.literal(config.tokensPerFloor + " per floor, " + config.tokensPerBadge + " per badge,").formatted(Formatting.GRAY),
                Text.literal("+" + config.championTokenBonus + " for beating the Champion.").formatted(Formatting.GRAY)));
    }

    private static ItemStack iconOf(String itemId) {
        ItemStack stack = ItemBridge.stack(itemId);
        return stack.isEmpty() ? ItemBridge.stack("minecraft:paper") : stack;
    }
}
